name := "jetchat"

version := "1.0-SNAPSHOT"

scalaVersion := "2.11.5"

libraryDependencies ++= Seq(
  "org.webjars" %% "webjars-play" % "2.3.0-2",
  "org.webjars" % "bootstrap" % "3.3.4",
  "org.webjars" % "jquery" % "2.0.3",
  "org.webjars" % "jquery-ui" % "1.11.4",
  "org.webjars" % "jquery-cookie" % "1.4.1",
  "org.webjars.bower" % "jquery-dateFormat" % "1.0.2",
  "com.typesafe.play" %% "play-slick" % "0.8.1",
  "postgresql" % "postgresql" % "9.1-901.jdbc4",
  "com.github.tminglei" %% "slick-pg" % "0.8.2",
  "com.github.tminglei" %% "slick-pg_joda-time" % "0.6.5.3",
  "com.github.tminglei" %% "slick-pg_play-json" % "0.6.5.3",
  "com.github.tminglei" %% "slick-pg_jts" % "0.6.5.3",
  "com.fasterxml.jackson.module" %% "jackson-module-scala" % "2.5.1",
  "joda-time" % "joda-time" % "2.4",
  "org.apache.commons" % "commons-lang3" % "3.3.2",
  "org.webjars" % "font-awesome" % "4.3.0-1",
  ws
)

unmanagedBase <<= baseDirectory { base => base / "lib" }

fork in Test := false

lazy val root = (project in file(".")).enablePlugins(PlayScala)
