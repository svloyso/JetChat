# --- !Ups

create table `integration_groups` (`integration_id` VARCHAR(254) NOT NULL, `integration_group_id` VARCHAR(254) NOT NULL, `name` VARCHAR(254) NOT NULL);
alter table `integration_groups` add primary key (`integration_id`, `integration_group_id`);

# --- !Downs

drop table `integration_groups`;

