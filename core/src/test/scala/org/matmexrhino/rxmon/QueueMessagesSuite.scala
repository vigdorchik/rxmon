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

import org.junit.runner.RunWith
import org.scalatest.junit.JUnitRunner
import org.junit.Assert._
import org.scalatest.FunSuite

import com.typesafe.config.{ Config, ConfigFactory }
import akka.testkit.{ TestProbe, TestKit, ImplicitSender }
import akka.actor.{ ActorRef, ActorSystem, Props }
import akka.dispatch._
import org.scalatest.matchers.ShouldMatchers
import org.scalatest.FunSuite
import scala.reflect.ClassTag
import scala.concurrent.duration._
import Tester._
import QueueMessages._

class MyMailbox(settings: ActorSystem.Settings, config: Config) extends MailboxType {
  def create(owner: Option[ActorRef], system: Option[ActorSystem]) = new MyMessageQueue(system.get)
}
class MyMessageQueue(val system: ActorSystem) extends UnboundedMailbox.MessageQueue with QueueSizeReporter

@RunWith(classOf[JUnitRunner])
class QueueMessagesSuite extends FunSuite {
  val N = 10000
  val mailboxType = "test.my.mailbox"

  def doTest(m: QueueMessage, pred: List[Long] => Boolean) = {
    val conf = ConfigFactory.parseString(s"""akka.actor.default-mailbox {
                                              mailbox-type = ${classOf[MyMailbox].getName}
					 }""".stripMargin)
    implicit val system = ActorSystem("test", conf)
    val registry = system.actorOf(Props(classOf[TestRegistry[Long]], implicitly[ClassTag[Long]]), "registry")
    val client = TestProbe()
    val monitor = Tester.getMonitor(registry, client)
    for (_ <- 1 to N) monitor ! m
    monitor ! new AnyRef

    client.send(registry, Done)
    val l = client.expectMsgClass(classOf[List[Long]])
    assert(pred(l))

    system.shutdown()
  }

  test("size") {
    doTest(Size, {l =>
      val max = l.max
      max < N && max > 0
    })
  }

  test("enqueue") {
    doTest(Enqueues, {l => l.max == N})
  }

  test("dequeue") {
    doTest(Dequeues, {l => l == l.sorted})
  }
}
