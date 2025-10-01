package Utils.Loader;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Comparator;
import java.util.Map;
import java.util.stream.Stream;

/**
 * Utility class for managing FareConfirm API response files.
 * Responsibilities:
 * - Loading previously saved FareConfirm responses from disk
 * - Saving new FareConfirm responses to disk
 * - Deleting the FareConfirmResponses folder and all its contents
 * Useful for:
 * - Reusing FareConfirm responses across multiple test cases
 * - Debugging and validating API responses outside of runtime
 * - Keeping test data consistent between runs
 * Files are stored in:
 *   src/test/resources/TestData/FareConfirmResponses/
 * File naming pattern:
 *   {offerId}.json (sanitized for OS compatibility)
 */
public class FareConfirmFileManager {

    private static final String BASE_PATH = "src/test/resources/TestData/FareConfirmResponses/";

    /**
     * Sanitizes an offerId so it can be safely used as a filename.
     * Replaces all invalid characters with underscores.
     *
     * @param offerId original offer identifier
     * @return sanitized offer identifier
     */
    private static String sanitizeFileName(String offerId) {
        // Replace everything except letters, digits, dot, hyphen, underscore
        return offerId.replaceAll("[^a-zA-Z0-9._-]", "_");
    }

    /**
     * Loads a FareConfirm API response from disk for a given offerId.
     *
     * @param offerId the unique offer identifier (before sanitization)
     * @return a Map representation of the JSON response
     * @throws RuntimeException if the file cannot be read or parsed
     */
    public static Map<String, Object> loadFareConfirmResponse(String offerId) {
        try {
            ObjectMapper mapper = new ObjectMapper();
            String safeOfferId = sanitizeFileName(offerId);
            File file = new File(BASE_PATH + safeOfferId + ".json");

            if (!file.exists()) {
                throw new RuntimeException("❌ File not found for offerId: " + offerId +
                        " (sanitized: " + safeOfferId + ")");
            }

            return mapper.readValue(file, new TypeReference<>() {});
        } catch (IOException e) {
            throw new RuntimeException("❌ Failed to load fareConfirm response for offerId: " + offerId, e);
        }
    }

    /**
     * Saves a FareConfirm API response to disk.
     *
     * @param offerId     the unique offer identifier (file name will be sanitized)
     * @param responseMap the parsed FareConfirm API response as a Map
     * @throws RuntimeException if the file cannot be written
     */
    public static void saveFareConfirmResponse(String offerId, Map<String, Object> responseMap) {
        try {
            ObjectMapper mapper = new ObjectMapper();
            String safeOfferId = sanitizeFileName(offerId);

            File file = new File(BASE_PATH + safeOfferId + ".json");
            File parentDir = file.getParentFile();

            // Ensure the directory exists
            if (!parentDir.exists() && parentDir.mkdirs()) {
                System.out.println("📂 Created directory: " + parentDir.getAbsolutePath());
            }

            // Save JSON with pretty printing for readability
            mapper.writerWithDefaultPrettyPrinter().writeValue(file, responseMap);
            System.out.println("💾 FareConfirm response saved for offerId: " + safeOfferId);
        } catch (IOException e) {
            throw new RuntimeException("❌ Failed to save FareConfirm response for offerId: " + offerId, e);
        }
    }

    /**
     * Deletes the entire FareConfirmResponses folder and all files inside it.
     * Files are deleted before directories (reverse order) to ensure cleanup.
     * If the folder does not exist, a message is logged instead.
     *
     * @throws RuntimeException if any file/folder cannot be deleted
     */
    public static void deleteFareConfirmResponsesFolder() {
        Path folderPath = Paths.get(BASE_PATH);

        if (Files.exists(folderPath)) {
            try (Stream<Path> paths = Files.walk(folderPath)) {
                paths.sorted(Comparator.reverseOrder()) // Delete files first, then directories
                        .map(Path::toFile)
                        .forEach(file -> {
                            if (!file.delete()) {
                                System.out.println("⚠️ Failed to delete: " + file.getAbsolutePath());
                            }
                        });
                System.out.println("🗑️ FareConfirmResponses folder deleted successfully.");
            } catch (IOException e) {
                throw new RuntimeException("❌ Failed to delete FareConfirmResponses folder", e);
            }
        } else {
            System.out.println("ℹ️ FareConfirmResponses folder does not exist.");
        }
    }
}
