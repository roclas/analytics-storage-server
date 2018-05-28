package com.roclas.logic

import java.io.FileWriter

import akka.actor.Actor
import com.roclas.server.Work

class Writer extends Actor {
    val fw = new FileWriter(s"out_${self.path.name}", true)
    def receive = {
      case Work(x) ⇒
        fw.append(s"$x\n")
        sender ! s"processing ${x} in thread ${Thread.currentThread().getId}\n"
    }
    override def postStop(): Unit = {
      fw.close()
      super.postStop()
    }
}

