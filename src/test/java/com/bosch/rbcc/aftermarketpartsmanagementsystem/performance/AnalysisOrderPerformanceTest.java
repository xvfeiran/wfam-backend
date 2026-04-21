package com.bosch.rbcc.aftermarketpartsmanagementsystem.performance;

import oracle.jdbc.OracleDriver;

import java.sql.*;
import java.util.Properties;

/**
 * JDBC performance test for analysis order queries
 * Run with: cd backend && mvn test exec:java -Dexec.mainClass="com.bosch.rbcc.aftermarketpartsmanagementsystem.performance.AnalysisOrderPerformanceTest"
 */
public class AnalysisOrderPerformanceTest {

    private static final String URL = "jdbc:oracle:thin:@cngorarac01.apac.bosch.com:38000/aepqual_app.apac.bosch.com";
    private static final String USER = "SUPER_LINE_LEADER";
    private static final String PASSWORD = "TQAkldfgHJ$2309";

    public static void main(String[] args) throws Exception {
        OracleDriver driver = new OracleDriver();
        Properties props = new Properties();
        props.put("user", USER);
        props.put("password", PASSWORD);
        props.put("defaultRowPrefetch", "100"); // Reduce round trips

        try (Connection conn = driver.connect(URL, props)) {
            System.out.println("=== Connected to Oracle ===");

            // Test 1: Count records
            System.out.println("\n--- 1. Record Counts ---");
            long aoCount = queryCount(conn, "SELECT COUNT(*) FROM APMS_ANALYSIS_ORDER");
            long partCount = queryCount(conn, "SELECT COUNT(*) FROM APMS_PART");
            long roCount = queryCount(conn, "SELECT COUNT(*) FROM APMS_RETURN_ORDER");
            System.out.println("APMS_ANALYSIS_ORDER: " + aoCount);
            System.out.println("APMS_PART: " + partCount);
            System.out.println("APMS_RETURN_ORDER: " + roCount);

            // Test 2: Check existing indexes
            System.out.println("\n--- 2. Indexes on APMS_ANALYSIS_ORDER ---");
            listIndexes(conn, "APMS_ANALYSIS_ORDER");

            // Test 3: Query performance - findByAnalyst
            System.out.println("\n--- 3. findByAnalyst Performance ---");
            String analyst = getFirstAnalyst(conn);
            if (analyst != null) {
                testQuery(conn, "SELECT * FROM APMS_ANALYSIS_ORDER WHERE ANALYST = ?", analyst, "findByAnalyst");

                // Test with execution plan
                showExecutionPlan(conn, "SELECT * FROM APMS_ANALYSIS_ORDER WHERE ANALYST = '" + analyst + "'");
            }

            // Test 4: Query performance - findAll
            System.out.println("\n--- 4. findAll Performance ---");
            testQuery(conn, "SELECT * FROM APMS_ANALYSIS_ORDER", null, "findAll");

            // Test 5: N+1 problem simulation
            System.out.println("\n--- 5. N+1 Query Simulation (toDTO) ---");
            simulateNPlusOne(conn);

            // Test 6: Check if ANALYST index would help
            System.out.println("\n--- 6. Index Impact Analysis ---");
            if (analyst != null) {
                System.out.println("Recommended: CREATE INDEX IDX_APMS_AO_ANALYST ON APMS_ANALYSIS_ORDER(ANALYST)");
                System.out.println("This will speed up findByAnalyst queries");
            }
        }
    }

    private static long queryCount(Connection conn, String sql) throws SQLException {
        try (Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery(sql)) {
            return rs.next() ? rs.getLong(1) : 0;
        }
    }

