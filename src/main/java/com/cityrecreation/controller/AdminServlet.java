package com.cityrecreation.controller;

import com.cityrecreation.util.DatabaseUtil;
import com.google.gson.Gson;

import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import java.io.IOException;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Small administrator surface for the records already modeled by schema.sql. */
@WebServlet("/api/admin")
public class AdminServlet extends HttpServlet {
    private static final long serialVersionUID = 1L;
    private final Gson gson = new Gson();

    @Override
    protected void doGet(HttpServletRequest req, HttpServletResponse resp) throws IOException {
        setup(resp); if (!isAdmin(req)) { send(resp, 403, error("Administrator access is required.")); return; }
        try { Map<String, Object> result = new LinkedHashMap<>(); result.put("facilities", query("SELECT f.facility_id, f.name AS facility_name, c.name AS city_name, f.address, f.is_active FROM FACILITY f JOIN CITY c ON c.city_id = f.city_id ORDER BY f.name")); result.put("spaces", query("SELECT s.space_id, s.name AS space_name, f.name AS facility_name, s.space_type, s.access_mode, s.price, s.is_active FROM SPACE s JOIN FACILITY f ON f.facility_id = s.facility_id ORDER BY f.name, s.name")); result.put("reservations", query("SELECT r.reservation_id, r.status, u.email, sp.name AS space_name, ts.start_time, ts.end_time FROM RESERVATION r JOIN `USER` u ON u.user_id = r.user_id JOIN TIME_SLOT ts ON ts.slot_id = r.slot_id JOIN SPACE sp ON sp.space_id = ts.space_id ORDER BY ts.start_time DESC")); result.put("events", query("SELECT e.event_id, e.title, e.description, e.visibility FROM EVENT e ORDER BY e.title")); result.put("slots", query("SELECT ts.slot_id, sp.name AS space_name, ts.start_time, ts.end_time, ts.status FROM TIME_SLOT ts JOIN SPACE sp ON sp.space_id = ts.space_id ORDER BY ts.start_time")); result.put("comments", query("SELECT c.comment_id, c.body, u.email, e.title FROM `COMMENT` c JOIN `USER` u ON u.user_id = c.user_id JOIN EVENT e ON e.event_id = c.event_id ORDER BY c.created_time DESC")); send(resp, 200, result); } catch (SQLException e) { e.printStackTrace(); send(resp, 500, error("Admin data could not be loaded.")); }
    }

    @Override
    protected void doPost(HttpServletRequest req, HttpServletResponse resp) throws IOException {
        setup(resp); if (!isAdmin(req)) { send(resp, 403, error("Administrator access is required.")); return; }
        Map<String, String> in = body(req);
        try {
            String action = in.get("action");
            if ("create_facility".equals(action)) execute("INSERT INTO FACILITY (city_id, name, address, is_active) VALUES (?, ?, ?, TRUE)", in.get("cityId"), in.get("name"), in.get("address"));
            else if ("create_space".equals(action)) execute("INSERT INTO SPACE (facility_id, name, space_type, access_mode, price, is_active) VALUES (?, ?, ?, ?, ?, TRUE)", in.get("facilityId"), in.get("name"), in.get("spaceType"), in.get("accessMode"), in.get("price"));
            else if ("update_space".equals(action)) updateSpace(in);
            else if ("deactivate_space".equals(action)) execute("UPDATE SPACE SET is_active = FALSE WHERE space_id = ?", in.get("spaceId"));
            else if ("cancel_reservation".equals(action)) execute("UPDATE RESERVATION SET status = 'CANCELLED' WHERE reservation_id = ?", in.get("reservationId"));
            else if ("delete_comment".equals(action)) execute("DELETE FROM `COMMENT` WHERE comment_id = ?", in.get("commentId"));
            else if ("update_event".equals(action)) updateEvent(in);
            else if ("update_slot".equals(action)) execute("UPDATE TIME_SLOT SET status = ? WHERE slot_id = ?", in.get("status"), in.get("slotId"));
            else { send(resp, 400, error("Unknown admin action.")); return; }
            send(resp, 200, ok("Administrator change saved."));
        } catch (SQLException e) { e.printStackTrace(); send(resp, 409, error("Administrator change could not be saved.")); }
    }

    private void updateSpace(Map<String, String> in) throws SQLException { try (Connection c = DatabaseUtil.getConnection(); PreparedStatement p = c.prepareStatement("UPDATE SPACE SET name = ?, space_type = ?, access_mode = ?, price = ? WHERE space_id = ?")) { p.setString(1, in.get("name")); p.setString(2, in.get("spaceType")); p.setString(3, in.get("accessMode")); p.setBigDecimal(4, new java.math.BigDecimal(in.get("price"))); p.setInt(5, Integer.parseInt(in.get("spaceId"))); p.executeUpdate(); } }
    private void updateEvent(Map<String, String> in) throws SQLException { try (Connection c = DatabaseUtil.getConnection(); PreparedStatement p = c.prepareStatement("UPDATE EVENT SET title = ?, description = ?, visibility = ? WHERE event_id = ?")) { p.setString(1, in.get("title")); p.setString(2, in.get("description")); p.setString(3, in.get("visibility")); p.setInt(4, Integer.parseInt(in.get("eventId"))); p.executeUpdate(); } }
    private void execute(String sql, String... values) throws SQLException { try (Connection c = DatabaseUtil.getConnection(); PreparedStatement p = c.prepareStatement(sql)) { for (int i = 0; i < values.length; i++) { if (values[i] != null && values[i].matches("-?\\d+(\\.\\d+)?") && (sql.contains("_id") || sql.contains("price"))) p.setString(i + 1, values[i]); else p.setString(i + 1, values[i]); } p.executeUpdate(); } }
    private List<Map<String, String>> query(String sql) throws SQLException { List<Map<String, String>> rows = new ArrayList<>(); try (Connection c = DatabaseUtil.getConnection(); PreparedStatement p = c.prepareStatement(sql); ResultSet r = p.executeQuery()) { int n = r.getMetaData().getColumnCount(); while (r.next()) { Map<String, String> row = new LinkedHashMap<>(); for (int i = 1; i <= n; i++) row.put(r.getMetaData().getColumnLabel(i), r.getObject(i) == null ? "" : String.valueOf(r.getObject(i))); rows.add(row); } } return rows; }
    private Map<String, String> body(HttpServletRequest req) throws IOException { Map<String, String> out = new LinkedHashMap<>(); Map<?, ?> json = gson.fromJson(req.getReader().lines().reduce("", (a, b) -> a + b), Map.class); if (json != null) for (Map.Entry<?, ?> e : json.entrySet()) out.put(String.valueOf(e.getKey()), e.getValue() == null ? "" : String.valueOf(e.getValue())); return out; }
    private boolean isAdmin(HttpServletRequest req) { HttpSession s = req.getSession(false); return s != null && "ADMIN".equals(s.getAttribute("role")); }
    private void setup(HttpServletResponse r) { r.setContentType("application/json"); r.setCharacterEncoding("UTF-8"); }
    private Map<String, Object> ok(String m) { Map<String, Object> o = new LinkedHashMap<>(); o.put("ok", true); o.put("message", m); return o; }
    private Map<String, Object> error(String m) { Map<String, Object> o = new LinkedHashMap<>(); o.put("ok", false); o.put("error", m); return o; }
    private void send(HttpServletResponse r, int status, Map<String, ?> o) throws IOException { r.setStatus(status); r.getWriter().write(gson.toJson(o)); }
}
