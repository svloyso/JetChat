# --- !Ups

CREATE TABLE `group_follow_statuses` (`group_id` BIGINT NOT NULL, `user_id` BIGINT NOT NULL);

ALTER TABLE `group_follow_statuses` ADD PRIMARY KEY (`group_id`, `user_id`);
ALTER TABLE `group_follow_statuses` ADD CONSTRAINT `group_follow_status_group_fk` FOREIGN KEY(`group_id`) REFERENCES `groups`(`id`) ON UPDATE NO ACTION ON DELETE NO ACTION;
ALTER TABLE `group_follow_statuses` ADD CONSTRAINT `group_follow_status_user_fk` FOREIGN KEY(`user_id`) REFERENCES `users`(`id`) ON UPDATE NO ACTION ON DELETE NO ACTION;

CREATE TABLE `topic_follow_statuses` (`topic_id` BIGINT NOT NULL, `user_id` BIGINT NOT NULL);

ALTER TABLE `topic_follow_statuses` ADD PRIMARY KEY (`topic_id`, `user_id`);
ALTER TABLE `topic_follow_statuses` ADD CONSTRAINT `topic_follow_status_topic_fk` FOREIGN KEY(`topic_id`) REFERENCES `topics`(`id`) ON UPDATE NO ACTION ON DELETE NO ACTION;
ALTER TABLE `topic_follow_statuses` ADD CONSTRAINT `topic_follow_status_user_fk` FOREIGN KEY(`user_id`) REFERENCES `users`(`id`) ON UPDATE NO ACTION ON DELETE NO ACTION;

# --- !Downs

DROP TABLE `group_follow_statuses`;
DROP TABLE `topic_follow_statuses`;
