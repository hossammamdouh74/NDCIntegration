package AgenciesTest.LCC.GenericTest;

import AgenciesTest.Shared.BaseAirlineTest;
import Utils.Helper.SearchResult;
import Utils.ReportManager.ReportManager;
import org.testng.SkipException;
import org.testng.annotations.*;

import java.util.List;
import java.util.Map;
import java.util.Objects;

import static Utils.Assertions.PerformAssertions.*;
import static Utils.Helper.HelperTestData.*;
import static Utils.Helper.SavedBookResponses.getBookResponses;
import static Utils.Loader.HeaderLoader.getHeaders;
import static Utils.Loader.PayloadLoader.*;
import static Utils.ReportManager.ReportManager.logPassengerBreakdown;

/**
 * Generic test suite for LCC agencies that validates the entire booking lifecycle:
 * Search → FareConfirm → AddPax → Book / Hold → Retrieve.
 * Designed to be reusable for different agencies by simply passing the agency name.
 * Uses TestNG DataProviders to load test cases dynamically from JSON.
 */
public class AllSuppliersTest extends BaseAirlineTest {

    // The specific agency this test instance will run for.
    private final String agencyName;

    // Constructor to set the agency name when instantiating the test.
    public AllSuppliersTest(String agencyName) {
        this.agencyName = agencyName;
    }

    /**
     * Step 1: Search for flights based on test data.
     * Loads payload from JSON, executes Search API, selects an offer,
     * and stores it for the next steps in the flow.
     */
    @Test(dataProvider = "searchDataProvider")
    public void testSearch(String testCaseId, String description, Map<String, Object> data) {
        ReportManager.getTest().info("Running: " + testCaseId + " - " + description);
        System.out.println("Test Supplier: "+agencyName+"\nRunning test case: " + testCaseId + " - " + description);

        Map<String, Object> payload = SearchPayload(data);
        Map<String, String> headers = getHeaders(agencyName);

        int expectedStatusCode = (int) data.getOrDefault("expectedStatusCode", 200);
        String scenarioType = (String) data.get("invalidScenarioType");

        ReportManager.getTest().info("Search URL: " + SearchEndPoint);

        // perform search
        SearchResult result = PerformSearch(SearchEndPoint, headers, payload, agencyName, expectedStatusCode, scenarioType);

        // ✅ handle invalid scenario (status 400)
        if (expectedStatusCode == 400) {
            if (scenarioType == null || scenarioType.trim().isEmpty()) {
                System.out.println("⚠️ Skipping invalid assertion: scenarioType is missing for testCaseId=" + testCaseId);
                ReportManager.getTest().warning("Skipping invalid assertion: scenarioType is missing.");
                return; // exit safely
            }
            System.out.println("Running invalid assertion for scenario: " + scenarioType);
            return; // we don’t need to continue with offer selection
        }

        // ✅ handle valid case
        if (result == null) {
            throw new AssertionError("❌ SearchResult is null for valid scenario: " + testCaseId);
        }

        String selectedOfferID = result.offerId();
        Map<String, Object> selectedOffer = result.offerResponseMap();
        selectedOffer.put("searchPayload", payload);
        selectedOffer.put("bookingFlow", data.getOrDefault("bookingFlow", "book")); // default = "book"

        System.out.println("✅ Selected Offer ID: " + selectedOfferID);
        ReportManager.getTest().info("✅ Selected Offer ID: " + selectedOfferID);

        AddValidSearchOffer(testCaseId, description, selectedOfferID, selectedOffer);
    }

    /**
     * Step 2: FareConfirm the selected offer from the search step.
     * Confirms pricing & availability before adding passengers.
     */
    @Test(priority = 1, dataProvider = "validFareConfirmData")
    public void testFareConfirm(String selectedOfferId, String testCaseId, String description, Map<String, Object> selectedOfferFromSearch) {
        if (selectedOfferId == null || selectedOfferId.isEmpty()) {
            throw new SkipException("❌ Skipping FareConfirm - No valid offer for: " + testCaseId);
        }

        Map<String, String> headers = getHeaders(agencyName);
        Map<String, Object> payload = FareConfirmPayload(selectedOfferId);
        String addPaxFlow = (String) selectedOfferFromSearch.getOrDefault("addPaxFlow","pass");

        String FareConfirmOfferID = PerformFareConfirm(FareConfirmOfferEndPoint, headers, payload, selectedOfferFromSearch, 200,addPaxFlow);
        System.out.println("✅ FareConfirmOfferID captured: " + FareConfirmOfferID);

        // Store for AddPax step
        AddValidFareConfirmOffer(testCaseId, description, FareConfirmOfferID, selectedOfferFromSearch);
    }

