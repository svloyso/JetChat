# --- !Ups

CREATE TABLE users (id BIGINT(20) NOT NULL AUTO_INCREMENT PRIMARY KEY, login VARCHAR(254) NOT NULL, name VARCHAR(254) NOT NULL, avatar VARCHAR(254));
CREATE UNIQUE INDEX user_login_index ON users (login);

CREATE TABLE groups (id BIGINT(20) NOT NULL AUTO_INCREMENT PRIMARY KEY, name VARCHAR(254) NOT NULL);
CREATE UNIQUE INDEX group_name_index ON groups (name);

CREATE TABLE topics (id BIGINT(20) NOT NULL AUTO_INCREMENT PRIMARY KEY, group_id BIGINT(20) NOT NULL, user_id  BIGINT(20) NOT NULL, date TIMESTAMP NOT NULL, text TEXT NOT NULL);
ALTER TABLE topics ADD CONSTRAINT topic_group_fk FOREIGN KEY (group_id) REFERENCES groups (id);
ALTER TABLE topics ADD CONSTRAINT topic_user_fk FOREIGN KEY (user_id) REFERENCES users (id);
CREATE INDEX topic_group_fk ON topics (group_id);
CREATE INDEX topic_user_fk ON topics (user_id);

CREATE TABLE comments (id BIGINT(20) NOT NULL AUTO_INCREMENT PRIMARY KEY, group_id BIGINT(20) NOT NULL, topic_id BIGINT(20) NOT NULL, user_id  BIGINT(20) NOT NULL, date TIMESTAMP NOT NULL, text TEXT NOT NULL);
ALTER TABLE comments ADD CONSTRAINT comment_group_fk FOREIGN KEY (group_id) REFERENCES groups (id);
ALTER TABLE comments ADD CONSTRAINT comment_topic_fk FOREIGN KEY (topic_id) REFERENCES topics (id);
ALTER TABLE comments ADD CONSTRAINT comment_user_fk FOREIGN KEY (user_id) REFERENCES users (id);
CREATE INDEX comment_group_fk ON comments (group_id);
CREATE INDEX comment_topic_fk ON comments (topic_id);
CREATE INDEX comment_user_fk ON comments (user_id);

CREATE TABLE direct_messages (id BIGINT(20) NOT NULL AUTO_INCREMENT PRIMARY KEY, from_user_id BIGINT(20) NOT NULL, to_user_id BIGINT(20) NOT NULL, date TIMESTAMP NOT NULL, text TEXT NOT NULL);
ALTER TABLE direct_messages ADD CONSTRAINT dm_from_user_fk FOREIGN KEY (from_user_id) REFERENCES users (id);
ALTER TABLE direct_messages ADD CONSTRAINT dm_to_user_fk FOREIGN KEY (to_user_id) REFERENCES users (id);
CREATE INDEX dm_from_user_fk ON direct_messages (from_user_id);
CREATE INDEX dm_to_user_fk ON direct_messages (to_user_id);

CREATE TABLE integration_users (integration_id VARCHAR(254) NOT NULL, user_id BIGINT(20), integration_user_id VARCHAR(254) NOT NULL, integration_user_name VARCHAR(254) NOT NULL, integration_user_avatar VARCHAR(254));
ALTER TABLE integration_users ADD PRIMARY KEY (integration_id, integration_user_id);
ALTER TABLE integration_users ADD CONSTRAINT integration_user_user_fk FOREIGN KEY (user_id) REFERENCES users (id);
CREATE INDEX integration_user_id_index ON integration_users (user_id, integration_id);

CREATE TABLE integration_groups (integration_id VARCHAR(254) NOT NULL, integration_group_id VARCHAR(254) NOT NULL, name VARCHAR(254) NOT NULL, user_id BIGINT(20) NOT NULL);
ALTER TABLE integration_groups ADD PRIMARY KEY (integration_group_id, integration_id, user_id);
ALTER TABLE integration_groups ADD CONSTRAINT integration_group_user_fk FOREIGN KEY (user_id) REFERENCES users (id);
CREATE INDEX integration_group_user_fk ON integration_groups (user_id);

CREATE TABLE integration_tokens (user_id BIGINT(20) NOT NULL, integration_id VARCHAR(254) NOT NULL, token VARCHAR(254) NOT NULL);
ALTER TABLE integration_tokens ADD CONSTRAINT integration_token_user_fk FOREIGN KEY (user_id) REFERENCES users (id);
CREATE UNIQUE INDEX integration_token_index ON integration_tokens (user_id, integration_id);
CREATE INDEX integration_token_user_index ON integration_tokens (user_id);

