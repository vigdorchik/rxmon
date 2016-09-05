/*
 * Copyright 2013 Eugene Vigdorchik.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License. */
package org.maxmexrhino.rxmon
package bench

import Operations._
import akka.actor._
import scala.concurrent.duration._

object BinopConfig {
  val nLeaves = 10
  val era = 30
  val rate = 500
  val nSends = era * rate
  val sum = nLeaves * nSends
}

object BinopBenchmark extends App {
  import BinopConfig._

  class MyRegistry extends Registry {
    val start = System.currentTimeMillis

    val leaves = 1 to nLeaves map {x =>
      register[Int](x.toString)
    }
    val root = leaves reduce (_ + _)

    root subscribe { x =>
      if (x == sum) {
	println(s"${System.currentTimeMillis - start} milliseconds elapsed.")
	context.system.terminate()
      }
    }
  }

  case class Msg(targets: Iterable[ActorRef], gen: Int)

  class Send(registry: ActorRef) extends Actor {
    registry ! ListEntries

    def receive = {
      case EntriesResponse(targets) =>
        self ! Msg(targets.values, 1)
      case Msg(targets, gen) =>
        for (j <- 1 to rate; target <- targets) target ! (gen * j)
        if (gen < era) {
          import system.dispatcher

          context.system.scheduler.scheduleOnce(1.seconds) {
            self ! Msg(targets, gen+1)
          }
        }
    }
  }

  val system = ActorSystem()
  val registry = system.actorOf(Props(classOf[MyRegistry]))
  system.actorOf(Props(classOf[Send], registry))
}
