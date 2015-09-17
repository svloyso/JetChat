# --- !Ups

create table `integration_users` (`integration_id` VARCHAR(254) NOT NULL, `user_id` BIGINT, `integration_user_id` VARCHAR(254) NOT NULL, `integration_user_name` VARCHAR(254) NOT NULL, `integration_user_avatar` VARCHAR(254));
create index `integration_user_id_index` on `integration_users` (`user_id`, `integration_id`);
create unique index `integration_integration_user_id_index` on `integration_users` (`integration_user_id`, `integration_id`);
alter table `integration_users` add constraint `integration_user_user_fk` foreign key(`user_id`) references `users`(`id`) on update NO ACTION on delete NO ACTION;

# --- !Downs

ALTER TABLE `integration_users` DROP FOREIGN KEY `integration_user_user_fk`;
drop table `integration_users`;

