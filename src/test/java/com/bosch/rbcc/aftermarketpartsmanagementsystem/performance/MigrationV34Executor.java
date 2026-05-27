package com.bosch.rbcc.aftermarketpartsmanagementsystem.performance;

import oracle.jdbc.OracleDriver;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.Properties;

/**
 * Execute migration V34 to add ANALYST index
 */
public class MigrationV34Executor {

    private static final String URL = "jdbc:oracle:thin:@cngorarac01.apac.bosch.com:38000/aepqual_app.apac.bosch.com";
    private static final String USER = "SUPER_LINE_LEADER";
    private static final String PASSWORD = "TQAkldfgHJ$2309";

    public static void main(String[] args) throws Exception {
        OracleDriver driver = new OracleDriver();
        Properties props = new Properties();
        props.put("user", USER);
        props.put("password", PASSWORD);

        try (Connection conn = driver.connect(URL, props)) {
            System.out.println("=== Connected to Oracle ===");

            // Check current indexes
            System.out.println("\n--- Before Migration ---");
            listIndexes(conn);

            // Check if index already exists
            if (indexExists(conn, "IDX_APMS_AO_ANALYST")) {
                System.out.println("\nIndex IDX_APMS_AO_ANALYST already exists. Nothing to do.");
                return;
            }

            // Create index
            System.out.println("\n--- Creating Index IDX_APMS_AO_ANALYST ---");
            try (Statement stmt = conn.createStatement()) {
                stmt.execute("CREATE INDEX IDX_APMS_AO_ANALYST ON APMS_ANALYSIS_ORDER(ANALYST)");
                System.out.println("Created index IDX_APMS_AO_ANALYST");
            }

            // Verify
            System.out.println("\n--- After Migration ---");
            listIndexes(conn);

            // Test query performance
            System.out.println("\n--- Performance Test ---");
            testQueryPerformance(conn);

            System.out.println("\n=== Migration V34 completed successfully! ===");
        }
    }

    private static void listIndexes(Connection conn) throws Exception {
        String sql = """
            SELECT INDEX_NAME, COLUMN_NAME, COLUMN_POSITION
            FROM USER_IND_COLUMNS
            WHERE TABLE_NAME = 'APMS_ANALYSIS_ORDER'
            ORDER BY INDEX_NAME, COLUMN_POSITION
            """;
        try (Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery(sql)) {
            System.out.println("Indexes on APMS_ANALYSIS_ORDER:");
            while (rs.next()) {
                System.out.println("  - " + rs.getString("INDEX_NAME") +
                        " on " + rs.getString("COLUMN_NAME") +
                        " (pos: " + rs.getInt("COLUMN_POSITION") + ")");
            }
        }
    }

    private static boolean indexExists(Connection conn, String indexName) throws Exception {
        String sql = "SELECT COUNT(*) FROM USER_INDEXES WHERE INDEX_NAME = ?";
        try (PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setString(1, indexName);
            try (ResultSet rs = pstmt.executeQuery()) {
                return rs.next() && rs.getInt(1) > 0;
            }
        }
    }

    private static void testQueryPerformance(Connection conn) throws Exception {
        // Get a test analyst
        String analyst = null;
        try (Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery("SELECT DISTINCT ANALYST FROM APMS_ANALYSIS_ORDER WHERE ROWNUM = 1")) {
            if (rs.next()) {
                analyst = rs.getString("ANALYST");
            }
        }

        if (analyst == null) {
            System.out.println("No data to test performance");
            return;
        }

        // Warm up
        for (int i = 0; i < 3; i++) {
            try (PreparedStatement pstmt = conn.prepareStatement(
                    "SELECT * FROM APMS_ANALYSIS_ORDER WHERE ANALYST = ?")) {
                pstmt.setString(1, analyst);
                try (ResultSet rs = pstmt.executeQuery()) {
                    while (rs.next()) {}
                }
            }
        }

        // Measure
        long total = 0;
        int runs = 5;
        for (int i = 0; i < runs; i++) {
            long start = System.currentTimeMillis();
            try (PreparedStatement pstmt = conn.prepareStatement(
                    "SELECT * FROM APMS_ANALYSIS_ORDER WHERE ANALYST = ?")) {
                pstmt.setString(1, analyst);
                try (ResultSet rs = pstmt.executeQuery()) {
                    while (rs.next()) {
                        // consume rows
                    }
                }
            }
            total += System.currentTimeMillis() - start;
        }

        System.out.println("Query: SELECT * FROM APMS_ANALYSIS_ORDER WHERE ANALYST = ?");
        System.out.println("Average time over " + runs + " runs: " + (total / runs) + "ms");
    }
}
