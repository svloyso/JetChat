package models

import java.sql.Timestamp

trait AbstractIntegrationMessage {
  def integrationId: String
  def integrationGroupId: String
  def integrationTopicId: String
  def integrationUserId: String
  def date: Timestamp
  def text: String
}
