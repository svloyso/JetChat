import actors.ClusterListener
import akka.actor.Props
import play.api.libs.concurrent.Akka
import play.api.{Application, GlobalSettings}
import play.api.Play.current

object Global extends GlobalSettings {
  override def onStart(app: Application): Unit = {
    if (!play.api.Play.isTest(play.api.Play.current)) {
      Akka.system.actorOf(Props[ClusterListener], "cluster-listener")
    }
  }
}
