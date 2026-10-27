package com.cityrecreation.service;

import com.cityrecreation.model.Comment;
import com.cityrecreation.model.EventParticipant;
import com.cityrecreation.model.FavoriteSpace;
import com.cityrecreation.model.Preference;
import com.cityrecreation.model.User;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Thread-safe mock implementation of GeneralUserService.
 * Pre-seeded with data matching the authoritative schema.sql so the app
 * is fully demonstrable and interactive without touching or altering MySQL.
 */
public class MockGeneralUserService implements GeneralUserService {

    private static final DateTimeFormatter ISO_FMT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    private final Map<Integer, User> usersById = new ConcurrentHashMap<>();
    private final Map<String, User> usersByEmail = new ConcurrentHashMap<>();
    private final Map<Integer, Preference> preferencesByUserId = new ConcurrentHashMap<>();
    // userId -> list of spaceIds
    private final Map<Integer, List<Integer>> favoritesByUserId = new ConcurrentHashMap<>();
    // eventId -> list of participants
    private final Map<Integer, List<EventParticipant>> participantsByEvent = new ConcurrentHashMap<>();
    // eventId -> list of comments
    private final Map<Integer, List<Comment>> commentsByEvent = new ConcurrentHashMap<>();

    // Mock spaces metadata lookup
    private final Map<Integer, FavoriteSpace> spaceCatalog = new ConcurrentHashMap<>();

    private final AtomicInteger nextUserId = new AtomicInteger(100);
    private final AtomicInteger nextCommentId = new AtomicInteger(10);

    public MockGeneralUserService() {
        seedInitialData();
    }

    private void seedInitialData() {
        // Spaces catalog (matching schema.sql)
        spaceCatalog.put(1, new FavoriteSpace(0, 1, "Main Soccer Field", "Soccer Field", "Central Park", "San Jose", "OPEN_PLAY", new BigDecimal("0.00")));
        spaceCatalog.put(2, new FavoriteSpace(0, 2, "Indoor Court A", "Basketball Court", "Central Park Gym", "San Jose", "RESERVATION_ONLY", new BigDecimal("25.00")));
        spaceCatalog.put(3, new FavoriteSpace(0, 3, "Outdoor Tennis Court 1", "Tennis Court", "Rivermark Park", "Santa Clara", "OPEN_PLAY", new BigDecimal("0.00")));
        spaceCatalog.put(4, new FavoriteSpace(0, 4, "Baseball Diamond 1", "Baseball Diamond", "Washington Park", "Sunnyvale", "PAY_TO_PLAY", new BigDecimal("15.00")));
        spaceCatalog.put(5, new FavoriteSpace(0, 5, "Pickleball Court 1", "Pickleball Court", "Mitchell Park", "Palo Alto", "RESERVATION_ONLY", new BigDecimal("10.00")));
        spaceCatalog.put(6, new FavoriteSpace(0, 6, "Batting Cages", "Baseball", "Memorial Park", "Cupertino", "PAY_TO_PLAY", new BigDecimal("12.00")));
        spaceCatalog.put(7, new FavoriteSpace(0, 7, "Community Turf", "Soccer Field", "Cardoza Park", "Milpitas", "OPEN_PLAY", new BigDecimal("0.00")));
        spaceCatalog.put(8, new FavoriteSpace(0, 8, "Court B", "Basketball Court", "Central Park Community Center", "Fremont", "RESERVATION_ONLY", new BigDecimal("20.00")));
        spaceCatalog.put(9, new FavoriteSpace(0, 9, "Sand Volleyball Pit", "Volleyball", "Oak Meadow Park", "Los Gatos", "OPEN_PLAY", new BigDecimal("0.00")));
        spaceCatalog.put(10, new FavoriteSpace(0, 10, "Track & Field Oval", "Track", "Campbell Community Center", "Campbell", "OPEN_PLAY", new BigDecimal("0.00")));

        // Users matching schema.sql
        User u1 = new User(1, 1, "GENERAL_USER", "user1@example.com", "hashedpwd1", "555-0001", true, true);
        User u4 = new User(4, 1, "GENERAL_USER", "user4@example.com", "hashedpwd5", "555-0005", false, false);
        User u5 = new User(5, 1, "GENERAL_USER", "user5@example.com", "hashedpwd6", "555-0006", true, false);
        User u2 = new User(2, 2, "RESERVATION_USER", "user2@example.com", "hashedpwd2", "555-0002", true, true);
        User admin = new User(3, 3, "ADMIN", "admin1@example.com", "hashedpwd4", "555-0004", true, true);

        for (User u : new User[]{u1, u4, u5, u2, admin}) {
            usersById.put(u.getUserId(), u);
            usersByEmail.put(u.getEmail().toLowerCase(), u);
        }

        // Preferences for user1 (schema.sql user 1)
        preferencesByUserId.put(1, new Preference(1, 1, "San Jose", "Soccer", 10, "EMAIL", "MONTH"));
        preferencesByUserId.put(4, new Preference(4, 2, "Santa Clara", "Basketball", 15, "EMAIL", "WEEK"));

        // Favorites for user1 (schema.sql user 1 favorites spaces 1 and 3)
        List<Integer> favs1 = new ArrayList<>();
        favs1.add(1);
        favs1.add(3);
        favoritesByUserId.put(1, favs1);

        // Event participants for Event 1 (schema.sql)
        List<EventParticipant> ep1 = new ArrayList<>();
        ep1.add(new EventParticipant(1, "user1@example.com", 1, "GOING", "2026-10-01 09:30:00"));
        ep1.add(new EventParticipant(4, "user4@example.com", 1, "MAYBE", "2026-10-01 10:15:00"));
        participantsByEvent.put(1, ep1);

        // Comments for Event 1 (schema.sql)
        List<Comment> c1 = new ArrayList<>();
        c1.add(new Comment(1, 1, 1, "user1@example.com", "Bring light and dark jerseys for scrimmage.", "2026-10-01 10:00:00"));
        c1.add(new Comment(2, 1, 4, "user4@example.com", "Is there water available by the field?", "2026-10-01 11:20:00"));
        c1.add(new Comment(3, 1, 1, "user1@example.com", "Yes, hydration station is right by the bleachers.", "2026-10-01 11:45:00"));
        commentsByEvent.put(1, c1);
    }

