package Utils.Assertions;

import Utils.Helper.SearchResult;
import io.restassured.RestAssured;
import io.restassured.http.ContentType;
import io.restassured.response.Response;
import org.testng.asserts.SoftAssert;
import java.util.*;

import static Utils.Assertions.BookingAssertions.*;
import static Utils.Assertions.FareConfirmAssertions.*;
import static Utils.Assertions.PositiveSearchAssertions.*;
import static Utils.Assertions.RetrieveAssertions.validateRetrievePNR;
import static Utils.Helper.HelperNegativeAssertions.assertContainsExpectedError;
import static Utils.Loader.FareConfirmFileManager.*;
import static Utils.Helper.HelperGetResponse.*;
import static Utils.Loader.PayloadLoader.*;

/**
 * Utility class that orchestrates API calls for different booking workflow steps
 * and immediately triggers the corresponding validation/assertion logic.
 * Each method:
 *  1. Sends the request to the specified endpoint.
 *  2. Validates the HTTP response status and content.
 *  3. Runs detailed business-rule assertions.
 *  4. Returns extracted key data for chaining to the next step.
 * NOTE: This class currently assumes that responses follow the happy path structure.
 *       We should expand assertion methods to handle and log invalid / error cases
 *       for robustness in negative testing.
 */
public class PerformAssertions {

    /**
     * Executes a POST request with the provided payload and headers.
     * Logs the HTTP method and status, and returns the raw RestAssured Response.
     *
     * @param fullUrl Endpoint URL
     * @param requestPayload JSON payload object
     * @param headers HTTP headers
     * @return Response from the API
     */
    private static Response performPost(String fullUrl, Object requestPayload, Map<String, String> headers) {
        Response response = RestAssured
                .given()
                    .body(requestPayload)
                    .contentType(ContentType.JSON)
                    .headers(headers)
                    .log().method()
                .when()
                    .post(fullUrl)
                .then()
                    .log().status()
                    .extract()
                    .response();

        String correlationId = response.getHeader("Correlation-Id");
        System.out.println("Correlation-Id: " + correlationId);

        return response;
    }

    /**
     * Performs the Search API step:
     *  - Sends search payload
     *  - Validates response code & structure
     *  - Runs search-level assertions based on statusCode
     *  - Extracts and returns the first offer ID and its details (for valid searches)
     *
     * @param Url Search API endpoint
     * @param headers HTTP headers
     * @param payload Search request payload
     * @param agencyName Agency identifier (for assertions)
     * @param expectedStatusCode Expected HTTP status code
     * @return SearchResult containing offer ID and selected offer details (null if invalid search)
     */
    public static SearchResult PerformSearch(String Url, Map<String, String> headers,
                                             Map<String, Object> payload, String agencyName,
                                             int expectedStatusCode,String searchAddPaxScenarioType) {

        Response response = performPost(Url, payload, headers);
        validateResponse(response, expectedStatusCode, true);

        SoftAssert softAssert = new SoftAssert();

        switch (expectedStatusCode) {
            case 200:
                if (Objects.equals(searchAddPaxScenarioType, "pass")) {
                    validatePositiveSearchAssertions(response, payload, headers, agencyName, softAssert);
                    softAssert.assertAll();
                }
                // Run positive assertions
                String offerId = getSearchOfferId(response, 0);
                Map<String, Object> selectedOffer = getNthOffer(response, 0);

                return new SearchResult(offerId, selectedOffer);

            case 400:
                validateResponse(response, expectedStatusCode, true);
                String expectedMsg = NegativeSearchAssertions.generateExpectedMessage(searchAddPaxScenarioType);
                assertContainsExpectedError(response, expectedMsg,softAssert);
                softAssert.assertAll();
                return null;

            default:
                softAssert.fail("Unhandled status code. Expected 200 or 400 but got: " + expectedStatusCode);
                softAssert.assertAll();
                return null;
        }
    }

    /**
     * Performs the FareConfirm API step:
     *  - Sends selected offer for confirmation
     *  - Validates status code & structure
     *  - Extracts confirmed Offer ID
     *  - Saves response for later validation
     *  - Runs assertions comparing it with Search offer details
     * @param Url FareConfirm endpoint
     * @param headers HTTP headers
     * @param payloadMap FareConfirm payload
     * @param selectedOfferFromSearch Offer details from Search step
     * @param expectedStatusCode Expected status code
     * @return FareConfirm offer ID
     */
    public static String PerformFareConfirm(String Url, Map<String, String> headers,
                                            Map<String, Object> payloadMap,
                                            Map<String, Object> selectedOfferFromSearch, int expectedStatusCode, String searchAddPaxScenarioType) {
        Response response = performPost(Url, payloadMap, headers);
        validateResponse(response, expectedStatusCode, true);

        Map<String, Object> responseMap = response.jsonPath().getMap("$");
        System.out.println(responseMap);

        String fareConfirmOfferId = getFareConfirmOfferId(response);

        saveFareConfirmResponse(fareConfirmOfferId, responseMap);
        selectedOfferFromSearch.put("fareConfirmOfferId", fareConfirmOfferId);
        if (Objects.equals(searchAddPaxScenarioType, "pass")) {
            validateFareConfirm(response, selectedOfferFromSearch, headers);
        }
        return fareConfirmOfferId;
    }

