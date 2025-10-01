| Phase                                       | Always Runs?                              | Data Provider          | Based On               |
| ------------------------------------------- | ----------------------------------------  | ---------------------- | ---------------------- |
| `testSearch`                                | ✅ Yes                                    | `searchDataProvider`   | From payload JSON      |
| `testFareConfirm`                           | ✅ Yes (for valid search offers)          | `validFareConfirmData` | After search           |
| `testAddPax`                                | ✅ Yes                                    | `validAddPaxData`      | After fare confirm     |
| `testBook`, `testHold`, `testBookAfterHold` | 🚩Conditional                             | `validBookData`        | Based on `bookingFlow` |
| `testRetrieve`                              | ✅ Always (after book/hold/bookAfterHold) | `validRetrieveData`    | After booking phase    |


ال logic بتاع ال project ان كل supplier موجود في agency واحده وكل agency ليها ال credential بتاعتها ك headers
 واكيد برضو كل supplier ليه ال payload بتاعه في ال search
 for ex: search payload in AirCairo not the same in Indigo
طيب عشان نعمل ال project dynamic هنضيف كل ال agencies headers في file json
ونعمل method تنده علي ال supplier name ترجعلك ال headers بتاعته اللي ضفناها في
check this method :(String agencyName)HeaderLoader.getHeaders هي دي
طيب كده انا بعت ال headers عايز ابعت ال payload
بس انا هنا كل payload بيمثلي test scenario محتلف تحتيه test cases كتيره
احنا اتفقنا ان كل supplier ليه ال payload بتاعه
searchCriteria: one way - round trip - multi city  طيب في انا عايز ابعت في ال
وعايز اجرب في ال passengers Cases مختلفه فهنعمل لكل agency folder و جواه folder for each end point
وجوه ال folder ده فيه ال testcases.json files
وهنعمل method بتاخد ال folder path وتلف علي كل .json files
PayloadLoader.loadTestCases(String folderPath) هي دي
و method تانيه بقي بتاخد التاخد بس ال searchCriteria وال passengers
check this method :PayloadLoader.SearchPayload(data)
طيب كده انا هندلت ال headers , payload محتاج اكلم ال endpoints بقي
كل ال endpoints post ف هنعمل shared method تهندل ده وهتاحد مني ال url,payload,headers
SharedMethods.performPost(String fullUrl, Object requestPayload, Map<String, String> headers)
طيب دلوقتي احنا كده عملنا كل حاجه الا اهم حاجه ال testcases وال assertions
هنعمل class تانيه فيها كل ال assertions وال validations في كل ال endpoints
check ResponseValidator class
طيب كده عملت ال assertions generic هستخدمه ازاي ؟
هضيف generic method لكل end point مثلا ال search وهنده فيها ال assertions اللي عايزها.
check this method :SharedMethods.PerformSearch() هي دي
هنعمل ايه في ال تاني انا دلوقتي عايز اجرب AirCairo مثلا هعمل ايه؟
endpoints بتاع ال folderPath هضيف اول حاجه AirCairoDataProvider علشان احط فيه ال
check this method :AirCairoDataProvider.searchDataProvider() هي دي
هروح اضيف class ل tests هثلا AirCairoTests وهضيف فيها ال headers وال payload بتوع AirCairo وبعدين هنعمل call ل SharedMethods.PerformSearch() وبس
يبقي انا كده لو عايز اضيف agency جديده هعمل ايه؟
1- هضيف ال headers بتاعتها في ال json file
2-هضيف ال test scenarios بتاعه ال payload في ال TestData
3-هضيف الAgencyDataProvider
4- هضيف ال agencyTests class وال data provider class

