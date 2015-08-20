package models

import java.sql.Timestamp

trait AbstractMessage {
  def id: Long
  def date: Timestamp
  def text: String
}
