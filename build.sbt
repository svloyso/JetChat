import play.PlayImport.PlayKeys.playRunHooks

name := "jetchat"

version := System.getProperty("build.number", "0.1-SNAPSHOT")

organization := "com.jetbrains"

scalaVersion := "2.11.7"

resolvers += "Akka Snapshot Repository" at "http://repo.akka.io/snapshots/"

resolvers += "scalaz-bintray" at "http://dl.bintray.com/scalaz/releases"

libraryDependencies ++= Seq(
  "com.typesafe.play" %% "play-slick" % "1.0.1",
  "com.typesafe.play" %% "play-slick-evolutions" % "1.0.1",
  specs2 % Test,
  evolutions,
  ws,
  "org.reflections" % "reflections" % "0.9.10",
  "com.google.inject.extensions" % "guice-multibindings" % "4.0",
  "mysql" % "mysql-connector-java" % "5.1.36",
  "com.h2database" % "h2" % "1.4.189",
  "com.typesafe.akka" %% "akka-cluster" % "2.4-SNAPSHOT",
  "com.typesafe.akka" %% "akka-cluster-tools" % "2.4-SNAPSHOT",
  "org.mousio" % "etcd4j" % "2.7.0",
  "com.fasterxml.jackson.jaxrs" % "jackson-jaxrs-json-provider" % "2.5.4",
  "org.apache.commons" % "commons-lang3" % "3.3.2",
  "com.papertrailapp" % "logback-syslog4j" % "1.0.0"
)

unmanagedBase <<= baseDirectory { base => base / "lib" }

fork in Test := false
javaOptions in Test += "-Dconfig.file=conf/application.test.conf"

lazy val root = (project in file(".")).enablePlugins(PlayScala, DockerPlugin)

playRunHooks <+= baseDirectory.map(Webpack.apply)

lazy val webpack = taskKey[Unit]("Running webpack when packaging the application...")

def runWebpack(file: File) = {
  Process("npm install", file) !
}

webpack := {
  if (runWebpack(baseDirectory.value) != 0) throw new Exception("Something goes wrong when running webpack")
}

dist <<= dist dependsOn webpack

stage <<= stage dependsOn webpack

routesGenerator := InjectedRoutesGenerator

dockerBaseImage := "java:8u45"

maintainer := "Andrey Cheptsov <andrey.cheptsov@jetbrains.com>"

dockerExposedPorts in Docker := Seq(8080)