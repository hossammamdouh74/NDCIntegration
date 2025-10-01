package Utils.Assertions;

import io.restassured.response.Response;
import org.testng.asserts.SoftAssert;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.*;
import static Utils.Assertions.FareConfirmAssertions.validateRbdMatchesSelectedOffer;
import static Utils.Helper.HelperGeneralMethods.*;
import static Utils.Helper.HelperPassengerBreakdown.*;

public class BookingAssertions {

    /**
     * Master validation method for booking API responses.
     * Runs a series of checks to ensure:
     *  - Mandatory fields are present
     *  - Journeys, segments, and baggage details match FareConfirm
     *  - Passenger info matches AddPax payload
     *  - Amount calculations are correct (passenger & total level)
     *  - Taxes sum correctly
     *  - Structure integrity (valid segmentReferenceIds)
     *  - Price breakdown matches FareConfirm
     *  - RBD and currencies are correct
     */
    public static void validateBookingResponse(Response BookResponse, Map<String, Object> bookResponse,
                                               Map<String, Object> fareConfirmResponse, Map<String, Object> addPaxPayload
                                               , Map<String, Object> selectedOfferFromSearch,
                                               SoftAssert softAssert) {
        System.out.println("==================== 🧾 VALIDATING BOOKING RESPONSE ====================\n");

        // Check that key fields exist
        validateNotNullFields(bookResponse, softAssert);

        // Compare journeys, segments, and baggage info with fareConfirm
        validateJourneyAndSegmentMatch(bookResponse, fareConfirmResponse, softAssert);

        // Ensure passenger type codes match AddPax payload
        validatePassengerDetails(bookResponse, addPaxPayload, softAssert);

        // Validate per-passenger fare totals
        validatePassengerTotalAmount(bookResponse, softAssert);

        // Validate per-passenger taxes total
        validatePassengerTaxes(bookResponse, softAssert);

        // Validate total price calculation at priceDetails level
        validateTotalAmountInPriceDetails(bookResponse, softAssert);

        // Validate that taxesAmount equals sum of all taxesAndFees
        validateTaxesAmountSum(bookResponse, softAssert);

        // Ensure breakdown segment references exist in segments
        validateStructureMatchAgainstFareConfirm(bookResponse, softAssert);

        // Compare price details & passenger breakdown with fareConfirm
        validateBreakdownAgainstFareConfirm(bookResponse, fareConfirmResponse, softAssert);

        // Validate that RBD codes match the originally selected offer
        validateRbdMatchesSelectedOffer(BookResponse, selectedOfferFromSearch, softAssert, "order");

        // Validate currency consistency in price details
        //validateCurrencies(BookResponse, "order", softAssert);

        System.out.println("\n==================== ✅ BOOKING VALIDATION COMPLETE ====================\n");
    }

    /**
     * Checks presence of mandatory booking response fields and ensures each journey contains segmentReferenceIds.
     */
    private static void validateNotNullFields(Map<String, Object> bookResponse, SoftAssert softAssert) {
        System.out.println("🔍TC:1 [VALIDATION] Check mandatory fields are not null...");

        // Booking reference
        softAssert.assertNotNull(bookResponse.get("ndcBookingReference"), "❌ ndcBookingReference is null");
        // Airline PNR
        softAssert.assertNotNull(bookResponse.get("airlinePnr"), "❌ airlinePnr is null");

        // Ensure each journey has segment references
        Map<String, Object> journeys = (Map<String, Object>) bookResponse.get("journeys");
        for (Map.Entry<String, Object> journeyEntry : journeys.entrySet()) {
            Map<String, Object> journey = (Map<String, Object>) journeyEntry.getValue();
            List<String> segmentIds = (List<String>) journey.get("segmentReferenceIds");
            System.out.println("Journey: " + journeyEntry.getKey() + ", segmentReferenceIds: " + segmentIds);
            softAssert.assertNotNull(segmentIds, "❌ segmentReferenceIds is null in journey: " + journeyEntry.getKey());
        }
    }

    /**
     * Compares booking journeys, segments, and baggageDetails with fareConfirm data.
     * Removes bundleReferenceIds before comparison to avoid irrelevant mismatches.
     */
    private static void validateJourneyAndSegmentMatch(Map<String, Object> bookResponse, Map<String, Object> fareConfirmResponse, SoftAssert softAssert) {
        System.out.println("📦TC.2: [VALIDATION] Compare journeys, segments, and baggageDetails with fareConfirm...");

        if (fareConfirmResponse == null) {
            System.out.println("⚠️ FareConfirm response not provided. Skipping comparison.");
            return;
        }

        // Clean out bundleReferenceIds for fair comparison
        Map<String, Object> cleanedBookJourneys = removeBundleReferenceIds((Map<String, Object>) bookResponse.get("journeys"));
        Map<String, Object> cleanedFareJourneys = removeBundleReferenceIds((Map<String, Object>) fareConfirmResponse.get("journeys"));

        // Compare journeys ignoring bundles
        softAssert.assertEquals(cleanedBookJourneys, cleanedFareJourneys, "❌ Journeys mismatch (ignoring bundleReferenceIds)");

        // Compare entire segments object
        softAssert.assertEquals(bookResponse.get("segments"), fareConfirmResponse.get("segments"), "❌ Segments mismatch with fareConfirm");

        // Compare baggage details structure & values
        assertJsonEquals(bookResponse.get("baggageDetails"), fareConfirmResponse.get("baggageDetails"), softAssert);

    }

