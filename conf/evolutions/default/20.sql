# --- !Ups

INSERT INTO `last_direct_messages` (min_user_id, max_user_id, date, text, direct_message_id)
  SELECT
    ua.`id`,
    ub.`id`,
    d.`date`,
    d.`text`,
    d.`id`
  FROM `users` ua, `users` ub, `direct_messages` d
  WHERE ua.`id` < ub.`id` AND d.`date` = (SELECT max(ll.`date`)
                                          FROM `direct_messages` ll
                                          WHERE (ll.`to_user_id` = ua.`id` AND
                                                 ll.`from_user_id` = ub.`id`
                                                ) OR (ll.`from_user_id` = ua.`id` AND
                                                      ll.`to_user_id` = ub.`id`));
# --- !Downs

DELETE FROM `last_direct_messages`;