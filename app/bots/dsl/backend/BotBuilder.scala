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

    def arg: Parser[String] = not(regex("initWith".r)) ~> """[^\s]+""".r

    def field_definition: Parser[(String, String)] =
      (("[" ~> squareInner <~ "]") ~ arg ) ^^ {
        case tpe ~ fieldName => {
          val strippedFieldName = fieldName.stripPrefix("\"").stripSuffix("\"")
          nameToType += (strippedFieldName -> tpe); (tpe, fieldName)
        }
      }

    def preamble: Parser[String] = """storesData""".r
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

  object BehaviourWrapper extends JavaTokenParsers {
    override def skipWhitespace = false

    def wrapBefore = """(new Behaviour {
                          override def handler(msg: TextMessage) = """

    def wrapAfter = """ }) """
    def preamble = "State".r

    def word = regex("""[^()]+""".r) | roundBrackets

    def roundBrackets: Parser[String] = ("(" ~ rep(word) ~ ")") ^^ { case lbr ~ l ~ rbr =>  lbr + l.mkString + rbr }

    def figureBrackets: Parser[String] = "{" ~ figureInner ~ "}" ^^ { case lbr ~ in ~ rbr => lbr + in + rbr}

    def expr: Parser[String] = """[^{}]+""".r

    def figureInner: Parser[String] = rep1(figureBrackets | expr) ^^ { _.mkString }

    def stateDef: Parser[String] = (preamble ~ roundBrackets ~ figureBrackets ^^  { case definition ~ name ~ handler =>
      definition + name + wrapBefore + handler + wrapAfter}) | regex("(?s).".r)

    def mainParser = rep1(stateDef ) ^^ { _.mkString }
    def apply(input: String): String = {
      parseAll(mainParser, input) match {
        case Success(result, _) => result
        case failure: NoSuccess => sys.error(failure.msg)
      }
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
    DefinitionsParser(userCode)                         // collect definitions of data fields
    val ascribedUserCode = DefinitionsParser(userCode)  // add explicit casts to use of data fields
    val wrappedUserCode = BehaviourWrapper(ascribedUserCode)    // wrap handlers into anonymous classes to add context
    val code = preambleCode + wrappedUserCode + postambleCode // add imports and reflective calls
    compileBot(system, code)
  }
}
