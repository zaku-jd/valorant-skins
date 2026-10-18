package app.quietshop;

import android.content.Context;
import android.content.SharedPreferences;
import android.net.Uri;
import android.util.Base64;
import org.json.*;
import javax.net.ssl.HttpsURLConnection;
import java.net.*;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.*;

final class Store {
    static final String API = "https://valorant-api.com/v1/";
    static final String CALLBACK = "https://playvalorant.com/opt_in";
    static final String AUTH = "https://auth.riotgames.com";
    static final String VP = "85ad13f7-3d1b-5128-9eb2-7cd8a2d5b362";
    static SharedPreferences prefs(Context c) { return c.getSharedPreferences("settings", 0); }
    static class Reply {
        JSONObject data; List<String> cookies = new ArrayList<>();
    }
    static Reply request(String url, String method, Map<String,String> headers, JSONObject body) throws Exception {
        HttpsURLConnection conn = (HttpsURLConnection) new URL(url).openConnection();
        conn.setConnectTimeout(20000); conn.setReadTimeout(25000); conn.setInstanceFollowRedirects(false);
        conn.setRequestMethod(method); conn.setRequestProperty("Accept", "application/json");
        for (var h : headers.entrySet()) conn.setRequestProperty(h.getKey(), h.getValue());
        try {
            if (body != null) {
                conn.setDoOutput(true); conn.setRequestProperty("Content-Type", "application/json");
                try (OutputStream out = conn.getOutputStream()) { out.write(body.toString().getBytes(StandardCharsets.UTF_8)); }
            }
            int code = conn.getResponseCode();
            if (code == 401) throw new IOException("Riot session expired. Sign in again in Settings.");
            if (code == 403) throw new IOException("Riot refused this request. Sign in again, or retry later; no checks can run while access is blocked.");
            if (code == 429) throw new IOException("Riot rate limit reached. Checks will wait and retry later.");
            if (code < 200 || code >= 300) throw new IOException("Service unavailable (HTTP " + code + "). Try again later.");
            Reply reply = new Reply();
            for (var h : conn.getHeaderFields().entrySet()) if (h.getKey() != null && h.getKey().equalsIgnoreCase("Set-Cookie")) reply.cookies.addAll(h.getValue());
            try (InputStream in = conn.getInputStream()) { reply.data = new JSONObject(new String(Util.read(in), StandardCharsets.UTF_8)); }
            return reply;
        } finally { conn.disconnect(); }
    }
    static JSONObject get(String url) throws Exception { return request(url, "GET", Util.map(), null).data; }
    static File file(Context c, String name) { return new File(c.getFilesDir(), name); }
    static JSONObject cached(Context c, String name) {
        try (InputStream in = new FileInputStream(file(c,name))) { return new JSONObject(new String(Util.read(in), StandardCharsets.UTF_8)); }
        catch (Exception e) { return new JSONObject(); }
    }
    static void cache(Context c, String name, JSONObject data) throws Exception {
        File tmp = file(c, name + ".tmp");
        try (OutputStream out = new FileOutputStream(tmp)) { out.write(data.toString().getBytes(StandardCharsets.UTF_8)); }
        if (!tmp.renameTo(file(c, name))) throw new IOException("Unable to save local cache.");
    }
    static String parameter(String url, String key) {
        Uri u = Uri.parse(url);
        String q = u.getQueryParameter(key);
        if (q != null) return q;
        String f = u.getFragment();
        return f == null ? null : Uri.parse("https://local/?"+f).getQueryParameter(key);
    }
    static String loginUrl(String state) {
        return AUTH+"/authorize?client_id=play-valorant-web-prod&redirect_uri="+Uri.encode(CALLBACK)+"&response_type=token%20id_token&scope=account%20openid&nonce="+state+"&state="+state;
    }
    static JSONObject claims(String token) throws Exception {
        return new JSONObject(new String(Base64.decode(token.split("\\.")[1], Base64.URL_SAFE | Base64.NO_WRAP), StandardCharsets.UTF_8));
    }
    static String mergeCookies(String old, List<String> updates) {
        Map<String,String> jar = new LinkedHashMap<>();
        for (String part : old.split(";")) { int at = part.indexOf('='); if (at>0) jar.put(part.substring(0,at).trim(),part.substring(at+1).trim()); }
        for (String set : updates) {
            try { for (HttpCookie cookie : HttpCookie.parse(set)) { if (cookie.getMaxAge()==0 || cookie.hasExpired()) jar.remove(cookie.getName()); else jar.put(cookie.getName(),cookie.getValue()); } }
            catch (IllegalArgumentException ignored) { }
        }
        List<String> parts = new ArrayList<>(); for (var entry : jar.entrySet()) parts.add(entry.getKey()+"="+entry.getValue()); return String.join("; ",parts);
    }
    static synchronized JSONObject session(Context c) throws Exception {
        JSONObject s = Vault.read(c);
        String token = s.optString("access");
        if (token.isEmpty()) throw new IOException("Sign in to Riot in Settings to see your offers.");
        if (claims(token).optLong("exp") > System.currentTimeMillis()/1000+120) return s;
        String cookies = s.optString("cookies");
        if (cookies.isEmpty()) throw new IOException("Session expired. Sign in again in Settings.");
        JSONObject body = new JSONObject().put("client_id","play-valorant-web-prod").put("redirect_uri",CALLBACK)
            .put("response_type","token id_token").put("response_mode","query").put("scope","account openid").put("nonce", UUID.randomUUID().toString());
        Reply re = request(AUTH+"/api/v1/authorization", "POST", Util.map("Cookie", cookies), body);
        s.put("cookies",mergeCookies(cookies,re.cookies));
        JSONObject response = re.data.optJSONObject("response");
        JSONObject params = response == null ? null : response.optJSONObject("parameters");
        String access = params == null ? null : parameter(params.optString("uri"),"access_token");
        if (access == null) { Vault.save(c,s); throw new IOException("Riot requires sign-in again. Open Settings to continue."); }
        s.put("access",access); Vault.save(c,s); return s;
    }
    static synchronized JSONObject refresh(Context c) throws Exception {
        JSONObject s = session(c); String token = s.getString("access");
        String entitlement = request("https://entitlements.auth.riotgames.com/api/token/v1/", "POST", Util.map("Authorization","Bearer "+token), new JSONObject()).data.getString("entitlements_token");
        String region = prefs(c).getString("region","eu");
        if (!Util.set("eu","na","ap","kr").contains(region)) throw new IOException("Select a valid account region.");
        String sub = claims(token).getString("sub");
        if (!sub.matches("[a-zA-Z0-9-]+")) throw new IOException("Invalid account ID.");
        String platform = Base64.encodeToString("{\"platformType\":\"PC\",\"platformOS\":\"Windows\",\"platformOSVersion\":\"10.0.19042.1.256.64bit\",\"platformChipset\":\"Unknown\"}".getBytes(StandardCharsets.UTF_8),Base64.NO_WRAP);
        String version=get(API+"version").getJSONObject("data").getString("riotClientVersion");
        var headers = Util.map("Authorization","Bearer "+token,"X-Riot-Entitlements-JWT",entitlement,"X-Riot-ClientVersion",version,"X-Riot-ClientPlatform",platform);
        JSONObject shop = request("https://pd."+region+".a.pvp.net/store/v3/storefront/"+sub,"POST",headers,new JSONObject()).data;
        if (shop.optJSONObject("SkinsPanelLayout") == null) throw new IOException("Riot returned an unfamiliar store format. App update needed.");
        // Retain daily rotation only; drop bundles and unrelated account data.
        shop=new JSONObject().put("SkinsPanelLayout",shop.getJSONObject("SkinsPanelLayout")).put("checkedAt",System.currentTimeMillis());
        cache(c,"shop.json",shop);
        JSONObject prices=cached(c,"prices.json");
        if(System.currentTimeMillis()-prices.optLong("updatedAt")>7L*86400000L){
            try {
                JSONArray all=request("https://pd."+region+".a.pvp.net/store/v1/offers/","GET",headers,null).data.getJSONArray("Offers");
                for(int i=0;i<all.length();i++){JSONObject offer=all.getJSONObject(i);JSONObject cost=offer.optJSONObject("Cost");if(cost!=null&&cost.has(VP))prices.put(offer.getString("OfferID"),cost.getInt(VP));}
                prices.put("updatedAt",System.currentTimeMillis());
            }catch(Exception ignored){ /* Daily offers remain usable if the optional price catalogue is unavailable. */ }
        }
        JSONArray daily=shop.getJSONObject("SkinsPanelLayout").optJSONArray("SingleItemStoreOffers");
        if(daily!=null)for(int i=0;i<daily.length();i++){JSONObject offer=daily.getJSONObject(i),cost=offer.optJSONObject("Cost");if(cost!=null&&cost.has(VP))prices.put(offer.getString("OfferID"),cost.getInt(VP));}
        cache(c,"prices.json",prices);Catalogue.get(c).prices=prices;
        prefs(c).edit().remove("widgetState").putString("status","Last check succeeded").putLong("lastCheck",System.currentTimeMillis()).apply();
        return shop;
    }
    static synchronized void signOut(Context c){Vault.clear(c);file(c,"shop.json").delete();prefs(c).edit().remove("lastCheck").remove("status").remove("widgetState").apply();}
    static synchronized void acceptSession(Context c,JSONObject s)throws Exception{Vault.save(c,s);file(c,"shop.json").delete();prefs(c).edit().remove("widgetState").apply();}
    static Set<String> offers(JSONObject shop) {
        Set<String> out = new HashSet<>(); JSONObject panel = shop.optJSONObject("SkinsPanelLayout");
        JSONArray arr = panel == null ? null : panel.optJSONArray("SingleItemOffers");
        if (arr != null) for (int i=0;i<arr.length();i++) out.add(arr.optString(i));
        if (out.isEmpty() && panel != null) { JSONArray detailed = panel.optJSONArray("SingleItemStoreOffers"); if (detailed != null) for(int i=0;i<detailed.length();i++) out.add(detailed.optJSONObject(i).optString("OfferID")); }
        out.remove(""); return out;
    }
}
