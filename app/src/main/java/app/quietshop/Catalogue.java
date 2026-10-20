package app.quietshop;

import android.content.Context;
import android.graphics.*;
import android.util.LruCache;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import org.json.*;

/** Immutable bundled catalogue. No network requests and no on-device asset updates. */
final class Catalogue {
  static Catalogue instance;
  final Map<String, JSONObject> skins = new LinkedHashMap<>(), tiers = new LinkedHashMap<>();
  final Map<String, String> weaponIcons = new LinkedHashMap<>();
  final JSONObject images, info, usage;
  final Map<String, String> aliases = new HashMap<>(),
      themeIcons = new HashMap<>(),
      seasonSets = new HashMap<>();
  final Map<String, JSONObject> rankSets = new LinkedHashMap<>(),
      currencies = new LinkedHashMap<>();
  final Map<String, String> parents = new HashMap<>();
  final JSONArray seasons;
  volatile JSONObject prices;
  final LruCache<String, Bitmap> bitmaps =
      new LruCache<String, Bitmap>(24000) {
        protected int sizeOf(String k, Bitmap b) {
          return b.getByteCount() / 1024;
        }
      };

  static synchronized Catalogue get(Context c) {
    if (instance == null)
      try {
        instance = new Catalogue(c.getApplicationContext());
      } catch (Exception e) {
        throw new IllegalStateException("Bundled catalogue could not be read", e);
      }
    return instance;
  }

  static String read(Context c, String name) throws Exception {
    try (InputStream in = c.getAssets().open(name)) {
      return new String(Util.read(in), StandardCharsets.UTF_8);
    }
  }

  Catalogue(Context c) throws Exception {
    images = new JSONObject(read(c, "image-index.json"));
    info = new JSONObject(read(c, "skin-info.json"));
    usage = new JSONObject(read(c, "weapon-usage.json"));
    prices = Store.cached(c, "prices.json");
    seasons = new JSONArray(read(c, "seasons.json"));
    for (int i = 0; i < seasons.length(); i++) {
      JSONObject a = seasons.getJSONObject(i);
      parents.put(a.getString("uuid"), a.optString("parentUuid"));
    }
    JSONArray rs = new JSONArray(read(c, "competitivetiers.json"));
    for (int i = 0; i < rs.length(); i++)
      rankSets.put(rs.getJSONObject(i).getString("uuid"), rs.getJSONObject(i));
    JSONArray cs = new JSONObject(read(c, "competitive-seasons.json")).getJSONArray("data");
    for (int i = 0; i < cs.length(); i++) {
      JSONObject a = cs.getJSONObject(i);
      seasonSets.put(a.getString("seasonUuid"), a.getString("competitiveTiersUuid"));
    }
    JSONArray money = new JSONArray(read(c, "currencies.json"));
    for (int i = 0; i < money.length(); i++)
      currencies.put(money.getJSONObject(i).getString("uuid"), money.getJSONObject(i));
    JSONArray ts = new JSONArray(read(c, "tiers.json"));
    for (int i = 0; i < ts.length(); i++) {
      JSONObject t = ts.getJSONObject(i);
      tiers.put(t.getString("uuid"), t);
    }
    Map<String, String> themes = new HashMap<>();
    JSONArray th = new JSONArray(read(c, "themes.json"));
    for (int i = 0; i < th.length(); i++)
      themes.put(
          th.getJSONObject(i).getString("uuid"), th.getJSONObject(i).getString("displayName"));
    for (int i = 0; i < th.length(); i++) {
      JSONObject t = th.getJSONObject(i);
      if (!t.isNull("displayIcon"))
        themeIcons.put(t.optString("displayName"), t.optString("displayIcon"));
    }
    JSONArray weapons = new JSONObject(read(c, "weapons.json")).getJSONArray("data");
    for (int i = 0; i < weapons.length(); i++) {
      JSONObject weapon = weapons.getJSONObject(i);
      String gun = weapon.getString("displayName");
      weaponIcons.put(gun, weapon.optString("displayIcon"));
      JSONArray ws = weapon.getJSONArray("skins");
      for (int j = 0; j < ws.length(); j++) {
        JSONObject skin = ws.getJSONObject(j);
        String id = skin.getJSONArray("levels").getJSONObject(0).getString("uuid");
        JSONObject extra = info.optJSONObject(id.replace("-", ""));
        String line =
            themes.getOrDefault(
                skin.optString("themeUuid"), skin.optString("displayName").replace(" " + gun, ""));
        skin.put("weapon", gun)
            .put("category", weapon.optString("category").replace("EEquippableCategory::", ""));
        skin.put("line", extra == null ? line : extra.optString("collection", line));
        skin.put(
            "obtain",
            extra == null
                ? (skin.optString("displayName").startsWith("Standard ") ? "free" : "unknown")
                : extra.optString("obtain", "unknown"));
        skin.put("recordedPrice", extra == null ? 0 : extra.optInt("price", 0));
        skins.put(id, skin);
        aliases.put(skin.optString("uuid"), id);
        for (int k = 0; k < skin.getJSONArray("levels").length(); k++)
          aliases.put(skin.getJSONArray("levels").getJSONObject(k).optString("uuid"), id);
        if (!themeIcons.containsKey(skin.optString("line")))
          themeIcons.put(skin.optString("line"), primary(skin));
        skin.put("limited", extra != null && extra.optBoolean("limited"));
      }
    }
    JSONArray accessories = new JSONArray(read(c, "accessories.json"));
    for (int i = 0; i < accessories.length(); i++) {
      JSONObject a = accessories.getJSONObject(i);
      String id = a.getString("uuid");
      a.put("accessory", true)
          .put("itemId", id)
          .put("line", a.optString("kind"))
          .put("obtain", "unknown");
      skins.put(id, a);
      aliases.put(id, id);
      JSONArray ids = a.optJSONArray("aliases");
      for (int j = 0; ids != null && j < ids.length(); j++) aliases.put(ids.getString(j), id);
    }
    for (var e : skins.entrySet()) e.getValue().put("itemId", e.getKey());
  }

