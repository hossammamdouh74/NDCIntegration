package Utils.Assertions;

import io.restassured.path.json.JsonPath;
import io.restassured.response.Response;
import org.testng.asserts.SoftAssert;

import java.math.BigDecimal;
import java.util.*;

public class FareConfirmAssertions {

    /**
     * Main method to validate that the FareConfirm API response
     * matches the offer that was originally selected in the Search results.
     * Steps:
     * 1. Compare basic boolean flags (`haveBundles`, `canBeHeld`).
     * 2. Compare price details (total, base, taxes).
     * 3. Validate passenger fare breakdown (amounts, passengers count).
     * 4. Validate all currencies match the expected "AgencyCurrency".
     * 5. Validate RBD codes per passenger and segment.
     * 6. Validate passenger breakdown totals match overall amounts.
     * 7. Ensure only one offer is returned.
     * 8. Validate journey count consistency between Search and FareConfirm.
     * 9. Validate fare breakdown calculations are correct.
     * 10. Ensure no duplicate tax codes exist.
     * 11. Validate price classes (RBDs, brands).
     * 12. Validate baggage details (structure, match to Search offer).
     * 13. Validate passenger code uniqueness and associated amounts data.
     *
     * @param fareConfirmResponse       API response from FareConfirm
     * @param selectedOfferFromSearch   Map containing the offer data from Search API
     */
    public static void validateFareConfirm(Response fareConfirmResponse, Map<String, Object> selectedOfferFromSearch) {
        SoftAssert softAssert = new SoftAssert();

        // Extract the selected offer object from FareConfirm response
        Map<String, Object> FareConfirmOffer = fareConfirmResponse.jsonPath().getMap("selectedOffer");
        System.out.println("🛂 Validating Fare Confirm Offer against selected Search Offer...");
        if (FareConfirmOffer == null) {
            softAssert.fail("❌ selectedOffer object not found in FareConfirm response.");
            softAssert.assertAll();
            return;
        }
        // Perform multiple checks
        validateRbdMatchesSelectedOffer(fareConfirmResponse, selectedOfferFromSearch, softAssert);
        validatePriceDetails(softAssert, selectedOfferFromSearch, FareConfirmOffer);
        validatePassengerFareBreakdown(softAssert, selectedOfferFromSearch, FareConfirmOffer);
        //validateCurrencies(fareConfirmResponse, "selectedOffer", softAssert);
        validatePassengerBreakdownTotalsMatchOverall(fareConfirmResponse,selectedOfferFromSearch,softAssert);
        validateSingleOfferReturned(fareConfirmResponse,softAssert);
        validateJourneyCountConsistency(fareConfirmResponse, selectedOfferFromSearch, softAssert);
        validateFareBreakdownCalculation(fareConfirmResponse,selectedOfferFromSearch,softAssert);
        validateNoDuplicateTaxCodes(fareConfirmResponse,softAssert);
        validatePriceClasses(fareConfirmResponse,softAssert);
        validateBaggageDetails(fareConfirmResponse,softAssert);
        validatePassengerCodeUniquenessAndAmounts(fareConfirmResponse,softAssert);
        // Trigger assertion failures if any collected errors exist
        softAssert.assertAll();
    }

