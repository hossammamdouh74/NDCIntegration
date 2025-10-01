package Utils.Helper;

import com.aventstack.extentreports.ExtentTest;

public class HelperTestData {

    // ✅ Base URL
    public static final String baseUrl = "https://flight-engine-staging.azurewebsites.net";

    // ✅ API Endpoints
    public static final String SearchEndPoint = baseUrl + "/api/Offers/GetAvailableOffers";
    public static final String FareConfirmOfferEndPoint = baseUrl + "/api/Offers/FareConfirmOffer";
    public static final String AddPaxEndPoint = baseUrl + "/api/Offers/AddPassengersDetails";
    public static final String BookEndPoint = baseUrl + "/api/Order/BookAndPay";
    public static final String HoldEndPoint = baseUrl + "/api/Order/Hold";
    public static final String BookAfterHoldEndPoint = baseUrl + "/api/Order/BookAfterHold";
    public static final String RetrieveEndPoint = baseUrl + "/api/Order/Retrieve";

    // ✅ Supplier Agencies
    public static final String AgencyName = "Aegean";

    // ✅ Extent Reports
    public static ThreadLocal<ExtentTest> extentTest = new ThreadLocal<>();
}
