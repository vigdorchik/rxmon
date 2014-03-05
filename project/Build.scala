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
 * limitations under the License. */

import sbt._
import sbt.Keys._

object RxmonBuild extends Build {
  lazy val commonSettings = Defaults.defaultSettings ++ Seq (
    organization := "org.matmexrhino",
    version := "0.2.0",
    scalaVersion := "2.10.3",
    resolvers += "Typesafe Repository" at "http://repo.typesafe.com/typesafe/releases/",
    libraryDependencies ++= Seq(
	"org.scalatest" %% "scalatest" % "1.9.1" % "test",
	"junit" % "junit" % "4.10" % "test"
    ),
    parallelExecution in Test := false
  )

  lazy val rxmon = Project (
    id = "rxmon",
    base = file("."),
    aggregate = Seq(core)
  )

  lazy val core = Project (
    id = "core",
    base = file("core"),
    settings = commonSettings ++ Seq (
      name := "rxmon-core",
      libraryDependencies ++= Seq(
	"com.netflix.rxjava" % "rxjava-scala" % "0.17.0-RC6",
	"com.typesafe.akka" %% "akka-actor" % "2.3.0",
	"com.typesafe.akka" %% "akka-testkit" % "2.3.0" % "test"
      )
    )
  )

  lazy val benchmarks = Project (
    id = "benchmarks",
    base = file("benchmarks"),
    settings = commonSettings ++ Seq (
      publishArtifact := false
    ),
    dependencies = Seq(core)
  )
}