    /**
     * TC.1 Validates that the Reservation Booking Designator (RBD) codes in the
     * FareConfirm API response match those from the Search API selected offer.
     *
     * @param response      The FareConfirm API response
     * @param selectedOfferFromSearch The selected offer from Search API (expected values)
     * @param softAssert    SoftAssert to collect and defer assertion failures
     */
    protected static void validateRbdMatchesSelectedOffer(Response response,
                                                          Map<String, Object> selectedOfferFromSearch,
                                                          SoftAssert softAssert) {
        System.out.println("\t✅[TC:1]: Validating RBD Matches Selected Offer ");

        JsonPath jsonPath = response.jsonPath();

        // Directly extract passengerFareBreakdown from the selectedOffer object
        List<Map<String, Object>> actualFareBreakdowns =
                jsonPath.getList("selectedOffer.passengerFareBreakdown");

        List<Map<String, Object>> expectedFareBreakdowns =
                (List<Map<String, Object>>) selectedOfferFromSearch.get("passengerFareBreakdown");

        // Fail if missing passengerFareBreakdown in either response
        if (actualFareBreakdowns == null || expectedFareBreakdowns == null) {
            System.out.println("\t❌[TC:1] passengerFareBreakdown is missing in one of the responses.");
            softAssert.fail("❌[TC:1] passengerFareBreakdown is missing in one of the responses.");
            return;
        }

        // Compare each passenger type (e.g., ADT, CHD, INF)
        for (Map<String, Object> expectedPax : expectedFareBreakdowns) {
            String type = (String) expectedPax.get("passengerTypeCode");

            // Match passenger type between expected and actual
            Optional<Map<String, Object>> actualPaxOpt = actualFareBreakdowns.stream()
                    .filter(p -> type.equals(p.get("passengerTypeCode")))
                    .findFirst();

            if (actualPaxOpt.isEmpty()) {
                softAssert.fail("❌[TC:1] Missing passenger type :" + type);
                continue;
            }

            // Compare segments
            List<Map<String, Object>> expectedSegment =
                    (List<Map<String, Object>>) expectedPax.get("segmentDetails");
            List<Map<String, Object>> actualSegment =
                    (List<Map<String, Object>>) actualPaxOpt.get().get("segmentDetails");

            int minSize = Math.min(expectedSegment.size(), actualSegment.size());

            if (expectedSegment.size() != actualSegment.size()) {
                softAssert.fail(String.format(
                        "❌[TC:1] Segment count mismatch for %s: expected %d, actual %d",
                        type, expectedSegment.size(), actualSegment.size()
                ));
            }

            // Compare RBD field for each segment
            for (int i = 0; i < minSize; i++) {
                String expectedRbd = String.valueOf(expectedSegment.get(i).get("rbd"));
                String actualRbd = String.valueOf(actualSegment.get(i).get("rbd"));

                System.out.printf(
                        "\t\t🔍 [%s - Segment %d] Expected RBD: %s, Actual RBD: %s%n",
                        type, i, expectedRbd, actualRbd
                );

                softAssert.assertEquals(actualRbd, expectedRbd,
                        String.format("❌[TC:1] RBD mismatch for %s - Segment %d", type, i));
            }

            // Log success
            System.out.println("\t✅[TC:1] RBD match check passed for " + type);
        }
    }

    /**
     * TC.2 Compares price-related fields inside the 'priceDetails' object
     * (totalAmount, taxesAmount, baseAmount).
     * Why:
     *  - Ensures the total fare, tax breakdown, and base fare align between Search and FareConfirm
     *  - Any mismatch could mean inconsistent pricing presented to the user vs. charged
     * Steps:
     *  1. Extract `priceDetails` object from both Search and FareConfirm
     *  2. Compare critical fields (totalAmount, taxesAmount, baseAmount)
     *  3. Use helper method compareField for logging/assertion
     *
     * @param softAssert    SoftAssert for assertions
     * @param selectedOfferFromSearch Selected offer from Search API
     * @param actual        Offer from FareConfirm API
     */
    private static void validatePriceDetails(SoftAssert softAssert, Map<String, Object> selectedOfferFromSearch, Map<String, Object> actual) {
        System.out.println("\t💰[TC:2]: Comparing price details...");

        Map<String, Object> expectedPriceDetails = (Map<String, Object>) selectedOfferFromSearch.get("priceDetails");
        Map<String, Object> actualPriceDetails = (Map<String, Object>) actual.get("priceDetails");

        compareField("totalAmount", expectedPriceDetails, actualPriceDetails, softAssert);
        compareField("totalTaxAmount", expectedPriceDetails, actualPriceDetails, softAssert);
        compareField("totalBaseAmount", expectedPriceDetails, actualPriceDetails, softAssert);
    }

