package com.roclas.server

import java.io.FileWriter
import java.util.concurrent.Executors

import akka.actor.{Actor, ActorSystem, Props, Terminated}
import akka.http.scaladsl.Http
import akka.http.scaladsl.marshalling.ToResponseMarshaller
import akka.http.scaladsl.model._
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


case class Work(json: String)

object Server extends App {

  var cnf = ConfigFactory.load()
  val POOL_SIZE_CNF_STR = "blocking-io-dispatcher.thread-pool-executor.fixed-pool-size"
  val nOfCores = Runtime.getRuntime().availableProcessors()
  val extraThreads = 50

  val nOfThreadsXCore = ConfigFactory.load().getInt("my.app.nOfThreadsPerCore")
  val port = cnf.getInt("my.app.port")
  val analyticsPath = cnf.getString("my.app.analyticsPath")
  val server = cnf.getString("my.app.server")
  cnf = cnf.withValue(POOL_SIZE_CNF_STR, ConfigValueFactory.fromAnyRef(nOfThreadsXCore * nOfCores + extraThreads))

  implicit val system = ActorSystem("analytics-storage-system", cnf)


  println(s"\n\nyour processor has ${nOfCores} cores!!")
  println(s"Using ${nOfThreadsXCore} threads/core + $extraThreads = ${cnf.getInt(POOL_SIZE_CNF_STR)} threads\n\n")


  class WriterRouter extends Actor {
    val writer = context.actorOf(Props[Writer])
    var router = {
      val routees = Vector.fill(nOfCores * nOfThreadsXCore) {
        val r = context.actorOf(Props[Writer])
        context watch r
        ActorRefRoutee(r)
      }
      Router(RoundRobinRoutingLogic(), routees)
    }

    def receive = {
      case w: Work ⇒ router.route(w, sender())
      case Terminated(a) ⇒
        router = router.removeRoutee(a)
        val r = context.actorOf(Props[Writer])
        context watch r
        router = router.addRoutee(r)
    }
  }

  // needed for the future flatMap/onComplete in the end
  implicit val materializer = ActorMaterializer()
  implicit val ec = ExecutionContext.fromExecutor(Executors.newFixedThreadPool(nOfCores * nOfThreadsXCore + 50))
  val writers = system.actorOf(Props[WriterRouter].withDispatcher("blocking-io-dispatcher"), "writers")
  val route0 = path("") {
    get {
      complete(HttpEntity(ContentTypes.`text/html(UTF-8)`,
        s"""<h1>Welcome to the Analytics Storage Server</h1>""" +
          s"""use it <a href="http://$server:$port/$analyticsPath">HERE</a> """
      ))
    }
  }
  val route1 = path(analyticsPath) {
    put {
      implicit val askTimeout: Timeout = 3.seconds
      entity(as[String]) { json =>
        onSuccess((writers ? Work(json)).mapTo[String]) { result =>
          complete(HttpEntity(ContentTypes.`text/html(UTF-8)`, s"<h2>${result}</h2><br /> SAVED!!"))
        }
      }
    } ~
      get {
        complete(HttpEntity(ContentTypes.`text/html(UTF-8)`,
          s"""<h2>Fire Analytic Events to the Server</h2>curl -X PUT -H "Content-Type: application/json" """ +
            s"""-d '{"key1":"value"}' $server:$port/$analyticsPath"""
        ))
      }
  }

  val bindingFuture = Http().bindAndHandle(route1 ~ route0, "localhost", port)

  println(s"Server online at http://localhost:$port/analytics\nPress RETURN to stop...")
  StdIn.readLine() // let it run until user presses return
  bindingFuture
    .flatMap(_.unbind()) // trigger unbinding from the port
    .onComplete(_ => system.terminate()) // and shutdown when done

}