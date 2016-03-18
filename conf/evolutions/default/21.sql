# --- !Ups

ALTER TABLE `integration_topics` DROP FOREIGN KEY integration_topic_integration_group_fk;
ALTER TABLE `integration_topics` DROP FOREIGN KEY integration_topic_integration_user_fk;
ALTER TABLE `integration_topics` DROP INDEX integration_topic_integration_group_fk;
ALTER TABLE `integration_topics` DROP INDEX integration_topic_integration_user_fk;

ALTER TABLE `integration_updates` DROP FOREIGN KEY integration_update_integration_topic_fk;
ALTER TABLE `integration_updates` DROP INDEX integration_update_integration_topic_fk;

ALTER TABLE `integration_topics` DROP PRIMARY KEY;

ALTER TABLE `integration_topics` ADD `id` BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY;
ALTER TABLE `integration_topics` MODIFY `integration_topic_id` VARCHAR(255);

CREATE INDEX `integration_topics_index` ON `integration_topics` (`integration_id`, `integration_group_id`, `integration_user_id`, `user_id`);

# --- !Downs