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

import java.util.concurrent.atomic.AtomicInteger
import akka.actor._
import akka.dispatch._

/**
 * Sends of this object result in sending the size of the message queue to the
 * specified target.
 */
case object QueueSize

/**
 * Mixin this trait in order to get the queue tracking its enqueues and dequeues
 * and reporting its size on demand. It's a good idea to have bare Integers
 * prioritized over other messages, but that's not obligatory.
 */
trait QueueSizeReporter { self: MessageQueue =>
  def system: ActorSystem

  val messagesCount = new AtomicInteger

  override def enqueue(receiver: ActorRef, handle: Envelope) {
    val count = messagesCount.incrementAndGet()
    handle match {
      case Envelope(QueueSize, sender) =>
	self.enqueue(receiver, Envelope(count, sender, system))
      case _ =>
	self.enqueue(receiver, handle)
    }
  }

  override def dequeue(): Envelope = {
    val x = self.dequeue()
    if (x ne null) messagesCount.decrementAndGet()
    x
  }
}
