package AgenciesTest.LCC.OneSupplierTest;

import AgenciesTest.Shared.BaseAirlineTest;
import Utils.Helper.SearchResult;
import Utils.ReportManager.ReportManager;
import org.testng.SkipException;
import org.testng.annotations.Test;
import java.util.Map;
import static Utils.Assertions.PerformAssertions.*;
import static Utils.Helper.HelperTestData.*;
import static Utils.Helper.SavedBookResponses.getBookResponses;
import static Utils.Loader.HeaderLoader.getHeaders;
import static Utils.Loader.PayloadLoader.*;

/**
 * 🔹 OneSupplierTests
 * Updated for unified booking flow (AddPax removed)
 * Flow: Search → FareConfirm → Book/Hold → Retrieve
 */
public class OneSupplierTests extends BaseAirlineTest {

    // 🔹 POSITIVE SEARCH TEST
    @Test(dataProvider = "positiveSearchDataProvider", dataProviderClass = OneSupplierDataProvider.class)
    public void testPositiveSearch(String testCaseId, String description, Map<String, Object> data) {
        ReportManager.getTest().info("Test Supplier: " + AgencyName + "\nRunning test case: " + testCaseId + " - " + description);

        // Build payload + headers
        Map<String, Object> payload = SearchPayload(data);
        Map<String, String> headers = getHeaders(AgencyName);
        int expectedStatusCode = (int) data.getOrDefault("expectedStatusCode", 200);

        // Execute Search
        SearchResult result = PerformSearch(SearchEndPoint, headers, payload, expectedStatusCode, "pass");

        if (result == null) throw new AssertionError("❌ SearchResult is null for testCaseId: " + testCaseId);

        String selectedOfferID = result.offerId();
        Map<String, Object> selectedOffer = result.offerResponseMap();

        // Save context for downstream flows
        selectedOffer.put("searchPayload", payload);
        selectedOffer.put("bookingFlow", data.getOrDefault("bookingFlow", "book")); // book or holdbook
        selectedOffer.put("searchResponseId", result.responseId());
        selectedOffer.put("supplier", result.supplier());
        selectedOffer.put("credentialsSelector", data.get("credentialsSelector"));

        AddValidSearchOffer(testCaseId, description, selectedOfferID, selectedOffer);
    }

    // 🔹 FARE CONFIRM TEST
    @Test(priority = 1, dataProvider = "validFareConfirmData")
    public void testFareConfirm(String testCaseId, String selectedOfferId, String description, Map<String, Object> selectedOfferFromSearch) {
        if (selectedOfferId == null || selectedOfferId.isEmpty())
            throw new SkipException("No valid offer ID to confirm for: " + testCaseId);

        System.out.println("\n🔹 Running FareConfirm for: " + AgencyName + " | " + testCaseId + " Description: " + description);
        ReportManager.getTest().info("Running FareConfirm for: " + testCaseId + " - " + description);

        Map<String, Object> payload = FareConfirmPayload(selectedOfferFromSearch);

        String fareConfirmOfferID = PerformFareConfirm(FareConfirmOfferEndPoint, payload, selectedOfferFromSearch, 200, "pass");
        System.out.println("✅ FareConfirmOfferID captured: " + fareConfirmOfferID);

        AddValidFareConfirmOffer(testCaseId, description, fareConfirmOfferID, selectedOfferFromSearch);
    }

    // 🔹 BOOK/HOLD TEST
    @Test(priority = 2, dataProvider = "validBookData")
    public void testBookingFlow(String testCaseId, String description, Map<String, Object> selectedOfferFromSearch) {
        String bookingFlow = (String) selectedOfferFromSearch.get("bookingFlow");
        runUnifiedBookingFlow(bookingFlow, testCaseId, description, selectedOfferFromSearch, AgencyName);
    }
/*
    // 🔹 RETRIEVE TEST
    @Test(priority = 3, dataProvider = "validRetrieveData")
    public void testRetrieve(String testCaseId, String description, Map<String, Object> selectedOfferFromSearch) {
        String ndcBookingReference = (String) selectedOfferFromSearch.get("ndcBookingReference");
        String airlinePnr = (String) selectedOfferFromSearch.get("airlinePnr");

        if (ndcBookingReference == null || airlinePnr == null)
            throw new SkipException("Booking info not found for testCaseId: " + testCaseId);

        Map<String, Object> bookMap = getBookResponses(testCaseId);
        if (bookMap == null) throw new SkipException("No saved booking response for testCaseId: " + testCaseId);

        Map<String, Object> payload = RetrievePayload(selectedOfferFromSearch);
        Map<String, Object> retrieveInfo = PerformRetrieve(RetrieveEndPoint, payload, 200, bookMap);

        System.out.println("✅ Retrieve Response: " + retrieveInfo);
        ReportManager.getTest().info("✅ Retrieve Response: " + retrieveInfo);
    }*/
}
