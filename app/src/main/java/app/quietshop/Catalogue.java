package app.quietshop;

import android.content.Context;
import android.graphics.*;
import android.util.LruCache;
import org.json.*;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.*;

/** Immutable bundled catalogue. No network requests and no on-device asset updates. */
final class Catalogue {
    static Catalogue instance;
    final Map<String,JSONObject> skins=new LinkedHashMap<>(), tiers=new LinkedHashMap<>();
    final Map<String,String> weaponIcons=new LinkedHashMap<>();
    final JSONObject images,info;
    volatile JSONObject prices;
    final LruCache<String,Bitmap> bitmaps=new LruCache<String,Bitmap>(24000){protected int sizeOf(String k,Bitmap b){return b.getByteCount()/1024;}};
    static synchronized Catalogue get(Context c) {
        if(instance==null)try{instance=new Catalogue(c.getApplicationContext());}catch(Exception e){throw new IllegalStateException("Bundled catalogue could not be read",e);}return instance;
    }
    static String read(Context c,String name)throws Exception{try(InputStream in=c.getAssets().open(name)){return new String(Util.read(in),StandardCharsets.UTF_8);}}
    Catalogue(Context c)throws Exception{
        images=new JSONObject(read(c,"image-index.json")); info=new JSONObject(read(c,"skin-info.json"));prices=Store.cached(c,"prices.json");
        JSONArray ts=new JSONArray(read(c,"tiers.json"));for(int i=0;i<ts.length();i++){JSONObject t=ts.getJSONObject(i);tiers.put(t.getString("uuid"),t);}
        Map<String,String> themes=new HashMap<>();JSONArray th=new JSONArray(read(c,"themes.json"));for(int i=0;i<th.length();i++)themes.put(th.getJSONObject(i).getString("uuid"),th.getJSONObject(i).getString("displayName"));
        JSONArray weapons=new JSONObject(read(c,"weapons.json")).getJSONArray("data");
        for(int i=0;i<weapons.length();i++){
            JSONObject weapon=weapons.getJSONObject(i);String gun=weapon.getString("displayName");weaponIcons.put(gun,weapon.optString("displayIcon"));
            JSONArray ws=weapon.getJSONArray("skins");
            for(int j=0;j<ws.length();j++){
                JSONObject skin=ws.getJSONObject(j);String id=skin.getJSONArray("levels").getJSONObject(0).getString("uuid");
                JSONObject extra=info.optJSONObject(id.replace("-",""));
                String line=themes.getOrDefault(skin.optString("themeUuid"),skin.optString("displayName").replace(" "+gun,""));
                skin.put("weapon",gun).put("category",weapon.optString("category").replace("EEquippableCategory::",""));
                skin.put("line",extra==null?line:extra.optString("collection",line));
                skin.put("obtain",extra==null?(skin.optString("displayName").startsWith("Standard ")?"free":"unknown"):extra.optString("obtain","unknown"));
                skin.put("recordedPrice",extra==null?0:extra.optInt("price",0));skins.put(id,skin);
            }
        }
    }
    String primary(JSONObject s){String icon=s.optString("displayIcon","");if(icon.isEmpty()||icon.equals("null"))icon=s.optJSONArray("levels").optJSONObject(0).optString("displayIcon","");return icon;}
    Bitmap image(Context c,String url){
        if(url==null)return null;Bitmap hit=bitmaps.get(url);if(hit!=null)return hit;
        String asset=images.optString(url,"");if(asset.isEmpty())return null;
        try(InputStream in=c.getAssets().open(asset)){Bitmap bitmap=BitmapFactory.decodeStream(in);if(bitmap!=null)bitmaps.put(url,bitmap);return bitmap;}catch(Exception e){return null;}
    }
    String tier(JSONObject s){JSONObject t=tiers.get(s.optString("contentTierUuid"));return t==null?"Standard":t.optString("displayName").replace(" Edition","");}
    int color(JSONObject s){JSONObject t=tiers.get(s.optString("contentTierUuid"));try{return t==null?Color.rgb(105,118,139):Color.parseColor("#"+t.getString("highlightColor").substring(0,6));}catch(Exception e){return Color.GRAY;}}
    int price(Context c,String id){
        JSONObject s=skins.get(id);int known=prices.optInt(id,0);if(known>0)return known;
        if(s==null)return 0;if(s.optInt("recordedPrice")>0)return s.optInt("recordedPrice");
        String obtain=s.optString("obtain");if(!obtain.equals("store")&&!obtain.equals("bundle-only"))return 0;
        int base=switch(tier(s)){case "Select"->875;case "Deluxe"->1275;case "Premium"->1775;case "Exclusive"->2175;case "Ultra"->2475;default->0;};
        return s.optString("weapon").equals("Melee")?base*2:base;
    }
    boolean estimated(Context c,String id){JSONObject s=skins.get(id);return s!=null&&s.optInt("recordedPrice")==0&&prices.optInt(id,0)==0&&price(c,id)>0;}
    String priceText(Context c,String id){
        int cost=price(c,id);if(cost>0)return (estimated(c,id)?"≈ ":"")+String.format(Locale.getDefault(),"%,d VP",cost);
        JSONObject s=skins.get(id);String obtain=s==null?"":s.optString("obtain");return switch(obtain){case "pass"->"Battle pass";case "free"->"Included";case "contract"->"Agent gear";default->"Price unavailable";};
    }
    static String wiki(JSONObject skin){return "https://valorant.fandom.com/wiki/"+android.net.Uri.encode(skin.optString("line").replace(' ','_')+"_Collection")+"#Weapon_Skins";}
}
