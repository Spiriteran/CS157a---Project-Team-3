package com.cityrecreation.controller;

import com.cityrecreation.util.DatabaseUtil;
import com.google.gson.Gson;

import javax.servlet.annotation.WebServlet;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.servlet.http.HttpSession;
import java.io.IOException;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Favorites, public-event attendance, and comments. */
@WebServlet("/api/community")
public class CommunityServlet extends HttpServlet {
    private static final long serialVersionUID = 1L;
    private final Gson gson = new Gson();

    @Override
    protected void doGet(HttpServletRequest req, HttpServletResponse resp) throws IOException {
        setup(resp);
        Integer userId = userId(req);
        if (userId == null) { send(resp, 401, error("Sign in to view your community data.")); return; }
        try {
            String action = req.getParameter("action");
            if ("favorites".equals(action)) { Map<String, Object> result = new LinkedHashMap<>(); result.put("rows", favorites(userId)); send(resp, 200, result); }
            else send(resp, 400, error("Unknown community request."));
        } catch (SQLException e) { e.printStackTrace(); send(resp, 500, error("Community data could not be loaded.")); }
    }

    @Override
    protected void doPost(HttpServletRequest req, HttpServletResponse resp) throws IOException {
        setup(resp); Integer userId = userId(req);
        if (userId == null) { send(resp, 401, error("Sign in to use this feature.")); return; }
        Map<String, String> input = body(req);
        try {
            String action = input.get("action");
            if ("favorite".equals(action)) favorite(userId, Integer.parseInt(input.get("spaceId")), input.get("enabled"));
            else if ("join".equals(action)) join(userId, Integer.parseInt(input.get("eventId")));
            else if ("comment".equals(action)) comment(userId, Integer.parseInt(input.get("eventId")), input.get("body"));
            else { send(resp, 400, error("Unknown community action.")); return; }
            send(resp, 200, ok("Your change was saved."));
        } catch (NumberFormatException e) { send(resp, 400, error("The selected id is invalid.")); }
        catch (SQLException e) { e.printStackTrace(); send(resp, 409, error("That community change could not be saved.")); }
    }

    private void favorite(int userId, int spaceId, String enabled) throws SQLException {
        if ("false".equalsIgnoreCase(enabled)) {
            try (Connection c = DatabaseUtil.getConnection(); PreparedStatement p = c.prepareStatement("DELETE FROM USER_FAVORITE WHERE user_id = ? AND space_id = ?")) { p.setInt(1, userId); p.setInt(2, spaceId); p.executeUpdate(); }
        } else {
            try (Connection c = DatabaseUtil.getConnection(); PreparedStatement p = c.prepareStatement("INSERT IGNORE INTO USER_FAVORITE (user_id, space_id) VALUES (?, ?)")) { p.setInt(1, userId); p.setInt(2, spaceId); p.executeUpdate(); }
        }
    }

    private void join(int userId, int eventId) throws SQLException {
        String sql = "INSERT INTO EVENT_PARTICIPANT (user_id, event_id, joined_time, status) SELECT ?, e.event_id, NOW(), 'GOING' FROM EVENT e JOIN RESERVATION r ON r.reservation_id = e.reservation_id WHERE e.event_id = ? AND e.visibility = 'PUBLIC' AND r.status = 'CONFIRMED'";
        try (Connection c = DatabaseUtil.getConnection(); PreparedStatement p = c.prepareStatement(sql)) { p.setInt(1, userId); p.setInt(2, eventId); if (p.executeUpdate() == 0) throw new SQLException("event unavailable"); }
    }

    private void comment(int userId, int eventId, String body) throws SQLException {
        if (body == null || body.trim().isEmpty() || body.length() > 2000) throw new SQLException("invalid comment");
        String sql = "INSERT INTO `COMMENT` (event_id, user_id, body) SELECT e.event_id, ?, ? FROM EVENT e JOIN RESERVATION r ON r.reservation_id = e.reservation_id WHERE e.event_id = ? AND e.visibility = 'PUBLIC' AND r.status = 'CONFIRMED'";
        try (Connection c = DatabaseUtil.getConnection(); PreparedStatement p = c.prepareStatement(sql)) { p.setInt(1, userId); p.setString(2, body.trim()); p.setInt(3, eventId); if (p.executeUpdate() == 0) throw new SQLException("event unavailable"); }
    }

    private List<Map<String, String>> favorites(int userId) throws SQLException { return query("SELECT s.space_id, s.name AS space_name, f.name AS facility_name, s.space_type, s.access_mode, s.price FROM USER_FAVORITE uf JOIN SPACE s ON s.space_id = uf.space_id JOIN FACILITY f ON f.facility_id = s.facility_id WHERE uf.user_id = ? ORDER BY f.name, s.name", String.valueOf(userId)); }
    private List<Map<String, String>> query(String sql, String param) throws SQLException { List<Map<String, String>> rows = new ArrayList<>(); try (Connection c = DatabaseUtil.getConnection(); PreparedStatement p = c.prepareStatement(sql)) { p.setString(1, param); try (ResultSet r = p.executeQuery()) { int n = r.getMetaData().getColumnCount(); while (r.next()) { Map<String, String> row = new LinkedHashMap<>(); for (int i = 1; i <= n; i++) row.put(r.getMetaData().getColumnLabel(i), r.getObject(i) == null ? "" : String.valueOf(r.getObject(i))); rows.add(row); } } } return rows; }
    private Map<String, String> body(HttpServletRequest req) throws IOException { Map<String, String> out = new LinkedHashMap<>(); Map<?, ?> json = gson.fromJson(req.getReader().lines().reduce("", (a, b) -> a + b), Map.class); if (json != null) for (Map.Entry<?, ?> e : json.entrySet()) out.put(String.valueOf(e.getKey()), e.getValue() == null ? "" : String.valueOf(e.getValue())); return out; }
    private Integer userId(HttpServletRequest req) { HttpSession s = req.getSession(false); return s == null ? null : (Integer) s.getAttribute("userId"); }
    private void setup(HttpServletResponse r) { r.setContentType("application/json"); r.setCharacterEncoding("UTF-8"); }
    private Map<String, Object> ok(String m) { Map<String, Object> o = new LinkedHashMap<>(); o.put("ok", true); o.put("message", m); return o; }
    private Map<String, Object> error(String m) { Map<String, Object> o = new LinkedHashMap<>(); o.put("ok", false); o.put("error", m); return o; }
    private void send(HttpServletResponse r, int status, Map<String, ?> o) throws IOException { r.setStatus(status); r.getWriter().write(gson.toJson(o)); }
}
