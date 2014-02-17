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
 * limitations under the License.
 */
package org.maxmexrhino.rxmon

import scala.reflect.ClassTag
import rx.lang.scala.Observable
import scala.collection.mutable.ListBuffer
import akka.testkit.TestProbe
import akka.actor.{ PoisonPill, ActorRef }

case object Done
// This is done just for the test. In prod you probably don't want your observables to escape.
class TestRegistry[T](implicit tag: ClassTag[T]) extends Registry {
  val X: Observable[T] = register[T](Tester.id)
  X.subscribe (buff += _)

  val buff = ListBuffer[T]()
  override def receive  = super.receive orElse {
    case Done =>
      sender ! buff.toList
      self ! PoisonPill
  }
}

object Tester {
  val id = "X"

  def getMonitor(registry: ActorRef, client: TestProbe) = {
    client.send(registry, ListEntries)
    val EntriesResponse(entries) = client.expectMsgClass(classOf[EntriesResponse])
    entries(id)
  }
}
