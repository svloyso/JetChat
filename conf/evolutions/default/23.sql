# --- !Ups

ALTER TABLE `users` ADD COLUMN `isbot` BOOLEAN DEFAULT FALSE;

# --- !Downs

ALTER TABLE `users` DROP COLUMN `isbot`;
