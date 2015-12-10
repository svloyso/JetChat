package actors

import scala.reflect.NameTransformer

/**
  * @author Alefas
  * @since  10/12/15
  */
object ActorUtils {
  def encodePath(path: String): String = {
    NameTransformer.encode(path)
  }
}
