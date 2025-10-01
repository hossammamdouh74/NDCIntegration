package AgenciesTest.LCC.NegativeTest;

import Utils.Loader.PayloadLoader;
import org.testng.annotations.DataProvider;

public class NegativeScenarioWTDataProvider {

   @DataProvider(name = "negativeSearchDataProvider")
    static public Object[][] negativeSearchDataProvider() {
       String folderPath = "src/test/resources/TestData/NegativeSearchWT/Search";
       return PayloadLoader.getSearchPayloadFromJSON(folderPath);
   }

   @DataProvider(name = "AddPaxDataProvider")
    static public Object[][] AddPaxDataProvider() {
       String folderPath = "src/test/resources/TestData/NegativeAddPaxWT/AddPax";
       return PayloadLoader.getSearchPayloadFromJSON(folderPath);
   }

   @DataProvider(name = "positiveSearchDataProvider")
    static public Object[][] positiveSearchDataProvider() {
        String folderPath = "src/test/resources/TestData/NegativeAddPaxWT/Search";
        return PayloadLoader.getSearchPayloadFromJSON(folderPath);
    }

}