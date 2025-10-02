package Utils.Helper;

import io.restassured.path.json.JsonPath;
import io.restassured.response.Response;
import org.slf4j.*;
import java.math.BigDecimal;
import java.util.*;

public class HelperGetResponse {

    private static final Logger logger = LoggerFactory.getLogger(HelperGetResponse.class);

    /**
    * Passenger Fare Breakdown Methods
    * Extract passenger fare breakdowns from the response */
    public static List<Map<String, Object>> getPassengerBreakdowns(JsonPath jsonPath, int offerOrder) {
        if (jsonPath == null) {
            logger.error("JsonPath is null while getting passenger fare breakdown.");
            return new ArrayList<>();
        }
        return jsonPath.getList("offers[" + offerOrder + "].passengerFareBreakdown");
    }

    /** Get price field mapping for response validation
     *
     */
    public static Map<String, String> getPriceFieldMapping() {
        return Map.of(
                "totalTaxAmount", "paxTotalTaxAmount",
                "totalBaseAmount", "paxBaseAmount"
        );
    }

    /**
    * Offer ID Retrieval
    * Get the offer ID from search response*/
    public static String getSearchOfferId(Response response, int offerOrder) {
        JsonPath jsonPath = response.jsonPath();
        return jsonPath.getString("offers[" + offerOrder + "].offerId");
    }

    /* Get the offer ID from fare confirm response**/
    public static String getFareConfirmOfferId(Response response) {
        JsonPath jsonPath = response.jsonPath();
        return jsonPath.getString("selectedOffer.offerId");
    }
    /* Get the offer ID from fare confirm response**/
    public static String getFareConfirmResponseId(Response response) {
        JsonPath jsonPath = response.jsonPath();
        return jsonPath.getString("responseId");
    }

    /** Get the offer ID from AddPax response*/
    public static String getAddPaxOfferId(Response response) {
        String offerId = response.jsonPath().getString("offerId");

        if (offerId != null && !offerId.isEmpty()) {
            logger.info("✅ AddPax Offer ID: {}", offerId);
        } else {
            logger.error("❌ Failed to extract offerId from AddPax response");
            throw new RuntimeException("Failed to extract offerId from AddPax response");
        }

        return offerId;
    }

    /** Offer Retrieval
    * Get the nth offer from the response*/
    public static Map<String, Object> getNthOffer(Response response, int index) {
        List<Map<String, Object>> offers = response.jsonPath().getList("offers");

        if (offers != null && offers.size() >= index) {
            return offers.get(index); // index is 1-based
        } else {
            logger.warn("Offers list is either null or does not contain the requested index: {}", index);
        }
        return null;
    }

    /** Get offer by carrier code*/
    public static Map<String, Object> getOfferByCarrierCode(Response response, String carrierCode) {
        JsonPath jsonPath = response.jsonPath();
        Map<String, Map<String, Object>> segments = jsonPath.getMap("flightSegments");

        if (segments == null || segments.isEmpty()) {
            logger.warn("❌ No segments found in response.");
            return null;
        }

        for (Map.Entry<String, Map<String, Object>> entry : segments.entrySet()) {
            String segmentRefId = entry.getKey();
            Map<String, Object> segment = entry.getValue();

            String marketingCode = (String) segment.get("marketingCarrierCode");
            String operatingCode = (String) segment.get("operatingCarrierCode");

            if (carrierCode.equals(marketingCode) && carrierCode.equals(operatingCode)) {
                List<Map<String, Object>> offers = jsonPath.getList("offers");
                if (offers == null || offers.isEmpty()) {
                    logger.warn("❌ No offers found in response.");
                    return null;
                }

                for (Map<String, Object> offer : offers) {
                    List<Map<String, Object>> fareBreakdowns =
                            (List<Map<String, Object>>) offer.get("passengerFareBreakdown");
                    if (fareBreakdowns == null) continue;

                    for (Map<String, Object> breakdown : fareBreakdowns) {
                        List<Map<String, Object>> segmentDetails =
                                (List<Map<String, Object>>) breakdown.get("segmentDetails");
                        if (segmentDetails == null) continue;

                        for (Map<String, Object> detail : segmentDetails) {
                            String segRefInOffer = (String) detail.get("segmentRefId");
                            if (segmentRefId.equals(segRefInOffer)) {
                                return offer; // ✅ Return full offer map
                            }
                        }
                    }
                }

                logger.warn("❌ No offer references the matched segment for carrier: {}", carrierCode);
                return null;
            }
        }

        logger.warn("❌ No segment matches both marketing and operating carrier: {}", carrierCode);
        return null;
    }

