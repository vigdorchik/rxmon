/**
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

import org.junit.runner.RunWith
import org.scalatest.junit.JUnitRunner
import org.junit.Assert._
import org.scalatest.FunSuite
import scala.concurrent.duration._
import scala.reflect.ClassTag
import Batcher._
import akka.testkit.TestProbe
import akka.actor.{ ActorRef, ActorSystem, Props }

@RunWith(classOf[JUnitRunner])
class BatchingSuite extends FunSuite {
  def doTest[T: ClassTag](expected: T)(f: (BatchContext, ActorSystem) => Unit) {
    implicit val system = ActorSystem("test")
    val client = TestProbe()
    val registry = system.actorOf(Props(classOf[TestRegistry[T]], implicitly[ClassTag[T]]), "registry")
    val monitor = Tester.getMonitor(registry, client)
    val d = 1.seconds
    val c = BatchContext(d, monitor)
    f(c, system)
    client.expectNoMsg(d plus 100.milliseconds)
    client.send(registry, Done)
    val l = client.expectMsgClass(classOf[List[T]])
    assertEquals(List(expected), l)

    system.shutdown()
  }

  val P = 37
  val gen = 2
  def subgroup: List[Int] = List.iterate(gen, P - 1)(x => (x * gen) % P )

  test("min") {
    doTest(1) { (c, s) =>
      val batch = s.actorOf(Props(min[Int](c)))
      for (i <- subgroup) batch ! i
    }
  }

  test("max") {
    doTest(P - 1) { (c, s) =>
      val batch = s.actorOf(Props(max[Int](c)))
      for (i <- subgroup) batch ! i
    }
  }
}
