package Utils.Helper;

import io.restassured.response.Response;
import org.testng.asserts.SoftAssert;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class HelperNegativeAssertions {

    /**
     * Normalizes validation errors from API response.
     * Converts keys to lowercase to make assertions case-insensitive.
     */
    public static List<Map<String, Object>> getValidationErrors(Response response) {
        List<Map<String, Object>> rawErrors = response.jsonPath().getList("ValidationErrors");
        if (rawErrors == null) rawErrors = response.jsonPath().getList("validationErrors");
        if (rawErrors == null) return List.of();

        List<Map<String, Object>> normalized = new ArrayList<>();
        for (Map<String, Object> err : rawErrors) {
            Map<String, Object> map = new HashMap<>();
            for (Map.Entry<String, Object> entry : err.entrySet()) {
                String key = entry.getKey().toLowerCase().trim();
                String value = entry.getValue() == null ? "" : entry.getValue().toString().toLowerCase().trim();
                map.put(key, value);
            }
            normalized.add(map);
        }
        return normalized;
    }

    /**
     * Checks if the API response contains a specific expected error message.
     * - Extracts and normalizes validation errors using getValidationErrors().
     * - Converts all values to lowercase for case-insensitive comparison.
     *
     * @param response        The API response
     * @param expectedMessage The expected error message (case-insensitive)
     * @return true if the error message is found, false otherwise
     */
    public static boolean containsExpectedError(Response response, String expectedMessage) {
        List<Map<String, Object>> errors = getValidationErrors(response);

        // Convert expected message to lowercase for case-insensitive match
        String expected = expectedMessage.toLowerCase();

        return errors.stream()
                .anyMatch(err -> err.values().stream()
                        .map(val -> val == null ? "" : val.toString().toLowerCase()) // ✅ convert Object -> String safely
                        .anyMatch(val -> val.contains(expected)) // ✅ safe contains() on string
                );
    }
    /**
     * Asserts that the expected error message exists in the API response.
     * Logs all actual validation errors for better debugging.
     * Highlights the matched error if found.
     * Handles cases where error messages or property names are null, empty, or missing.
     *
     * @param response        The API response object
     * @param expectedMessage The expected error message to validate
     * @param softAssert      The SoftAssert instance for non-blocking assertions
     */
    public static void assertContainsExpectedError(Response response, String expectedMessage, SoftAssert softAssert) {
        // Check if the expected error message exists in the response
        boolean found = containsExpectedError(response, expectedMessage);

        // Retrieve validation errors from the response (could be empty or null)
        List<Map<String, Object>> validationErrors = getValidationErrors(response);

        // Print all actual validation errors for debugging
        System.out.println("️📄 Actual ValidationErrors in response:");
        for (Map<String, Object> err : validationErrors) {
            // Safely get the error message, default to "<empty>" if null or missing
            String msg = err.get("errormessage") != null ? err.get("errormessage").toString() : "<empty>";
            // Safely get the property name, default to "<unknown>" if null or missing
            String prop = err.get("propertyname") != null ? err.get("propertyname").toString() : "<unknown>";

            // Highlight the expected error message if it matches
            if (msg.equals(expectedMessage)) {
                System.out.println("  ✅ " + msg + " (Property: " + prop + ") <-- MATCHED EXPECTED");
            } else {
                System.out.println("  - " + msg + " (Property: " + prop + ")");
            }
        }

        // Print summary of expected message presence
        if (found) {
            System.out.println("✅ Expected error message FOUND: \"" + expectedMessage + "\"");
        } else {
            System.out.println("❌ Expected error message NOT found: \"" + expectedMessage + "\"");
        }

        // Soft assertion: fail if expected message not found, including actual messages in the assertion message
        softAssert.assertTrue(found,
                "Expected error message not found in response. Expected: \"" + expectedMessage +
                        "\", Actual: " + validationErrors.stream()
                        .map(err -> err.get("errormessage") != null ? err.get("errormessage").toString() : "<empty>")
                        .toList());
    }

}

