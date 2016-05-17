# --- !Ups

ALTER TABLE users ADD COLUMN isbot BOOLEAN DEFAULT FALSE;
CREATE TABLE bots (
  user_id BIGINT,
  code TEXT,
  state TEXT,
  is_active BOOLEAN,
  PRIMARY KEY(user_id),
  FOREIGN KEY(user_id) REFERENCES users(id)
);

# --- !Downs

ALTER TABLE users DROP COLUMN isbot;
DROP TABLE bots;