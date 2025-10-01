package Utils.Assertions;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.restassured.path.json.JsonPath;
import io.restassured.response.Response;
import org.testng.asserts.SoftAssert;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import static Utils.Helper.HelperCurrencyValidator.validateCurrencies;
import static Utils.Helper.HelperGetResponse.*;
import static Utils.Helper.HelperPassengerBreakdown.validateOfferJourneys;
import static Utils.Helper.HelperPassengerBreakdown.validatePassengerReferences;
import static Utils.Helper.HelperPriceValidator.validatePriceFields;
import static Utils.Helper.HelperTaxFeeValidator.*;

/**
    * Assertion methods for validating the structure and content of a search API response.
    */
public class PositiveSearchAssertions {

    /**
     * Entry point to execute all relevant validations on a Search API response.
     */
    protected static void validatePositiveSearchAssertions(Response response, Map<String, Object> payload, Map<String, String> headers, String agencyName , SoftAssert softAssert) {
        validateNumberOfStops(response, softAssert);
        validateAllSegmentDetailsPerPassenger(response, payload, softAssert);
        validateOfferJourneyCount(response, payload, softAssert);
        validatePassengerTypes(response, payload, softAssert);
        ValidateUniqueTaxCodesPerPassenger(response, softAssert);
        validateAllOffersPassengerFareBreakdowns(response, softAssert);
        validateAllOffersTotalPriceDetails(response, softAssert);
        validateAllOffersPricingDetails(response, softAssert);
        validateAllReferencesOffers(response, softAssert);
        validateCurrencies(response, headers, "offers", softAssert);
        validateRbdNotNull(response, softAssert);
        validateOfferIdUniqueness(response,softAssert);
        validateOfferSegmentsExistAndAreNonOverlapping(response,softAssert);
        validateSegmentChainingPerJourney(response,payload,softAssert);
        validateOfferIdContainsSupplier(response,agencyName,softAssert);
        validateOffersSortedByTotalAmount(response,softAssert);
        validateOffersAreUnique(response,softAssert);
        validateNoNullValuesInSearchResponse(response,softAssert);
    }

    /**
     * Validates the API response status code and optionally prints the full response.
     *
     * @param response           The API response object.
     * @param expectedStatusCode The HTTP status code expected from the response.
     * @param printResponse      If true, the method will pretty-print the full JSON/XML body.
     */
    protected static void validateResponse(Response response, int expectedStatusCode, boolean printResponse) {
        if (printResponse) {
            System.out.println("\n📩 Full API Response:");
            response.prettyPrint();
        }

        int actualStatusCode = response.getStatusCode();
        System.out.println("🔍 Validating Response → Expected Status: "
                + expectedStatusCode
                + " | Actual Status: "
                + actualStatusCode);

        response.then().statusCode(expectedStatusCode);
    }

    /**
     * TC.1: Validates that the number of stops in each journey equals
     *       (number of segments - 1).
     * Why: A journey with N segments should always have N-1 stops.
     *
     * @param response   API response containing journey details.
     * @param softAssert SoftAssert instance to record assertion results without stopping execution.
     */
    private static void validateNumberOfStops(Response response, SoftAssert softAssert) {
        System.out.println("\n⛔ === TC.1: Validating Number of Stops ===\n");
        JsonPath jsonPath = response.jsonPath();
        Map<String, Map<String, Object>> journeys = jsonPath.getMap("journeys");

        if (journeys == null || journeys.isEmpty()) {
            System.out.println("⚠️ No journeys found.");
            return;
        }

        journeys.forEach((journeyId, journey) -> {
            int expectedStops = ((List<?>) journey.get("segmentReferenceIds")).size() - 1;
            int actualStops = ((Integer) journey.getOrDefault("numberOfStops", -1));

            softAssert.assertEquals(
                    actualStops,
                    expectedStops,
                    String.format("❌TC.1 Journey %s: Expected %d stops, found %d", journeyId, expectedStops, actualStops)
            );
        });

        System.out.println("\n✅ TC.1: Number of Stops validation completed.");
    }

    /**
     * TC.2: Validates that each passenger has at least as many segment details as
     *       the search criteria requires (≥ number of requested journeys).
     * Why: If the request is for multi-leg travel, every passenger's fare breakdown
     *      must contain all segment details for every leg.
     * @param response   API response containing offers and passenger breakdowns.
     * @param payload    The request payload (used to determine expected journey/segment count).
     * @param softAssert SoftAssert instance to record assertion results.
     */
    private static void validateAllSegmentDetailsPerPassenger(Response response, Map<String, Object> payload, SoftAssert softAssert) {
        List<Map<String, Object>> searchCriteria = (List<Map<String, Object>>) payload.get("searchCriteria");
        int expectedSegments = searchCriteria.size();
        JsonPath jsonPath = response.jsonPath();
        List<Map<String, Object>> offers = jsonPath.getList("offers");

        System.out.printf("\n=== ✈ TC.2: Validating %d Offers' Segment Details Against Search Criteria Size%n===\n", offers.size());

        for (int offerIndex = 0; offerIndex < offers.size(); offerIndex++) {
            List<Map<String, Object>> paxList = jsonPath.getList("offers[" + offerIndex + "].passengerFareBreakdown");

            for (int paxIndex = 0; paxIndex < paxList.size(); paxIndex++) {
                List<Map<String, Object>> segments = jsonPath.getList(
                        "offers[" + offerIndex + "].passengerFareBreakdown[" + paxIndex + "].segmentDetails"
                );
                String paxType = jsonPath.getString(
                        "offers[" + offerIndex + "].passengerFareBreakdown[" + paxIndex + "].passengerTypeCode"
                );

                softAssert.assertTrue(
                        segments.size() >= expectedSegments,
                        String.format("❌TC.2 Offer[%d] Pax[%d] (%s): Expected ≥ %d segments, but found %d",
                                offerIndex, paxIndex, paxType, expectedSegments, segments.size())
                );
            }
        }

        System.out.println("\n✅ TC.2: Segment details validated.");
    }

