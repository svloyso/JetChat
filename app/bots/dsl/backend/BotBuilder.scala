package bots.dsl.backend

import akka.actor.ActorSystem
import scala.reflect.runtime._
import scala.util.parsing.combinator.JavaTokenParsers
import tools.reflect.ToolBox

/**
  * Created by dsavvinov on 5/13/16.
  */
object BotBuilder {
  object DefinitionsParser extends JavaTokenParsers {
    override def skipWhitespace() = true

    val nameToType = collection.mutable.Map[String, String]()

    def word = regex("""[^\[\]\s]+""".r)

    def squareInner: Parser[String] = rep1(squareBrackets | word) ^^ { _.mkString }

    def squareBrackets: Parser[String] = ("[" ~ squareInner ~ "]") ^^ { case lbr ~ in ~ rbr => lbr + in + rbr }

    def roundBrackets: Parser[String] = ("(" ~ args ~ ")") ^^ { case lbr ~ l ~ rbr => lbr + l + rbr }

    def arg: Parser[String] = rep("""[^,()]+""".r | roundBrackets) ^^ { _.mkString}

    def args: Parser[String] =  repsep(arg, ",") ^^ { _(0) }

    def field_definition: Parser[(String, String)] =
      (("[" ~> squareInner <~ "]") ~ ("(" ~> args <~ ")")) ^^ {
        case tpe ~ fieldName => {
          val strippedFieldName = fieldName.stripPrefix("\"").stripSuffix("\"")
          nameToType += (strippedFieldName -> tpe); (tpe, fieldName)
        }
      }

    def preamble: Parser[String] = """.storesData""".r
    def whitespace: Parser[String] = """\s""".r

    def chunk: Parser[String] = (preamble ~ field_definition) ^^ { _ => "" } | (not(preamble | whitespace) ~> ".".r) ^^ { _ => "" }
    def chunks = rep1(chunk)
    def apply(input: String): String = {
      nameToType.clear()
      parseAll(chunks, input) match {
        case Success(result, _) => ""
        case failure: NoSuccess => scala.sys.error(failure.msg)
      }
      ascribe(input)
    }

    def ascribe(input: String): String = {
      var output = input
      nameToType.foreach { case (field, tpe) =>
        output = output.replaceAll(s"""data.$field""", s"""data.$field.asInstanceOf[$tpe]""")
      }
      output
    }
  }

  def compileBot(system: ActorSystem, code: String): Unit = {
    val cm      = universe.runtimeMirror(getClass.getClassLoader)
    val tb      = cm.mkToolBox()
    val botUserDefinedClass = tb.eval(tb.parse(code)).asInstanceOf[Class[BotDescription]]
    val botUserDefinedObj = botUserDefinedClass.getConstructors()(0).newInstance()
    val bot = botUserDefinedObj.asInstanceOf[BotDescription].apply()
    bot.launch(system)
  }

  val preambleCode = """
                       import bots.dsl.backend.BotMessages.TextMessage
                       import bots.dsl.backend.BotDescription
                       import bots.dsl.frontend.{Behaviour, Bot, State}
                       class ReflectiveDescription extends BotDescription {
                           def apply() = {
                     """

  val postambleCode =  """
                          }
                         }
                        scala.reflect.classTag[ReflectiveDescription].runtimeClass
                       """
  def buildBot(system: ActorSystem, userCode: String) = {
    DefinitionsParser(userCode)
    val ascribedUserCode = DefinitionsParser(userCode)
    compileBot(system, preambleCode + ascribedUserCode + postambleCode)
  }
}
