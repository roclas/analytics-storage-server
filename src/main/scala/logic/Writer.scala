package com.roclas.logic

import java.io.PrintStream
import java.net.{InetAddress, Socket}

import akka.actor.Actor
import com.roclas.server.Work

import scala.io.BufferedSource

class Writer (tcpPort:Int) extends Actor {
    val s = new Socket(InetAddress.getByName("localhost"), tcpPort)
    lazy val in = new BufferedSource(s.getInputStream()).getLines()
    val out = new PrintStream(s.getOutputStream())

    def receive = {
      case Work(x) ⇒
        println(s"$x")
        out.println(s"$x")
        out.flush()
        sender ! s"processing ${x} in thread ${Thread.currentThread().getId}\n"
    }
    override def postStop(): Unit = {
      s.close()
      super.postStop()
    }
}

