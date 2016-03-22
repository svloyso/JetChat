package actors

import akka.actor.{ActorLogging, Actor}

trait MasterActor extends Actor with ActorLogging {
  override def preStart(): Unit = {
    context.system.eventStream.subscribe(self, MasterEvent.getClass)
    context.system.eventStream.subscribe(self, SlaveEvent.getClass)
    context.system.eventStream.publish(MasterStateInquiry())
  }

  final override def receive: Receive = receiveMaster orElse receiveAsSlave

  private def receiveMaster: Receive = {
    case SlaveEvent =>
    case MasterEvent =>
      log.info("Turning master")
      context.become(receiveSlave orElse receiveAsMaster)
      turningMaster()
  }

  private def receiveSlave: Receive = {
    case MasterEvent =>
    case SlaveEvent =>
      log.info("Turning slave")
      context.unbecome()
      turningSlave()
  }

  def turningMaster(): Unit = {}
  def turningSlave(): Unit = {}
  def receiveAsSlave: Receive = { case _ => }
  def receiveAsMaster: Receive
}

case object MasterEvent
case object SlaveEvent
