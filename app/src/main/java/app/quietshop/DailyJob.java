package app.quietshop;

import android.app.job.*;
import android.os.*;
import java.util.concurrent.*;
import org.json.*;

public class DailyJob extends JobService {
  final ExecutorService executor = Executors.newSingleThreadExecutor();
  Future<?> pending;

  @Override
  public boolean onStartJob(JobParameters params) {
    if (!Store.prefs(this).getBoolean("daily", true)) return false;
    pending =
        executor.submit(
            () -> {
              for (JSONObject a : Accounts.list(this)) {
                if (Thread.currentThread().isInterrupted()
                    || !Store.prefs(this).getBoolean("daily", true)) return;
                if (a.optLong("nextAttempt") > System.currentTimeMillis()) continue;
                try {
                  Store.refresh(this, a.getString("id"), false);
                } catch (Exception e) {
                  Daily.failure(this, a, e);
                }
              }
              ShopWidget.updateAll(this);
              Daily.changed(this);
              new Handler(Looper.getMainLooper())
                  .post(
                      () -> {
                        jobFinished(params, false);
                        Daily.schedule(this);
                      });
            });
    return true;
  }

  @Override
  public boolean onStopJob(JobParameters params) {
    if (pending != null) pending.cancel(true);
    return true;
  }

  @Override
  public void onDestroy() {
    executor.shutdownNow();
    super.onDestroy();
  }
}
