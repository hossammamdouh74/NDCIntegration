package Utils.Helper;

import io.restassured.path.json.JsonPath;
import org.testng.asserts.SoftAssert;
import java.math.BigDecimal;
import java.math.RoundingMode;
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
    public static void validatePriceFields(JsonPath jsonPath, int offerOrder,
                                           List<Map<String, Object>> passengerBreakdowns,
                                           Map<String, String> priceFieldMapping,
                                           SoftAssert softAssert) {
        // Loop through all mappings (e.g., totalAmount ↔ passengerTotalAmount)
        for (Map.Entry<String, String> entry : priceFieldMapping.entrySet()) {
            String priceDetailsField = entry.getKey();    // Field in priceDetails
            String passengerField    = entry.getValue();  // Field in passenger breakdown

            // Calculate expected value from passenger breakdown (full precision)
            BigDecimal calculatedTotal = calculateTotalAmountForPaxBigDecimal(passengerBreakdowns, passengerField);

            // Extract actual value from offer.priceDetails
            BigDecimal reportedAmount  = getPriceDetailsAmount(jsonPath, offerOrder, priceDetailsField);

            // Normalize both values to 2 decimals for comparison
            BigDecimal expected = calculatedTotal.setScale(2, RoundingMode.HALF_UP);
            BigDecimal actual   = reportedAmount.setScale(2, RoundingMode.HALF_UP);

            // Log raw and normalized values for debugging
            System.out.printf(
                    "\t✔ [Offer %d] Validating %s: Expected=%.2f, Actual=%.2f (raw calc=%.5f, raw api=%.5f)%n",
                    offerOrder, priceDetailsField,
                    expected.doubleValue(),
                    actual.doubleValue(),
                    calculatedTotal.doubleValue(),
                    reportedAmount.doubleValue()
            );

            // Validate with tolerance check
            assertWithRoundingTolerance(actual, expected, 0.01, priceDetailsField, offerOrder, softAssert);
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
     * Calculates the total amount for a specific field in passenger breakdown data.
     * Example: If fieldName = "baseFare" and a passenger has 2 tickets,
     * total = baseFare × numberOfPassengers (summed for all passenger types).
     *
     * @param passengerBreakdowns List of passenger breakdown maps.
     * @param fieldName           Field in passenger breakdown to sum (e.g., "baseFare", "taxes").
     * @return Total amount as BigDecimal.
     */
    private static BigDecimal calculateTotalAmountForPaxBigDecimal(List<Map<String, Object>> passengerBreakdowns,
                                                                   String fieldName) {
        BigDecimal total = BigDecimal.ZERO;

        for (Map<String, Object> passenger : passengerBreakdowns) {
            Number count = (Number) passenger.get("numberOfPassengers"); // Number of passengers for this breakdown

            // Retrieve field data (should be a map containing "amount")
            Object fieldObj = passenger.get(fieldName);
            if (fieldObj instanceof Map && count != null) {
                Map<String, Object> fieldMap = (Map<String, Object>) fieldObj;
                BigDecimal amount = getAmountOrZero(fieldMap); // Convert to BigDecimal
                BigDecimal countBD = new BigDecimal(count.toString());
                total = total.add(amount.multiply(countBD)); // Multiply fare by passenger count
            }
        }

        return total;
    }
}
