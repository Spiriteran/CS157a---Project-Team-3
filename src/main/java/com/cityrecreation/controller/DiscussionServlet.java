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

/** Read-only public discussion summaries and comments for the forum page. */
@WebServlet("/api/discussions")
public class DiscussionServlet extends HttpServlet {
    private static final long serialVersionUID = 1L;
    private final Gson gson = new Gson();

    @Override
    protected void doGet(HttpServletRequest req, HttpServletResponse resp) throws IOException {
        resp.setContentType("application/json");
        resp.setCharacterEncoding("UTF-8");
        String eventId = req.getParameter("event_id");
        try {
            Map<String, Object> result = "".equals(eventId) || eventId == null
                    ? summaries() : comments(eventId);
            resp.getWriter().write(gson.toJson(result));
        } catch (Exception e) {
            e.printStackTrace();
            resp.setStatus(500);
            resp.getWriter().write(gson.toJson(error("Discussions could not be loaded right now.")));
        }
    }

    private Map<String, Object> summaries() throws Exception {
        String sql = "SELECT e.event_id, e.title, e.description, f.name AS facility_name, "
                + "sp.name AS space_name, ts.start_time, ts.end_time, COUNT(c.comment_id) AS comment_count, "
                + "MAX(c.created_time) AS latest_comment "
                + "FROM EVENT e JOIN RESERVATION r ON r.reservation_id = e.reservation_id "
                + "JOIN TIME_SLOT ts ON ts.slot_id = r.slot_id JOIN SPACE sp ON sp.space_id = ts.space_id "
                + "JOIN FACILITY f ON f.facility_id = sp.facility_id LEFT JOIN `COMMENT` c ON c.event_id = e.event_id "
                + "WHERE e.visibility = 'PUBLIC' AND r.status = 'CONFIRMED' "
                + "GROUP BY e.event_id, e.title, e.description, f.name, sp.name, ts.start_time, ts.end_time "
                + "ORDER BY COALESCE(MAX(c.created_time), ts.start_time) DESC";
        return query(sql, new String[0]);
    }

    private Map<String, Object> comments(String eventId) throws Exception {
        int id;
        try { id = Integer.parseInt(eventId); } catch (NumberFormatException e) { return error("Invalid discussion id."); }
        String sql = "SELECT c.comment_id, c.event_id, c.body, c.created_time, u.email AS author "
                + "FROM `COMMENT` c JOIN `USER` u ON u.user_id = c.user_id "
                + "JOIN EVENT e ON e.event_id = c.event_id JOIN RESERVATION r ON r.reservation_id = e.reservation_id "
                + "WHERE c.event_id = ? AND e.visibility = 'PUBLIC' AND r.status = 'CONFIRMED' "
                + "ORDER BY c.created_time";
        return query(sql, new String[]{String.valueOf(id)});
    }

    private Map<String, Object> query(String sql, String[] params) throws Exception {
        List<Map<String, String>> rows = new ArrayList<>();
        try (Connection conn = DatabaseUtil.getConnection(); PreparedStatement ps = conn.prepareStatement(sql)) {
            for (int i = 0; i < params.length; i++) ps.setString(i + 1, params[i]);
            try (ResultSet rs = ps.executeQuery()) {
                int count = rs.getMetaData().getColumnCount();
                while (rs.next()) {
                    Map<String, String> row = new LinkedHashMap<>();
                    for (int i = 1; i <= count; i++) row.put(rs.getMetaData().getColumnLabel(i), String.valueOf(rs.getObject(i)));
                    rows.add(row);
                }
            }
        }
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("rows", rows); result.put("rowCount", rows.size());
        return result;
    }

    private Map<String, Object> error(String message) {
        Map<String, Object> result = new LinkedHashMap<>(); result.put("error", message); return result;
    }
}
