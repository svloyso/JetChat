package bots.dsl.backend

import bots.dsl.frontend.Bot

/**
  * Created by dsavvinov on 5/13/16.
  */

class DataTransformer[T](val fieldName: String, val bot: Bot) {
  def initWith(value: T) {
    bot.data.initHolder[T](fieldName, value)
  }
}

class BotDataStorage(val dataStorage: collection.mutable.Map[String, DataHolder] = collection.mutable.Map.empty[String, DataHolder]) {
  override def clone(): BotDataStorage = {
    new BotDataStorage(dataStorage.clone())
  }

  def initHolder[T](fieldName: String, data: T) = {
    dataStorage += (fieldName -> new DataHolder {
      type DataType = T
      val dataValue: DataType = data
    })
  }

  def getHolder(fieldName: String): DataHolder = {
    dataStorage.get(fieldName) match {
      case Some(field) => field
      case None => throw new ClassCastException(s"Error acquiring data $fieldName: incorrect name")
    }
  }
}
