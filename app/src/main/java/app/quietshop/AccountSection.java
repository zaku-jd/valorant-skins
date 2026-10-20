package app.quietshop;

import android.text.TextUtils;
import android.view.*;
import android.widget.*;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.card.MaterialCardView;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import java.util.*;
import org.json.*;

final class AccountSection {
  static String stamp(MainActivity a) {
    StringBuilder stamp = new StringBuilder(Accounts.active(a) + Accounts.main(a));
    for (JSONObject account : Accounts.list(a))
      stamp
          .append(account.optString("id"))
          .append(account.optString("name"))
          .append(account.optBoolean("expired"))
          .append(account.optString("error"))
          .append(Store.file(a, "profile_" + account.optString("id") + ".json").lastModified());
    return stamp.toString();
  }

  static void add(MainActivity a, LinearLayout body) {
    String selected = Accounts.active(a), main = Accounts.main(a);
    List<JSONObject> accounts = Accounts.list(a);
    Map<String, JSONObject> profiles = new HashMap<>();
    for (JSONObject account : accounts) {
      String id = account.optString("id");
      profiles.put(id, Store.cached(a, "profile_" + id + ".json"));
    }
    accounts.sort(
        Comparator.comparingInt(
                (JSONObject account) -> -profiles.get(account.optString("id")).optInt("level", -1))
            .thenComparing(Accounts::label, String.CASE_INSENSITIVE_ORDER));
    if (accounts.isEmpty()) {
      body.addView(a.text("Add a Riot account to view its shop.", 14, MainActivity.MUTED));
      return;
    }
    for (JSONObject account : accounts) {
      String id = account.optString("id");
      JSONObject profile = profiles.get(id);
      MaterialCardView card = new MaterialCardView(a);
      card.setCardBackgroundColor(MainActivity.SURFACE);
      card.setRadius(a.dp(18));
      card.setStrokeWidth(a.dp(id.equals(selected) ? 2 : 1));
      card.setStrokeColor(id.equals(selected) ? MainActivity.PRIMARY : 0xff332222);
      card.setContentDescription(Accounts.label(account) + ". Tap to view shop.");
      LinearLayout content = a.column();
      content.setPadding(a.dp(12), a.dp(10), a.dp(10), a.dp(2));
      card.addView(content);
      LinearLayout head = new LinearLayout(a);
      head.setGravity(Gravity.CENTER_VERTICAL);
      content.addView(head, new LinearLayout.LayoutParams(-1, a.dp(58)));
      ImageView avatar = new ImageView(a);
      avatar.setScaleType(ImageView.ScaleType.CENTER_CROP);
      MaterialCardView frame = new MaterialCardView(a);
      frame.setRadius(a.dp(10));
      frame.addView(avatar);
      head.addView(frame, new LinearLayout.LayoutParams(a.dp(48), a.dp(48)));
      JSONObject playerCard = a.catalogue.skins.get(profile.optString("card"));
      if (playerCard != null) a.loadImage(avatar, playerCard.optString("displayIcon"));
      else avatar.setImageDrawable(a.icon(R.drawable.ic_switch_account));
      LinearLayout identity = a.column();
      identity.setPadding(a.dp(9), 0, a.dp(4), 0);
      head.addView(identity, new LinearLayout.LayoutParams(0, -2, 1));
      String name = Accounts.label(account);
      int hash = name.lastIndexOf('#');
      TextView nick = a.text(hash < 0 ? name : name.substring(0, hash), 14, MainActivity.TEXT);
      nick.setSingleLine(true);
      nick.setEllipsize(TextUtils.TruncateAt.END);
      identity.addView(nick);
      if (hash >= 0) identity.addView(a.text(name.substring(hash), 11, MainActivity.MUTED));
      identity.addView(
          a.text(
              profile.optInt("level") > 0
                  ? "Level " + profile.optInt("level")
                  : "Level unavailable",
              10,
              MainActivity.MUTED));
      JSONObject ranks = profile.optJSONObject("ranks");
      rank(
          a,
          head,
          ranks == null ? null : ranks.optJSONObject("current"),
          "CURRENT",
          ranks == null ? 0 : ranks.optInt("rr"));
      rank(a, head, ranks == null ? null : ranks.optJSONObject("peak"), "PEAK", 0);
      if (account.optBoolean("expired"))
        content.addView(a.text("Reconnect to update", 10, MainActivity.PRIMARY));
      card.setOnClickListener(v -> a.switchAccount(id));
      head.setOnClickListener(v -> a.switchAccount(id));
      LinearLayout actions = new LinearLayout(a);
      content.addView(actions, new LinearLayout.LayoutParams(-1, a.dp(40)));
      button(a, actions, "Owned items", R.drawable.ic_inventory_2, () -> a.showOwned(id));
      button(
          a,
          actions,
          id.equals(main) ? "MAIN" : "Make main",
          R.drawable.ic_riot,
          () -> {
            if (id.equals(main)) return;
            a.tasks.submit(
                () -> {
                  try {
                    Accounts.setMain(a, id);
                    ShopWidget.updateAll(a);
                    a.runOnUiThread(() -> a.pages[3].filter());
                  } catch (Exception ignored) {
                  }
                });
          });
      button(
          a,
          actions,
          "Remove",
          R.drawable.ic_delete,
          () ->
              new MaterialAlertDialogBuilder(a)
                  .setTitle("Remove saved account?")
                  .setMessage(
                      Accounts.label(account)
                          + " will be removed from this app. Saved history stays on this phone.")
                  .setNegativeButton("Cancel", null)
                  .setPositiveButton(
                      "Remove",
                      (d, w) ->
                          a.tasks.submit(
                              () -> {
                                try {
                                  Accounts.remove(a, id);
                                  Daily.schedule(a);
                                  ShopWidget.updateAll(a);
                                  a.runOnUiThread(
                                      () -> {
                                        a.readShop();
                                        for (MainActivity.Page page : a.pages)
                                          if (page != null) page.filter();
                                      });
                                } catch (Exception ignored) {
                                }
                              }))
                  .show());
      LinearLayout.LayoutParams spacing = new LinearLayout.LayoutParams(-1, -2);
      spacing.setMargins(0, 0, 0, a.dp(10));
      body.addView(card, spacing);
    }
  }

