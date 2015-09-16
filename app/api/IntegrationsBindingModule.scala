package api

import com.google.inject.AbstractModule
import com.google.inject.multibindings.Multibinder
import org.reflections.Reflections
import play.twirl.api.TemplateMagic.javaCollectionToScala
import play.api.Logger

class IntegrationsBindingModule extends AbstractModule {
  @Override
  def configure(): Unit = {
    val b = Multibinder.newSetBinder(binder, classOf[Integration])
    val classes = new Reflections().getSubTypesOf(classOf[Integration])
    classes.toSeq.map { case c: Class[Integration] =>
      if (c.isAnnotationPresent(classOf[javax.inject.Singleton])) {
        Logger.debug(s"Binding an integration: ${c.getCanonicalName}")
        b.addBinding().to(c)
      }
    }
  }
}
