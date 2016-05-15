package bots.dsl.backend


/**
  * Created by dsavvinov on 5/13/16.
  */

class DataTransformer[T](val fieldName: String, val dataStorage: BotDataStorage) {
  def initWith(value: T) {
    dataStorage.initHolder[T](fieldName, value)
  }
}

class BotDataStorage(val dataStorage: collection.mutable.Map[String, Any] = collection.mutable.Map.empty[String, Any]) {
  override def clone(): BotDataStorage = {
    new BotDataStorage(dataStorage.clone())
  }

  def initHolder[T](fieldName: String, data: T) = {
    dataStorage += (fieldName -> data.asInstanceOf[Any])
  }

  def getHolder(fieldName: String): Any = {
    apply(fieldName)
  }

  def apply(fieldName: String): Any = {
    dataStorage.get(fieldName) match {
      case Some(field) => field
      case None => throw new ClassCastException(s"Error acquiring data $fieldName: incorrect name")
    }
  }
}