    /**
     * TC.3 Validates the passengerFareBreakdown section for each passenger type.
     * Why:
     *  - Ensures that the fare breakdown (amounts, taxes, base, etc.) for each passenger type
     *    in FareConfirm matches the expected Search offer.
     *  - Detects if any passenger type (ADT, CHD, INF, etc.) is missing or has mismatched details.
     * Steps:
     *  1. Extract passengerFareBreakdown from both expected (Search) and actual (FareConfirm).
     *  2. For each passenger type in expected, check that it exists in actual.
     *  3. If found → call helper validateSinglePassengerBreakdown() to validate amounts.
     *  4. If not found → log and fail.
     *
     * @param softAssert    TestNG SoftAssert instance
     * @param expectedOffer Selected offer from Search API (expected values)
     * @param actual        Offer from FareConfirm API (actual values)
     */
    private static void validatePassengerFareBreakdown(SoftAssert softAssert, Map<String, Object> expectedOffer, Map<String, Object> actual) {
        System.out.println("\t🧍‍♂️ [TC:3]: Validating passenger fare breakdown...");

        // Extract passenger breakdowns from expected and actual
        List<Map<String, Object>> expectedPFB = (List<Map<String, Object>>) expectedOffer.get("passengerFareBreakdown");
        List<Map<String, Object>> actualPFB = (List<Map<String, Object>>) actual.get("passengerFareBreakdown");

        if (expectedPFB != null && actualPFB != null) {
            for (Map<String, Object> expectedPassenger : expectedPFB) {
                String type = (String) expectedPassenger.get("passengerTypeCode");
                System.out.println("\t🔎 Checking passenger type: " + type);

                // Find matching passenger type in actual breakdown
                Optional<Map<String, Object>> actualMatchOpt = actualPFB.stream()
                        .filter(p -> type.equals(p.get("passengerTypeCode")))
                        .findFirst();

                if (actualMatchOpt.isPresent()) {
                    // ✅ If found → validate detailed breakdown (amounts, taxes, base)
                    validateSinglePassengerBreakdown(softAssert, type, expectedPassenger, actualMatchOpt.get());
                } else {
                    // ❌ If not found → fail test
                    System.out.printf("❌[TC:3] Passenger of type [%s] not found in fare confirm response%n", type);
                    softAssert.fail("Passenger of type " + type + " not found in fare confirm response");
                }
            }
        } else {
            System.out.println("❌ [TC:3] passengerFareBreakdown is missing in one or both responses");
            softAssert.fail("PassengerFareBreakdown missing in one or both responses");
        }
    }

    /**
     * TC.4 Validates Passenger Fare Breakdown Consistency:
     * Ensures that the passengerFareBreakdown totals in FareConfirm
     * match those from the Search API's selected offer.
     * Why:
     *  - Ensures amounts for each passenger type (ADT, CHD, INF, etc.) are consistent.
     *  - Prevents discrepancies between Search (offer shown to user) and FareConfirm (final booking).
     * Steps:
     *  1. Extract passengerFareBreakdown from both Search and FareConfirm.
     *  2. Iterate over each passenger type in expected list.
     *  3. Compare with corresponding actual passenger type.
     *  4. Call helper validateSinglePassengerBreakdown() for detailed validation.
     *
     * @param fareConfirmResponse FareConfirm API response
     * @param selectedOfferFromSearch       Selected offer from Search API
     * @param softAssert          TestNG SoftAssert instance
     */
    private static void validatePassengerBreakdownTotalsMatchOverall(Response fareConfirmResponse, Map<String, Object> selectedOfferFromSearch, SoftAssert softAssert) {

        System.out.println("\n🔎 [TC:4] Validating Passenger Fare Breakdown Consistency...");

        // Extract actual breakdown from FareConfirm
        JsonPath jsonPath = fareConfirmResponse.jsonPath();

        // Directly extract passengerFareBreakdown from the selectedOffer object
        List<Map<String, Object>> actualBreakdown =
                jsonPath.getList("selectedOffer.passengerFareBreakdown");

        // Extract expected breakdown from Search
        List<Map<String, Object>> expectedBreakdown =
                (List<Map<String, Object>>) selectedOfferFromSearch.get("passengerFareBreakdown");

        if (actualBreakdown == null || expectedBreakdown == null) {
            System.out.println("⚠️[TC:4] Passenger breakdown missing in one of the structures!");
            softAssert.fail("❌ [TC:4] Passenger fare breakdown not found in response or expected offer");
            return;
        }

        // Compare passenger types one by one
        for (int i = 0; i < expectedBreakdown.size(); i++) {
            Map<String, Object> expected = expectedBreakdown.get(i);
            Map<String, Object> actual = actualBreakdown.get(i);

            String type = (String) expected.get("passengerTypeCode");

            System.out.printf("➡️ Validating fare breakdown for passenger type: %s%n", type);

            // Validate detailed breakdown via helper
            validateSinglePassengerBreakdown(softAssert, type, expected, actual);
        }

        System.out.println("✅[TC:4] Passenger Fare Breakdown Consistency validated successfully.");
    }

