package app.quietshop;

import android.content.*;

public class RefreshReceiver extends BroadcastReceiver {
  public void onReceive(Context c, Intent i) {
    Daily.schedule(c);
    ShopWidget.updateAll(c);
  }
}
