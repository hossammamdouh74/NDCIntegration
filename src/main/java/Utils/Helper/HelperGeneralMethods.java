package Utils.Helper;

import Utils.ReportManager.ReportManager;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import io.restassured.path.json.JsonPath;
import org.testng.SkipException;
import org.testng.asserts.SoftAssert;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.*;
import static Utils.Helper.HelperGetResponse.getAmountOrZero;

public class HelperGeneralMethods {

    /**
     * Normalizes a price details map by ensuring that
     * serviceChargeAmount is set to null if it is either missing or zero.
     * This helps in avoiding false mismatches when comparing price details.
     * @param rawPriceDetails Original price details map from API
     * @return New map with normalized serviceChargeAmount
     */
    public static Map<String, Object> normalizePriceDetails(Map<String, Object> rawPriceDetails) {
        Map<String, Object> normalized = new HashMap<>(rawPriceDetails);

        Object sc = rawPriceDetails.get("serviceChargeAmount");

        // Treat both null and 0.00 as null for comparison purposes
        if (sc == null || getAmountOrZero(sc).compareTo(BigDecimal.ZERO) == 0) {
            normalized.put("serviceChargeAmount", null);
        }

        return normalized;
    }

    /**
     * Rounds a BigDecimal value to 2 decimal places using HALF_UP rounding mode.
     * Commonly used for currency values to avoid floating-point mismatches.
     * @param value BigDecimal value to round
     * @return Rounded BigDecimal value
     */
    public static BigDecimal roundTo2Decimals(BigDecimal value) {
        return value.setScale(2, RoundingMode.HALF_UP);
    }

    /**
     * Extracts a currency value from a JSON path result.
     * If the value is a list, the first element is returned.
     * If the value is a single value, it is returned as string.
     * @param jsonPath JSONPath object containing parsed response
     * @param path     Path to the currency field
     * @return Currency code as string, or null if not found
     */
    public static String extractCurrency(JsonPath jsonPath, String path) {
        Object value = jsonPath.get(path);
        if (value instanceof List<?> list && !list.isEmpty()) {
            return list.get(0).toString();
        } else if (value != null) {
            return value.toString();
        }
        return null;
    }

    /**
     * Compares price details maps (actual vs expected) and asserts equality for each field.
     * Only compares fields where the value is a nested map containing amount data.
     * @param actualPriceDetails   Map containing actual price details from API
     * @param expectedPriceDetails Map containing expected price details
     * @param softAssert           SoftAssert object for non-blocking assertions
     */
    public static void comparePriceDetails(Map<String, Object> actualPriceDetails, Map<String, Object> expectedPriceDetails, SoftAssert softAssert) {
        for (Map.Entry<String, Object> entry : actualPriceDetails.entrySet()) {
            String key = entry.getKey();
            Object value = entry.getValue();

            if (value instanceof Map) {
                BigDecimal actual = getAmountOrZero(value);
                BigDecimal expected = getAmountOrZero(expectedPriceDetails.get(key));

                softAssert.assertEquals(
                        actual,
                        expected,
                        String.format("❌ Mismatch in priceDetails.%s: expected [%.2f] but found [%.2f]", key, expected, actual)
                );
            }
        }
    }

    /**
     * Compares expected vs actual values with a tolerance for rounding issues.
     * - Exact match → pass silently
     * - Difference ≤ tolerance (0.01) → log as rounding info (not fail)
     * - Difference > tolerance → fail the test
     */
    public static void assertWithRoundingTolerance(BigDecimal actual, BigDecimal expected,
                                                    double tolerance, String fieldName,
                                                    int offerOrder, String TcId ,SoftAssert softAssert ) {
        // Calculate absolute difference between actual and expected
        BigDecimal diff = actual.subtract(expected).abs();

        // Case 1: Perfect match → nothing to report
        if (actual.compareTo(expected) == 0) {
            return;
        }

        // Case 2: Within tolerance → log info, do not fail
        if (diff.compareTo(BigDecimal.valueOf(tolerance)) <= 0) {
            System.out.printf(
                    "ℹ️"+TcId+ "[Offer %d] Minor rounding difference in %s: Expected=%.2f, Actual=%.2f (Diff=%.4f)%n",
                    offerOrder, fieldName, expected.doubleValue(), actual.doubleValue(), diff.doubleValue()
            );
        }
        // Case 3: Beyond tolerance → fail as real mismatch
        else {
            softAssert.fail(
                    String.format("❌ " +TcId+" [Offer %d] Mismatch in %s: Expected=%.2f, Actual=%.2f (Diff=%.4f)",
                            offerOrder, fieldName, expected.doubleValue(), actual.doubleValue(), diff.doubleValue()
                    )
            );
        }
    }

