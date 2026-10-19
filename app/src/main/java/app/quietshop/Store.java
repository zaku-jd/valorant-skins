package app.quietshop;

import android.content.Context;
import android.content.SharedPreferences;
import android.net.Uri;
import android.util.Base64;
import java.io.*;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import javax.net.ssl.HttpsURLConnection;
import org.json.*;

final class Store {
  static final String API = "https://valorant-api.com/v1/";
  static final String CALLBACK = "https://playvalorant.com/opt_in";
  static final String AUTH = "https://auth.riotgames.com";
  static final String VP = "85ad13f7-3d1b-5128-9eb2-7cd8a2d5b362";

  static SharedPreferences prefs(Context c) {
    return c.getSharedPreferences("settings", 0);
  }

  static class Reply {
    JSONObject data;
    List<String> cookies = new ArrayList<>();
  }

  static Reply request(String url, String method, Map<String, String> headers, Object body)
      throws Exception {
    HttpsURLConnection conn = (HttpsURLConnection) new URL(url).openConnection();
    conn.setConnectTimeout(20000);
    conn.setReadTimeout(25000);
    conn.setInstanceFollowRedirects(false);
    conn.setRequestMethod(method);
    conn.setRequestProperty("Accept", "application/json");
    for (var h : headers.entrySet()) conn.setRequestProperty(h.getKey(), h.getValue());
    try {
      if (body != null) {
        conn.setDoOutput(true);
        conn.setRequestProperty("Content-Type", "application/json");
        try (OutputStream out = conn.getOutputStream()) {
          out.write(body.toString().getBytes(StandardCharsets.UTF_8));
        }
      }
      int code = conn.getResponseCode();
      if (code == 401) throw new SessionExpired("Riot session expired. Sign in again in Settings.");
      if (code == 403)
        throw new IOException(
            "Riot refused this request. Sign in again, or retry later; no checks can run while"
                + " access is blocked.");
      if (code == 429) throw new IOException("Riot rate limit reached. Try again later.");
      if (code < 200 || code >= 300)
        throw new IOException("Service unavailable (HTTP " + code + "). Try again later.");
      Reply reply = new Reply();
      for (var h : conn.getHeaderFields().entrySet())
        if (h.getKey() != null && h.getKey().equalsIgnoreCase("Set-Cookie"))
          reply.cookies.addAll(h.getValue());
      try (InputStream in = conn.getInputStream()) {
        Object parsed =
            new JSONTokener(new String(Util.read(in), StandardCharsets.UTF_8)).nextValue();
        reply.data =
            parsed instanceof JSONObject
                ? (JSONObject) parsed
                : new JSONObject().put("items", parsed);
      }
      return reply;
    } finally {
      conn.disconnect();
    }
  }

  static JSONObject get(String url) throws Exception {
    return request(url, "GET", Util.map(), null).data;
  }

  static File file(Context c, String name) {
    return new File(c.getFilesDir(), name);
  }

  static JSONObject cached(Context c, String name) {
    try (InputStream in = new FileInputStream(file(c, name))) {
      return new JSONObject(new String(Util.read(in), StandardCharsets.UTF_8));
    } catch (Exception e) {
      return new JSONObject();
    }
  }

  static void cache(Context c, String name, JSONObject data) throws Exception {
    File tmp = file(c, name + ".tmp");
    try (OutputStream out = new FileOutputStream(tmp)) {
      out.write(data.toString().getBytes(StandardCharsets.UTF_8));
    }
    if (!tmp.renameTo(file(c, name))) throw new IOException("Unable to save local cache.");
  }

  static String parameter(String url, String key) {
    Uri u = Uri.parse(url);
    String q = u.getQueryParameter(key);
    if (q != null) return q;
    String f = u.getFragment();
    return f == null ? null : Uri.parse("https://local/?" + f).getQueryParameter(key);
  }

