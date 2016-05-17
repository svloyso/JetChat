package bots.dsl.backend

import scala.language.dynamics

/**
  * Created by dsavvinov on 5/13/16.
  */

trait DataHolder {
    type DataType
    val dataValue : DataType
}

class LocalDynamicProxyStorage extends Dynamic {
    var talk : Talk = null
    def selectDynamic(name: String) : Any = {
        talk.data.getHolder(name)
    }

    def updateDynamic(name: String)(value: Any) : Unit = {
        talk.data.dataStorage(name) = value
    }
}

