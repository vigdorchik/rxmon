organization := "org.matmexrhino"

version := "0.1.0"

name := "rxmon"

scalaVersion := "2.10.3"

resolvers += "Typesafe Repository" at "http://repo.typesafe.com/typesafe/releases/"

libraryDependencies ++= Seq(
    "com.netflix.rxjava" % "rxjava-scala" % "0.16.0",
    "org.scalatest" %% "scalatest" % "1.9.1" % "test",
    "junit" % "junit" % "4.10" % "test",
    "com.typesafe.akka" %% "akka-actor" % "2.2.3",
    "com.typesafe.akka" %% "akka-testkit" % "2.2.3" % "test"
)