    /**
     * TC.3: Validates that each offer contains the same number of journeys as requested.
     * Why: The number of journeys in an offer should match the original search criteria
     *      (e.g., round-trip request should have 2 journeys).
     *
     * @param response   API response containing offer details.
     * @param payload    The request payload (to determine expected journey count).
     * @param softAssert SoftAssert instance to record assertion results.
     */
    private static void validateOfferJourneyCount(Response response, Map<String, Object> payload, SoftAssert softAssert) {
        List<Map<String, Object>> searchCriteria = (List<Map<String, Object>>) payload.get("searchCriteria");
        int expectedJourneyCount = searchCriteria.size();
        System.out.println("\n=== 🛫 TC.3: Validating Offer Journey Count ===\n");

        JsonPath jsonPath = response.jsonPath();
        List<Map<String, Object>> offers = jsonPath.getList("offers");

        for (int i = 0; i < offers.size(); i++) {
            List<String> journeyRefs = jsonPath.getList("offers[" + i + "].journeys");
            int actualCount = journeyRefs != null ? journeyRefs.size() : 0;

            softAssert.assertEquals(
                    actualCount,
                    expectedJourneyCount,
                    String.format("❌TC.3 Offer[%d]: Expected %d journeys, found %d", i, expectedJourneyCount, actualCount)
            );
        }

        System.out.println("\n✅ TC.3: Offer journey count validated.");
    }

    /**
     * TC.4: Validates passenger type counts per offer against the request payload,
     *       and ensures there are no duplicate passenger type codes in the response.
     * Why: Passenger counts and types (e.g., ADT, CHD, INF) must match the request,
     *      and there should be no duplicate type entries per offer.
     *
     * @param response   API response containing passenger breakdown.
     * @param payload    The request payload (used to validate counts and types).
     * @param softAssert SoftAssert instance to record assertion results.
     */
    private static void validatePassengerTypes(Response response, Map<String, Object> payload, SoftAssert softAssert) {
        JsonPath jsonPath = response.jsonPath();
        List<Map<String, Object>> offers = jsonPath.getList("offers");
        List<Map<String, Object>> payloadPassengers = (List<Map<String, Object>>) payload.get("passengers");

        // Build expected passenger type → count mapping from payload
        Map<String, Integer> expectedTypeCountMap = new HashMap<>();
        for (Map<String, Object> pax : payloadPassengers) {
            String type = (String) pax.get("passengerTypeCode");
            Integer count = ((Number) pax.get("count")).intValue();
            expectedTypeCountMap.put(type, count);
        }

        System.out.println("\n=== 🧍‍♂️ TC.4: Validating Passenger Types for " + offers.size() + " offers===\n");

        for (int i = 0; i < offers.size(); i++) {
            List<Map<String, Object>> fareBreakdownList = jsonPath.getList("offers[" + i + "].passengerFareBreakdown");

            Map<String, Integer> actualTypeCountMap = new HashMap<>();
            Set<String> seenTypes = new HashSet<>(); // Track passenger types to detect duplicates

            for (Map<String, Object> breakdown : fareBreakdownList) {
                String type = (String) breakdown.get("passengerTypeCode");

                // Duplicate passenger type check
                if (!seenTypes.add(type)) {
                    softAssert.fail(String.format("❌TC.4 Offer[%d]: Duplicate passenger type code found: %s", i, type));
                }

                Integer count = ((Number) breakdown.get("numberOfPassengers")).intValue();
                actualTypeCountMap.put(type, count);
            }

            // Validate passenger type counts match the expected payload values
            for (Map.Entry<String, Integer> entry : expectedTypeCountMap.entrySet()) {
                String expectedType = entry.getKey();
                int expectedCount = entry.getValue();

                if (actualTypeCountMap.containsKey(expectedType)) {
                    int actualCount = actualTypeCountMap.get(expectedType);
                    softAssert.assertEquals(
                            actualCount,
                            expectedCount,
                            String.format("❌ TC.4 Offer[%d]: PassengerType %s count mismatch", i, expectedType)
                    );
                } else {
                    softAssert.fail("❌ TC.4 Offer[" + i + "]: Missing passenger type: " + expectedType);
                }
            }
        }

        System.out.println("\n✅ TC.4: Passenger type/count + duplicate type check completed.");
    }

    /**
     * TC.5: Ensure tax codes per passenger are unique within their offer.
     * This test checks each passenger's "taxesAndFees" list in every offer
     * to confirm that no tax code appears more than once for that passenger.
     * @param response   The API response containing offers and fare breakdowns
     * @param softAssert SoftAssert instance for accumulating assertion results
     */
    private static void ValidateUniqueTaxCodesPerPassenger(Response response, SoftAssert softAssert) {
        // Extract list of offers from the JSON response
        List<Map<String, Object>> offers = response.jsonPath().getList("offers");
        System.out.println("\n===🧾 TC.5: Validating Unique Tax Codes Per Passenger ===\n");

        // Iterate over each offer
        for (int offerIndex = 0; offerIndex < offers.size(); offerIndex++) {
            // Extract passenger fare breakdown for the current offer
            List<Map<String, Object>> breakdowns = (List<Map<String, Object>>) offers.get(offerIndex).get("passengerFareBreakdown");
            if (breakdowns == null) continue; // Skip if breakdowns are missing

            // Validate each passenger's tax codes
            for (Map<String, Object> breakdown : breakdowns) {
                String paxType = (String) breakdown.get("passengerTypeCode");
                List<Map<String, Object>> taxes = (List<Map<String, Object>>) breakdown.get("taxesAndFees");

                if (taxes == null) {
                    System.out.printf("⚠️TC.5 Warning: 'taxesAndFees' is null for paxType '%s' in offer[%d]%n", paxType, offerIndex);
                    continue; // Skip passenger without tax details
                }

                // Track seen tax codes to detect duplicates
                Set<String> seenCodes = new HashSet<>();
                for (Map<String, Object> tax : taxes) {
                    String code = (String) tax.get("code");
                    if (code != null && !seenCodes.add(code)) {
                        // Duplicate found → fail the soft assertion
                        softAssert.fail(String.format("❌ TC.5 Duplicate tax code '%s' found for paxType '%s' in offer[%d]", code, paxType, offerIndex));
                    }
                }
            }
        }

        System.out.println("\n✅ TC.5: Tax code uniqueness validated.");
    }