    /**
     * Performs the AddPax API step:
     *  - Sends passenger details
     *  - Validates status code & response
     *  - Returns generated Offer ID
     *
     * @param Url AddPax endpoint
     * @param headers HTTP headers
     * @param payloadMap AddPax payload
     * @param expectedStatusCode Expected HTTP status code
     * @return AddPax offer ID
     */
    public static String PerformAddPax(String Url, Map<String, String> headers, Map<String, Object> payloadMap, int expectedStatusCode, String addPaxFlow,String scenarioType) {
        Response response = performPost(Url, payloadMap, headers);
        SoftAssert softAssert = new SoftAssert();
        if (Objects.equals(addPaxFlow, "fail")) {
          //  validateResponse(response, expectedStatusCode, true);
            String expectedMsg = NegativeAddPaxAssertions.generateExpectedMessage(scenarioType);
            assertContainsExpectedError(response,expectedMsg,softAssert);
            softAssert.assertAll();
            return null;
        }
        validateResponse(response, expectedStatusCode, true);
        return getAddPaxOfferId(response);

    }

    /**
     * Performs the Book API step:
     *  - Sends booking request
     *  - Validates status code & response structure
     *  - Loads AddPax payload & FareConfirm data for deep validation
     *  - Runs booking assertions against prior steps
     *  - Returns booking info (PNR, ticket numbers, etc.)
     *
     * @param url Book endpoint
     * @param headers HTTP headers
     * @param payloadMap Booking payload
     * @param expectedStatusCode Expected HTTP status code
     * @param selectedOfferFromSearch Offer data from Search
     * @param searchPayload Original search payload
     * @param fareConfirmId FareConfirm ID
     * @return Booking info map
     */
    public static Map<String, Object> PerformBook(String url, Map<String, String> headers, Map<String, Object> payloadMap,
                                                  int expectedStatusCode, Map<String, Object> selectedOfferFromSearch,
                                                  Map<String, Object> searchPayload, String fareConfirmId) {
        Response response = performPost(url, payloadMap, headers);
        validateResponse(response, expectedStatusCode, true);

        Map<String, Object> bookResponse = response.jsonPath().getMap("$");
        Map<String, Object> addPaxPayload = AddPaxPayload(searchPayload, fareConfirmId);
        Map<String, Object> fareConfirmResponse = loadFareConfirmResponse(fareConfirmId);

        SoftAssert softAssert = new SoftAssert();
        validateBookingResponse(response, bookResponse, fareConfirmResponse, addPaxPayload, headers, selectedOfferFromSearch, softAssert);
        softAssert.assertAll();

        return getBookingInfo(response);
    }

    /**
     * Performs the BookAfterHold API step:
     *  - Confirms a held booking
     *  - Validates status code & structure
     *  - Compares Retrieve data against Book step
     *  - Returns final booking info
     */
    public static Map<String, Object> PerformBookAfterHold(String Url, Map<String, String> headers, Map<String, Object> payloadMap, int expectedStatusCode, Map<String, Object> bookMap) {
        Response response = performPost(Url, payloadMap, headers);
        validateResponse(response, expectedStatusCode, true);

        Map<String, Object> retrieveMap = getBookingInfo(response);
        SoftAssert softAssert = new SoftAssert();
        validateRetrievePNR(bookMap, retrieveMap, softAssert);
        softAssert.assertAll();

        return getBookingInfo(response);
    }

    /**
     * Performs the Retrieve API step:
     *  - Retrieves booking details
     *  - Validates status code & structure
     *  - Compares Retrieve data against Book step
     */
    public static Map<String, Object> PerformRetrieve(String url, Map<String, String> headers, Map<String, Object> payloadMap, int expectedStatusCode, Map<String, Object> bookMap) {
        Response response = performPost(url, payloadMap, headers);
        validateResponse(response, expectedStatusCode, true);

        Map<String, Object> retrieveMap = getBookingInfo(response);
        SoftAssert softAssert = new SoftAssert();
        validateRetrievePNR(bookMap, retrieveMap, softAssert);
        softAssert.assertAll();

        return retrieveMap;
    }

}