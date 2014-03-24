/*
 * Copyright 2014 Eugene Vigdorchik.
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
package org.maxmexrhino.rxmon.util

import scala.reflect.ClassTag

class RoundRobbin[T: ClassTag] {
  var top: Int = 0
  var bottom: Int = 0

  var size = 0
  var store: Array[T] = new Array(20)

  def dropWhile(p: T => Boolean) {
    while (size > 0 && p(store(bottom))) {
      bottom = (bottom + 1) % store.size
      size -= 1
    }
  }

  def addLast(t: T) {
    if (top == bottom && size > 0) {
      grow()
      addLast(t)
    } else {
      store(top) = t
      top = (top + 1) % store.size
      size += 1
    }
  }

  // precondition: top == bottom
  private def grow() {
    val newSize = size * 2
    val newStore = new Array[T](newSize)
    Array.copy(store, bottom, newStore, 0, size - bottom)
    Array.copy(store, 0, newStore, size - bottom, bottom)
    bottom = 0
    top = size
    store = newStore
  }

  // precondition: nonempty
  def sequence: Seq[T] =
    if (top > bottom)
      store.view.slice(bottom, top)
    else
      store.view.slice(bottom, store.size) ++ store.view.slice(0, top)
}
