package com.recreation;

import java.io.IOException;
import java.io.PrintWriter;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;

import jakarta.servlet.ServletException;
import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

/*
 * ============================================================
 *  City Recreational Spaces Navigator — HomeServlet
 * ============================================================
 *  THREE-TIER ARCHITECTURE
 *    [Client Layer]     HTML + CSS + Leaflet map + login modal
 *    [Application]      This servlet queries MySQL and builds the page
 *    [Database Layer]   MySQL 8.4 LTS  `recreation_navigator.spaces`
 * ============================================================
 */
@WebServlet("/home")
public class HomeServlet extends HttpServlet {

    private static final long serialVersionUID = 1L;

    /* My Configurations */
    private static final String HOSTNAME = "localhost"; // "localhost" OR "127.0.0.1"
    private static final String SQL_PORT = "3306";      // Default port is 3306
    private static final String DATABASE = "recreation_navigator";
    private static final String USERNAME = "root";
    private static final String PASSWORD = "1aBCD23$";

    private static final String URL =
            "jdbc:mysql://" + HOSTNAME + ":" + SQL_PORT + "/" + DATABASE +
            "?useSSL=false" +
            "&allowPublicKeyRetrieval=true" +
            "&serverTimezone=UTC";

    @Override
    protected void doGet(HttpServletRequest request, HttpServletResponse response)
            throws ServletException, IOException {

        response.setContentType("text/html;charset=UTF-8");

        StringBuilder markers  = new StringBuilder();
        StringBuilder listHTML = new StringBuilder();
        int count     = 0;
        int openCount = 0;

        try {
            // MySQL Connector/J
            Class.forName("com.mysql.cj.jdbc.Driver"); // "com.mysql.jdbc.Driver" is deprecated

            try (
                    /* Step 01: Connect to database */
                    Connection connection = DriverManager.getConnection(URL, USERNAME, PASSWORD);
                    Statement statement = connection.createStatement();
                    ResultSet resultSet = statement.executeQuery("SELECT * FROM spaces")
                ) {
                System.out.println(DATABASE + " database successfully opened.");

                /* Step 02: Read every space and build client-side fragments */
                while (resultSet.next()) {
                    count++;
                    String name    = resultSet.getString("name");
                    String type    = resultSet.getString("type");
                    String address = resultSet.getString("address");
                    double lat     = resultSet.getDouble("latitude");
                    double lng     = resultSet.getDouble("longitude");
                    String status  = resultSet.getString("status");
                    String price   = resultSet.getString("price");

                    if ("open".equalsIgnoreCase(status)) openCount++;

                    String jsName = escapeJs(name);

                    // JS snippet for the map
                    markers.append(String.format(
                        "  addMarker(%f, %f, '%s', '%s', '%s', '%s', '%s');%n",
                        lat, lng, jsName, escapeJs(type), escapeJs(address),
                        status, escapeJs(price)));

                    // HTML snippet for the sidebar
                    listHTML.append("<div class=\"space-card\">")
                            .append("<h3>").append(escapeHtml(name)).append("</h3>")
                            .append("<p class=\"meta\">").append(escapeHtml(type))
                            .append(" &middot; ").append(escapeHtml(address))
                            .append("</p>")
                            .append("<span class=\"badge ").append(status).append("\">")
                            .append(status).append("</span>")
                            .append("</div>");
                } //END while

            } //END inner try
            // END outer try

        } catch (ClassNotFoundException exception) {
            System.err.println("MySQL JDBC driver was not found.");
            System.err.println("Check whether the Connector/J JAR is under WEB-INF/lib.");
            exception.printStackTrace();
        } catch (SQLException exception) {
            System.err.println("Failed to connect to MySQL.");
            System.err.println("SQLState: " + exception.getSQLState());
            System.err.println("Error code: " + exception.getErrorCode());
            System.err.println("Message: " + exception.getMessage());
            exception.printStackTrace();
        } // END try-catch

        /* Step 03: Build the CLIENT LAYER (HTML page) */
        try (PrintWriter out = response.getWriter()) {
            out.println(page(markers.toString(), listHTML.toString(), count, openCount));
        }
    } // END doGet

