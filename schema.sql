-- schema for city rec spaces navigator (MySQL 8)
-- hand-written, no ORM
-- enforces the rules from the write-up:
-- end_time > start_time, no dup time slots per space
-- one active lock per slot, one CONFIRMED reservation per slot
-- image belongs to an event xor a comment
-- domain checks on access_mode / status / visibility / role_name

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

-- location hierarchy: city -> facility -> space -> time_slot

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

-- accounts: role -> user -> preference

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
    `payment_method_stub` VARCHAR(100), -- stub only, no real card number
    FOREIGN KEY (`role_id`) REFERENCES `ROLE`(`role_id`)
);

-- user has 0 or 1 preference (user_id is PK)
CREATE TABLE `PREFERENCE` (
    `user_id` INT PRIMARY KEY,
    `default_city_id` INT, -- nullable, 0 or 1 default city
    `default_sport` VARCHAR(100),
    `map_radius` INT NOT NULL DEFAULT 10,
    `notify_pref` VARCHAR(30) NOT NULL DEFAULT 'EMAIL',
    `calendar_view` VARCHAR(20) NOT NULL DEFAULT 'MONTH',
    FOREIGN KEY (`user_id`) REFERENCES `USER`(`user_id`),
    FOREIGN KEY (`default_city_id`) REFERENCES `CITY`(`city_id`)
);

-- booking: slot_lock (temp hold) -> reservation -> event
-- unique(slot_id) = one active lock per slot
-- expired locks get deleted in the app layer before re-locking
CREATE TABLE `SLOT_LOCK` (
    `lock_id` INT PRIMARY KEY AUTO_INCREMENT,
    `user_id` INT NOT NULL,
    `slot_id` INT NOT NULL UNIQUE,
    `created_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    `expires_at` DATETIME NOT NULL,
    FOREIGN KEY (`user_id`) REFERENCES `USER`(`user_id`),
    FOREIGN KEY (`slot_id`) REFERENCES `TIME_SLOT`(`slot_id`)
);

-- active_slot_id is NULL unless CONFIRMED
-- unique on it = one CONFIRMED per slot, cancelled history unlimited
CREATE TABLE `RESERVATION` (
    `reservation_id` INT PRIMARY KEY AUTO_INCREMENT,
    `user_id` INT NOT NULL,
    `slot_id` INT NOT NULL,
    `status` VARCHAR(30) NOT NULL DEFAULT 'CONFIRMED',
    `active_slot_id` INT GENERATED ALWAYS AS (CASE WHEN `status` = 'CONFIRMED' THEN `slot_id` ELSE NULL END) STORED,
    `created_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
