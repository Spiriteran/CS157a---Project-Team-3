package com.cityrecreation.controller;

import com.cityrecreation.model.Facility;
import com.cityrecreation.model.Space;
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
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@WebServlet("/api/facilities")
public class FacilityServlet extends HttpServlet {

    private Gson gson = new Gson();

    @Override
    protected void doGet(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
        resp.setContentType("application/json");
        resp.setCharacterEncoding("UTF-8");

        List<Facility> facilities = new ArrayList<>();
        Map<Integer, Facility> facilityMap = new HashMap<>();

        try (Connection conn = DatabaseUtil.getConnection()) {
            // 1. Fetch facilities
            String facQuery = "SELECT * FROM facilities";
            try (PreparedStatement ps = conn.prepareStatement(facQuery);
                 ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    Facility f = new Facility(
                            rs.getInt("id"),
                            rs.getString("name"),
                            rs.getString("address"),
                            rs.getDouble("latitude"),
                            rs.getDouble("longitude"),
                            rs.getString("status")
                    );
                    facilities.add(f);
                    facilityMap.put(f.id, f);
                }
            }

            // 2. Fetch spaces and attach to facilities
            String spaceQuery = "SELECT * FROM spaces";
            try (PreparedStatement ps = conn.prepareStatement(spaceQuery);
                 ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    int facId = rs.getInt("facility_id");
                    Facility parentFac = facilityMap.get(facId);
                    if (parentFac != null) {
                        Space s = new Space(
                                rs.getInt("id"),
                                rs.getString("name"),
                                rs.getString("access_type"),
                                "Available" // Mock status since we aren't querying active reservations yet
                        );
                        parentFac.spaces.add(s);
                    }
                }
            }

            // Write JSON response
            String jsonResponse = gson.toJson(facilities);
            resp.getWriter().write(jsonResponse);

        } catch (SQLException e) {
            e.printStackTrace();
            resp.setStatus(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
            resp.getWriter().write("{\"error\": \"Database error occurred.\"}");
        }
    }
}
