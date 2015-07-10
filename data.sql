INSERT INTO public."User" (id, login, name, avatar) VALUES (100000, 'andrey.cheptsov', 'Andrey Cheptsov', 'https://hackathon15.labs.intellij.net/hub/api/rest/avatar/f53fba19-dc48-4009-aedd-894615f6f0f1');
INSERT INTO public."User" (id, login, name, avatar) VALUES (100001, 'mazine', 'Maxim Mazin', 'https://sso.jetbrains.com/api/rest/avatar/5c7f7a0f-fe59-4f58-ab99-5260511f357f');

INSERT INTO public."Group" (id) VALUES ('ring-team');
INSERT INTO public."Group" (id) VALUES ('intellij');


INSERT INTO public."Topic" (id, "userId", "groupId", date, text) VALUES (200000, 2, 'ring-team', '2015-07-09 19:51:55.706000', 'сори за офтопик, в рамках хакатона  - никому не интересно было бы к рингу чат прикрутить? (замена slack)');

INSERT INTO public."Comment" (id, "topicId", "userId", "groupId", date, text)
VALUES (300000, 200000, 2, 'ring-team', '2015-07-09 19:54:27.183000', 'если кто-то знает почему этого делать нет смысла - буду тоже рад выслушать');
INSERT INTO public."Comment" (id, "topicId", "userId", "groupId", date, text)
VALUES (300001, 200000, 3, 'ring-team', '2015-07-09 19:57:18.356000', 'А что он должен уметь из того, чего нет в Slack?');
INSERT INTO public."Comment" (id, "topicId", "userId", "groupId", date, text)
VALUES (300002, 200000, 2, 'ring-team', '2015-07-09 20:06:42.262000', 'две вещи: первая - это topic-based структура канала. второе - интеграция из коробки с нашими продуктами');
INSERT INTO public."Comment" (id, "topicId", "userId", "groupId", date, text)
VALUES (300003, 200000, 2, 'ring-team', '2015-07-09 20:08:29.391000', 'второе - уже для пользователей ринга');
INSERT INTO public."Comment" (id, "topicId", "userId", "groupId", date, text)
VALUES (300004, 200000, 3, 'ring-team', '2015-07-09 20:16:28.050000',
        'Канал с topic-based структурой — это же просто форум.');
INSERT INTO public."Comment" (id, "topicId", "userId", "groupId", date, text)
VALUES (300005, 200000, 3, 'ring-team', '2015-07-09 20:30:10.516000', 'Мы как раз недавно один такой похоронили');
