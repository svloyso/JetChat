package models

trait AbstractGroupMessage extends AbstractMessage {
  def groupId: String
  def userId: Long
}
