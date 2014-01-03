# rxmon

Provide a way to monitor your application activity through composing *[RxJava](https://github.com/Netflix/RxJava)* Observable stream. *[Akka](http://akka.io)* actors may send events to monitor, and those will be viewed as Observable.

## Details

The defined combinators, that can be used to construct the final monitored Observable, include:

* binary operations <b>+</b>, <b>-</b>, <b>*</b> with a constant or another <i>Observable</i> for <i>Numeric</i> <i>Observable</i>s.

* comparison operations <b> < </b>, <b> > </b> with a constant or another <i>Observable</i> for <i>Numeric</i> <i>Observable</i>s.

* logical operations <b>&&</b>, <b>||</b>, <b>^</b> for two <i>Boolean</i> <i>Observable</i>s.

* <b>max</b>, <b>min</b>, <b>avg</b> over a specified <i>Duration</i> for <i>Numeric</i> <i>Observable</i>s.

* <b>diff</b> giving an <i>Observable</i> of relative differences for <i>Numeric</i> <i>Observable</i>s. This is used to model variable growth.

* <b>stable</b> for <i>Boolean</i> <i>Observable</i>s that yields true only if its operand observable is true during parameter <i>Duration</i>.

* <b>count</b> the number of ticks of <i>Unit</i> <i>Observable</i>.

```Scala
import org.matmexrhino.rxmon._
import Monitoring._

class MyMonitoring extends Registry {
  val actorErrors: Observable[Unit] = register[Unit]("some_error")
  val tooManyErrors: Observable[Boolean] =
    (count(actorError, 1.minutes) > 100).stable(1.minutes)
  tooManyErrors.whenTrue { () =>
    // alert or something else
  }
}

...

// Some actor that might err:
registry ! ListEntries 
def receive = {
  ...
  case EntriesResponse(map) =>
    val errorsCollector = map("some_error")
    ...
}
```