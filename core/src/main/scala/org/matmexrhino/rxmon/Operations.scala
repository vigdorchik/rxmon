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
 * limitations under the License. */
package org.maxmexrhino.rxmon

import scala.concurrent.duration._
import scala.math.{Numeric, Ordering}
import rx.lang.scala._
import java.util.concurrent.TimeUnit

object Operations {
  private[Operations] trait Ops[T] {
    type Sample = (Long, T)

    def binop[T, R](lop: Observable[T], rop: Observable[T], f: (T, T) => R): Observable[R] =
      if (lop eq rop)
        lop.map(x => f(x, x))
      else
        lop combineLatest (rop, f)

    def aggregate[R](op: Observable[T], d: Duration, s: Scheduler)(f: Seq[Sample] => R): Observable[R] =
      Observable { observer =>
        val millis = d.toMillis
        val probes = new util.RoundRobbin[Sample]()

        op.timestamp(s).subscribe (
          onError = { err => observer.onError(err) },
          onCompleted = { () => observer.onCompleted() },
          onNext = { value =>
            val (now, _) = value
            val start = now - millis
            probes dropWhile {
              case (ts, _) => ts < start
            }
            probes addLast value
            observer onNext f(probes.sequence)
          }
        )
      }
  }

  implicit class NumericObservableOps[T: Numeric](observable: Observable[T]) extends Ops[T] {
    lazy val num = implicitly[Numeric[T]]

    /**
     * Creates an Observable of the sum of 2 observables.
     */
    def +(that: Observable[T]): Observable[T] =
      binop(observable, that, num.plus _)
    def +[V <% T](v: V): Observable[T] = observable map (num.plus(_, v))

    /**
     * Creates an Observable of the difference of 2 observables.
     */
    def -(that: Observable[T]): Observable[T] =
      binop(observable, that, num.minus _)
    def -[V <% T](v: V): Observable[T] = observable map (num.minus(_, v))

    /**
     * Creates an Observable of the product of 2 observables.
     */
    def *(that: Observable[T]): Observable[T] =
      binop(observable, that, num.times _)
    def *[V <% T](v: V): Observable[T] = observable map (num.times(_, v))

    /**
     * Creates an Observable of the fact that (observable < that).
     */
    def <(that: Observable[T]): Observable[Boolean] =
      binop(observable, that, num.lt _)
    def <[V <% T](v: V): Observable[Boolean] = observable map (num.lt(_, v))

    /**
     * Creates an Observable of the fact that (observable > that).
     */
    def >(that: Observable[T]): Observable[Boolean] =
      binop(observable, that, num.gt _)
    def >[V <% T](v: V): Observable[Boolean] = observable map (num.gt(_, v))


    /**
     * Creates an Observable of the average over a certain period.
     */
    def avg(d: Duration)(implicit s: Scheduler): Observable[Double] =
      aggregate(observable, d, s) { probes =>
	val area = (0.0 /: probes.zip(probes.tail)) {
          case (res, ((t1, s1), (t2, s2))) =>
            res + (num.toDouble(s1) + num.toDouble(s2)) * (t2 - t1) / 2
	}
	area / d.toMillis
      }

    /**
     * Creates an Observable of the minimum over a certain period.
     */
    def min(d: Duration)(implicit s: Scheduler): Observable[T] =
      aggregate(observable, d, s) { probes => probes.min(sampleOrd)._2 }

    /**
     * Creates an Observable of the maximum over a certain period.
     */
    def max(d: Duration)(implicit s: Scheduler): Observable[T] =
      aggregate(observable, d, s) { probes => probes.max(sampleOrd)._2 }

    private def sampleOrd: Ordering[Sample] = num.on (_._2)

    /**
     * Create an Observable of the derivative of the source observable over time.
     * @param unit TimeUnit to measure the time. Units not less than milliseconds are supported.
     */
    def drv(unit: TimeUnit = TimeUnit.SECONDS)(implicit s: Scheduler): Observable[Double] =
      Observable { observer =>
        val mult: Double = 1.0 / TimeUnit.MILLISECONDS.convert(1, unit)
	var prevSample: Option[Sample] = None
	observable.timestamp(s).subscribe (
	  onError = { err => observer.onError(err) },
	  onCompleted = { () => observer.onCompleted() },
	  onNext = {
	    case curr@(t2, v2) =>
	      for ((t1, v1) <- prevSample) {
		val dx = num.toDouble(num.minus(v2, v1))
		val dt = (t2 - t1) * mult
		observer.onNext(dx / dt)
	      }
	      prevSample = Some(curr)
	  }
	)
      }
  }

  implicit class BooleanObservableOps(observable: Observable[Boolean]) extends Ops[Boolean] {
    /**
     * Creates an Observable that yields true iff both arguments are true.
     */
    def &&(that: Observable[Boolean]): Observable[Boolean] =
      binop[Boolean, Boolean](observable, that, (_ && _))

    /**
     * Creates an Observable that yields true iff any of its two argumenta is true.
     */
    def ||(that: Observable[Boolean]): Observable[Boolean] =
      binop[Boolean, Boolean](observable, that, (_ || _))

    /**
     * Creates an Observable that yields true iff arguments are not the same.
     */
    def ^(that: Observable[Boolean]): Observable[Boolean] =
      binop[Boolean, Boolean](observable, that, (_ ^ _))

    /**
     * Creates an Observable that yields true iff observable stays true for a specified period.
     */
    def always(d: Duration)(implicit s: Scheduler): Observable[Boolean] =
      aggregate(observable, d, s) (_ forall (_._2))

    /**
     * Subscribe to observable with action executed only when the condition is true.
     */
    def whenTrue(action:  () => Unit): Unit = observable.subscribe (if (_) action())

    /*
     * Negate the given observable.
     * For example, jittering can be expressed as follows:
     * {{{
     *   (!X.always(t1) && !(!X).always(t1)).always(t2)
     * }}}
     */
    def unary_!(): Observable[Boolean] = observable map (!_)
  }

  implicit class AnyObservableOps(ticker: Observable[Any]) extends Ops[Any] {
    /**
     * Create an Observable of the number of ticks of this ticker in duration.
     */
    def count(d: Duration)(implicit s: Scheduler): Observable[Int] = aggregate(ticker, d, s) (_.size)

    /**
     * Create an Observable emitting true when source Observable doesn't tick
     * for a specified duration.
     */
    def watchdog(d: Duration)(implicit s: Scheduler): Observable[Boolean] = {
      val m = Observable.interval(d, s) drop 1 merge ticker.map(_ => -1L)
      val w = m window (d, s) map (_ take 1)
      w.flatten map (_ != -1L)
    }
  }

  /*
   * Shorthand for creating const observables.
   */
  def const[T](value: T): Observable[T] = Observable items (value)

  /**
   * Implicit scheduler compatible with java timestamp implementation.
   */
  implicit lazy val immediateScheduler = schedulers.ImmediateScheduler()
}
