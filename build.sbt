import NativePackagerKeys._ // with auto plugins this won't be necessary soon

name := "jetchat"

version := System.getProperty("build.number", "0.1-SNAPSHOT")

organization := "jetbrains.com"

scalaVersion := "2.11.5"

libraryDependencies ++= Seq(
  "org.webjars" %% "webjars-play" % "2.3.0-2",
  "org.webjars" % "bootstrap" % "3.3.4",
  "org.webjars" % "jquery" % "2.0.3",
  "org.webjars" % "jquery-cookie" % "1.4.1",
  "org.webjars.bower" % "jquery-dateFormat" % "1.0.2",
  "com.typesafe.play" %% "play-slick" % "0.8.1",
  "mysql" % "mysql-connector-java" % "5.1.36",
  "joda-time" % "joda-time" % "2.4",
  "org.apache.commons" % "commons-lang3" % "3.3.2",
  "org.webjars" % "font-awesome" % "4.3.0-1",
  ws,
  "org.joda" % "joda-convert" % "1.6",
  "com.github.tototoshi" %% "slick-joda-mapper" % "1.2.0"
)

unmanagedBase <<= baseDirectory { base => base / "lib" }

fork in Test := false

lazy val root = (project in file(".")).enablePlugins(PlayScala, DockerPlugin)

dockerBaseImage := "java:8u45"

maintainer := "Andrey Cheptsov <andrey.cheptsov@jetbrains.com>"

dockerExposedPorts in Docker := Seq(9000, 9000)