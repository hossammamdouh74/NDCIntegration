package Utils.Helper;

import com.aventstack.extentreports.ExtentTest;

public class HelperTestData {

    // ✅ Base URL
    public static final String baseUrl = "https://ndc-supplier-integration-staging.azurewebsites.net/";

    // ✅ API Endpoints
    public static final String SearchEndPoint = baseUrl + "api/FlightSearch/Search";
    public static final String FareConfirmOfferEndPoint = baseUrl + "api/FlightSearch/FareConfirm";
    public static final String BookEndPoint = baseUrl + "api/FlightBooking/Book";
    public static final String HoldEndPoint = baseUrl + "api/FlightBooking/Hold";
    public static final String BookAfterHoldEndPoint = baseUrl + "api/FlightBooking/BookAfterHold";
    public static final String RetrieveEndPoint = baseUrl + "api/FlightBooking/RetrieveBooking";

    // ✅ Supplier Agencies
    public static final String AgencyName = "Aegean";

    // ✅ Extent Reports
    public static ThreadLocal<ExtentTest> extentTest = new ThreadLocal<>();
}
