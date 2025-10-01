package AgenciesTest.LCC.OneSupplierTest;

import Utils.Loader.PayloadLoader;
import org.testng.annotations.DataProvider;

import static Utils.Helper.HelperTestData.AgencyName;

public class OneSupplierDataProvider {

   @DataProvider(name = "negativeSearchDataProvider")
    static public Object[][] negativeSearchDataProvider() {
       String folderPath = "src/test/resources/TestData/NegativeSearchWT/Search";
       return PayloadLoader.getSearchPayloadFromJSON(folderPath);
   }

   @DataProvider(name = "AddPaxDataProvider")
    static public Object[][] AddPaxDataProvider() {
       String folderPath = "src/test/resources/TestData/"+AgencyName+"/AddPax";
       return PayloadLoader.getSearchPayloadFromJSON(folderPath);
   }

    @DataProvider(name = "positiveSearchDataProvider")
    static public Object[][] positiveSearchDataProvider() {
        String folderPath = "src/test/resources/TestData/"+AgencyName+"/Search";
        return PayloadLoader.getSearchPayloadFromJSON(folderPath);
    }
}