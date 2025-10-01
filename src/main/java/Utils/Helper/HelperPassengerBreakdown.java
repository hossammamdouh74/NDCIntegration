package Utils.Helper;

import org.testng.asserts.SoftAssert;
import java.math.BigDecimal;
import java.util.*;
import java.util.stream.Collectors;

import static Utils.Helper.HelperGetResponse.getAmountOrZero;
import static Utils.Helper.HelperGeneralMethods.assertEqualDoubles;
import static Utils.Helper.HelperGeneralMethods.roundTo2Decimals;

public class HelperPassengerBreakdown {

    /**
     * Aggregates passenger fare breakdown amounts (base, taxes, before discount, total)
     * by passenger type from a list of breakdown maps.
     *
     * @param breakdownList List of maps representing each passenger's fare breakdown.
     * @return A map where:
     *         - Key = passenger type code (e.g., "ADT", "CHD")
     *         - Value = Map of amount type to aggregated BigDecimal value.
     */
    public static Map<String, Map<String, BigDecimal>> aggregateBreakdown(List<Map<String, Object>> breakdownList) {
        Map<String, Map<String, BigDecimal>> aggregated = new HashMap<>();

        // Loop through each passenger entry in the breakdown list
        for (Map<String, Object> pax : breakdownList) {
            String type = (String) pax.get("passengerTypeCode"); // Extract passenger type
            aggregated.putIfAbsent(type, new HashMap<>()); // Ensure a nested map exists for this type
            Map<String, BigDecimal> typeMap = aggregated.get(type);

            // Accumulate amounts for each field, defaulting to zero if missing
            typeMap.put("totalBaseAmount", typeMap.getOrDefault("totalBaseAmount", BigDecimal.ZERO)
                    .add(getAmountOrZero(pax.get("paxBaseAmount"))));

            typeMap.put("totalTaxAmount", typeMap.getOrDefault("totalTaxAmount", BigDecimal.ZERO)
                    .add(getAmountOrZero(pax.get("passengerTaxesAmount"))));
        }

        return aggregated;
    }

    /**
     * Compares passenger breakdown values between booking response and fare confirm response.
     * Ensures amounts match after multiplying fare confirm values by the passenger count.
     *
     * @param bookBreakdown         Booking API's passenger fare breakdown list.
     * @param fareConfirmBreakdown  Fare confirm API's passenger fare breakdown list.
     * @param softAssert            Soft assertion object for non-blocking verification.
     */
    public static void comparePassengerBreakdown(List<Map<String, Object>> bookBreakdown,
                                                 List<Map<String, Object>> fareConfirmBreakdown,
                                                 SoftAssert softAssert) {

        // Count passengers per type in booking breakdown
        Map<String, Long> paxTypeCounts = bookBreakdown.stream()
                .collect(Collectors.groupingBy(p -> (String) p.get("passengerTypeCode"), Collectors.counting()));

        // Aggregate booking breakdown for direct comparison
        Map<String, Map<String, BigDecimal>> bookAggregated = aggregateBreakdown(bookBreakdown);

        // Loop through each fare confirm passenger type
        for (Map<String, Object> fcPax : fareConfirmBreakdown) {
            String paxType = (String) fcPax.get("passengerTypeCode");
            int count = paxTypeCounts.getOrDefault(paxType, 0L).intValue();

            // Multiply fare confirm values by passenger count to get expected totals
            Map<String, BigDecimal> expected = multiplyFareConfirmByCount(fcPax, count);
            Map<String, BigDecimal> actual = bookAggregated.getOrDefault(paxType, new HashMap<>());

            // Compare each field (base, taxes, total, beforeDiscount)
            for (Map.Entry<String, BigDecimal> entry : expected.entrySet()) {
                String field = entry.getKey();
                BigDecimal expectedValue = entry.getValue();
                BigDecimal actualValue = actual.getOrDefault(field, BigDecimal.ZERO);

                assertEqualDoubles(
                        actualValue,
                        expectedValue,
                        String.format("❌ [Offer %d] Mismatch in %s for paxType: %s" ,field, paxType),
                        softAssert
                );
            }
        }
    }

