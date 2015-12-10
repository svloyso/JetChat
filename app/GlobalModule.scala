import com.google.inject.AbstractModule

class GlobalModule extends AbstractModule {
  override def configure() = {
    bind(classOf[Global]).asEagerSingleton()
  }
}
