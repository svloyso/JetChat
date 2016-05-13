package bots.dsl.backend

import scala.language.dynamics

/**
  * Created by dsavvinov on 5/13/16.
  */

trait DataHolder {
    type DataType
    val dataValue : DataType
}

class DynamicProxyStorage extends Dynamic {
    var talk : Talk = null
    def selectDynamic(name: String) : Any = {
        talk.data.getHolder(name).dataValue
    }

    def updateDynamic(name: String)(value: Any) : Unit = {
        val holder = talk.data.getHolder(name)
        val newHolder = new DataHolder {
            override type DataType = holder.DataType
            override val dataValue: DataType = holder.dataValue
        }
        talk.data.dataStorage(name) = newHolder
    }
}