  static void button(MainActivity a, LinearLayout row, String label, int icon, Runnable action) {
    MaterialButton b = a.action(label, icon, action, false);
    b.setTextSize(10);
    b.setIconSize(a.dp(15));
    b.setIconPadding(a.dp(4));
    b.setPadding(a.dp(2), 0, a.dp(2), 0);
    b.setInsetTop(0);
    b.setInsetBottom(0);
    row.addView(b, new LinearLayout.LayoutParams(0, a.dp(40), 1));
  }

  static void rank(MainActivity a, LinearLayout head, JSONObject rank, String label, int rr) {
    LinearLayout cell = a.column();
    cell.setGravity(Gravity.CENTER);
    head.addView(cell, new LinearLayout.LayoutParams(a.dp(44), -2));
    ImageView icon = new ImageView(a);
    icon.setScaleType(ImageView.ScaleType.FIT_CENTER);
    cell.addView(icon, new LinearLayout.LayoutParams(a.dp(32), a.dp(32)));
    if (rank != null) a.loadImage(icon, rank.optString("largeIcon"));
    TextView caption = a.text(label, 8, MainActivity.MUTED);
    caption.setGravity(Gravity.CENTER);
    cell.addView(caption);
    String name = rank == null ? "Unavailable" : rank.optString("tierName", "Unrated");
    String info =
        label
            + ": "
            + name
            + (label.equals("CURRENT") && rank != null && ProfileData.order(rank) > 0
                ? " · " + rr + " RR"
                : "");
    cell.setContentDescription(info);
    cell.setOnClickListener(v -> Toast.makeText(a, info, Toast.LENGTH_SHORT).show());
    cell.setTooltipText(info);
  }
}
