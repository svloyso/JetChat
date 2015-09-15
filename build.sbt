name := "jetchat"

version := System.getProperty("build.number", "0.1-SNAPSHOT")

organization := "com.jetbrains"

scalaVersion := "2.11.5"

resolvers += "Akka Snapshot Repository" at "http://repo.akka.io/snapshots/"

resolvers += "scalaz-bintray" at "http://dl.bintray.com/scalaz/releases"

libraryDependencies ++= Seq(
  "com.typesafe.play" %% "play-slick" % "1.0.1",
  "com.typesafe.play" %% "play-slick-evolutions" % "1.0.1",
  specs2 % Test,
  evolutions,
  "mysql" % "mysql-connector-java" % "5.1.36",
  "com.h2database" % "h2" % "1.4.189",
  "com.typesafe.akka" %% "akka-cluster" % "2.4-SNAPSHOT",
  "com.typesafe.akka" %% "akka-cluster-tools" % "2.4-SNAPSHOT",
  "org.mousio" % "etcd4j" % "2.7.0",
  "com.fasterxml.jackson.jaxrs" % "jackson-jaxrs-json-provider" % "2.5.4"
)

unmanagedBase <<= baseDirectory { base => base / "lib" }

fork in Test := false

lazy val root = (project in file(".")).enablePlugins(PlayScala, DockerPlugin)

routesGenerator := InjectedRoutesGenerator

dockerBaseImage := "java:8u45"

maintainer := "Andrey Cheptsov <andrey.cheptsov@jetbrains.com>"

dockerExposedPorts in Docker := Seq(8080)