    /**
         * TC.7: Validate passenger fare breakdown amounts for all offers.
         * Ensures passengerTotalAmount = passengerBaseAmount + passengerTaxesAmount for each passenger type.
         *
         * @param response   The API response containing offers
         * @param softAssert SoftAssert instance for accumulating assertion results
         */
    private static void validateAllOffersPassengerFareBreakdowns(Response response, SoftAssert softAssert) {
            JsonPath jsonPath = response.jsonPath();
            List<Map<String, Object>> offers = jsonPath.getList("offers");

            if (offers == null || offers.isEmpty()) {
                softAssert.fail("❌ TC.7 No offers found in the response.");
                return;
            }

            System.out.println("\n💰 === TC.7: Validating Passenger Fare Breakdowns for All " + offers.size() + " Offers ===\n");

            // Iterate through all offers
            for (int offerIndex = 0; offerIndex < offers.size(); offerIndex++) {
                System.out.println("\n\t📦 ✔ Validating Offer Index: " + offerIndex);

                // Get passenger breakdowns for this offer
                List<Map<String, Object>> passengerBreakdowns =
                        jsonPath.getList("offers[" + offerIndex + "].passengerFareBreakdown");

                if (passengerBreakdowns == null || passengerBreakdowns.isEmpty()) {
                    softAssert.fail("❌ TC.7 No passenger fare breakdown found in offer " + offerIndex);
                    continue;
                }

                // Check each passenger's fare calculation
                for (Map<String, Object> passenger : passengerBreakdowns) {
                    String type = (String) passenger.get("passengerTypeCode");

                    // Extract amounts as BigDecimals (unRounded first!)
                    BigDecimal actualTotal = getAmountOrZero(passenger.get("passengerTotalAmount"));
                    BigDecimal actualBase  = getAmountOrZero(passenger.get("passengerBaseAmount"));
                    BigDecimal actualTax   = getAmountOrZero(passenger.get("passengerTaxesAmount"));

                    // Expected total = base + tax (unRounded sum)
                    BigDecimal expectedTotal = actualBase.add(actualTax);

                    // Apply rounding only once at the comparison step (currency = 2 decimals)
                    BigDecimal roundedActualTotal   = actualTotal.setScale(2, RoundingMode.HALF_UP);
                    BigDecimal roundedExpectedTotal = expectedTotal.setScale(2, RoundingMode.HALF_UP);

                    // Log comparison
                    System.out.printf(
                            "\t🧾 ✔ Passenger Type: %s\n\t\tExpected Total: %.2f = Base(%.4f) + Tax(%.4f)\n\t\tActual Total:   %.2f\n",
                            type,
                            roundedExpectedTotal.doubleValue(),
                            actualBase.doubleValue(),
                            actualTax.doubleValue(),
                            roundedActualTotal.doubleValue()
                    );

                    // Soft assert to compare expected vs actual totals
                    softAssert.assertEquals(
                            roundedActualTotal,
                            roundedExpectedTotal,
                            String.format("❌ TC.7 Mismatch in [Offer %d] total fare calculation for passenger type '%s'", offerIndex, type)
                    );
                }

                System.out.println("✅ Offer " + offerIndex + " fare breakdown validated.\n");
            }

            System.out.println("\n✅ TC.7: completed! Total Prices Calculated and Validated for All Offers Successfully.\n");
        }

    /**
     * TC.8: Validate total price details per offer.
     * Ensures priceDetails.totalAmount = priceDetails.baseAmount + priceDetails.taxesAmount.
     * @param response   The API response containing offers
     * @param softAssert SoftAssert instance for accumulating assertion results
     */
    private static void validateAllOffersTotalPriceDetails(Response response, SoftAssert softAssert) {
        JsonPath jsonPath = response.jsonPath();
        List<Map<String, Object>> offers = jsonPath.getList("offers");

        if (offers == null || offers.isEmpty()) {
            softAssert.fail("❌TC.8 No offers found in the response.");
            return;
        }

        System.out.println("\n💵 === TC.8: Validating Total Price Details for All " + offers.size() + " Offers ===\n");

        // Validate each offer's total calculation
        for (int offerIndex = 0; offerIndex < offers.size(); offerIndex++) {
            Map<String, Object> offer = offers.get(offerIndex);
            Map<String, Object> priceDetails = (Map<String, Object>) offer.get("priceDetails");

            if (priceDetails == null) {
                softAssert.fail("❌TC.8 priceDetails is missing in offer " + offerIndex);
                continue;
            }

            // Extract base, taxes, and total amounts
            Map<String, Object> totalAmountMap = (Map<String, Object>) priceDetails.get("totalAmount");
            Map<String, Object> baseAmountMap = (Map<String, Object>) priceDetails.get("baseAmount");
            Map<String, Object> taxesAmountMap = (Map<String, Object>) priceDetails.get("taxesAmount");

            BigDecimal baseAmount = new BigDecimal(String.valueOf(baseAmountMap.get("amount")));
            BigDecimal taxesAmount = new BigDecimal(String.valueOf(taxesAmountMap.get("amount")));
            BigDecimal actualTotal = new BigDecimal(String.valueOf(totalAmountMap.get("amount")));
            BigDecimal expectedTotal = baseAmount.add(taxesAmount);

            // Assertion
            softAssert.assertEquals(actualTotal.setScale(2, RoundingMode.HALF_UP),
                    expectedTotal.setScale(2, RoundingMode.HALF_UP),
                    "❌ TC.8 totalAmount mismatch in offer " + offerIndex +
                            " → expected " + expectedTotal + " but found " + actualTotal);

            // Log breakdown
            System.out.printf("\t📦 Offer %d ➤ Base: %.2f + Taxes: %.2f = Expected: %.2f | Actual: %.2f%n",
                    offerIndex, baseAmount, taxesAmount, expectedTotal, actualTotal
            );
        }

        System.out.println("\n✅ TC.8: completed! All offers passed total price validation.");
    }

