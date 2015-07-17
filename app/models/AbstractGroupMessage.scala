package models

trait AbstractGroupMessage extends AbstractMessage {
  def groupId: Long
  def userId: Long
}
