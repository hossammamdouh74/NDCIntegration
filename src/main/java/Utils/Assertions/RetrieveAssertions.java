package Utils.Assertions;

import org.testng.asserts.SoftAssert;

import java.util.*;

/**
 * Utility class providing assertion methods to validate
 * that the Retrieve booking response matches the Book response.
 * Responsibilities:
 * - Field-by-field validation (IDs, journeys, segments, passengers, order, baggage, priceClass, taxes, etc.)
 * - Clear logging with TC IDs for traceability
 * - Soft assertions for aggregated reporting
 */
public class RetrieveAssertions {

    /**
     * Main entry point:
     * Validates that the Retrieve booking response matches the Book response.
     *
     * @param bookMap     Book response data
     * @param retrieveMap Retrieve response data
     * @param softAssert  Soft assertion instance for aggregating assertion results
     */
    public static void validateRetrievePNR(Map<String, Object> bookMap,
                                                    Map<String, Object> retrieveMap,
                                                    SoftAssert softAssert) {
        System.out.println("========== START VALIDATING RETRIEVE vs BOOK ==========");

        // TC.1 - Validate airlinePnr
        assertTopLevelFieldEquals("airlinePnr", bookMap, retrieveMap, softAssert, 1);

        // TC.2 - Validate gdsPnr
        assertTopLevelFieldEquals("gdsPnr", bookMap, retrieveMap, softAssert, 2);

        // TC.3 - Validate ndcBookingReference
        assertTopLevelFieldEquals("ndcBookingReference", bookMap, retrieveMap, softAssert, 3);

        // TC.4 - Validate bookingToken
        assertBookingTokenEquals(bookMap, retrieveMap, softAssert, 4);

        // TC.5 - Validate journeys
        assertNestedMapEquals("journeys", bookMap, retrieveMap, softAssert, 5);

        // TC.6 - Validate segments
        assertNestedMapEquals("segments", bookMap, retrieveMap, softAssert, 6);

        // TC.7 - Validate passengers
        assertNestedMapEquals("passengers", bookMap, retrieveMap, softAssert, 7);

        // TC.8 - Validate baggage details
        assertBaggageDetailsEquals(bookMap, retrieveMap, softAssert, 8);

        // TC.9 - Validate priceClass
        assertPriceClassEquals(bookMap, retrieveMap, softAssert, 9);

        // TC.10 - Validate order (priceDetails + passengerFareBreakdown)
        assertOrderEquals(bookMap, retrieveMap, softAssert, 10);

        // TC.11 - Validate passengerFareBreakdown
        assertPassengerFareBreakdownEquals(bookMap, retrieveMap, softAssert, 11);

        // TC.12 - Validate priceDetails totals and taxes
        assertPriceDetailsEquals(bookMap, retrieveMap, softAssert, 12);

        System.out.println("========== VALIDATION COMPLETE ==========\n");
    }

    // ==============================================================
    // ============== INDIVIDUAL ASSERTION METHODS ==================
    // ==============================================================

    /**
     * Compares a top-level field between Book and Retrieve.
     */
    private static void assertTopLevelFieldEquals(String fieldName,
                                                  Map<String, Object> bookMap,
                                                  Map<String, Object> retrieveMap,
                                                  SoftAssert softAssert,
                                                  int tcId) {
        System.out.println("\n--- TC." + tcId + ": Comparing top-level field: " + fieldName + " ---");
        Object bookVal = bookMap.get(fieldName);
        Object retrieveVal = retrieveMap.get(fieldName);

        if (!Objects.equals(bookVal, retrieveVal)) {
            System.out.printf("❌ %s mismatch: Book='%s' vs Retrieve='%s'%n", fieldName, bookVal, retrieveVal);
        } else {
            System.out.printf("✅ %s matches: '%s'%n", fieldName, bookVal);
        }
        softAssert.assertEquals(retrieveVal, bookVal, "Mismatch in " + fieldName);
    }

    /**
     * Validate bookingToken exists and matches.
     */
    private static void assertBookingTokenEquals(Map<String, Object> bookMap,
                                                 Map<String, Object> retrieveMap,
                                                 SoftAssert softAssert,
                                                 int tcId) {
        System.out.println("\n--- TC." + tcId + ": Validating bookingToken ---");
        Object bookVal = bookMap.get("bookingToken");
        Object retrieveVal = retrieveMap.get("bookingToken");

        if (retrieveVal == null) {
            System.out.println("❌ bookingToken is missing in Retrieve response");
        } else if (!Objects.equals(bookVal, retrieveVal)) {
            System.out.printf("❌ bookingToken mismatch: Book='%s' vs Retrieve='%s'%n", bookVal, retrieveVal);
        } else {
            System.out.printf("✅ bookingToken matches: '%s'%n", bookVal);
        }

        softAssert.assertNotNull(retrieveVal, "bookingToken should exist in Retrieve");
        softAssert.assertEquals(retrieveVal, bookVal, "Mismatch in bookingToken");
    }

