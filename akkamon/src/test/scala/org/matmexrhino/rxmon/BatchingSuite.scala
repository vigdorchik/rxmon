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
  val P = 37
  val gen = 2
  val group = List.iterate(gen, P - 1)(x => (x * gen) % P )
  assertEquals(P - 1, group.toSet.size)

  def doTest[T: ClassTag](batch: BatchContext => Batcher[_, _, _], expected: T) {
    implicit val system = ActorSystem("test")
    val client = TestProbe()
    val registry = system.actorOf(Props(classOf[TestRegistry[T]], implicitly[ClassTag[T]]), "registry")
    val monitor = Tester.getMonitor(registry, client)
    val d = 1.seconds

    val c = BatchContext(d, monitor)
    val target = system.actorOf(Props(batch(c)))
    for (i <- group) target ! i
    target ! new AnyRef

    client.send(registry, Done)
    val l = client.expectMsgClass(classOf[List[T]])
    assertEquals(List(expected), l)

    system.terminate()
  }

  test("min") {
    doTest(min[Int] _, 1)
  }

  test("max") {
    doTest(max[Int] _, P - 1)
  }

  test("avg") {
    doTest(avg[Int] _, P.toDouble / 2)
  }
}
