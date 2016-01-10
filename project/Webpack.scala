import sbt._
import play.sbt.PlayRunHook

object Webpack {
  def apply(base: File): PlayRunHook = {

    object WebpackProcess extends PlayRunHook {

      var webpackRun: Option[Process] = None

      override def beforeStarted(): Unit = {
        val log = ConsoleLogger()
        log.info("Running npm install...")
        npmProcess(base, "install").!

        log.info("Starting webpack --watch")
        webpackRun = Some(webpackProcess(base, "--watch").run())
      }

      override def afterStopped(): Unit = {
        // Stop webpack when play run stops
        webpackRun.foreach(p => p.destroy())
        webpackRun = None
      }

    }

    WebpackProcess
  }

  def webpackCommand(base: File) = Command.args("webpack", "<webpack-command>") { (state, args) =>
    webpackProcess(base, args:_*) !;
    state
  }

  def webpackProcess(base: File, args: String*) = Process("node" :: "node_modules/.bin/webpack" :: args.toList, base)
  def npmProcess(base: File, args: String*) = Process("npm" :: args.toList, base)
}