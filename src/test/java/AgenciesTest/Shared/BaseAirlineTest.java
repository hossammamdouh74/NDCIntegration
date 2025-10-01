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
    public static final List<Map<String, Object>> validAddPaxOffers = new ArrayList<>();
    public static final List<Map<String, Object>> validRetrieveOffers = new ArrayList<>();

    // 🔁 Shared method for adding entries
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

    // 🔹 FareConfirm → Valid for Book
    public static void AddValidFareConfirmOffer(String testCaseId, String description, String offerId, Map<String, Object> offerMap) {
        addValidEntry(validFareConfirmOffers, testCaseId, description, offerId, offerMap);
    }



    // 🔹 Book or Hold → Valid for Retrieve
    public static void AddValidRetrieveOffer(String testCaseId, String description, String offerId, Map<String, Object> offerMap) {
        addValidEntry(validRetrieveOffers, testCaseId, description, offerId, offerMap);
    }


    // ✅ Data Providers

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
    public Object[][] validAddPaxData() {
        return validFareConfirmOffers.stream()
                .map(entry -> new Object[]{
                        entry.get("testCaseId"),
                        entry.get("offerId"),
                        entry.get("description"),
                        entry.get("expectedOffer")
                }).toArray(Object[][]::new);
    }

    @DataProvider
    public Object[][] validBookData() {
        return validAddPaxOffers.stream()
                .map(entry -> new Object[]{
                        entry.get("testCaseId"),
                        entry.get("offerId"),
                        entry.get("description"),
                        entry.get("expectedOffer")
                }).toArray(Object[][]::new);
    }

    // 🔚 Final step - Retrieve
    @DataProvider
    public Object[][] validRetrieveData() {
        return validRetrieveOffers.stream()
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