  static String loginUrl(String state) {
    return AUTH
        + "/authorize?client_id=play-valorant-web-prod&redirect_uri="
        + Uri.encode(CALLBACK)
        + "&response_type=token%20id_token&scope=account%20openid&nonce="
        + state
        + "&state="
        + state;
  }

  static JSONObject claims(String token) throws Exception {
    return new JSONObject(
        new String(
            Base64.decode(token.split("\\.")[1], Base64.URL_SAFE | Base64.NO_WRAP),
            StandardCharsets.UTF_8));
  }

  static String mergeCookies(String old, List<String> updates) {
    Map<String, String> jar = new LinkedHashMap<>();
    for (String part : old.split(";")) {
      int at = part.indexOf('=');
      if (at > 0) jar.put(part.substring(0, at).trim(), part.substring(at + 1).trim());
    }
    for (String set : updates) {
      try {
        for (HttpCookie cookie : HttpCookie.parse(set)) {
          if (cookie.getMaxAge() == 0 || cookie.hasExpired()) jar.remove(cookie.getName());
          else jar.put(cookie.getName(), cookie.getValue());
        }
      } catch (IllegalArgumentException ignored) {
      }
    }
    List<String> parts = new ArrayList<>();
    for (var entry : jar.entrySet()) parts.add(entry.getKey() + "=" + entry.getValue());
    return String.join("; ", parts);
  }

  static class SessionExpired extends IOException {
    SessionExpired(String message) {
      super(message);
    }
  }

  static JSONObject shop(Context c) {
    String id = Accounts.active(c);
    return id.isEmpty() ? new JSONObject() : cached(c, "shop_" + id + ".json");
  }

  static synchronized JSONObject session(Context c, String id) throws Exception {
    JSONObject s = Accounts.get(c, id);
    String token = s.optString("access");
    if (token.isEmpty()) throw new SessionExpired("Sign in again in Settings.");
    if (claims(token).optLong("exp") > System.currentTimeMillis() / 1000 + 120) return s;
    String cookies = s.optString("cookies");
    if (cookies.isEmpty()) throw new SessionExpired("Session expired. Sign in again in Settings.");
    JSONObject body =
        new JSONObject()
            .put("client_id", "play-valorant-web-prod")
            .put("redirect_uri", CALLBACK)
            .put("response_type", "token id_token")
            .put("response_mode", "query")
            .put("scope", "account openid")
            .put("nonce", UUID.randomUUID().toString());
    Reply re = request(AUTH + "/api/v1/authorization", "POST", Util.map("Cookie", cookies), body);
    s.put("cookies", mergeCookies(cookies, re.cookies));
    JSONObject response = re.data.optJSONObject("response"),
        params = response == null ? null : response.optJSONObject("parameters");
    String access = params == null ? null : parameter(params.optString("uri"), "access_token");
    if (access == null) {
      Accounts.put(c, s);
      throw new SessionExpired(
          "Riot requires sign-in again. Open Settings to reconnect this account.");
    }
    if (!id.equals(claims(access).optString("sub")))
      throw new SessionExpired("Session account changed. Sign in again.");
    s.put("access", access);
    Accounts.put(c, s);
    return s;
  }

  static synchronized JSONObject refresh(Context c) throws Exception {
    return refresh(c, Accounts.active(c), false);
  }

