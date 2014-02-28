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

object Config {
  val nLeaves = 10
  val nSends = 1000000
  val sum = nLeaves * nSends
}

object Benchmark extends App {
  import Config._

  var start: Long = _

  class BenchRegistry extends Registry {
    val leaves = 1 to nLeaves map (x => register[Int](x.toString))
    val root = leaves reduce (_ + _)

    root subscribe { x =>
      if (x == sum) {
	println(s"${System.currentTimeMillis - start} milliseconds elapsed.")
	context.system.shutdown()
      }
    }
  }

  class Send(registry: ActorRef) extends Actor {
    registry ! ListEntries

    def receive = {
      case EntriesResponse(targets) =>
	start = System.currentTimeMillis
	for (i <- 1 to nSends; (_, t) <- targets) t ! i
    }
  }

  override def main(args: Array[String]) {
    val system = ActorSystem()
    val registry = system.actorOf(Props(classOf[BenchRegistry]))
    system.actorOf(Props(classOf[Send], registry))
  }
}
