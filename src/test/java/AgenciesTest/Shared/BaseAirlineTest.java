package AgenciesTest.Shared;

import Utils.ReportManager.ReportManager;
import com.aventstack.extentreports.ExtentTest;
import org.testng.ITestResult;
import org.testng.SkipException;
import org.testng.annotations.*;
import java.lang.reflect.Method;
import java.util.*;
import static Utils.Assertions.PerformAssertions.*;
import static Utils.Helper.HelperGeneralMethods.skipIfBookingFlowNotIn;
import static Utils.Helper.HelperTestData.*;
import static Utils.Helper.HelperTestData.BookAfterHoldEndPoint;
import static Utils.Helper.SavedBookResponses.putBookResponses;
import static Utils.Loader.FareConfirmFileManager.deleteFareConfirmResponsesFolder;
import static Utils.Loader.PayloadLoader.*;

public abstract class BaseAirlineTest {

    // ✅ Lists for each flow stage
    public static final List<Map<String, Object>> validOfferIds = new ArrayList<>();
    public static final List<Map<String, Object>> validFareConfirmOffers = new ArrayList<>();
    public static final List<Map<String, Object>> validRetrieveOffers = new ArrayList<>();

    // 🔁 Shared method for adding valid entries
    public static void addValidEntry(List<Map<String, Object>> list, String testCaseId, String description, String offerId, Map<String, Object> offerMap) {
        if (offerId == null || offerId.isEmpty() || offerMap == null) return;
        Map<String, Object> entry = new HashMap<>();
        entry.put("testCaseId", testCaseId);
        entry.put("description", description);
        entry.put("offerId", offerId);
        entry.put("expectedOffer", offerMap);
        list.add(entry);
    }

    // 🔹 Search → Valid for FareConfirm
    public static void AddValidSearchOffer(String testCaseId, String description, String offerId, Map<String, Object> offerMap) {
        addValidEntry(validOfferIds, testCaseId, description, offerId, offerMap);
    }

    // 🔹 FareConfirm → Valid for Book/Hold
    public static void AddValidFareConfirmOffer(String testCaseId, String description, String offerId, Map<String, Object> offerMap) {
        // Save full map to be used by booking test
        Map<String, Object> fullEntry = new HashMap<>(offerMap);
        fullEntry.put("SelectedOfferId", offerId);
        addValidEntry(validFareConfirmOffers, testCaseId, description, offerId, offerMap);
    }

    // 🔹 Book/Hold → Valid for Retrieve
    public static void AddValidRetrieveOffer(String testCaseId, String description, String offerId, Map<String, Object> offerMap) {
        addValidEntry(validRetrieveOffers, testCaseId, description, offerId, offerMap);
    }

    /**
     * 🔹 Unified Booking Flow
     * Handles both book and hold flows (including BookAfterHold)
     */
    public static void runUnifiedBookingFlow(String bookingFlow,
                                             String testCaseId,
                                             String description,
                                             Map<String, Object> selectedOfferFromSearch,
                                             String agencyName) {

        Map<String, Object> searchPayload = (Map<String, Object>) selectedOfferFromSearch.get("searchPayload");
        String fareConfirmId = (String) selectedOfferFromSearch.get("fareConfirmResponseId");
        String selectedOfferId = (String) selectedOfferFromSearch.get("SelectedOfferId");

        Map<String, Object> bookingInfo;

        switch (bookingFlow) {
            case "book":
                System.out.println("\n🔹 Running Book for: " + agencyName + " | " + testCaseId + " | " + description);
                ReportManager.getTest().info("🔹 Running Book for: " + testCaseId + " - " + description);

                skipIfBookingFlowNotIn(selectedOfferFromSearch, Set.of("book"), testCaseId, "Booking");

                Map<String, Object> bookPayload = buildBookPayload(searchPayload, fareConfirmId, selectedOfferId);
                bookingInfo = PerformBook(BookEndPoint, bookPayload, 200, selectedOfferFromSearch, searchPayload, fareConfirmId);
                break;

            case "holdbook":
                System.out.println("\n🔹 Running Hold for: " + agencyName + " | " + testCaseId + " | " + description);
                ReportManager.getTest().info("🔹 Running Hold for: " + testCaseId + " - " + description);

                skipIfBookingFlowNotIn(selectedOfferFromSearch, Set.of("holdbook"), testCaseId, "Hold");

                Map<String, Object> holdPayload = buildBookPayload(searchPayload, fareConfirmId, selectedOfferId);
                bookingInfo = PerformBook(HoldEndPoint, holdPayload, 200, selectedOfferFromSearch, searchPayload, fareConfirmId);

                selectedOfferFromSearch.putAll(bookingInfo);

                // ✅ Continue to BookAfterHold
                System.out.println("\n🔹 Running BookAfterHold...");
                ReportManager.getTest().info("🔹 Running BookAfterHold for: " + testCaseId + " - " + description);

                Map<String, Object> bookAfterHoldPayload = BookAfterHoldPayload(selectedOfferFromSearch);
                bookingInfo = PerformBookAfterHold(BookAfterHoldEndPoint, bookAfterHoldPayload, 200, bookingInfo);
                break;

            default:
                throw new SkipException("❌ Unsupported booking flow: " + bookingFlow + " for testCaseId: " + testCaseId);
        }

        // 🔁 Post-booking: Save response for retrieve
        selectedOfferFromSearch.putAll(bookingInfo);
        putBookResponses(testCaseId, bookingInfo);
        AddValidRetrieveOffer(testCaseId, description, selectedOfferId, selectedOfferFromSearch);

        System.out.println("✅ Booking Flow [" + bookingFlow + "] Success — Booking Info: " + bookingInfo);
        ReportManager.getTest().info("✅ Booking Flow [" + bookingFlow + "] Success — Booking Info: " + bookingInfo);
    }

    // 📊 Data Providers

    @DataProvider
    public Object[][] validFareConfirmData() {
        return validOfferIds.stream()
                .map(entry -> new Object[]{
                        entry.get("testCaseId"),
                        entry.get("offerId"),
                        entry.get("description"),
                        entry.get("expectedOffer")
                }).toArray(Object[][]::new);
    }

    @DataProvider
    public Object[][] validBookData() {
        return validFareConfirmOffers.stream()
                .map(entry -> new Object[]{
                        entry.get("testCaseId"),
                        entry.get("description"),
                        entry.get("expectedOffer")
                }).toArray(Object[][]::new);
    }

    // 📊 Extent Report Setup
    @BeforeSuite
    public void setUp() {
        ReportManager.getInstance();
        deleteFareConfirmResponsesFolder();
    }

    @BeforeMethod
    public void registerTest(Method method) {
        String testName = method.getName();
        ExtentTest test = ReportManager.createTest(testName, "API Test Execution");
        extentTest.set(test);
    }

    @AfterMethod
    public void updateResult(ITestResult result) {
        ExtentTest test = extentTest.get();
        switch (result.getStatus()) {
            case ITestResult.FAILURE:
                test.fail(result.getThrowable());
                break;
            case ITestResult.SUCCESS:
                test.pass("Test passed");
                break;
            case ITestResult.SKIP:
                test.skip(result.getThrowable());
                break;
        }
        extentTest.remove();
    }

    @AfterSuite
    public void tearDownReport() {
        ReportManager.flush();
    }
}