  static synchronized JSONObject refresh(Context c, String id, boolean force) throws Exception {
    if (id.isEmpty()) throw new SessionExpired("Sign in in Settings to see your shop.");
    JSONObject old = cached(c, "shop_" + id + ".json"),
        panel = old.optJSONObject("SkinsPanelLayout");
    long end =
        SkinLogic.expiresAt(
            old.optLong("checkedAt"),
            panel == null ? 0 : panel.optLong("SingleItemOffersRemainingDurationInSeconds"));
    if (!force && SkinLogic.current(old.optLong("checkedAt"), end, System.currentTimeMillis())) {
      History.record(c, id, old);
      JSONObject account = Accounts.get(c, id);
      if (!account.optBoolean("expired")) {
        account.put("nextAttempt", Math.max(System.currentTimeMillis() + 60000, end + 120000));
        Accounts.put(c, account);
      }
      return old;
    }
    try {
      return fetch(c, id);
    } catch (Exception error) {
      try {
        JSONObject a = Accounts.get(c, id);
        a.put("error", error instanceof SessionExpired ? "Sign-in expired" : "Shop unavailable")
            .put("expired", error instanceof SessionExpired)
            .put(
                "nextAttempt",
                System.currentTimeMillis()
                    + (error instanceof SessionExpired ? 86400000L : 3600000L));
        Accounts.put(c, a);
      } catch (Exception ignored) {
      }
      throw error;
    }
  }

  private static JSONObject fetch(Context c, String id) throws Exception {
    JSONObject s = session(c, id);
    String token = s.getString("access");
    String region = s.optString("region", "eu");
    if (!Util.set("eu", "na", "ap", "kr").contains(region) || !id.matches("[a-zA-Z0-9-]+"))
      throw new IOException("Check the account region in Settings.");
    String entitlement =
        request(
                "https://entitlements.auth.riotgames.com/api/token/v1/",
                "POST",
                Util.map("Authorization", "Bearer " + token),
                new JSONObject())
            .data
            .getString("entitlements_token");
    String platform =
        Base64.encodeToString(
            "{\"platformType\":\"PC\",\"platformOS\":\"Windows\",\"platformOSVersion\":\"10.0.19042.1.256.64bit\",\"platformChipset\":\"Unknown\"}"
                .getBytes(StandardCharsets.UTF_8),
            Base64.NO_WRAP);
    String version = get(API + "version").getJSONObject("data").getString("riotClientVersion");
    var headers =
        Util.map(
            "Authorization",
            "Bearer " + token,
            "X-Riot-Entitlements-JWT",
            entitlement,
            "X-Riot-ClientVersion",
            version,
            "X-Riot-ClientPlatform",
            platform);
    String base = "https://pd." + region + ".a.pvp.net";
    JSONObject raw =
        request(base + "/store/v3/storefront/" + id, "POST", headers, new JSONObject()).data;
    JSONObject panel = raw.optJSONObject("SkinsPanelLayout");
    if (panel == null) throw new IOException("Riot returned an unfamiliar shop format.");
    long now = System.currentTimeMillis();
    JSONObject shop = new JSONObject().put("SkinsPanelLayout", panel).put("checkedAt", now);
    cache(c, "shop_" + id + ".json", shop);
    History.record(c, id, shop);
    JSONObject prices = cached(c, "prices.json");
    if (now - prices.optLong("updatedAt") > 7L * 86400000L)
      try {
        JSONArray all =
            request(base + "/store/v1/offers/", "GET", headers, null).data.getJSONArray("Offers");
        for (int i = 0; i < all.length(); i++) {
          JSONObject o = all.getJSONObject(i), cost = o.optJSONObject("Cost");
          if (cost != null && cost.has(VP)) prices.put(o.getString("OfferID"), cost.getInt(VP));
        }
        prices.put("updatedAt", now);
      } catch (Exception ignored) {
      }
    JSONArray daily = panel.optJSONArray("SingleItemStoreOffers");
    if (daily != null)
      for (int i = 0; i < daily.length(); i++) {
        JSONObject o = daily.getJSONObject(i), cost = o.optJSONObject("Cost");
        if (cost != null && cost.has(VP)) prices.put(o.getString("OfferID"), cost.getInt(VP));
      }
    cache(c, "prices.json", prices);
    Catalogue.get(c).prices = prices;
    if (now - s.optLong("ownedAt") > 86400000L)
      try {
        JSONObject owned =
            request(
                    base + "/store/v1/entitlements/" + id + "/e7c63390-eda7-46e0-bb7a-a6abdacd2433",
                    "GET",
                    headers,
                    null)
                .data;
        Set<String> ids = new HashSet<>();
        JSONArray groups = owned.optJSONArray("EntitlementsByTypes");
        if (groups == null && owned.optJSONArray("Entitlements") == null)
          throw new IOException("Unknown ownership response");
        if (groups != null)
          for (int g = 0; g < groups.length(); g++)
            addOwned(ids, groups.getJSONObject(g).optJSONArray("Entitlements"));
        else addOwned(ids, owned.optJSONArray("Entitlements"));
        cache(
            c,
            "owned_" + id + ".json",
            new JSONObject().put("ids", new JSONArray(ids)).put("synced", now));
        s.put("ownedAt", now);
      } catch (Exception ignored) {
      }
    if (s.optString("name").isEmpty())
      try {
        JSONArray names =
            request(base + "/name-service/v2/players", "PUT", headers, new JSONArray().put(id))
                .data
                .getJSONArray("items");
        JSONObject name = names.getJSONObject(0);
        if (!name.optString("GameName").isEmpty())
          s.put("name", name.optString("GameName") + "#" + name.optString("TagLine"));
      } catch (Exception ignored) {
      }
    if (s.optString("name").isEmpty())
      try {
        JSONObject
            user =
                request(
                        AUTH + "/userinfo",
                        "GET",
                        Util.map("Authorization", "Bearer " + token),
                        null)
                    .data,
            acct = user.optJSONObject("acct");
        if (acct != null && !acct.optString("game_name").isEmpty())
          s.put("name", acct.optString("game_name") + "#" + acct.optString("tag_line"));
      } catch (Exception ignored) {
      }
    s.put("expired", false)
        .put("error", "")
        .put("lastSuccess", now)
        .put(
            "nextAttempt",
            Math.max(
                now + 60000,
                SkinLogic.expiresAt(
                        now, panel.optLong("SingleItemOffersRemainingDurationInSeconds"))
                    + 120000));
    Accounts.put(c, s);
    prefs(c).edit().remove("widgetState").putLong("lastCheck", now).apply();
    return shop;
  }