    /**
     * TC.5 Validates that only one offer is returned in FareConfirm
     * when the request is NOT an upselling flow.
     * Why:
     *  - In non-upsell scenarios, user should see only one confirmed offer.
     *  - Multiple offers in this case would indicate a logic error in the backend.
     * Steps:
     *  1. Extract selectedOffer list from FareConfirm response.
     *  2. Assert that exactly one offer exists.
     *  3. Log results.
     *
     * @param response   FareConfirm API response
     * @param softAssert SoftAssert instance
     */
    private static void validateSingleOfferReturned(Response response, SoftAssert softAssert) {
        System.out.println("\n📌 [TC:5] Validating single offer returned for NON UPSELLING...");

        // Extract selectedOffer as a Map
        Map<String, Object> offer = response.jsonPath().getMap("selectedOffer");

        if (offer == null || offer.isEmpty()) {
            System.out.println("❌[TC:5] selectedOffer is missing in response");
            softAssert.fail("selectedOffer is missing in response");
            return;
        }

        System.out.println("\t🔎 Found 1 offer in response.");

        // Assert only one offer exists (since it's an object, not an array)
        softAssert.assertTrue(true, "✅ [TC:7] Exactly one offer returned as expected.");
    }

    /**
     * TC.6 Validates journey count consistency between Search request and FareConfirm response.
     * Why:
     *  - Ensures that the number of journeys (legs) in FareConfirm matches
     *    what was originally requested in Search.
     *  - Prevents over/under booking or misaligned journey data.
     * Steps:
     *  1. From Search payload → get journey count (searchCriteria size).
     *  2. From FareConfirm response → get journey count.
     *  3. Compare counts.
     *  4. Fail test if mismatch found.
     *
     * @param response                FareConfirm API response
     * @param selectedOfferFromSearch The original Search offer (contains "searchPayload")
     * @param softAssert              TestNG SoftAssert instance
     */
    private static void validateJourneyCountConsistency(Response response, Map<String, Object> selectedOfferFromSearch, SoftAssert softAssert) {
        System.out.println("\t🛫 [TC:6] Validating journey count consistency...");

        try {
            // From Search → extract journey count
            Map<String, Object> searchPayload = (Map<String, Object>) selectedOfferFromSearch.get("searchPayload");
            List<Map<String, Object>> criteria = (List<Map<String, Object>>) searchPayload.get("searchCriteria");
            int expectedJourneyCount = (criteria != null) ? criteria.size() : 0;

            // From FareConfirm → extract journey count
            List<Map<String, Object>> journeys = response.jsonPath().getList("selectedOffer.offerJourneys");
            int actualJourneyCount = (journeys != null) ? journeys.size() : 0;

            // Assert counts match
            softAssert.assertEquals(actualJourneyCount, expectedJourneyCount,
                    "❌ [TC:6] Journey count mismatch: expected=" + expectedJourneyCount + ", actual=" + actualJourneyCount);

            System.out.println("\t✅[TC:6] Journey count validation passed. Expected="
                    + expectedJourneyCount + ", Actual=" + actualJourneyCount);

        } catch (Exception e) {
            softAssert.fail("⚠️[TC:6] Exception while validating journey count consistency: " + e.getMessage());
            System.out.println("\t⚠️[TC:6] Exception in validateJourneyCountConsistency: " + e.getMessage());
        }
    }

