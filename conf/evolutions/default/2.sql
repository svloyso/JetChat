# --- !Ups

create table `integration_tokens` (`user_id` BIGINT NOT NULL,`integration_id` VARCHAR(254) NOT NULL,`token` VARCHAR(254) NOT NULL);
create unique index `integration_token_index` on `integration_tokens` (`user_id`, `integration_id`);
alter table `integration_tokens` add constraint `integration_token_user_fk` foreign key(`user_id`) references `users`(`id`) on update NO ACTION on delete NO ACTION;

# --- !Downs

ALTER TABLE topics DROP FOREIGN KEY integration_token_user_fk;
drop table `integration_tokens`;

