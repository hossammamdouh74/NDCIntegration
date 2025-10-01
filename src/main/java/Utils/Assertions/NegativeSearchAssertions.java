package Utils.Assertions;

/**
 * 🚦 NegativeSearchAssertions
 * Utility class for validating **negative test scenarios** in flight search API responses.
 * Features:
 * - Dynamically generates expected error messages based on scenario & payload.
 * - Works with any scenario type (missing fields, invalid passenger counts, return journey errors, etc.).
 * - Normalizes and logs validation errors for easier debugging.
 */
public class NegativeSearchAssertions {

    // ================== Helper Methods ==================

    /**
     * Generates the expected error message dynamically based on scenario & payload.
     *
     * @param scenario Scenario identifier
     * @return Expected error message
     */
    public static String generateExpectedMessage(String scenario) {
        return switch (scenario.toUpperCase()) {
            case "EMPTY_SEARCH_CRITERIA", "BLANK_CRITERIA" ->
                    "at least one search segment is required";

            case "EMPTY_PASSENGER_TYPE_CODE", "INVALID_PASSENGER_TYPE" ->
                    "invalid passenger type";

            case "BLANK_ORIGIN", "MISSING_ORIGIN" ->
                    "the origin field is required";

            case "BLANK_DESTINATION", "MISSING_DESTINATION" ->
                    "the destination field is required";

            case "BLANK_DATE", "MISSING_DATE" ->
                    "date must be in the future";

            case "SAME_ORIGIN_DESTINATION" ->
                    "the origin location cannot be the same as the destination";

            case "INVALID_RETURN_JOURNEY_DATE" ->
                    "cannot be before the previous outbound journey date";

            case "INFANT_TO_ADULT_RATIO" ->
                    "infants (inf) must not exceed the number of adults (adt)";

            case "BLANK_PASSENGER_COUNT" ->
                    "passenger count must be greater than 0";

            case "EMPTY_PASSENGERS_LIST" ->
                    "at least one passenger is required";

            case "BLANK_PASSENGER_TYPE_FIELD" ->
                    "must exist at least one adult";

            case "INVALID_CODE_LENGTH" ->
                    "must be exactly 3 uppercase letters";

            case "NON_EXISTING_PASSENGER_CODE" ->
                    "Invalid passenger type. Please select a valid type. Allowed values: ADT (Adult), CHD (Child), INF (Infant)";

            case "FUTURE_DATE_ONLY" ->
                    "The search date must be in the future";

            case "ORIGIN_DATA_TYPE" ->
                    "Origin must be exactly 3 uppercase letters";

            case "WRONG_DATE_FORMAT" ->
                    "Could not convert string to DateTime: 2025-27-05.";

            default -> "validation error";
        };
    }

}