    /**
     * Compares a nested map field between Book and Retrieve.
     */
    private static void assertNestedMapEquals(String fieldName,
                                              Map<String, Object> bookMap,
                                              Map<String, Object> retrieveMap,
                                              SoftAssert softAssert,
                                              int tcId) {
        System.out.println("\n--- TC." + tcId + ": Comparing nested field: " + fieldName + " ---");
        Map<String, Object> bookNested = getMap(bookMap, fieldName);
        Map<String, Object> retrieveNested = getMap(retrieveMap, fieldName);

        if (!Objects.equals(bookNested, retrieveNested)) {
            System.out.printf("❌ %s mismatch:%nBOOK: %s%nRETRIEVE: %s%n", fieldName, bookNested, retrieveNested);
        } else {
            System.out.printf("✅ %s matches%n", fieldName);
        }

        softAssert.assertEquals(retrieveNested, bookNested, "Mismatch in nested map: " + fieldName);
    }

    /**
     * Validate baggage details.
     */
    private static void assertBaggageDetailsEquals(Map<String, Object> bookMap,
                                                   Map<String, Object> retrieveMap,
                                                   SoftAssert softAssert,
                                                   int tcId) {
        assertNestedMapEquals("baggageDetails", bookMap, retrieveMap, softAssert, tcId);
    }

    /**
     * Validate priceClass (code + name only).
     */
    private static void assertPriceClassEquals(Map<String, Object> bookMap,
                                               Map<String, Object> retrieveMap,
                                               SoftAssert softAssert,
                                               int tcId) {
        System.out.println("\n--- TC." + tcId + ": Validating priceClasses ---");
        Map<String, Object> bookClasses = getMap(bookMap, "priceClasses");
        Map<String, Object> retrieveClasses = getMap(retrieveMap, "priceClasses");

        for (String key : bookClasses.keySet()) {
            Map<String, Object> bookClass = getMap(bookClasses, key);
            Map<String, Object> retrieveClass = getMap(retrieveClasses, key);

            if (retrieveClass == null) {
                System.out.println("❌ Missing priceClass in Retrieve: " + key);
                softAssert.fail("Missing priceClass: " + key);
                continue;
            }

            Object bookName = bookClass.get("priceClassName");
            Object retrieveName = retrieveClass.get("priceClassName");

            if (!Objects.equals(bookName, retrieveName)) {
                System.out.printf("❌ priceClassName mismatch for %s: Book='%s' vs Retrieve='%s'%n",
                        key, bookName, retrieveName);
            } else {
                System.out.printf("✅ priceClassName matches for %s: '%s'%n", key, bookName);
            }

            softAssert.assertEquals(retrieveName, bookName, "Mismatch in priceClassName for " + key);
        }
    }

    /**
     * Validate order object: priceDetails and passengerFareBreakdown.
     */
    private static void assertOrderEquals(Map<String, Object> bookMap,
                                          Map<String, Object> retrieveMap,
                                          SoftAssert softAssert,
                                          int tcId) {
        System.out.println("\n--- TC." + tcId + ": Validating order object ---");
        Map<String, Object> bookOrder = getMap(bookMap, "order");
        Map<String, Object> retrieveOrder = getMap(retrieveMap, "order");

        if (bookOrder.isEmpty() && retrieveOrder.isEmpty()) {
            System.out.println("⚠️ Order object not found in either response, skipping check.");
            return;
        }

        if (!Objects.equals(bookOrder, retrieveOrder)) {
            System.out.printf("❌ Order mismatch:%nBOOK: %s%nRETRIEVE: %s%n", bookOrder, retrieveOrder);
        } else {
            System.out.println("✅ Order matches.");
        }

        softAssert.assertEquals(retrieveOrder, bookOrder, "Mismatch in order object");
    }

