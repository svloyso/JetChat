# --- !Ups

ALTER TABLE `integration_tokens` ADD COLUMN `enabled` BOOLEAN DEFAULT FALSE;

# --- !Downs

ALTER TABLE `integration_tokens` DROP COLUMN `enabled`;
