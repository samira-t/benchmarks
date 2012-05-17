package roygbiv

import roygbiv.server.Server
import roygbiv.client.Client

object Main {
  def main(args: Array[String]) {

    val system = akka.actor.ActorSystem("Raytrace")
    val s = new Server(system)
    s.start()
    Thread.sleep(1000)
    val c = new Client(system)

    Thread.sleep(200000)

  }
}