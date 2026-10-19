package app.quietshop;

import android.Manifest;
import android.app.*;
import android.app.job.*;
import android.content.*;
import android.content.pm.PackageManager;
import android.os.*;
import org.json.*;

/**
 * One persisted job at the earliest account rotation expiry, using elapsed delay rather than local
 * clock hours.
 */
final class Daily {
  static final int JOB = 4043;
  static final String CHANNEL = "account_alerts_quiet";
  static final String UPDATED = "app.quietshop.SHOP_UPDATED";

  static void changed(Context c) {
    c.sendBroadcast(new Intent(UPDATED).setPackage(c.getPackageName()));
  }

  static void schedule(Context c) {
    JobScheduler jobs = c.getSystemService(JobScheduler.class);
    if (!Store.prefs(c).getBoolean("daily", true) || Accounts.list(c).isEmpty()) {
      jobs.cancel(JOB);
      return;
    }
    long now = System.currentTimeMillis(), next = Long.MAX_VALUE;
    for (JSONObject a : Accounts.list(c)) next = Math.min(next, a.optLong("nextAttempt", 0));
    long delay = SkinLogic.nextDelay(next, now);
    jobs.schedule(
        new JobInfo.Builder(JOB, new ComponentName(c, DailyJob.class))
            .setRequiredNetworkType(JobInfo.NETWORK_TYPE_ANY)
            .setMinimumLatency(delay)
            .setOverrideDeadline(delay + 15 * 60000L)
            .setPersisted(true)
            .build());
  }

  static void channel(Context c) {
    NotificationChannel ch =
        new NotificationChannel(
            CHANNEL, "Quiet account reminders", NotificationManager.IMPORTANCE_LOW);
    ch.setSound(null, null);
    ch.enableVibration(false);
    ch.enableLights(false);
    c.getSystemService(NotificationManager.class).createNotificationChannel(ch);
  }

  static void failure(Context c, JSONObject account, Exception error) {
    if (!Store.prefs(c).getBoolean("notices", true)) return;
    channel(c);
    if (Build.VERSION.SDK_INT >= 33
        && c.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS)
            != PackageManager.PERMISSION_GRANTED) return;
    String id = account.optString("id"), key = "notice_" + id;
    long now = System.currentTimeMillis();
    if (now - Store.prefs(c).getLong(key, 0) < 86400000L) return;
    boolean expired = error instanceof Store.SessionExpired;
    String title = expired ? "Account sign-in expired" : "Shop refresh needs attention";
    String message =
        Accounts.label(account)
            + (expired
                ? " — reconnect in Settings to resume daily history."
                : " — today's offers couldn't be loaded. Check your connection or open Settings.");
    PendingIntent open =
        PendingIntent.getActivity(
            c,
            12,
            new Intent(c, MainActivity.class).putExtra("page", 3),
            PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
    c.getSystemService(NotificationManager.class)
        .notify(
            id.hashCode(),
            new Notification.Builder(c, CHANNEL)
                .setSmallIcon(R.drawable.ic_refresh)
                .setContentTitle(title)
                .setContentText(message)
                .setStyle(new Notification.BigTextStyle().bigText(message))
                .setContentIntent(open)
                .setAutoCancel(true)
                .setSound(null)
                .setVibrate(new long[0])
                .setDefaults(0)
                .setOnlyAlertOnce(true)
                .build());
    Store.prefs(c).edit().putLong(key, now).apply();
  }
}