    /**
     * TC.9: Validate priceDetails aggregation and taxes/fees at various levels.
     * Performs multiple validations:
     *  - Aggregated price fields match passenger-level breakdowns
     *  - Taxes are consistent per passenger
     *  - Total taxes in priceDetails match aggregated passenger taxes
     * @param response   The API response containing offers
     * @param softAssert SoftAssert instance for accumulating assertion results
     */
    private static void validateAllOffersPricingDetails(Response response, SoftAssert softAssert) {
        JsonPath jsonPath = response.jsonPath();
        List<Map<String, Object>> offers = jsonPath.getList("offers");

        System.out.printf("\n=== TC.9: Validating Pricing Details for %d offers ===\n", offers.size());

        // Validate each offer in sequence
        for (int offerOrder = 0; offerOrder < offers.size(); offerOrder++) {
            System.out.printf("\n🔍 TC9.1.%d: Validating Offer Pricing at Index: %d \n", offerOrder, offerOrder);

            // Step 1: Get passenger breakdowns for the current offer
            List<Map<String, Object>> passengerBreakdowns = getPassengerBreakdowns(jsonPath, offerOrder);

            // Step 2: Map priceDetails fields to corresponding passenger fields
            Map<String, String> priceFieldMapping = getPriceFieldMapping();

            // Step 3: Validate aggregated base, tax, and total fields
            System.out.println("\n💰 TC9.2." + offerOrder + ": Validating Aggregated Price Fields...\n");
            validatePriceFields(jsonPath, offerOrder, passengerBreakdowns, priceFieldMapping, softAssert);

            // Step 4: Validate individual taxes per passenger type
            System.out.println("\n🧾 TC9.3." + offerOrder + ": Validating taxesAndFees per Passenger Type...\n");
            validateTaxesAndFeesPerPassenger(passengerBreakdowns, offerOrder,softAssert);

            // Step 5: Validate aggregated total taxes in priceDetails
            System.out.println("\n📊 TC9.4." + offerOrder + ": Validating Total taxesAndFees from PriceDetails...\n");
            validateTotalTaxesAndFeesInPriceDetails(jsonPath, offerOrder, softAssert);
        }

        System.out.println("\n✅ TC.9: completed! Validated all " + offers.size() + " offers successfully.");
    }

    /**
     * TC.10 + [WT][Search_API][Response][Offer][offerJourneys]
     * Validates that all references in each offer exist in their respective root-level objects.
     * Specifically checks:
     *  - All segmentReferenceIds exist in "segments" map.
     *  - All priceClass references exist in "priceClasses" map.
     *  - All baggageDetails references exist in "baggageDetails" map.
     *  - All journey IDs in offerJourneys exist in "journeys" map.
     */
    private static void validateAllReferencesOffers(Response response, SoftAssert softAssert) {
        JsonPath jsonPath = response.jsonPath();

        // Extract top-level reference objects from response
        List<Map<String, Object>> offers = jsonPath.getList("offers");
        Map<String, Object> segments = jsonPath.getMap("segments");
        Map<String, Object> priceClasses = jsonPath.getMap("priceClasses");
        Map<String, Map<String, String>> baggageDetails = jsonPath.getMap("baggageDetails");
        Map<String, Object> rootJourneys = jsonPath.getMap("journeys");

        System.out.println("\n🔍 ===TC10: Validating all References IDs for " +
                (offers != null ? offers.size() : 0) + " offers ===\n");
        System.out.printf("\t📦 Segments: %d | PriceClasses: %d | BaggageDetails: %d | Journeys: %d%n",
                segments != null ? segments.size() : 0,
                priceClasses != null ? priceClasses.size() : 0,
                baggageDetails != null ? baggageDetails.size() : 0,
                rootJourneys != null ? rootJourneys.size() : 0);

        // Fail immediately if there are no offers
        if (offers == null || offers.isEmpty()) {
            softAssert.fail("❌TC.10 No offers found in response.");
            return;
        }

        // Validate each offer
        for (int offerIndex = 0; offerIndex < offers.size(); offerIndex++) {
            Map<String, Object> offer = offers.get(offerIndex);
            System.out.printf("\t\t🧾 Validating Offer Index: %d%n", offerIndex);

            // Validate passenger-related references
            validatePassengerReferences(
                    offer,
                    segments != null ? segments : Collections.emptyMap(),
                    priceClasses != null ? priceClasses : Collections.emptyMap(),
                    baggageDetails != null ? baggageDetails : Collections.emptyMap(),
                    softAssert,
                    offerIndex
            );

            // Validate journey-related references
            validateOfferJourneys(
                    offer,
                    rootJourneys != null ? rootJourneys : Collections.emptyMap(),
                    softAssert,
                    offerIndex
            );
        }

        System.out.printf("\n✅ TC.10: completed! Reference Validation for %d Offers%n%n", offers.size());
    }

