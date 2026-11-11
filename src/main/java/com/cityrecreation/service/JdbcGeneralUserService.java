package com.cityrecreation.service;

import com.cityrecreation.model.Comment;
import com.cityrecreation.model.EventParticipant;
import com.cityrecreation.model.FavoriteSpace;
import com.cityrecreation.model.Preference;
import com.cityrecreation.model.User;
import com.cityrecreation.util.DatabaseUtil;
import com.cityrecreation.util.PasswordUtil;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.sql.Types;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Set;

/**
 * JDBC-backed implementation of GeneralUserService operating against the
 * authoritative MySQL `city_recreation` schema.
 */
public class JdbcGeneralUserService implements GeneralUserService {

    private static final Set<String> VALID_RSVP_STATUSES = Set.of("GOING", "MAYBE", "DECLINED");

    private static final String SELECT_USER_BASE =
            "SELECT u.user_id, u.role_id, r.role_name, u.email, u.password_hash, "
          + "       u.phone_num, u.email_verified, u.twofa_enabled, "
          + "       u.billing_address, u.payment_method_stub "
          + "FROM `USER` u "
          + "JOIN ROLE r ON r.role_id = u.role_id ";

    @Override
    public User authenticate(String email, String password) {
        String normalized = PasswordUtil.normalizeEmail(email);
        if (normalized == null || password == null || password.isEmpty()) {
            return null;
        }
        User user = findByEmail(normalized);
        if (user == null || !PasswordUtil.passwordMatches(password, user.getPasswordHash())) {
            return null;
        }
        return user;
    }

    @Override
    public User register(String email, String password, String phoneNum, String roleName) {
        String normalized = PasswordUtil.normalizeEmail(email);
        if (normalized == null || password == null || password.length() < 8) {
            return null;
        }
        String role = (roleName != null && "RESERVATION_USER".equalsIgnoreCase(roleName.trim()))
                ? "RESERVATION_USER"
                : "GENERAL_USER";

        String sql = "INSERT INTO `USER` "
                + "(role_id, email, password_hash, phone_num, email_verified, twofa_enabled) "
                + "SELECT role_id, ?, ?, ?, FALSE, (role_name = 'RESERVATION_USER') "
                + "FROM ROLE WHERE role_name = ?";

        String cleanPhone = (phoneNum == null || phoneNum.trim().isEmpty()) ? null : phoneNum.trim();
        String hashed = PasswordUtil.hashPassword(password);

        try (Connection conn = DatabaseUtil.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            ps.setString(1, normalized);
            ps.setString(2, hashed);
            if (cleanPhone == null) {
                ps.setNull(3, Types.VARCHAR);
            } else {
                ps.setString(3, cleanPhone);
            }
            ps.setString(4, role);

            if (ps.executeUpdate() != 1) {
                return null;
            }
            try (ResultSet keys = ps.getGeneratedKeys()) {
                if (keys.next()) {
                    return findById(keys.getInt(1));
                }
            }
        } catch (SQLException e) {
            // Duplicate email (MySQL ER_DUP_ENTRY = 1062)
            if (e.getErrorCode() == 1062) {
                return null;
            }
            throw new IllegalStateException("Failed to register user", e);
        }
        return null;
    }

