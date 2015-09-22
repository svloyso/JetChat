# --- !Ups

create table `integration_topics` (`integration_id` VARCHAR(254) NOT NULL, `integration_topic_id` VARCHAR(254) NOT NULL, `integration_group_id` VARCHAR(254) NOT NULL, `integration_user_id` VARCHAR(254) NOT NULL, `user_id` BIGINT, `date` TIMESTAMP NOT NULL, `text` text NOT NULL);
alter table `integration_topics` add primary key (`integration_id`, `integration_topic_id`);
alter table `integration_topics` add constraint `integration_topic_user_fk` foreign key(`user_id`) references `users`(`id`) on update NO ACTION on delete NO ACTION;

alter table `integration_users` drop index `integration_integration_user_id_index`;
alter table `integration_users` add primary key (`integration_id`, `integration_user_id`);

alter table `integration_topics` add constraint `integration_topic_integration_user_fk` foreign key(`integration_id`, `integration_user_id`) references `integration_users`(`integration_id`, `integration_user_id`) on update NO ACTION on delete NO ACTION;

alter table `integration_topics` add constraint `integration_topic_integration_group_fk` foreign key(`integration_id`, `integration_group_id`) references `integration_groups`(`integration_id`, `integration_group_id`) on update NO ACTION on delete NO ACTION;

create index `integration_topic_integration_group_index` on `integration_topics` (`integration_id`, `integration_group_id`);
create index `integration_topic_user_integration_group_index` on `integration_topics` (`integration_id`, `integration_group_id`, `integration_user_id`);

# --- !Downs

drop table `integration_topics`;

