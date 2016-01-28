# --- !Ups

INSERT INTO `topic_read_statuses` (`topic_id`, `user_id` )
SELECT `id`, `user_id` FROM `topics` t WHERE NOT EXISTS (SELECT `topic_id`, `user_id` FROM `topic_read_statuses` s WHERE s.`topic_id` = t.`id` AND s.`user_id` = t.`user_id`);

INSERT INTO `comment_read_statuses` (`comment_id`, `user_id` )
SELECT `id`, `user_id` FROM `comments` t WHERE NOT EXISTS (SELECT `comment_id`, `user_id` FROM `comment_read_statuses` s WHERE s.`comment_id` = t.`id` AND s.`user_id` = t.`user_id`);

# --- !Downs