    @Override
    public boolean markEmailVerified(String email) {
        String normalized = PasswordUtil.normalizeEmail(email);
        if (normalized == null) {
            return false;
        }
        String sql = "UPDATE `USER` SET email_verified = TRUE WHERE email = ?";
        try (Connection conn = DatabaseUtil.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, normalized);
            return ps.executeUpdate() > 0;
        } catch (SQLException e) {
            e.printStackTrace();
            return false;
        }
    }

    @Override
    public User findByEmail(String email) {
        String normalized = PasswordUtil.normalizeEmail(email);
        if (normalized == null) {
            return null;
        }
        return findOneUser(SELECT_USER_BASE + "WHERE u.email = ?", normalized);
    }

    @Override
    public User findById(int userId) {
        return findOneUser(SELECT_USER_BASE + "WHERE u.user_id = ?", userId);
    }

    private User findOneUser(String sql, Object param) {
        try (Connection conn = DatabaseUtil.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setObject(1, param);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return mapUser(rs);
                }
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
        return null;
    }

    private static User mapUser(ResultSet rs) throws SQLException {
        User u = new User(
                rs.getInt("user_id"),
                rs.getInt("role_id"),
                rs.getString("role_name"),
                rs.getString("email"),
                rs.getString("password_hash"),
                rs.getString("phone_num"),
                rs.getBoolean("email_verified"),
                rs.getBoolean("twofa_enabled")
        );
        u.setBillingAddress(rs.getString("billing_address"));
        u.setPaymentMethodStub(rs.getString("payment_method_stub"));
        return u;
    }

    @Override
    public Preference getPreferences(int userId) {
        String sql = "SELECT p.user_id, p.default_city_id, c.name AS default_city_name, "
                + "       p.default_sport, p.map_radius, p.notify_pref, p.calendar_view "
                + "FROM PREFERENCE p "
                + "LEFT JOIN CITY c ON c.city_id = p.default_city_id "
                + "WHERE p.user_id = ?";
        try (Connection conn = DatabaseUtil.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, userId);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    Integer cityId = rs.getObject("default_city_id") == null
                            ? null : rs.getInt("default_city_id");
                    return new Preference(
                            rs.getInt("user_id"),
                            cityId,
                            rs.getString("default_city_name"),
                            rs.getString("default_sport"),
                            rs.getInt("map_radius"),
                            rs.getString("notify_pref"),
                            rs.getString("calendar_view")
                    );
                }
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
        return new Preference(userId, 1, "San Jose", "General", 10, "EMAIL", "MONTH");
    }

    @Override
    public boolean updatePreferences(int userId, Preference pref) {
        if (pref == null) {
            return false;
        }
        String sql = "INSERT INTO PREFERENCE "
                + "(user_id, default_city_id, default_sport, map_radius, notify_pref, calendar_view) "
                + "VALUES (?, ?, ?, ?, ?, ?) "
                + "ON DUPLICATE KEY UPDATE "
                + "    default_city_id = VALUES(default_city_id), "
                + "    default_sport = VALUES(default_sport), "
                + "    map_radius = VALUES(map_radius), "
                + "    notify_pref = VALUES(notify_pref), "
                + "    calendar_view = VALUES(calendar_view)";
        try (Connection conn = DatabaseUtil.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, userId);
            if (pref.getDefaultCityId() == null) {
                ps.setNull(2, Types.INTEGER);
            } else {
                ps.setInt(2, pref.getDefaultCityId());
            }
            ps.setString(3, pref.getDefaultSport());
            ps.setInt(4, pref.getMapRadius() > 0 ? pref.getMapRadius() : 10);
            ps.setString(5, pref.getNotifyPref() != null ? pref.getNotifyPref() : "EMAIL");
            ps.setString(6, pref.getCalendarView() != null ? pref.getCalendarView() : "MONTH");
            ps.executeUpdate();
            return true;
        } catch (SQLException e) {
            e.printStackTrace();
            return false;
        }
    }

    @Override
    public List<FavoriteSpace> getFavorites(int userId) {
        String sql = "SELECT uf.user_id, s.space_id, s.name AS space_name, s.space_type, "
                + "       f.name AS facility_name, c.name AS city_name, s.access_mode, s.price "
                + "FROM USER_FAVORITE uf "
                + "JOIN SPACE s ON s.space_id = uf.space_id "
                + "JOIN FACILITY f ON f.facility_id = s.facility_id "
                + "JOIN CITY c ON c.city_id = f.city_id "
                + "WHERE uf.user_id = ? "
                + "ORDER BY uf.created_at DESC, s.name";
        List<FavoriteSpace> result = new ArrayList<>();
        try (Connection conn = DatabaseUtil.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, userId);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    result.add(new FavoriteSpace(
                            rs.getInt("user_id"),
                            rs.getInt("space_id"),
                            rs.getString("space_name"),
                            rs.getString("space_type"),
                            rs.getString("facility_name"),
                            rs.getString("city_name"),
                            rs.getString("access_mode"),
                            rs.getBigDecimal("price")
                    ));
                }
            }
        } catch (SQLException e) {
            e.printStackTrace();
            return Collections.emptyList();
        }
        return result;
    }

    @Override
    public boolean addFavorite(int userId, int spaceId) {
        String sql = "INSERT IGNORE INTO USER_FAVORITE (user_id, space_id, created_at) "
                + "VALUES (?, ?, NOW())";
        try (Connection conn = DatabaseUtil.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, userId);
            ps.setInt(2, spaceId);
            ps.executeUpdate();
            return true;
        } catch (SQLException e) {
            e.printStackTrace();
            return false;
        }
    }

    @Override
    public boolean removeFavorite(int userId, int spaceId) {
        String sql = "DELETE FROM USER_FAVORITE WHERE user_id = ? AND space_id = ?";
        try (Connection conn = DatabaseUtil.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, userId);
            ps.setInt(2, spaceId);
            return ps.executeUpdate() > 0;
        } catch (SQLException e) {
            e.printStackTrace();
            return false;
        }
    }

    @Override
    public boolean isFavorite(int userId, int spaceId) {
        String sql = "SELECT 1 FROM USER_FAVORITE WHERE user_id = ? AND space_id = ? LIMIT 1";
        try (Connection conn = DatabaseUtil.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, userId);
            ps.setInt(2, spaceId);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next();
            }
        } catch (SQLException e) {
            e.printStackTrace();
            return false;
        }
    }

    @Override
    public List<EventParticipant> getEventParticipants(int eventId) {
        String sql = "SELECT ep.user_id, u.email AS user_email, ep.event_id, ep.status, ep.joined_time "
                + "FROM EVENT_PARTICIPANT ep "
                + "JOIN `USER` u ON u.user_id = ep.user_id "
                + "WHERE ep.event_id = ? "
                + "ORDER BY ep.joined_time";
        List<EventParticipant> list = new ArrayList<>();
        try (Connection conn = DatabaseUtil.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, eventId);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    list.add(new EventParticipant(
                            rs.getInt("user_id"),
                            rs.getString("user_email"),
                            rs.getInt("event_id"),
                            rs.getString("status"),
                            rs.getString("joined_time")
                    ));
                }
            }
        } catch (SQLException e) {
            e.printStackTrace();
            return Collections.emptyList();
        }
        return list;
    }

    @Override
    public String getUserRsvp(int userId, int eventId) {
        String sql = "SELECT status FROM EVENT_PARTICIPANT WHERE user_id = ? AND event_id = ?";
        try (Connection conn = DatabaseUtil.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, userId);
            ps.setInt(2, eventId);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return rs.getString("status");
                }
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
        return null;
    }

    @Override
    public boolean setRsvp(int userId, int eventId, String status) {
        if (status == null || status.trim().isEmpty()
                || "NONE".equalsIgnoreCase(status.trim())
                || "LEAVE".equalsIgnoreCase(status.trim())) {
            String delSql = "DELETE FROM EVENT_PARTICIPANT WHERE user_id = ? AND event_id = ?";
            try (Connection conn = DatabaseUtil.getConnection();
                 PreparedStatement ps = conn.prepareStatement(delSql)) {
                ps.setInt(1, userId);
                ps.setInt(2, eventId);
                ps.executeUpdate();
                return true;
            } catch (SQLException e) {
                e.printStackTrace();
                return false;
            }
        }

        String normalized = status.trim().toUpperCase();
        if (!VALID_RSVP_STATUSES.contains(normalized)) {
            normalized = "GOING";
        }

        String sql = "INSERT INTO EVENT_PARTICIPANT (user_id, event_id, status, joined_time) "
                + "SELECT ?, e.event_id, ?, NOW() "
                + "FROM EVENT e "
                + "JOIN RESERVATION r ON r.reservation_id = e.reservation_id "
                + "WHERE e.event_id = ? AND e.visibility = 'PUBLIC' AND r.status = 'CONFIRMED' "
                + "ON DUPLICATE KEY UPDATE status = VALUES(status), joined_time = NOW()";
        try (Connection conn = DatabaseUtil.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, userId);
            ps.setString(2, normalized);
            ps.setInt(3, eventId);
            return ps.executeUpdate() > 0;
        } catch (SQLException e) {
            e.printStackTrace();
            return false;
        }
    }

    @Override
    public List<Comment> getEventComments(int eventId) {
        String sql = "SELECT c.comment_id, c.event_id, c.user_id, u.email AS user_email, "
                + "       c.body, c.created_time "
                + "FROM `COMMENT` c "
                + "JOIN `USER` u ON u.user_id = c.user_id "
                + "WHERE c.event_id = ? "
                + "ORDER BY c.created_time";
        List<Comment> list = new ArrayList<>();
        try (Connection conn = DatabaseUtil.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, eventId);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    list.add(mapComment(rs));
                }
            }
        } catch (SQLException e) {
            e.printStackTrace();
            return Collections.emptyList();
        }
        return list;
    }

    @Override
    public Comment addComment(int userId, int eventId, String body) {
        if (body == null || body.trim().isEmpty() || body.trim().length() > 2000) {
            return null;
        }
        String cleanBody = body.trim();
        String insertSql = "INSERT INTO `COMMENT` (event_id, user_id, body, created_time) "
                + "SELECT e.event_id, ?, ?, NOW() "
                + "FROM EVENT e "
                + "JOIN RESERVATION r ON r.reservation_id = e.reservation_id "
                + "WHERE e.event_id = ? AND e.visibility = 'PUBLIC' AND r.status = 'CONFIRMED'";
        try (Connection conn = DatabaseUtil.getConnection();
             PreparedStatement ps = conn.prepareStatement(insertSql, Statement.RETURN_GENERATED_KEYS)) {
            ps.setInt(1, userId);
            ps.setString(2, cleanBody);
            ps.setInt(3, eventId);
            if (ps.executeUpdate() == 0) {
                return null;
            }
            try (ResultSet keys = ps.getGeneratedKeys()) {
                if (keys.next()) {
                    return findCommentById(conn, keys.getInt(1));
                }
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
        return null;
    }

    private Comment findCommentById(Connection conn, int commentId) throws SQLException {
        String sql = "SELECT c.comment_id, c.event_id, c.user_id, u.email AS user_email, "
                + "       c.body, c.created_time "
                + "FROM `COMMENT` c "
                + "JOIN `USER` u ON u.user_id = c.user_id "
                + "WHERE c.comment_id = ?";
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, commentId);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return mapComment(rs);
                }
            }
        }
        return null;
    }

    private static Comment mapComment(ResultSet rs) throws SQLException {
        return new Comment(
                rs.getInt("comment_id"),
                rs.getInt("event_id"),
                rs.getInt("user_id"),
                rs.getString("user_email"),
                rs.getString("body"),
                rs.getString("created_time")
        );
    }

    @Override
    public boolean deleteComment(int userId, int commentId) {
        String checkSql = "SELECT 1 FROM `COMMENT` WHERE comment_id = ? AND user_id = ?";
        String deleteImagesSql = "DELETE FROM IMAGE WHERE comment_id = ?";
        String deleteCommentSql = "DELETE FROM `COMMENT` WHERE comment_id = ? AND user_id = ?";
        try (Connection conn = DatabaseUtil.getConnection()) {
            conn.setAutoCommit(false);
            try {
                try (PreparedStatement check = conn.prepareStatement(checkSql)) {
                    check.setInt(1, commentId);
                    check.setInt(2, userId);
                    try (ResultSet rs = check.executeQuery()) {
                        if (!rs.next()) {
                            conn.rollback();
                            return false;
                        }
                    }
                }
                // IMAGE.comment_id references COMMENT without ON DELETE CASCADE
                try (PreparedStatement delImg = conn.prepareStatement(deleteImagesSql)) {
                    delImg.setInt(1, commentId);
                    delImg.executeUpdate();
                }
                int deleted;
                try (PreparedStatement delComment = conn.prepareStatement(deleteCommentSql)) {
                    delComment.setInt(1, commentId);
                    delComment.setInt(2, userId);
                    deleted = delComment.executeUpdate();
                }
                conn.commit();
                return deleted > 0;
            } catch (SQLException e) {
                conn.rollback();
                throw e;
            } finally {
                conn.setAutoCommit(true);
            }
        } catch (SQLException e) {
            e.printStackTrace();
            return false;
        }
    }
}