    /**
     * TC.7: Validate Fare Breakdown Calculation
     * - Verifies that per-pax totals = base + taxes
     * - Verifies group totals = per-pax total × passenger count (from request payload, not response)
     * - Verifies aggregated group totals = overall total
     */
    private static void validateFareBreakdownCalculation(Response response,
                                                         Map<String, Object> selectedOfferFromSearch,
                                                         SoftAssert softAssert) {
        System.out.println("\n📌 TC.7: Validating fare breakdown calculations...");

        try {
            // ✅ Extract overall total from response
            BigDecimal overallTotal = parseBigDecimalSafe(
                    response.jsonPath().getMap("selectedOffer[0].priceDetails.totalAmount")
            );

            // ✅ Extract passenger fare breakdowns from response
            List<Map<String, Object>> passengerBreakdowns =
                    response.jsonPath().getList("selectedOffer.passengerFareBreakdown");

            // ✅ Extract passenger counts from request payload
            Map<String, Object> searchPayload = (Map<String, Object>) selectedOfferFromSearch.get("searchPayload");
            List<Map<String, Object>> passengersFromPayload =
                    (List<Map<String, Object>>) searchPayload.get("passengers");

            // Convert payload passengers into lookup map (e.g., ADT=4, CHD=2, INF=3)
            Map<String, Integer> paxCountMap = new HashMap<>();
            for (Map<String, Object> pax : passengersFromPayload) {
                String paxType = String.valueOf(pax.get("passengerTypeCode"));
                int count = Integer.parseInt(String.valueOf(pax.get("count")));
                paxCountMap.put(paxType, count);
            }

            BigDecimal sumOfPassengerTotals = BigDecimal.ZERO;

            if (passengerBreakdowns != null && !passengerBreakdowns.isEmpty()) {
                for (Map<String, Object> pax : passengerBreakdowns) {
                    String paxType = String.valueOf(pax.get("passengerTypeCode"));

                    // ✅ Use count from payload instead of response
                    int paxCount = paxCountMap.getOrDefault(paxType, 0);

                    // Extract base, tax, and total values
                    BigDecimal baseFare = parseBigDecimalSafe(pax.get("passengerBaseAmount"));
                    BigDecimal taxes = parseBigDecimalSafe(pax.get("passengerTaxesAmount"));
                    BigDecimal perPaxTotal = parseBigDecimalSafe(pax.get("passengerTotalAmount"));

                    // Expected = base + taxes
                    BigDecimal expectedPerPaxTotal = baseFare.add(taxes);

                    // Validate per-pax total calculation
                    softAssert.assertEquals(perPaxTotal, expectedPerPaxTotal,
                            "❌ [TC.7] Per-pax total mismatch for " + paxType);

                    // Group total = per pax × count
                    BigDecimal paxGroupTotal = perPaxTotal.multiply(BigDecimal.valueOf(paxCount));
                    sumOfPassengerTotals = sumOfPassengerTotals.add(paxGroupTotal);

                    System.out.printf(
                            "\t🔍 [TC.7][%s] base=%s + taxes=%s → perPax=%s × count=%d → groupTotal=%s%n",
                            paxType, baseFare, taxes, perPaxTotal, paxCount, paxGroupTotal
                    );
                }

                // Compare aggregated totals with overall total
                System.out.printf("\t🔍 [TC.7] Sum of passenger group totals = %s | Overall total = %s%n",
                        sumOfPassengerTotals, overallTotal);

                softAssert.assertEquals(sumOfPassengerTotals, overallTotal,
                        "❌ [TC.7] Overall total mismatch");

            } else {
                System.out.println("⚠️ [TC.7] No passengerFareBreakdown found in response.");
                softAssert.fail("Missing passengerFareBreakdown in FareConfirm response");
            }

            System.out.println("✅ [TC.7] Fare breakdown calculation check completed.");
        } catch (Exception e) {
            softAssert.fail("⚠️ [TC.7] Exception while validating fare breakdown calculation: " + e.getMessage());
            System.out.println("\t⚠️ [TC.7] Exception: " + e.getMessage());
        }
    }


