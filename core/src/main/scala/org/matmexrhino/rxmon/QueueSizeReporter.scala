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

import java.util.concurrent.atomic.AtomicLong
import akka.actor._
import akka.dispatch._

object QueueMessages {
  trait QueueMessage

  /**
   * Sends of this object result in sending the size of the message queue to the
   * specified target.
   */
  case object Size extends QueueMessage

  /**
   * Sends of this object result in sending the number of enqueues to the specified target.
   */
  case object Enqueues extends QueueMessage

  /**
   * Sends of this object result in sending the number of dequeues to the specified target.
   */
  case object Dequeues extends QueueMessage
}

/**
 * Mixin this trait in order to get the queue tracking its enqueues and dequeues
 * and reporting its size on demand. It's a good idea to have bare Longs
 * prioritized over other messages, but that's not obligatory.
 */
trait QueueSizeReporter extends MessageQueue {
  import QueueMessages._

  def system: ActorSystem

  val enqueues, dequeues = new AtomicLong

  abstract override def enqueue(receiver: ActorRef, handle: Envelope) {
    val enqueuesCount = enqueues.incrementAndGet()
    def dequeuesCount = dequeues.get

    handle match {
      case Envelope(Size, sender) =>
	super.enqueue(receiver, Envelope(enqueuesCount - dequeuesCount, sender, system))
      case Envelope(Enqueues, sender) =>
	super.enqueue(receiver, Envelope(enqueuesCount, sender, system))
      case Envelope(Dequeues, sender) =>
	super.enqueue(receiver, Envelope(dequeuesCount, sender, system))
      case _ =>
	super.enqueue(receiver, handle)
    }
  }

  abstract override def dequeue(): Envelope = {
    val x = super.dequeue()
    if (x ne null) dequeues.incrementAndGet()
    x
  }
}
