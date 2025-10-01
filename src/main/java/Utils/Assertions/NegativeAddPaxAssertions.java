package Utils.Assertions;

public class NegativeAddPaxAssertions {
   // ================== Helper Methods ==================

   /**
    * Generates the expected error message dynamically based on scenario & payload.
    *
    * @param scenario Scenario identifier
    * @return Expected error message
    */
   public static String generateExpectedMessage(String scenario) {
      return switch (scenario.toUpperCase()) {
         case "PASSENGER_TITLE_GENDER_ALIGN" ->
                 "title and gender do not align";
         case "FIRST_NAME_REQUIRED" ->
                 "First Name is required.";
         case "PASSENGER_DATA_MISMATCH" ->
                 "Passenger data must match travel document details";
         case "INF_REFER_TO_EXIST_ADT"->
            "referenced passenger 'adt2' does not exist";
         case "INF_REFER_TO_ONLY_ONE_ADT"->
                 "adult passenger 'adt1' is referenced by more than one infant, which is not allowed.";
         case "DUPLICATE_PASSENGER_DATA"->
                 "Duplicate passengers detected: Duplicate for";
         case "DUPLICATE_TRAVEL_DOCUMENT"->
                 "Duplicate travel document detected. Each combination of Document Number and Type must be unique";
         case "PASSENGER_COUNT_MISMATCH"->
                 "Passenger count mismatch.";
         case "FIRST_NAME_ONE_CHARACTER"->
                 "First Name must be more than one character.";
         case "LAST_NAME_ONE_CHARACTER"->
                 "Last Name must be more than one character.";
         case "LAST_NAME_REQUIRED" ->
                 "LAST Name is required.";
         case "GENDER_REQUIRED" ->
                 "Invalid gender. Please select either Male or Female";
         case "INVALID_DOCUMENT_TYPE" ->
                 "Invalid document type. Allowed values: Passport, IQAMA, NationalId.";
         case "DOCUMENT_TYPE_REQUIRED" ->
                 "Document type is required for international flights.";
         case "PASSPORT_>_BIRTH_DATE" ->
                 "Travel document expiration date must be after the birth date.";
         case "RESIDENCE_CODE_REQUIRED" ->
                 "Residence country code is required.";
         case "RESIDENCE_CODE_INVALID" ->
                 "Residence country code is invalid";
         case "NATIONAL_CODE_REQUIRED" ->
                 "Nationality country code is required.";
         case "NATIONAL_CODE_INVALID" ->
                 "Nationality country code is invalid";
         case "ISSUANCE_CODE_REQUIRED" ->
                 "Issuance country code is required.";
         case "ISSUANCE_CODE_INVALID" ->
                 "Issuance country code is invalid";
         default -> "validation error";
      };
   }
}