    /**
     * 🔹 ADD PAX TESTS
     * - Positive flow → Adds passengers successfully (status 200).
     * - Negative flow → Iterates over invalid payloads from /AddPax/ folder (status 400).
     * - Saves AddPaxOfferID for booking tests.
     */
    @Test(priority = 2, dataProvider = "validAddPaxData")
    public void testAddPax(String fareConfirmOfferId, String testCaseId, String description, Map<String, Object> selectedOfferFromSearch) {
        if (fareConfirmOfferId == null || fareConfirmOfferId.isEmpty()) {
            throw new SkipException("No valid offer ID for AddPax for: " + testCaseId);
        }

        // Ensure searchPayload exists (needed for passenger info)
        Map<String, Object> searchPayload = (Map<String, Object>) selectedOfferFromSearch.get("searchPayload");
        if (searchPayload == null) {
            throw new SkipException("Missing searchPayload for AddPax test: " + testCaseId);
        }

        Map<String, String> headers = getHeaders(agencyName);
        System.out.println("\n🔹 Running Add Pax for: " +agencyName + " | " + testCaseId + " Description: " + description);

        logPassengerBreakdown(searchPayload, agencyName, testCaseId, description);

        String addPaxFlow = (String) selectedOfferFromSearch.get("addPaxFlow");

        // 🔸 Negative flow
        if (Objects.equals(addPaxFlow, "fail")) {
            String folderPath = "src/test/resources/TestData/" + agencyName + "/AddPax/";
            List<Map<String, Object>> negativePayloads = getNegativeAddPaxPayloads(folderPath,testCaseId ,fareConfirmOfferId);

            for (Map<String, Object> negPayload : negativePayloads) {
                System.out.println("⚠️ Running Negative AddPax ");
                PerformAddPax(AddPaxEndPoint, headers, negPayload, 400, addPaxFlow,"");
            }
            return; // Exit after running negative tests
        }

        // 🔸 Positive flow
        Map<String, Object> payload = AddPaxPayload(searchPayload, fareConfirmOfferId);

        String addPaxOfferId = PerformAddPax(AddPaxEndPoint, headers, payload, 200, "","");
        System.out.println("✅ AddPax Offer ID: " + addPaxOfferId);

        ReportManager.getTest().info("✅ AddPax Offer ID: " + addPaxOfferId);
        AddValidAddPax(testCaseId, description, addPaxOfferId, selectedOfferFromSearch);
    }

    /**
     * Step 4: Booking Flow (Book / Hold → BookAfterHold).
     * The type of flow is determined by "bookingFlow" in test data.
     */
/*    @Test(priority = 3, dataProvider = "validBookData")
    public void testBookingFlow(String addPaxOfferId, String testCaseId, String description, Map<String, Object> selectedOfferFromSearch) {
        String bookingFlow = (String) selectedOfferFromSearch.get("bookingFlow");
        runUnifiedBookingFlow(bookingFlow, addPaxOfferId, testCaseId, description, selectedOfferFromSearch, agencyName);
    }
*/
    /**
     * Step 5: Retrieve booking details from NDCBookingReference & AirlinePNR.
     * Ensures that booking matches what was saved in the Book step.
     */
/*    @Test(priority = 99, dataProvider = "validRetrieveData")
    public void testRetrieve(String testCaseId, String description, Map<String, Object> selectedOfferFromSearch) {
        String ndcBookingReference = (String) selectedOfferFromSearch.get("ndcBookingReference");
        String airlinePnr = (String) selectedOfferFromSearch.get("airlinePnr");

        if (ndcBookingReference == null || airlinePnr == null) {
            throw new SkipException("❌ Booking info not found for test: " + testCaseId);
        }

        Map<String, Object> bookMap = getBookResponses(testCaseId);
        if (bookMap == null) {
            throw new SkipException("❌ No saved booking response found for testCaseId: " + testCaseId+ " | " + description);
        }
        System.out.println("\n🔹 Running Retrieve for: " + agencyName + " | " + testCaseId + " | " + description);
        ReportManager.getTest().info("🔹 Running Retrieve for: " + testCaseId + " - " + description);

        Map<String, String> headers = getHeaders(agencyName);
        Map<String, Object> payload = RetrievePayload(selectedOfferFromSearch);

        Map<String, Object> retrieveInfo = PerformRetrieve(RetrieveEndPoint, headers, payload, 200, bookMap);
        System.out.println("✅ Retrieve completed for: " + testCaseId + retrieveInfo);
        ReportManager.getTest().info("✅ Retrieve Response: " + retrieveInfo);
    }
*/
    /**
     * Loads Search test data from the agency-specific Search folder.
     * Each JSON file becomes one test case.
     */
    @DataProvider(name = "searchDataProvider")
    public Object[][] searchDataProvider() {
        String folderPath = "src/test/resources/TestData/" + agencyName + "/Search";
        return getSearchPayloadFromJSON(folderPath);
    }
}
