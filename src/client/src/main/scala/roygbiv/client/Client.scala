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
package roygbiv.client

import akka.kernel.Bootable
import akka.actor.{ Props, ActorSystem }
import roygbiv.ClientRegistration

class Client(system: ActorSystem) { //}extends Bootable {
  // Start the actor system and a worker actor
  //val system = ActorSystem("RaytraceClient")
  val clientWorker = system.actorOf(Props[Worker], "worker")

  // Call remote server and register client
  println("distributer:" + system.actorFor(system.settings.config.getString("akka.roygbiv.serverWorkDistributor")) +
    system.settings.config.getString("akka.roygbiv.serverWorkDistributor"))
  system.actorFor(system.settings.config.getString("akka.roygbiv.serverWorkDistributor")) !
    ClientRegistration(system.settings.config.getString("akka.roygbiv.client"))

  println("*** ROYGBIV CLIENT STARTED ***")

  def startup() {}

  def shutdown() {
    system.shutdown()
  }
}

//object Client extends App {
//  new Client
//}