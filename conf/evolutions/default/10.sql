# --- !Ups

ALTER TABLE `integration_topics` ADD COLUMN title VARCHAR(254) NULL;

# --- !Downs

ALTER TABLE `integration_topics` DROP COLUMN title;