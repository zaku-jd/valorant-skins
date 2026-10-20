package app.quietshop;

import android.app.*;
import android.app.job.*;
import android.appwidget.*;
import android.content.*;
import android.graphics.*;
import android.os.*;
import android.widget.RemoteViews;
import java.util.*;
import org.json.*;

public class ShopWidget extends AppWidgetProvider {
  static final String REFRESH = "app.quietshop.WIDGET_REFRESH";

  @Override
  public void onReceive(Context c, Intent intent) {
    if (REFRESH.equals(intent.getAction())) {
      Store.prefs(c).edit().putString("widgetState", "…").apply();
      updateAll(c);
      JobInfo.Builder job =
          new JobInfo.Builder(4042, new ComponentName(c, WidgetRefreshJob.class))
              .setRequiredNetworkType(JobInfo.NETWORK_TYPE_ANY);
      if (Build.VERSION.SDK_INT >= 31) job.setExpedited(true);
      c.getSystemService(JobScheduler.class).schedule(job.build());
    } else super.onReceive(c, intent);
  }

  @Override
  public void onUpdate(Context c, AppWidgetManager manager, int[] ids) {
    for (int id : ids) render(c, manager, id);
  }

  @Override
  public void onAppWidgetOptionsChanged(
      Context c, AppWidgetManager manager, int id, Bundle options) {
    render(c, manager, id);
  }

  static void updateAll(Context c) {
    AppWidgetManager manager = AppWidgetManager.getInstance(c);
    for (int id : manager.getAppWidgetIds(new ComponentName(c, ShopWidget.class)))
      render(c, manager, id);
  }

  static Bitmap tile(Context c, JSONObject skin) {
    Bitmap tile = Bitmap.createBitmap(160, 150, Bitmap.Config.ARGB_8888);
    Canvas canvas = new Canvas(tile);
    Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
    Catalogue cat = Catalogue.get(c);
    p.setColor(
        skin == null
            ? MainActivity.SURFACE
            : MainActivity.blend(MainActivity.SURFACE, cat.color(skin), 0.4f));
    canvas.drawRoundRect(0, 0, 160, 150, 24, 24, p);
    if (skin != null) {
      Bitmap art = cat.image(c, cat.primary(skin));
      if (art != null) {
        float scale = Math.min(144f / art.getWidth(), 128f / art.getHeight());
        float width = art.getWidth() * scale, height = art.getHeight() * scale;
        canvas.drawBitmap(
            art,
            null,
            new RectF((160 - width) / 2, (150 - height) / 2, (160 + width) / 2, (150 + height) / 2),
            new Paint(Paint.ANTI_ALIAS_FLAG | Paint.FILTER_BITMAP_FLAG));
      }
    }
    return tile;
  }

  static void render(Context c, AppWidgetManager manager, int widget) {
    RemoteViews views = new RemoteViews(c.getPackageName(), R.layout.shop_widget);
    JSONObject shop = Store.cached(c, "shop_" + Accounts.main(c) + ".json");
    long checked = shop.optLong("checkedAt");
    JSONObject panel = shop.optJSONObject("SkinsPanelLayout");
    long end =
        SkinLogic.expiresAt(
            checked,
            panel == null ? 0 : panel.optLong("SingleItemOffersRemainingDurationInSeconds"));
    List<String> offers = new ArrayList<>(Store.offers(shop));
    Collections.sort(offers);
    boolean expired = !SkinLogic.current(checked, end, System.currentTimeMillis());
    int[] images = {R.id.weapon1, R.id.weapon2, R.id.weapon3, R.id.weapon4};
    Catalogue cat = Catalogue.get(c);
    PendingIntent open =
        PendingIntent.getActivity(
            c,
            0,
            new Intent(c, MainActivity.class).putExtra("account", Accounts.main(c)),
            PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
    for (int i = 0; i < 4; i++) {
      JSONObject s = !expired && i < offers.size() ? cat.skins.get(offers.get(i)) : null;
      views.setImageViewBitmap(images[i], tile(c, s));
      views.setContentDescription(
          images[i], s == null ? "Refresh to load today’s shop" : s.optString("displayName"));
      views.setOnClickPendingIntent(images[i], open);
    }
    PendingIntent refresh =
        PendingIntent.getBroadcast(
            c,
            1,
            new Intent(c, ShopWidget.class).setAction(REFRESH),
            PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
    views.setOnClickPendingIntent(R.id.widget_refresh, refresh);
    String stamp = Store.prefs(c).getString("widgetState", "");
    if (stamp.isEmpty())
      stamp =
          expired ? "Refresh" : android.text.format.DateFormat.format("dd/MM", checked).toString();
    views.setTextViewText(R.id.widget_date, stamp);
    views.setOnClickPendingIntent(R.id.widget_date, open);
    manager.updateAppWidget(widget, views);
  }
}
