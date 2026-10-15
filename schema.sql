-- =====================================================================
-- City Recreational Spaces Navigator — Relational Schema (MySQL 8.x)
-- Team 3 — CS157A
--
-- This is the AUTHORITATIVE schema. It is hand-written SQL (no ORM /
-- no generator) and enforces the integrity rules described in the
-- functional-requirements write-up:
--   * TIME_SLOT end_time must be after start_time
--   * No two TIME_SLOTs for the same space at the same start/end
--   * At most one active SLOT_LOCK per time slot
--   * At most one CONFIRMED reservation per time slot (via a generated
--     column + UNIQUE), while allowing many historical CANCELLED rows
--   * An IMAGE belongs to exactly one of EVENT xor COMMENT
--   * Domain CHECKs on access_mode / status / visibility / role_name
-- =====================================================================

CREATE DATABASE IF NOT EXISTS city_recreation;
USE city_recreation;

SET FOREIGN_KEY_CHECKS = 0;
DROP TABLE IF EXISTS `SYSTEM_LOG`;
DROP TABLE IF EXISTS `IMAGE`;
DROP TABLE IF EXISTS `COMMENT`;
DROP TABLE IF EXISTS `EVENT_PARTICIPANT`;
DROP TABLE IF EXISTS `EVENT`;
DROP TABLE IF EXISTS `RESERVATION`;
DROP TABLE IF EXISTS `SLOT_LOCK`;
DROP TABLE IF EXISTS `USER_FAVORITE`;
DROP TABLE IF EXISTS `PREFERENCE`;
DROP TABLE IF EXISTS `TIME_SLOT`;
DROP TABLE IF EXISTS `SPACE`;
DROP TABLE IF EXISTS `FACILITY`;
DROP TABLE IF EXISTS `USER`;
DROP TABLE IF EXISTS `CITY`;
DROP TABLE IF EXISTS `ROLE`;
SET FOREIGN_KEY_CHECKS = 1;

-- ---------------------------------------------------------------------
-- Core location hierarchy: CITY -> FACILITY -> SPACE -> TIME_SLOT
-- ---------------------------------------------------------------------

CREATE TABLE `CITY` (
    `city_id` INT PRIMARY KEY AUTO_INCREMENT,
    `name` VARCHAR(100) NOT NULL,
    `state` CHAR(2) NOT NULL,
    `is_active` BOOLEAN NOT NULL DEFAULT TRUE
);

CREATE TABLE `FACILITY` (
    `facility_id` INT PRIMARY KEY AUTO_INCREMENT,
    `city_id` INT NOT NULL,
    `name` VARCHAR(150) NOT NULL,
    `address` VARCHAR(255),
    `latitude` DECIMAL(9,6),
    `longitude` DECIMAL(9,6),
    `is_active` BOOLEAN NOT NULL DEFAULT TRUE,
    FOREIGN KEY (`city_id`) REFERENCES `CITY`(`city_id`)
);

CREATE TABLE `SPACE` (
    `space_id` INT PRIMARY KEY AUTO_INCREMENT,
    `facility_id` INT NOT NULL,
    `name` VARCHAR(150) NOT NULL,
    `space_type` VARCHAR(100),
    `access_mode` VARCHAR(30) NOT NULL,
    `price` DECIMAL(10,2) NOT NULL DEFAULT 0.00,
    `is_active` BOOLEAN NOT NULL DEFAULT TRUE,
    FOREIGN KEY (`facility_id`) REFERENCES `FACILITY`(`facility_id`),
    CONSTRAINT `chk_space_access_mode` CHECK (`access_mode` IN ('OPEN_PLAY', 'PAY_TO_PLAY', 'RESERVATION_ONLY'))
);

