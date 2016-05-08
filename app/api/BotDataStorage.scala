package api

/**
  * Created by dsavvinov on 4/25/16.
  */

trait DataHolder {
    type DataType
    val dataValue : DataType
}

class BotDataStorage(val dataStorage : collection.mutable.Map[String, DataHolder] = collection.mutable.Map.empty[String, DataHolder]) {
    override def clone() : BotDataStorage = {
        new BotDataStorage(dataStorage.clone())
    }

    def initHolder[T](fieldName : String, data : T) = {
        dataStorage += (fieldName -> new DataHolder {
            type DataType = T
            val dataValue: DataType = data
        })
    }

    def getHolder(fieldName : String) : DataHolder = {
        dataStorage.get(fieldName) match {
            case Some(field) => field
            case None => throw new ClassCastException(s"Error acquiring data $fieldName: incorrect name")
        }
    }
}