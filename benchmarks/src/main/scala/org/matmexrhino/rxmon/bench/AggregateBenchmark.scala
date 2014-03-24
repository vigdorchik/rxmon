/*
 * Copyright 2013-2014 Eugene Vigdorchik.
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
import rx.lang.scala.schedulers.TestScheduler

object AggregateConfig {
  val srcName = "X"
  val window = 10
  val era = 1000
  val rate = 100
  val N = era * rate
}

object AggregateBenchmark extends App {
  import AggregateConfig._

  class MyRegistry(s: TestScheduler) extends Registry {
    val start = System.currentTimeMillis

    val src = register[Int](srcName)
    val m = src.max(window.seconds)(s)

    var t = 0
    m subscribe { x =>
      t += 1
      if (x == N) {
	println(s"${System.currentTimeMillis - start} milliseconds elapsed.")
	context.system.shutdown()
      } else if (t == rate) {
	t = 0
	s advanceTimeBy 1.seconds
      }
    }
  }

  class Send(registry: ActorRef) extends Actor {
    registry ! ListEntries

    def receive = {
      case EntriesResponse(targets) =>
	val target = targets(srcName)
	for (i <- 1 to era; j <- 1 to rate) target ! (i * j)
    }
  }

  override def main(args: Array[String]) {
    val system = ActorSystem()
    val s = TestScheduler()
    val registry = system.actorOf(Props(classOf[MyRegistry], s))
    system.actorOf(Props(classOf[Send], registry))
  }
}