    private static void listIndexes(Connection conn, String tableName) throws SQLException {
        String sql = """
            SELECT INDEX_NAME, COLUMN_NAME, COLUMN_POSITION
            FROM USER_IND_COLUMNS
            WHERE TABLE_NAME = ?
            ORDER BY INDEX_NAME, COLUMN_POSITION
            """;
        try (PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setString(1, tableName);
            try (ResultSet rs = pstmt.executeQuery()) {
                boolean hasIndex = false;
                while (rs.next()) {
                    hasIndex = true;
                    System.out.println("  Index: " + rs.getString("INDEX_NAME") +
                            " on " + rs.getString("COLUMN_NAME") +
                            " (pos: " + rs.getInt("COLUMN_POSITION") + ")");
                }
                if (!hasIndex) {
                    System.out.println("  No indexes found (except PK)");
                }
            }
        }
    }

    private static String getFirstAnalyst(Connection conn) throws SQLException {
        try (Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery("SELECT DISTINCT ANALYST FROM APMS_ANALYSIS_ORDER WHERE ROWNUM = 1")) {
            return rs.next() ? rs.getString("ANALYST") : null;
        }
    }

    private static void testQuery(Connection conn, String sql, String param, String label) throws SQLException {
        long start = System.currentTimeMillis();

        try (PreparedStatement pstmt = conn.prepareStatement(sql)) {
            if (param != null) {
                pstmt.setString(1, param);
            }

            int count = 0;
            try (ResultSet rs = pstmt.executeQuery()) {
                while (rs.next()) {
                    count++;
                }
            }

            long elapsed = System.currentTimeMillis() - start;
            System.out.println("  " + label + ": " + count + " rows in " + elapsed + "ms");
        }
    }

    private static void showExecutionPlan(Connection conn, String sql) throws SQLException {
        String planSql = "SELECT * FROM TABLE(DBMS_XPLAN.DISPLAY_CURSOR(NULL, NULL, 'ALLSTATS LAST'))";
        // First execute the query with gather_plan_statistics
        try (Statement stmt = conn.createStatement()) {
            // Enable statistics collection
            stmt.execute("ALTER SESSION SET STATISTICS_LEVEL = ALL");

            // Execute query
            stmt.execute(sql);

            // Get plan
            try (ResultSet rs = stmt.executeQuery(
                    "SELECT PLAN_TABLE_OUTPUT FROM TABLE(DBMS_XPLAN.DISPLAY_CURSOR(NULL, NULL, 'BASIC PREDICATE'))")) {
                System.out.println("  Execution Plan:");
                while (rs.next()) {
                    String line = rs.getString(1);
                    if (line != null && !line.isEmpty()) {
                        System.out.println("    " + line);
                    }
                }
            }
        }
    }

    private static void simulateNPlusOne(Connection conn) throws SQLException {
        // Simulate the N+1 problem in toDTO method
        String analyst = getFirstAnalyst(conn);
        if (analyst == null) return;

        long start = System.currentTimeMillis();

        try (PreparedStatement pstmt = conn.prepareStatement("SELECT * FROM APMS_ANALYSIS_ORDER WHERE ANALYST = ?")) {
            pstmt.setString(1, analyst);

            int orderCount = 0;
            int extraQueries = 0;

            try (ResultSet rs = pstmt.executeQuery()) {
                while (rs.next()) {
                    orderCount++;
                    String orderId = rs.getString("ORDER_ID");

                    // Simulate the N+1 query for order number
                    try (PreparedStatement pstmt2 = conn.prepareStatement("SELECT ORDER_NUMBER FROM APMS_RETURN_ORDER WHERE ID = ?")) {
                        pstmt2.setString(1, orderId);
                        try (ResultSet rs2 = pstmt2.executeQuery()) {
                            if (rs2.next()) {
                                extraQueries++;
                            }
                        }
                    }
                }
            }

            long elapsed = System.currentTimeMillis() - start;
            System.out.println("  Fetched " + orderCount + " orders with " + extraQueries + " extra queries in " + elapsed + "ms");
            System.out.println("  This is the N+1 problem! Should use JOIN.");
        }
    }
}
