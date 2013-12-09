package org.maxmexrhino.rxmon

import org.junit.runner.RunWith
import org.scalatest.junit.JUnitRunner
import org.junit.Assert._
import org.scalatest.FunSuite
import rx.lang.scala.Observable
import Monitoring._
import scala.concurrent.duration._

@RunWith(classOf[JUnitRunner])
class MonitoringSuite extends FunSuite {
  test("basic") {
    val X: Observable[Int] = Observable(1 to 100)
    val F: Observable[Int] = X * X * 2 + 1
    def f(x: Int) = x * x * 2 + 1
    assertEquals(X.toBlockingObservable.toList map f, F.toBlockingObservable.toList)
  }

  test("binop") {
    val range = 1 to 10
    val X, Y: Observable[Int] = Observable(range)
    val F: Observable[Int] = X * Y
    // Range is propagated (deterministically) for one multiplier only. Why?
    assertEquals(range map (_ * range.last), F.toBlockingObservable.toList)
  }

  def cut[T](obs: Observable[T], d: Duration): Observable[T] = obs.takeUntil(Observable.interval(d))

  test("stable") {
    val X: Observable[Double] = Observable.interval(50.milliseconds) map (x => math.sin(x.toDouble))
    val F: Observable[Boolean] = cut((X > -1 && X < 1).stable(200.milliseconds), 1.seconds)
    val l = F.toBlockingObservable.toList
    assertTrue(l.size == 1 && l.head)
  }

  test("monotonic") {
    val X: Observable[Long] = Observable.interval(50.milliseconds)
    val F: Observable[Double] = cut(X.avg(400.milliseconds), 1.seconds)
    val G: Observable[Long] = cut(X.min(400.milliseconds), 1.seconds)
    val H: Observable[Long] = cut(X.max(400.milliseconds), 1.seconds)

    def check[T: Ordering](obs: Observable[T]) {
      val l = obs.toBlockingObservable.toList
      assertTrue(l == l.sorted)
    }
    check(F)
    check(G)
    check(H)
  }

  test("avg") {
    val X: Observable[Double] = Observable.interval(50.milliseconds) map (x => (2*(x % 2) - 1).toDouble)
    val F: Observable[Double] = cut(X.avg(200.milliseconds), 1.seconds)
    val l = F.toBlockingObservable.toList
    assertTrue(l.forall(x => math.abs(x) < 0.1))
  }
}