CREATE TABLE `TIME_SLOT` (
    `slot_id` INT PRIMARY KEY AUTO_INCREMENT,
    `space_id` INT NOT NULL,
    `start_time` DATETIME NOT NULL,
    `end_time` DATETIME NOT NULL,
    `status` VARCHAR(30) NOT NULL DEFAULT 'AVAILABLE',
    FOREIGN KEY (`space_id`) REFERENCES `SPACE`(`space_id`),
    CONSTRAINT `chk_timeslot_time` CHECK (`end_time` > `start_time`),
    CONSTRAINT `chk_timeslot_status` CHECK (`status` IN ('AVAILABLE', 'RESERVED', 'OPEN_PLAY', 'CLOSED')),
    UNIQUE KEY `uk_space_time` (`space_id`, `start_time`, `end_time`)
);

-- ---------------------------------------------------------------------
-- Accounts: ROLE -> USER -> PREFERENCE
-- ---------------------------------------------------------------------

CREATE TABLE `ROLE` (
    `role_id` INT PRIMARY KEY AUTO_INCREMENT,
    `role_name` VARCHAR(50) NOT NULL UNIQUE,
    CONSTRAINT `chk_role_name` CHECK (`role_name` IN ('GENERAL_USER', 'RESERVATION_USER', 'ADMIN'))
);

CREATE TABLE `USER` (
    `user_id` INT PRIMARY KEY AUTO_INCREMENT,
    `role_id` INT NOT NULL,
    `email` VARCHAR(255) NOT NULL UNIQUE,
    `password_hash` VARCHAR(255) NOT NULL,
    `phone_num` VARCHAR(30),
    `email_verified` BOOLEAN NOT NULL DEFAULT FALSE,
    `twofa_enabled` BOOLEAN NOT NULL DEFAULT FALSE,
    `billing_address` VARCHAR(255),
    `payment_method_stub` VARCHAR(100), -- placeholder only; never stores a real card number
    FOREIGN KEY (`role_id`) REFERENCES `ROLE`(`role_id`)
);

-- A USER has 0 or 1 PREFERENCE (user_id is PK, so at most one row per user)
CREATE TABLE `PREFERENCE` (
    `user_id` INT PRIMARY KEY,
    `default_city_id` INT, -- nullable: 0 or 1 default city
    `default_sport` VARCHAR(100),
    `map_radius` INT NOT NULL DEFAULT 10,
    `notify_pref` VARCHAR(30) NOT NULL DEFAULT 'EMAIL',
    `calendar_view` VARCHAR(20) NOT NULL DEFAULT 'MONTH',
    FOREIGN KEY (`user_id`) REFERENCES `USER`(`user_id`),
    FOREIGN KEY (`default_city_id`) REFERENCES `CITY`(`city_id`)
);

-- ---------------------------------------------------------------------
-- Booking: SLOT_LOCK (temporary hold) -> RESERVATION -> EVENT
-- ---------------------------------------------------------------------

-- UNIQUE(slot_id) => at most one active lock per slot. Expired locks are
-- deleted transactionally in the application layer before re-acquiring.
CREATE TABLE `SLOT_LOCK` (
    `lock_id` INT PRIMARY KEY AUTO_INCREMENT,
    `user_id` INT NOT NULL,
    `slot_id` INT NOT NULL UNIQUE,
    `created_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    `expires_at` DATETIME NOT NULL,
    FOREIGN KEY (`user_id`) REFERENCES `USER`(`user_id`),
    FOREIGN KEY (`slot_id`) REFERENCES `TIME_SLOT`(`slot_id`)
);

-- active_slot_id is NULL unless the row is CONFIRMED; UNIQUE on it means
-- at most one CONFIRMED reservation per slot, with unlimited CANCELLED history.
CREATE TABLE `RESERVATION` (
    `reservation_id` INT PRIMARY KEY AUTO_INCREMENT,
    `user_id` INT NOT NULL,
    `slot_id` INT NOT NULL,
    `status` VARCHAR(30) NOT NULL DEFAULT 'CONFIRMED',
    `active_slot_id` INT GENERATED ALWAYS AS (CASE WHEN `status` = 'CONFIRMED' THEN `slot_id` ELSE NULL END) STORED,
    `created_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    FOREIGN KEY (`user_id`) REFERENCES `USER`(`user_id`),
    FOREIGN KEY (`slot_id`) REFERENCES `TIME_SLOT`(`slot_id`),
    CONSTRAINT `chk_reservation_status` CHECK (`status` IN ('CONFIRMED', 'CANCELLED')),
    UNIQUE KEY `uk_active_slot` (`active_slot_id`)
);

