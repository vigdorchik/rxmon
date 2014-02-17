
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

import org.maxmexrhino.rxmon.util.ReceiveBoxed
import scala.concurrent.duration._
import scala.math.Numeric
import scala.reflect.ClassTag
import akka.actor.{ Actor, ActorRef, Props }

case class BatchContext(d: FiniteDuration, target: ActorRef)

/**
 * Batching requests from local actors. Statistics is aggregated and sent to target after a specified
 * duration. 'avg', 'min' and 'max' aggregation is provided for numerics. Unit is aggregated to Int,
 * and Boolean is aggregated according to natural operations.
 * */
abstract class Batcher[From: ClassTag, Run, To](c: BatchContext) extends Actor with ReceiveBoxed[From] {
  import c._

  def ct = implicitly[ClassTag[From]]

  protected def aggregate(s: From, t: Run): Run
  protected def zero: Run
  protected def output(r: Run): To

  private var curr: Run = zero

  private def send(target: ActorRef) {
    if (curr != zero) {
      target ! output(curr)
      curr = zero
    }
  }

  import scala.concurrent.ExecutionContext.Implicits.global // TODO: revisit execution context usage here.
  context.system.scheduler.schedule(d, d)(send(target))

  def receive: Receive = {
    case boxedTag(v) =>
      curr = aggregate(v, curr)
  }
}

abstract class IdBatcher[From: ClassTag, To](c: BatchContext) extends Batcher[From, To, To](c) {
  def output(t: To) = t
}

object Batcher {
  def tick(c: BatchContext): IdBatcher[Unit, Int] =
    new IdBatcher[Unit, Int](c) {
      def aggregate(from: Unit, to: Int) = to + 1
      def zero: Int = 0
    }

  def always(c: BatchContext): IdBatcher[Boolean, Boolean] =
    new IdBatcher[Boolean, Boolean](c) {
      def aggregate(from: Boolean, to: Boolean) = from && to
      def zero: Boolean = true
    }

  def ever(c: BatchContext): IdBatcher[Boolean, Boolean] =
    new IdBatcher[Boolean, Boolean](c) {
      def aggregate(from: Boolean, to: Boolean) = from || to
      def zero: Boolean = false
    }

  private def numeric[T: Numeric: ClassTag](c: BatchContext)(f: (Numeric[T], T, T) => T): IdBatcher[T, T] =
    new IdBatcher[T, T](c) {
      private val num = implicitly[Numeric[T]]
      def zero: T = num.zero
      def aggregate(from: T, to: T): T = f(num, from, to)
    }

  def avg[T: Numeric: ClassTag](c: BatchContext): Batcher[T, (T, Int), Double] =
    new Batcher[T, (T, Int), Double](c) {
      private val num = implicitly[Numeric[T]]
      def aggregate(from: T, to: (T, Int)) = (num plus (from, to._1), to._2 + 1)
      def zero: (T, Int) = (num.zero, 0)
      def output(r: (T, Int)): Double = num.toDouble(r._1) / r._2
    }

  def max[T: Numeric: ClassTag](c: BatchContext): IdBatcher[T, T] = numeric(c) { (num, from, to) =>
    num max (from, to)
  }

  def min[T: Numeric: ClassTag](c: BatchContext): IdBatcher[T, T] = numeric(c) { (num, from, to) =>
    num min (from, to)
  }
}
