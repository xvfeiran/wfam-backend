package com.bosch.rbcc.aftermarketpartsmanagementsystem.performance;

import oracle.jdbc.OracleDriver;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.Properties;

/**
 * Execute migration V33 to add REPAIR_STATION_LOCATION column
 */
public class MigrationV33Executor {

    private static final String URL = "jdbc:oracle:thin:@cngorarac01.apac.bosch.com:38000/aepqual_app.apac.bosch.com";
    private static final String USER = "SUPER_LINE_LEADER";
    private static final String PASSWORD = "TQAkldfgHJ$2309";

    public static void main(String[] args) throws Exception {
        OracleDriver driver = new OracleDriver();
        Properties props = new Properties();
        props.put("user", USER);
        props.put("password", PASSWORD);

        try (Connection conn = driver.connect(URL, props)) {
            conn.setAutoCommit(false);
            System.out.println("=== Connected to Oracle ===");

            // Check current state
            System.out.println("\n--- Checking current schema ---");
            boolean hasRepairStationLocation = columnExists(conn, "APMS_PART", "REPAIR_STATION_LOCATION");
            boolean hasRepairStation = columnExists(conn, "APMS_PART", "REPAIR_STATION");
            boolean hasComplaintLocation = columnExists(conn, "APMS_PART", "COMPLAINT_LOCATION");

            System.out.println("REPAIR_STATION_LOCATION exists: " + hasRepairStationLocation);
            System.out.println("REPAIR_STATION exists: " + hasRepairStation);
            System.out.println("COMPLAINT_LOCATION exists: " + hasComplaintLocation);

            if (hasRepairStationLocation) {
                System.out.println("\nColumn REPAIR_STATION_LOCATION already exists. Checking if old columns need cleanup...");

                // Check if old columns still exist
                if (hasRepairStation || hasComplaintLocation) {
                    System.out.println("Old columns still exist. Dropping them...");
                    if (hasRepairStation) {
                        executeUpdate(conn, "ALTER TABLE APMS_PART DROP COLUMN REPAIR_STATION");
                        System.out.println("Dropped REPAIR_STATION");
                    }
                    if (hasComplaintLocation) {
                        executeUpdate(conn, "ALTER TABLE APMS_PART DROP COLUMN COMPLAINT_LOCATION");
                        System.out.println("Dropped COMPLAINT_LOCATION");
                    }
                    conn.commit();
                    System.out.println("Cleanup completed successfully!");
                } else {
                    System.out.println("Migration already completed. Nothing to do.");
                }
                return;
            }

            // Step 1: Add new column
            if (!hasRepairStationLocation) {
                System.out.println("\n--- Step 1: Adding REPAIR_STATION_LOCATION column ---");
                executeUpdate(conn, "ALTER TABLE APMS_PART ADD REPAIR_STATION_LOCATION VARCHAR2(255 CHAR)");
                System.out.println("Added REPAIR_STATION_LOCATION column");
            }

            // Step 2: Migrate data (only if old columns exist)
            if (hasRepairStation || hasComplaintLocation) {
                System.out.println("\n--- Step 2: Migrating data from old columns ---");

                String updateSql = """
                    UPDATE APMS_PART
                    SET REPAIR_STATION_LOCATION =
                        CASE
                            WHEN REPAIR_STATION IS NOT NULL AND COMPLAINT_LOCATION IS NOT NULL
                            THEN REPAIR_STATION || '-' || COMPLAINT_LOCATION
                            WHEN REPAIR_STATION IS NOT NULL
                            THEN REPAIR_STATION
                            WHEN COMPLAINT_LOCATION IS NOT NULL
                            THEN COMPLAINT_LOCATION
                            ELSE NULL
                        END
                    """;

                int rowsUpdated = executeUpdate(conn, updateSql);
                System.out.println("Migrated " + rowsUpdated + " rows");

                // Show sample of migrated data
                System.out.println("\nSample of migrated data:");
                try (Statement stmt = conn.createStatement();
                     ResultSet rs = stmt.executeQuery("""
                        SELECT PART_NUMBER, REPAIR_STATION_LOCATION
                        FROM APMS_PART
                        WHERE REPAIR_STATION_LOCATION IS NOT NULL
                        AND ROWNUM <= 5
                        """)) {
                    while (rs.next()) {
                        System.out.println("  " + rs.getString("PART_NUMBER") + " -> " + rs.getString("REPAIR_STATION_LOCATION"));
                    }
                }
            }

            // Step 3: Drop old columns
            System.out.println("\n--- Step 3: Dropping old columns ---");
            if (hasRepairStation) {
                executeUpdate(conn, "ALTER TABLE APMS_PART DROP COLUMN REPAIR_STATION");
                System.out.println("Dropped REPAIR_STATION column");
            }
            if (hasComplaintLocation) {
                executeUpdate(conn, "ALTER TABLE APMS_PART DROP COLUMN COMPLAINT_LOCATION");
                System.out.println("Dropped COMPLAINT_LOCATION column");
            }

            conn.commit();
            System.out.println("\n=== Migration V33 completed successfully! ===");

            // Verify
            System.out.println("\n--- Verification ---");
            boolean after = columnExists(conn, "APMS_PART", "REPAIR_STATION_LOCATION");
            System.out.println("REPAIR_STATION_LOCATION exists: " + after);
        }
    }

    private static boolean columnExists(Connection conn, String tableName, String columnName) throws Exception {
        String sql = "SELECT COUNT(*) FROM USER_TAB_COLUMNS WHERE TABLE_NAME = ? AND COLUMN_NAME = ?";
        try (PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setString(1, tableName);
            pstmt.setString(2, columnName);
            try (ResultSet rs = pstmt.executeQuery()) {
                return rs.next() && rs.getInt(1) > 0;
            }
        }
    }

    private static int executeUpdate(Connection conn, String sql) throws Exception {
        try (Statement stmt = conn.createStatement()) {
            return stmt.executeUpdate(sql);
        }
    }
}
