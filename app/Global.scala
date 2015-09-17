import javax.inject.Inject

import actors.ClusterListener
import akka.actor.{ActorSystem, Props}
import play.api.Application

class Global @Inject()(val system: ActorSystem, val application: Application) {
    if (!play.api.Play.isTest(application)) {
      system.actorOf(Props[ClusterListener], "cluster-listener")
    }
}
