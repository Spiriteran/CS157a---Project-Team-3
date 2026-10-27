-- "General User View" functional reqs
-- registered user, email verified
--
-- run against the schema in schema.sql:
--   mysql -u root city_recreation < functional_requirements.sql

USE city_recreation;

-- user sign in (fetch account + role)
--   verifies login identity and gets role tier
SELECT  u.user_id,
        u.role_id,
        r.role_name,
        u.email,
        u.password_hash,
        u.phone_num,
        u.email_verified,
        u.twofa_enabled
FROM        USER u
JOIN        ROLE r ON r.role_id = u.role_id
WHERE   u.email = 'user1@example.com';   -- login email


-- user registration (create account)
--   default GENERAL_USER tier; general users don't need 2FA/payment yet
INSERT INTO USER (role_id, email, password_hash, phone_num, email_verified, twofa_enabled)
VALUES (1, 'newuser@example.com', 'hashedpwd', '555-0199', TRUE, FALSE);


-- view/manage profile preferences (default city, sport, map radius)
--   weak entity (user has 0 or 1 preference row)
SELECT  p.user_id,
        p.default_city_id,
        c.name        AS default_city_name,
        p.default_sport,
        p.map_radius,
        p.notify_pref,
        p.calendar_view
FROM        PREFERENCE p
LEFT JOIN   CITY       c ON c.city_id = p.default_city_id
WHERE   p.user_id = 1;                   -- user profile


-- update preferences
--   upserts if user already has preferences set
INSERT INTO PREFERENCE (user_id, default_city_id, default_sport, map_radius, notify_pref, calendar_view)
VALUES (1, 1, 'Soccer', 10, 'EMAIL', 'MONTH')
ON DUPLICATE KEY UPDATE
    default_city_id = VALUES(default_city_id),
    default_sport   = VALUES(default_sport),
    map_radius      = VALUES(map_radius),
    notify_pref     = VALUES(notify_pref),
    calendar_view   = VALUES(calendar_view);


-- view favorite spaces
--   general users can save and view their favorited spaces
SELECT  s.space_id,
        s.name        AS space_name,
        s.space_type,
        s.access_mode,
        s.price,
        f.name        AS facility_name,
        c.name        AS city_name,
        uf.created_at AS favorited_at
FROM        USER_FAVORITE uf
JOIN        SPACE         s  ON s.space_id    = uf.space_id
JOIN        FACILITY      f  ON f.facility_id = s.facility_id
JOIN        CITY          c  ON c.city_id     = f.city_id
WHERE   uf.user_id = 1
ORDER BY uf.created_at DESC;


-- add to favorites / remove from favorites
INSERT IGNORE INTO USER_FAVORITE (user_id, space_id, created_at)
VALUES (1, 2, NOW());

DELETE FROM USER_FAVORITE
WHERE user_id = 1 AND space_id = 2;


-- view event attendees and rsvp status
--   general users can join public events
SELECT  ep.user_id,
        u.email       AS user_email,
        ep.status,
        ep.joined_time
FROM        EVENT_PARTICIPANT ep
JOIN        USER              u  ON u.user_id = ep.user_id
WHERE   ep.event_id = 1
ORDER BY ep.joined_time ASC;


-- join event / update rsvp (GOING / MAYBE / DECLINED)
INSERT INTO EVENT_PARTICIPANT (user_id, event_id, status, joined_time)
VALUES (1, 1, 'GOING', NOW())
ON DUPLICATE KEY UPDATE
    status      = VALUES(status),
    joined_time = NOW();


-- view comments on an event
--   discussion thread for public events
SELECT  c.comment_id,
        c.event_id,
        c.user_id,
        u.email        AS user_email,
        c.body,
        c.created_time
FROM        COMMENT c
JOIN        USER    u ON u.user_id = c.user_id
WHERE   c.event_id = 1
ORDER BY c.created_time ASC;


-- post a comment on an event
INSERT INTO COMMENT (event_id, user_id, body, created_time)
VALUES (1, 1, 'Looking forward to this game!', NOW());


-- delete own comment
--   users may only delete their own comments
DELETE FROM COMMENT
WHERE comment_id = 1 AND user_id = 1;
