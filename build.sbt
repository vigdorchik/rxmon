organization := "org.matmexrhino"

name := "rxmon"

scalaVersion := "2.10.2"

libraryDependencies ++= Seq(
    "com.netflix.rxjava" % "rxjava-scala" % "0.15.1",
    "org.scalatest" %% "scalatest" % "1.9.1" % "test",
    "junit" % "junit" % "4.10" % "test",
    "com.typesafe.akka" %% "akka-actor" % "2.2.3",
    "com.typesafe.akka" %% "akka-testkit" % "2.2.3" % "test"
)
