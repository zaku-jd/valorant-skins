package app.quietshop;

import org.json.*;

/** Pure account-container transformations; encryption and selection are handled by Accounts. */
final class AccountModel {
  static JSONObject migrate(JSONObject old, String id, String region) throws JSONException {
    if (old.optJSONObject("accounts") != null) return old;
    JSONObject all = new JSONObject();
    if (!old.optString("access").isEmpty()) {
      validate(id);
      JSONObject session = new JSONObject(old.toString());
      session.put("id", id).put("region", region);
      all.put(id, session);
    }
    return new JSONObject().put("accounts", all);
  }

  static void upsert(JSONObject root, JSONObject account) throws JSONException {
    String id = account.getString("id");
    validate(id);
    root.getJSONObject("accounts").put(id, new JSONObject(account.toString()));
  }

  static void validate(String id) {
    if (id == null || !id.matches("[a-zA-Z0-9-]+"))
      throw new IllegalArgumentException("Invalid account ID");
  }
}