    /**
     * TC.11: Validates that the RBD (Reservation Booking Designator) field is present and not empty
     * for every segmentDetail in passengerFareBreakdown of each offer.
     */
    private static void validateRbdNotNull(Response response, SoftAssert softAssert) {
        JsonPath jsonPath = response.jsonPath();
        List<Map<String, Object>> offers = jsonPath.getList("offers");
        System.out.println("\n🔍 ===TC11: Validating all RBD values are Not Null for " +
                (offers != null ? offers.size() : 0) + " offers ===\n");

        if (offers == null || offers.isEmpty()) {
            softAssert.fail("❌TC.11 No offers found in search response.");
            return;
        }

        // Iterate through all offers and their passengerFareBreakdowns
        for (int i = 0; i < offers.size(); i++) {
            List<Map<String, Object>> fareBreakdowns = jsonPath.getList("offers[" + i + "].passengerFareBreakdown");
            if (fareBreakdowns != null) {
                for (int j = 0; j < fareBreakdowns.size(); j++) {
                    List<Map<String, Object>> segmentDetails = jsonPath.getList(
                            String.format("offers[%d].passengerFareBreakdown[%d].segmentDetails", i, j));
                    if (segmentDetails != null) {
                        for (int k = 0; k < segmentDetails.size(); k++) {
                            String rbd = jsonPath.getString(
                                    String.format("offers[%d].passengerFareBreakdown[%d].segmentDetails[%d].rbd", i, j, k));
                            softAssert.assertNotNull(rbd,
                                    String.format("❌TC.11 RBD is null at offers[%d].passengerFareBreakdown[%d].segmentDetails[%d]", i, j, k));
                            softAssert.assertFalse(rbd.trim().isEmpty(),
                                    String.format("❌TC.11 RBD is empty at offers[%d].passengerFareBreakdown[%d].segmentDetails[%d]", i, j, k));
                        }
                    }
                }
            }
        }
        System.out.println("\n✅ TC.11: completed! RBD validation completed for all " +
                offers.size() + " offers in Search response.\n");
    }

    /**
     * TC.12: Validates that all offerId values in the offers[] array are unique.
     */
    private static void validateOfferIdUniqueness(Response response, SoftAssert softAssert) {
        JsonPath jsonPath = response.jsonPath();
        List<Map<String, Object>> offers = jsonPath.getList("offers");
        List<String> offerIds = jsonPath.getList("offers.offerId");

        System.out.println("\n🔍 === TC.12: Validate OfferId uniqueness in " + (offers != null ? offers.size() : 0) + " offers ===\n");

        Set<String> uniqueIds = new HashSet<>();

        for (String offerId : offerIds) {
            if (!uniqueIds.add(offerId)) {
                softAssert.fail("❌TC.12 Duplicate offerId found: " + offerId);
            }
        }

        if (uniqueIds.size() == offerIds.size()) {
            System.out.println("\n✅ TC.12: completed! All offerIds are unique.");
        } else {
            System.out.println("❌TC.12 Duplicate offerIds detected in search response.");
        }
    }

    /**
     * TC.13: Validates that the brandCode field is present and not null/empty
     * for each offer in the search response.
     * TC.13: validate Offer Segments Exist And Are Non Overlapping
     */
    private static void validateOfferSegmentsExistAndAreNonOverlapping(Response response, SoftAssert softAssert) {
        JsonPath jsonPath = response.jsonPath();
        List<Map<String, Object>> offers = jsonPath.getList("offers");
        Map<String, Map<String, Object>> segmentsMap = jsonPath.getMap("segments");

        System.out.println("\n🔍===TC.13: validate Offer Segments Exist And Are Non Overlapping " + offers.size() + " offers ===\n");

        if (offers.isEmpty()) {
            String message = "❌TC.13 No offers found in the response.";
            System.out.println(message);
            softAssert.fail(message);
            return;
        }

        DateTimeFormatter formatter = DateTimeFormatter.ISO_LOCAL_DATE_TIME;

        for (int offerIndex = 0; offerIndex < offers.size(); offerIndex++) {
            List<Map<String, Object>> segmentDetailsList = jsonPath.getList("offers[" + offerIndex + "].passengerFareBreakdown[0].segmentDetails");

            if (segmentDetailsList == null || segmentDetailsList.isEmpty()) {
                String message = "❌TC.13 Segment details list is null or empty for offer[" + offerIndex + "]";
                System.out.println(message);
                softAssert.fail(message);
                continue;
            }

            for (int i = 0; i < segmentDetailsList.size(); i++) {
                Map<String, Object> segmentDetail = segmentDetailsList.get(i);
                String segmentRefId = (String) segmentDetail.get("segmentReferenceId");

                if (!segmentsMap.containsKey(segmentRefId)) {
                    String message = "❌TC.13 Segment reference ID [" + segmentRefId + "] not found in segments map for offer[" + offerIndex + "]";
                    System.out.println(message);
                    softAssert.fail(message);
                    continue;
                }

                Map<String, Object> currentSegment = segmentsMap.get(segmentRefId);
                String depTimeStr = (String) currentSegment.get("departureDateTime");
                String arrTimeStr = (String) currentSegment.get("arrivalDateTime");

                if (depTimeStr == null || arrTimeStr == null) {
                    String message = "❌TC.13 Missing departure or arrival time for segment [" + segmentRefId + "] in offer[" + offerIndex + "]";
                    System.out.println(message);
                    softAssert.fail(message);
                    continue;
                }

                try {
                    LocalDateTime depTime = LocalDateTime.parse(depTimeStr, formatter);
                    LocalDateTime arrTime = LocalDateTime.parse(arrTimeStr, formatter);

                    if (!arrTime.isAfter(depTime)) {
                        String message = "❌TC.13 Segment [" + segmentRefId + "] has invalid timing: arrival [" + arrTimeStr + "] is not after departure [" + depTimeStr + "]";
                        System.out.println(message);
                        softAssert.fail(message);
                    }

                    if (i < segmentDetailsList.size() - 1) {
                        String nextSegmentRefId = (String) segmentDetailsList.get(i + 1).get("segmentReferenceId");

                        if (!segmentsMap.containsKey(nextSegmentRefId)) {
                            String message = "❌TC.13 Next segment reference ID [" + nextSegmentRefId + "] not found in segments map for offer[" + offerIndex + "]";
                            System.out.println(message);
                            softAssert.fail(message);
                            continue;
                        }

                        Map<String, Object> nextSegment = segmentsMap.get(nextSegmentRefId);
                        String nextDepTimeStr = (String) nextSegment.get("departureDateTime");

                        if (nextDepTimeStr == null) {
                            String message = "❌TC.13 Missing departure time for next segment [" + nextSegmentRefId + "] in offer[" + offerIndex + "]";
                            System.out.println(message);
                            softAssert.fail(message);
                            continue;
                        }

                        LocalDateTime nextDepTime = LocalDateTime.parse(nextDepTimeStr, formatter);

                        if (!arrTime.isBefore(nextDepTime)) {
                            String message = String.format(
                                    "❌TC.13 Segment [%s] in offer[%d] (segment %d of %d) arrival [%s] overlaps or matches next segment [%s] departure [%s]",
                                    segmentRefId, offerIndex, i + 1, segmentDetailsList.size(), arrTimeStr, nextSegmentRefId, nextDepTimeStr
                            );
                            System.out.println(message);
                            softAssert.fail(message);
                        }
                    }
                } catch (Exception e) {
                    String message = "❌TC.13 Error parsing date for segment [" + segmentRefId + "] in offer[" + offerIndex + "]: " + e.getMessage();
                    System.out.println(message);
                    softAssert.fail(message);
                }
            }
        }

        System.out.println("\n✅ TC.13: completed! check all Offer Segments Exist And Are Non Overlapping " + offers.size() + " offers in Search response. Successfully.\n");
    }

