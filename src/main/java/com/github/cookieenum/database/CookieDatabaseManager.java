package com.github.cookieenum.database;

import burp.api.montoya.MontoyaApi;
import com.github.cookieenum.models.CookieCategory;
import com.github.cookieenum.models.CookieInfo;
import com.github.cookieenum.models.PrivacyImpact;

import java.io.BufferedReader;
import java.io.File;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.security.MessageDigest;
import java.sql.*;
import java.time.Instant;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Manages the SQLite database for cookie information
 */
public class CookieDatabaseManager {
    private final MontoyaApi api;
    private Connection connection;
    private final String databasePath;

    public CookieDatabaseManager(MontoyaApi api) {
        this.api = api;
        this.databasePath = getDatabasePath();
        initializeDatabase();
    }

    /**
     * Get configured database path or default
     */
    private String getDatabasePath() {
        // Check user configuration first
        String configured = api.persistence().preferences()
                .getString("cookiedb.path");

        if (configured != null && !configured.isEmpty()) {
            return configured;
        }

        // Use default path
        return getDefaultDatabasePath();
    }

    /**
     * Get default database path based on OS
     */
    private String getDefaultDatabasePath() {
        String home = System.getProperty("user.home");
        String os = System.getProperty("os.name").toLowerCase();

        String basePath;
        if (os.contains("win")) {
            basePath = home + "\\.burp-cookie-db";
        } else {
            basePath = home + "/.burp-cookie-db";
        }

        // Create directory if it doesn't exist
        try {
            Files.createDirectories(Paths.get(basePath));
        } catch (Exception e) {
            api.logging().logToError("Failed to create database directory: " + e.getMessage());
        }

        return basePath + File.separator + "cookies.db";
    }

    /**
     * Initialize database connection and schema
     */
    private void initializeDatabase() {
        try {
            // Load SQLite JDBC driver
            Class.forName("org.sqlite.JDBC");

            // Create directory if needed
            File dbFile = new File(databasePath);
            File dbDir = dbFile.getParentFile();
            if (dbDir != null && !dbDir.exists()) {
                dbDir.mkdirs();
            }

            // Connect to database
            String url = "jdbc:sqlite:" + databasePath;
            connection = DriverManager.getConnection(url);

            // Enable foreign keys
            try (Statement stmt = connection.createStatement()) {
                stmt.execute("PRAGMA foreign_keys = ON");
            }

            // Initialize schema
            executeSchemaScript();

            api.logging().logToOutput("Cookie database initialized: " + databasePath);

        } catch (Exception e) {
            api.logging().logToError("Failed to initialize database: " + e.getMessage());
            e.printStackTrace();
        }
    }

    /**
     * Execute the schema.sql script
     */
    private void executeSchemaScript() throws Exception {
        InputStream schemaStream = getClass().getClassLoader()
                .getResourceAsStream("schema.sql");

        if (schemaStream == null) {
            api.logging().logToError("schema.sql not found in resources");
            return;
        }

        try (BufferedReader reader = new BufferedReader(new InputStreamReader(schemaStream))) {
            StringBuilder currentStatement = new StringBuilder();
            String line;
            int lineNum = 0;
            int beginEndDepth = 0; // Track nesting of BEGIN...END blocks

            try (Statement stmt = connection.createStatement()) {
                int stmtCount = 0;

                while ((line = reader.readLine()) != null) {
                    lineNum++;
                    String trimmed = line.trim();

                    // Skip empty lines and full-line comments
                    if (trimmed.isEmpty() || trimmed.startsWith("--")) {
                        continue;
                    }

                    // Remove inline comments
                    int commentPos = trimmed.indexOf("--");
                    if (commentPos > 0) {
                        trimmed = trimmed.substring(0, commentPos).trim();
                    }

                    // Track BEGIN...END depth
                    String upper = trimmed.toUpperCase();
                    if (upper.equals("BEGIN") || upper.startsWith("BEGIN ")) {
                        beginEndDepth++;
                    }
                    if (upper.equals("END;") || upper.equals("END")) {
                        if (beginEndDepth > 0) {
                            beginEndDepth--;
                        }
                    }

                    // Append line to current statement
                    currentStatement.append(trimmed).append(" ");

                    // Statement is complete only if it ends with ; AND we're not inside BEGIN...END
                    if (trimmed.endsWith(";") && beginEndDepth == 0) {
                        String sqlStatement = currentStatement.toString().trim();

                        if (!sqlStatement.isEmpty()) {
                            try {
                                stmt.execute(sqlStatement);
                                stmtCount++;
                                api.logging().logToOutput("Executed statement " + stmtCount + ": " +
                                    sqlStatement.substring(0, Math.min(50, sqlStatement.length())) + "...");
                            } catch (SQLException e) {
                                api.logging().logToError("Failed at line " + lineNum);
                                api.logging().logToError("Statement: " + sqlStatement);
                                api.logging().logToError("Error: " + e.getMessage());
                                throw e;
                            }
                        }

                        // Reset for next statement
                        currentStatement = new StringBuilder();
                    }
                }

                // Check for incomplete statement
                if (currentStatement.length() > 0) {
                    api.logging().logToError("Warning: Incomplete statement at end of file: " +
                        currentStatement.toString().trim());
                }

                api.logging().logToOutput("Successfully executed " + stmtCount + " SQL statements");
            }

        }
    }


