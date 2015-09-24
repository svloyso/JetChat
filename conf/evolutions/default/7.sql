# --- !Ups

create table `integration_updates` (`id` BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY, `integration_id` VARCHAR(254) NOT NULL, `integration_update_id` VARCHAR(254), `integration_group_id` VARCHAR(254) NOT NULL, `topic_id` BIGINT NOT NULL, `integration_user_id` VARCHAR(254) NOT NULL, `user_id` BIGINT, `date` TIMESTAMP NOT NULL, `text` text NOT NULL);
create index `integration_update_index` on `integration_updates` (`integration_id`, `integration_update_id`);
alter table `integration_updates` add constraint `integration_update_user_fk` foreign key(`user_id`) references `users`(`id`) on update NO ACTION on delete NO ACTION;

alter table `integration_updates` add constraint `integration_update_topic_fk` foreign key(`topic_id`) references `integration_topics`(`id`) on update NO ACTION on delete NO ACTION;

alter table `integration_updates` add constraint `integration_update_integration_user_fk` foreign key(`integration_id`, `integration_user_id`) references `integration_users`(`integration_id`, `integration_user_id`) on update NO ACTION on delete NO ACTION;

alter table `integration_updates` add constraint `integration_update_integration_group_fk` foreign key(`integration_id`, `integration_group_id`) references `integration_groups`(`integration_id`, `integration_group_id`) on update NO ACTION on delete NO ACTION;

create index `integration_update_integration_group_index` on `integration_updates` (`integration_id`, `integration_group_id`);
create index `integration_update_user_integration_group_index` on `integration_updates` (`integration_id`, `integration_group_id`, `integration_user_id`);

# --- !Downs

drop table `integration_updates`;

