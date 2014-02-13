
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

import scala.concurrent.duration._
import scala.math.Numeric
import scala.reflect.ClassTag
import akka.actor.{ Actor, ActorRef, Props }
import rx.lang.scala.{ Observable, Observer, Subscription }

case object ListEntries
case class EntriesResponse(entries: Map[String, ActorRef])

/** Subclass this actor class and register observables you want to collect.
 *  Then each actor that wants to send statistics needs to send ListEntries
 *  and send statistics to the actor it identifies from a map it gets with
 *  EntriesResponse. In addition local batching is supported, so that a proxy
 *  may batch local statistics.
 */
abstract class Registry extends Actor {
  private var monitors = Map.empty[String, ActorRef]

  def receive = {
    case ListEntries => sender ! EntriesResponse(monitors)
  }

  protected def register[T: ClassTag](name: String): Observable[T] =
    Observable create { observer =>
      val tag = implicitly[ClassTag[T]]
      val monitor = context.actorOf(Props(new Monitor[T](observer, tag)), name)
      monitors = monitors + (name -> monitor)
      Subscription {
        context stop monitor
        monitors = monitors - name
      }
    }

  private class Monitor[T](observer: Observer[T], val ct: ClassTag[T]) extends Actor with PrimitiveBoxer[T] {
    def receive: Receive = {
      case boxedTag(v) => observer onNext v.asInstanceOf[T]
    }
  }
}

trait PrimitiveBoxer[T] {
  def ct: ClassTag[T]

  val boxedTag =
     ct match {
       case `ClassTag`.`Byte` => ClassTag(classOf[java.lang.Byte])
       case `ClassTag`.`Char` => ClassTag(classOf[java.lang.Character])
       case `ClassTag`.`Short` => ClassTag(classOf[java.lang.Short])
       case `ClassTag`.`Int` => ClassTag(classOf[java.lang.Integer])
       case `ClassTag`.`Long` => ClassTag(classOf[java.lang.Long])
       case `ClassTag`.`Float` => ClassTag(classOf[java.lang.Float])
       case `ClassTag`.`Double` => ClassTag(classOf[java.lang.Double])
       case `ClassTag`.`Boolean` => ClassTag(classOf[java.lang.Boolean])
       case `ClassTag`.`Unit` => ClassTag(classOf[scala.runtime.BoxedUnit])
       case _ => ct
     }
}

/* Batching requests from local actors. Statistics is aggregated and sent to target identified
   from the registry after the specified duration. 'avg', 'min' and 'max' aggregation is provided
   for numerics. Unit is aggregated to Int, and Boolean is aggregated according to natural
   operations. */

case class BatchContext(val d: FiniteDuration, val registry: ActorRef, val id: String)

abstract class Batcher[From: ClassTag, Run, To](c: BatchContext) extends Actor with PrimitiveBoxer[From] {
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

  registry ! ListEntries

  def receive: Receive = {
    case boxedTag(v) =>
      curr = aggregate(v, curr)
    case EntriesResponse(map) =>
      map.get(id) match {
	case None =>
	  sys.error(s"Couldn't find the target to send to for $id")
	case Some(target) =>
	  import scala.concurrent.ExecutionContext.Implicits.global // TODO: revise context usage here.
	  context.system.scheduler.schedule(0.milliseconds, d)(send(target))
      }
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