CREATE TABLE `EVENT` (
    `event_id` INT PRIMARY KEY AUTO_INCREMENT,
    `reservation_id` INT NOT NULL UNIQUE,
    `title` VARCHAR(150) NOT NULL,
    `description` TEXT,
    `visibility` VARCHAR(20) NOT NULL DEFAULT 'PUBLIC',
    FOREIGN KEY (`reservation_id`) REFERENCES `RESERVATION`(`reservation_id`),
    CONSTRAINT `chk_event_visibility` CHECK (`visibility` IN ('PUBLIC', 'PRIVATE'))
);

-- ---------------------------------------------------------------------
-- Social: EVENT_PARTICIPANT, COMMENT, IMAGE
-- ---------------------------------------------------------------------

CREATE TABLE `EVENT_PARTICIPANT` (
    `user_id` INT NOT NULL,
    `event_id` INT NOT NULL,
    `joined_time` DATETIME,
    `status` VARCHAR(30) NOT NULL DEFAULT 'GOING',
    PRIMARY KEY (`user_id`, `event_id`),
    FOREIGN KEY (`user_id`) REFERENCES `USER`(`user_id`),
    FOREIGN KEY (`event_id`) REFERENCES `EVENT`(`event_id`),
    CONSTRAINT `chk_participant_status` CHECK (`status` IN ('GOING', 'MAYBE', 'DECLINED', 'INVITED'))
);

CREATE TABLE `COMMENT` (
    `comment_id` INT PRIMARY KEY AUTO_INCREMENT,
    `event_id` INT NOT NULL,
    `user_id` INT NOT NULL,
    `body` TEXT NOT NULL,
    `created_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    FOREIGN KEY (`event_id`) REFERENCES `EVENT`(`event_id`),
    FOREIGN KEY (`user_id`) REFERENCES `USER`(`user_id`)
);

-- An IMAGE belongs to exactly one of EVENT xor COMMENT (never both, never neither)
CREATE TABLE `IMAGE` (
    `image_id` INT PRIMARY KEY AUTO_INCREMENT,
    `user_id` INT NOT NULL,
    `event_id` INT,
    `comment_id` INT,
    `file_path` VARCHAR(500) NOT NULL,
    `image_type` VARCHAR(50),
    `img_size` INT,
    FOREIGN KEY (`user_id`) REFERENCES `USER`(`user_id`),
    FOREIGN KEY (`event_id`) REFERENCES `EVENT`(`event_id`),
    FOREIGN KEY (`comment_id`) REFERENCES `COMMENT`(`comment_id`),
    CONSTRAINT `chk_image_association` CHECK (
        (`event_id` IS NOT NULL AND `comment_id` IS NULL) OR
        (`event_id` IS NULL AND `comment_id` IS NOT NULL)
    )
);

CREATE TABLE `SYSTEM_LOG` (
    `log_id` INT PRIMARY KEY AUTO_INCREMENT,
    `user_id` INT,
    `action` VARCHAR(255) NOT NULL,
    `logged_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    FOREIGN KEY (`user_id`) REFERENCES `USER`(`user_id`)
);

