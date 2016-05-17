package bots.dsl.backend

import bots.dsl.frontend.Bot

/**
  * Created by dsavvinov on 5/13/16.
  */
abstract class BotDescription {
    def apply() : Bot
}
