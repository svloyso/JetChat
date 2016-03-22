package actors

import javax.inject.Singleton

@Singleton()
class OnlineUserRegistry {
  @volatile var userKeys = Set[Long]()
}