    /* ============================================================
       The full HTML page. Kept in one method for readability.
       ============================================================ */
    private static String page(String markers, String listHTML, int count, int openCount) {
        return "<!DOCTYPE html>\n"
             + "<html lang=\"en\">\n"
             + "<head>\n"
             + "<meta charset=\"UTF-8\">\n"
             + "<meta name=\"viewport\" content=\"width=device-width, initial-scale=1\">\n"
             + "<title>City Recreational Spaces Navigator</title>\n"
             + "<link rel=\"stylesheet\" href=\"https://unpkg.com/leaflet@1.9.4/dist/leaflet.css\"/>\n"
             + "<style>\n"
             + "  * { box-sizing: border-box; }\n"
             + "  body { margin: 0; font-family: \"Segoe UI\", system-ui, sans-serif;\n"
             + "         background: #f6faf7; color: #1f2933; }\n"
             + "  .site-header { background: #fff; border-bottom: 1px solid #e2e8f0;\n"
             + "                 padding: 12px 20px; display: flex; align-items: center; gap: 24px; }\n"
             + "  .brand { font-weight: 700; font-size: 17px; }\n"
             + "  .brand small { display: block; font-weight: 400; font-size: 11px; color: #6b7280; }\n"
             + "  .btn { border: none; border-radius: 9px; font-size: 14px; font-weight: 600;\n"
             + "         padding: 9px 18px; cursor: pointer; }\n"
             + "  .btn-primary { background: #2e7d32; color: #fff; margin-left: auto; }\n"
             + "  .hero { background: linear-gradient(135deg, #1b5e20, #43a047);\n"
             + "          color: #fff; padding: 40px 20px; text-align: center; }\n"
             + "  .hero h1 { margin: 0 0 8px; font-size: 30px; }\n"
             + "  .layout { max-width: 1400px; margin: 0 auto; display: grid;\n"
             + "            grid-template-columns: 370px 1fr; gap: 20px; padding: 26px 20px 56px; }\n"
             + "  .panel { background: #fff; border: 1px solid #e2e8f0; border-radius: 14px; overflow: hidden; }\n"
             + "  .panel-head { padding: 16px 18px; border-bottom: 1px solid #e2e8f0;\n"
             + "                display: flex; justify-content: space-between; }\n"
             + "  .panel-head h2 { margin: 0; font-size: 16px; }\n"
             + "  .panel-body { padding: 12px; max-height: 600px; overflow-y: auto; }\n"
             + "  .space-card { border: 1px solid #e2e8f0; border-radius: 11px;\n"
             + "                padding: 13px 14px; margin-bottom: 10px; }\n"
             + "  .space-card h3 { margin: 0 0 4px; font-size: 14.5px; }\n"
             + "  .meta { margin: 0; font-size: 12.5px; color: #6b7280; }\n"
             + "  .badge { display: inline-block; font-size: 10.5px; font-weight: 700;\n"
             + "           text-transform: uppercase; padding: 3px 8px; border-radius: 20px; margin-top: 6px; }\n"
             + "  .badge.open { background: #e8f5e9; color: #1b5e20; }\n"
             + "  .badge.limited { background: #fff4e5; color: #b26a00; }\n"
             + "  .badge.reserved { background: #fdecea; color: #b3261e; }\n"
             + "  #map { height: 600px; width: 100%; border-radius: 14px; border: 1px solid #e2e8f0; }\n"
             + "  @media (max-width: 900px) { .layout { grid-template-columns: 1fr; } }\n"
             + "</style>\n"
             + "</head>\n"
             + "<body>\n"

             + "<header class=\"site-header\">\n"
             + "  <div class=\"brand\">&#127795; City Recreational Spaces Navigator\n"
             + "    <small>Find it. Check it. Reserve it.</small>\n"
             + "  </div>\n"
             + "  <button class=\"btn btn-primary\">Login</button>\n"
             + "</header>\n"

             + "<section class=\"hero\">\n"
             + "  <h1>Find an open court, field, or park &mdash; right now.</h1>\n"
             + "  <p><b>" + count + "</b> spaces tracked &nbsp;&middot;&nbsp;\n"
             + "     <b>" + openCount + "</b> open now</p>\n"
             + "</section>\n"

             + "<main class=\"layout\">\n"
             + "  <aside class=\"panel\">\n"
             + "    <div class=\"panel-head\">\n"
             + "      <h2>Nearby Spaces</h2>\n"
             + "      <span>" + count + " results</span>\n"
             + "    </div>\n"
             + "    <div class=\"panel-body\">\n"
             + "      " + listHTML + "\n"
             + "    </div>\n"
             + "  </aside>\n"
             + "  <div><div id=\"map\"></div></div>\n"
             + "</main>\n"

             + "<script src=\"https://unpkg.com/leaflet@1.9.4/dist/leaflet.js\"></script>\n"
             + "<script>\n"
             + "  var map = L.map('map').setView([37.7775, -122.4230], 14);\n"
             + "  L.tileLayer('https://{s}.tile.openstreetmap.org/{z}/{x}/{y}.png',\n"
             + "    { maxZoom: 19, attribution: '&copy; OpenStreetMap contributors' }).addTo(map);\n"
             + "\n"
             + "  var STATUS_COLOR = { open: '#2e7d32', limited: '#f59e0b', reserved: '#dc2626' };\n"
             + "\n"
             + "  function addMarker(lat, lng, name, type, address, status, price) {\n"
             + "    L.circleMarker([lat, lng], {\n"
             + "      radius: 10, color: '#fff', weight: 2,\n"
             + "      fillColor: STATUS_COLOR[status] || '#6b7280', fillOpacity: 0.95\n"
             + "    }).addTo(map).bindPopup(\n"
             + "      '<strong>' + name + '</strong><br>' + type + ' &middot; ' + address +\n"
             + "      '<br><em>' + price + '</em>'\n"
             + "    );\n"
             + "  }\n"
             + "\n"
             + markers
             + "</script>\n"
             + "</body>\n"
             + "</html>\n";
    }

    /* Helper: escape single quotes and backslashes for JS string literals */
    private static String escapeJs(String value) {
        if (value == null) return "";
        return value.replace("\\", "\\\\").replace("'", "\\'");
    }

    /* Helper: escape HTML metacharacters */
    private static String escapeHtml(String value) {
        if (value == null) return "";
        return value.replace("&", "&amp;")
                    .replace("<", "&lt;")
                    .replace(">", "&gt;")
                    .replace("\"", "&quot;");
    }

} // END public class