    /**
     * Skips a test if the booking flow from the selected offer
     * does not match any of the allowed booking flows.
     * @param selectedOfferFromSearch Selected offer map from search API
     * @param allowedFlows            Allowed booking flow types (e.g., "book", "hold")
     * @param testCaseId              Test case identifier
     * @param stepName                Name of the test step being executed
     */
    public static void skipIfBookingFlowNotIn(Map<String, Object> selectedOfferFromSearch, Set<String> allowedFlows, String testCaseId, String stepName) {
        String actualFlow = ((String) selectedOfferFromSearch.getOrDefault("bookingFlow", "book")).toLowerCase();

        // If the current booking flow is not in the allowed set, skip the test
        if (!allowedFlows.contains(actualFlow)) {
            String allowed = String.join(", ", allowedFlows);
            String message = String.format(
                    "⏭ Skipping [%s]: bookingFlow is '%s' but expected one of [%s] for testCaseId: %s",
                    stepName, actualFlow, allowed, testCaseId
            );

            System.out.println(message);
            ReportManager.getTest().info(message);

            // Gracefully skip this test in TestNG
            throw new SkipException(message);
        }
    }

    /**
     * Compares two BigDecimal values with 2 decimal precision and fails softly if they differ.
     * @param actual    Actual value from API
     * @param expected  Expected value
     * @param message   Assertion message
     * @param softAssert SoftAssert object for non-blocking assertions
     */
    public static void assertEqualDoubles(BigDecimal actual, BigDecimal expected, String message, SoftAssert softAssert) {
        if (actual.setScale(2, RoundingMode.HALF_UP).compareTo(expected.setScale(2, RoundingMode.HALF_UP)) != 0) {
            softAssert.fail(String.format("%s → Expected: %.2f, Actual: %.2f", message, expected, actual));
        }
    }

    /**
     * Compares two objects by converting them to JSON and comparing the JSON strings.
     * Useful for comparing nested objects with consistent ordering of map keys.
     * @param actual     Actual object
     * @param expected   Expected object
     * @param softAssert SoftAssert object for non-blocking assertions
     */
    public static void assertJsonEquals(Object actual, Object expected, SoftAssert softAssert) {
        ObjectMapper mapper = new ObjectMapper();
        mapper.configure(SerializationFeature.ORDER_MAP_ENTRIES_BY_KEYS, true); // Ensure consistent ordering

        try {
            String actualJson = mapper.writeValueAsString(actual);
            String expectedJson = mapper.writeValueAsString(expected);

            System.out.println("Actual JSON: " + actualJson);
            System.out.println("Expected JSON: " + expectedJson);

            softAssert.assertEquals(actualJson, expectedJson, "❌ Baggage details mismatch");
        } catch (JsonProcessingException e) {
            throw new RuntimeException("Failed to compare objects as JSON", e);
        }
    }

    /**
     * Removes 'bundleReferenceIds' from all journey maps inside the given journeys map.
     * Useful when bundleReferenceIds are dynamic and should be excluded from comparisons.
     * @param journeys Map of journeyId → journey details
     * @return New map with bundleReferenceIds removed
     */
    public static Map<String, Object> removeBundleReferenceIds(Map<String, Object> journeys) {
        Map<String, Object> cleaned = new HashMap<>();
        for (Map.Entry<String, Object> entry : journeys.entrySet()) {
            Map<String, Object> journey = new HashMap<>((Map<String, Object>) entry.getValue());
            journey.remove("bundleReferenceIds");
            cleaned.put(entry.getKey(), journey);
        }
        return cleaned;
    }
}