    /**
     * TC.14: validate Segment Chaining Per Journey "Direct & Transit"
     */
    private static void validateSegmentChainingPerJourney(Response response, Map<String, Object> payload, SoftAssert softAssert) {
        System.out.println("\n=== TC.14: 🔍 Starting segment chaining validation per journey===\n");

        JsonPath jsonPath = response.jsonPath();

        // Load full search criteria list
        List<Map<String, String>> searchCriteriaList = (List<Map<String, String>>) payload.get("searchCriteria");

        // Get segments and journeys maps
        Map<String, Map<String, Object>> segmentsMap = jsonPath.getMap("segments");
        Map<String, Map<String, Object>> journeysMap = jsonPath.getMap("journeys");

        int journeyIndex = 0;

        for (Map.Entry<String, Map<String, Object>> journeyEntry : journeysMap.entrySet()) {
            journeyIndex++;
            Map<String, Object> journey = journeyEntry.getValue();
            int numberOfStops = (int) journey.get("numberOfStops");
            List<String> segmentReferenceIds = (List<String>) journey.get("segmentReferenceIds");

            System.out.println("\n✈️ Validating Journey #" + journeyIndex + " with " + numberOfStops + " stops");

            if (segmentReferenceIds == null || segmentReferenceIds.isEmpty()) {
                softAssert.fail("❌TC.14 Journey #" + journeyIndex + " has no segmentReferenceIds.");
                continue;
            }

            // Build and sort segment list
            List<Map<String, Object>> segments = new ArrayList<>();
            for (String segId : segmentReferenceIds) {
                Map<String, Object> segObj = segmentsMap.get(segId);
                if (segObj == null) {
                    softAssert.fail("❌TC.14 Segment ID " + segId + " not found in segments map.");
                    continue;
                }
                segments.add(segObj);
            }
            segments.sort(Comparator.comparing(seg -> seg.get("departureDateTime").toString()));

            // Log each segment
            for (int i = 0; i < segments.size(); i++) {
                String origin = segments.get(i).get("origin").toString();
                String destination = segments.get(i).get("destination").toString();
                System.out.println("\t🔗 Segment " + (i + 1) + ": " + origin + " → " + destination);
            }

            // Detect expected origin/destination by matching segment direction to search criteria
            String actualOrigin = segments.get(0).get("origin").toString();
            String actualDestination = segments.get(segments.size() - 1).get("destination").toString();

            String expectedOrigin = actualOrigin;  // default
            String expectedDestination = actualDestination;  // default

            for (Map<String, String> criteria : searchCriteriaList) {
                String scOrigin = criteria.get("origin");
                String scDestination = criteria.get("destination");
                if (scOrigin.equals(actualOrigin) && scDestination.equals(actualDestination)) {
                    expectedOrigin = scOrigin;
                    expectedDestination = scDestination;
                    break;
                }
            }

            System.out.println("\t📥 Expected Origin: " + expectedOrigin + ", Expected Destination: " + expectedDestination);
            System.out.println("\t📤 Actual Origin: " + actualOrigin + ", Actual Destination: " + actualDestination);

            // Non-stop validation
            if (numberOfStops == 0) {
                if (segments.size() != 1) {
                    softAssert.fail("Expected 1 segment for non-stop journey, found " + segments.size());
                    continue;
                }

                if (!expectedOrigin.equals(actualOrigin)) {
                    System.out.println("\t❌TC.14 Origin mismatch.");
                } else {
                    System.out.println("\t✅TC.14 Origin matches.");
                }

                if (expectedDestination.equals(actualDestination)) {
                    System.out.println("\t✅TC.14 Destination matches.");
                } else {
                    System.out.println("\t❌TC.14 Destination mismatch.");
                }

                softAssert.assertEquals(actualOrigin, expectedOrigin, "Non-stop journey origin mismatch.");
                softAssert.assertEquals(actualDestination, expectedDestination, "Non-stop journey destination mismatch.");

            } else {
                // Multi-stop validation
                System.out.println("\t🔍 Multi-stop check:");
                System.out.println("\t\t🟡 First Segment Origin: " + actualOrigin);
                System.out.println("\t\t🟡 Last Segment Destination: " + actualDestination);
                softAssert.assertEquals(
                        actualOrigin,
                        expectedOrigin,
                        "❌TC.14 Non-stop journey #" + journeyIndex + " origin mismatch. Expected [" + expectedOrigin + "] but found [" + actualOrigin + "]"
                );
                softAssert.assertEquals(actualDestination, expectedDestination, "❌TC.14 Last segment destination mismatch in journey #" + journeyIndex);

                // Segment chaining check
                for (int i = 0; i < segments.size() - 1; i++) {
                    String arrival = segments.get(i).get("destination").toString();
                    String nextDeparture = segments.get(i + 1).get("origin").toString();

                    if (!arrival.equals(nextDeparture)) {
                        System.out.println("\t❌TC.14  Segment chaining mismatch between segment " + (i + 1) + " and " + (i + 2) +
                                ": expected arrival " + arrival + " to match next departure " + nextDeparture);
                    } else {
                        System.out.println("\t✅ Segment chaining OK between segment " + (i + 1) + " and " + (i + 2));
                    }

                    softAssert.assertEquals(arrival, nextDeparture,
                            "❌TC.14 Segment chaining mismatch between segment " + (i + 1) + " and " + (i + 2));
                }
            }

            System.out.println("✅TC.14  Completed validation for Journey #" + journeyIndex);
        }

        System.out.println("\n✅ TC.14: Segment chaining validation completed for all journeys.");
    }

