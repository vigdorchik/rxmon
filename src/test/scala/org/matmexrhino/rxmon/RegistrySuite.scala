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
 * limitations under the License.
 */
package org.maxmexrhino.rxmon

import org.junit.runner.RunWith
import org.scalatest.junit.JUnitRunner
import org.junit.Assert._
import org.scalatest.FunSuite

import akka.testkit.{ TestProbe, TestKit, ImplicitSender }
import akka.actor.{ ActorSystem, Props }
import rx.lang.scala.Observable
import org.scalatest.matchers.ShouldMatchers
import org.scalatest.FunSuite
import scala.reflect.ClassTag
import scala.concurrent.duration._
import scala.collection.mutable.ListBuffer

case object Done
// This is done just for test. In prod you probably don't want your observables to escape.
class TestRegistry[T](implicit tag: ClassTag[T]) extends Registry {
  val X: Observable[T] = register[T]("X")
  X.subscribe (buff += _)

  val buff = ListBuffer[T]()

  override def receive  = super.receive orElse {
    case Done => sender ! buff.toList
  }
}

@RunWith(classOf[JUnitRunner])
class RegistrySuite extends FunSuite {
  test("basic") {
    val range = 1 to 100
    
    implicit val system = ActorSystem("basic")
    val registryActor = system.actorOf(Props(classOf[TestRegistry[Int]], ClassTag.Int), "registry")
    val client = TestProbe()
    client.send(registryActor, ListEntries)
    val EntriesResponse(entries) = client.expectMsgClass(classOf[EntriesResponse])
    val monitor = entries("X")

    for (i <- range)  monitor ! i

    client.expectNoMsg(1.seconds)
    client.send(registryActor, Done)
    val l = client.expectMsgClass(classOf[List[Int]])
    assertEquals(range.toList, l)

    system.shutdown()
  }

  test("unit") {
    implicit val system = ActorSystem("unit")
    val registryActor = system.actorOf(Props(classOf[TestRegistry[Unit]], ClassTag.Unit), "registry")
    val client = TestProbe()
    client.send(registryActor, ListEntries)
    val EntriesResponse(entries) = client.expectMsgClass(classOf[EntriesResponse])
    val monitor = entries("X")

    val N = 5
    for (_ <- 1 to N) monitor ! ()

    client.expectNoMsg(1.seconds)
    client.send(registryActor, Done)
    val l = client.expectMsgClass(classOf[List[Int]])
    assertEquals(List.fill(N)( {} ), l)

    system.shutdown()
  }
}
