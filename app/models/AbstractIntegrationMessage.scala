package models

import java.sql.Timestamp

trait AbstractIntegrationMessage {
  def integrationId: String
  def integrationGroupId: String
  def integrationUserId: String
  def userId:Option[Long]
  def date: Timestamp
  def text: String
}
