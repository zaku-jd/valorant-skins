package app.quietshop;

import org.json.*;

public class CosmeticModelTest {
  public static void main(String[] args) throws Exception {
    String vp = "85ad13f7-3d1b-5128-9eb2-7cd8ee0b5741", kc = "85ca954a-41f2-ce94-9b45-8ca3dd39a00d";
    JSONObject offer =
        new JSONObject().put("OfferID", "skin").put("Cost", new JSONObject().put(vp, 1775));
    JSONObject prices =
        CosmeticModel.prices(
            new JSONObject()
                .put(
                    "SingleItemStoreOffers",
                    new JSONArray()
                        .put(offer)
                        .put(
                            new JSONObject()
                                .put(
                                    "Offer",
                                    new JSONObject()
                                        .put("OfferID", "wrapped")
                                        .put("Cost", new JSONObject().put(vp, 4350))))));
    if (prices.getJSONObject("skin").getInt(vp) != 1775
        || prices.getJSONObject("wrapped").getInt(vp) != 4350)
      throw new AssertionError("Exact costs");
    JSONObject accessory =
        new JSONObject()
            .put(
                "Offer",
                new JSONObject()
                    .put("OfferID", "offer-id")
                    .put("Cost", new JSONObject().put(kc, 4000))
                    .put(
                        "Rewards",
                        new JSONArray().put(new JSONObject().put("ItemID", "buddy-id"))));
    JSONObject shop =
        new JSONObject()
            .put(
                "AccessoryStore",
                new JSONObject().put("AccessoryStoreOffers", new JSONArray().put(accessory)));
    if (!CosmeticModel.accessories(shop).contains("buddy-id")
        || CosmeticModel.accessories(shop).contains("offer-id"))
      throw new AssertionError("Accessory reward identity");
    if (CosmeticModel.accessoryCosts(shop, "buddy-id").getInt(kc) != 4000)
      throw new AssertionError("Kingdom Credits");
    if (!CosmeticModel.accessories(new JSONObject()).isEmpty())
      throw new AssertionError("Absent optional store");
    System.out.println(
        "Flat/wrapped offers, exact currency prices and accessory reward mapping passed.");
  }
}