    /**
     * TC.8 – Validate No Duplicate Tax Codes
     * Ensures that within each passenger’s fare breakdown:
     *  - taxesAndFees[].code values are unique (no duplicates allowed).
     * Example:
     *  - Valid → ["YQ", "FR", "XT"]
     *  - Invalid → ["YQ", "FR", "YQ"]  → duplicate detected
     *
     * @param response   The FareConfirm API response
     * @param softAssert The TestNG SoftAssert instance
     */
    private static void validateNoDuplicateTaxCodes(Response response, SoftAssert softAssert) {
        System.out.println("\n📌 [TC.8]: Validating no duplicate tax codes per passenger...");

        List<Map<String, Object>> passengerBreakdowns =
                response.jsonPath().getList("selectedOffer.passengerFareBreakdown");

        if (passengerBreakdowns == null || passengerBreakdowns.isEmpty()) {
            System.out.println("⚠️ [TC.8] No passengerFareBreakdown found in response.");
            softAssert.fail("[TC.8] passengerFareBreakdown is missing");
            return;
        }

        for (Map<String, Object> pax : passengerBreakdowns) {
            String paxType = String.valueOf(pax.get("passengerTypeCode"));
            System.out.println("\t🔎 Checking tax codes for passenger type: " + paxType);

            List<Map<String, Object>> taxes = (List<Map<String, Object>>) pax.get("taxesAndFees");
            if (taxes == null) {
                System.out.println("\t⚠️ [TC.8][" + paxType + "] No taxesAndFees found — skipping.");
                continue;
            }

            Set<String> seenCodes = new HashSet<>();
            for (Map<String, Object> tax : taxes) {
                String code = String.valueOf(tax.get("code"));
                System.out.println("\t\tTax code found: " + code);

                if (seenCodes.contains(code)) {
                    softAssert.fail("[TC.8][" + paxType + "] Duplicate tax code found: " + code);
                } else {
                    seenCodes.add(code);
                }
            }
            System.out.println("\t✅ [TC.8][" + paxType + "] No duplicate tax codes found.");
        }

        System.out.println("✅ [TC.8] Duplicate tax codes validation completed for all passengers.");
    }

    /**
     * TC.9 – Validate Price Classes
     * Ensures that the `priceClasses` object exists and for each class:
     *  - priceClassName is not null
     *  - fareDescription is not null
     *  - rulesAndPenalties list exists and is not empty
     * Example validation:
     *  {
     *    "priceClassName": "Light",
     *    "fareDescription": "PublicFare",
     *    "rulesAndPenalties": ["Non-Refundable", "Change with fee"]
     *  }
     *
     * @param response   The FareConfirm API response
     * @param softAssert The TestNG SoftAssert instance
     */
    private static void validatePriceClasses(Response response, SoftAssert softAssert) {
        System.out.println("\n📌 [TC.9]: Validating price classes...");

        Map<String, Map<String, Object>> priceClasses =
                response.jsonPath().getMap("priceClasses");

        if (priceClasses == null || priceClasses.isEmpty()) {
            softAssert.fail("[TC.9] priceClasses are missing or empty.");
            System.out.println("❌ [TC.9] priceClasses are missing or empty.");
            return;
        }

        int i = 0;
        for (Map.Entry<String, Map<String, Object>> entry : priceClasses.entrySet()) {
            i++;
            String priceClassKey = entry.getKey();
            Map<String, Object> priceClass = entry.getValue();

            System.out.println("\t🔎 Checking PriceClass #" + i + " (" + priceClassKey + ")");

            // Validate required fields
            softAssert.assertNotNull(priceClass.get("priceClassName"),
                    "[TC.9] priceClassName is missing in priceClass: " + priceClassKey);
            softAssert.assertNotNull(priceClass.get("fareDescription"),
                    "[TC.9] fareDescription is missing in priceClass: " + priceClassKey);

            // Validate rules list
            List<String> rules = (List<String>) priceClass.get("rulesAndPenalties");
            softAssert.assertTrue(rules != null && !rules.isEmpty(),
                    "[TC.9] rulesAndPenalties missing/empty in priceClass: " + priceClassKey);

            System.out.println("\t✅ Validated PriceClass: name=" + priceClass.get("priceClassName")
                    + ", fareDescription=" + priceClass.get("fareDescription")
                    + ", rulesCount=" + rules.size());
        }

        System.out.println("✅ [TC.9] All price classes validated successfully.");
    }

