package org.maxmexrhino.rxmon

import org.junit.runner.RunWith
import org.scalatest.junit.JUnitRunner
import org.junit.Assert._
import org.scalatest.FunSuite
import rx.lang.scala.Observable
import Operations._
import scala.concurrent.duration._

@RunWith(classOf[JUnitRunner])
class OperationsSuite extends FunSuite {
  test("basic") {
    val X: Observable[Int] = Observable(1 to 100)
    val F: Observable[Int] = X * X * 2 + 1
    def f(x: Int) = x * x * 2 + 1
    assertEquals(X.toBlockingObservable.toList map f, F.toBlockingObservable.toList)
  }

  def cut[T](obs: Observable[T], d: Duration): Observable[T] = obs.takeUntil(Observable.interval(d))

  test("binop") {
    val X, Y: Observable[Long] = Observable.interval(100.milliseconds)
    val F: Observable[Long] = cut(X * Y, 1.seconds)
    val l = F.toBlockingObservable.toList.toSet
    val expected = (0 to 9).zip(0 to 9).flatMap {
      case (i, j) => List[Long](i*j, i*(j+1))
    }.toSet
    assertTrue(l forall expected) // Timer might tick for both X and Y at the same time.
  }

  test("stable") {
    val X: Observable[Double] = Observable.interval(50.milliseconds) map (x => math.sin(x.toDouble))
    val F: Observable[Boolean] = cut((X > -1 && X < 1).stable(200.milliseconds), 1.seconds)
    val l = F.toBlockingObservable.toList
    assertTrue(l.size == 1 && l.head)
  }

  test("monotonic") {
    val X: Observable[Long] = Observable.interval(150.milliseconds)
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
    val X: Observable[Double] = Observable.interval(200.milliseconds) map (x => (2*(x % 2) - 1).toDouble)
    val F: Observable[Double] = cut(X.avg(400.milliseconds), 1.seconds)
    val l = F.toBlockingObservable.toList
    assertTrue(l.forall(x => math.abs(x) < 0.0001))
  }

  test("count") {
    val X: Observable[Unit] = Observable.interval(100.milliseconds) map (_ => ())
    val F: Observable[Int] =  cut(X.count(400.milliseconds), 1.seconds)
    val l = F.toBlockingObservable.toList
    assertTrue(l.forall(_ <= 5)) // Additional element might come due to timer imprecision.
  }

  test("diff") {
    val X: Observable[Long] = Observable.interval(100.milliseconds)
    val F: Observable[Double] =  cut(X.diff(), 1.seconds)
    val l = F.toBlockingObservable.toList
    assertTrue(l.forall(x => x > 8.0 && x < 12.0))
  }
}