    @Override
    public User authenticate(String email, String password) {
        if (email == null || password == null) return null;
        User user = usersByEmail.get(email.trim().toLowerCase());
        if (user == null) return null;
        // Mock password check: accept either matching mock hash or "password" / "password123" or identical string
        String p = password.trim();
        if (p.equals(user.getPasswordHash()) || p.equalsIgnoreCase("password") || p.equalsIgnoreCase("password123")) {
            return user;
        }
        return null;
    }

    @Override
    public User register(String email, String password, String phoneNum, String roleName) {
        if (email == null || email.trim().isEmpty()) return null;
        String normalizedEmail = email.trim().toLowerCase();
        if (usersByEmail.containsKey(normalizedEmail)) {
            return null; // email already exists
        }

        int uid = nextUserId.incrementAndGet();
        String role = (roleName != null && roleName.equalsIgnoreCase("RESERVATION_USER"))
                ? "RESERVATION_USER" : "GENERAL_USER";
        int roleId = role.equals("RESERVATION_USER") ? 2 : 1;

        User newUser = new User(uid, roleId, role, normalizedEmail, password != null ? password : "hashed",
                phoneNum != null ? phoneNum.trim() : "", true, false);

        usersById.put(uid, newUser);
        usersByEmail.put(normalizedEmail, newUser);

        // default preference
        preferencesByUserId.put(uid, new Preference(uid, 1, "San Jose", "General", 10, "EMAIL", "MONTH"));
        favoritesByUserId.put(uid, new ArrayList<>());

        return newUser;
    }

    @Override
    public User findById(int userId) {
        return usersById.get(userId);
    }

    @Override
    public Preference getPreferences(int userId) {
        Preference p = preferencesByUserId.get(userId);
        if (p == null) {
            p = new Preference(userId, 1, "San Jose", "None", 10, "EMAIL", "MONTH");
            preferencesByUserId.put(userId, p);
        }
        return p;
    }

    @Override
    public boolean updatePreferences(int userId, Preference pref) {
        if (pref == null) return false;
        pref.setUserId(userId);
        preferencesByUserId.put(userId, pref);
        return true;
    }

