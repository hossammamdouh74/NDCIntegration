package Utils.Helper;

import java.util.Map;

/**
 * Immutable data structure representing a single search result offer.
 * <p>
 * This record is used to store:
 * - The unique identifier of the offer (offerId)
 * - The full parsed offer response (as a Map) returned by the Search API
 * <p>
 * Since this is a {@code record}, it is:
 * - Immutable (fields cannot be changed after creation)
 * - Thread-safe for read-only usage
 * - Automatically provides {@code equals()}, {@code hashCode()}, and {@code toString()} implementations
 */
public record SearchResult(
        /*
          Unique identifier for the offer returned by the Search API.
         */
        String offerId,

        /*
          Full parsed Search API offer response, stored as a Map<String, Object>.
          This allows other test steps to validate fields or reuse pricing details.
         */
        Map<String, Object> offerResponseMap
) {
}
