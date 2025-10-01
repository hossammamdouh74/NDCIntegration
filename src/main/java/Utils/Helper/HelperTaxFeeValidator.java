package Utils.Helper;

import io.restassured.path.json.JsonPath;
import org.testng.asserts.SoftAssert;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;
import java.util.Map;

import static Utils.Helper.HelperGeneralMethods.assertWithRoundingTolerance;
import static Utils.Helper.HelperGetResponse.getAmountOrZero;
import static Utils.Helper.HelperPriceValidator.sumAmountsFromList;

/**
 * Utility class for validating tax and fee amounts in both passenger-level breakdowns
 * and offer-level price details.
 *
 * <p>This class ensures that:
 * <ul>
 *   <li>Taxes and fees per passenger type match the reported totals in the response</li>
 *   <li>Total taxes in offer price details match the sum of individual tax/fee entries</li>
 * </ul>
 *
 * <p>Used in API response validations to maintain pricing accuracy across different data sections.
 */
public class HelperTaxFeeValidator {

    /**
     * Step 9.3: Validate taxesAndFees per passenger type.
     * ---------------------------------------------------
     * For each passenger type (ADT, CHD, INF):
     *  - Sum up all individual taxes and fees
     *  - Compare directly with passengerTaxesAmount
     *
     * @param passengerBreakdowns Passenger fare breakdown list
     * @param offerOrder          Index of the offer being validated
     * @param softAssert          TestNG SoftAssert for validation
     */
    public static void validateTaxesAndFeesPerPassenger(List<Map<String, Object>> passengerBreakdowns,
                                                        int offerOrder, SoftAssert softAssert) {
        // Iterate through all passenger types in the breakdown
        for (Map<String, Object> passenger : passengerBreakdowns) {
            // Extract taxesAndFees breakdown list for this passenger type
            List<Map<String, Object>> taxesAndFeesList = (List<Map<String, Object>>) passenger.get("taxesAndFees");
            // Extract passenger type code (ADT, CHD, INF, etc.)
            String paxType = (String) passenger.get("passengerTypeCode");

            // Skip validation if breakdown is missing
            if (taxesAndFeesList == null) {
                System.out.printf("⚠️ Skipped taxesAndFees validation for passengerTypeCode=%s due to null values%n", paxType);
                continue;
            }

            // Calculate expected taxes by summing breakdown (ignore passenger count)
            BigDecimal sumTaxesAndFees = sumAmountsFromList(taxesAndFeesList).setScale(4, RoundingMode.HALF_UP);

            // Extract reported passengerTaxesAmount
            Map<String, Object> passengerTaxesAmountMap = (Map<String, Object>) passenger.get("paxTotalTaxAmount");
            BigDecimal reportedTaxesAmount = getAmountOrZero(passengerTaxesAmountMap).setScale(4, RoundingMode.HALF_UP);

            // Log calculated vs reported for debugging
            System.out.printf("\tTC:9.3✔ [Offer %d] Validating taxes for %s: Calculated=%.2f, Reported=%.2f%n",
                    offerOrder, paxType, sumTaxesAndFees.doubleValue(), reportedTaxesAmount.doubleValue());

            // Validate with tolerance check (rounded to 2 decimals)
            assertWithRoundingTolerance(
                    reportedTaxesAmount.setScale(2, RoundingMode.HALF_UP),
                    sumTaxesAndFees.setScale(2, RoundingMode.HALF_UP),
                    0.01,
                    "taxes for " + paxType,
                    offerOrder, "TC:9.3" ,
                    softAssert
            );
        }
    }


    /**
     * Step 9.4: Validate aggregated total taxes in priceDetails.
     * ----------------------------------------------------------
     * - Retrieve the total taxes reported at offer-level (priceDetails.taxesAmount)
     * - Retrieve the breakdown list of taxesAndFees at offer-level
     * - Compare the reported vs calculated total
     * If no breakdown exists, log info and skip strict validation.
     *
     * @param jsonPath   Parsed API response
     * @param offerOrder Index of the offer being validated
     * @param softAssert TestNG SoftAssert for validation
     */
    public static void validateTotalTaxesAndFeesInPriceDetails(JsonPath jsonPath, int offerOrder, SoftAssert softAssert) {
        // Get full list of taxesAndFees from priceDetails
        List<Map<String, Object>> priceTaxesAndFeesList =
                jsonPath.getList("offers[" + offerOrder + "].priceDetails.taxesAndFees");

        // Get reported total taxes amount from priceDetails
        Map<String, Object> priceDetailsTaxesAmountMap =
                jsonPath.getMap("offers[" + offerOrder + "].priceDetails.totalTaxAmount");

        // Normalize reported amount
        BigDecimal reported = getAmountOrZero(priceDetailsTaxesAmountMap).setScale(2, RoundingMode.HALF_UP);

        // Case 1: No breakdown provided → log info only
        if (priceTaxesAndFeesList == null || priceTaxesAndFeesList.isEmpty()) {
            System.out.printf("\tℹ️ [Offer %d] No taxesAndFees breakdown provided. Reported Total Taxes = %.2f%n",
                    offerOrder, reported.doubleValue());
            return;
        }

        // Case 2: Breakdown exists → calculate expected sum
        BigDecimal calculated = sumAmountsFromList(priceTaxesAndFeesList).setScale(2, RoundingMode.HALF_UP);

        // Log calculated vs reported for debugging
        System.out.printf("\t✔TC:9.4 [Offer %d] Validating total taxes in priceDetails: Calculated=%.2f, Reported=%.2f%n",
                offerOrder, calculated.doubleValue(), reported.doubleValue());

        // Validate with tolerance check
        assertWithRoundingTolerance(reported, calculated, 0.01,
                "total taxes in priceDetails", offerOrder,"TC:9.4" ,softAssert);
    }
}
