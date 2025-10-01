package AgenciesTest.LCC.GenericTest;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.testng.annotations.Factory;

import java.io.File;
import java.util.Map;

/**
 * TestNG Factory class for dynamically creating {@link AllSuppliersTest} instances
 * based on agencies defined in the {@code agencyClaims.json} file.
 *
 * <p>Purpose:
 * <ul>
 *   <li>Reads the JSON file containing multiple agency credentials.</li>
 *   <li>Extracts all agency names (keys in the JSON).</li>
 *   <li>Creates one {@link AllSuppliersTest} instance for each agency name.</li>
 *   <li>Ensures that all agencies are tested without manually listing them.</li>
 * </ul>
 *
 * <p>JSON Structure Example:
 * <pre>{@code
 * {
 *   "AirCairo": { "AgencyID": "xxx", "BranchId": "yyy" },
 *   "NileAir":  { "AgencyID": "aaa", "BranchId": "bbb" }
 * }
 * }</pre>
 *
 * <p>Execution:
 * <ul>
 *   <li>This factory will be executed by TestNG before running tests.</li>
 *   <li>Each agency entry in the JSON creates a separate test instance.</li>
 * </ul>
 */
public class SuppliersFactoryTest {

    /**
     * Reads all agencies from {@code agencyClaims.json} and creates one
     * {@link AllSuppliersTest} instance per agency.
     *
     * @return an array of {@link AllSuppliersTest} instances for TestNG to execute.
     */
    @Factory
    public Object[] createTests() {
        try {
            // Step 1: Load the JSON file containing all agencies
            ObjectMapper mapper = new ObjectMapper();
            File file = new File("src/test/resources/headers/agencyClaims.json");

            // Step 2: Deserialize into a Map of agencyName → headersMap
            Map<String, Map<String, String>> agencies = mapper.readValue(
                    file, new TypeReference<>() {}
            );

            // Step 3: Create a GenericAgencyTest instance for each agency name
            return agencies.keySet().stream()
                    .map(AllSuppliersTest::new) // Pass agencyName to the test constructor
                    .toArray(AllSuppliersTest[]::new);

        } catch (Exception e) {
            throw new RuntimeException(
                    "Failed to load agencies dynamically from agencyClaims.json", e
            );
        }
    }
}
