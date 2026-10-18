package app.quietshop;
import android.app.job.*;import java.util.concurrent.*;

/** One user-requested widget refresh. No periodic scheduling, notifications or wakeup loop. */
public class WidgetRefreshJob extends JobService {
    final ExecutorService executor=Executors.newSingleThreadExecutor();Future<?> pending;
    @Override public boolean onStartJob(JobParameters params){pending=executor.submit(()->{try{Store.refresh(this);Store.prefs(this).edit().remove("widgetState").apply();}catch(Exception e){Store.prefs(this).edit().putString("widgetState","Open app").apply();}ShopWidget.updateAll(this);jobFinished(params,false);});return true;}
    @Override public boolean onStopJob(JobParameters params){if(pending!=null)pending.cancel(true);Store.prefs(this).edit().putString("widgetState","Retry").apply();return false;}
    @Override public void onDestroy(){executor.shutdownNow();super.onDestroy();}
}