  String currentSeason(long now) {
    for (int i = 0; i < seasons.length(); i++) {
      JSONObject a = seasons.optJSONObject(i);
      try {
        if (!a.isNull("parentUuid")
            && java.time.Instant.parse(a.getString("startTime")).toEpochMilli() <= now
            && java.time.Instant.parse(a.getString("endTime")).toEpochMilli() > now)
          return a.getString("uuid");
      } catch (Exception ignored) {
      }
    }
    return "";
  }

  JSONObject rank(String season, int tier) {
    String set = seasonSets.getOrDefault(season, seasonSets.getOrDefault(parents.get(season), ""));
    JSONObject group = rankSets.get(set);
    if (group == null) for (JSONObject r : rankSets.values()) group = r;
    JSONArray ranks = group == null ? new JSONArray() : group.optJSONArray("tiers");
    for (int i = 0; ranks != null && i < ranks.length(); i++)
      if (ranks.optJSONObject(i).optInt("tier") == tier) return ranks.optJSONObject(i);
    return new JSONObject();
  }

  boolean owned(JSONObject s, Set<String> ids) {
    return ids.contains(s.optString("uuid")) || ids.contains(s.optString("itemId"));
  }

  String currencyIcon(String id) {
    JSONObject c = currencies.get(id);
    return c == null ? "" : c.optString("displayIcon");
  }

  String primary(JSONObject s) {
    String icon =
        s.optBoolean("accessory")
            ? s.optString("preview", s.optString("displayIcon", ""))
            : s.optString("displayIcon", "");
    if (icon.isEmpty()) icon = s.optString("displayIcon", "");
    if (icon.isEmpty() || icon.equals("null")) {
      JSONArray levels = s.optJSONArray("levels"), chromas = s.optJSONArray("chromas");
      icon =
          levels != null && levels.length() > 0
              ? levels.optJSONObject(0).optString("displayIcon", "")
              : "";
      if ((icon.isEmpty() || icon.equals("null")) && chromas != null && chromas.length() > 0)
        icon = chromas.optJSONObject(0).optString("fullRender", "");
    }
    return icon;
  }

  Bitmap image(Context c, String url) {
    if (url == null) return null;
    Bitmap hit = bitmaps.get(url);
    if (hit != null) return hit;
    String asset = images.optString(url, "");
    if (asset.isEmpty()) return null;
    try (InputStream in = c.getAssets().open(asset)) {
      Bitmap bitmap = BitmapFactory.decodeStream(in);
      if (bitmap != null) bitmaps.put(url, bitmap);
      return bitmap;
    } catch (Exception e) {
      return null;
    }
  }

  String tier(JSONObject s) {
    JSONObject t = tiers.get(s.optString("contentTierUuid"));
    return s.optBoolean("accessory")
        ? s.optString("kind")
        : t == null ? "Unrated" : t.optString("displayName").replace(" Edition", "");
  }

  String tierIcon(JSONObject s) {
    JSONObject t = tiers.get(s.optString("contentTierUuid"));
    return t == null ? "" : t.optString("displayIcon");
  }

  boolean eligible(JSONObject s) {
    return s != null && s.optString("obtain").equals("store") && !s.optBoolean("limited");
  }

  int color(JSONObject s) {
    JSONObject t = tiers.get(s.optString("contentTierUuid"));
    try {
      return t == null
          ? Color.rgb(105, 118, 139)
          : Color.parseColor("#" + t.getString("highlightColor").substring(0, 6));
    } catch (Exception e) {
      return Color.GRAY;
    }
  }

  int price(Context c, String id) {
    JSONObject s = skins.get(id);
    int known = prices.optInt(id, 0);
    if (known > 0) return known;
    if (s == null) return 0;
    if (s.optInt("recordedPrice") > 0) return s.optInt("recordedPrice");
    return 0;
  }

  String priceText(Context c, String id) {
    int cost = price(c, id);
    if (cost > 0) return String.format(Locale.getDefault(), "%,d VP", cost);
    JSONObject s = skins.get(id);
    String obtain = s == null ? "" : s.optString("obtain");
    return switch (obtain) {
      case "pass" -> "Battle pass";
      case "free" -> "Included";
      case "contract" -> "Agent gear";
      default -> "Price unavailable";
    };
  }

  static String wiki(JSONObject skin) {
    if (skin.optBoolean("accessory"))
      return "https://valorant.fandom.com/wiki/"
          + android.net.Uri.encode(skin.optString("displayName").replace(' ', '_'));
    return "https://valorant.fandom.com/wiki/"
        + android.net.Uri.encode(skin.optString("line").replace(' ', '_') + "_Collection")
        + "#Weapon_Skins";
  }
}
