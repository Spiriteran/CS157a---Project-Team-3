package com.cityrecreation.controller;

import com.cityrecreation.util.DatabaseUtil;
import com.google.gson.Gson;

import javax.servlet.ServletException;
import javax.servlet.annotation.WebServlet;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.ResultSetMetaData;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Serves the five GUEST-level functional requirements of the City Recreational
 * Spaces Navigator as JSON. Each requirement maps to one hand-written SELECT
 * (the same SQL kept in functional_requirements.sql), executed as a
 * PreparedStatement against the authoritative `city_recreation` schema.
 *
 * Endpoint:  GET /api/fr?fr=1..5  (plus optional per-FR filter params)
 *
 * Response shape:
 *   { "fr": 1, "title": "...", "sql": "...",
 *     "columns": [...], "rows": [ {col:val, ...}, ... ] }
 */
@WebServlet("/api/fr")
public class FunctionalRequirementServlet extends HttpServlet {

    private static final long serialVersionUID = 1L;
    private final Gson gson = new Gson();

    // ----- FR-1: Search & filter facilities by city + space type ---------
    private static final String SQL_FR1 =
            "SELECT f.facility_id, f.name AS facility_name, f.address, "
          + "       c.name AS city_name, s.space_type "
          + "FROM FACILITY f "
          + "JOIN CITY c ON c.city_id = f.city_id "
          + "JOIN SPACE s ON s.facility_id = f.facility_id "
          + "WHERE c.name = ? AND s.space_type = ? "
          + "  AND f.is_active = TRUE AND s.is_active = TRUE "
          + "GROUP BY f.facility_id, f.name, f.address, c.name, s.space_type "
          + "ORDER BY f.name";

    // ----- FR-2: View full details for one space -------------------------
    private static final String SQL_FR2 =
            "SELECT s.space_id, s.name AS space_name, s.space_type, s.access_mode, "
          + "       s.price, f.name AS facility_name, f.address, "
          + "       c.name AS city_name, c.state "
          + "FROM SPACE s "
          + "JOIN FACILITY f ON f.facility_id = s.facility_id "
          + "JOIN CITY c ON c.city_id = f.city_id "
          + "WHERE s.space_id = ?";

    // ----- FR-3: Availability by time slot for a space -------------------
    private static final String SQL_FR3 =
            "SELECT ts.slot_id, ts.start_time, ts.end_time, "
          + "       CASE WHEN r.reservation_id IS NOT NULL THEN 'OCCUPIED' "
          + "            ELSE ts.status END AS availability "
          + "FROM TIME_SLOT ts "
          + "LEFT JOIN RESERVATION r ON r.slot_id = ts.slot_id AND r.status = 'CONFIRMED' "
          + "WHERE ts.space_id = ? "
          + "ORDER BY ts.start_time";

    // ----- FR-4: Public events (never PRIVATE, never cancelled) ----------
    private static final String SQL_FR4 =
            "SELECT e.event_id, e.title, e.description, f.name AS facility_name, "
          + "       sp.name AS space_name, ts.start_time, ts.end_time "
          + "FROM EVENT e "
          + "JOIN RESERVATION r ON r.reservation_id = e.reservation_id "
          + "JOIN TIME_SLOT ts ON ts.slot_id = r.slot_id "
          + "JOIN SPACE sp ON sp.space_id = ts.space_id "
          + "JOIN FACILITY f ON f.facility_id = sp.facility_id "
          + "WHERE e.visibility = 'PUBLIC' AND r.status = 'CONFIRMED' "
          + "ORDER BY ts.start_time";

    // ----- FR-5: Access-mode labels for every active space ---------------
    private static final String SQL_FR5 =
            "SELECT s.space_id, s.name AS space_name, f.name AS facility_name, "
          + "       s.access_mode, "
          + "       CASE s.access_mode "
          + "            WHEN 'OPEN_PLAY' THEN 'Open Play (free)' "
          + "            WHEN 'PAY_TO_PLAY' THEN 'Pay to Play' "
          + "            WHEN 'RESERVATION_ONLY' THEN 'Reservation Only' "
          + "            ELSE 'Unknown' END AS access_label, "
          + "       s.price "
          + "FROM SPACE s "
          + "JOIN FACILITY f ON f.facility_id = s.facility_id "
          + "WHERE s.is_active = TRUE "
          + "ORDER BY s.access_mode, s.name";

