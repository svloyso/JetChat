# --- !Ups

DELETE FROM `integration_topics`;
DELETE FROM `integration_updates`;

ALTER TABLE `integration_updates` DROP FOREIGN KEY `integration_update_integration_topic_fk`;
ALTER TABLE `integration_updates` DROP INDEX `integration_update_integration_topic_fk`;

ALTER TABLE `integration_topics` DROP PRIMARY KEY;

ALTER TABLE `integration_topics` ADD PRIMARY KEY (`integration_topic_id`, `integration_group_id`, `integration_id`, `user_id`);
ALTER TABLE `integration_updates` ADD CONSTRAINT `integration_update_integration_topic_fk` FOREIGN KEY(`integration_topic_id`, `integration_group_id`, `integration_id`, `user_id`) REFERENCES `integration_topics`(`integration_topic_id`, `integration_group_id`, `integration_id`, `user_id`) ON UPDATE NO ACTION ON DELETE NO ACTION;

# --- !Downs