    /**
     * Validate passengerFareBreakdown.
     */
    private static void assertPassengerFareBreakdownEquals(Map<String, Object> bookMap,
                                                           Map<String, Object> retrieveMap,
                                                           SoftAssert softAssert,
                                                           int tcId) {
        System.out.println("\n--- TC." + tcId + ": Comparing passengerFareBreakdown ---");
        List<Map<String, Object>> bookPFB = getList(getMap(bookMap, "order"), "passengerFareBreakdown");
        List<Map<String, Object>> retrievePFB = getList(getMap(retrieveMap, "order"), "passengerFareBreakdown");

        if (bookPFB.size() != retrievePFB.size()) {
            System.out.printf("❌ passengerFareBreakdown size mismatch: Book=%d, Retrieve=%d%n",
                    bookPFB.size(), retrievePFB.size());
        } else {
            System.out.printf("✅ passengerFareBreakdown size matches: %d%n", bookPFB.size());
        }
        softAssert.assertEquals(retrievePFB.size(), bookPFB.size(),
                "Mismatch in passengerFareBreakdown size");

        for (int i = 0; i < Math.min(bookPFB.size(), retrievePFB.size()); i++) {
            Map<String, Object> bookEntry = bookPFB.get(i);
            Map<String, Object> retrieveEntry = retrievePFB.get(i);

            for (String key : bookEntry.keySet()) {
                Object bookVal = bookEntry.get(key);
                Object retrieveVal = retrieveEntry.get(key);

                if (!Objects.equals(bookVal, retrieveVal)) {
                    System.out.printf("❌ passengerFareBreakdown[%d].%s: Book='%s' vs Retrieve='%s'%n",
                            i, key, bookVal, retrieveVal);
                } else {
                    System.out.printf("✅ passengerFareBreakdown[%d].%s matches%n", i, key);
                }

                softAssert.assertEquals(retrieveVal, bookVal,
                        "Mismatch in passengerFareBreakdown[" + i + "]." + key);
            }
        }
    }

    /**
     * Validate priceDetails totals and taxes.
     */
    private static void assertPriceDetailsEquals(Map<String, Object> bookMap,
                                                 Map<String, Object> retrieveMap,
                                                 SoftAssert softAssert,
                                                 int tcId) {
        System.out.println("\n--- TC." + tcId + ": Validating priceDetails totals and taxes ---");
        Map<String, Object> bookPrice = getMap(bookMap, "priceDetails");
        Map<String, Object> retrievePrice = getMap(retrieveMap, "priceDetails");

        // Totals
        compareField("totalAmount", bookPrice, retrievePrice, softAssert);
        compareField("baseAmount", bookPrice, retrievePrice, softAssert);
        compareField("taxesAmount", bookPrice, retrievePrice, softAssert);

        // taxesAndFees
        List<Map<String, Object>> bookTaxes = getList(bookPrice, "taxesAndFees");
        List<Map<String, Object>> retrieveTaxes = getList(retrievePrice, "taxesAndFees");

        if (bookTaxes.size() != retrieveTaxes.size()) {
            System.out.printf("❌ taxesAndFees size mismatch: Book=%d, Retrieve=%d%n",
                    bookTaxes.size(), retrieveTaxes.size());
        } else {
            System.out.printf("✅ taxesAndFees size matches: %d%n", bookTaxes.size());
        }
        softAssert.assertEquals(retrieveTaxes.size(), bookTaxes.size(),
                "Mismatch in taxesAndFees size");

        for (int i = 0; i < Math.min(bookTaxes.size(), retrieveTaxes.size()); i++) {
            Map<String, Object> bookTax = bookTaxes.get(i);
            Map<String, Object> retrieveTax = retrieveTaxes.get(i);

            if (!Objects.equals(bookTax, retrieveTax)) {
                System.out.printf("❌ taxesAndFees[%d] mismatch:%nBOOK: %s%nRETRIEVE: %s%n",
                        i, bookTax, retrieveTax);
            } else {
                System.out.printf("✅ taxesAndFees[%d] matches%n", i);
            }

            softAssert.assertEquals(retrieveTax, bookTax,
                    "Mismatch in taxesAndFees at index " + i);
        }
    }

    // ==============================================================
    // ============== HELPER METHODS ================================
    // ==============================================================

    private static void compareField(String field,
                                     Map<String, Object> book,
                                     Map<String, Object> retrieve,
                                     SoftAssert softAssert) {
        Object bookVal = book.get(field);
        Object retrieveVal = retrieve.get(field);

        if (!Objects.equals(bookVal, retrieveVal)) {
            System.out.printf("❌ %s mismatch: Book='%s' vs Retrieve='%s'%n", field, bookVal, retrieveVal);
        } else {
            System.out.printf("✅ %s matches: '%s'%n", field, bookVal);
        }

        softAssert.assertEquals(retrieveVal, bookVal, "Mismatch in " + field);
    }
    private static Map<String, Object> getMap(Map<String, Object> parent, String key) {
        return (Map<String, Object>) parent.getOrDefault(key, Map.of());
    }
    private static List<Map<String, Object>> getList(Map<String, Object> parent, String key) {
        return (List<Map<String, Object>>) parent.getOrDefault(key, List.of());
    }
}