    /**
     * Get cookie info from database (exact match first, then pattern match)
     */
    public Optional<CookieInfo> getCookieInfo(String name, String domain) {
        // Try exact match first
        Optional<CookieInfo> exact = getCookieInfoExact(name);
        if (exact.isPresent()) {
            return exact;
        }

        // Try pattern match
        return getCookieInfoByPattern(name);
    }

    /**
     * Get cookie info by exact name match
     */
    private Optional<CookieInfo> getCookieInfoExact(String name) {
        String sql = "SELECT * FROM cookies WHERE name = ?";

        try (PreparedStatement stmt = connection.prepareStatement(sql)) {
            stmt.setString(1, name);

            try (ResultSet rs = stmt.executeQuery()) {
                if (rs.next()) {
                    return Optional.of(mapResultSetToCookieInfo(rs));
                }
            }
        } catch (SQLException e) {
            api.logging().logToError("Database query error: " + e.getMessage());
        }

        return Optional.empty();
    }

    /**
     * Get cookie info by pattern matching (e.g., _ga_* matches _ga)
     */
    private Optional<CookieInfo> getCookieInfoByPattern(String name) {
        String sql = "SELECT c.* FROM cookies c " +
                     "JOIN cookie_patterns p ON c.id = p.cookie_id " +
                     "WHERE ? LIKE REPLACE(p.pattern, '*', '%')";

        try (PreparedStatement stmt = connection.prepareStatement(sql)) {
            stmt.setString(1, name);

            try (ResultSet rs = stmt.executeQuery()) {
                if (rs.next()) {
                    CookieInfo info = mapResultSetToCookieInfo(rs);
                    api.logging().logToOutput(String.format(
                        "Pattern match: %s matched to %s", name, info.getName()));
                    return Optional.of(info);
                }
            }
        } catch (SQLException e) {
            api.logging().logToError("Pattern match error: " + e.getMessage());
        }

        return Optional.empty();
    }

    /**
     * Store or update cookie information
     */
    public void storeCookieInfo(CookieInfo info) {
        String sql = "INSERT INTO cookies (name, vendor, category, purpose, privacy_impact, " +
                     "is_third_party, typical_expiration, common_domains, notes, confidence_score, source) " +
                     "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?) " +
                     "ON CONFLICT(name) DO UPDATE SET " +
                     "vendor = excluded.vendor, " +
                     "category = excluded.category, " +
                     "purpose = excluded.purpose, " +
                     "privacy_impact = excluded.privacy_impact, " +
                     "is_third_party = excluded.is_third_party, " +
                     "typical_expiration = excluded.typical_expiration, " +
                     "common_domains = excluded.common_domains, " +
                     "notes = excluded.notes, " +
                     "confidence_score = excluded.confidence_score";

        try (PreparedStatement stmt = connection.prepareStatement(sql)) {
            stmt.setString(1, info.getName());
            stmt.setString(2, info.getVendor());
            stmt.setString(3, info.getCategory().name());
            stmt.setString(4, info.getPurpose());
            stmt.setString(5, info.getPrivacyImpact() != null ? info.getPrivacyImpact().name() : null);
            stmt.setBoolean(6, info.isThirdParty());
            stmt.setString(7, info.getTypicalExpiration());
            stmt.setString(8, String.join(",", info.getCommonDomains()));
            stmt.setString(9, info.getNotes());
            stmt.setDouble(10, info.getConfidenceScore());
            stmt.setString(11, info.getSource());

            stmt.executeUpdate();

            api.logging().logToOutput(String.format(
                "Stored cookie info: %s (%s - %s)",
                info.getName(), info.getVendor(), info.getCategory()));

        } catch (SQLException e) {
            api.logging().logToError("Failed to store cookie info: " + e.getMessage());
        }
    }

