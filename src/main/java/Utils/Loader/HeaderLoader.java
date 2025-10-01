package Utils.Loader;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.File;
import java.util.Map;

/**
 * Utility class responsible for loading HTTP request headers for a specific agency
 * from a JSON configuration file.
 *
 * <p>This is useful when running automated API tests for multiple agencies where
 * each agency has its own authentication and request header configuration.</p>
 *
 * <p>Example JSON file structure ("agency_credentials.json"):</p>
 * <pre>
 * {
 *   "AirCairo": {
 *     "Authorization": "Bearer some_token",
 *     "Accept": "application/json"
 *   },
 *   "EgyptAir": {
 *     "Authorization": "Bearer another_token",
 *     "Accept": "application/json"
 *   }
 * }
 * </pre>
 *
 * <p>Usage example:</p>
 * <pre>
 * Map&lt;String, String&gt; headers = HeaderLoader.getHeaders("AirCairo");
 * </pre>
 */
public class HeaderLoader {

    /**
     * Retrieves the headers for the specified agency from the JSON file.
     *
     * @param agencyName the name of the agency whose headers are needed
     *                   (must exactly match the key in the JSON file, e.g., "AirCairo").
     * @return a {@code Map<String, String>} containing the header key-value pairs for the agency.
     * @throws RuntimeException if the file cannot be read or the agency name is not found.
     */
    public static Map<String, String> getHeaders(String agencyName) {
        try {
            // Create an ObjectMapper instance for reading JSON
            ObjectMapper mapper = new ObjectMapper();

            // JSON file containing all agencies' headers
            File jsonFile = new File("src/test/resources/headers/agencyClaims.json");

            // Read the JSON into a nested map:
            // Outer map -> key: agencyName, value: headers map
            // Inner map -> key: header name, value: header value
            Map<String, Map<String, String>> allAgencies =
                    mapper.readValue(jsonFile, new TypeReference<>() {
                    });

            // Return the headers for the requested agency
            return allAgencies.get(agencyName);

        } catch (Exception e) {
            // Wrap in RuntimeException for easier handling in test code
            throw new RuntimeException("❌ Failed to read headers for agency: " + agencyName, e);
        }
    }
}
