/*
 * @author Eugene Vigdorchik.
 */
package org.maxmexrhino.rxmon

import scala.collection.mutable.Queue
import scala.concurrent.duration._
import scala.math.{Numeric, Ordering}
import rx.lang.scala.{Observable, Observer}

object Monitoring {
  private[Monitoring] sealed trait Ops[T] {
    type Sample = (Long, T)

    def binop[T, R](lop: Observable[T], rop: Observable[T], f: (T, T) => R): Observable[R] =
      if (lop eq rop)
        lop.map(x => f(x, x))
      else
        lop combineLatest rop map f.tupled

    def aggregate[R](op: Observable[T], d: Duration)(f: Seq[Sample] => R): Observable[R] =
      Observable { observer =>
        val millis = d.toMillis
        val startFeed = System.currentTimeMillis + millis
        val probes = Queue[Sample]()

        op.timestamp.subscribe (
          onError = { err => observer.onError(err) },
          onCompleted = { () => observer.onCompleted() },
          onNext = { value =>
            val (now, _) = value
            probes enqueue value
            val start = now - millis
            probes dequeueAll {
              case (ts, _) => ts < start
            }
            if (now >= startFeed) observer onNext f(probes)
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
    def avg(d: Duration): Observable[Double] = aggregate(observable, d) { probes =>
      val area = (0.0 /: probes.zip(probes.tail)) {
        case (res, ((t1, s1), (t2, s2))) =>
          res + (num.toDouble(s1) + num.toDouble(s2)) * (t2 - t1) / 2
      }
      area / d.toMillis
    }

    /**
     * Creates an Observable of the minimum over a certain period.
     */
    def min(d: Duration): Observable[T] = aggregate(observable, d) { probes => probes.min(sampleOrd)._2 }

    /**
     * Creates an Observable of the maximum over a certain period.
     */
    def max(d: Duration): Observable[T] = aggregate(observable, d) { probes => probes.max(sampleOrd)._2 }


    private def sampleOrd: Ordering[Sample] = num.on (_._2)
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
     * Only changes are emitted.
     */
    def stable(d: Duration): Observable[Boolean] = {
      val ticks = aggregate(observable, d) { probes =>
        probes forall (_._2)
      }
      ticks.distinctUntilChanged
    }
  }

  implicit class UnitObservableOps(ticker: Observable[Unit]) extends Ops[Unit] {
    /**
     * Create an Observable of the number of ticks of this ticker in duration.
     */
    def count(d: Duration): Observable[Int] = aggregate(ticker, d) (_.size)
  }

  implicit class UnitObserverOps(val observer: Observer[Unit]) extends AnyVal {
    def tick(): Unit = observer onNext ()
  }

  /*
   * Shorthand for creating const observables.
   */
  def const[T](value: T): Observable[T] = Observable(value)
}
