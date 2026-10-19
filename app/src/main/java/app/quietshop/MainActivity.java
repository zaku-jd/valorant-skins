package app.quietshop;

import android.app.*;
import android.content.*;
import android.content.res.ColorStateList;
import android.graphics.*;
import android.graphics.drawable.Drawable;
import android.net.Uri;
import android.os.*;
import android.text.*;
import android.view.*;
import android.widget.*;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.recyclerview.widget.*;
import androidx.viewpager2.widget.ViewPager2;
import com.google.android.material.bottomsheet.BottomSheetDialog;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.card.MaterialCardView;
import com.google.android.material.chip.Chip;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.tabs.*;
import com.google.android.material.textfield.*;
import java.io.*;
import java.util.*;
import java.util.concurrent.*;
import org.json.*;

public class MainActivity extends AppCompatActivity {
  static final int BG = 0xff000000,
      SURFACE = 0xff111111,
      TEXT = 0xfff6f3f3,
      MUTED = 0xffb9abab,
      PRIMARY = 0xffff4655;
  final ExecutorService tasks = Executors.newSingleThreadExecutor(),
      imageTasks = Executors.newFixedThreadPool(3);
  final Handler clock = new Handler(Looper.getMainLooper());
  final Page[] pages = new Page[4];
  Catalogue catalogue;
  Set<String> stars;
  ViewPager2 pager;
  Typeface condensed;
  Set<String> eligible = new HashSet<>();
  Map<String, Integer> seen = new HashMap<>();
  int savedRotations = 0;
  JSONObject shop = new JSONObject(), livePrices = new JSONObject();
  boolean busy = false;
  final String[] titles = {"All Skins", "Shop", "Starred", "Settings"};
  final BroadcastReceiver shopUpdates =
      new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
          readShop();
          for (Page p : pages) if (p != null) p.filter();
        }
      };
  final Runnable tick =
      new Runnable() {
        public void run() {
          if (pages[1] != null) pages[1].countdown();
          clock.postDelayed(this, 1000);
        }
      };

  @Override
  public void onCreate(Bundle state) {
    super.onCreate(state);
    getWindow().setNavigationBarColor(android.graphics.Color.TRANSPARENT);
    if (Build.VERSION.SDK_INT >= 29) getWindow().setNavigationBarContrastEnforced(false);
    androidx.core.view.WindowCompat.setDecorFitsSystemWindows(getWindow(), false);
    catalogue = Catalogue.get(this);
    stars = new HashSet<>(Store.prefs(this).getStringSet("stars", Util.set()));
    condensed = Typeface.createFromAsset(getAssets(), "category-font.ttf");
    readShop();
    LinearLayout root = column();
    root.setBackgroundColor(BG);
    setContentView(root);
    ViewCompat.setOnApplyWindowInsetsListener(
        root,
        (v, i) -> {
          var bars = i.getInsets(WindowInsetsCompat.Type.systemBars());
          v.setPadding(bars.left, bars.top, bars.right, 0);
          return i;
        });
    TabLayout tabs = new TabLayout(this);
    tabs.setBackgroundColor(BG);
    tabs.setTabTextColors(MUTED, PRIMARY);
    tabs.setSelectedTabIndicatorColor(PRIMARY);
    root.addView(tabs);
    pager = new ViewPager2(this);
    pager.setOffscreenPageLimit(2);
    pager.setAdapter(
        new RecyclerView.Adapter<Slot>() {
          public int getItemCount() {
            return 4;
          }

          public Slot onCreateViewHolder(ViewGroup parent, int type) {
            FrameLayout f = new FrameLayout(MainActivity.this);
            f.setLayoutParams(new ViewGroup.LayoutParams(-1, -1));
            return new Slot(f);
          }

          public void onBindViewHolder(Slot h, int index) {
            if (pages[index] == null) pages[index] = new Page(index);
            h.frame.removeAllViews();
            if (pages[index].root.getParent() != null)
              ((ViewGroup) pages[index].root.getParent()).removeView(pages[index].root);
            h.frame.addView(pages[index].root);
          }
        });
    root.addView(pager, new LinearLayout.LayoutParams(-1, 0, 1));
    new TabLayoutMediator(tabs, pager, (tab, index) -> tab.setText(titles[index])).attach();
    pager.registerOnPageChangeCallback(
        new ViewPager2.OnPageChangeCallback() {
          public void onPageSelected(int index) {
            if (pages[index] != null) pages[index].filter();
          }
        });
    pager.setCurrentItem(
        state == null ? getIntent().getIntExtra("page", 1) : state.getInt("page", 1), false);
    androidx.core.view.WindowInsetsControllerCompat bars =
        new androidx.core.view.WindowInsetsControllerCompat(getWindow(), root);
    bars.hide(WindowInsetsCompat.Type.navigationBars());
    bars.setSystemBarsBehavior(
        androidx.core.view.WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE);
    Daily.channel(this);
    Daily.schedule(this);
    if (Build.VERSION.SDK_INT >= 33 && !Store.prefs(this).getBoolean("askedNotices", false)) {
      Store.prefs(this).edit().putBoolean("askedNotices", true).apply();
      requestPermissions(new String[] {android.Manifest.permission.POST_NOTIFICATIONS}, 90);
    }
    tasks.submit(
        () -> {
          try {
            if (!Accounts.active(this).isEmpty()) runOnUiThread(this::refresh);
          } catch (Exception ignored) {
          }
        });
  }

  static class Slot extends RecyclerView.ViewHolder {
    final FrameLayout frame;

    Slot(FrameLayout f) {
      super(f);
      frame = f;
    }
  }

  int dp(int v) {
    return Math.round(v * getResources().getDisplayMetrics().density);
  }

  LinearLayout column() {
    LinearLayout l = new LinearLayout(this);
    l.setOrientation(1);
    return l;
  }

  TextView text(String value, int size, int color) {
    TextView t = new TextView(this);
    t.setText(value);
    t.setTextColor(color);
    t.setTextSize(size);
    return t;
  }

  Drawable icon(int resource) {
    Drawable d = getDrawable(resource).mutate();
    d.setTint(TEXT);
    return d;
  }

  MaterialButton action(String label, int resource, Runnable run, boolean iconOnly) {
    MaterialButton b =
        new MaterialButton(this, null, com.google.android.material.R.attr.borderlessButtonStyle);
    b.setText(iconOnly ? "" : label);
    b.setContentDescription(label);
    b.setMinWidth(0);
    b.setMinimumWidth(0);
    b.setIcon(icon(resource));
    b.setIconTint(ColorStateList.valueOf(PRIMARY));
    b.setIconGravity(MaterialButton.ICON_GRAVITY_TEXT_START);
    b.setIconPadding(iconOnly ? 0 : dp(8));
    b.setPadding(dp(12), 0, dp(12), 0);
    b.setOnClickListener(v -> run.run());
    return b;
  }

  void loadImage(ImageView view, String url) {
    view.setImageDrawable(null);
    view.setTag(url);
    Bitmap cached = catalogue.bitmaps.get(url == null ? "" : url);
    if (cached != null) {
      view.setImageBitmap(cached);
      return;
    }
    imageTasks.submit(
        () -> {
          Bitmap b = catalogue.image(this, url);
          if (b != null)
            runOnUiThread(
                () -> {
                  if (Objects.equals(view.getTag(), url)) view.setImageBitmap(b);
                });
        });
  }

  void readShop() {
    shop = Store.shop(this);
    seen = History.appearances(this, Accounts.active(this));
    savedRotations = History.list(this, Accounts.active(this)).size();
    eligible.clear();
    Set<String> owned = new HashSet<>();
    JSONArray ownedArray =
        Store.cached(this, "owned_" + Accounts.active(this) + ".json").optJSONArray("ids");
    if (ownedArray != null)
      for (int i = 0; i < ownedArray.length(); i++) owned.add(ownedArray.optString(i));
    for (var entry : catalogue.skins.entrySet())
      if (catalogue.eligible(entry.getValue()) && !owned.contains(entry.getKey()))
        eligible.add(entry.getKey());
    livePrices = new JSONObject();
    JSONObject panel = shop.optJSONObject("SkinsPanelLayout");
    JSONArray offers = panel == null ? null : panel.optJSONArray("SingleItemStoreOffers");
    if (offers != null)
      for (int i = 0; i < offers.length(); i++) {
        JSONObject o = offers.optJSONObject(i);
        JSONObject cost = o.optJSONObject("Cost");
        try {
          if (cost != null && cost.has(Store.VP))
            livePrices.put(o.optString("OfferID"), cost.optInt(Store.VP));
        } catch (Exception ignored) {
        }
      }
  }

  String priceText(String id) {
    return livePrices.has(id)
        ? String.format(Locale.getDefault(), "%,d VP", livePrices.optInt(id))
        : catalogue.priceText(this, id);
  }

  int price(String id) {
    int v = livePrices.optInt(id, catalogue.price(this, id));
    return v > 0 ? v : Integer.MAX_VALUE;
  }

  void refresh() {
    refresh(false);
  }

  void refresh(boolean force) {
    if (busy) return;
    busy = true;
    if (pages[1] != null) pages[1].status.setText("Refreshing…");
    tasks.submit(
        () -> {
          try {
            Store.refresh(this, Accounts.active(this), force);
            Daily.schedule(this);
            ShopWidget.updateAll(this);
            runOnUiThread(
                () -> {
                  readShop();
                  for (Page p : pages) if (p != null) p.filter();
                  finishRefresh("");
                });
          } catch (Exception e) {
            Daily.schedule(this);
            runOnUiThread(() -> finishRefresh(error(e)));
          }
        });
  }

  void finishRefresh(String message) {
    busy = false;
    if (pages[1] != null) pages[1].status.setText(message.isEmpty() ? activeLabel() : message);
  }

  static String error(Exception e) {
    return e instanceof IOException && e.getMessage() != null
        ? e.getMessage()
        : "Could not refresh. Check your connection or sign in again.";
  }

  void star(String id) {
    if (!stars.add(id)) stars.remove(id);
    Store.prefs(this).edit().putStringSet("stars", new HashSet<>(stars)).apply();
    for (Page p : pages) if (p != null) p.filter();
  }

  String activeLabel() {
    try {
      return "Signed in as " + Accounts.label(Accounts.get(this, Accounts.active(this)));
    } catch (Exception e) {
      return "No account selected";
    }
  }

  void login() {
    startActivityForResult(new Intent(this, LoginActivity.class), 42);
  }

  @Override
  protected void onActivityResult(int req, int result, Intent data) {
    super.onActivityResult(req, result, data);
    if (req == 42 && result == RESULT_OK) {
      readShop();
      pager.setCurrentItem(1, true);
      refresh();
    }
  }

  @Override
  protected void onSaveInstanceState(Bundle out) {
    out.putInt("page", pager.getCurrentItem());
    super.onSaveInstanceState(out);
  }

  @Override
  protected void onStart() {
    super.onStart();
    androidx.core.content.ContextCompat.registerReceiver(
        this,
        shopUpdates,
        new IntentFilter(Daily.UPDATED),
        androidx.core.content.ContextCompat.RECEIVER_NOT_EXPORTED);
  }

  @Override
  protected void onStop() {
    unregisterReceiver(shopUpdates);
    super.onStop();
  }

  @Override
  protected void onResume() {
    super.onResume();
    readShop();
    for (Page p : pages) if (p != null) p.filter();
    Daily.schedule(this);
    clock.post(tick);
  }

  @Override
  protected void onPause() {
    clock.removeCallbacks(tick);
    super.onPause();
  }

  @Override
  protected void onDestroy() {
    tasks.shutdownNow();
    imageTasks.shutdownNow();
    super.onDestroy();
  }

  class Page {
    final int index;
    final LinearLayout root;
    final RecyclerView grid;
    final SkinAdapter adapter;
    final TextView status, timer, empty;
    LinearLayout weapons, filtersPanel;
    Set<String> guns = new HashSet<>();
    boolean allGuns = true;
    Chip lineChip, rarityChip, sortChip;
    String line = "All lines", rarity = "All rarities", query = "", sort = "Name";
    final List<String> ids = new ArrayList<>();

    Page(int index) {
      this.index = index;
      root = column();
      root.setPadding(dp(12), 0, dp(12), 0);
      timer = text("", 16, TEXT);
      timer.setPadding(dp(4), dp(12), dp(4), dp(4));
      root.addView(timer);
      timer.setVisibility(index == 1 ? View.VISIBLE : View.GONE);
      status = text("", 12, MUTED);
      status.setPadding(dp(4), dp(4), dp(4), dp(8));
      if (index != 0) root.addView(status);
      if (index == 3) {
        empty = text("", 12, MUTED);
        grid = null;
        adapter = null;
        populateSettings(this);
        return;
      }
      if (index == 2) {
        timer.setVisibility(View.VISIBLE);
        timer.setTextSize(14);
        root.addView(
            action(
                "About these estimates",
                R.drawable.ic_tune,
                MainActivity.this::explainOdds,
                false));
      }
      if (index == 1) {
        LinearLayout controls = new LinearLayout(MainActivity.this);
        controls.addView(action("Refresh shop", R.drawable.ic_refresh, () -> refresh(true), true));
        controls.addView(
            action("Saved shop history", R.drawable.ic_sort, MainActivity.this::history, false));
        root.addView(controls);
      }
      if (index == 0) {
        filtersPanel = column();
        root.addView(filtersPanel);
        LinearLayout searchRow = new LinearLayout(MainActivity.this);
        filtersPanel.addView(searchRow);
        TextInputLayout input =
            new TextInputLayout(
                MainActivity.this, null, com.google.android.material.R.attr.textInputOutlinedStyle);
        input.setHint("Search skins");
        input.setStartIconDrawable(icon(R.drawable.ic_search));
        input.setEndIconMode(TextInputLayout.END_ICON_CLEAR_TEXT);
        TextInputEditText field = new TextInputEditText(input.getContext());
        field.setSingleLine(true);
        field.setTextSize(16);
        input.addView(field);
        input.setVisibility(View.GONE);
        searchRow.addView(
            action(
                "Search skins",
                R.drawable.ic_search,
                () -> {
                  input.setVisibility(
                      input.getVisibility() == View.VISIBLE ? View.GONE : View.VISIBLE);
                  if (input.getVisibility() == View.VISIBLE) {
                    field.requestFocus();
                    ((android.view.inputmethod.InputMethodManager)
                            getSystemService(INPUT_METHOD_SERVICE))
                        .showSoftInput(
                            field, android.view.inputmethod.InputMethodManager.SHOW_IMPLICIT);
                  } else {
                    field.setText("");
                    ((android.view.inputmethod.InputMethodManager)
                            getSystemService(INPUT_METHOD_SERVICE))
                        .hideSoftInputFromWindow(field.getWindowToken(), 0);
                  }
                },
                true),
            new LinearLayout.LayoutParams(dp(48), dp(48)));
        searchRow.addView(input, new LinearLayout.LayoutParams(0, dp(58), 1));
        field.addTextChangedListener(
            watch(
                s -> {
                  query = s;
                  filter();
                }));
        weapons = new LinearLayout(MainActivity.this);
        filtersPanel.addView(horizontal(weapons), new LinearLayout.LayoutParams(-1, dp(60)));
        weaponStrip();
        LinearLayout filters = new LinearLayout(MainActivity.this);
        lineChip = filterChip("Skinline", () -> chooseLine(this));
        rarityChip = filterChip("Rarity", () -> chooseRarity(this));
        sortChip = filterChip("Sort", () -> chooseSort(this));
        filters.addView(lineChip);
        filters.addView(rarityChip);
        filters.addView(sortChip);
        Chip pass =
            filterChip(
                Store.prefs(MainActivity.this).getBoolean("hidePass", true)
                    ? "Battle pass hidden"
                    : "Battle pass shown",
                () -> {});
        pass.setOnClickListener(
            v -> {
              boolean hide = !Store.prefs(MainActivity.this).getBoolean("hidePass", true);
              Store.prefs(MainActivity.this).edit().putBoolean("hidePass", hide).apply();
              pass.setText(hide ? "Battle pass hidden" : "Battle pass shown");
              filter();
            });
        filters.addView(pass);
        filtersPanel.addView(horizontal(filters), new LinearLayout.LayoutParams(-1, dp(48)));
        filtersPanel.addView(status);
      }
      empty =
          text(
              index == 1
                  ? "Sign in to see your daily offers."
                  : index == 2 ? "Star a skin to save it here." : "No skins match these filters.",
              16,
              MUTED);
      empty.setGravity(Gravity.CENTER);
      empty.setPadding(dp(16), dp(30), dp(16), dp(24));
      root.addView(empty);
      if (index == 1) {
        MaterialButton signin =
            action("Sign in", R.drawable.ic_switch_account, MainActivity.this::login, false);
        root.addView(signin);
        signin.setTag("signin");
      }
      grid = new RecyclerView(MainActivity.this);
      grid.setLayoutManager(new GridLayoutManager(MainActivity.this, 2));
      grid.setClipToPadding(false);
      grid.setPadding(0, dp(4), 0, dp(12));
      adapter = new SkinAdapter(this);
      grid.setAdapter(adapter);
      root.addView(grid, new LinearLayout.LayoutParams(-1, 0, 1));
      if (index == 0)
        grid.addOnScrollListener(
            new RecyclerView.OnScrollListener() {
              public void onScrolled(RecyclerView r, int dx, int dy) {
                if (dy > 8 && r.computeVerticalScrollOffset() > dp(60))
                  filtersPanel.setVisibility(View.GONE);
                else if (dy < 0) filtersPanel.setVisibility(View.VISIBLE);
              }
            });
      filter();
    }

    Chip filterChip(String label, Runnable run) {
      Chip c = new Chip(MainActivity.this);
      c.setText(label);
      c.setChipIcon(icon(R.drawable.ic_expand_more));
      c.setOnClickListener(v -> run.run());
      return c;
    }

    void weaponStrip() {
      if (weapons == null) return;
      weapons.removeAllViews();
      MaterialButton all =
          action(
              "All",
              R.drawable.ic_tune,
              () -> {
                allGuns = !allGuns;
                guns.clear();
                weaponStrip();
                filter();
              },
              false);
      weapons.addView(all);
      all.setAlpha(allGuns ? 1 : 0.45f);
      List<String> names = new ArrayList<>(catalogue.weaponIcons.keySet());
      JSONObject rates = catalogue.usage.optJSONObject("rates");
      names.sort(
          Comparator.comparingDouble((String n) -> -(rates == null ? 0 : rates.optDouble(n, 0)))
              .thenComparing(n -> n));
      for (String name : names) {
        boolean selected = allGuns || guns.contains(name);
        LinearLayout option = column();
        option.setGravity(Gravity.CENTER);
        option.setPadding(dp(8), 0, dp(8), 0);
        option.setContentDescription(name + " filter");
        ImageView image = new ImageView(MainActivity.this);
        image.setScaleType(ImageView.ScaleType.FIT_CENTER);
        image.setColorFilter(selected ? PRIMARY : TEXT);
        option.addView(image, new LinearLayout.LayoutParams(dp(56), dp(26)));
        loadImage(image, catalogue.weaponIcons.get(name));
        TextView label = text(name, 11, selected ? PRIMARY : MUTED);
        label.setGravity(Gravity.CENTER);
        option.addView(label);
        TextView percent =
            text(
                String.format(Locale.ROOT, "%.1f%%", rates == null ? 0 : rates.optDouble(name, 0)),
                10,
                MUTED);
        percent.setGravity(Gravity.CENTER);
        option.addView(percent);
        option.setAlpha(selected ? 1 : 0.5f);
        option.setOnClickListener(
            v -> {
              if (allGuns) {
                allGuns = false;
                guns.clear();
              }
              if (!guns.add(name)) guns.remove(name);
              weaponStrip();
              filter();
            });
        weapons.addView(option, new LinearLayout.LayoutParams(-2, dp(58)));
      }
    }

    void filter() {
      if (index == 3) {
        populateSettings(this);
        return;
      }
      if (adapter == null) return;
      ids.clear();
      if (index == 1) ids.addAll(Store.offers(shop));
      else
        for (var entry : catalogue.skins.entrySet()) {
          JSONObject s = entry.getValue();
          if (index == 2 && !stars.contains(entry.getKey())) continue;
          if (index == 0 && !allGuns && !guns.contains(s.optString("weapon"))) continue;
          if (index == 0
              && Store.prefs(MainActivity.this).getBoolean("hidePass", true)
              && s.optString("obtain").equals("pass")) continue;
          if (!line.equals("All lines") && !line.equals(s.optString("line"))) continue;
          if (!rarity.equals("All rarities") && !rarity.equals(catalogue.tier(s))) continue;
          if (!s.optString("displayName")
              .toLowerCase(Locale.ROOT)
              .contains(query.toLowerCase(Locale.ROOT))) continue;
          ids.add(entry.getKey());
        }
      Comparator<String> names =
          Comparator.comparing(
              id -> {
                JSONObject s = catalogue.skins.get(id);
                return s == null ? id : s.optString("displayName");
              });
      if (sort.equals("Price: low to high"))
        ids.sort(Comparator.comparingInt((String id) -> price(id)).thenComparing(names));
      else if (sort.equals("Price: high to low"))
        ids.sort(
            Comparator.comparingInt((String id) -> SkinLogic.priceSort(price(id), true))
                .thenComparing(names));
      else ids.sort(names);
      empty.setVisibility(ids.isEmpty() ? View.VISIBLE : View.GONE);
      View signin = root.findViewWithTag("signin");
      if (signin != null) signin.setVisibility(ids.isEmpty() ? View.VISIBLE : View.GONE);
      adapter.notifyDataSetChanged();
      if (index == 1 && !busy) status.setText(activeLabel());
      if (index == 0) status.setText(ids.size() + " skins · pick-rate snapshot 18 Oct 2026");
      if (index == 2) {
        int desired = 0;
        for (String id : stars) if (eligible.contains(id)) desired++;
        double p = SkinLogic.chance(eligible.size(), desired, 4);
        timer.setText(
            String.format(
                Locale.ROOT,
                "Estimated chance of any desired skin: %.2f%% / rotation\n"
                    + "Average wait: %s · %d saved rotations",
                100 * p,
                p > 0 ? String.format(Locale.ROOT, "%.0f days", Math.ceil(1 / p)) : "—",
                savedRotations));
        status.setText(
            "Based on "
                + eligible.size()
                + " catalogue-eligible, unowned skins · "
                + (Store.cached(
                            MainActivity.this,
                            "owned_" + Accounts.active(MainActivity.this) + ".json")
                        .has("synced")
                    ? "ownership synced"
                    : "ownership not yet available"));
      }
      countdown();
    }

    void countdown() {
      if (index != 1) return;
      long checked = shop.optLong("checkedAt");
      JSONObject panel = shop.optJSONObject("SkinsPanelLayout");
      long
          end =
              SkinLogic.expiresAt(
                  checked,
                  panel == null ? 0 : panel.optLong("SingleItemOffersRemainingDurationInSeconds")),
          left = SkinLogic.remaining(end, System.currentTimeMillis());
      if (checked == 0) {
        timer.setText("Your daily rotation");
        return;
      }
      if (left == 0) {
        timer.setText("Rotation expired · refresh to see today’s shop");
        return;
      }
      timer.setText(
          "Refreshes at "
              + android.text.format.DateFormat.getTimeFormat(MainActivity.this)
                  .format(new Date(end))
              + "  ·  in "
              + String.format(
                  Locale.ROOT, "%d:%02d:%02d", left / 3600, (left / 60) % 60, left % 60));
    }
  }

  interface Change {
    void run(String s);
  }

  TextWatcher watch(Change change) {
    return new TextWatcher() {
      public void beforeTextChanged(CharSequence s, int st, int count, int after) {}

      public void afterTextChanged(Editable e) {}

      public void onTextChanged(CharSequence s, int st, int before, int count) {
        change.run(s.toString());
      }
    };
  }

  HorizontalScrollView horizontal(View child) {
    HorizontalScrollView s =
        new HorizontalScrollView(this) {
          public boolean onInterceptTouchEvent(MotionEvent e) {
            if (e.getActionMasked() == MotionEvent.ACTION_DOWN)
              getParent().requestDisallowInterceptTouchEvent(true);
            return super.onInterceptTouchEvent(e);
          }
        };
    s.setHorizontalScrollBarEnabled(false);
    s.addView(child);
    return s;
  }

  class SkinAdapter extends RecyclerView.Adapter<SkinHolder> {
    final Page page;

    SkinAdapter(Page p) {
      page = p;
    }

    public int getItemCount() {
      return page.ids.size();
    }

    public SkinHolder onCreateViewHolder(ViewGroup p, int type) {
      return new SkinHolder();
    }

    public void onBindViewHolder(SkinHolder h, int pos) {
      String id = page.ids.get(pos);
      JSONObject s = catalogue.skins.get(id);
      h.card.setOnClickListener(v -> details(id));
      h.stats.setVisibility(page.index == 2 ? View.VISIBLE : View.GONE);
      if (page.index == 2) {
        double chance = eligible.contains(id) ? SkinLogic.chance(eligible.size(), 1, 4) : 0;
        h.stats.setText(
            seen.getOrDefault(id, 0)
                + " appearances in saved history\n"
                + (chance > 0
                    ? String.format(
                        Locale.ROOT,
                        "≈ %.2f%% daily · %.0f days average",
                        chance * 100,
                        Math.ceil(1 / chance))
                    : "Owned or not in the estimated daily pool"));
      }
      h.name.setText(s == null ? "New skin" : s.optString("displayName"));
      h.price.setText(priceText(id));
      h.star.setIcon(icon(stars.contains(id) ? R.drawable.ic_star : R.drawable.ic_star_border));
      h.star.setContentDescription(stars.contains(id) ? "Unstar skin" : "Star skin");
      h.star.setOnClickListener(v -> star(id));
      if (s != null) {
        int color = catalogue.color(s);
        h.card.setCardBackgroundColor(blend(SURFACE, color, page.index == 1 ? 0.40f : 0.18f));
        h.card.setStrokeColor(blend(SURFACE, color, 0.6f));
        h.tier.setText(catalogue.tier(s));
        String ti = catalogue.tierIcon(s);
        h.rarityArt.setVisibility(ti.isEmpty() ? View.GONE : View.VISIBLE);
        if (!ti.isEmpty()) loadImage(h.rarityArt, ti);
        loadImage(h.art, catalogue.primary(s));
      } else {
        h.card.setCardBackgroundColor(SURFACE);
        h.art.setImageDrawable(null);
        h.tier.setText("Unknown rarity");
        h.rarityArt.setVisibility(View.GONE);
      }
    }
  }

  class SkinHolder extends RecyclerView.ViewHolder {
    MaterialCardView card;
    TextView name, price, tier, stats;
    ImageView art, rarityArt;
    MaterialButton star;

    SkinHolder() {
      super(new MaterialCardView(MainActivity.this));
      card = (MaterialCardView) itemView;
      card.setRadius(dp(20));
      card.setStrokeWidth(dp(1));
      card.setClickable(true);
      card.setFocusable(true);
      RecyclerView.LayoutParams lp = new RecyclerView.LayoutParams(-1, -2);
      lp.setMargins(dp(4), dp(4), dp(4), dp(8));
      card.setLayoutParams(lp);
      LinearLayout body = column();
      body.setPadding(dp(12), dp(8), dp(12), dp(12));
      card.addView(body);
      LinearLayout head = new LinearLayout(MainActivity.this);
      head.setGravity(Gravity.CENTER_VERTICAL);
      rarityArt = new ImageView(MainActivity.this);
      rarityArt.setScaleType(ImageView.ScaleType.FIT_CENTER);
      head.addView(rarityArt, new LinearLayout.LayoutParams(dp(20), dp(20)));
      tier = text("", 11, MUTED);
      head.addView(tier, new LinearLayout.LayoutParams(0, -2, 1));
      star = action("Star skin", R.drawable.ic_star_border, () -> {}, true);
      head.addView(star, new LinearLayout.LayoutParams(dp(42), dp(42)));
      body.addView(head);
      art = new ImageView(MainActivity.this);
      art.setScaleType(ImageView.ScaleType.FIT_CENTER);
      body.addView(art, new LinearLayout.LayoutParams(-1, dp(92)));
      name = text("", 15, TEXT);
      name.setTypeface(null, Typeface.BOLD);
      name.setMaxLines(2);
      name.setMinHeight(dp(42));
      body.addView(name);
      price = text("", 14, TEXT);
      body.addView(price);
      stats = text("", 12, MUTED);
      stats.setPadding(0, dp(8), 0, 0);
      body.addView(stats);
    }
  }

  static int blend(int base, int accent, float amount) {
    return Color.rgb(
        (int) (Color.red(base) * (1 - amount) + Color.red(accent) * amount),
        (int) (Color.green(base) * (1 - amount) + Color.green(accent) * amount),
        (int) (Color.blue(base) * (1 - amount) + Color.blue(accent) * amount));
  }

  void chooseLine(Page p) {
    TreeSet<String> lines = new TreeSet<>();
    for (JSONObject s : catalogue.skins.values()) lines.add(s.optString("line"));
    List<String> values = new ArrayList<>();
    values.add("All lines");
    values.addAll(lines);
    BottomSheetDialog dialog = new BottomSheetDialog(this);
    LinearLayout body = column();
    body.setPadding(dp(20), dp(16), dp(20), dp(24));
    body.addView(text("Skinline", 24, TEXT));
    TextInputLayout field =
        new TextInputLayout(this, null, com.google.android.material.R.attr.textInputOutlinedStyle);
    field.setHint("Find a skinline");
    TextInputEditText edit = new TextInputEditText(field.getContext());
    edit.setSingleLine(true);
    field.addView(edit);
    body.addView(field);
    ListView list = new ListView(this);
    List<String> shown = new ArrayList<>(values);
    ArrayAdapter<String> adapter =
        new ArrayAdapter<>(this, android.R.layout.simple_list_item_1, shown);
    list.setAdapter(adapter);
    body.addView(list, new LinearLayout.LayoutParams(-1, dp(350)));
    edit.addTextChangedListener(
        watch(
            s -> {
              shown.clear();
              for (String line : values)
                if (line.toLowerCase(Locale.ROOT).contains(s.toLowerCase(Locale.ROOT)))
                  shown.add(line);
              adapter.notifyDataSetChanged();
            }));
    list.setOnItemClickListener(
        (a, v, pos, id) -> {
          p.line = shown.get(pos);
          p.lineChip.setText(p.line.equals("All lines") ? "Skinline" : p.line);
          p.filter();
          dialog.dismiss();
        });
    dialog.setContentView(body);
    dialog.show();
  }

  void chooseRarity(Page p) {
    BottomSheetDialog dialog = new BottomSheetDialog(this);
    LinearLayout body = column();
    body.setPadding(dp(20), dp(16), dp(20), dp(24));
    for (String name :
        new String[] {
          "All rarities", "Standard", "Select", "Deluxe", "Premium", "Exclusive", "Ultra"
        }) {
      MaterialButton row =
          action(
              name,
              R.drawable.ic_tune,
              () -> {
                p.rarity = name;
                p.rarityChip.setText(name.equals("All rarities") ? "Rarity" : name);
                p.filter();
                dialog.dismiss();
              },
              false);
      for (JSONObject tier : catalogue.tiers.values())
        if (tier.optString("displayName").startsWith(name)) {
          Bitmap image = catalogue.image(this, tier.optString("displayIcon"));
          if (image != null) {
            row.setIcon(new android.graphics.drawable.BitmapDrawable(getResources(), image));
            row.setIconTint(null);
          }
        }
      body.addView(row);
    }
    dialog.setContentView(body);
    dialog.show();
  }

  void chooseSort(Page p) {
    String[] options = {"Name", "Price: low to high", "Price: high to low"};
    new MaterialAlertDialogBuilder(this)
        .setTitle("Sort skins")
        .setSingleChoiceItems(
            options,
            Arrays.asList(options).indexOf(p.sort),
            (d, i) -> {
              p.sort = options[i];
              p.sortChip.setText(i == 0 ? "Sort" : i == 1 ? "Price ↑" : "Price ↓");
              p.filter();
              d.dismiss();
            })
        .setNegativeButton("Cancel", null)
        .show();
  }

  void details(String id) {
    JSONObject s = catalogue.skins.get(id);
    if (s == null) {
      new MaterialAlertDialogBuilder(this)
          .setTitle("New skin")
          .setMessage(
              "This skin was released after the bundled catalogue. Its live price is "
                  + priceText(id)
                  + ".")
          .setPositiveButton("Close", null)
          .show();
      return;
    }
    BottomSheetDialog sheet = new BottomSheetDialog(this);
    LinearLayout body = column();
    body.setPadding(dp(24), dp(16), dp(24), dp(28));
    LinearLayout head = new LinearLayout(this);
    head.addView(
        text(s.optString("displayName"), 24, TEXT), new LinearLayout.LayoutParams(0, -2, 1));
    MaterialButton favourite =
        action(
            "Star skin",
            stars.contains(id) ? R.drawable.ic_star : R.drawable.ic_star_border,
            () -> {},
            true);
    favourite.setOnClickListener(
        v -> {
          star(id);
          favourite.setIcon(
              icon(stars.contains(id) ? R.drawable.ic_star : R.drawable.ic_star_border));
        });
    head.addView(favourite, new LinearLayout.LayoutParams(dp(48), dp(48)));
    body.addView(head);
    ImageView art = new ImageView(this);
    art.setScaleType(ImageView.ScaleType.FIT_CENTER);
    body.addView(art, new LinearLayout.LayoutParams(-1, dp(185)));
    loadImage(art, catalogue.primary(s));
    body.addView(
        text(s.optString("line") + " · " + catalogue.tier(s) + " · " + priceText(id), 15, MUTED));
    if (catalogue.estimated(this, id) && !livePrices.has(id))
      body.addView(text("≈ Edition base estimate. Actual VP cost can differ.", 12, MUTED));
    LinearLayout variants = new LinearLayout(this);
    JSONArray chromas = s.optJSONArray("chromas");
    if (chromas != null)
      for (int i = 0; i < chromas.length(); i++) {
        JSONObject ch = chromas.optJSONObject(i);
        String label =
            ch.optString("displayName")
                .replace(s.optString("displayName"), "")
                .replace("(Variant ", "(")
                .trim();
        if (label.isEmpty()) label = "Default";
        String url = ch.optString("fullRender", catalogue.primary(s));
        if (url.equals("null")) url = catalogue.primary(s);
        final String image = url;
        Chip chip = new Chip(this);
        chip.setText(label);
        chip.setCheckable(true);
        chip.setChecked(i == 0);
        chip.setOnClickListener(
            v -> {
              loadImage(art, image);
              for (int j = 0; j < variants.getChildCount(); j++)
                ((Chip) variants.getChildAt(j)).setChecked(variants.getChildAt(j) == chip);
            });
        variants.addView(chip);
      }
    body.addView(horizontal(variants));
    body.addView(
        action(
            "Open wiki",
            R.drawable.ic_open_in_new,
            () -> {
              try {
                startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse(Catalogue.wiki(s))));
              } catch (ActivityNotFoundException ignored) {
              }
            },
            false));
    sheet.setContentView(body);
    sheet.show();
  }

  void settings() {
    pager.setCurrentItem(3, true);
  }

  void populateSettings(Page page) {
    page.root.removeAllViews();
    ScrollView scroll = new ScrollView(this);
    LinearLayout body = column();
    body.setPadding(dp(8), dp(16), dp(8), dp(24));
    scroll.addView(body);
    page.root.addView(scroll, new LinearLayout.LayoutParams(-1, -1));
    String active = Accounts.active(this);
    List<JSONObject> accounts = Accounts.list(this);
    if (accounts.isEmpty())
      body.addView(text("Sign in to save your daily shop history.", 16, TEXT));
    else
      for (JSONObject account : accounts) {
        String id = account.optString("id");
        boolean selected = id.equals(active);
        MaterialCardView card = new MaterialCardView(this);
        card.setCardBackgroundColor(SURFACE);
        card.setRadius(dp(18));
        card.setStrokeWidth(dp(selected ? 2 : 1));
        card.setStrokeColor(selected ? PRIMARY : 0xff332222);
        LinearLayout item = column();
        item.setPadding(dp(16), dp(12), dp(16), dp(12));
        card.addView(item);
        item.addView(text((selected ? "Signed in as " : "") + Accounts.label(account), 17, TEXT));
        item.addView(
            text(
                account.optBoolean("expired")
                    ? "Sign-in expired · reconnect required"
                    : account.optString("error").isEmpty()
                        ? (selected ? "Active account" : "Saved session · tap to switch")
                        : account.optString("error"),
                13,
                account.optBoolean("expired") ? PRIMARY : MUTED));
        if (!selected)
          item.addView(
              action(
                  "Switch to this account",
                  R.drawable.ic_switch_account,
                  () -> switchAccount(id),
                  false));
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(-1, -2);
        params.setMargins(0, 0, 0, dp(12));
        body.addView(card, params);
      }
    body.addView(
        action("Add / reconnect account", R.drawable.ic_switch_account, this::login, false));
    if (!active.isEmpty())
      body.addView(
          action(
              "Sign out of this account",
              R.drawable.ic_logout,
              () ->
                  tasks.submit(
                      () -> {
                        Store.signOut(this);
                        Daily.schedule(this);
                        ShopWidget.updateAll(this);
                        runOnUiThread(
                            () -> {
                              readShop();
                              for (Page p : pages) if (p != null) p.filter();
                            });
                      }),
              false));
    TextInputLayout regionLabel =
        new TextInputLayout(
            this,
            null,
            com.google.android.material.R.attr.textInputOutlinedExposedDropdownMenuStyle);
    regionLabel.setHint("Region for added / reconnected accounts");
    MaterialAutoCompleteTextView region =
        new MaterialAutoCompleteTextView(regionLabel.getContext());
    region.setInputType(0);
    String[] regions = {"eu", "na", "ap", "kr"};
    region.setAdapter(new ArrayAdapter<>(this, android.R.layout.simple_list_item_1, regions));
    region.setText(Store.prefs(this).getString("region", "eu"), false);
    regionLabel.addView(region);
    body.addView(regionLabel);
    region.setOnItemClickListener(
        (a, v, pos, id) -> Store.prefs(this).edit().putString("region", regions[pos]).apply());
    com.google.android.material.materialswitch.MaterialSwitch daily =
        new com.google.android.material.materialswitch.MaterialSwitch(this);
    daily.setText("Save daily shops automatically");
    daily.setChecked(Store.prefs(this).getBoolean("daily", true));
    body.addView(daily);
    daily.setOnCheckedChangeListener(
        (v, on) -> {
          Store.prefs(this).edit().putBoolean("daily", on).apply();
          Daily.schedule(this);
        });
    com.google.android.material.materialswitch.MaterialSwitch notices =
        new com.google.android.material.materialswitch.MaterialSwitch(this);
    notices.setText("Quiet daily account reminders");
    notices.setChecked(Store.prefs(this).getBoolean("notices", true));
    body.addView(notices);
    notices.setOnCheckedChangeListener(
        (v, on) -> {
          Store.prefs(this).edit().putBoolean("notices", on).apply();
          if (on
              && Build.VERSION.SDK_INT >= 33
              && checkSelfPermission(android.Manifest.permission.POST_NOTIFICATIONS)
                  != android.content.pm.PackageManager.PERMISSION_GRANTED)
            requestPermissions(new String[] {android.Manifest.permission.POST_NOTIFICATIONS}, 90);
        });
    if (Build.VERSION.SDK_INT >= 33
        && checkSelfPermission(android.Manifest.permission.POST_NOTIFICATIONS)
            != android.content.pm.PackageManager.PERMISSION_GRANTED)
      body.addView(text("Android notification permission is currently off.", 13, PRIMARY));
    body.addView(
        text(
            "Daily checks follow Riot’s rotation expiry. Android can delay background work. Expired"
                + " accounts get at most one silent reminder per day; temporary failures are"
                + " labelled separately. History starts with the rotations this app has captured.",
            13,
            MUTED));
    body.addView(
        action(
            "Weapon usage source",
            R.drawable.ic_open_in_new,
            () ->
                startActivity(
                    new Intent(Intent.ACTION_VIEW, Uri.parse(catalogue.usage.optString("source")))),
            false));
    body.addView(
        text(
            "Weapon order: overall competitive pick-rate snapshot, 18 Oct 2026. Catalogue:"
                + " valorant-api.com · prices: valorant.gs. Sessions stay encrypted on this"
                + " device.",
            12,
            MUTED));
  }

  void switchAccount(String id) {
    tasks.submit(
        () -> {
          try {
            Accounts.select(this, id);
            Daily.schedule(this);
            ShopWidget.updateAll(this);
            runOnUiThread(
                () -> {
                  readShop();
                  for (Page p : pages) if (p != null) p.filter();
                  pager.setCurrentItem(1, true);
                  refresh();
                });
          } catch (Exception e) {
            runOnUiThread(
                () ->
                    new MaterialAlertDialogBuilder(this)
                        .setMessage("This saved session is unavailable. Add the account again.")
                        .setPositiveButton("OK", null)
                        .show());
          }
        });
  }

  void explainOdds() {
    new MaterialAlertDialogBuilder(this)
        .setTitle("Estimated shop odds")
        .setMessage(
            "The model assumes four different skins drawn uniformly from the catalogue’s"
                + " daily-eligible pool each rotation. Owned skins are excluded after an ownership"
                + " sync. Limited, battle-pass and other non-daily items are excluded. The snapshot"
                + " may omit new skins or include skins Riot currently withholds, so the actual"
                + " pool and weighting can differ.\n\n"
                + "For one skin the estimate is 4 ÷ pool size. For any desired skin it is 1 −"
                + " C(pool − desired, 4) ÷ C(pool, 4). Average waiting time is 1 ÷ daily"
                + " probability. Waiting longer does not make the next draw more likely.\n\n"
                + "Appearance counts cover only saved rotations for the selected account, including"
                + " rotations imported from the previous version. Missed days cannot be recovered.")
        .setPositiveButton("Got it", null)
        .show();
  }

  void history() {
    List<JSONObject> rotations = History.list(this, Accounts.active(this));
    BottomSheetDialog dialog = new BottomSheetDialog(this);
    LinearLayout content = column();
    content.setPadding(dp(16), dp(12), dp(16), dp(20));
    content.addView(text("Saved shop history", 20, TEXT));
    content.addView(text(rotations.size() + " captured rotations · selected account", 13, MUTED));
    if (rotations.isEmpty())
      content.addView(text("Your first successful refresh starts the history.", 15, MUTED));
    ListView list = new ListView(this);
    list.setDivider(null);
    list.setAdapter(
        new BaseAdapter() {
          public int getCount() {
            return rotations.size();
          }

          public Object getItem(int i) {
            return rotations.get(i);
          }

          public long getItemId(int i) {
            return i;
          }

          public View getView(int pos, View old, ViewGroup parent) {
            JSONObject record = rotations.get(pos);
            LinearLayout row = column();
            row.setPadding(0, dp(12), 0, dp(12));
            row.addView(
                text(
                    android.text.format.DateFormat.format(
                            "EEE, d MMM yyyy · HH:mm", record.optLong("checkedAt"))
                        .toString(),
                    14,
                    TEXT));
            LinearLayout cards = new LinearLayout(MainActivity.this);
            row.addView(cards);
            for (String id : Store.offers(record)) {
              JSONObject skin = catalogue.skins.get(id);
              LinearLayout card = column();
              card.setPadding(dp(4), dp(6), dp(4), 0);
              ImageView art = new ImageView(MainActivity.this);
              art.setScaleType(ImageView.ScaleType.FIT_CENTER);
              card.addView(art, new LinearLayout.LayoutParams(-1, dp(64)));
              if (skin != null) loadImage(art, catalogue.primary(skin));
              TextView name =
                  text(skin == null ? "Unknown skin" : skin.optString("displayName"), 11, MUTED);
              name.setMaxLines(2);
              card.addView(name);
              card.setOnClickListener(v -> details(id));
              cards.addView(card, new LinearLayout.LayoutParams(0, -2, 1));
            }
            return row;
          }
        });
    content.addView(list, new LinearLayout.LayoutParams(-1, dp(430)));
    dialog.setContentView(content);
    dialog.show();
  }
}