CREATE TABLE `USER_FAVORITE` (
    `user_id` INT NOT NULL,
    `space_id` INT NOT NULL,
    `created_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (`user_id`, `space_id`),
    FOREIGN KEY (`user_id`) REFERENCES `USER`(`user_id`),
    FOREIGN KEY (`space_id`) REFERENCES `SPACE`(`space_id`)
);

-- =====================================================================
-- MOCK DATA (>= 10 rows per core table) — hand-written, consistent with
-- every constraint above.
-- =====================================================================

INSERT INTO `ROLE` (`role_name`) VALUES
('GENERAL_USER'), ('RESERVATION_USER'), ('ADMIN');

INSERT INTO `CITY` (`name`, `state`, `is_active`) VALUES
('San Jose', 'CA', TRUE), ('Santa Clara', 'CA', TRUE), ('Sunnyvale', 'CA', TRUE),
('Mountain View', 'CA', TRUE), ('Palo Alto', 'CA', TRUE), ('Cupertino', 'CA', TRUE),
('Milpitas', 'CA', TRUE), ('Fremont', 'CA', TRUE), ('Los Gatos', 'CA', TRUE), ('Campbell', 'CA', TRUE);

INSERT INTO `USER` (`role_id`, `email`, `password_hash`, `phone_num`, `email_verified`, `twofa_enabled`) VALUES
(1, 'user1@example.com', 'hashedpwd1', '555-0001', TRUE, TRUE),
(2, 'user2@example.com', 'hashedpwd2', '555-0002', TRUE, TRUE),
(2, 'user3@example.com', 'hashedpwd3', '555-0003', TRUE, FALSE),
(3, 'admin1@example.com', 'hashedpwd4', '555-0004', TRUE, TRUE),
(1, 'user4@example.com', 'hashedpwd5', '555-0005', FALSE, FALSE),
(1, 'user5@example.com', 'hashedpwd6', '555-0006', TRUE, FALSE),
(2, 'user6@example.com', 'hashedpwd7', '555-0007', TRUE, TRUE),
(3, 'mod1@example.com', 'hashedpwd8', '555-0008', TRUE, TRUE),
(2, 'manager1@example.com', 'hashedpwd9', '555-0009', TRUE, TRUE),
(1, 'organizer1@example.com', 'hashedpwd10', '555-0010', TRUE, TRUE);

INSERT INTO `PREFERENCE` (`user_id`, `default_city_id`, `default_sport`, `map_radius`, `notify_pref`, `calendar_view`) VALUES
(1, 1, 'Basketball', 10, 'EMAIL', 'MONTH'),
(2, 1, 'Soccer', 15, 'SMS', 'WEEK'),
(3, 2, 'Tennis', 5, 'NONE', 'DAY'),
(4, 1, 'Swimming', 20, 'EMAIL', 'MONTH'),
(5, 3, 'Volleyball', 10, 'NONE', 'WEEK'),
(6, 4, 'Basketball', 25, 'EMAIL', 'MONTH'),
(7, 5, 'Baseball', 15, 'SMS', 'WEEK'),
(8, 6, 'Soccer', 5, 'EMAIL', 'DAY'),
(9, 7, 'Tennis', 10, 'EMAIL', 'MONTH'),
(10, 8, 'Badminton', 20, 'EMAIL', 'MONTH');

INSERT INTO `FACILITY` (`city_id`, `name`, `address`, `latitude`, `longitude`, `is_active`) VALUES
(1, 'Central Park Gym', '100 Main St', 37.3362, -121.8850, TRUE),
(1, 'Southside Fields', '200 South Ave', 37.3302, -121.8820, TRUE),
(2, 'Rivermark Courts', '300 River Rd', 37.3402, -121.8750, TRUE),
(3, 'Sunnyvale Comm Center', '400 Sun Way', 37.3500, -121.9100, TRUE),
(4, 'Shoreline Park', '500 Shore Blvd', 37.3600, -121.9500, TRUE),
(5, 'Mitchell Park', '600 Middlefield', 37.3200, -121.8500, TRUE),
(6, 'Memorial Park', '700 Stevens Creek', 37.3380, -121.8800, TRUE),
(7, 'Milpitas Sports Center', '800 E Calaveras', 37.3450, -121.9000, TRUE),
(8, 'Central Park Fremont', '900 Paseo Padre', 37.3311, -121.8900, TRUE),
(9, 'Vasona Park', '1000 Blossom Hill', 37.3252, -121.8860, TRUE);

INSERT INTO `SPACE` (`facility_id`, `name`, `space_type`, `access_mode`, `price`, `is_active`) VALUES
(1, 'Indoor Court A', 'Basketball Court', 'RESERVATION_ONLY', 15.00, TRUE),
(1, 'Indoor Court B', 'Basketball Court', 'RESERVATION_ONLY', 15.00, TRUE),
(2, 'Field 1', 'Soccer Field', 'RESERVATION_ONLY', 25.00, TRUE),
(2, 'Field 2', 'Soccer Field', 'OPEN_PLAY', 0.00, TRUE),
(3, 'Tennis Court 1', 'Tennis Court', 'RESERVATION_ONLY', 10.00, TRUE),
(4, 'Multi-purpose Room', 'Studio', 'RESERVATION_ONLY', 20.00, TRUE),
(5, 'Volleyball Sand 1', 'Volleyball Court', 'OPEN_PLAY', 0.00, TRUE),
(6, 'Pickleball 1', 'Pickleball Court', 'OPEN_PLAY', 0.00, TRUE),
(7, 'Baseball Diamond A', 'Baseball Field', 'RESERVATION_ONLY', 30.00, TRUE),
(8, 'Lap Pool Lane 1', 'Pool', 'PAY_TO_PLAY', 5.00, TRUE);

INSERT INTO `TIME_SLOT` (`space_id`, `start_time`, `end_time`, `status`) VALUES
(1, '2026-10-01 08:00:00', '2026-10-01 09:00:00', 'RESERVED'),
(1, '2026-10-01 09:00:00', '2026-10-01 10:00:00', 'AVAILABLE'),
(2, '2026-10-01 10:00:00', '2026-10-01 11:00:00', 'RESERVED'),
(3, '2026-10-01 12:00:00', '2026-10-01 14:00:00', 'RESERVED'),
(5, '2026-10-01 15:00:00', '2026-10-01 16:00:00', 'AVAILABLE'),
(6, '2026-10-02 08:00:00', '2026-10-02 10:00:00', 'RESERVED'),
(7, '2026-10-02 11:00:00', '2026-10-02 12:00:00', 'AVAILABLE'),
(8, '2026-10-02 13:00:00', '2026-10-02 15:00:00', 'RESERVED'),
(9, '2026-10-03 09:00:00', '2026-10-03 12:00:00', 'RESERVED'),
(10, '2026-10-03 06:00:00', '2026-10-03 07:00:00', 'AVAILABLE');

-- Each lock targets a distinct slot (UNIQUE(slot_id)). Original mock reused
-- slot 2 twice; that is intentionally removed to respect the constraint.
INSERT INTO `SLOT_LOCK` (`user_id`, `slot_id`, `created_at`, `expires_at`) VALUES
(2, 2, '2026-09-30 10:00:00', '2026-09-30 10:15:00'),
(3, 5, '2026-09-30 10:10:00', '2026-09-30 10:25:00'),
(4, 7, '2026-09-30 11:00:00', '2026-09-30 11:15:00'),
(7, 10, '2026-09-30 11:00:00', '2026-09-30 11:15:00');

-- Confirmed reservations each own a distinct slot (uk_active_slot);
-- cancelled rows may freely share slots with confirmed/other cancelled rows.
INSERT INTO `RESERVATION` (`user_id`, `slot_id`, `status`) VALUES
(3, 1, 'CONFIRMED'),
(7, 3, 'CONFIRMED'),
(8, 4, 'CONFIRMED'),
(9, 6, 'CONFIRMED'),
(10, 8, 'CONFIRMED'),
(4, 9, 'CONFIRMED'),
(2, 10, 'CANCELLED'),
(1, 2, 'CANCELLED'),
(5, 5, 'CANCELLED'),
(6, 7, 'CANCELLED');

INSERT INTO `EVENT` (`reservation_id`, `title`, `description`, `visibility`) VALUES
(1, 'Morning 5v5 Basketball', 'Casual pickup game', 'PUBLIC'),
(2, 'Youth Soccer Scrimmage', 'Local U12 teams', 'PUBLIC'),
(3, 'Corporate Retreat', 'Private event', 'PRIVATE'),
(4, 'Dance Rehearsal', 'Preparing for the showcase', 'PRIVATE'),
(5, 'Pickleball Tournament', 'Round robin', 'PUBLIC'),
(6, 'Little League Match', 'Championship game', 'PUBLIC'),
(7, 'Cancelled Event 1', 'No longer happening', 'PUBLIC'),
(8, 'Cancelled Event 2', 'No longer happening', 'PUBLIC'),
(9, 'Cancelled Event 3', 'No longer happening', 'PUBLIC'),
(10, 'Cancelled Event 4', 'No longer happening', 'PUBLIC');

INSERT INTO `EVENT_PARTICIPANT` (`user_id`, `event_id`, `status`) VALUES
(1, 1, 'GOING'), (2, 1, 'GOING'), (4, 1, 'MAYBE'),
(3, 2, 'GOING'), (5, 2, 'GOING'),
(6, 5, 'GOING'), (7, 5, 'GOING'), (8, 5, 'DECLINED'),
(9, 6, 'GOING'), (10, 6, 'GOING');

INSERT INTO `COMMENT` (`event_id`, `user_id`, `body`) VALUES
(1, 1, 'I will bring an extra ball!'),
(1, 2, 'Sounds good, see you there.'),
(2, 3, 'Are parents allowed to watch?'),
(2, 5, 'Yes, bleachers are open.'),
(5, 6, 'What time does the first bracket start?'),
(5, 7, 'We start promptly at 1pm.'),
(6, 9, 'Go Team A!'),
(6, 10, 'Should be a great match.'),
(1, 4, 'I might be 5 mins late.'),
(2, 1, 'Can anyone give me a ride?');

INSERT INTO `IMAGE` (`user_id`, `event_id`, `comment_id`, `file_path`, `image_type`, `img_size`) VALUES
(1, 1, NULL, '/images/events/bball1.jpg', 'image/jpeg', 1024),
(2, 1, NULL, '/images/events/bball2.jpg', 'image/jpeg', 2048),
(3, 2, NULL, '/images/events/soccer1.jpg', 'image/jpeg', 1500),
(1, NULL, 1, '/images/comments/ball.jpg', 'image/png', 500),
(5, 2, NULL, '/images/events/field.jpg', 'image/jpeg', 3000),
(6, 5, NULL, '/images/events/bracket.png', 'image/png', 1200),
(9, 6, NULL, '/images/events/baseball.jpg', 'image/jpeg', 1800),
(4, NULL, 9, '/images/comments/traffic.jpg', 'image/jpeg', 800),
(7, 5, NULL, '/images/events/paddle.jpg', 'image/jpeg', 900),
(10, 6, NULL, '/images/events/trophy.jpg', 'image/jpeg', 2200);

INSERT INTO `SYSTEM_LOG` (`user_id`, `action`) VALUES
(1, 'USER_LOGIN'),
(2, 'UPDATE_PROFILE'),
(3, 'CREATE_RESERVATION'),
(4, 'APPROVE_EVENT'),
(5, 'CANCEL_RESERVATION'),
(6, 'UPLOAD_IMAGE'),
(7, 'POST_COMMENT'),
(8, 'USER_LOGOUT'),
(NULL, 'SYSTEM_BACKUP'),
(NULL, 'CLEANUP_EXPIRED_LOCKS');

INSERT INTO `USER_FAVORITE` (`user_id`, `space_id`) VALUES
(1, 1), (1, 2), (2, 3), (3, 5), (4, 1),
(5, 7), (6, 8), (7, 9), (8, 10), (9, 4), (10, 6);
