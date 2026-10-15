USE city_recreation;

-- Constraint Test 1: TIME_SLOT end_time > start_time
-- Should fail CHECK constraint
INSERT INTO CITY (name, state) VALUES ('Test City', 'CA');
INSERT INTO FACILITY (city_id, name) VALUES (1, 'Test Facility');
INSERT INTO SPACE (facility_id, name, access_mode) VALUES (1, 'Test Space', 'OPEN_PLAY');
-- Un-commenting below will fail:
-- INSERT INTO TIME_SLOT (space_id, start_time, end_time) VALUES (1, '2026-10-10 10:00:00', '2026-10-10 09:00:00'); 

-- Constraint Test 2: TIME_SLOT duplicate times
-- Should fail UNIQUE constraint on the second insert
INSERT INTO TIME_SLOT (space_id, start_time, end_time) VALUES (1, '2026-10-10 10:00:00', '2026-10-10 11:00:00');
-- Un-commenting below will fail:
-- INSERT INTO TIME_SLOT (space_id, start_time, end_time) VALUES (1, '2026-10-10 10:00:00', '2026-10-10 11:00:00'); 

-- Constraint Test 3: SLOT_LOCK single active hold
-- Should fail UNIQUE constraint on the second insert
INSERT INTO ROLE (role_name) VALUES ('GENERAL_USER');
INSERT INTO USER (role_id, email, password_hash) VALUES (1, 'test@example.com', 'hash');
INSERT INTO SLOT_LOCK (user_id, slot_id, expires_at) VALUES (1, 1, '2026-10-10 10:05:00');
-- Un-commenting below will fail:
-- INSERT INTO SLOT_LOCK (user_id, slot_id, expires_at) VALUES (1, 1, '2026-10-10 10:10:00');

-- Constraint Test 4: RESERVATION active_slot_id
-- We can have multiple CANCELLED reservations for the same slot, but only one CONFIRMED.
INSERT INTO RESERVATION (user_id, slot_id, status) VALUES (1, 1, 'CANCELLED'); 
INSERT INTO RESERVATION (user_id, slot_id, status) VALUES (1, 1, 'CANCELLED'); 
INSERT INTO RESERVATION (user_id, slot_id, status) VALUES (1, 1, 'CONFIRMED'); 
-- Un-commenting below will fail (duplicate active_slot_id):
-- INSERT INTO RESERVATION (user_id, slot_id, status) VALUES (1, 1, 'CONFIRMED'); 

-- Constraint Test 5: IMAGE exclusive belonging
-- Should fail CHECK constraint (both non-null and both null cases)
INSERT INTO RESERVATION (user_id, slot_id, status) VALUES (1, 2, 'CONFIRMED'); -- Need another slot first...
INSERT INTO EVENT (reservation_id, title) VALUES (2, 'Test Event');
INSERT INTO COMMENT (event_id, user_id, body) VALUES (1, 1, 'Test Comment');

-- Un-commenting below will fail (both not null):
-- INSERT INTO IMAGE (user_id, event_id, comment_id, file_path) VALUES (1, 1, 1, '/path'); 
-- Un-commenting below will fail (both null):
-- INSERT INTO IMAGE (user_id, event_id, comment_id, file_path) VALUES (1, NULL, NULL, '/path'); 
