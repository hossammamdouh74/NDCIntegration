package Utils.Helper;

import Utils.ReportManager.ReportManager;
import io.restassured.path.json.JsonPath;
import io.restassured.response.Response;
import org.testng.asserts.SoftAssert;
import java.util.List;
import java.util.Map;
import static Utils.Helper.HelperGeneralMethods.extractCurrency;

public class HelperCurrencyValidator {

    /**
     * Validates that all currency values in the given JSON response
     * match the "AgencyCurrency" value from the request headers.
     *
     * @param response    The API response to validate
     * @param headers     The request headers containing "AgencyCurrency"
     * @param rootPath    The JSON path where validation starts
     * @param softAssert  SoftAssert instance to collect validation errors without stopping the test
     */
    public static void validateCurrencies(Response response, Map<String, String> headers, String rootPath, SoftAssert softAssert) {
        // Expected currency taken from headers (trimmed to remove spaces)
        String expectedCurrency = headers.getOrDefault("AgencyCurrency", "").trim();

        System.out.println("\n💵 === TC: Validating currencies against headers ===");
        System.out.println("\t💱 Expected Currency from headers: " + expectedCurrency);

        // Fail immediately if the AgencyCurrency header is missing
        if (expectedCurrency.isEmpty()) {
            softAssert.fail("❌ Missing AgencyCurrency in headers.");
            return;
        }

        // Parse JSON response
        JsonPath jsonPath = JsonPath.from(response.asString());
        List<Map<String, Object>> entries;
        try {
            // Try to read as a list (multiple entries)
            entries = jsonPath.getList(rootPath);
        } catch (Exception e) {
            // If fails, treat it as a single object
            System.out.println("⚠️ Could not fetch entries from path: " + rootPath + " → treating as single object");
            entries = List.of(jsonPath.getMap(rootPath));
        }

        // Fail if no entries are found at the given path
        if (entries == null || entries.isEmpty()) {
            softAssert.fail("❌ No entries found under path: " + rootPath);
            return;
        }

        // Loop through each entry and validate currency fields
        System.out.println("\t📦 Validating " + entries.size() + " entries under path: " + rootPath);
        for (int i = 0; i < entries.size(); i++) {
            // Determine the JSON path for each entry
            String basePath = (entries.size() == 1 && !jsonPath.get(rootPath).toString().startsWith("["))
                    ? rootPath
                    : rootPath + "[" + i + "]";
            System.out.println("🔍 Validating entry #" + (i + 1) + " → " + basePath);

            // Validate currency in fare breakdown, price details, and taxes/fees
            validateFareBreakdownCurrencies(jsonPath, basePath, expectedCurrency, softAssert);
            validatePriceDetailsCurrencies(jsonPath, basePath, expectedCurrency, softAssert);
            validateTaxesAndFeesCurrencies(jsonPath, basePath, expectedCurrency, softAssert);
        }

        // Log completion
        System.out.println("✅ Completed currency validation for entries under: " + rootPath);
        ReportManager.getTest().info("✅ Currency validation completed against expected: " + expectedCurrency);
    }

    /**
     * Validates that all currency fields inside 'passengerFareBreakdown' match the expected currency.
     */
    private static void validateFareBreakdownCurrencies(JsonPath jsonPath, String basePath, String expected, SoftAssert softAssert) {
        List<Map<String, Object>> fareBreakdowns = jsonPath.getList(basePath + ".passengerFareBreakdown");
        if (fareBreakdowns == null) return;

        for (int j = 0; j < fareBreakdowns.size(); j++) {
            String pbPath = basePath + ".passengerFareBreakdown[" + j + "]";
            String[] currencyPaths = {
                    pbPath + ".paxBaseAmount.currency",
                    pbPath + ".paxTotalTaxAmount.currency"
            };

            // Validate currency for each amount in passenger fare breakdown
            for (String path : currencyPaths) {
                assertCurrencyMatches(jsonPath, path, expected, softAssert);
            }
        }
    }

    /**
     * Validates currency fields inside the 'priceDetails' section.
     */
    private static void validatePriceDetailsCurrencies(JsonPath jsonPath, String basePath, String expected, SoftAssert softAssert) {
        String[] pricePaths = {
                basePath + ".priceDetails.totalAmount.currency",
                basePath + ".priceDetails.totalBaseAmount.currency",
                basePath + ".priceDetails.totalTaxAmount.currency"
        };

        // Check each price field for currency match
        for (String path : pricePaths) {
            assertCurrencyMatches(jsonPath, path, expected, softAssert);
        }
    }

    /**
     * Validates the currency for each tax/fee amount, skipping special cases like "CancelFee" or "ChangeFee".
     */
    private static void validateTaxesAndFeesCurrencies(JsonPath jsonPath, String basePath, String expected, SoftAssert softAssert) {
        List<Map<String, Object>> taxes = jsonPath.getList(basePath + ".passengerFareBreakdown[0].taxesAndFees");
        if (taxes == null) return;

        for (int t = 0; t < taxes.size(); t++) {
            String feeCode = jsonPath.getString(basePath + ".passengerFareBreakdown[0].taxesAndFees[" + t + "].code");

            // Skip validation for specific fee codes
            if ("CancelFee".equalsIgnoreCase(feeCode) || "ChangeFee".equalsIgnoreCase(feeCode)) {
                System.out.printf("\t⏭️ Skipping fee: %s%n", feeCode);
                continue;
            }

            // Validate the currency for each tax/fee amount
            String path = basePath + ".passengerFareBreakdown[0].taxesAndFees[" + t + "].amount.currency";
            assertCurrencyMatches(jsonPath, path, expected, softAssert);
        }
    }

    /**
     * Helper method to compare actual currency value at a given JSON path with the expected currency.
     */
    private static void assertCurrencyMatches(JsonPath jsonPath, String path, String expected, SoftAssert softAssert) {
        String actual = extractCurrency(jsonPath, path);
        System.out.println("\t🔎 Checking path: " + path);

        // If currency is missing, log a warning and skip
        if (actual == null || actual.isBlank()) {
            System.out.printf("\t⚠️ Currency missing at %s → Skipped%n", path);
            return;
        }

        // Log and assert currency match
        System.out.printf("\t✅ %s → %s%n", path, actual);
        softAssert.assertEquals(actual, expected,
                "❌ Currency mismatch at " + path + ": expected " + expected + ", found " + actual);
    }
}