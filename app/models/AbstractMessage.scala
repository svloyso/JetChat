package models

import org.joda.time.DateTime

trait AbstractMessage {
  def id: Long
  def date: DateTime
  def text: String
}