    /** Price Details & Amount Handling*/
    protected static BigDecimal getPriceDetailsAmount(JsonPath jsonPath, int offerOrder, String field) {
        Map<String, Object> priceField = jsonPath.getMap("offers[" + offerOrder + "].priceDetails." + field);
        return getAmountOrZero(priceField);
    }

    /** Get amount from map or return zero if not found*/
    public static BigDecimal getAmountOrZero(Map<String, Object> amountMap) {
        if (amountMap == null || amountMap.get("amount") == null) {
            logger.warn("Amount map is null or amount key is missing.");
            return BigDecimal.ZERO;
        }

        Object amount = amountMap.get("amount");
        return convertToBigDecimal(amount);
    }

    /** Get amount from map or object safely */
    public static BigDecimal getAmountOrZero(Object amountObj) {
        if (amountObj == null) return BigDecimal.ZERO;

        if (amountObj instanceof Map) {
            Object amount = ((Map<?, ?>) amountObj).get("amount");
            return convertToBigDecimal(amount);
        }

        return convertToBigDecimal(amountObj);
    }

    private static BigDecimal convertToBigDecimal(Object value) {
        if (value instanceof BigDecimal) {
            return (BigDecimal) value;
        }
        if (value instanceof Number) {
            return new BigDecimal(value.toString()); // avoid double constructor
        }
        if (value instanceof String) {
            try {
                return new BigDecimal((String) value);
            } catch (NumberFormatException e) {
                logger.warn("Invalid amount string: {}", value);
            }
        }
        return BigDecimal.ZERO;
    }

    /**
    * Booking Info*/
    public static Map<String, Object> getBookingInfo(Response response) {
        JsonPath json = response.jsonPath();
        Map<String, Object> bookingInfo = new HashMap<>();

        bookingInfo.put("ndcBookingReference", json.getString("ndcBookingReference"));
        bookingInfo.put("airlinePnr", json.getString("airlinePnr"));
        bookingInfo.put("gdsPnr", json.getString("gdsPnr"));
        bookingInfo.put("bookingToken", ""); // optional, left empty

        return bookingInfo;
    }

    /** Get Offer ID by Carrier Code*/
    public static String getOfferIdByCarrierCode(Response response, String carrierCode) {
        JsonPath jsonPath = response.jsonPath();
        Map<String, Map<String, Object>> segments = jsonPath.getMap("flightSegments");

        if (segments == null || segments.isEmpty()) {
            return "❌ No segments found in response.";
        }

        for (Map.Entry<String, Map<String, Object>> entry : segments.entrySet()) {
            String segmentRefId = entry.getKey();
            Map<String, Object> segment = entry.getValue();

            String marketingCode = (String) segment.get("marketingCarrierCode");
            String operatingCode = (String) segment.get("operatingCarrierCode");

            if (carrierCode.equals(marketingCode) && carrierCode.equals(operatingCode)) {
                // ✅ Matching segment found
                List<Map<String, Object>> offers = jsonPath.getList("offers");
                if (offers == null || offers.isEmpty()) {
                    return "❌ No offers found in response.";
                }

                for (Map<String, Object> offer : offers) {
                    String offerId = (String) offer.get("offerId");
                    List<Map<String, Object>> fareBreakdowns =
                            (List<Map<String, Object>>) offer.get("passengerFareBreakdown");
                    if (fareBreakdowns == null) continue;

                    for (Map<String, Object> breakdown : fareBreakdowns) {
                        List<Map<String, Object>> segmentDetails =
                                (List<Map<String, Object>>) breakdown.get("segmentDetails");
                        if (segmentDetails == null) continue;

                        for (Map<String, Object> detail : segmentDetails) {
                            String segRefInOffer = (String) detail.get("segmentRefId");
                            if (segmentRefId.equals(segRefInOffer)) {
                                return offerId; // ✅ Found offer
                            }
                        }
                    }
                }

                return "❌ No offer references the matched segment for carrier code: " + carrierCode;
            }
        }

        return "❌ No segment found where both carrier codes match: " + carrierCode;
    }
}