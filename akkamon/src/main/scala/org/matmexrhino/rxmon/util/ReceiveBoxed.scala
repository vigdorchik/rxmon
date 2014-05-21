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
package org.maxmexrhino.rxmon.util

import scala.reflect.ClassTag

trait ReceiveBoxed[T] {
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
