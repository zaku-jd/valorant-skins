package app.quietshop;

import android.content.*;
import android.database.Cursor;
import android.database.sqlite.*;
import java.util.*;
import org.json.*;

final class History extends SQLiteOpenHelper {
  private final Context context;

  private History(Context c) {
    super(c, "shop-history.db", null, 1);
    context = c.getApplicationContext();
  }

  public void onCreate(SQLiteDatabase db) {
    try {
      db.execSQL(Catalogue.read(context, "history-schema.sql"));
    } catch (Exception e) {
      throw new IllegalStateException("Cannot create shop history", e);
    }
  }

  public void onUpgrade(SQLiteDatabase db, int old, int next) {}

  static void record(Context c, String account, JSONObject shop) {
    JSONObject panel = shop.optJSONObject("SkinsPanelLayout");
    if (panel == null || account.isEmpty() || Store.offers(shop).isEmpty()) return;
    long checked = shop.optLong("checkedAt"),
        end =
            SkinLogic.expiresAt(
                checked, panel.optLong("SingleItemOffersRemainingDurationInSeconds"));
    if (checked <= 0) return;
    try (History helper = new History(c)) {
      ContentValues row = new ContentValues();
      row.put("account", account);
      row.put("rotation", SkinLogic.rotation(end));
      row.put("checked", checked);
      row.put("ends", end);
      row.put("payload", shop.toString());
      helper
          .getWritableDatabase()
          .insertWithOnConflict("rotations", null, row, SQLiteDatabase.CONFLICT_IGNORE);
    }
  }

  static List<JSONObject> list(Context c, String account) {
    List<JSONObject> out = new ArrayList<>();
    try (History helper = new History(c);
        Cursor rows =
            helper
                .getReadableDatabase()
                .query(
                    "rotations",
                    new String[] {"payload"},
                    "account=?",
                    new String[] {account},
                    null,
                    null,
                    "ends DESC")) {
      while (rows.moveToNext())
        try {
          out.add(new JSONObject(rows.getString(0)));
        } catch (JSONException ignored) {
        }
    }
    return out;
  }

  static Map<String, Integer> appearances(Context c, String account) {
    Map<String, Integer> count = new HashMap<>();
    for (JSONObject shop : list(c, account))
      for (String id : Store.offers(shop)) count.put(id, count.getOrDefault(id, 0) + 1);
    return count;
  }
}
