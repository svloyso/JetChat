# --- !Ups

ALTER TABLE users ADD COLUMN isbot BOOLEAN DEFAULT FALSE;
CREATE TABLE bots (
  id BIGINT AUTO_INCREMENT,
  user_id BIGINT,
  name VARCHAR(128),
  code TEXT,
  is_active BOOLEAN,
  PRIMARY KEY(id),
  FOREIGN KEY(user_id) REFERENCES users(id)
);

# --- !Downs

ALTER TABLE users DROP COLUMN isbot;
DROP TABLE bots;