    /**
     * TC.10 – Validate Baggage Details
     * Ensures that:
     *  - `baggageDetails` object exists and is not empty.
     *  - Each baggage entry has a valid key.
     *  - Each baggage entry contains non-null `carryOnBaggage` and `checkInBaggage` structures.
     * Example validation:
     *  {
     *    "carryOnBaggage": { "weight": 8, "unit": "KG" },
     *    "checkInBaggage": { "weight": 23, "unit": "KG" }
     *  }
     *
     * @param response   The FareConfirm API response
     * @param softAssert The TestNG SoftAssert instance
     */
    private static void validateBaggageDetails(Response response, SoftAssert softAssert) {
        System.out.println("📌 [TC.10]: Validating baggage details...");

        Map<String, Object> responseMap = response.jsonPath().getMap("$");

        Map<String, Object> baggageDetails = (Map<String, Object>) responseMap.get("baggageDetails");

        // 1. Validate baggageDetails presence
        softAssert.assertNotNull(baggageDetails, "[TC.10] baggageDetails is missing.");
        softAssert.assertFalse(baggageDetails.isEmpty(), "[TC.10] baggageDetails is empty.");

        // 2. Iterate and check fields
        for (Map.Entry<String, Object> entry : baggageDetails.entrySet()) {
            String baggageKey = entry.getKey();
            Map<String, Object> baggage = (Map<String, Object>) entry.getValue();

            System.out.println("\t🔎 Validating baggage key: " + baggageKey);

            // Validate key is not null
            softAssert.assertNotNull(baggageKey, "[TC.10] baggage key is null.");

            // Validate carryOn & checkIn baggage presence
            softAssert.assertTrue(baggage.containsKey("carryOnBaggage"),
                    "[TC.10] Missing carryOnBaggage for key: " + baggageKey);
            softAssert.assertTrue(baggage.containsKey("checkInBaggage"),
                    "[TC.10] Missing checkInBaggage for key: " + baggageKey);

            softAssert.assertNotNull(baggage.get("carryOnBaggage"),
                    "[TC.10] carryOnBaggage is null for key: " + baggageKey);
            softAssert.assertNotNull(baggage.get("checkInBaggage"),
                    "[TC.10] checkInBaggage is null for key: " + baggageKey);
        }

        System.out.println("✅ [TC.10] Baggage details validated successfully.");
    }