CREATE TABLE integration_topics (integration_id VARCHAR(254) NOT NULL, integration_topic_id VARCHAR(254) NOT NULL, integration_group_id VARCHAR(254) NOT NULL, integration_user_id  VARCHAR(254) NOT NULL, date TIMESTAMP NOT NULL, text TEXT NOT NULL, title VARCHAR(254), user_id BIGINT(20) NOT NULL);
ALTER TABLE integration_topics ADD PRIMARY KEY (integration_topic_id, integration_group_id, integration_id, user_id);
ALTER TABLE integration_topics ADD CONSTRAINT integration_topic_integration_group_fk FOREIGN KEY (integration_group_id, integration_id, user_id) REFERENCES integration_groups (integration_group_id, integration_id, user_id);
ALTER TABLE integration_topics ADD CONSTRAINT integration_topic_integration_user_fk FOREIGN KEY (integration_id, integration_user_id) REFERENCES integration_users (integration_id, integration_user_id);
ALTER TABLE integration_topics ADD CONSTRAINT integration_topic_user_fk FOREIGN KEY (user_id) REFERENCES users (id);
CREATE INDEX integration_topic_integration_group_fk ON integration_topics (integration_group_id, integration_id, user_id);
CREATE INDEX integration_topic_integration_user_fk ON integration_topics (integration_id, integration_user_id);
CREATE INDEX integration_topic_user_fk ON integration_topics (user_id);

CREATE TABLE integration_updates (id BIGINT(20) NOT NULL AUTO_INCREMENT PRIMARY KEY, user_id BIGINT(20) NOT NULL, integration_id VARCHAR(254) NOT NULL, integration_group_id  VARCHAR(254) NOT NULL, integration_topic_id VARCHAR(254) NOT NULL, integration_update_id VARCHAR(254), integration_user_id VARCHAR(254) NOT NULL, date TIMESTAMP NOT NULL, text TEXT NOT NULL);
ALTER TABLE integration_updates ADD CONSTRAINT integration_update_integration_group_fk FOREIGN KEY (integration_group_id, integration_id, user_id) REFERENCES integration_groups (integration_group_id, integration_id, user_id);
ALTER TABLE integration_updates ADD CONSTRAINT integration_update_integration_topic_fk FOREIGN KEY (integration_topic_id, integration_group_id, integration_id, user_id) REFERENCES integration_topics (integration_topic_id, integration_group_id, integration_id, user_id);
ALTER TABLE integration_updates ADD CONSTRAINT integration_update_integration_user_fk FOREIGN KEY (integration_id, integration_user_id) REFERENCES integration_users (integration_id, integration_user_id);
ALTER TABLE integration_updates ADD CONSTRAINT integration_update_user_fk FOREIGN KEY (user_id) REFERENCES users (id);
CREATE INDEX integration_update_index ON integration_updates (integration_update_id, integration_id, user_id);
CREATE INDEX integration_update_integration_group_fk ON integration_updates (integration_group_id, integration_id, user_id);
CREATE INDEX integration_update_integration_topic_fk ON integration_updates (integration_topic_id, integration_id, user_id);
CREATE INDEX integration_update_integration_user_fk ON integration_updates (integration_id, integration_user_id);
CREATE INDEX integration_update_user_fk ON integration_updates (user_id);

# --- !Downs

ALTER TABLE integration_updates DROP FOREIGN KEY integration_update_integration_group_fk;
ALTER TABLE integration_updates DROP FOREIGN KEY integration_update_integration_topic_fk;
ALTER TABLE integration_updates DROP FOREIGN KEY integration_update_integration_user_fk;
ALTER TABLE integration_updates DROP FOREIGN KEY integration_update_user_fk;
ALTER TABLE integration_tokens DROP FOREIGN KEY integration_token_user_fk;
ALTER TABLE direct_messages DROP FOREIGN KEY dm_from_user_fk;
ALTER TABLE direct_messages DROP FOREIGN KEY dm_to_user_fk;
ALTER TABLE comments DROP FOREIGN KEY comment_group_fk;
ALTER TABLE comments DROP FOREIGN KEY comment_topic_fk;
ALTER TABLE comments DROP FOREIGN KEY comment_user_fk;
ALTER TABLE integration_topics DROP FOREIGN KEY integration_topic_integration_group_fk;
ALTER TABLE integration_topics DROP FOREIGN KEY integration_topic_integration_user_fk;
ALTER TABLE integration_topics DROP FOREIGN KEY integration_topic_user_fk;
ALTER TABLE topics DROP FOREIGN KEY topic_group_fk;
ALTER TABLE topics DROP FOREIGN KEY topic_user_fk;
ALTER TABLE integration_users DROP FOREIGN KEY integration_user_user_fk;
ALTER TABLE integration_groups DROP FOREIGN KEY integration_group_user_fk;
DROP TABLE integration_updates;
DROP TABLE integration_tokens;
DROP TABLE direct_messages;
DROP TABLE comments;
DROP TABLE integration_topics;
DROP TABLE topics;
DROP TABLE integration_users;
DROP TABLE integration_groups;
DROP TABLE users;
DROP TABLE groups;