    /**
     * Store AI query in cache
     */
    public void storeAIQueryCache(String cookieName, String domain, String rawResponse) {
        String queryHash = generateQueryHash(cookieName, domain);
        String sql = "INSERT OR REPLACE INTO ai_query_cache " +
                     "(cookie_name, domain, query_hash, raw_response) " +
                     "VALUES (?, ?, ?, ?)";

        try (PreparedStatement stmt = connection.prepareStatement(sql)) {
            stmt.setString(1, cookieName);
            stmt.setString(2, domain);
            stmt.setString(3, queryHash);
            stmt.setString(4, rawResponse);

            stmt.executeUpdate();

        } catch (SQLException e) {
            api.logging().logToError("Failed to cache AI query: " + e.getMessage());
        }
    }

    /**
     * Add a pattern for a cookie
     */
    public void addCookiePattern(String cookieName, String pattern) {
        // First get the cookie ID
        String getCookieIdSql = "SELECT id FROM cookies WHERE name = ?";
        String insertPatternSql = "INSERT OR IGNORE INTO cookie_patterns (pattern, cookie_id) VALUES (?, ?)";

        try (PreparedStatement getCookie = connection.prepareStatement(getCookieIdSql)) {
            getCookie.setString(1, cookieName);

            try (ResultSet rs = getCookie.executeQuery()) {
                if (rs.next()) {
                    long cookieId = rs.getLong("id");

                    try (PreparedStatement insertPattern = connection.prepareStatement(insertPatternSql)) {
                        insertPattern.setString(1, pattern);
                        insertPattern.setLong(2, cookieId);
                        insertPattern.executeUpdate();

                        api.logging().logToOutput(String.format(
                            "Added pattern '%s' for cookie '%s'", pattern, cookieName));
                    }
                }
            }
        } catch (SQLException e) {
            api.logging().logToError("Failed to add pattern: " + e.getMessage());
        }
    }

    /**
     * Get all cookies in database
     */
    public List<CookieInfo> getAllCookies() {
        List<CookieInfo> cookies = new ArrayList<>();
        String sql = "SELECT * FROM cookies ORDER BY name";

        try (Statement stmt = connection.createStatement();
             ResultSet rs = stmt.executeQuery(sql)) {

            while (rs.next()) {
                cookies.add(mapResultSetToCookieInfo(rs));
            }

        } catch (SQLException e) {
            api.logging().logToError("Failed to get all cookies: " + e.getMessage());
        }

        return cookies;
    }

    /**
     * Get database statistics
     */
    public Map<String, Object> getStatistics() {
        Map<String, Object> stats = new HashMap<>();

        try (Statement stmt = connection.createStatement()) {
            // Total cookies
            ResultSet rs = stmt.executeQuery("SELECT COUNT(*) as count FROM cookies");
            if (rs.next()) {
                stats.put("totalCookies", rs.getInt("count"));
            }

            // Total patterns
            rs = stmt.executeQuery("SELECT COUNT(*) as count FROM cookie_patterns");
            if (rs.next()) {
                stats.put("totalPatterns", rs.getInt("count"));
            }

            // Total AI queries cached
            rs = stmt.executeQuery("SELECT COUNT(*) as count FROM ai_query_cache");
            if (rs.next()) {
                stats.put("totalAIQueries", rs.getInt("count"));
            }

            // Cookies by category
            rs = stmt.executeQuery("SELECT category, COUNT(*) as count FROM cookies GROUP BY category");
            Map<String, Integer> byCategory = new HashMap<>();
            while (rs.next()) {
                byCategory.put(rs.getString("category"), rs.getInt("count"));
            }
            stats.put("byCategory", byCategory);

        } catch (SQLException e) {
            api.logging().logToError("Failed to get statistics: " + e.getMessage());
        }

        return stats;
    }

