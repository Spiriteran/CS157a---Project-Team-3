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
import java.sql.Statement;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Reservation slots, temporary locks, confirmation, editing, and cancellation. */
@WebServlet("/api/reservations")
public class ReservationServlet extends HttpServlet {
    private static final long serialVersionUID = 1L;
    private final Gson gson = new Gson();

    @Override
    protected void doGet(HttpServletRequest req, HttpServletResponse resp) throws IOException {
        resp.setContentType("application/json"); resp.setCharacterEncoding("UTF-8");
        Integer userId = userId(req);
        if (userId == null) { send(resp, 401, error("Sign in to manage reservations.")); return; }
        try {
            Map<String, Object> result = new LinkedHashMap<>();
            result.put("role", role(req));
            result.put("reservations", queryReservations(userId));
            String spaceId = req.getParameter("space_id");
            if (spaceId != null && !spaceId.trim().isEmpty()) result.put("slots", querySlots(Integer.parseInt(spaceId)));
            send(resp, 200, result);
        } catch (Exception e) { e.printStackTrace(); send(resp, 500, error("Reservations could not be loaded.")); }
    }

    @Override
    protected void doPost(HttpServletRequest req, HttpServletResponse resp) throws IOException {
        resp.setContentType("application/json"); resp.setCharacterEncoding("UTF-8");
        Integer userId = userId(req);
        if (userId == null) { send(resp, 401, error("Sign in to manage reservations.")); return; }
        Map<String, String> input = body(req);
        try {
            String action = input.get("action");
            if (!canReserve(req)) { send(resp, 403, error("A reservation-user account is required.")); return; }
            if ("lock".equals(action)) lock(resp, userId, Integer.parseInt(input.get("slotId")));
            else if ("confirm".equals(action)) confirm(resp, userId, Integer.parseInt(input.get("lockId")));
            else if ("update".equals(action)) update(resp, userId, Integer.parseInt(input.get("reservationId")), Integer.parseInt(input.get("slotId")));
            else if ("cancel".equals(action)) cancel(resp, userId, Integer.parseInt(input.get("reservationId")));
            else send(resp, 400, error("Unknown reservation action."));
        } catch (NumberFormatException e) { send(resp, 400, error("Reservation ids must be numbers.")); }
        catch (SQLException e) { e.printStackTrace(); send(resp, 409, error("The slot is no longer available.")); }
    }

    private void lock(HttpServletResponse resp, int userId, int slotId) throws SQLException, IOException {
        try (Connection conn = DatabaseUtil.getConnection()) {
            conn.setAutoCommit(false);
            Map<String, Object> result = null;
            try {
                try (PreparedStatement clear = conn.prepareStatement("DELETE FROM SLOT_LOCK WHERE slot_id = ? AND expires_at <= NOW()")) { clear.setInt(1, slotId); clear.executeUpdate(); }
                try (PreparedStatement check = conn.prepareStatement("SELECT ts.status, r.reservation_id FROM TIME_SLOT ts LEFT JOIN RESERVATION r ON r.slot_id = ts.slot_id AND r.status = 'CONFIRMED' WHERE ts.slot_id = ? FOR UPDATE")) {
                    check.setInt(1, slotId);
                    try (ResultSet rs = check.executeQuery()) {
                        if (!rs.next() || "CLOSED".equals(rs.getString("status")) || "RESERVED".equals(rs.getString("status")) || rs.getString("reservation_id") != null) throw new SQLException("slot unavailable");
                    }
                }
                try (PreparedStatement insert = conn.prepareStatement("INSERT INTO SLOT_LOCK (user_id, slot_id, expires_at) VALUES (?, ?, DATE_ADD(NOW(), INTERVAL 5 MINUTE))", Statement.RETURN_GENERATED_KEYS)) {
                    insert.setInt(1, userId); insert.setInt(2, slotId); insert.executeUpdate();
                    try (ResultSet keys = insert.getGeneratedKeys()) { keys.next(); result = ok("Slot held for five minutes."); result.put("lockId", keys.getInt(1)); }
                }
                conn.commit();
                send(resp, 200, result);
            } catch (SQLException e) { conn.rollback(); throw e; }
            finally { conn.setAutoCommit(true); }
        }
    }

    private void confirm(HttpServletResponse resp, int userId, int lockId) throws SQLException, IOException {
        try (Connection conn = DatabaseUtil.getConnection()) {
            conn.setAutoCommit(false);
            try {
                int slotId;
                try (PreparedStatement ps = conn.prepareStatement("SELECT slot_id FROM SLOT_LOCK WHERE lock_id = ? AND user_id = ? AND expires_at > NOW() FOR UPDATE")) {
                    ps.setInt(1, lockId); ps.setInt(2, userId); try (ResultSet rs = ps.executeQuery()) { if (!rs.next()) throw new SQLException("lock expired"); slotId = rs.getInt(1); }
                }
                try (PreparedStatement ps = conn.prepareStatement("INSERT INTO RESERVATION (user_id, slot_id, status) VALUES (?, ?, 'CONFIRMED')")) { ps.setInt(1, userId); ps.setInt(2, slotId); ps.executeUpdate(); }
                try (PreparedStatement ps = conn.prepareStatement("DELETE FROM SLOT_LOCK WHERE lock_id = ?")) { ps.setInt(1, lockId); ps.executeUpdate(); }
                conn.commit(); send(resp, 201, ok("Reservation confirmed."));
            } catch (SQLException e) { conn.rollback(); throw e; }
            finally { conn.setAutoCommit(true); }
        }
    }

