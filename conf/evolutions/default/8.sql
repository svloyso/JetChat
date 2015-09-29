# --- !Ups

alter table `integration_updates` drop index `integration_update_index`;
alter table `integration_updates` drop index `integration_update_user_integration_group_index`;

alter table `integration_updates` drop FOREIGN KEY `integration_update_user_fk`;
alter table `integration_updates` drop FOREIGN KEY `integration_update_topic_fk`;

alter table `integration_updates` drop column `user_id`;
alter table `integration_updates` drop column `topic_id`;

alter table `integration_updates` modify id BIGINT NOT NULL;
alter table `integration_updates` drop PRIMARY KEY;
alter table `integration_updates` drop column id;

alter table `integration_updates` modify `integration_update_id` VARCHAR(254) NOT NULL;
alter table `integration_updates` add column `integration_topic_id` VARCHAR(254) NOT NULL;

alter table `integration_updates` add PRIMARY KEY(`integration_id`, `integration_update_id`);

alter table `integration_topics` drop index `integration_topic_user_integration_group_index`;
alter table `integration_topics` drop index `integration_topic_index`;

alter table `integration_topics` drop FOREIGN KEY `integration_topic_user_fk`;

alter table `integration_topics` drop column `user_id`;

alter table `integration_topics` modify id BIGINT NOT NULL;
alter table `integration_topics` drop PRIMARY KEY;
alter table `integration_topics` drop column id;

alter table `integration_topics` modify `integration_topic_id` VARCHAR(254) NOT NULL;

alter table `integration_topics` add PRIMARY KEY(`integration_id`, `integration_topic_id`);

alter table `integration_updates` add constraint `integration_update_integration_topic_fk` foreign key(`integration_id`, `integration_topic_id`) references `integration_topics`(`integration_id`, `integration_topic_id`) on update NO ACTION on delete NO ACTION;

# --- !Downs
