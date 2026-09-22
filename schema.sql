-- City Recreational Spaces Navigator Schema

CREATE DATABASE IF NOT EXISTS city_recreation;
USE city_recreation;

-- Drop tables if they exist to allow easy resetting
DROP TABLE IF EXISTS events;
DROP TABLE IF EXISTS reservations;
DROP TABLE IF EXISTS spaces;
DROP TABLE IF EXISTS facilities;
DROP TABLE IF EXISTS users;

-- Users Table
CREATE TABLE users (
    id INT AUTO_INCREMENT PRIMARY KEY,
    email VARCHAR(255) NOT NULL UNIQUE,
    password_hash VARCHAR(255) NOT NULL,
    phone_number VARCHAR(20),
    role ENUM('Guest', 'General', 'Paid', 'Admin') DEFAULT 'Guest',
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

-- Facilities Table (Parks, Centers, etc.)
CREATE TABLE facilities (
    id INT AUTO_INCREMENT PRIMARY KEY,
    name VARCHAR(255) NOT NULL,
    address VARCHAR(255),
    latitude DECIMAL(10, 8),
    longitude DECIMAL(11, 8),
    status ENUM('open', 'limited', 'closed') DEFAULT 'open'
);

-- Spaces Table (Specific courts or fields within a facility)
CREATE TABLE spaces (
    id INT AUTO_INCREMENT PRIMARY KEY,
    facility_id INT NOT NULL,
    name VARCHAR(255) NOT NULL,
    access_type ENUM('open-play', 'pay-to-play', 'reservation-only') DEFAULT 'open-play',
    FOREIGN KEY (facility_id) REFERENCES facilities(id) ON DELETE CASCADE
);

-- Reservations Table
CREATE TABLE reservations (
    id INT AUTO_INCREMENT PRIMARY KEY,
    user_id INT NOT NULL,
    space_id INT NOT NULL,
    start_time DATETIME NOT NULL,
    end_time DATETIME NOT NULL,
    status ENUM('pending', 'confirmed', 'cancelled', 'expired') DEFAULT 'pending',
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    FOREIGN KEY (user_id) REFERENCES users(id),
    FOREIGN KEY (space_id) REFERENCES spaces(id)
);

-- Events Table
CREATE TABLE events (
    id INT AUTO_INCREMENT PRIMARY KEY,
    reservation_id INT NOT NULL,
    title VARCHAR(255) NOT NULL,
    description TEXT,
    is_public BOOLEAN DEFAULT TRUE,
    FOREIGN KEY (reservation_id) REFERENCES reservations(id) ON DELETE CASCADE
);


-- INSERT MOCK DATA FOR PROTOTYPE
INSERT INTO facilities (id, name, address, latitude, longitude, status) VALUES 
(1, 'Central Recreation Center', '120 Civic Way', 37.3362, -121.8850, 'open'),
(2, 'Riverside Courts', '450 River Rd', 37.3302, -121.8820, 'limited'),
(3, 'Southside Pool', '88 Aqua St', 37.3252, -121.8860, 'open'),
(4, 'North Campus Gym', '100 University Ave', 37.3402, -121.8750, 'closed');

INSERT INTO spaces (facility_id, name, access_type) VALUES 
(1, 'Indoor basketball court A', 'reservation-only'),
(1, 'Fitness studio', 'reservation-only'),
(1, 'Community room', 'reservation-only'),
(2, 'Outdoor basketball court 1', 'open-play'),
(2, 'Outdoor basketball court 2', 'open-play');

-- We won't insert users or reservations yet, as the Explore page primarily needs facilities and spaces.
