# General User Tier — Proposed SQL Catalog (For Review & Approval)

This document contains the exact SQL queries planned for the **General User Tier (FR-6 through FR-10)**. 
Per project instructions, **no SQL has been executed against the database or added to `.sql` files**. 
Please review these queries and approve or suggest edits before they are wired to MySQL via JDBC.

---

## 1. FR-6: User Authentication & Registration

### 1.1 Find User by Email (Login)
* **Goal**: Retrieve the user's account details and stored password hash for authentication.
* **Tables**: `USER u JOIN ROLE r ON u.role_id = r.role_id`
* **Query**:
```sql
SELECT  u.user_id,
        u.role_id,
        r.role_name,
        u.email,
        u.password_hash,
        u.phone_num,
        u.email_verified,
        u.twofa_enabled
FROM    `USER` u
JOIN    `ROLE` r ON r.role_id = u.role_id
WHERE   u.email = ?;
```

### 1.2 Register New General User
* **Goal**: Create a new account assigned to `GENERAL_USER` (role_id = 1).
* **Table**: `USER`
* **Query**:
```sql
INSERT INTO `USER` (
    role_id,
    email,
    password_hash,
    phone_num,
    email_verified,
    twofa_enabled
) VALUES (
    (SELECT role_id FROM `ROLE` WHERE role_name = 'GENERAL_USER'),
    ?,
    ?,
    ?,
    TRUE,   -- Default mock verified for development, or FALSE if requiring confirmation
    FALSE
);
```

---

## 2. FR-7: User Profile & Preferences Management

### 2.1 Fetch User Preferences
* **Goal**: Retrieve default city, preferred sport, map radius, notification pref, and calendar view.
* **Tables**: `PREFERENCE p LEFT JOIN CITY c ON p.default_city_id = c.city_id`
* **Query**:
```sql
SELECT  p.user_id,
        p.default_city_id,
        c.name AS default_city_name,
        p.default_sport,
        p.map_radius,
        p.notify_pref,
        p.calendar_view
FROM    `PREFERENCE` p
LEFT JOIN `CITY` c ON c.city_id = p.default_city_id
WHERE   p.user_id = ?;
```

### 2.2 Upsert User Preferences
* **Goal**: Insert or update preferences when saved from `profile.html`.
* **Table**: `PREFERENCE`
* **Query**:
```sql
INSERT INTO `PREFERENCE` (
    user_id,
    default_city_id,
    default_sport,
    map_radius,
    notify_pref,
    calendar_view
) VALUES (?, ?, ?, ?, ?, ?)
ON DUPLICATE KEY UPDATE
    default_city_id = VALUES(default_city_id),
    default_sport   = VALUES(default_sport),
    map_radius      = VALUES(map_radius),
    notify_pref     = VALUES(notify_pref),
    calendar_view   = VALUES(calendar_view);
```

---

## 3. FR-8: Favorite Spaces Management

### 3.1 List Favorited Spaces for User
* **Goal**: Display all spaces saved by the user with location, type, and access info.
* **Tables**: `USER_FAVORITE uf JOIN SPACE s JOIN FACILITY f JOIN CITY c`
* **Query**:
```sql
SELECT  s.space_id,
        s.name        AS space_name,
        s.space_type,
        s.access_mode,
        s.price,
        f.name        AS facility_name,
        c.name        AS city_name,
        uf.created_at AS favorited_at
FROM        `USER_FAVORITE` uf
JOIN        `SPACE`         s  ON s.space_id    = uf.space_id
JOIN        `FACILITY`      f  ON f.facility_id = s.facility_id
JOIN        `CITY`          c  ON c.city_id     = f.city_id
WHERE   uf.user_id = ?
ORDER BY uf.created_at DESC;
```

### 3.2 Add Space to Favorites
* **Goal**: Save a space to user's favorites.
* **Table**: `USER_FAVORITE`
* **Query**:
```sql
INSERT IGNORE INTO `USER_FAVORITE` (user_id, space_id, created_at)
VALUES (?, ?, NOW());
```

### 3.3 Remove Space from Favorites
* **Goal**: Remove a bookmarked space.
* **Table**: `USER_FAVORITE`
* **Query**:
```sql
DELETE FROM `USER_FAVORITE`
WHERE user_id = ? AND space_id = ?;
```

### 3.4 Check If Space Is Favorited
* **Query**:
```sql
SELECT 1 FROM `USER_FAVORITE`
WHERE user_id = ? AND space_id = ?;
```

---

## 4. FR-9: Event Participation & RSVP

### 4.1 Get Event RSVP Summary & Attendees
* **Goal**: Count RSVPs and list participants.
* **Tables**: `EVENT_PARTICIPANT ep JOIN USER u`
* **Query**:
```sql
SELECT  ep.user_id,
        u.email       AS user_email,
        ep.status,
        ep.joined_time
FROM    `EVENT_PARTICIPANT` ep
JOIN    `USER`              u  ON u.user_id = ep.user_id
WHERE   ep.event_id = ?
ORDER BY ep.joined_time ASC;
```

### 4.2 Upsert / Change RSVP Status
* **Goal**: Set or switch RSVP between `GOING`, `MAYBE`, or `DECLINED`.
* **Table**: `EVENT_PARTICIPANT`
* **Query**:
```sql
INSERT INTO `EVENT_PARTICIPANT` (user_id, event_id, status, joined_time)
VALUES (?, ?, ?, NOW())
ON DUPLICATE KEY UPDATE
    status      = VALUES(status),
    joined_time = NOW();
```

### 4.3 Remove RSVP (Leave Event)
* **Query**:
```sql
DELETE FROM `EVENT_PARTICIPANT`
WHERE user_id = ? AND event_id = ?;
```

---

## 5. FR-10: Event Discussion & Comments

### 5.1 List Comments for an Event
* **Goal**: Display discussion thread with author emails and timestamps.
* **Tables**: `COMMENT c JOIN USER u`
* **Query**:
```sql
SELECT  c.comment_id,
        c.event_id,
        c.user_id,
        u.email        AS user_email,
        c.body,
        c.created_time
FROM    `COMMENT` c
JOIN    `USER`    u ON u.user_id = c.user_id
WHERE   c.event_id = ?
ORDER BY c.created_time ASC;
```

### 5.2 Post a New Comment
* **Goal**: Allow an authenticated user to post a comment to an event thread.
* **Table**: `COMMENT`
* **Query**:
```sql
INSERT INTO `COMMENT` (event_id, user_id, body, created_time)
VALUES (?, ?, ?, NOW());
```

### 5.3 Delete Comment (Author Only)
* **Goal**: Enforce author-only deletion rule (CRUD matrix).
* **Table**: `COMMENT`
* **Query**:
```sql
DELETE FROM `COMMENT`
WHERE comment_id = ? AND user_id = ?;
```

---

## Approval Status
- [ ] **FR-6**: Authentication & Registration SQL
- [ ] **FR-7**: Preferences SQL
- [ ] **FR-8**: Favorites SQL
- [ ] **FR-9**: Event RSVP SQL
- [ ] **FR-10**: Event Comments SQL

*Once you approve, these queries will be implemented in the JDBC layer connecting directly to the `city_recreation` database.*
