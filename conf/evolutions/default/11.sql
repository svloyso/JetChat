# --- !Ups

DELETE FROM `integration_updates`;

ALTER TABLE `integration_updates` ADD COLUMN `user_id` BIGINT NOT NULL;
ALTER TABLE `integration_updates` ADD CONSTRAINT `integration_update_user_fk` FOREIGN KEY(`user_id`) REFERENCES `users`(`id`) ON UPDATE NO ACTION ON DELETE NO ACTION;
ALTER TABLE `integration_updates` DROP PRIMARY KEY;
ALTER TABLE `integration_updates` MODIFY `integration_update_id` VARCHAR(254) NULL;
ALTER TABLE `integration_updates` ADD COLUMN `id` BIGINT NOT NULL;
ALTER TABLE `integration_updates` ADD PRIMARY KEY (`id`);
ALTER TABLE `integration_updates` MODIFY `id` BIGINT NOT NULL AUTO_INCREMENT;

CREATE INDEX `integration_update_index` on `integration_updates` (`integration_update_id`, `integration_id`, `user_id`);

ALTER TABLE `integration_updates` DROP FOREIGN KEY `integration_update_integration_group_fk`;
ALTER TABLE `integration_updates` DROP FOREIGN KEY `integration_update_integration_topic_fk`;
ALTER TABLE `integration_updates` DROP INDEX `integration_update_integration_topic_fk`;
ALTER TABLE `integration_updates` DROP FOREIGN KEY `integration_update_integration_user_fk`;
ALTER TABLE `integration_updates` DROP INDEX `integration_update_integration_user_fk`;

ALTER TABLE `integration_updates` DROP INDEX `integration_update_integration_group_index`;

DELETE FROM `integration_topics`;

ALTER TABLE `integration_topics` DROP FOREIGN KEY `integration_topic_integration_group_fk`;
ALTER TABLE `integration_topics` DROP FOREIGN KEY `integration_topic_integration_user_fk`;
ALTER TABLE `integration_topics` DROP INDEX `integration_topic_integration_user_fk`;

ALTER TABLE `integration_topics` DROP INDEX `integration_topic_integration_group_index`;

ALTER TABLE `integration_topics` ADD COLUMN `user_id` BIGINT NOT NULL;
ALTER TABLE `integration_topics` ADD CONSTRAINT `integration_topic_user_fk` FOREIGN KEY(`user_id`) REFERENCES `users`(`id`) ON UPDATE NO ACTION ON DELETE NO ACTION;
ALTER TABLE `integration_topics` DROP PRIMARY KEY;
ALTER TABLE `integration_topics` ADD PRIMARY KEY (`integration_topic_id`, `integration_id`, `user_id`);

DELETE FROM `integration_groups`;

ALTER TABLE `integration_groups` ADD COLUMN `user_id` BIGINT NOT NULL;
ALTER TABLE `integration_groups` ADD CONSTRAINT `integration_group_user_fk` FOREIGN KEY(`user_id`) REFERENCES `users`(`id`) ON UPDATE NO ACTION ON DELETE NO ACTION;
ALTER TABLE `integration_groups` DROP PRIMARY KEY;
ALTER TABLE `integration_groups` ADD PRIMARY KEY (`integration_group_id`, `integration_id`, `user_id`);


ALTER TABLE `integration_updates` ADD CONSTRAINT `integration_update_integration_user_fk` FOREIGN KEY(`integration_id`, `integration_user_id`) REFERENCES `integration_users`(`integration_id`, `integration_user_id`) ON UPDATE NO ACTION ON DELETE NO ACTION;
ALTER TABLE `integration_updates` ADD CONSTRAINT `integration_update_integration_group_fk` FOREIGN KEY(`integration_group_id`, `integration_id`, `user_id`) REFERENCES `integration_groups`(`integration_group_id`, `integration_id`, `user_id`) ON UPDATE NO ACTION ON DELETE NO ACTION;
ALTER TABLE `integration_updates` ADD CONSTRAINT `integration_update_integration_topic_fk` FOREIGN KEY(`integration_topic_id`, `integration_id`, `user_id`) REFERENCES `integration_topics`(`integration_topic_id`, `integration_id`, `user_id`) ON UPDATE NO ACTION ON DELETE NO ACTION;

ALTER TABLE `integration_topics` ADD CONSTRAINT `integration_topic_integration_user_fk` FOREIGN KEY(`integration_id`, `integration_user_id`) REFERENCES `integration_users`(`integration_id`, `integration_user_id`) ON UPDATE NO ACTION ON DELETE NO ACTION;
ALTER TABLE `integration_topics` ADD CONSTRAINT `integration_topic_integration_group_fk` FOREIGN KEY(`integration_group_id`, `integration_id`, `user_id`) REFERENCES `integration_groups`(`integration_group_id`, `integration_id`, `user_id`) ON UPDATE NO ACTION ON DELETE NO ACTION;

# --- !Downs