    /**
     * Validates that all passenger references in the offer exist in their respective maps:
     * - Segments
     * - Price classes
     * - Baggage details
     *
     * @param offer          Offer data containing passengerFareBreakdown.
     * @param segments       Map of segmentReferenceId -> segment details.
     * @param priceClasses   Map of priceClassReferenceId -> price class details.
     * @param baggageDetails Map of baggageDetailsReferenceId -> baggage info.
     * @param softAssert     Soft assertion object for reporting.
     * @param offerIndex     Index of the offer (for logging purposes).
     */
    public static void validatePassengerReferences(Map<String, Object> offer,
                                                   Map<String, Object> segments,
                                                   Map<String, Object> priceClasses,
                                                   Map<String, Map<String, String>> baggageDetails,
                                                   SoftAssert softAssert,
                                                   int offerIndex) {

        List<Map<String, Object>> paxBreakdowns =
                (List<Map<String, Object>>) offer.get("passengerFareBreakdown");

        // Fail if passenger breakdown is missing or empty
        if (paxBreakdowns == null || paxBreakdowns.isEmpty()) {
            softAssert.fail(String.format("❌TC.10 Missing passengerFareBreakdown in offer %d", offerIndex));
            return;
        }

        // Validate each passenger's segment references
        for (Map<String, Object> pax : paxBreakdowns) {
            String paxType = (String) pax.get("passengerTypeCode");
            List<Map<String, String>> segmentDetails =
                    (List<Map<String, String>>) pax.get("segmentDetails");

            // Fail if no segment details for this passenger
            if (segmentDetails == null || segmentDetails.isEmpty()) {
                softAssert.fail(String.format("❌TC.10 Missing segmentDetails for pax %s in offer %d", paxType, offerIndex));
                continue;
            }

            for (Map<String, String> segment : segmentDetails) {
                String segmentRefId = segment.get("segmentRefId");
                String priceClassRefId = segment.get("priceClassRefId");
                String baggageRefId = segment.get("baggageDetailsRefId");

                // Validate references exist
                softAssert.assertTrue(segments.containsKey(segmentRefId),
                        String.format("❌TC.10 Segment reference not found: %s (offer: %d, pax: %s)",
                                segmentRefId, offerIndex, paxType));

                softAssert.assertTrue(priceClasses.containsKey(priceClassRefId),
                        String.format("❌TC.10 PriceClass reference not found: %s (offer: %d, pax: %s)",
                                priceClassRefId, offerIndex, paxType));

                softAssert.assertTrue(baggageDetails.containsKey(baggageRefId),
                        String.format("❌TC.10 BaggageDetails reference not found: %s (offer: %d, pax: %s)",
                                baggageRefId, offerIndex, paxType));
            }
        }
    }

    /**
     * Validates that all journeys referenced in the offer exist in the root journeys map.
     *
     * @param offer        Offer map containing journey references.
     * @param rootJourneys Map of journeyId -> journey details.
     * @param softAssert   Soft assertion object.
     * @param offerIndex   Offer index (for logging).
     */
    public static void validateOfferJourneys(Map<String, Object> offer,
                                             Map<String, Object> rootJourneys,
                                             SoftAssert softAssert,
                                             int offerIndex) {
        List<?> offerJourneys = (List<?>) offer.get("offerJourneys");

        // Fail if no journeys found in the offer
        if (offerJourneys == null || offerJourneys.isEmpty()) {
            softAssert.fail(String.format("❌TC.10 Missing or empty journeys in offer %d", offerIndex));
            return;
        }

        // Ensure each journey ID exists in root journeys
        for (int j = 0; j < offerJourneys.size(); j++) {
            String journeyId = offerJourneys.get(j).toString();
            softAssert.assertTrue(rootJourneys.containsKey(journeyId),
                    String.format("❌TC.10 Journey reference not found: %s (offer: %d, index: %d)",
                            journeyId, offerIndex, j));
        }
    }

    /**
     * Multiplies fare confirm passenger amounts by passenger count to match booking totals.
     *
     * @param fareConfirmPax Map of fare confirm passenger data.
     * @param count          Number of passengers of this type.
     * @return Map of amount fields with multiplied values (rounded to 2 decimals).
     */
    private static Map<String, BigDecimal> multiplyFareConfirmByCount(Map<String, Object> fareConfirmPax, int count) {
        Map<String, BigDecimal> result = new HashMap<>();
        BigDecimal countBD = BigDecimal.valueOf(count);

        result.put("totalBaseAmount", roundTo2Decimals(getAmountOrZero(fareConfirmPax.get("paxBaseAmount")).multiply(countBD)));
        result.put("totalTaxAmount", roundTo2Decimals(getAmountOrZero(fareConfirmPax.get("paxTotalTaxAmount")).multiply(countBD)));

        return result;
    }

}