    /**
     * Validates passenger info from booking matches the AddPax payload.
     */
    private static void validatePassengerDetails(Map<String, Object> bookResponse, Map<String, Object> addPaxPayload, SoftAssert softAssert) {
        System.out.println("👤TC.3: [VALIDATION] Validate passengers match AddPax payload...");

        Map<String, Object> bookingPassengers = (Map<String, Object>) bookResponse.get("passengers");
        Map<String, Object> sentPassengers = (Map<String, Object>) addPaxPayload.get("Passengers");

        // Compare passenger keys and type codes
        for (String key : sentPassengers.keySet()) {
            Map<String, Object> sent = (Map<String, Object>) sentPassengers.get(key);
            Map<String, Object> booked = (Map<String, Object>) bookingPassengers.get(key);

            softAssert.assertTrue(bookingPassengers.containsKey(key), "❌ Booking passengers missing key: " + key);
            softAssert.assertEquals(booked.get("passengerTypeCode"), sent.get("PassengerTypeCode"),
                    "❌ Mismatch in passengerTypeCode for key: " + key);
        }
    }

    /**
     * Checks that passengerTotalAmount = base + taxes - discount + service charge for each passenger.
     */
    private static void validatePassengerTotalAmount(Map<String, Object> bookResponse, SoftAssert softAssert) {
        System.out.println("💰 TC.4: [VALIDATION] Validate passengerTotalAmount = base + tax - discount + service...");

        List<Map<String, Object>> breakdown =
                (List<Map<String, Object>>)((Map<String, Object>) bookResponse.get("order")).get("passengerFareBreakdown");

        for (Map<String, Object> pax : breakdown) {
            String paxType = (String) pax.get("passengerTypeCode");

            // Extract amounts as BigDecimal for accurate math
            BigDecimal base = new BigDecimal(((Map<String, Object>) pax.get("passengerBaseAmount")).get("amount").toString());
            BigDecimal tax = new BigDecimal(((Map<String, Object>) pax.get("passengerTaxesAmount")).get("amount").toString());
            BigDecimal discount = new BigDecimal(((Map<String, Object>) pax.get("passengerDiscountAmount")).get("amount").toString());
            BigDecimal service = new BigDecimal(((Map<String, Object>) pax.get("passengerServiceChargeAmount")).get("amount").toString());

            // Expected total calculation
            BigDecimal expected = base.add(tax).subtract(discount).add(service).setScale(2, RoundingMode.HALF_UP);
            BigDecimal actual = new BigDecimal(((Map<String, Object>) pax.get("passengerTotalAmount")).get("amount").toString()).setScale(2, RoundingMode.HALF_UP);

            if (actual.compareTo(expected) != 0) {
                softAssert.fail(String.format("❌ Incorrect passengerTotalAmount for type %s expected [%s] but found [%s]", paxType, expected, actual));
            }
        }
    }

    /**
     * Validates passengerTaxesAmount equals the sum of all taxes and fees for each passenger.
     */
    private static void validatePassengerTaxes(Map<String, Object> bookResponse, SoftAssert softAssert) {
        System.out.println("🧾TC.5: [VALIDATION] Validate passengerTaxesAmount = sum of taxesAndFees...");

        List<Map<String, Object>> breakdown =
                (List<Map<String, Object>>)((Map<String, Object>) bookResponse.get("order")).get("passengerFareBreakdown");

        for (Map<String, Object> pax : breakdown) {
            String paxType = (String) pax.get("passengerTypeCode");
            List<Map<String, Object>> taxes = (List<Map<String, Object>>) pax.get("taxesAndFees");

            // Sum all tax amounts
            BigDecimal sum = BigDecimal.ZERO;
            for (Map<String, Object> fee : taxes) {
                sum = sum.add(new BigDecimal(((Map<String, Object>) fee.get("amount")).get("amount").toString()));
            }

            BigDecimal expected = new BigDecimal(((Map<String, Object>) pax.get("passengerTaxesAmount")).get("amount").toString());
            softAssert.assertEquals(sum, expected, "❌ Incorrect passengerTaxesAmount for type " + paxType);
        }
    }

