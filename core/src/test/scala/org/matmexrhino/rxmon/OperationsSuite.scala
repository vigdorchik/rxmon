/**
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

import org.junit.runner.RunWith
import org.scalatest.junit.JUnitRunner
import org.junit.Assert._
import org.scalatest.FunSuite
import rx.lang.scala.Observable
import rx.lang.scala.schedulers.TestScheduler
import Operations._
import scala.collection.mutable.ListBuffer
import scala.concurrent.duration._

@RunWith(classOf[JUnitRunner])
class OperationsSuite extends FunSuite {
  test("basic") {
    val X: Observable[Int] = Observable.from(1 to 100)
    val F: Observable[Int] = X * X * 2 + 1
    def f(x: Int) = x * x * 2 + 1
    assertEquals(X.toBlockingObservable.toList map f, F.toBlockingObservable.toList)
  }

  def withScheduler[T](pre: TestScheduler => Observable[T])(post: List[T] => Unit)(implicit stop: Duration) = {
    val scheduler = TestScheduler()
    val obs = pre(scheduler).takeUntil(Observable.interval(stop, scheduler))
    val l = ListBuffer[T]()
    obs.subscribe (
      onNext = {v =>
        l += v
      },
      onError = { t => },
      onCompleted = {() => }
    )

    val step = 50.milliseconds
    val end = stop + step
    for (_ <- 1 to (end / step).toInt) {
      scheduler.advanceTimeBy(step)
    }
    post(l.toList)
  }

  test("binop") {
    implicit val stop = 1100.milliseconds
    withScheduler { scheduler =>
      val X, Y: Observable[Long] = Observable.interval(100.milliseconds, scheduler)
      X * Y
    } { samples =>
      val expected = (0 to 9).zip(0 to 9).flatMap {
        case (i, j) => List[Long](i*j, i*(j+1))
      }.toSet - 90L
      assertTrue(samples.toSet == expected)
    }
  }

  test("stable") {
    implicit val stop = 1.seconds
    withScheduler { scheduler =>
      val X: Observable[Double] = Observable.interval(50.milliseconds, scheduler) map (x => math.sin(x.toDouble))
      (X > -1 && X < 1).always(200.milliseconds)(scheduler)
    } { samples =>
      assertTrue(samples.nonEmpty && !samples.exists(!_))
    }
  }

  test("jitter") {
    implicit val stop = 1.seconds
    withScheduler { scheduler =>
      val X: Observable[Boolean] = Observable.interval(50.milliseconds, scheduler) map (_ % 2 == 1)
      val t = 200.milliseconds
      (!X.always(t)(scheduler) && !(!X).always(t)).always(t * 2)(scheduler)
    } { samples =>
      // First samples are when things haven't stabilized.
      val drop = samples dropWhile (!_)
      assertTrue(drop.nonEmpty && !drop.exists(!_))
    }
  }

  test("monotonic") {
    implicit val stop = 1.seconds
    def check[T: Ordering](l: List[T]) {
      assertTrue(l.nonEmpty && l == l.sorted)
    }
    def X(scheduler: TestScheduler): Observable[Long] = Observable.interval(150.milliseconds, scheduler)

    withScheduler { scheduler => X(scheduler).avg(400.milliseconds)(scheduler) } (check(_))
    withScheduler { scheduler => X(scheduler).max(400.milliseconds)(scheduler) } (check(_))
    withScheduler { scheduler => X(scheduler).min(400.milliseconds)(scheduler) } (check(_))
  }

  test("avg") {
    implicit val stop = 1.seconds
    withScheduler { scheduler =>
      val X: Observable[Double] = Observable.interval(200.milliseconds, scheduler) map (x => (2*(x % 2) - 1).toDouble)
      X.avg(400.milliseconds)(scheduler)
    } { samples =>
      assertTrue(samples.nonEmpty && samples.forall(x => math.abs(x) < 0.0001))
    }
  }

  test("count") {
    implicit val stop = 1.seconds
    withScheduler { scheduler =>
      val X: Observable[Unit] = Observable.interval(100.milliseconds, scheduler) map (_ => ())
      X.count(400.milliseconds)(scheduler)
    } { samples =>
      assertTrue(samples == List(1, 2, 3, 4, 5, 5, 5, 5, 5))
    }
  }

  test("diff") {
    implicit val stop = 1.seconds
    withScheduler { scheduler =>
      val X: Observable[Long] = Observable.interval(100.milliseconds, scheduler)
      X.diff()(scheduler)
    } { samples =>
      assertTrue(samples.nonEmpty && samples.forall(x => math.abs(x - 10.0)  < 0.0001))
    }
  }
}
