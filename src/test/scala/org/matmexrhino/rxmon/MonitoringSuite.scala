package org.maxmexrhino.rxmon

import org.junit.runner.RunWith
import org.scalatest.junit.JUnitRunner
import org.junit.Assert._
import org.scalatest.FunSuite
import rx.lang.scala.Observable
import Monitoring._

@RunWith(classOf[JUnitRunner])
class MonitoringSuite extends FunSuite {
  test("basic") {
    val X: Observable[Int] = Observable(1 to 10)
    val F: Observable[Int] = X * X
    def f(x: Int) = x * x
    assertEquals(X.toBlockingObservable.toList map f, F.toBlockingObservable.toList)
  }
}