    /**
     * Map ResultSet row to CookieInfo object
     */
    private CookieInfo mapResultSetToCookieInfo(ResultSet rs) throws SQLException {
        CookieInfo info = new CookieInfo();

        info.setId(rs.getLong("id"));
        info.setName(rs.getString("name"));
        info.setVendor(rs.getString("vendor"));
        info.setCategory(CookieCategory.fromString(rs.getString("category")));
        info.setPurpose(rs.getString("purpose"));

        String privacyStr = rs.getString("privacy_impact");
        if (privacyStr != null) {
            info.setPrivacyImpact(PrivacyImpact.fromString(privacyStr));
        }

        info.setThirdParty(rs.getBoolean("is_third_party"));
        info.setTypicalExpiration(rs.getString("typical_expiration"));

        String domainsStr = rs.getString("common_domains");
        if (domainsStr != null && !domainsStr.isEmpty()) {
            info.setCommonDomains(Arrays.asList(domainsStr.split(",")));
        }

        info.setNotes(rs.getString("notes"));
        info.setConfidenceScore(rs.getDouble("confidence_score"));

        Timestamp created = rs.getTimestamp("created_at");
        if (created != null) {
            info.setCreatedAt(created.toInstant());
        }

        Timestamp updated = rs.getTimestamp("updated_at");
        if (updated != null) {
            info.setUpdatedAt(updated.toInstant());
        }

        info.setSource(rs.getString("source"));

        return info;
    }

    /**
     * Generate hash for query caching
     */
    private String generateQueryHash(String cookieName, String domain) {
        try {
            MessageDigest md = MessageDigest.getInstance("MD5");
            String combined = cookieName + "|" + (domain != null ? domain : "");
            byte[] hash = md.digest(combined.getBytes());

            StringBuilder hexString = new StringBuilder();
            for (byte b : hash) {
                String hex = Integer.toHexString(0xff & b);
                if (hex.length() == 1) hexString.append('0');
                hexString.append(hex);
            }
            return hexString.toString();

        } catch (Exception e) {
            return String.valueOf((cookieName + domain).hashCode());
        }
    }

    /**
     * Update an existing cookie in the database
     */
    public void updateCookieInfo(CookieInfo info) {
        String sql = "UPDATE cookies SET " +
                     "vendor = ?, " +
                     "category = ?, " +
                     "purpose = ?, " +
                     "privacy_impact = ?, " +
                     "is_third_party = ?, " +
                     "typical_expiration = ?, " +
                     "confidence_score = ? " +
                     "WHERE name = ?";

        try (PreparedStatement stmt = connection.prepareStatement(sql)) {
            stmt.setString(1, info.getVendor());
            stmt.setString(2, info.getCategory() != null ? info.getCategory().name() : null);
            stmt.setString(3, info.getPurpose());
            stmt.setString(4, info.getPrivacyImpact() != null ? info.getPrivacyImpact().name() : null);
            stmt.setBoolean(5, info.isThirdParty());
            stmt.setString(6, info.getTypicalExpiration());
            stmt.setDouble(7, info.getConfidenceScore());
            stmt.setString(8, info.getName());

            int rowsAffected = stmt.executeUpdate();

            if (rowsAffected > 0) {
                api.logging().logToOutput(String.format(
                    "Updated cookie in database: %s", info.getName()));
            } else {
                api.logging().logToError("Cookie not found for update: " + info.getName());
            }

        } catch (SQLException e) {
            api.logging().logToError("Failed to update cookie: " + e.getMessage());
            e.printStackTrace();
        }
    }

    /**
     * Delete a cookie from the database
     */
    public void deleteCookie(String cookieName) {
        String sql = "DELETE FROM cookies WHERE name = ?";

        try (PreparedStatement stmt = connection.prepareStatement(sql)) {
            stmt.setString(1, cookieName);

            int rowsAffected = stmt.executeUpdate();

            if (rowsAffected > 0) {
                api.logging().logToOutput(String.format(
                    "Deleted cookie from database: %s", cookieName));
            } else {
                api.logging().logToError("Cookie not found for deletion: " + cookieName);
            }

        } catch (SQLException e) {
            api.logging().logToError("Failed to delete cookie: " + e.getMessage());
            e.printStackTrace();
        }
    }

    /**
     * Close database connection
     */
    public void close() {
        try {
            if (connection != null && !connection.isClosed()) {
                connection.close();
                api.logging().logToOutput("Database connection closed");
            }
        } catch (SQLException e) {
            api.logging().logToError("Error closing database: " + e.getMessage());
        }
    }

    public String getDatabasePathString() {
        return databasePath;
    }
}
