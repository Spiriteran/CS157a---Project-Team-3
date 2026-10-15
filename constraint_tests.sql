USE city_recreation;

-- TIME_SLOT end_time > start_time
-- should fail CHECK constraint
INSERT INTO CITY (name, state) VALUES ('Test City', 'CA');
INSERT INTO FACILITY (city_id, name) VALUES (1, 'Test Facility');
INSERT INTO SPACE (facility_id, name, access_mode) VALUES (1, 'Test Space', 'OPEN_PLAY');
-- fail:
-- INSERT INTO TIME_SLOT (space_id, start_time, end_time) VALUES (1, '2026-10-10 10:00:00', '2026-10-10 09:00:00'); 

-- TIME_SLOT duplicate times (should be unique)
-- should fail UNIQUE constraint
INSERT INTO TIME_SLOT (space_id, start_time, end_time) VALUES (1, '2026-10-10 10:00:00', '2026-10-10 11:00:00');
-- fail:
-- INSERT INTO TIME_SLOT (space_id, start_time, end_time) VALUES (1, '2026-10-10 10:00:00', '2026-10-10 11:00:00'); 

-- SLOT_LOCK single active hold
-- should fail UNIQUE constraint
INSERT INTO ROLE (role_name) VALUES ('GENERAL_USER');
INSERT INTO USER (role_id, email, password_hash) VALUES (1, 'test@example.com', 'hash');
INSERT INTO SLOT_LOCK (user_id, slot_id, expires_at) VALUES (1, 1, '2026-10-10 10:05:00');
-- fail:
-- INSERT INTO SLOT_LOCK (user_id, slot_id, expires_at) VALUES (1, 1, '2026-10-10 10:10:00');

-- RESERVATION active_slot_id
-- can have multiple CANCELLED reservations for the same slot, but only one CONFIRMED
INSERT INTO RESERVATION (user_id, slot_id, status) VALUES (1, 1, 'CANCELLED'); 
INSERT INTO RESERVATION (user_id, slot_id, status) VALUES (1, 1, 'CANCELLED'); 
INSERT INTO RESERVATION (user_id, slot_id, status) VALUES (1, 1, 'CONFIRMED'); 
-- fail:
-- INSERT INTO RESERVATION (user_id, slot_id, status) VALUES (1, 1, 'CONFIRMED'); 

-- IMAGE exclusive belonging
-- should fail CHECK constraint (both non-null/null cases)
INSERT INTO RESERVATION (user_id, slot_id, status) VALUES (1, 2, 'CONFIRMED'); -- Need another slot first...
INSERT INTO EVENT (reservation_id, title) VALUES (2, 'Test Event');
INSERT INTO COMMENT (event_id, user_id, body) VALUES (1, 1, 'Test Comment');

-- fail: both not null
-- INSERT INTO IMAGE (user_id, event_id, comment_id, file_path) VALUES (1, 1, 1, '/path'); 
-- fail: both null
-- INSERT INTO IMAGE (user_id, event_id, comment_id, file_path) VALUES (1, NULL, NULL, '/path'); 