    /**
     * TC.15: Validate decoded offerId contains the agency name (case-insensitive).
     */
    private static void validateOfferIdContainsSupplier(Response response, String agencyName, SoftAssert softAssert) {
        System.out.println("\n=== TC.15: Validate decoded offerId contains agency name: " + agencyName + " ===\n");

        List<Map<String, Object>> offers = response.jsonPath().getList("offers");

        if (offers == null || offers.isEmpty()) {
            softAssert.fail("❌TC.15 No offers found in the response for agency: " + agencyName);
            System.out.println("❌TC.15 No offers found in the response for agency: " + agencyName);
            return;
        }

        System.out.println("Total offers found: " + offers.size());

        for (int i = 0; i < offers.size(); i++) {
            System.out.println("---- Validating offer index: " + i + " ----");

            String offerIdEncoded = (String) offers.get(i).get("offerId");

            if (offerIdEncoded == null) {
                softAssert.fail("❌TC.15 Offer[" + i + "] has null offerId for agency: " + agencyName);
                System.out.println("❌TC.15 Offer[" + i + "] has null offerId for agency: " + agencyName);
                continue;
            }

            System.out.println("Encoded offerId: " + offerIdEncoded);

            try {
                String decoded = new String(Base64.getDecoder().decode(offerIdEncoded), StandardCharsets.UTF_8);
                System.out.println("Decoded offerId: " + decoded);

                if (!decoded.toLowerCase().contains(agencyName.toLowerCase())) {
                    String failMsg = "❌TC.15 Offer[" + i + "] decoded offerId does NOT contain expected agency name '"
                            + agencyName + "'. Decoded value: " + decoded;
                    softAssert.fail(failMsg);
                    System.out.println(failMsg);
                } else {
                    System.out.println("✅TC.15 Offer[" + i + "] decoded offerId contains agency name '" + agencyName + "'.");
                }

            } catch (IllegalArgumentException e) {
                String failMsg = "❌TC.15 Offer[" + i + "] has invalid Base64 encoding: " + offerIdEncoded;
                softAssert.fail(failMsg);
                System.out.println(failMsg);
            }
        }

        System.out.println("\n=== ✅ TC.15 Validation Completed for agency: " + agencyName + " =====");
    }

    /**
     * TC.16: Validate that offers[] are sorted in ascending order (allowing equal values) by totalAmount in priceDetails
     */
    private static void validateOffersSortedByTotalAmount(Response response, SoftAssert softAssert) {
        System.out.println("\n=== TC.16: Start - Validate offers[] sorted by totalAmount (allow equal) ===\n");

        List<Map<String, Object>> offers = response.jsonPath().getList("offers");
        System.out.println("Offers count: " + (offers == null ? 0 : offers.size()));

        if (offers == null || offers.isEmpty()) {
            softAssert.fail("offers[] list is null or empty.");
            System.out.println("❌TC.16 FAIL: offers[] is null or empty");
            return;
        }

        double previousAmount = Double.NEGATIVE_INFINITY;
        for (int offerIndex = 0; offerIndex < offers.size(); offerIndex++) {
            Map<String, Object> offer = offers.get(offerIndex);
            System.out.println("Checking offer index: " + offerIndex);

            Map<String, Object> priceDetails = (Map<String, Object>) offer.get("priceDetails");
            if (priceDetails == null) {
                softAssert.fail("priceDetails is missing for offer index: " + offerIndex);
                System.out.println("❌TC.16 FAIL: priceDetails missing at index " + offerIndex);
                continue;
            }

            Map<String, Object> totalAmount = (Map<String, Object>) priceDetails.get("totalAmount");
            if (totalAmount == null || totalAmount.get("amount") == null) {
                softAssert.fail("totalAmount.amount is missing for offer index: " + offerIndex);
                System.out.println("❌TC.16 FAIL: totalAmount.amount missing at index " + offerIndex);
                continue;
            }

            double currentAmount = ((Number) totalAmount.get("amount")).doubleValue();
            System.out.printf("TC.16 Offer[%d] totalAmount: %.2f | Previous: %.2f%n", offerIndex, currentAmount, previousAmount);

            if (currentAmount < previousAmount) {
                softAssert.fail(String.format(
                        "Offers are not sorted by totalAmount at index %d. Previous: %.2f, Current: %.2f",
                        offerIndex, previousAmount, currentAmount
                ));
                System.out.printf("❌TC.16 FAIL: Sorting error at index %d%n", offerIndex);
            }

            previousAmount = currentAmount;
        }

        System.out.println("\n===✅ TC.16: End Validate offers[] sorted by totalAmount completed ===");
    }