  static void addOwned(Set<String> out, JSONArray a) {
    if (a != null)
      for (int i = 0; i < a.length(); i++) {
        JSONObject item = a.optJSONObject(i);
        if (item != null) out.add(item.optString("ItemID"));
      }
    out.remove("");
  }

  static synchronized void signOut(Context c) {
    try {
      Accounts.remove(c, Accounts.active(c));
    } catch (Exception ignored) {
    }
  }

  static synchronized void acceptSession(Context c, JSONObject session) throws Exception {
    String id = claims(session.getString("access")).getString("sub");
    if (!id.matches("[a-zA-Z0-9-]+")) throw new IOException("Invalid account identity.");
    JSONObject previous;
    try {
      previous = Accounts.get(c, id);
    } catch (Exception e) {
      previous = new JSONObject();
    }
    previous
        .put("id", id)
        .put("access", session.getString("access"))
        .put("cookies", session.optString("cookies"))
        .put("region", prefs(c).getString("region", "eu"))
        .put("expired", false)
        .put("error", "")
        .put("nextAttempt", 0);
    Accounts.put(c, previous);
    Accounts.select(c, id);
  }

  static Set<String> offers(JSONObject shop) {
    Set<String> out = new HashSet<>();
    JSONObject panel = shop.optJSONObject("SkinsPanelLayout");
    JSONArray arr = panel == null ? null : panel.optJSONArray("SingleItemOffers");
    if (arr != null) for (int i = 0; i < arr.length(); i++) out.add(arr.optString(i));
    if (out.isEmpty() && panel != null) {
      JSONArray detailed = panel.optJSONArray("SingleItemStoreOffers");
      if (detailed != null)
        for (int i = 0; i < detailed.length(); i++)
          out.add(detailed.optJSONObject(i).optString("OfferID"));
    }
    out.remove("");
    return out;
  }
}