    @Override
    protected void doGet(HttpServletRequest req, HttpServletResponse resp)
            throws ServletException, IOException {

        resp.setContentType("application/json");
        resp.setCharacterEncoding("UTF-8");

        String frParam = req.getParameter("fr");
        int fr;
        try {
            fr = Integer.parseInt(frParam == null ? "" : frParam.trim());
        } catch (NumberFormatException e) {
            writeError(resp, HttpServletResponse.SC_BAD_REQUEST,
                    "Missing or invalid 'fr' parameter (expected 1-5).");
            return;
        }

        String sql;
        String title;
        Object[] params;

        try {
            switch (fr) {
                case 1:
                    sql = SQL_FR1;
                    title = "FR-1: Search & filter facilities";
                    params = new Object[]{
                            defaultStr(req.getParameter("city"), "San Jose"),
                            defaultStr(req.getParameter("space_type"), "Basketball Court")
                    };
                    break;
                case 2:
                    sql = SQL_FR2;
                    title = "FR-2: View space details";
                    params = new Object[]{ parseIntDefault(req.getParameter("space_id"), 3) };
                    break;
                case 3:
                    sql = SQL_FR3;
                    title = "FR-3: View availability by time slot";
                    params = new Object[]{ parseIntDefault(req.getParameter("space_id"), 1) };
                    break;
                case 4:
                    sql = SQL_FR4;
                    title = "FR-4: View public events";
                    params = new Object[]{};
                    break;
                case 5:
                    sql = SQL_FR5;
                    title = "FR-5: Access-mode labels";
                    params = new Object[]{};
                    break;
                default:
                    writeError(resp, HttpServletResponse.SC_BAD_REQUEST,
                            "'fr' must be between 1 and 5.");
                    return;
            }

            Map<String, Object> payload = runQuery(sql, params);
            payload.put("fr", fr);
            payload.put("title", title);
            payload.put("sql", sql);
            resp.getWriter().write(gson.toJson(payload));

        } catch (SQLException e) {
            e.printStackTrace();
            writeError(resp, HttpServletResponse.SC_INTERNAL_SERVER_ERROR,
                    "Database error: " + e.getMessage());
        }
    }

    /** Executes a parameterized query and returns {columns, rows}. */
    private Map<String, Object> runQuery(String sql, Object[] params) throws SQLException {
        Map<String, Object> result = new LinkedHashMap<>();
        List<String> columns = new ArrayList<>();
        List<Map<String, Object>> rows = new ArrayList<>();

        try (Connection conn = DatabaseUtil.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {

            for (int i = 0; i < params.length; i++) {
                ps.setObject(i + 1, params[i]);
            }

            try (ResultSet rs = ps.executeQuery()) {
                ResultSetMetaData md = rs.getMetaData();
                int colCount = md.getColumnCount();
                for (int i = 1; i <= colCount; i++) {
                    columns.add(md.getColumnLabel(i));
                }
                while (rs.next()) {
                    Map<String, Object> row = new LinkedHashMap<>();
                    for (int i = 1; i <= colCount; i++) {
                        Object val = rs.getObject(i);
                        row.put(md.getColumnLabel(i), val == null ? null : String.valueOf(val));
                    }
                    rows.add(row);
                }
            }
        }

        result.put("columns", columns);
        result.put("rows", rows);
        result.put("rowCount", rows.size());
        return result;
    }

    private void writeError(HttpServletResponse resp, int status, String message)
            throws IOException {
        resp.setStatus(status);
        Map<String, Object> err = new LinkedHashMap<>();
        err.put("error", message);
        resp.getWriter().write(gson.toJson(err));
    }

    private static String defaultStr(String v, String fallback) {
        return (v == null || v.trim().isEmpty()) ? fallback : v.trim();
    }

    private static int parseIntDefault(String v, int fallback) {
        try {
            return Integer.parseInt(v.trim());
        } catch (Exception e) {
            return fallback;
        }
    }
}