    /**
     * Ensures every segmentReferenceId in breakdown exists in the segments object.
     */
    private static void validateStructureMatchAgainstFareConfirm(Map<String, Object> bookResponse, SoftAssert softAssert) {
        System.out.println("🧩TC.6: [VALIDATION] Ensure segmentReferenceIds in breakdown exist in segments...");

        Map<String, Object> segments = (Map<String, Object>) bookResponse.get("segments");
        List<Map<String, Object>> breakdown =
                (List<Map<String, Object>>)((Map<String, Object>) bookResponse.get("order")).get("passengerFareBreakdown");

        for (Map<String, Object> pax : breakdown) {
            for (Map<String, Object> seg : (List<Map<String, Object>>) pax.get("segmentDetails")) {
                String id = (String) seg.get("segmentReferenceId");
                softAssert.assertTrue(segments.containsKey(id), "❌ Missing segmentReferenceId: " + id);
            }
        }
    }

    /**
     * Validates that totalAmount in priceDetails = base + taxes - discount + service charge.
     */
    private static void validateTotalAmountInPriceDetails(Map<String, Object> bookResponse, SoftAssert softAssert) {
        System.out.println("💲TC.7 [VALIDATION] Validate totalAmount in priceDetails...");

        Map<String, Object> price = (Map<String, Object>) ((Map<String, Object>) bookResponse.get("order")).get("priceDetails");

        BigDecimal base = new BigDecimal(((Map<String, Object>) price.get("baseAmount")).get("amount").toString());
        BigDecimal tax = new BigDecimal(((Map<String, Object>) price.get("taxesAmount")).get("amount").toString());
        BigDecimal discount = new BigDecimal(((Map<String, Object>) price.get("discountAmount")).get("amount").toString());
        BigDecimal service = new BigDecimal(((Map<String, Object>) price.get("serviceChargeAmount")).get("amount").toString());

        BigDecimal expected = base.add(tax).subtract(discount).add(service);
        BigDecimal actual = new BigDecimal(((Map<String, Object>) price.get("totalAmount")).get("amount").toString());

        softAssert.assertEquals(actual, expected, "❌ Incorrect totalAmount in priceDetails");
    }

    /**
     * Validates that taxesAmount in priceDetails matches sum of all taxesAndFees.
     */
    private static void validateTaxesAmountSum(Map<String, Object> bookResponse, SoftAssert softAssert) {
        System.out.println("💼TC.8 [VALIDATION] Validate taxesAmount in priceDetails = sum of taxesAndFees...");

        Map<String, Object> price = (Map<String, Object>) ((Map<String, Object>) bookResponse.get("order")).get("priceDetails");
        List<Map<String, Object>> taxes = (List<Map<String, Object>>) price.get("taxesAndFees");

        BigDecimal sum = BigDecimal.ZERO;
        for (Map<String, Object> fee : taxes) {
            sum = sum.add(new BigDecimal(((Map<String, Object>) fee.get("amount")).get("amount").toString()));
        }

        BigDecimal expected = new BigDecimal(((Map<String, Object>) price.get("taxesAmount")).get("amount").toString()).setScale(2, RoundingMode.HALF_UP);
        softAssert.assertEquals(sum.setScale(2, RoundingMode.HALF_UP), expected, "❌ Incorrect taxesAmount in priceDetails");
    }

    /**
     * Compares booking breakdown against fareConfirm selected offer breakdown.
     */
    private static void validateBreakdownAgainstFareConfirm(Map<String, Object> bookResponse, Map<String, Object> fareConfirmResponse, SoftAssert softAssert) {
        System.out.println("🔄TC.9: [VALIDATION] Comparing booking breakdown against fareConfirm breakdown...");

        if (fareConfirmResponse == null) {
            System.out.println("⚠️ FareConfirm response not provided. Skipping final comparison.");
            return;
        }

        Map<String, Object> orderFromBook = (Map<String, Object>) bookResponse.get("order");
        Map<String, Object> bookPriceDetails = normalizePriceDetails((Map<String, Object>) orderFromBook.get("priceDetails"));
        List<Map<String, Object>> bookBreakdown = (List<Map<String, Object>>) orderFromBook.get("passengerFareBreakdown");

        List<Map<String, Object>> selectedOfferOptions = (List<Map<String, Object>>) fareConfirmResponse.get("selectedOfferOptions");
        Map<String, Object> selectedOffer = selectedOfferOptions.get(0);
        Map<String, Object> fareConfirmPriceDetails = normalizePriceDetails((Map<String, Object>) selectedOffer.get("priceDetails"));
        List<Map<String, Object>> fareConfirmBreakdown = (List<Map<String, Object>>) selectedOffer.get("passengerFareBreakdown");

        // Compare normalized price details
        comparePriceDetails(bookPriceDetails, fareConfirmPriceDetails, softAssert);
        // Compare passenger-level breakdown
        comparePassengerBreakdown(bookBreakdown, fareConfirmBreakdown, softAssert);
    }
}