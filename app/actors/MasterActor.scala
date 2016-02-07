package actors

import akka.actor.{ActorLogging, Actor}

abstract class MasterActor extends Actor with ActorLogging {
  var master = false

  override def preStart(): Unit = {
    context.system.eventStream.subscribe(self, classOf[MasterEvent])
    context.system.eventStream.subscribe(self, classOf[SlaveEvent])

    context.system.eventStream.publish(MasterStateInquiry())
  }

  final override def receive: Receive = {
    case masterEvent: MasterEvent =>
      if (!master) {
        master = true
        log.info("Turning master")
        turningMaster()
      }
    case slaveEvent: SlaveEvent =>
      if (master) {
        master = true
        log.info("Turning slave")
        turningSlave()
      }
    case _ =>
      if (master) {
        receiveAsMaster()
      }
  }

  def turningMaster() = {
  }

  def turningSlave() = {
  }

  def receiveAsMaster: Receive
}