    private void update(HttpServletResponse resp, int userId, int reservationId, int slotId) throws SQLException, IOException {
        try (Connection conn = DatabaseUtil.getConnection()) {
            conn.setAutoCommit(false);
            try {
                try (PreparedStatement check = conn.prepareStatement("SELECT reservation_id FROM RESERVATION WHERE reservation_id = ? AND user_id = ? AND status = 'CONFIRMED'")) { check.setInt(1, reservationId); check.setInt(2, userId); try (ResultSet rs = check.executeQuery()) { if (!rs.next()) throw new SQLException("reservation not found"); } }
                try (PreparedStatement check = conn.prepareStatement("SELECT ts.status, r.reservation_id FROM TIME_SLOT ts LEFT JOIN RESERVATION r ON r.slot_id = ts.slot_id AND r.status = 'CONFIRMED' AND r.reservation_id <> ? WHERE ts.slot_id = ? FOR UPDATE")) { check.setInt(1, reservationId); check.setInt(2, slotId); try (ResultSet rs = check.executeQuery()) { if (!rs.next() || "CLOSED".equals(rs.getString("status")) || "RESERVED".equals(rs.getString("status")) || rs.getString("reservation_id") != null) throw new SQLException("target unavailable"); } }
                try (PreparedStatement ps = conn.prepareStatement("UPDATE RESERVATION SET slot_id = ? WHERE reservation_id = ? AND user_id = ? AND status = 'CONFIRMED'")) { ps.setInt(1, slotId); ps.setInt(2, reservationId); ps.setInt(3, userId); ps.executeUpdate(); }
                conn.commit(); send(resp, 200, ok("Reservation updated."));
            } catch (SQLException e) { conn.rollback(); throw e; }
            finally { conn.setAutoCommit(true); }
        }
    }

    private void cancel(HttpServletResponse resp, int userId, int reservationId) throws SQLException, IOException {
        try (Connection conn = DatabaseUtil.getConnection(); PreparedStatement ps = conn.prepareStatement("UPDATE RESERVATION SET status = 'CANCELLED' WHERE reservation_id = ? AND user_id = ? AND status = 'CONFIRMED'")) { ps.setInt(1, reservationId); ps.setInt(2, userId); int count = ps.executeUpdate(); if (count == 0) { send(resp, 404, error("Reservation not found.")); return; } send(resp, 200, ok("Reservation cancelled.")); }
    }

    private List<Map<String, String>> querySlots(int spaceId) throws SQLException {
        String sql = "SELECT ts.slot_id, ts.start_time, ts.end_time, ts.status FROM TIME_SLOT ts LEFT JOIN RESERVATION r ON r.slot_id = ts.slot_id AND r.status = 'CONFIRMED' LEFT JOIN SLOT_LOCK l ON l.slot_id = ts.slot_id AND l.expires_at > NOW() WHERE ts.space_id = ? AND ts.status NOT IN ('CLOSED','RESERVED') AND r.reservation_id IS NULL AND l.lock_id IS NULL ORDER BY ts.start_time";
        return query(sql, new String[]{String.valueOf(spaceId)});
    }

    private List<Map<String, String>> queryReservations(int userId) throws SQLException {
        String sql = "SELECT r.reservation_id, r.slot_id, r.status, ts.start_time, ts.end_time, sp.name AS space_name, f.name AS facility_name FROM RESERVATION r JOIN TIME_SLOT ts ON ts.slot_id = r.slot_id JOIN SPACE sp ON sp.space_id = ts.space_id JOIN FACILITY f ON f.facility_id = sp.facility_id WHERE r.user_id = ? ORDER BY ts.start_time DESC";
        return query(sql, new String[]{String.valueOf(userId)});
    }

    private List<Map<String, String>> query(String sql, String[] params) throws SQLException {
        List<Map<String, String>> rows = new ArrayList<>();
        try (Connection conn = DatabaseUtil.getConnection(); PreparedStatement ps = conn.prepareStatement(sql)) {
            for (int i = 0; i < params.length; i++) ps.setString(i + 1, params[i]);
            try (ResultSet rs = ps.executeQuery()) { int n = rs.getMetaData().getColumnCount(); while (rs.next()) { Map<String, String> row = new LinkedHashMap<>(); for (int i = 1; i <= n; i++) row.put(rs.getMetaData().getColumnLabel(i), rs.getObject(i) == null ? "" : String.valueOf(rs.getObject(i))); rows.add(row); } }
        }
        return rows;
    }

    private Map<String, String> body(HttpServletRequest req) throws IOException { Map<String, String> out = new LinkedHashMap<>(); String raw = req.getReader().lines().reduce("", (a, b) -> a + b); Map<?, ?> json = gson.fromJson(raw, Map.class); if (json != null) for (Map.Entry<?, ?> e : json.entrySet()) out.put(String.valueOf(e.getKey()), e.getValue() == null ? "" : String.valueOf(e.getValue())); return out; }
    private Integer userId(HttpServletRequest req) { HttpSession s = req.getSession(false); return s == null ? null : (Integer) s.getAttribute("userId"); }
    private String role(HttpServletRequest req) { HttpSession s = req.getSession(false); return s == null ? "" : String.valueOf(s.getAttribute("role")); }
    private boolean canReserve(HttpServletRequest req) { return "RESERVATION_USER".equals(role(req)) || "ADMIN".equals(role(req)); }
    private Map<String, Object> ok(String message) { Map<String, Object> out = new LinkedHashMap<>(); out.put("ok", true); out.put("message", message); return out; }
    private Map<String, Object> error(String message) { Map<String, Object> out = new LinkedHashMap<>(); out.put("ok", false); out.put("error", message); return out; }
    private void send(HttpServletResponse resp, int status, Map<String, ?> payload) throws IOException { resp.setStatus(status); resp.getWriter().write(gson.toJson(payload)); }
}
