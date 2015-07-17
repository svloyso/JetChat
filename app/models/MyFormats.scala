package models

import java.util.UUID

import org.joda.time.LocalDateTime
import play.api.data.FormError
import play.api.data.format.{Formats, Formatter}
import play.api.libs.json.{JsValue, Json, Reads, Writes}

/**
 * my play form data formatters
 */
object MyFormats {

  def jsonFormat: Formatter[JsValue] = new Formatter[JsValue] {
    override val format = Some(("format.json", Nil))

    def bind(key: String, data: Map[String, String]) =
      parsing(Json.parse, "error.json", Nil)(key, data)
    def unbind(key: String, value: JsValue) = Map(key -> Json.stringify(value))
  }

  ///
  def jodaDateTimeFormat: Formatter[LocalDateTime] = new Formatter[LocalDateTime] {
    override val format = Some(("format.datetime", Nil))

    def bind(key: String, data: Map[String, String]) =
      parsing(LocalDateTime.parse, "error.datetime", Nil)(key, data)
    def unbind(key: String, value: LocalDateTime) = Map(key -> value.toString)
  }

  ///
  def uuidFormat: Formatter[UUID] = new Formatter[UUID] {
    override val format = Some(("format.uuid", Nil))

    def bind(key: String, data: Map[String, String]) =
      parsing(UUID.fromString, "error.uuid", Nil)(key, data)
    def unbind(key: String, value: UUID) = Map(key -> value.toString)
  }

  ///
  def strMapFormat = new Formatter[Map[String, String]] {
    override val format = Some(("format.jsonmap", Seq("{key1:value1, key2:value2, ...}")))

    def bind(key: String, data: Map[String, String]) =
      parsing(fromJsonStr(_).getOrElse(Map.empty[String,String]), "error.jsonmap", Nil)(key, data)
    def unbind(key: String, value: Map[String,String]) = Map(key -> toJsonStr(value))
  }

  implicit private val mapReads = Reads.mapReads[String]
  implicit private val mapWrites = Writes.mapWrites[String]
  def toJsonStr(v: Map[String,String]): String = Json.stringify(Json.toJson(v))
  def fromJsonStr(s: String): Option[Map[String,String]] = Option(Json.fromJson(Json.parse(s)).get)

  /**
   * (copy from [[play.api.data.format.Formats#parsing]])
   * Helper for formatters binders
   * @param parse Function parsing a String value into a T value, throwing an exception in case of failure
   * @param errMsg Error to set in case of parsing failure
   * @param errArgs Arguments for error message
   * @param key Key name of the field to parse
   * @param data Field data
   */
  private def parsing[T](parse: String => T, errMsg: String, errArgs: Seq[Any])(
    key: String, data: Map[String, String]): Either[Seq[FormError], T] = {
    Formats.stringFormat.bind(key, data).right.flatMap { s =>
      scala.util.control.Exception.allCatch[T]
        .either(parse(s))
        .left.map(e => Seq(FormError(key, errMsg, errArgs)))
    }
  }
}
