package app.quietshop;

import android.app.*;import android.os.*;import android.graphics.Bitmap;import android.net.Uri;import android.view.*;import android.webkit.*;import android.widget.*;
import androidx.appcompat.app.AppCompatActivity;import androidx.core.view.ViewCompat;import androidx.core.view.WindowInsetsCompat;
import com.google.android.material.appbar.MaterialToolbar;import org.json.*;import java.util.*;import java.util.concurrent.*;

/** An isolated Riot login page, with no JavaScript bridge or injected scripts. */
public class LoginActivity extends AppCompatActivity {
    WebView web;TextView status;String state;boolean captured=false;final ExecutorService task=Executors.newSingleThreadExecutor();
    @Override public void onCreate(Bundle saved){
        super.onCreate(saved);getWindow().addFlags(WindowManager.LayoutParams.FLAG_SECURE);state=UUID.randomUUID().toString();LinearLayout root=new LinearLayout(this);root.setOrientation(1);setContentView(root);
        ViewCompat.setOnApplyWindowInsetsListener(root,(v,i)->{var b=i.getInsets(WindowInsetsCompat.Type.systemBars());v.setPadding(b.left,b.top,b.right,b.bottom);return i;});
        MaterialToolbar bar=new MaterialToolbar(this);bar.setTitle("Riot sign-in");bar.setNavigationIcon(R.drawable.ic_arrow_back);bar.setNavigationOnClickListener(v->finish());root.addView(bar);
        status=new TextView(this);status.setText("auth.riotgames.com · HTTPS");status.setPadding(24,8,24,8);root.addView(status);
        web=new WebView(this);web.getSettings().setJavaScriptEnabled(true);web.getSettings().setDomStorageEnabled(true);web.getSettings().setAllowFileAccess(false);web.getSettings().setAllowContentAccess(false);web.getSettings().setMixedContentMode(WebSettings.MIXED_CONTENT_NEVER_ALLOW);CookieManager.getInstance().setAcceptThirdPartyCookies(web,false);
        web.setWebViewClient(new WebViewClient(){
            boolean navigate(String url){Uri uri=Uri.parse(url);String host=uri.getHost();
                if("https".equals(uri.getScheme())&&"playvalorant.com".equals(host)&&"/opt_in".equals(uri.getPath())){capture(url);return true;}
                boolean allowed="https".equals(uri.getScheme())&&host!=null&&(host.equals("riotgames.com")||host.endsWith(".riotgames.com"));if(!allowed)Toast.makeText(LoginActivity.this,"Use Riot username sign-in; external social login is not supported.",Toast.LENGTH_LONG).show();return !allowed;
            }
            public boolean shouldOverrideUrlLoading(WebView v,WebResourceRequest r){return navigate(r.getUrl().toString());}
            public void onPageStarted(WebView v,String url,Bitmap icon){Uri uri=Uri.parse(url);if("playvalorant.com".equals(uri.getHost())&&"/opt_in".equals(uri.getPath()))navigate(url);else status.setText(uri.getHost()+" · HTTPS");}
        });root.addView(web,new LinearLayout.LayoutParams(-1,0,1));CookieManager.getInstance().removeAllCookies(done->{if(!isFinishing())web.loadUrl(Store.loginUrl(state));});
    }
    void capture(String url){if(captured)return;String token=Store.parameter(url,"access_token");if(!state.equals(Store.parameter(url,"state"))||token==null){status.setText("Sign-in was not completed. Go back and retry.");return;}captured=true;String cookies=CookieManager.getInstance().getCookie(Store.AUTH);status.setText("Saving session securely…");web.setVisibility(View.GONE);
        task.submit(()->{try{Store.claims(token).getString("sub");Store.acceptSession(this,new JSONObject().put("access",token).put("cookies",cookies==null?"":cookies));ShopWidget.updateAll(this);runOnUiThread(()->{setResult(RESULT_OK);finish();});}catch(Exception e){runOnUiThread(()->{captured=false;status.setText("Could not save the session. Go back and retry.");});}});
    }
    @Override protected void onDestroy(){if(web!=null){web.stopLoading();web.clearCache(true);web.clearHistory();((ViewGroup)web.getParent()).removeView(web);web.destroy();}CookieManager.getInstance().removeAllCookies(null);WebStorage.getInstance().deleteAllData();task.shutdownNow();super.onDestroy();}
}
