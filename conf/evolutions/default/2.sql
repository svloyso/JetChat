
# --- !Ups

create table "DirectMessage" ("id" BIGSERIAL NOT NULL PRIMARY KEY, "fromUserId" BIGINT NOT NULL, "toUserId" BIGINT NOT NULL, "date" timestamptz NOT NULL, "text" text NOT NULL);

alter table "DirectMessage" add constraint "from_user_fk" foreign key("fromUserId") references "User"("id") on update NO ACTION on delete NO ACTION;
alter table "DirectMessage" add constraint "to_user_fk" foreign key("toUserId") references "User"("id") on update NO ACTION on delete NO ACTION;

# --- !Downs

alter table "DirectMessage" drop constraint "to_user_fk";
alter table "DirectMessage" drop constraint "from_user_fk";

drop table "DirectMessage";
