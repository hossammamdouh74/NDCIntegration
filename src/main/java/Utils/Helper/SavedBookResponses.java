package Utils.Helper;

import java.util.concurrent.ConcurrentHashMap;
import java.util.Map;

/**
 * Utility class for storing and retrieving booking API responses during test execution.
 * <p>
 * This class acts as a thread-safe in-memory storage for "Book" API responses,
 * keyed by a unique test case ID. It allows different parts of the test suite
 * to share the same booking response without having to repeat the booking call.
 * <p>
 * Implementation Details:
 * - Uses {@link ConcurrentHashMap} to ensure safe concurrent access in multithreaded test execution.
 * - Stores each booking response as a Map<String, Object>, which matches the parsed JSON structure.
 */
public class SavedBookResponses {

    /**
     * Thread-safe map to store booking responses per test case.
     * Key   → Test case ID (unique identifier for a test scenario)
     * Value → Booking response map (parsed JSON response body)
     */
    private static final Map<String, Map<String, Object>> savedResponses = new ConcurrentHashMap<>();

    /**
     * Saves the booking response for a specific test case.
     *
     * @param testCaseId Unique identifier for the test case (e.g., "TC001")
     * @param bookMap    Parsed booking response (as a Map)
     */
    public static void putBookResponses(String testCaseId, Map<String, Object> bookMap) {
        savedResponses.put(testCaseId, bookMap);
    }

    /**
     * Retrieves the previously saved booking response for a given test case.
     *
     * @param testCaseId Unique identifier for the test case (must match the one used in {@link #putBookResponses})
     * @return The stored booking response map, or {@code null} if no response was saved for this test case.
     */
    public static Map<String, Object> getBookResponses(String testCaseId) {
        return savedResponses.get(testCaseId);
    }
}
