package AgenciesTest.LCC.NegativeTest;

import AgenciesTest.Shared.BaseAirlineTest;
import Utils.Helper.HelperTestData;
import Utils.Helper.SearchResult;
import Utils.ReportManager.ReportManager;
import org.testng.SkipException;
import org.testng.annotations.Test;

import java.util.List;
import java.util.Map;

import static Utils.Assertions.PerformAssertions.*;
import static Utils.Helper.HelperTestData.*;
import static Utils.Loader.HeaderLoader.getHeaders;
import static Utils.Loader.PayloadLoader.*;
import static Utils.ReportManager.ReportManager.logPassengerBreakdown;

/**
 * 🔹 OneSupplierTests
 * End-to-end test flow for "OneSupplier" agency against NDC endpoints.
 * Covers:
 *   - Negative Search (validation failures)
 *   - Positive Search (offer retrieval)
 *   - FareConfirm
 *   - AddPax (positive & negative)
 *   - Booking
 *   - Retrieve
 * ⚖️ Note: All endpoints are currently in one class for full E2E visibility.
 * Could be split into dedicated test classes (SearchTests, FareConfirmTests, etc.)
 * if you want better separation of concerns.
 */
public class NegativeScenarioWTTest extends BaseAirlineTest {

    /**
     * 🔹 POSITIVE SEARCH TESTS
     * - Runs search with valid payloads.
     * - Expects status 200 and a non-null SearchResult.
     * - Saves selected offer for downstream tests (FareConfirm, AddPax, etc.).
     */
    @Test(dataProvider = "positiveSearchDataProvider", dataProviderClass = NegativeScenarioWTDataProvider.class)
    public void testSearch(String testCaseId, String description, Map<String, Object> data) {
        ReportManager.getTest().info("Running: " + testCaseId + " - " + description);
        System.out.println("Running test case: " + testCaseId + " - " + description);

        // Build payload + headers
        Map<String, Object> payload = SearchPayload(data);
        Map<String, String> headers = getHeaders(AgencyName);

        int expectedStatusCode = (int) data.getOrDefault("expectedStatusCode", 200);

        ReportManager.getTest().info("Search URL: " + SearchEndPoint);
        String addPaxFlow = (String) data.getOrDefault("addPaxFlow","pass");
        // Execute Search
        SearchResult result = PerformSearch(SearchEndPoint, headers, payload, AgencyName, expectedStatusCode ,addPaxFlow);

        // Ensure result is valid
        if (result == null) {
            throw new AssertionError("❌ SearchResult is null for valid scenario: " + testCaseId);
        }

        // Extract offer details
        String selectedOfferID = result.offerId();
        Map<String, Object> selectedOffer = result.offerResponseMap();

        // Save context for later test phases
        selectedOffer.put("searchPayload", payload);
        selectedOffer.put("bookingFlow", data.getOrDefault("bookingFlow", "book")); // default = book
        selectedOffer.put("addPaxFlow", data.getOrDefault("addPaxFlow", "pass"));  // default = pass

        System.out.println("✅ Selected Offer ID: " + selectedOfferID);
        ReportManager.getTest().info("✅ Selected Offer ID: " + selectedOfferID);

        AddValidSearchOffer(testCaseId, description, selectedOfferID, selectedOffer);
    }

    /**
     * 🔹 FARE CONFIRM TESTS
     * - Runs only if a valid offerID is found from Search.
     * - Verifies FareConfirm returns a valid FareConfirmOfferID.
     * - Saves FareConfirmOfferID for AddPax tests.
     */
    @Test(priority = 1, dataProvider = "validFareConfirmData")
    public void testFareConfirm(String testCaseId,String selectedOfferId,  String description, Map<String, Object> selectedOfferFromSearch) {
        if (selectedOfferId == null || selectedOfferId.isEmpty()) {
            throw new SkipException("No valid offer ID to confirm for: " + testCaseId);
        }

        System.out.println("\n🔹 Running FareConfirm for: " + AgencyName + " | " + testCaseId + " Description: " + description);

        // Build payload + headers
        Map<String, String> headers = getHeaders(AgencyName);
        Map<String, Object> payload = FareConfirmPayload(selectedOfferId);
        String addPaxFlow = (String) selectedOfferFromSearch.getOrDefault("addPaxFlow","pass");

        // Execute FareConfirm
        ReportManager.getTest().info("Running FareConfirm for: " + testCaseId + " - " + description);
        String FareConfirmOfferID = PerformFareConfirm(FareConfirmOfferEndPoint, headers, payload, selectedOfferFromSearch, 200,addPaxFlow);

        System.out.println("✅ FareConfirmOfferID captured: " + FareConfirmOfferID);
        AddValidFareConfirmOffer(testCaseId, description, FareConfirmOfferID, selectedOfferFromSearch);
    }

