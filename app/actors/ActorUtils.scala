package actors

import scala.reflect.NameTransformer
import scala.concurrent._
import scala.concurrent.duration.FiniteDuration
import ExecutionContext.Implicits.global
import akka.actor.ActorSystem
import akka.pattern.after

/**
  * @author Alefas
  * @since  10/12/15
  */
object ActorUtils {
  def encodePath(path: String): String = {
    NameTransformer.encode(path)
  }

  implicit class FutureExtensions[T](f: Future[T]) {
    def withTimeout(implicit duration: FiniteDuration, system: ActorSystem): Future[T] = {
      Future firstCompletedOf Seq(f, after(duration, system.scheduler)(Future.failed(new TimeoutException)))
    }
  }
}
