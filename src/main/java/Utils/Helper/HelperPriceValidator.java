package Utils.Helper;

import io.restassured.path.json.JsonPath;
import org.testng.asserts.SoftAssert;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static Utils.Helper.HelperGeneralMethods.assertWithRoundingTolerance;
import static Utils.Helper.HelperGetResponse.getAmountOrZero;
import static Utils.Helper.HelperGetResponse.getPriceDetailsAmount;

/**
 * Utility class for validating price fields in API responses against calculated passenger breakdown totals.
 * This class compares pricing values returned in the API response
 * with values calculated from the passenger breakdown payload.
 */
public class HelperPriceValidator {

    /**
     * Step 9.2: Validate aggregated price fields at the offer level.
     * --------------------------------------------------------------
     * For each price field (baseAmount, taxesAmount, totalAmount, etc.),
     * this method calculates the sum across all passenger breakdowns
     * and compares it against the reported value in priceDetails.
     *
     * @param jsonPath           Parsed API response
     * @param offerOrder         Index of the offer being validated
     * @param passengerBreakdowns Passenger fare breakdown list
     * @param priceFieldMapping  Mapping of priceDetails fields → breakdown fields
     * @param softAssert         TestNG SoftAssert for validation
     */
    public static void validatePriceFields (JsonPath jsonPath,
                                            int offerOrder,
                                            List<Map<String, Object>> passengerBreakdowns,
                                            Map<String, String> priceFieldMapping,
                                            Map<String, Object> payload, SoftAssert softAssert) {
        // Loop through all mappings (e.g., totalAmount ↔ passengerTotalAmount)
        for (Map.Entry<String, String> entry : priceFieldMapping.entrySet()) {
            String priceDetailsField = entry.getKey();    // Field in priceDetails
            String passengerField    = entry.getValue();  // Field in passenger breakdown

            // Calculate expected value from passenger breakdown (full precision)
            BigDecimal calculatedTotal = calculateTotalAmountForPaxBigDecimal(passengerBreakdowns, passengerField, payload);

            // Extract actual value from offer.priceDetails
            BigDecimal reportedAmount  = getPriceDetailsAmount(jsonPath, offerOrder, priceDetailsField);

            // Normalize both values to 2 decimals for comparison
            BigDecimal expected = calculatedTotal.setScale(2, RoundingMode.HALF_UP);
            BigDecimal actual   = reportedAmount.setScale(2, RoundingMode.HALF_UP);

            // Log raw and normalized values for debugging
            System.out.printf(
                    "\tTC:9.2 ✔ [Offer %d] Validating %s: Expected=%.2f, Actual=%.2f (raw calc=%.5f, raw api=%.5f)%n",
                    offerOrder, priceDetailsField,
                    expected.doubleValue(),
                    actual.doubleValue(),
                    calculatedTotal.doubleValue(),
                    reportedAmount.doubleValue()
            );

            // Validate with tolerance check
            assertWithRoundingTolerance(actual, expected, 0.01, priceDetailsField, offerOrder,"TC:9.2" ,softAssert);
        }
    }


    /**
     * Sums all "amount" values from a given list of maps.
     *
     * @param items List of maps, each containing an "amount" field.
     * @return Total sum as BigDecimal.
     */
    public static BigDecimal sumAmountsFromList(List<Map<String, Object>> items) {
        return items.stream()
                .map(item -> getAmountOrZero(item.get("amount"))) // Converts null or invalid values to BigDecimal.ZERO
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    /**
     * Utility: Calculate total amount across passenger breakdowns for a given field (e.g., paxBaseAmount, paxTotalTaxAmount).
     * Multiplies per-passenger amount × count retrieved from the request payload.
     *
     * @param passengerBreakdowns Breakdown list from the response
     * @param fieldName           Field name to aggregate (e.g., "paxBaseAmount", "paxTotalTaxAmount")
     * @param payload             The request payload (contains passengers with type + count)
     * @return BigDecimal total aggregated amount
     */
    private static BigDecimal calculateTotalAmountForPaxBigDecimal(
            List<Map<String, Object>> passengerBreakdowns,
            String fieldName,
            Map<String, Object> payload) {

        BigDecimal total = BigDecimal.ZERO;

        // Build a map from payload passenger types → counts
        List<Map<String, Object>> payloadPassengers = (List<Map<String, Object>>) payload.get("passengers");
        Map<String, Integer> paxCountMap = new HashMap<>();
        for (Map<String, Object> pax : payloadPassengers) {
            String type = ((String) pax.get("passengerTypeCode")).toUpperCase(); // normalize
            Integer count = ((Number) pax.get("count")).intValue();
            paxCountMap.put(type, count);
        }

        // Now aggregate response breakdowns using payload counts
        for (Map<String, Object> passenger : passengerBreakdowns) {
            String paxType = ((String) passenger.get("passengerTypeCode")).toUpperCase();
            int paxCount = paxCountMap.getOrDefault(paxType, 1); // fallback to 1 if not in payload

            Object fieldObj = passenger.get(fieldName);
            if (fieldObj instanceof Map) {
                Map<String, Object> fieldMap = (Map<String, Object>) fieldObj;
                BigDecimal amount = getAmountOrZero(fieldMap);
                total = total.add(amount.multiply(BigDecimal.valueOf(paxCount)));
            }
        }

        return total;
    }


}
