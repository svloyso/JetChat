# --- !Ups

alter table `integration_topics` drop PRIMARY KEY;
alter table `integration_topics` add `id` BIGINT NOT NULL;
alter table `integration_topics` add primary key (`id`);
alter table `integration_topics` modify `id` BIGINT NOT NULL  AUTO_INCREMENT;
alter table `integration_topics` modify `integration_topic_id` VARCHAR(254) NULL;
create index `integration_topic_index` on `integration_topics` (`integration_id`, `integration_topic_id`);

# --- !Downs


