package Utils.Loader;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.File;
import java.io.IOException;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Utility class responsible for loading and constructing payloads
 * for different stages of the NDC booking flow.
 *
 * <p>Supported payloads:</p>
 * <ul>
 *   <li>Search payloads (with dynamic date injection)</li>
 *   <li>Fare confirm payloads</li>
 *   <li>Add passenger payloads (selecting from a shared template)</li>
 *   <li>Booking, hold, and retrieve payloads</li>
 * </ul>
 *
 * <p>This class can also read multiple JSON files from a folder
 * and return them in a format suitable for a TestNG DataProvider.</p>
 */
public class PayloadLoader {

    private static final Logger logger = Logger.getLogger(PayloadLoader.class.getName());
    private static final DateTimeFormatter ISO = DateTimeFormatter.ISO_LOCAL_DATE;

    /**
     * Extracts the `searchCriteria` and `passengers` sections from a given payload map,
     * and replaces dates with dynamically computed ones.
     *
     * @param data Full input payload map (usually from a JSON file)
     * @return A reduced payload map containing only `searchCriteria` and `passengers`
     */
    public static Map<String, Object> SearchPayload(Map<String, Object> data) {
        Map<String, Object> payload = new HashMap<>();
        payload.put("searchCriteria", data.get("searchCriteria"));
        payload.put("passengers", data.get("passengers"));
        payload.put("credentialsSelector",data.get("credentialsSelector"));
        payload.put("supplier",data.get("supplier"));

        injectDynamicDates(payload);
        return payload;
    }

    /**
     * Creates a Fare Confirm payload.
     *
     * @param selectedOfferFromSearch The offer ID to confirm
     * @return Map containing the offer ID
     */
    public static Map<String, Object> FareConfirmPayload(Map<String, Object> selectedOfferFromSearch) {
        Map<String, Object> payload = new HashMap<>();
        payload.put("selectedOfferId", selectedOfferFromSearch.get("offerId")); // new key name
        payload.put("supplier", selectedOfferFromSearch.get("supplier"));
        payload.put("credentialsSelector", selectedOfferFromSearch.get("credentialsSelector"));
        payload.put("searchResponseId", selectedOfferFromSearch.get("searchResponseId"));
        return payload;
    }


    /**
     * Builds an Add Passenger payload by selecting only the passengers
     * required from a shared template file.
     *
     * @param searchPayload The search payload used to determine passenger counts
     * @param fareConfirmId The fare confirm offer ID
     * @return Map containing the offer ID and selected passengers
     */
    public static Map<String, Object> AddPaxPayload(Map<String, Object> searchPayload, String fareConfirmId) {
        ObjectMapper mapper = new ObjectMapper();
        try {
            Map<String, Object> fullPayload = mapper.readValue(
                    new File("src/test/resources/TestData/SharedAddPax/SharedAddPax.json"),
                    new TypeReference<>() {}
            );

            Map<String, Object> dynamicPayload = new HashMap<>();
            dynamicPayload.put("OfferId", fareConfirmId);

            // All available passengers from the template
            Map<String, Object> fullPassengers = new TreeMap<>((Map<String, Object>) fullPayload.get("Passengers"));
            Map<String, Object> selectedPassengers = new LinkedHashMap<>();

            // Determine how many of each passenger type are required
            List<Map<String, Object>> searchPassengers = (List<Map<String, Object>>) searchPayload.get("passengers");
            Map<String, Integer> requiredCounts = new HashMap<>();
            for (Map<String, Object> pax : searchPassengers) {
                String type = ((String) pax.get("passengerTypeCode")).toUpperCase();
                int count = (int) pax.get("count");
                requiredCounts.put(type, count);
            }

            // Track how many of each type we've added
            Map<String, Integer> typeCounters = new HashMap<>();
            requiredCounts.keySet().forEach(type -> typeCounters.put(type, 0));

            // Select passengers from the template until we meet the required counts
            for (Map.Entry<String, Object> entry : fullPassengers.entrySet()) {
                String paxKey = entry.getKey();
                Map<String, Object> paxValue = (Map<String, Object>) entry.getValue();
                String type = ((String) paxValue.get("PassengerTypeCode")).toUpperCase();

                if (!requiredCounts.containsKey(type)) continue;

                int needed = requiredCounts.get(type);
                int used = typeCounters.get(type);

                if (used < needed) {
                    selectedPassengers.put(paxKey, paxValue);
                    typeCounters.put(type, used + 1);
                }
            }

            System.out.println("🎯 Required passenger counts: " + requiredCounts);
            System.out.println("📦 Total passengers available in template: " + fullPassengers.size());
            System.out.println("✅ Selected passengers:");
            selectedPassengers.forEach((k, v) ->
                    System.out.println("\t" + k + " → " + ((Map<?, ?>) v).get("PassengerTypeCode"))
            );

            dynamicPayload.put("Passengers", selectedPassengers);
            return dynamicPayload;

        } catch (IOException e) {
            throw new RuntimeException("❌ Failed to load or parse AddPax template", e);
        }
    }

