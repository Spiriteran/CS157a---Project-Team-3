-- "Guest View" functional reqs
-- no account required
--
-- run against the schema in schema.sql:
--   mysql -u root city_recreation < functional_requirements.sql

USE city_recreation;

-- search and filter for facilities (soccer field, basketball court, etc)." 
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


--  view space details - a general description of the space...and for what times
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

-- view space details (... open, occupied, closed, and reservable and for what times).
--   a slot is OCCUPIED if a CONFIRMED reservation exists for it
--   otw falls back to its scheduled status.
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


-- view public events
-- should never be able to view private events
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

-- open-play / pay-to-play / reservation-only
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
