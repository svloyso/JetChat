
# --- !Ups

create table "User" ("id" BIGSERIAL NOT NULL PRIMARY KEY, "login" VARCHAR(50) NOT NULL, "name" VARCHAR(255) NOT NULL, "avatar" VARCHAR(255));
create table "Group" ("id" VARCHAR(50) NOT NULL PRIMARY KEY);
create table "Topic" ("id" BIGSERIAL NOT NULL PRIMARY KEY, "userId" BIGINT NOT NULL, "groupId" VARCHAR(50) NOT NULL, "date" timestamptz NOT NULL, "text" text NOT NULL);
create table "Comment" ("id" BIGSERIAL NOT NULL PRIMARY KEY, "topicId" BIGINT NOT NULL, "userId" BIGINT NOT NULL, "groupId" VARCHAR(50) NOT NULL, "date" timestamptz NOT NULL, "text" text NOT NULL);

create unique index "login_index" on "User" ("login");

alter table "Topic" add constraint "user_fk" foreign key("userId") references "User"("id") on update NO ACTION on delete NO ACTION;
alter table "Topic" add constraint "group_fk" foreign key("groupId") references "Group"("id") on update NO ACTION on delete NO ACTION;
alter table "Comment" add constraint "user_fk" foreign key("userId") references "User"("id") on update NO ACTION on delete NO ACTION;
alter table "Comment" add constraint "group_fk" foreign key("groupId") references "Group"("id") on update NO ACTION on delete NO ACTION;
alter table "Comment" add constraint "topic_fk" foreign key("topicId") references "Topic"("id") on update NO ACTION on delete NO ACTION;

# --- !Downs

alter table "Comment" drop constraint "topic_fk";
alter table "Comment" drop constraint "user_fk";
alter table "Comment" drop constraint "group_fk";

alter table "Topic" drop constraint "group_fk";
alter table "Topic" drop constraint "user_fk";

drop table "Topic";
drop table "Group";
drop table "User";

