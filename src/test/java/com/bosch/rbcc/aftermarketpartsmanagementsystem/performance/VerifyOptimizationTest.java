package com.bosch.rbcc.aftermarketpartsmanagementsystem.performance;

import oracle.jdbc.OracleDriver;

import java.sql.*;
import java.util.Properties;

/**
 * Verify the N+1 query fix by comparing old vs new approach
 */
public class VerifyOptimizationTest {

    private static final String URL = "jdbc:oracle:thin:@cngorarac01.apac.bosch.com:38000/aepqual_app.apac.bosch.com";
    private static final String USER = "SUPER_LINE_LEADER";
    private static final String PASSWORD = "TQAkldfgHJ$2309";

    public static void main(String[] args) throws Exception {
        OracleDriver driver = new OracleDriver();
        Properties props = new Properties();
        props.put("user", USER);
        props.put("password", PASSWORD);
        props.put("defaultRowPrefetch", "100");

        try (Connection conn = driver.connect(URL, props)) {
            System.out.println("=== Performance Optimization Verification ===\n");

            // Get test analyst
            String analyst = getFirstAnalyst(conn);
            if (analyst == null) {
                System.out.println("No data found");
                return;
            }

            System.out.println("Testing with analyst: " + analyst);
            System.out.println("Record count: " + countOrders(conn, analyst));
            System.out.println();

            // OLD APPROACH: N+1 queries
            System.out.println("--- OLD: N+1 Approach (toDTO with separate queries) ---");
            long oldStart = System.currentTimeMillis();
            int oldQueries = testOldApproach(conn, analyst);
            long oldTime = System.currentTimeMillis() - oldStart;
            System.out.println("Total queries: " + oldQueries);
            System.out.println("Total time: " + oldTime + "ms");

            // NEW APPROACH: Single JOIN query
            System.out.println("\n--- NEW: JOIN Approach (single query) ---");
            long newStart = System.currentTimeMillis();
            int newQueries = testNewApproach(conn, analyst);
            long newTime = System.currentTimeMillis() - newStart;
            System.out.println("Total queries: " + newQueries);
            System.out.println("Total time: " + newTime + "ms");

            // Summary
            System.out.println("\n=== Summary ===");
            System.out.println("Queries reduced: " + oldQueries + " → " + newQueries + " (" +
                    (100 - newQueries * 100 / oldQueries) + "% reduction)");
            System.out.println("Time improved: " + oldTime + "ms → " + newTime + "ms (" +
                    (100 - newTime * 100 / oldTime) + "% faster)");
        }
    }

    private static String getFirstAnalyst(Connection conn) throws SQLException {
        try (Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery("SELECT DISTINCT ANALYST FROM APMS_ANALYSIS_ORDER WHERE ROWNUM = 1")) {
            return rs.next() ? rs.getString("ANALYST") : null;
        }
    }

    private static int countOrders(Connection conn, String analyst) throws SQLException {
        try (PreparedStatement pstmt = conn.prepareStatement(
                "SELECT COUNT(*) FROM APMS_ANALYSIS_ORDER WHERE ANALYST = ?")) {
            pstmt.setString(1, analyst);
            try (ResultSet rs = pstmt.executeQuery()) {
                return rs.next() ? rs.getInt(1) : 0;
            }
        }
    }

    private static int testOldApproach(Connection conn, String analyst) throws SQLException {
        int queryCount = 0;

        // Query 1: Get all analysis orders
        try (PreparedStatement pstmt = conn.prepareStatement(
                "SELECT * FROM APMS_ANALYSIS_ORDER WHERE ANALYST = ?")) {
            pstmt.setString(1, analyst);
            queryCount++;

            try (ResultSet rs = pstmt.executeQuery()) {
                while (rs.next()) {
                    String orderId = rs.getString("ORDER_ID");

                    // Query 2: For EACH order, query return order (N+1 problem!)
                    try (PreparedStatement pstmt2 = conn.prepareStatement(
                            "SELECT ORDER_NUMBER FROM APMS_RETURN_ORDER WHERE ID = ?")) {
                        pstmt2.setString(1, orderId);
                        queryCount++;
                        try (ResultSet rs2 = pstmt2.executeQuery()) {
                            rs2.next();
                        }
                    }
                }
            }
        }

        return queryCount;
    }

    private static int testNewApproach(Connection conn, String analyst) throws SQLException {
        int queryCount = 0;

        // Single query with JOIN - no N+1 problem!
        try (PreparedStatement pstmt = conn.prepareStatement("""
                SELECT a.ID, a.ORDER_ID, a.ANALYST, a.STATUS, a.STATUS_CHANGED_AT,
                       a.CREATED_BY, a.CREATED_AT, a.UPDATED_BY, a.UPDATED_AT,
                       r.ORDER_NUMBER
                FROM APMS_ANALYSIS_ORDER a
                LEFT JOIN APMS_RETURN_ORDER r ON a.ORDER_ID = r.ID
                WHERE a.ANALYST = ?
                """)) {
            pstmt.setString(1, analyst);
            queryCount++;

            try (ResultSet rs = pstmt.executeQuery()) {
                while (rs.next()) {
                    // All data already fetched in single query
                    String orderNumber = rs.getString("ORDER_NUMBER");
                }
            }
        }

        return queryCount;
    }
}
