/**
 * Copyright [2011] [Henrik Engstroem, Mario Gonzalez]
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package roygbiv.server

import akka.kernel.Bootable
import akka.actor.{ Props, ActorSystem }
import roygbiv.{ Stop, Pause, Start }

class Server(system: ActorSystem) { //}extends Bootable {
  //val system = ActorSystem("RaytraceServer")
  val aggregator = system.actorOf(Props[Aggregator], "aggregator")
  println(aggregator)
  val distributor = system.actorOf(Props[Distributor], "distributor")
  println(distributor)
  println("*** ROYGBIV SERVER STARTED ***")

  def startup() {
    // TODO (HE): temp start while Play 2.0-RCx is not running on Akka 2.0
    start()
  }

  def shutdown() {
    system.shutdown()
  }

  def start(): Unit = {
    distributor ! Start
    aggregator ! Start
  }

  def pause(): Unit = {
    distributor ! Pause
  }

  def stop(): Unit = {
    distributor ! Stop
  }
}

//object Server extends App {
//
//  val s = new Server
//  //  s.start
//  //  val c = new roygbiv.client.Client
//
//  def start(): Unit = {
//    s.start()
//  }
//
//  def pause(): Unit = {
//    s.pause()
//  }
//
//  def stop(): Unit = {
//    s.stop()
//  }
//}