    /**
     * TC.17: Validate that all offers[] JSON objects are unique (no duplicates)
     */
    private static void validateOffersAreUnique(Response response, SoftAssert softAssert) {
        System.out.println("\n=== TC.17: Start - Validate offers[] uniqueness ===\n");

        List<Map<String, Object>> offers = response.jsonPath().getList("offers");
        System.out.println("Offers count: " + (offers == null ? 0 : offers.size()));

        if (offers == null || offers.isEmpty()) {
            softAssert.fail("offers[] list is null or empty.");
            System.out.println("❌TC.17 FAIL: offers[] is null or empty");
            return;
        }

        Set<String> seenOffers = new HashSet<>();
        ObjectMapper mapper = new ObjectMapper();
        // Loop through offers and check price
        for (int i = 0; i < offers.size(); i++) {
            Map<String, Object> offer = offers.get(i);
            System.out.println("Processing offer index: " + i);

            String jsonString;
            try {
                jsonString = mapper.writeValueAsString(offer);
            } catch (JsonProcessingException e) {
                softAssert.fail("Failed to serialize offer at index " + i + ": " + e.getMessage());
                System.out.println("❌TC.17 FAIL: Serialization error at index " + i);
                continue;
            }

            if (!seenOffers.add(jsonString)) {
                softAssert.fail("Duplicate offer found at index " + i);
                System.out.println("❌TC.17 FAIL: Duplicate found at index " + i);
            } else {
                System.out.println("✅TC.17 Offer at index " + i + " is unique");
            }
        }

        System.out.println("\n===✅ TC.17: End Validate offers[] uniqueness ===");
    }

    /**
     * TC.18: Validates that the search API response does not contain null-like values.
     * Null-like values include:
     *  - NULL
     *  - EMPTY STRING ("")
     *  - EMPTY LIST ([])
     *  - EMPTY OBJECT ({})
     * The method recursively scans the JSON, records all issues, sorts them
     * by type and path, then logs them with clear grouping for easier review.
     */
    private static void validateNoNullValuesInSearchResponse(Response response, SoftAssert softAssert) {
        JsonPath jsonPath = response.jsonPath();
        Object root = jsonPath.get(); // Root of the JSON
        List<String> issues = new ArrayList<>();

        // Recursively find null-like issues starting from the root
        findNullsRecursive(root, "$", issues);

        // Sort by issue type rank first, then alphabetically by JSON path
        issues.sort(Comparator
                .comparing(PositiveSearchAssertions::getTypeRank) // Primary sort key: issue type order
                .thenComparing(issue -> issue.substring(issue.indexOf("path: ") + 6)) // Secondary: JSON path
        );

        if (!issues.isEmpty()) {
            System.out.println("\n==== TC.18 NULL VALUE REPORT ====\n");
            String lastType = null;

            for (String issue : issues) {
                String currentType = getTypeName(issue);

                // Add a blank line when the type changes
                if (lastType != null && !Objects.equals(currentType, lastType)) {
                    System.out.println();
                }

                // Extract the JSON path from the issue string
                String path = issue.substring(issue.indexOf("path: ") + 6);

                // Check if it's one of the allowed warning-only fields
                if (path.endsWith(".departureTerminal") ||
                        path.endsWith(".arrivalTerminal") ||
                        path.endsWith(".fareBasisCode")) {

                    System.out.println("[⚠️ WARNING] " + issue); // Print warning
                    // ❌ No softAssert.fail() for warnings

                } else {
                    System.out.println(issue); // Print error
                    softAssert.fail(issue);    // Fail for all other fields
                }

                lastType = currentType;
            }

            System.out.printf("====== TOTAL NULL-LIKE ISSUES FOUND: %d ======%n", issues.size());
        } else {
            System.out.println("\nTC.18: ✅ No null-like values found in search response.");
        }
    }

    /**
     * Recursively scans the JSON structure and records any null-like values.
     * @param node   Current JSON node (can be Map, List, String, Number, etc.)
     * @param path   Current JSON path representation
     * @param issues List to collect issue descriptions
     */
    private static void findNullsRecursive(Object node, String path, List<String> issues) {
        if (node == null) {
            // Null value detected
            issues.add(String.format("[TC.18] ❌ NULL found at path: %s", path));
        }
        else if (node instanceof String && ((String) node).trim().isEmpty()) {
            // Empty string detected
            issues.add(String.format("[TC.18] ❌ EMPTY STRING found at path: %s", path));
        }
        else if (node instanceof List<?> list) {
            if (list.isEmpty()) {
                // Empty list detected
                issues.add(String.format("[TC.18] ❌ EMPTY LIST found at path: %s", path));
            } else {
                // Recursively scan each element in the list
                for (int i = 0; i < list.size(); i++) {
                    findNullsRecursive(list.get(i), path + "[" + i + "]", issues);
                }
            }
        }
        else if (node instanceof Map<?, ?> map) {
            if (map.isEmpty()) {
                // Empty object detected
                issues.add(String.format("[TC.18] ❌ EMPTY OBJECT found at path: %s", path));
            } else {
                // Recursively scan each field in the object
                for (Map.Entry<?, ?> entry : map.entrySet()) {
                    String key = String.valueOf(entry.getKey());
                    findNullsRecursive(entry.getValue(), path + "." + key, issues);
                }
            }
        }
    }

    // ==============================================================
    // ============== HELPER METHODS ================================
    // ==============================================================

    /**
     * Returns a numeric rank for the given issue string so that
     * sorting will group them in a logical order:
     *   1 → NULL
     *   2 → EMPTY STRING
     *   3 → EMPTY LIST
     *   4 → EMPTY OBJECT
     *
     * @param issue Issue description
     * @return Numeric rank for sorting
     */
    private static int getTypeRank(String issue) {
        if (issue.contains("NULL found")) return 1;
        if (issue.contains("EMPTY STRING found")) return 2;
        if (issue.contains("EMPTY LIST found")) return 3;
        if (issue.contains("EMPTY OBJECT found")) return 4;
        return 99; // Fallback for unknown types
    }
    /**
     * Returns the type name (human-readable) extracted from the issue string.
     * This is used for grouping and printing headers or separators.
     *
     * @param issue Issue description
     * @return Type name (e.g., "NULL", "EMPTY STRING")
     */
    private static String getTypeName(String issue) {
        if (issue.contains("NULL found")) return "NULL";
        if (issue.contains("EMPTY STRING found")) return "EMPTY STRING";
        if (issue.contains("EMPTY LIST found")) return "EMPTY LIST";
        if (issue.contains("EMPTY OBJECT found")) return "EMPTY OBJECT";
        return "OTHER";
    }

}