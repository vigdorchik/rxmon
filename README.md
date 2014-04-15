# rxmon

Provide a way to monitor your application activity through composing *[RxJava](https://github.com/Netflix/RxJava)* Observable stream. *[Akka](http://akka.io)* actors may send events to monitor, and those will be viewed as Observable.

## Details

The defined combinators, that can be used to construct the final monitored Observable, include:

* binary operations <b>+</b>, <b>-</b>, <b>*</b> with a constant or another <i>Observable</i> for <i>Numeric</i> <i>Observable</i>s.

* comparison operations <b> < </b>, <b> > </b> with a constant or another <i>Observable</i> for <i>Numeric</i> <i>Observable</i>s.

* logical operations <b>&&</b>, <b>||</b>, <b>^</b> for two <i>Boolean</i> <i>Observable</i>s.

* <b>max</b>, <b>min</b>, <b>avg</b> over a specified <i>Duration</i> for <i>Numeric</i> <i>Observable</i>s.

* <b>drv</b> giving an <i>Observable</i> of the derivative for <i>Numeric</i> <i>Observable</i>s. This is used to model variable growth.

* <b>always</b> for <i>Boolean</i> <i>Observable</i>s that yields true only if its operand observable is true during parameter <i>Duration</i>.

* <b>count</b> the number of ticks of any <i>Observable</i>.

* <b>watchdog</b> <i>Boolean</i> <i>Observable</i> emitting true when the source <i>Observable</i> doesn't produce values for a specified duration.

```Scala
import org.matmexrhino.rxmon._
import Monitoring._

class MyMonitoring extends Registry {
  val actorErrors: Observable[Unit] = register[Unit]("some_error")
  val tooManyErrors: Observable[Boolean] =
    (count(actorError, 1.minutes) > 100).always(1.minutes)
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

## Batching

To prevent network congestion, it's possible to aggregate statistics on a local node and only
send the results of aggregation. Note, that aggregation naturally smoothens the curve, and prevents
accidental hickups. Batching scheme must be carefully chosen to be consistent with the final rx stream.
The following batching modes are supported:

* <b>max</b>, <b>min</b>, <b>avg</b> for for <i>Numeric</i> variables.
* <b>ever</b>, <b>never</b> for <i>Boolean</i> variables.
* <b>tick</b> counts the number of ticks of <i>Unit</i> variable, and outputs <i>Int</i>.

## Monitoring message queue

In addition to user-defined metrics, it's possible to monitor the health of akka itself:

```Scala
import org.matmexrhino.rxmon.QueueMessages._

target ! Size     // sends the current size of the message queue to target.
target ! Enqueues // sends the number of enqueues in the queue to target.
target ! Dequeues // sends the number of dequeues in the queue to target.
```

In order to be able to transform those messages into numbers, one has to declare the mailbox type and
register it:

```Scala
class MyMailbox(settings: ActorSystem.Settings, config: Config) extends MailboxType {
  def create(owner: Option[ActorRef], system: Option[ActorSystem]) = new MyMessageQueue(system.get)
}
class MyMessageQueue(val system: ActorSystem) extends UnboundedMailbox.MessageQueue with QueueSizeReporter
```
```
akka.actor.default-mailbox {
  mailbox-type = MyMailbox
}
```

## Referencing

This project is published on *[Bintray](https://bintray.com/)*.

To reference from sbt:

```Scala
resolvers += "bintray-vigdorchik" at "http://dl.bintray.com/vigdorchik/maven"

libraryDependencies += "org.matmexrhino" %% "rxmon" % "0.3.0"
```

To reference from maven:

Add the repository to Maven:

```XML
<repository>
  <id>bintray-vigdorchik</id>
  <url>http://dl.bintray.com/vigdorchik/maven</url>
</repository>
```

Resolve the library:

```XML
<dependency>
  <groupId>org.matmerhino</groupId>
  <artifactId>rxmon_2.10</artifactId>
  <version>0.1.0</version>
 </dependency>
```
