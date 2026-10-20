package app.quietshop;

import java.util.*;
import org.json.*;

/** Reads both flat and wrapped offer shapes without assigning rarity-based prices. */
final class CosmeticModel {
  static JSONObject offer(JSONObject row) {
    JSONObject nested = row == null ? null : row.optJSONObject("Offer");
    return nested != null ? nested : row == null ? new JSONObject() : row;
  }

  static String itemId(JSONObject row) {
    JSONObject o = offer(row);
    JSONArray rewards = o.optJSONArray("Rewards");
    return rewards != null && rewards.length() > 0
        ? rewards.optJSONObject(0).optString("ItemID", o.optString("OfferID"))
        : o.optString("OfferID");
  }

  static JSONObject prices(JSONObject panel) {
    JSONObject out = new JSONObject();
    JSONArray rows = panel == null ? null : panel.optJSONArray("SingleItemStoreOffers");
    if (rows != null)
      for (int i = 0; i < rows.length(); i++) {
        JSONObject o = offer(rows.optJSONObject(i)), cost = o.optJSONObject("Cost");
        if (cost != null)
          try {
            out.put(o.optString("OfferID"), cost);
          } catch (JSONException ignored) {
          }
      }
    return out;
  }

  static Set<String> accessories(JSONObject shop) {
    Set<String> ids = new LinkedHashSet<>();
    JSONObject store = shop.optJSONObject("AccessoryStore");
    JSONArray rows = store == null ? null : store.optJSONArray("AccessoryStoreOffers");
    if (rows != null)
      for (int i = 0; i < rows.length(); i++) {
        String id = itemId(rows.optJSONObject(i));
        if (!id.isEmpty()) ids.add(id);
      }
    return ids;
  }

  static JSONObject accessoryCosts(JSONObject shop, String id) {
    JSONObject store = shop.optJSONObject("AccessoryStore");
    JSONArray rows = store == null ? null : store.optJSONArray("AccessoryStoreOffers");
    if (rows != null)
      for (int i = 0; i < rows.length(); i++)
        if (itemId(rows.optJSONObject(i)).equals(id)) {
          JSONObject cost = offer(rows.optJSONObject(i)).optJSONObject("Cost");
          if (cost != null) return cost;
        }
    return new JSONObject();
  }
}
