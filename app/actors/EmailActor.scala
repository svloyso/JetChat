package actors

import models._
import play.api.Application
import play.api.libs.mailer.{Email, MailerClient}

import play.api.Play.current
import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.duration.DurationLong


class EmailActor(val topicsDAO: TopicsDAO, val commentsDAO: CommentsDAO,
                 val directMessagesDAO: DirectMessagesDAO,
                 val mailerClient: MailerClient, val application: Application) extends MasterActor {
  lazy val INTERVAL = application.configuration.getLong("notifications.email.interval").getOrElse(System.getProperty("notifications.email.interval", "900000").toLong)

  override def preStart(): Unit = {
    super.preStart()

    log.info(s"Scheduling for every ${INTERVAL.millisecond.toMinutes} minutes")
    context.system.scheduler.schedule(INTERVAL.millisecond, INTERVAL.millisecond, self, EmailEvent())
  }

  override def receiveAsMaster: Receive = {
    case _ =>
      // TODO
  }

  case class EmailEvent() {
  }

  /*def test(): Unit = {
    val email = Email(
      "Simple email",
      "Mister FROM <from@email.com>",
      Seq("Andrey Cheptsov <andrey.cheptsov@jetbrains.com>"),
      // adds attachment
      // sends text, HTML or both...
      bodyText = Some("A text message"),
      bodyHtml = Some("<html><body><p>An <b>html</b> message</p></body></html>")
    )
    mailerClient.send(email)
  }*/
}