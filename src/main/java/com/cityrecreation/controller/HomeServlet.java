package com.cityrecreation.controller;

import com.cityrecreation.util.DatabaseUtil;
import com.google.gson.Gson;

import javax.servlet.annotation.WebServlet;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Provides the minimal authenticated landing-page data. */
@WebServlet("/api/home")
public class HomeServlet extends HttpServlet {
    private static final long serialVersionUID = 1L;
    private final Gson gson = new Gson();

    @Override
    protected void doGet(HttpServletRequest req, HttpServletResponse resp) throws IOException {
        resp.setContentType("application/json");
        resp.setCharacterEncoding("UTF-8");
        Object userId = req.getSession(false) == null ? null : req.getSession(false).getAttribute("userId");
        if (userId == null) {
            resp.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
            resp.getWriter().write(gson.toJson(error("Please sign in to view your home page.")));
            return;
        }

        try {
            Map<String, Object> payload = new LinkedHashMap<>();
            String email = String.valueOf(req.getSession(false).getAttribute("email"));
            payload.put("username", email.substring(0, email.indexOf('@')));
            payload.put("city", "your area");
            payload.put("events", events(String.valueOf(userId), payload));
            resp.getWriter().write(gson.toJson(payload));
        } catch (Exception e) {
            e.printStackTrace();
            resp.setStatus(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
            resp.getWriter().write(gson.toJson(error("Your suggested events could not be loaded right now.")));
        }
    }

    private List<Map<String, String>> events(String userId, Map<String, Object> payload) throws Exception {
        String sql = "SELECT e.event_id, e.title, e.description, f.name AS facility_name, "
                + "sp.name AS space_name, c.name AS city_name, ts.start_time, ts.end_time "
                + "FROM EVENT e JOIN RESERVATION r ON r.reservation_id = e.reservation_id "
                + "JOIN TIME_SLOT ts ON ts.slot_id = r.slot_id JOIN SPACE sp ON sp.space_id = ts.space_id "
                + "JOIN FACILITY f ON f.facility_id = sp.facility_id JOIN CITY c ON c.city_id = f.city_id "
                + "LEFT JOIN PREFERENCE p ON p.user_id = ? "
                + "WHERE e.visibility = 'PUBLIC' AND r.status = 'CONFIRMED' "
                + "AND (p.default_city_id IS NULL OR c.city_id = p.default_city_id) "
                + "ORDER BY ts.start_time LIMIT 5";
        List<Map<String, String>> rows = new ArrayList<>();
        try (Connection conn = DatabaseUtil.getConnection(); PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, Integer.parseInt(userId));
            try (ResultSet rs = ps.executeQuery()) {
                int count = rs.getMetaData().getColumnCount();
                while (rs.next()) {
                    Map<String, String> row = new LinkedHashMap<>();
                    for (int i = 1; i <= count; i++) row.put(rs.getMetaData().getColumnLabel(i), value(rs.getObject(i)));
                    if (payload.get("city").equals("your area")) payload.put("city", row.get("city_name"));
                    rows.add(row);
                }
            }
        }
        return rows;
    }

    private String value(Object value) { return value == null ? "" : String.valueOf(value); }
    private Map<String, String> error(String message) { Map<String, String> result = new LinkedHashMap<>(); result.put("error", message); return result; }
}
