package org.maxmexrhino.rxmon

import org.junit.runner.RunWith
import org.scalatest.junit.JUnitRunner
import org.junit.Assert._
import org.scalatest.FunSuite
import rx.lang.scala.Observable
import rx.lang.scala.subscriptions.BooleanSubscription
import Monitoring._

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
    val X, Y: Observable[Int] = Observable { observer =>
      for (i <- range) observer.onNext(i)
      observer.onCompleted()
      BooleanSubscription()
    }
    val F: Observable[Int] = X * Y
    // Range is propagated (deterministically) for one multiplier only. Why?
    assertEquals(range map (_ * range.last), F.toBlockingObservable.toList)
  }
}