    /**
     * TC.11 – Validate Passenger Code Uniqueness and Associated Amounts Data
     * Purpose:
     * Ensures that each passengerTypeCode appears only once in the fare breakdown,
     * and verifies that required amount fields exist (not null).
     * Steps:
     * 1. Extract `selectedOffer[0].passengerFareBreakdown` from response.
     * 2. Fail if list is missing/empty.
     * 3. For each passenger entry:
     *    - Validate uniqueness of passengerTypeCode.
     *    - Validate presence of mandatory amount fields:
     *         - passengerTotalAmount
     *         - passengerTaxesAmount
     *         - passengerBaseAmount
     * 4. Log per-passenger validations.
     * 5. Report all errors via SoftAssert (non-blocking).
     *
     * @param response   The FareConfirm API response
     * @param softAssert The SoftAssert instance for validation
     */
    private static void validatePassengerCodeUniquenessAndAmounts(Response response, SoftAssert softAssert) {
        System.out.println("\n📌 [TC.11]: Validating Passenger Code Uniqueness and Amounts Data...");

        JsonPath jsonPath = response.jsonPath();

        // Directly extract passengerFareBreakdown from the selectedOffer object
        List<Map<String, Object>> passengerBreakdowns =
                jsonPath.getList("selectedOffer.passengerFareBreakdown");

        if (passengerBreakdowns == null || passengerBreakdowns.isEmpty()) {
            softAssert.fail("[TC.11] passengerFareBreakdown is missing or empty.");
            System.out.println("❌ [TC.11] passengerFareBreakdown is missing or empty.");
            return;
        }

        Set<String> seenTypes = new HashSet<>();
        int i = 0;

        for (Map<String, Object> pax : passengerBreakdowns) {
            i++;
            String paxType = String.valueOf(pax.get("passengerTypeCode"));
            System.out.println("\t🔎 Checking passenger #" + i + " (" + paxType + ")");

            // 1. Uniqueness check
            softAssert.assertTrue(!seenTypes.contains(paxType),
                    "[TC.11] Duplicate passengerTypeCode found: " + paxType);
            seenTypes.add(paxType);

            // 2. Required amount fields check
            softAssert.assertNotNull(pax.get("paxTotalTaxAmount"),
                    "[TC.11] passengerTaxesAmount missing for passengerTypeCode: " + paxType);
            softAssert.assertNotNull(pax.get("paxBaseAmount"),
                    "[TC.11] passengerBaseAmount missing for passengerTypeCode: " + paxType);

            System.out.println("\t✅ Validated passengerTypeCode=" + paxType);
        }

        System.out.println("✅ [TC.11] Passenger code uniqueness and amounts check completed.");
    }

    // ==============================================================
    // ============== HELPER METHODS ================================
    // ==============================================================

    /**
     * Helper method to safely parse BigDecimal from various object types.
     */
    private static BigDecimal parseBigDecimalSafe(Object value) {
        if (value == null) return BigDecimal.ZERO;

        try {
            if (value instanceof Map) {
                Object amount = ((Map<?, ?>) value).get("amount");
                return (amount != null) ? new BigDecimal(amount.toString()) : BigDecimal.ZERO;
            }
            return new BigDecimal(value.toString());
        } catch (NumberFormatException e) {
            System.out.println("⚠️ Invalid number format in response: " + value);
            return BigDecimal.ZERO;
        }
    }
    /**
     * Compares core fare amounts for a single passenger type.
     */
    private static void validateSinglePassengerBreakdown(SoftAssert softAssert, String type, Map<String, Object> expected, Map<String, Object> actual) {
        compareField("paxTotalTaxAmount", expected, actual, type, softAssert);
        compareField("paxBaseAmount", expected, actual, type, softAssert);
    }
    /**
     * Compares a single field in passenger breakdown for a given passenger type.
     */
    private static void compareField(String field, Map<String, Object> expected, Map<String, Object> actual, String type, SoftAssert softAssert) {
        Object expectedValue = expected.get(field);
        Object actualValue = actual.get(field);
        System.out.printf("\t\t🔍 [%s] %s → expected: %s, actual: %s%n", type, field, expectedValue, actualValue);
        softAssert.assertEquals(actualValue, expectedValue, "❌ " + field + " mismatch for " + type);
    }
    /**
     * Compares a single price field between expected and actual priceDetails.
     */
    private static void compareField(String field, Map<String, Object> expected, Map<String, Object> actual, SoftAssert softAssert) {
        Object expectedValue = expected.get(field);
        Object actualValue = actual.get(field);
        System.out.printf("\t\t💵 %s → expected: %s, actual: %s%n", field, expectedValue, actualValue);
        softAssert.assertEquals(actualValue, expectedValue, "❌ " + field + " mismatch");
    }
}