    public static List<Map<String, Object>> getNegativeAddPaxPayloads(String folderPath, String testCaseId, String fareConfirmId) {
        File folder = new File(folderPath);
        File[] files = folder.listFiles((dir, name) -> name.endsWith(".json") && name.contains(testCaseId));
        ObjectMapper mapper = new ObjectMapper();
        List<Map<String, Object>> payloads = new ArrayList<>();

        if (files == null || files.length == 0) {
            logger.log(Level.WARNING,
                    "⚠️ No NegativeAddPax file found in folder [{0}] for testCaseId [{1}]",
                    new Object[]{folderPath, testCaseId});
            return payloads; // empty list
        }

        for (File file : files) {
            try {
                Map<String, Object> jsonMap = mapper.readValue(file, new TypeReference<>() {});
                jsonMap.put("OfferId", fareConfirmId); // inject dynamic OfferId
                payloads.add(jsonMap);
            } catch (IOException e) {
                logger.log(Level.SEVERE, "❌ Failed to read NegativeAddPax file: " + file.getName(), e);
            }
        }

        return payloads;
    }


    /** Creates a Book After Hold payload from booking info. */
    public static Map<String, Object> BookAfterHoldPayload(Map<String, Object> bookingInfo) {
        Map<String, Object> payload = new HashMap<>();
        payload.put("ndcBookingReference", bookingInfo.get("ndcBookingReference"));
        payload.put("airlinePnr", bookingInfo.get("airlinePnr"));
        payload.put("gdsPnr", bookingInfo.get("gdsPnr"));
        payload.put("bookingToken", bookingInfo.getOrDefault("bookingToken", ""));
        return payload;
    }

    /** Creates a Retrieve payload from booking info. */
    public static Map<String, Object> RetrievePayload(Map<String, Object> bookingInfo) {
        Map<String, Object> payload = new HashMap<>();
        payload.put("ndcBookingReference", bookingInfo.get("ndcBookingReference"));
        payload.put("airlinePnr", bookingInfo.get("airlinePnr"));
        payload.put("gdsPnr", bookingInfo.get("gdsPnr"));
        payload.put("bookingToken", bookingInfo.getOrDefault("bookingToken", ""));
        return payload;
    }

    /** Creates a Book payload with no bundles. */
    public static Map<String, Object> BookPayload(String paxOfferId) {
        Map<String, Object> payload = new HashMap<>();
        payload.put("OfferId", paxOfferId);
        payload.put("selectedBundles", new ArrayList<>());
        return payload;
    }

    /** Creates a Hold payload with no bundles. */
    public static Map<String, Object> HoldPayload(String paxOfferId) {
        Map<String, Object> payload = new HashMap<>();
        payload.put("OfferId", paxOfferId);
        payload.put("selectedBundles", new ArrayList<>());
        return payload;
    }

    /**
     * Loads all `.json` files from a given folder and returns them as
     * a 2D array suitable for TestNG's {@code @DataProvider}.
     * Each row contains:
     * <ul>
     *   <li>testCaseId</li>
     *   <li>description</li>
     *   <li>full payload map</li>
     * </ul>
     *
     * @param folderPath Path to the folder containing JSON files
     * @return Object[][] where each row represents a test case
     */
    public static Object[][] getSearchPayloadFromJSON(String folderPath) {
        File folder = new File(folderPath);
        File[] files = folder.listFiles((dir, name) -> name.endsWith(".json"));
        ObjectMapper mapper = new ObjectMapper();
        List<Object[]> data = new ArrayList<>();

        if (files != null) {
            for (File file : files) {
                try {
                    Map<String, Object> jsonMap = mapper.readValue(file, new TypeReference<>() {});
                    String testCaseId = (String) jsonMap.get("testCaseId");
                    String description = (String) jsonMap.get("description");
                    data.add(new Object[]{testCaseId, description, jsonMap});
                } catch (IOException e) {
                    logger.log(Level.SEVERE, "Failed to read test case file: " + file.getName(), e);
                }
            }
        }
        return data.toArray(new Object[0][0]);
    }

    /**
     * Replaces every "date" in {@code searchCriteria} with
     * today's date plus an offset (if provided via "offsetDays").
     */
    private static void injectDynamicDates(Map<String, Object> data) {
        LocalDate today = LocalDate.now();
        List<Map<String, Object>> criteria = (List<Map<String, Object>>) data.get("searchCriteria");
        if (criteria == null) return;

        for (Map<String, Object> leg : criteria) {

            int offset; // default offset
            String computedDate;

            if (leg.containsKey("offsetDays")) {
                // Use offsetDays if present
                offset = ((Number) leg.get("offsetDays")).intValue();
                computedDate = today.plusDays(offset).format(ISO);
            } else if (leg.containsKey("date")) {
                Object dateValue = leg.get("date");

                if (dateValue instanceof Number) {
                    // Treat numeric date as offset in days
                    offset = ((Number) dateValue).intValue();
                    computedDate = today.plusDays(offset).format(ISO);
                } else if (dateValue instanceof String) {
                    // Assume it's already a proper ISO date string, keep it
                    computedDate = dateValue.toString();
                } else {
                    // fallback
                    computedDate = today.plusDays(0).format(ISO);
                }
            } else {
                // Neither offsetDays nor date present, default to today
                computedDate = today.plusDays(0).format(ISO);
            }

            leg.put("date", computedDate);
            leg.remove("offsetDays"); // remove offsetDays key to clean payload
        }
    }

}
