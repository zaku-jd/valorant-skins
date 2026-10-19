package app.quietshop;

import android.content.Context;
import java.util.*;
import org.json.*;

/** Every saved account remains inside the existing Keystore-encrypted vault. */
final class Accounts {
  static synchronized JSONObject root(Context c) throws Exception {
    JSONObject root = Vault.read(c);
    if (root.optJSONObject("accounts") == null) {
      String legacyId = "";
      if (!root.optString("access").isEmpty()) {
        String id = Store.claims(root.getString("access")).getString("sub");
        AccountModel.validate(id);
        legacyId = id;
        JSONObject old = Store.cached(c, "shop.json");
        if (old.has("SkinsPanelLayout")) {
          Store.cache(c, "shop_" + id + ".json", old);
          History.record(c, id, old);
          Store.file(c, "shop.json").delete();
        }
        Store.prefs(c).edit().putString("active", id).apply();
      }
      root = AccountModel.migrate(root, legacyId, Store.prefs(c).getString("region", "eu"));
      Vault.save(c, root);
    }
    return root;
  }

  static synchronized List<JSONObject> list(Context c) {
    try {
      JSONObject all = root(c).getJSONObject("accounts");
      List<JSONObject> out = new ArrayList<>();
      for (Iterator<String> i = all.keys(); i.hasNext(); ) out.add(all.getJSONObject(i.next()));
      out.sort(Comparator.comparing(a -> a.optString("name", a.optString("id"))));
      return out;
    } catch (Exception e) {
      return new ArrayList<>();
    }
  }

  static synchronized String active(Context c) {
    String id = Store.prefs(c).getString("active", "");
    List<JSONObject> list = list(c);
    for (JSONObject a : list) if (id.equals(a.optString("id"))) return id;
    if (list.isEmpty()) return "";
    id = list.get(0).optString("id");
    Store.prefs(c).edit().putString("active", id).apply();
    return id;
  }

  static synchronized JSONObject get(Context c, String id) throws Exception {
    JSONObject a = root(c).getJSONObject("accounts").optJSONObject(id);
    if (a == null) throw new Store.SessionExpired("Sign in in Settings to load this account.");
    return new JSONObject(a.toString());
  }

  static synchronized void put(Context c, JSONObject a) throws Exception {
    JSONObject r = root(c);
    AccountModel.upsert(r, a);
    Vault.save(c, r);
  }

  static synchronized void update(Context c, String id, String key, Object value) throws Exception {
    JSONObject a = get(c, id);
    a.put(key, value);
    put(c, a);
  }

  static synchronized void select(Context c, String id) throws Exception {
    get(c, id);
    Store.prefs(c).edit().putString("active", id).remove("widgetState").apply();
  }

  static synchronized void remove(Context c, String id) throws Exception {
    JSONObject r = root(c);
    r.getJSONObject("accounts").remove(id);
    Vault.save(c, r);
    Store.file(c, "shop_" + id + ".json").delete();
    Store.prefs(c).edit().remove("active").remove("widgetState").apply();
    active(c);
  }

  static String label(JSONObject a) {
    String name = a.optString("name");
    return name.isEmpty()
        ? "Account · " + a.optString("id").substring(0, Math.min(8, a.optString("id").length()))
        : name;
  }
}
