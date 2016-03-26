# --- !Ups

ALTER TABLE `users` ADD COLUMN `email` VARCHAR(255) NULL;

# --- !Downs

ALTER TABLE `users` DROP COLUMN `email`;
