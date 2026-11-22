package autolib.db;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;

/**
 * Manages PostgreSQL database connection lifecycle for AutoLib.
 */
public class DBConnection {

    private static final String DEFAULT_HOST = "192.168.254.125";
    private static final String DEFAULT_PORT = "5432";
    private static final String DEFAULT_DB   = "autolib_db";
    private static final String DEFAULT_USER = "postgres";
    private static final String DEFAULT_PASS = "postgres";

    // Try localhost first (when running on the server PC itself),
    // then fall back to the LAN IP (for client PCs on the network).
    private static String url = resolveUrl();
    private static String user = DEFAULT_USER;
    private static String password = DEFAULT_PASS;

    /**
     * Tries localhost first; if that fails, uses the LAN IP.
     * This makes the app work both on the server PC and on remote PCs.
     */
    private static String resolveUrl() {
        // Attempt localhost first
        String localUrl = String.format("jdbc:postgresql://localhost:%s/%s", DEFAULT_PORT, DEFAULT_DB);
        try {
            Class.forName("org.postgresql.Driver");
            java.sql.Connection c = java.sql.DriverManager.getConnection(localUrl, DEFAULT_USER, DEFAULT_PASS);
            if (c != null && !c.isClosed()) {
                c.close();
                System.out.println("[AutoLib DB] Using localhost connection.");
                return localUrl;
            }
        } catch (Exception ignored) {
            // localhost not available, try LAN IP
        }
        System.out.println("[AutoLib DB] Localhost unavailable, using LAN IP: " + DEFAULT_HOST);
        return String.format("jdbc:postgresql://%s:%s/%s", DEFAULT_HOST, DEFAULT_PORT, DEFAULT_DB);
    }

    static {
        try {
            Class.forName("org.postgresql.Driver");
        } catch (ClassNotFoundException e) {
            System.err.println("[AutoLib DB] PostgreSQL JDBC Driver not found on classpath.");
            System.err.println("[AutoLib DB] Please add postgresql-42.x.x.jar to your classpath/lib folder.");
        }
    }

    private DBConnection() {}

    /**
     * Configure connection parameters dynamically if needed.
     */
    public static void configure(String host, String port, String dbName, String dbUser, String dbPass) {
        url = String.format("jdbc:postgresql://%s:%s/%s", host, port, dbName);
        user = dbUser;
        password = dbPass;
    }

    /**
     * Retrieves a new active Connection to the PostgreSQL database.
     */
    public static Connection getConnection() throws SQLException {
        return DriverManager.getConnection(url, user, password);
    }

    /**
     * Quick test to verify connectivity.
     */
    public static boolean testConnection() {
        try (Connection conn = getConnection()) {
            return conn != null && !conn.isClosed();
        } catch (SQLException e) {
            System.err.println("[AutoLib DB] Connection test failed: " + e.getMessage());
            return false;
        }
    }

    public static void main(String[] args) {
        System.out.println("[AutoLib DB] Testing connection to: " + url + " as " + user);
        if (testConnection()) {
            System.out.println("[AutoLib DB] SUCCESS: Connected to PostgreSQL database successfully!");
        } else {
            System.out.println("[AutoLib DB] FAILED: Could not connect to PostgreSQL database.");
        }
    }
}

