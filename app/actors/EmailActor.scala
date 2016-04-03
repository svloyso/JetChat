package actors

import java.sql.Timestamp
import java.text.SimpleDateFormat
import java.util.{Calendar, Date}

import akka.actor.Cancellable
import models._
import play.api.Application
import play.api.libs.mailer.{Email, MailerClient}

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.duration.DurationLong


class EmailActor(val topicsDAO: TopicsDAO, val commentsDAO: CommentsDAO,
                 val directMessagesDAO: DirectMessagesDAO, val usersDAO: UsersDAO,
                 val mailerClient: MailerClient, val application: Application) extends MasterActor {
  lazy val INTERVAL = application.configuration.getLong("notifications.email.interval").getOrElse(System.getProperty("notifications.email.interval", "900000").toLong)
  lazy val SHIFT = application.configuration.getLong("notifications.email.shift").getOrElse(System.getProperty("notifications.email.shift", "300000").toLong)

  val DATE_FORMAT = new SimpleDateFormat("MMM d, yyyy")
  val SHORT_DATE_FORMAT = new SimpleDateFormat("MMM d")
  val TIME_FORMAT = new SimpleDateFormat("HH:mm")

  val NO_AVATAR = "https://avatars3.githubusercontent.com/u/4244988?v=3&s=72"

  var lastSince = 0L

  var task: Option[Cancellable] = None

  override def preStart(): Unit = {
    super.preStart()
  }

  override def turningMaster(): Unit = {
    if (task.isEmpty || task.get.isCancelled) {
      task = Some(context.system.scheduler.schedule(0L.millisecond, INTERVAL.millisecond, self, EmailEvent))
    }
  }


  override def turningSlave(): Unit = {
    if (task.isDefined) {
      task.get.cancel()
      task = None
    }
  }

  override def receiveAsMaster: Receive = {
    case EmailEvent =>
      val date = Calendar.getInstance()
      date.add(Calendar.MINUTE, - INTERVAL.milliseconds.toMinutes.toInt - SHIFT.milliseconds.toMinutes.toInt)
      val since = Math.max(lastSince, date.getTimeInMillis)
      date.add(Calendar.MINUTE, INTERVAL.milliseconds.toMinutes.toInt)
      val to = date.getTimeInMillis
      val sinceDate = new Timestamp(since)
      val toDate = new Timestamp(to)
      log.debug("Sending out unread messages from " + TIME_FORMAT.format(sinceDate) + " to " + TIME_FORMAT.format(toDate))
      lastSince = to
      usersDAO.all.map { case users =>
          users.map { case user =>
            if (user.email.isDefined) {
              directMessagesDAO.getUnreadMessages(user.id, sinceDate, toDate).map { case um =>
                if (um.nonEmpty) {
                  val allMessagesBlock = (um.groupBy { case (u, m) => u } map { case (fromUser, messages) =>
                    val userMessagesBlock = messages.map { case (u, m) =>
                      buildHtmlMessageTemplate(m)
                    }.mkString
                    buildHtmlUserMessagesTemplate(user, fromUser, messages.head._2, userMessagesBlock)
                  }).mkString
                  val email = Email(
                    "Notifications from JetChat for " + DATE_FORMAT.format(toDate) + " at "  + TIME_FORMAT.format(toDate),
                    "JetChat <noreply@chat.services.jetbrains.com>",
                    Seq(user.name + "<" + user.email.get + ">"),
                    // bodyText = Some(""),
                    bodyHtml = Some(buildHtmlBodyTemplate(user.name, allMessagesBlock))
                  )
                  mailerClient.send(email)
                }
              }
            }
          }
      }
  }

  def buildHtmlUserMessagesTemplate(user: User, fromUser: User, message: DirectMessage, userMessagesBlock: String): String = {
    "<div style=\"margin:0 0 10px;width:100%;word-break:break-word;clear:left;font-size:13.2px;line-height:18px;font-weight: 500px\">\n                                                            <a href=\"https://chat.services.jetbrains.com/?userId=" + fromUser.id + "\"\n                                                               style=\"color:#439fe0;font-weight:bold;text-decoration:none;word-break:break-word\"\n                                                               target=\"_blank\"><img\n                                                                    src=\"" + fromUser.avatar.getOrElse(NO_AVATAR) + "\"\n                                                                    style=\"outline:none;text-decoration:none;border:none;float:left;border-radius:4px;display:inline-block;width:36px;min-height:36px\"\n                                                                    class=\"CToWUd\"></a>\n                                                            <div style=\"padding-left:44px;\">\n                                                                <a href=\"https://chat.services.jetbrains.com/?userId=" + fromUser.id + "\"\n                                                                   style=\"color:#8c8c8c;font-weight: 500;text-decoration:none;word-break:break-word\"\n                                                                   target=\"_blank\">" + user.name + "</a>\n                                                                <a href=\"https://chat.services.jetbrains.com/?userId=" + fromUser.id + "\"\n                                                                   style=\"color:#8c8c8c;font-weight:normal;text-decoration:none;word-break:break-word;font-size:13.2px;white-space:nowrap\"\n                                                                   target=\"_blank\">" + TIME_FORMAT.format(message.date) + ", " + SHORT_DATE_FORMAT.format(message.date) +  "</a><br>" + userMessagesBlock + "</div>\n                                                        </div>"
  }

  def buildHtmlMessageTemplate(m: DirectMessage): String = {
    "<p style=\"padding: 3px 0; margin: 0 auto\">" + m.text + "</p>"
  }

  def buildHtmlBodyTemplate(name: String, messagesBlock: String): String = {
    "<html><body><div style=\"background:#f5f5f5;color:#373737;font-family:'Helvetica Neue',Helvetica,Arial,sans-serif;max-width:100%;width:100%!important;margin:0 auto;padding:0\">\n    <table width=\"100%\" cellpadding=\"0\" cellspacing=\"0\" border=\"0\"\n           style=\"border-collapse:collapse;margin:0;padding:0;width:100%;\">\n        <tbody>\n            <tr>\n                <td valign=\"top\" style=\"border-collapse:collapse\">\n                    <table width=\"100%\" cellpadding=\"0\" cellspacing=\"0\" border=\"0\" style=\"border-collapse:collapse\">\n                        <tbody>\n                            <tr>\n                                <td valign=\"bottom\" style=\"border-collapse:collapse;padding:20px 16px 12px\">\n                                    <div style=\"text-align:center\">\n                                        <a href=\"https://chat.services.jetbrains.com\"\n                                           target=\"_blank\">\n                                            <table style=\"display: inline-block; \">\n                                                <tr>\n                                                    <td><img\n                                                            src=\"https://chat.services.jetbrains.com/assets/images/favicon.png\"\n                                                            width=\"48px\"/>\n                                                    </td>\n                                                    <td>&nbsp;</td>\n                                                    <td>\n                                                        <span style=\"color:#333333;font-weight:bold;text-decoration:none;font-size: 15pt;word-break:break-word\">JetChat</span>\n                                                    </td>\n                                                </tr>\n                                            </table>\n                                        </a>\n                                    </div>\n                                </td>\n                            </tr>\n                        </tbody>\n                    </table>\n                </td>\n            </tr>\n            <tr>\n                <td valign=\"top\" style=\"border-collapse:collapse\">\n\n                    <table cellpadding=\"32\" cellspacing=\"0\" border=\"0\" align=\"center\"\n                           style=\"border-collapse:collapse;background:white;border-radius:0.2rem;margin-bottom:1rem\">\n                        <tbody>\n                            <tr>\n                                <td width=\"546\" valign=\"top\" style=\"border-collapse:collapse\">\n                                    <div style=\"max-width:600px;margin:0 auto\">\n                                        <h3 style=\"color:#333333;line-height:24px;margin-bottom:12px;margin:0 0 12px\">Hi\n                                            " + name + ",</h3>\n\n                                        <p style=\"font-size:14px;line-height:24px;margin:0 0 16px\">\n                                            You have unread messages:\n                                        </p>\n\n                                        <table width=\"100%\" cellpadding=\"0\" cellspacing=\"0\" border=\"0\" align=\"center\"\n                                               style=\"border-collapse:collapse\">\n                                            <tbody>\n                                                <tr>\n                                                    <td>\n                                                        " + messagesBlock + "\n                                                    </td>\n                                                </tr>\n                                            </tbody>\n                                        </table>\n                                    </div>\n                                </td>\n                            </tr>\n                        </tbody>\n                    </table>\n\n                    <table cellpadding=\"32\" cellspacing=\"0\" border=\"0\" align=\"center\"\n                           style=\"border-collapse:collapse;background:white;border-radius:0.5rem;margin-bottom:1rem\">\n                        <tbody>\n                            <tr>\n                                <td width=\"546\" valign=\"top\" style=\"border-collapse:collapse\">\n                                    <div style=\"max-width:600px;margin:0 auto\">\n                                        <p style=\"font-size:14px;line-height:14px;color:#aaa;text-align:left;max-width:100%;word-break:break-word;margin: 0 auto;\">\n                                            To turn off email notifications, see your <a\n                                                style=\"color:#0077cc; text-decoration: none;\" target=\"_blank\"\n                                                href=\"https://chat.services.jetbrains.com/?settings=true\">account\n                                            page</a>.\n                                        </p>\n\n                                    </div>\n                                </td>\n                            </tr>\n                        </tbody>\n                    </table>\n                </td>\n            </tr>\n        </tbody>\n    </table>\n</div></body></html>"
  }

  case object EmailEvent {
  }
}