-- =====================================================================
-- City Recreational Spaces Navigator — Functional Requirements (SQL)
-- Team 3 — CS157A
--
-- Five GUEST-level functional requirements, taken directly from the
-- "Guest View" of the functional-requirements write-up. Guests have
-- read-only access, so each requirement is expressed as a hand-written
-- SELECT. These are the queries the application layer (Tomcat/JSP) will
-- run to serve the public, account-free experience.
--
-- Run against the schema in schema.sql:
--   mysql -u root city_recreation < functional_requirements.sql
-- =====================================================================

USE city_recreation;

-- ---------------------------------------------------------------------
-- FR-1: Search and filter facilities.
--   Guest requirement: "search and filter for facilities (soccer field,
--   basketball court, etc)." Here: find active facilities in a given city
--   that offer a given type of space. Parameters shown inline as literals;
--   in the app these become prepared-statement placeholders (?).
-- ---------------------------------------------------------------------
SELECT  f.facility_id,
        f.name        AS facility_name,
        f.address,
        c.name        AS city_name,
        s.space_type
FROM        FACILITY f
JOIN        CITY     c ON c.city_id    = f.city_id
JOIN        SPACE    s ON s.facility_id = f.facility_id
WHERE   c.name       = 'San Jose'          -- filter: city
  AND   s.space_type = 'Basketball Court'  -- filter: space type
  AND   f.is_active  = TRUE
  AND   s.is_active  = TRUE
GROUP BY f.facility_id, f.name, f.address, c.name, s.space_type
ORDER BY f.name;

-- ---------------------------------------------------------------------
-- FR-2: View space details.
--   Guest requirement: "view space details (a general description of the
--   space ... and for what times)." Here: full detail for one space,
--   including its facility, city, access mode and price.
-- ---------------------------------------------------------------------
SELECT  s.space_id,
        s.name        AS space_name,
        s.space_type,
        s.access_mode,
        s.price,
        f.name        AS facility_name,
        f.address,
        c.name        AS city_name,
        c.state
FROM        SPACE    s
JOIN        FACILITY f ON f.facility_id = s.facility_id
JOIN        CITY     c ON c.city_id     = f.city_id
WHERE   s.space_id = 3;   -- the selected space

-- ---------------------------------------------------------------------
-- FR-3: View availability by time slot.
--   Guest requirement: "view space details (... open, occupied, closed,
--   and reservable and for what times)." Here: the schedule for a space
--   on a given day, reading like a weather report of time -> status.
--   A slot is OCCUPIED if a CONFIRMED reservation exists for it;
--   otherwise it falls back to its scheduled status.
-- ---------------------------------------------------------------------
SELECT  ts.slot_id,
        ts.start_time,
        ts.end_time,
        CASE
            WHEN r.reservation_id IS NOT NULL THEN 'OCCUPIED'
            ELSE ts.status
        END AS availability
FROM        TIME_SLOT ts
LEFT JOIN   RESERVATION r
        ON  r.slot_id = ts.slot_id
       AND  r.status  = 'CONFIRMED'
WHERE   ts.space_id   = 1
  AND   ts.start_time >= '2026-10-01 00:00:00'
  AND   ts.start_time <  '2026-10-02 00:00:00'
ORDER BY ts.start_time;

-- ---------------------------------------------------------------------
-- FR-4: View public events.
--   Guest requirement: "view public events." Guests may see PUBLIC events
--   only (never PRIVATE), joined through the reservation to the space and
--   facility so the listing shows where/when. Cancelled reservations are
--   excluded so stale events do not appear.
-- ---------------------------------------------------------------------
SELECT  e.event_id,
        e.title,
        e.description,
        f.name        AS facility_name,
        sp.name       AS space_name,
        ts.start_time,
        ts.end_time
FROM        EVENT       e
JOIN        RESERVATION r  ON r.reservation_id = e.reservation_id
JOIN        TIME_SLOT   ts ON ts.slot_id       = r.slot_id
JOIN        SPACE       sp ON sp.space_id      = ts.space_id
JOIN        FACILITY    f  ON f.facility_id    = sp.facility_id
WHERE   e.visibility = 'PUBLIC'
  AND   r.status     = 'CONFIRMED'
ORDER BY ts.start_time;

-- ---------------------------------------------------------------------
-- FR-5: View access-mode labels (open-play / pay-to-play / reservation-only).
--   Guest requirement: "and if the space is open-play, pay-to-play, or
--   reservation-only." Here: every active space grouped with a friendly
--   label and the price that applies, so the map/list can tag each space.
-- ---------------------------------------------------------------------
SELECT  s.space_id,
        s.name        AS space_name,
        f.name        AS facility_name,
        s.access_mode,
        CASE s.access_mode
            WHEN 'OPEN_PLAY'        THEN 'Open Play (free)'
            WHEN 'PAY_TO_PLAY'      THEN 'Pay to Play'
            WHEN 'RESERVATION_ONLY' THEN 'Reservation Only'
            ELSE 'Unknown'
        END           AS access_label,
        s.price
FROM        SPACE    s
JOIN        FACILITY f ON f.facility_id = s.facility_id
WHERE   s.is_active = TRUE
ORDER BY s.access_mode, s.name;
