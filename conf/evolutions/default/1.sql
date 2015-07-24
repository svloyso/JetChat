# --- Created by Slick DDL
# To stop Slick DDL generation, remove this comment and start using Evolutions

# --- !Ups

create table `comments` (`id` BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,`group_id` BIGINT NOT NULL,`topic_id` BIGINT NOT NULL,`user_id` BIGINT NOT NULL,`date` TIMESTAMP NOT NULL,`text` text NOT NULL);
create table `direct_messages` (`id` BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,`from_user_id` BIGINT NOT NULL,`to_user_id` BIGINT NOT NULL,`date` TIMESTAMP NOT NULL,`text` text NOT NULL);
create table `groups` (`id` BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,`name` VARCHAR(254) NOT NULL);
create unique index `group_name_index` on `groups` (`name`);
create table `topics` (`id` BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,`group_id` BIGINT NOT NULL,`user_id` BIGINT NOT NULL,`date` TIMESTAMP NOT NULL,`text` text NOT NULL);
create table `users` (`id` BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,`login` VARCHAR(254) NOT NULL,`name` VARCHAR(254) NOT NULL,`avatar` VARCHAR(254));
create unique index `user_login_index` on `users` (`login`);
alter table `comments` add constraint `comment_group_fk` foreign key(`group_id`) references `groups`(`id`) on update NO ACTION on delete NO ACTION;
alter table `comments` add constraint `comment_topic_fk` foreign key(`topic_id`) references `topics`(`id`) on update NO ACTION on delete NO ACTION;
alter table `comments` add constraint `comment_user_fk` foreign key(`user_id`) references `users`(`id`) on update NO ACTION on delete NO ACTION;
alter table `direct_messages` add constraint `dm_from_user_fk` foreign key(`from_user_id`) references `users`(`id`) on update NO ACTION on delete NO ACTION;
alter table `direct_messages` add constraint `dm_to_user_fk` foreign key(`to_user_id`) references `users`(`id`) on update NO ACTION on delete NO ACTION;
alter table `topics` add constraint `topic_group_fk` foreign key(`group_id`) references `groups`(`id`) on update NO ACTION on delete NO ACTION;
alter table `topics` add constraint `topic_user_fk` foreign key(`user_id`) references `users`(`id`) on update NO ACTION on delete NO ACTION;

# --- !Downs

ALTER TABLE topics DROP FOREIGN KEY topic_group_fk;
ALTER TABLE topics DROP FOREIGN KEY topic_user_fk;
ALTER TABLE direct_messages DROP FOREIGN KEY dm_from_user_fk;
ALTER TABLE direct_messages DROP FOREIGN KEY dm_to_user_fk;
ALTER TABLE comments DROP FOREIGN KEY comment_group_fk;
ALTER TABLE comments DROP FOREIGN KEY comment_topic_fk;
ALTER TABLE comments DROP FOREIGN KEY comment_user_fk;
drop table `users`;
drop table `topics`;
drop table `groups`;
drop table `direct_messages`;
drop table `comments`;

