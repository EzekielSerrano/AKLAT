package autolib.db;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;

/**
 * Manages PostgreSQL database connection lifecycle for AutoLib.
 */
public class DBConnection {

    private static final String DEFAULT_HOST = "192.168.2.2";
    private static final String DEFAULT_PORT = "5432";
    private static final String DEFAULT_DB   = "autolib_db";
    private static final String DEFAULT_USER = "postgres";
    private static final String DEFAULT_PASS = "postgres";

    private static String url;
    private static String user;
    private static String password;

    static {
        try {
            Class.forName("org.postgresql.Driver");
        } catch (ClassNotFoundException e) {
            System.err.println("[AutoLib DB] PostgreSQL JDBC Driver not found on classpath.");
            System.err.println("[AutoLib DB] Please add postgresql-42.x.x.jar to your classpath/lib folder.");
        }
        resolveConnectionParams();
    }

    /**
     * Resolves the PostgreSQL connection URL and credentials.
     * Checks environment variables first (e.g. for cloud/Neon DB),
     * otherwise falls back to localhost, and finally LAN IP.
     */
    private static void resolveConnectionParams() {
        String envHost = System.getenv("AUTOLIB_DB_HOST");
        if (envHost != null && !envHost.trim().isEmpty()) {
            String port = System.getenv("AUTOLIB_DB_PORT") != null ? System.getenv("AUTOLIB_DB_PORT") : "5432";
            String db = System.getenv("AUTOLIB_DB_NAME") != null ? System.getenv("AUTOLIB_DB_NAME") : "neondb";
            user = System.getenv("AUTOLIB_DB_USER") != null ? System.getenv("AUTOLIB_DB_USER") : "neondb_owner";
            password = System.getenv("AUTOLIB_DB_PASS") != null ? System.getenv("AUTOLIB_DB_PASS") : "";
            boolean ssl = "true".equalsIgnoreCase(System.getenv("AUTOLIB_DB_SSL"));
            String sslParam = ssl ? "?ssl=true&sslmode=require" : "";
            System.out.println("[AutoLib DB] Connecting via env config to cloud host: " + envHost);
            url = String.format("jdbc:postgresql://%s:%s/%s%s", envHost.trim(), port.trim(), db.trim(), sslParam);
            return;
        }

        user = DEFAULT_USER;
        password = DEFAULT_PASS;

        // Attempt localhost first
        String localUrl = String.format("jdbc:postgresql://localhost:%s/%s", DEFAULT_PORT, DEFAULT_DB);
        try {
            java.sql.Connection c = java.sql.DriverManager.getConnection(localUrl, DEFAULT_USER, DEFAULT_PASS);
            if (c != null && !c.isClosed()) {
                c.close();
                System.out.println("[AutoLib DB] Using localhost connection.");
                url = localUrl;
                return;
            }
        } catch (Exception ignored) {
            // localhost not available, try LAN IP
        }
        System.out.println("[AutoLib DB] Localhost unavailable, using LAN IP: " + DEFAULT_HOST);
        url = String.format("jdbc:postgresql://%s:%s/%s", DEFAULT_HOST, DEFAULT_PORT, DEFAULT_DB);
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

