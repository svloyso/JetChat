package models

import org.joda.time.DateTime

trait Message {
  def id: Long
  def groupId: String
  def userId: Long
  def date: DateTime
  def text: String
}
