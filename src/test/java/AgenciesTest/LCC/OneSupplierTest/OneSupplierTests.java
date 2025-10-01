package AgenciesTest.LCC.OneSupplierTest;

import AgenciesTest.Shared.BaseAirlineTest;
import Utils.Helper.SearchResult;
import Utils.ReportManager.ReportManager;
import org.testng.SkipException;
import org.testng.annotations.Test;
import java.util.List;
import java.util.Map;
import static Utils.Assertions.PerformAssertions.*;
import static Utils.Helper.HelperTestData.*;
import static Utils.Helper.SavedBookResponses.getBookResponses;
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
public class OneSupplierTests extends BaseAirlineTest {

    /**
     * 🔹 POSITIVE SEARCH TESTS
     * - Runs search with valid payloads.
     * - Expects status 200 and a non-null SearchResult.
     * - Saves selected offer for downstream tests (FareConfirm, AddPax, etc.).
     */
    @Test(dataProvider = "positiveSearchDataProvider", dataProviderClass = OneSupplierDataProvider.class)
    public void testPositiveSearch(String testCaseId, String description, Map<String, Object> data) {
        ReportManager.getTest().info("Test Supplier: "+AgencyName+"\nRunning test case: " + testCaseId + " - " + description);
        System.out.println("Test Supplier: "+AgencyName+"\nRunning test case: " + testCaseId + " - " + description);

        // Build payload + headers
        Map<String, Object> payload = SearchPayload(data);
        Map<String, String> headers = getHeaders(AgencyName);
        String addPaxFlow = (String) data.getOrDefault("addPaxFlow","pass");
        int expectedStatusCode = (int) data.getOrDefault("expectedStatusCode", 200);

        ReportManager.getTest().info("Search URL: " + SearchEndPoint);

        // Execute Search
        SearchResult result = PerformSearch(SearchEndPoint, headers, payload, AgencyName, expectedStatusCode, addPaxFlow);

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
    public void testFareConfirm( String testCaseId,String selectedOfferId, String description, Map<String, Object> selectedOfferFromSearch) {
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
    public void testAddPax( String testCaseId,String fareConfirmOfferId, String description, Map<String, Object> selectedOfferFromSearch) {
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

        logPassengerBreakdown(

                searchPayload, AgencyName, testCaseId, description);

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
        // 🔸 Positive flow
        Map<String, Object> payload = AddPaxPayload(searchPayload, fareConfirmOfferId);

        String addPaxOfferId = PerformAddPax(AddPaxEndPoint, headers, payload, 200, "","");
        System.out.println("✅ AddPax Offer ID: " + addPaxOfferId);

        ReportManager.getTest().info("✅ AddPax Offer ID: " + addPaxOfferId);
        AddValidAddPax(testCaseId, description, addPaxOfferId, selectedOfferFromSearch);
    }

    /**
     * 🔹 BOOKING FLOW TESTS
     * - Uses saved AddPaxOfferID.
     * - Booking flow type (e.g., book, hold, fail) is set in Search data.
     * - Unified booking flow runner handles different paths.
     */
    /*  @Test(priority = 3, dataProvider = "validBookData")
    public void testBookingFlow( String testCaseId,String addPaxOfferId, String description, Map<String, Object> selectedOfferFromSearch) {
        String bookingFlow = (String) selectedOfferFromSearch.get("bookingFlow");
        runUnifiedBookingFlow(bookingFlow, addPaxOfferId, testCaseId, description, selectedOfferFromSearch, AgencyName);
    }
*/
    /**
     * 🔹 RETRIEVE TESTS
     * - Final validation step: retrieve booking using NDC reference + PNR.
     * - Loads saved booking response for comparison.
     * - Asserts retrieve response matches expectations.
     */
    @Test(priority = 99, dataProvider = "validRetrieveData")
    public void testRetrieve(String testCaseId, String description, Map<String, Object> selectedOfferFromSearch) {
        System.out.println("Base URL being used for Retrieve request: " + RetrieveEndPoint);

        String ndcBookingReference = (String) selectedOfferFromSearch.get("ndcBookingReference");
        String airlinePnr = (String) selectedOfferFromSearch.get("airlinePnr");

        // Skip if no booking references are available
        if (ndcBookingReference == null || airlinePnr == null) {
            throw new SkipException("❌ Booking info not found for test: " + testCaseId);
        }

        // Load saved booking response
        Map<String, Object> bookMap = getBookResponses(testCaseId);
        if (bookMap == null) {
            throw new SkipException("❌ No saved booking response found for testCaseId: " + testCaseId);
        }

        System.out.println("\n🔹 Running Retrieve for: " + AgencyName + " | " + testCaseId + " | " + description);
        ReportManager.getTest().info("🔹 Running Retrieve for: " + testCaseId + " - " + description);

        Map<String, String> headers = getHeaders(AgencyName);
        Map<String, Object> payload = RetrievePayload(selectedOfferFromSearch);

        // Execute Retrieve
        Map<String, Object> retrieveInfo = PerformRetrieve(RetrieveEndPoint, headers, payload, 200, bookMap);

        System.out.println("✅ Retrieve Response: " + retrieveInfo);
        ReportManager.getTest().info("✅ Retrieve Response: " + retrieveInfo);
    }

}