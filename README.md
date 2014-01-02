# rxmon

Provide a way to monitor your application activity through composing *[RxJava](https://github.com/Netflix/RxJava)* Observable stream. *[Akka](http://akka.io)* actors may send events to monitor, and those will be viewed as Observable.

## Details

```Scala
import org.matmexrhino.rxmon._
import Monitoring._

class MyMonitoring extends Registry {
  val actorErrors: Observable[Unit] = register[Unit]("some_error")
  val tooManyErrors: Observable[Boolean] =
    (count(actorError, 1.minutes) > 100).stable(1.minutes)
  tooManyErrors.subscribe { error =>
    if (error) {
      // alert or something else
    }
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