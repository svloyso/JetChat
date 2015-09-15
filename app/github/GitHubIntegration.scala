package github

import api.{HookHandler, Authentificator, MessageHandler, Integration}
import models.AbstractMessage

/**
 * @author Alefas
 * @since  15/09/15
 */
class GitHubIntegration extends Integration {
  override def id: String = "GitHub"

  override def name: String = "GitHub"

  override def hookHandler: HookHandler = new HookHandler {
    override def handle(): Unit = {
      //todo:
    }
  }

  override def authentificator: Authentificator = new Authentificator {
    override def disable(): Unit = {
      //todo:
    }

    override def enable(): Unit = {
      //todo:
    }
  }

  override def messageHandler: MessageHandler = new MessageHandler {
    override def sendMessage(messages: Seq[AbstractMessage]): Unit = {
      //todo:
    }
  }
}
