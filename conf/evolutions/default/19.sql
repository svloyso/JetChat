# --- !Ups

CREATE TABLE `last_direct_messages` (`min_user_id` BIGINT NOT NULL, `max_user_id` BIGINT NOT NULL, `date` TIMESTAMP NOT NULL,`text` text NOT NULL, `direct_message_id` BIGINT NOT NULL);

ALTER TABLE `last_direct_messages` ADD CONSTRAINT `last_direct_message_min_user_fk` FOREIGN KEY(`min_user_id`) REFERENCES `users`(`id`) ON UPDATE NO ACTION ON DELETE NO ACTION;
ALTER TABLE `last_direct_messages` ADD CONSTRAINT `last_direct_message_max_user_fk` FOREIGN KEY(`max_user_id`) REFERENCES `users`(`id`) ON UPDATE NO ACTION ON DELETE NO ACTION;
ALTER TABLE `last_direct_messages` ADD CONSTRAINT `last_direct_message_direct_message_fk` FOREIGN KEY(`direct_message_id`) REFERENCES `direct_messages`(`id`) ON UPDATE NO ACTION ON DELETE NO ACTION;

ALTER TABLE `last_direct_messages` ADD PRIMARY KEY(`min_user_id`, `max_user_id`);

# --- !Downs

DROP TABLE `last_direct_messages`;