    @Override
    public synchronized List<FavoriteSpace> getFavorites(int userId) {
        List<Integer> spaceIds = favoritesByUserId.get(userId);
        if (spaceIds == null || spaceIds.isEmpty()) {
            return Collections.emptyList();
        }
        List<FavoriteSpace> result = new ArrayList<>();
        for (Integer sid : spaceIds) {
            FavoriteSpace template = spaceCatalog.get(sid);
            if (template != null) {
                result.add(new FavoriteSpace(userId, sid, template.getSpaceName(), template.getSpaceType(),
                        template.getFacilityName(), template.getCityName(), template.getAccessMode(), template.getPrice()));
            }
        }
        return result;
    }

    @Override
    public synchronized boolean addFavorite(int userId, int spaceId) {
        if (!spaceCatalog.containsKey(spaceId)) return false;
        List<Integer> list = favoritesByUserId.computeIfAbsent(userId, k -> new ArrayList<>());
        if (!list.contains(spaceId)) {
            list.add(spaceId);
        }
        return true;
    }

    @Override
    public synchronized boolean removeFavorite(int userId, int spaceId) {
        List<Integer> list = favoritesByUserId.get(userId);
        if (list != null) {
            return list.remove(Integer.valueOf(spaceId));
        }
        return false;
    }

    @Override
    public synchronized boolean isFavorite(int userId, int spaceId) {
        List<Integer> list = favoritesByUserId.get(userId);
        return list != null && list.contains(spaceId);
    }

    @Override
    public synchronized List<EventParticipant> getEventParticipants(int eventId) {
        List<EventParticipant> list = participantsByEvent.get(eventId);
        if (list == null) return Collections.emptyList();
        return new ArrayList<>(list);
    }

    @Override
    public synchronized String getUserRsvp(int userId, int eventId) {
        List<EventParticipant> list = participantsByEvent.get(eventId);
        if (list == null) return null;
        for (EventParticipant ep : list) {
            if (ep.getUserId() == userId) return ep.getStatus();
        }
        return null;
    }

    @Override
    public synchronized boolean setRsvp(int userId, int eventId, String status) {
        User user = usersById.get(userId);
        if (user == null) return false;

        List<EventParticipant> list = participantsByEvent.computeIfAbsent(eventId, k -> new ArrayList<>());
        // remove existing if present
        list.removeIf(ep -> ep.getUserId() == userId);

        if (status != null && !status.equalsIgnoreCase("NONE") && !status.equalsIgnoreCase("LEAVE")) {
            String cleanStatus = status.trim().toUpperCase();
            if (!cleanStatus.equals("GOING") && !cleanStatus.equals("MAYBE") && !cleanStatus.equals("DECLINED")) {
                cleanStatus = "GOING";
            }
            String now = LocalDateTime.now().format(ISO_FMT);
            list.add(new EventParticipant(userId, user.getEmail(), eventId, cleanStatus, now));
        }
        return true;
    }

    @Override
    public synchronized List<Comment> getEventComments(int eventId) {
        List<Comment> list = commentsByEvent.get(eventId);
        if (list == null) return Collections.emptyList();
        return new ArrayList<>(list);
    }

    @Override
    public synchronized Comment addComment(int userId, int eventId, String body) {
        if (body == null || body.trim().isEmpty()) return null;
        User user = usersById.get(userId);
        if (user == null) return null;

        int cid = nextCommentId.incrementAndGet();
        String now = LocalDateTime.now().format(ISO_FMT);
        Comment comment = new Comment(cid, eventId, userId, user.getEmail(), body.trim(), now);

        List<Comment> list = commentsByEvent.computeIfAbsent(eventId, k -> new ArrayList<>());
        list.add(comment);
        return comment;
    }

    @Override
    public synchronized boolean deleteComment(int userId, int commentId) {
        for (List<Comment> list : commentsByEvent.values()) {
            for (int i = 0; i < list.size(); i++) {
                Comment c = list.get(i);
                if (c.getCommentId() == commentId) {
                    if (c.getUserId() == userId) {
                        list.remove(i);
                        return true;
                    }
                    return false; // not author
                }
            }
        }
        return false;
    }
}
