# --- !Ups

create index `integration_token_user_index` on `integration_tokens` (`user_id`);

# --- !Downs

alter table `integration_tokens` drop index `integration_token_user_index`;