    /**
     * 🔹 ADD PAX TESTS
     * - Positive flow → Adds passengers successfully (status 200).
     * - Negative flow → Iterates over invalid payloads from /AddPax/ folder (status 400).
     * - Saves AddPaxOfferID for booking tests.
     */
    @Test(priority = 2, dataProvider = "validAddPaxData")
    public void testNegativeAddPax( String testCaseId,String fareConfirmOfferId, String description, Map<String, Object> selectedOfferFromSearch) {
        if (fareConfirmOfferId == null || fareConfirmOfferId.isEmpty()) {
            throw new SkipException("No valid offer ID for AddPax for: " + testCaseId);
        }

        // Ensure searchPayload exists (needed for passenger info)
        Map<String, Object> searchPayload = (Map<String, Object>) selectedOfferFromSearch.get("searchPayload");
        if (searchPayload == null) {
            throw new SkipException("Missing searchPayload for AddPax test: " + testCaseId);
        }

        Map<String, String> headers = getHeaders(AgencyName);
        System.out.println("\n🔹 Running Add Pax for: " + AgencyName + " | " + testCaseId + " Description: " + description);

        logPassengerBreakdown(searchPayload, AgencyName, testCaseId, description);

        String addPaxFlow = (String) selectedOfferFromSearch.get("addPaxFlow");

        // 🔸 Negative flow
        if ("fail".equals(addPaxFlow)) {
            System.out.println("\n🏃‍♂️ Running Negative AddPax...");
            String folderPath = "src/test/resources/TestData/" + AgencyName + "/AddPax/";
            List<Map<String, Object>> negativePayloads = getNegativeAddPaxPayloads(folderPath, testCaseId, fareConfirmOfferId);

            for (Map<String, Object> negPayload : negativePayloads) {
                String negScenarioType = (String) negPayload.get("scenarioType");
                PerformAddPax(AddPaxEndPoint, headers, negPayload, 400, addPaxFlow, negScenarioType);
            }
            return; // stop after negatives
        }
        System.out.println("❌ missing addPaxFlow :" +addPaxFlow);

    }

    /**
     * 🔹 NEGATIVE SEARCH TESTS
     * - Uses JSON data provider with invalid inputs.
     * - Verifies search fails with correct status code (default: 400).
     * - If scenarioType is missing → test is skipped gracefully.
     */
    @Test(priority = 3,dataProvider = "negativeSearchDataProvider", dataProviderClass = NegativeScenarioWTDataProvider.class)
    public void testNegativeSearch(String testCaseId, String description, Map<String, Object> data) {
        ReportManager.getTest().info("Running: " + testCaseId + " - " + description);
        System.out.println("Running test case: " + testCaseId + " - " + description);

        // Build payload + headers
        Map<String, Object> payload = SearchPayload(data);
        Map<String, String> headers = getHeaders(HelperTestData.AgencyName);

        // Default expected = 400
        int expectedStatusCode = (int) data.getOrDefault("expectedStatusCode", 400);
        String searchScenarioType = (String) data.get("invalidScenarioType");

        ReportManager.getTest().info("Search URL: " + SearchEndPoint);

        // Execute Search
        PerformSearch(SearchEndPoint, headers, payload, HelperTestData.AgencyName, expectedStatusCode, searchScenarioType);

        // Assertion only if scenarioType is present
        if (expectedStatusCode == 400) {
            if (searchScenarioType == null || searchScenarioType.trim().isEmpty()) {
                System.out.println("⚠️ Skipping invalid assertion: scenarioType is missing for testCaseId=" + testCaseId);
                ReportManager.getTest().warning("Skipping invalid assertion: scenarioType is missing.");
                return;
            }
            System.out.println("Running invalid assertion for scenario: " + searchScenarioType);
        }
    }
}