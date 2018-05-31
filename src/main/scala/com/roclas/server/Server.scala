package com.roclas.server

import java.io.FileWriter
import java.util.concurrent.Executors

import akka.actor.{Actor, ActorSystem, Props, Terminated}
import akka.http.scaladsl.Http
import akka.http.scaladsl.marshalling.ToResponseMarshaller
import akka.http.scaladsl.model._
import akka.http.scaladsl.model.headers.RawHeader
import akka.http.scaladsl.server.Directives._
import akka.stream.ActorMaterializer
import akka.pattern.ask
import akka.util.Timeout

import scala.concurrent.duration._
import scala.io.StdIn
import akka.routing.{ActorRefRoutee, RoundRobinRoutingLogic, Router}
import com.roclas.logic.Writer
import com.typesafe.config.{ConfigFactory, ConfigObject, ConfigValueFactory}

import scala.collection.JavaConverters._
import scala.concurrent.ExecutionContext
import akka.http.scaladsl.server.Directives._
import akka.http.scaladsl.server.StandardRoute


case class Work(json: String)

object Server extends App {

  var cnf = ConfigFactory.load()
  val POOL_SIZE_CNF_STR = "blocking-io-dispatcher.thread-pool-executor.fixed-pool-size"
  val nOfCores = Runtime.getRuntime().availableProcessors()
  val extraThreads = 50

  val nOfThreadsXCore = ConfigFactory.load().getInt("my.app.nOfThreadsPerCore")
  val port = cnf.getInt("my.app.port")
  val analyticsPath = cnf.getString("my.app.analyticsPath")
  val trackPath = cnf.getString("my.app.trackPath")
  val server = cnf.getString("my.app.server")
  val logstashTcpPort= cnf.getInt("my.app.logstashTcpPort")
  cnf = cnf.withValue(POOL_SIZE_CNF_STR, ConfigValueFactory.fromAnyRef(nOfThreadsXCore * nOfCores + extraThreads))

  implicit val system = ActorSystem("analytics-storage-system", cnf)


  println(s"\n\nyour processor has ${nOfCores} cores!!")
  println(s"Using ${nOfThreadsXCore} threads/core + $extraThreads = ${cnf.getInt(POOL_SIZE_CNF_STR)} threads\n\n")


  class WriterRouter extends Actor {
    var router = {
      val routees = Vector.fill(nOfCores * nOfThreadsXCore) {
        val r= context.actorOf(Props(new Writer(logstashTcpPort)))
        context watch r
        ActorRefRoutee(r)
      }
      Router(RoundRobinRoutingLogic(), routees)
    }

    def receive = {
      case w: Work ⇒ router.route(w, sender())
      case Terminated(a) ⇒
        router = router.removeRoutee(a)
        val r= context.actorOf(Props(new Writer(logstashTcpPort)))
        context watch r
        router = router.addRoutee(r)
    }
  }

  def xssSolve(s: String)={
    respondWithHeaders(RawHeader("Access-Control-Allow-Origin", "http://localhost:8080"),RawHeader("Access-Control-Allow-Credentials", "true")) { //CORS/XSS
              complete(HttpEntity(ContentTypes.`text/html(UTF-8)`, s"<h2>$s</h2><br /> SAVED!!"))
    }
  }

  // needed for the future flatMap/onComplete in the end
  implicit val materializer = ActorMaterializer()
  implicit val ec = ExecutionContext.fromExecutor(Executors.newFixedThreadPool(nOfCores * nOfThreadsXCore + 50))
  val writers = system.actorOf(Props[WriterRouter].withDispatcher("blocking-io-dispatcher"), "writers")
  val route = pathSingleSlash {
    get {
      complete(HttpEntity(ContentTypes.`text/html(UTF-8)`,
        s"""<h1>Welcome to the Analytics Storage Server</h1>""" +
          s"""use it <a href="http://$server:$port/$analyticsPath">HERE</a> """
      ))
    }
  } ~
    path(analyticsPath) {
      implicit val askTimeout: Timeout = 3.seconds
      put{ entity(as[String]) { json => onSuccess((writers ? Work(json)).mapTo[String]) { r=> xssSolve(r) } } } ~
      post{ entity(as[String]) { json => onSuccess((writers ? Work(json)).mapTo[String]) { r=> xssSolve(r) } } } ~
        get {
          complete(HttpEntity(ContentTypes.`text/html(UTF-8)`,
            s"""<h2>Fire Analytic Events to the Server</h2>curl -X PUT -H "Content-Type: application/json" """ +
              s"""-d '{"key1":"value"}' $server:$port/$analyticsPath"""
          ))
        }
    } ~
    path(analyticsPath / trackPath) {
      get {
        parameter('data) { data =>
          println(s"data=$data")//TODO: send to Writter actor to process instead
          xssSolve(data)
        }
      }
    }

  val bindingFuture = Http().bindAndHandle(route, "localhost", port)

  println(s"Server online at http://localhost:$port/analytics\nPress RETURN to stop...")
  StdIn.readLine() // let it run until user presses return
  bindingFuture
    .flatMap(_.unbind()) // trigger unbinding from the port
    .onComplete(_ => system.terminate()) // and shutdown when done

}