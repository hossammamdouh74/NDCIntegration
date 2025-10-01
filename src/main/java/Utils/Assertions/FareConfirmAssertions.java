package Utils.Assertions;

import Utils.ReportManager.ReportManager;
import io.restassured.path.json.JsonPath;
import io.restassured.response.Response;
import org.testng.asserts.SoftAssert;

import java.math.BigDecimal;
import java.util.*;

import static Utils.Helper.HelperCurrencyValidator.validateCurrencies;

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
     * @param headers                   Request headers (for currency validation)
     */
    public static void validateFareConfirm(Response fareConfirmResponse, Map<String, Object> selectedOfferFromSearch, Map<String, String> headers) {
        SoftAssert softAssert = new SoftAssert();

        // Extract the selected offer object from FareConfirm response
        Map<String, Object> FareConfirmOffer = fareConfirmResponse.jsonPath().getMap("selectedOfferOptions[0]");
        System.out.println("🛂 Validating Fare Confirm Offer against selected Search Offer...");

        // Perform multiple checks
        validateRbdMatchesSelectedOffer(fareConfirmResponse, selectedOfferFromSearch, softAssert, "selectedOfferOptions[0]");
        validateBasicFlags(softAssert, selectedOfferFromSearch, FareConfirmOffer);
        validatePriceDetails(softAssert, selectedOfferFromSearch, FareConfirmOffer);
        validatePassengerFareBreakdown(softAssert, selectedOfferFromSearch, FareConfirmOffer);
        validateCurrencies(fareConfirmResponse, headers, "selectedOfferOptions", softAssert);
        validatePassengerBreakdownTotalsMatchOverall(fareConfirmResponse,selectedOfferFromSearch,softAssert);
        validateSingleOfferReturned(fareConfirmResponse,softAssert);
        validateJourneyCountConsistency(fareConfirmResponse, selectedOfferFromSearch, softAssert);
        validateFareBreakdownCalculation(fareConfirmResponse,softAssert);
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
     * Why:
     *  - Ensures the booked cabin inventory matches what was offered during search
     *  - Detects mismatches that could indicate pricing/availability discrepancies
     * Steps:
     *  1. Extract passengerFareBreakdown from both Search and FareConfirm responses
     *  2. For each passenger type (ADT, CHD, INF, etc.), check segment counts
     *  3. Compare RBD codes segment by segment
     *  4. Log and assert failures where mismatches are found
     *
     * @param response      The FareConfirm API response
     * @param selectedOfferFromSearch The selected offer from Search API (expected values)
     * @param softAssert    SoftAssert to collect and defer assertion failures
     * @param rootPath      JSON root path pointing to the offer in FareConfirm response
     */
    protected static void validateRbdMatchesSelectedOffer(Response response, Map<String, Object> selectedOfferFromSearch, SoftAssert softAssert, String rootPath) {
        System.out.println("\t✅[TC:1]: Validating RBD Matches Selected Offer ");

        JsonPath jsonPath = response.jsonPath();
        List<Map<String, Object>> actualFareBreakdowns = jsonPath.getList(rootPath + ".passengerFareBreakdown");
        List<Map<String, Object>> expectedFareBreakdowns =
                (List<Map<String, Object>>) selectedOfferFromSearch.get("passengerFareBreakdown");

        // Fail if missing passengerFareBreakdown in either response
        if (actualFareBreakdowns == null || expectedFareBreakdowns == null) {
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
            List<Map<String, Object>> expectedSegment = (List<Map<String, Object>>) expectedPax.get("segmentDetails");
            List<Map<String, Object>> actualSegment = (List<Map<String, Object>>) actualPaxOpt.get().get("segmentDetails");
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
            ReportManager.getTest().info("✅ RBD match check passed  ");
            System.out.println("\t✅TC.1: RBD match check passed ");
        }
    }

    /**
     * TC.2 Validates the basic boolean flags `haveBundles` and `canBeHeld`
     * between Search API and FareConfirm API.
     * Why:
     *  - These flags control whether upsell bundles exist and whether the offer
     *    can be held for later confirmation/payment.
     *  - Incorrect mismatches could break booking flow or upsell logic.
     * Steps:
     *  1. Extract `haveBundles` and `canBeHeld` from both expected and actual offers
     *  2. Compare values
     *  3. Log results and assert mismatches
     *
     * @param softAssert    SoftAssert for collecting assertion results
     * @param selectedOfferFromSearch Selected offer from Search API (expected values)
     * @param actual        Offer from FareConfirm API (actual values)
     */
    private static void validateBasicFlags(SoftAssert softAssert, Map<String, Object> selectedOfferFromSearch, Map<String, Object> actual) {
        System.out.println("\t✅ TC.2: Checking `haveBundles` and `canBeHeld` flags...");

        Object expectedHaveBundles = selectedOfferFromSearch.get("haveBundles");
        Object actualHaveBundles = actual.get("haveBundles");
        System.out.printf("\t\t🔍 haveBundles → expected: %s, actual: %s%n", expectedHaveBundles, actualHaveBundles);
        softAssert.assertEquals(actualHaveBundles, expectedHaveBundles, "❌[TC:2] haveBundles mismatch");

        Object expectedCanBeHeld = selectedOfferFromSearch.get("canBeHeld");
        Object actualCanBeHeld = actual.get("canBeHeld");
        System.out.printf("\t\t🔍 canBeHeld → expected: %s, actual: %s%n", expectedCanBeHeld, actualCanBeHeld);
        softAssert.assertEquals(actualCanBeHeld, expectedCanBeHeld, "❌[TC:2] canBeHeld mismatch");
    }

    /**
     * TC.3 Compares price-related fields inside the 'priceDetails' object
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
        System.out.println("\t💰[TC:3]: Comparing price details...");

        Map<String, Object> expectedPriceDetails = (Map<String, Object>) selectedOfferFromSearch.get("priceDetails");
        Map<String, Object> actualPriceDetails = (Map<String, Object>) actual.get("priceDetails");

        compareField("totalAmount", expectedPriceDetails, actualPriceDetails, softAssert);
        compareField("taxesAmount", expectedPriceDetails, actualPriceDetails, softAssert);
        compareField("baseAmount", expectedPriceDetails, actualPriceDetails, softAssert);
    }

    /**
     * TC.4 Validates the passengerFareBreakdown section for each passenger type.
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
        System.out.println("\t🧍‍♂️ [TC:4]: Validating passenger fare breakdown...");

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
                    System.out.printf("❌[TC:4] Passenger of type [%s] not found in fare confirm response%n", type);
                    softAssert.fail("Passenger of type " + type + " not found in fare confirm response");
                }
            }
        } else {
            System.out.println("❌ [TC:4] passengerFareBreakdown is missing in one or both responses");
            softAssert.fail("PassengerFareBreakdown missing in one or both responses");
        }
    }

    /**
     * TC.6 Validates Passenger Fare Breakdown Consistency:
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

        System.out.println("\n🔎 [TC:6] Validating Passenger Fare Breakdown Consistency...");

        // Extract actual breakdown from FareConfirm
        List<Map<String, Object>> actualBreakdown = fareConfirmResponse.jsonPath()
                .getList("selectedOfferOptions[0].passengerFareBreakdown");

        // Extract expected breakdown from Search
        List<Map<String, Object>> expectedBreakdown =
                (List<Map<String, Object>>) selectedOfferFromSearch.get("passengerFareBreakdown");

        if (actualBreakdown == null || expectedBreakdown == null) {
            System.out.println("⚠️[TC:6] Passenger breakdown missing in one of the structures!");
            softAssert.fail("❌ [TC:6] Passenger fare breakdown not found in response or expected offer");
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

        System.out.println("✅[TC:6] Passenger Fare Breakdown Consistency validated successfully.");
    }

    /**
     * TC.7 Validates that only one offer is returned in FareConfirm
     * when the request is NOT an upselling flow.
     * Why:
     *  - In non-upsell scenarios, user should see only one confirmed offer.
     *  - Multiple offers in this case would indicate a logic error in the backend.
     * Steps:
     *  1. Extract selectedOfferOptions list from FareConfirm response.
     *  2. Assert that exactly one offer exists.
     *  3. Log results.
     *
     * @param response   FareConfirm API response
     * @param softAssert SoftAssert instance
     */
    private static void validateSingleOfferReturned(Response response, SoftAssert softAssert) {
        System.out.println("\n📌 [TC:7] Validating single offer returned for NON UPSELLING...");

        // Extract offers
        List<Map<String, Object>> offers = response.jsonPath().getList("selectedOfferOptions");

        if (offers == null) {
            System.out.println("❌[TC:7] selectedOfferOptions is missing in response");
            softAssert.fail("selectedOfferOptions is missing in response");
            return;
        }

        System.out.println("\t🔎 Found " + offers.size() + " offers in response.");

        // Assert exactly one offer is returned
        softAssert.assertEquals(
                offers.size(),
                1,
                "❌[TC:7] More than one offer returned for NON UPSELLING"
        );

        if (offers.size() == 1) {
            System.out.println("\t✅[TC:7] Exactly one offer returned as expected.");
        }
    }

    /**
     * TC.8 Validates journey count consistency between Search request and FareConfirm response.
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
        System.out.println("\t🛫 [TC:8] Validating journey count consistency...");

        try {
            // From Search → extract journey count
            Map<String, Object> searchPayload = (Map<String, Object>) selectedOfferFromSearch.get("searchPayload");
            List<Map<String, Object>> criteria = (List<Map<String, Object>>) searchPayload.get("searchCriteria");
            int expectedJourneyCount = (criteria != null) ? criteria.size() : 0;

            // From FareConfirm → extract journey count
            List<Map<String, Object>> journeys = response.jsonPath().getList("selectedOfferOptions[0].journeys");
            int actualJourneyCount = (journeys != null) ? journeys.size() : 0;

            // Assert counts match
            softAssert.assertEquals(actualJourneyCount, expectedJourneyCount,
                    "❌ [TC:8] Journey count mismatch: expected=" + expectedJourneyCount + ", actual=" + actualJourneyCount);

            System.out.println("\t✅[TC:8] Journey count validation passed. Expected="
                    + expectedJourneyCount + ", Actual=" + actualJourneyCount);

        } catch (Exception e) {
            softAssert.fail("⚠️[TC:8] Exception while validating journey count consistency: " + e.getMessage());
            System.out.println("\t⚠️[TC:8] Exception in validateJourneyCountConsistency: " + e.getMessage());
        }
    }

    /**
     * TC.9 – Validate Fare Breakdown Total Calculation
     * Ensures that:
     *  1. For each passenger fare breakdown: baseAmount + taxesAmount = totalAmount (per passenger).
     *  2. Per-passenger totals multiplied by passenger count = passenger group total.
     *  3. Sum of all passenger group totals = overall priceDetails.totalAmount.
     * Example validation flow:
     *  - ADT pax → base=100, tax=20 → per pax total=120 × count=2 → 240
     *  - CHD pax → base=50, tax=10 → per pax total=60 × count=1 → 60
     *  - Overall sum = 300 → must equal overall priceDetails.totalAmount
     *
     * @param response   The FareConfirm API response
     * @param softAssert The TestNG SoftAssert instance (to allow multiple validations per run)
     */
    private static void validateFareBreakdownCalculation(Response response, SoftAssert softAssert) {
        System.out.println("\n📌 TC.9: Validating fare breakdown calculations...");

        // Extract overall total from priceDetails
        BigDecimal overallTotal = parseBigDecimalSafe(
                response.jsonPath().getMap("selectedOfferOptions[0].priceDetails.totalAmount")
        );

        // Extract passenger fare breakdowns
        List<Map<String, Object>> passengerBreakdowns =
                response.jsonPath().getList("selectedOfferOptions[0].passengerFareBreakdown");

        BigDecimal sumOfPassengerTotals = BigDecimal.ZERO;

        if (passengerBreakdowns != null && !passengerBreakdowns.isEmpty()) {
            for (Map<String, Object> pax : passengerBreakdowns) {
                String paxType = String.valueOf(pax.get("passengerTypeCode"));
                int paxCount = Integer.parseInt(String.valueOf(pax.get("numberOfPassengers")));

                // Extract base, tax, and total values
                BigDecimal baseFare = parseBigDecimalSafe(pax.get("passengerBaseAmount"));
                BigDecimal taxes = parseBigDecimalSafe(pax.get("passengerTaxesAmount"));
                BigDecimal perPaxTotal = parseBigDecimalSafe(pax.get("passengerTotalAmount"));

                // Expected = base + taxes
                BigDecimal expectedPerPaxTotal = baseFare.add(taxes);

                // Validate per-pax total calculation
                softAssert.assertEquals(perPaxTotal, expectedPerPaxTotal,
                        "❌ [TC.9] Per-pax total mismatch for " + paxType);

                // Group total = per pax × count
                BigDecimal paxGroupTotal = perPaxTotal.multiply(BigDecimal.valueOf(paxCount));
                sumOfPassengerTotals = sumOfPassengerTotals.add(paxGroupTotal);

                System.out.printf(
                        "\t🔍 [TC.9][%s] base=%s + taxes=%s → perPax=%s × count=%d → groupTotal=%s%n",
                        paxType, baseFare, taxes, perPaxTotal, paxCount, paxGroupTotal
                );
            }

            // Compare aggregated totals with overall total
            System.out.printf("\t🔍 [TC.9] Sum of passenger group totals = %s | Overall total = %s%n",
                    sumOfPassengerTotals, overallTotal);

            softAssert.assertEquals(sumOfPassengerTotals, overallTotal,
                    "❌ [TC.9] Overall total mismatch");

        } else {
            System.out.println("⚠️ [TC.9] No passengerFareBreakdown found in response.");
            softAssert.fail("Missing passengerFareBreakdown in FareConfirm response");
        }

        System.out.println("✅ [TC.9] Fare breakdown calculation check completed.");
    }

    /**
     * TC.10 – Validate No Duplicate Tax Codes
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
        System.out.println("\n📌 [TC.10]: Validating no duplicate tax codes per passenger...");

        List<Map<String, Object>> passengerBreakdowns =
                response.jsonPath().getList("selectedOfferOptions[0].passengerFareBreakdown");

        if (passengerBreakdowns == null || passengerBreakdowns.isEmpty()) {
            System.out.println("⚠️ [TC.10] No passengerFareBreakdown found in response.");
            softAssert.fail("[TC.10] passengerFareBreakdown is missing");
            return;
        }

        for (Map<String, Object> pax : passengerBreakdowns) {
            String paxType = String.valueOf(pax.get("passengerTypeCode"));
            System.out.println("\t🔎 Checking tax codes for passenger type: " + paxType);

            List<Map<String, Object>> taxes = (List<Map<String, Object>>) pax.get("taxesAndFees");
            if (taxes == null) {
                System.out.println("\t⚠️ [TC.10][" + paxType + "] No taxesAndFees found — skipping.");
                continue;
            }

            Set<String> seenCodes = new HashSet<>();
            for (Map<String, Object> tax : taxes) {
                String code = String.valueOf(tax.get("code"));
                System.out.println("\t\tTax code found: " + code);

                if (seenCodes.contains(code)) {
                    softAssert.fail("[TC.10][" + paxType + "] Duplicate tax code found: " + code);
                } else {
                    seenCodes.add(code);
                }
            }
            System.out.println("\t✅ [TC.10][" + paxType + "] No duplicate tax codes found.");
        }

        System.out.println("✅ [TC.10] Duplicate tax codes validation completed for all passengers.");
    }

    /**
     * TC.11 – Validate Price Classes
     * Ensures that the `priceClasses` object exists and for each class:
     *  - priceClassName is not null
     *  - fareType is not null
     *  - rulesAndPenalties list exists and is not empty
     * Example validation:
     *  {
     *    "priceClassName": "Light",
     *    "fareType": "PublicFare",
     *    "rulesAndPenalties": ["Non-Refundable", "Change with fee"]
     *  }
     *
     * @param response   The FareConfirm API response
     * @param softAssert The TestNG SoftAssert instance
     */
    private static void validatePriceClasses(Response response, SoftAssert softAssert) {
        System.out.println("\n📌 [TC.11]: Validating price classes...");

        Map<String, Map<String, Object>> priceClasses =
                response.jsonPath().getMap("priceClasses");

        if (priceClasses == null || priceClasses.isEmpty()) {
            softAssert.fail("[TC.11] priceClasses are missing or empty.");
            System.out.println("❌ [TC.11] priceClasses are missing or empty.");
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
                    "[TC.11] priceClassName is missing in priceClass: " + priceClassKey);
            softAssert.assertNotNull(priceClass.get("fareType"),
                    "[TC.11] fareType is missing in priceClass: " + priceClassKey);

            // Validate rules list
            List<String> rules = (List<String>) priceClass.get("rulesAndPenalties");
            softAssert.assertTrue(rules != null && !rules.isEmpty(),
                    "[TC.11] rulesAndPenalties missing/empty in priceClass: " + priceClassKey);

            System.out.println("\t✅ Validated PriceClass: name=" + priceClass.get("priceClassName")
                    + ", fareType=" + priceClass.get("fareType")
                    + ", rulesCount=" + rules.size());
        }

        System.out.println("✅ [TC.11] All price classes validated successfully.");
    }

    /**
     * TC.12 – Validate Baggage Details
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
        System.out.println("📌 [TC.12]: Validating baggage details...");

        Map<String, Object> responseMap = response.jsonPath().getMap("$");

        Map<String, Object> baggageDetails = (Map<String, Object>) responseMap.get("baggageDetails");

        // 1. Validate baggageDetails presence
        softAssert.assertNotNull(baggageDetails, "[TC.12] baggageDetails is missing.");
        softAssert.assertFalse(baggageDetails.isEmpty(), "[TC.12] baggageDetails is empty.");

        // 2. Iterate and check fields
        for (Map.Entry<String, Object> entry : baggageDetails.entrySet()) {
            String baggageKey = entry.getKey();
            Map<String, Object> baggage = (Map<String, Object>) entry.getValue();

            System.out.println("\t🔎 Validating baggage key: " + baggageKey);

            // Validate key is not null
            softAssert.assertNotNull(baggageKey, "[TC.12] baggage key is null.");

            // Validate carryOn & checkIn baggage presence
            softAssert.assertTrue(baggage.containsKey("carryOnBaggage"),
                    "[TC.12] Missing carryOnBaggage for key: " + baggageKey);
            softAssert.assertTrue(baggage.containsKey("checkInBaggage"),
                    "[TC.12] Missing checkInBaggage for key: " + baggageKey);

            softAssert.assertNotNull(baggage.get("carryOnBaggage"),
                    "[TC.12] carryOnBaggage is null for key: " + baggageKey);
            softAssert.assertNotNull(baggage.get("checkInBaggage"),
                    "[TC.12] checkInBaggage is null for key: " + baggageKey);
        }

        System.out.println("✅ [TC.12] Baggage details validated successfully.");
    }

    /**
     * TC.13 – Validate Passenger Code Uniqueness and Associated Amounts Data
     * Purpose:
     * Ensures that each passengerTypeCode appears only once in the fare breakdown,
     * and verifies that required amount fields exist (not null).
     * Steps:
     * 1. Extract `selectedOfferOptions[0].passengerFareBreakdown` from response.
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
        System.out.println("\n📌 [TC.13]: Validating Passenger Code Uniqueness and Amounts Data...");

        List<Map<String, Object>> passengerBreakdowns =
                response.jsonPath().getList("selectedOfferOptions[0].passengerFareBreakdown");

        if (passengerBreakdowns == null || passengerBreakdowns.isEmpty()) {
            softAssert.fail("[TC.13] passengerFareBreakdown is missing or empty.");
            System.out.println("❌ [TC.13] passengerFareBreakdown is missing or empty.");
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
                    "[TC.13] Duplicate passengerTypeCode found: " + paxType);
            seenTypes.add(paxType);

            // 2. Required amount fields check
            softAssert.assertNotNull(pax.get("passengerTotalAmount"),
                    "[TC.13] passengerTotalAmount missing for passengerTypeCode: " + paxType);
            softAssert.assertNotNull(pax.get("passengerTaxesAmount"),
                    "[TC.13] passengerTaxesAmount missing for passengerTypeCode: " + paxType);
            softAssert.assertNotNull(pax.get("passengerBaseAmount"),
                    "[TC.13] passengerBaseAmount missing for passengerTypeCode: " + paxType);

            System.out.println("\t✅ Validated passengerTypeCode=" + paxType);
        }

        System.out.println("✅ [TC.13] Passenger code uniqueness and amounts check completed.");
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
        compareField("numberOfPassengers", expected, actual, type, softAssert);
        compareField("passengerTotalAmount", expected, actual, type, softAssert);
        compareField("passengerTaxesAmount", expected, actual, type, softAssert);
        compareField("passengerBaseAmount", expected, actual, type, softAssert);
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
