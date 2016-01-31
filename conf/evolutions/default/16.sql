# --- !Ups

CREATE TABLE `direct_message_read_statuses` (`direct_message_id` BIGINT NOT NULL PRIMARY KEY);

ALTER TABLE `direct_message_read_statuses` ADD CONSTRAINT `direct_message_read_status_direct_message_fk` FOREIGN KEY(`direct_message_id`) REFERENCES `direct_messages`(`id`) ON UPDATE NO ACTION ON DELETE NO ACTION;

# --- !Downs

DROP TABLE `direct_message_read_statuses`;
