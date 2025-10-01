package Utils.ReportManager;

import com.aventstack.extentreports.ExtentReports;
import com.aventstack.extentreports.ExtentTest;
import com.aventstack.extentreports.reporter.ExtentSparkReporter;
import java.util.List;
import java.util.Map;

/**
 * Utility class for managing ExtentReports lifecycle and logging.
 * Responsibilities:
 * - Create and manage a singleton instance of ExtentReports.
 * - Handle creation of test cases (ExtentTest).
 * - Provide logging utilities for passenger breakdown information.
 */
public class ReportManager {

    // Singleton instance of ExtentReports
    private static ExtentReports extent;

    // Thread-safe storage for ExtentTest instances (for parallel execution)
    private static final ThreadLocal<ExtentTest> extentTest = new ThreadLocal<>();

    /**
     * Retrieves the singleton ExtentReports instance.
     * Creates and configures it if not already initialized.
     *
     * @return ExtentReports instance
     */
    public static ExtentReports getInstance() {
        if (extent == null) {
            ExtentSparkReporter reporter = new ExtentSparkReporter("test-output/ExtentReport.html");
            extent = new ExtentReports();
            extent.attachReporter(reporter);
        }
        return extent;
    }

    /**
     * Creates a new test entry in the report.
     *
     * @param name        Test name
     * @param description Test description
     * @return ExtentTest instance for logging
     */
    public static ExtentTest createTest(String name, String description) {
        // Stores the currently active test in this thread
        ExtentTest test = getInstance().createTest(name, description);
        extentTest.set(test);
        return test;
    }

    /**
     * Finalizes and writes all logged report data to the output file.
     */
    public static void flush() {
        getInstance().flush();
    }

    /**
     * Retrieves the current test instance for this thread.
     *
     * @return Current thread's ExtentTest instance
     */
    public static ExtentTest getTest() {
        return extentTest.get();
    }

    /**
     * Logs a breakdown of passenger counts (ADT, CHD, INF) to the Extent report and console.
     *
     * @param searchPayload Search payload containing passenger details
     * @param agencyName    Name of the agency
     * @param testCaseId    Unique test case identifier
     * @param description   Short description of the test
     */
    public static void logPassengerBreakdown(Map<String, Object> searchPayload, String agencyName,
                                             String testCaseId, String description) {
        if (searchPayload == null || searchPayload.get("passengers") == null) {
            ReportManager.getTest().warning("⚠️ searchPayload or passengers list is missing");
            System.out.println("⚠️ searchPayload or passengers list is missing for: " + testCaseId);
            return;
        }

        // Extract passenger list from payload
        List<Map<String, Object>> passengersList = (List<Map<String, Object>>) searchPayload.get("passengers");

        int adtCount = 0, chdCount = 0, infCount = 0;

        // Count passengers by type
        for (Map<String, Object> pax : passengersList) {
            String type = (String) pax.get("passengerTypeCode");
            int count = (int) pax.get("count");

            switch (type) {
                case "ADT" -> adtCount = count;
                case "CHD" -> chdCount = count;
                case "INF" -> infCount = count;
            }
        }

        // Format log output
        String logLine = String.format(
                "Agency : %s  🧪 Test Case: %s - %s | Passengers → ADT: %d | CHD: %d | INF: %d",
                agencyName, testCaseId, description, adtCount, chdCount, infCount
        );

        // Log to report and console
        ExtentTest logger = ReportManager.getTest();
        logger.info(logLine);
        System.out.println(logLine);
    }
}