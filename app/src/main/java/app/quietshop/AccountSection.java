package app.quietshop;

import android.view.*;
import android.widget.*;
import com.google.android.material.card.MaterialCardView;
import java.util.*;
import org.json.*;

final class AccountSection {
  static void add(MainActivity a, LinearLayout body) {
    body.addView(a.text("Accounts", 20, MainActivity.TEXT));
    String selected = Accounts.active(a), main = Accounts.main(a);
    if (Accounts.list(a).isEmpty()) {
      body.addView(a.text("Add a Riot account to view its shop.", 14, MainActivity.MUTED));
      return;
    }
    for (JSONObject account : Accounts.list(a)) {
      String id = account.optString("id");
      JSONObject profile = Store.cached(a, "profile_" + id + ".json");
      MaterialCardView card = new MaterialCardView(a);
      card.setCardBackgroundColor(MainActivity.SURFACE);
      card.setRadius(a.dp(18));
      card.setStrokeWidth(a.dp(id.equals(selected) ? 2 : 1));
      card.setStrokeColor(id.equals(selected) ? MainActivity.PRIMARY : 0xff332222);
      LinearLayout content = a.column();
      content.setPadding(a.dp(14), a.dp(12), a.dp(14), a.dp(10));
      card.addView(content);
      LinearLayout head = new LinearLayout(a);
      head.setGravity(Gravity.CENTER_VERTICAL);
      content.addView(head);
      ImageView avatar = new ImageView(a);
      avatar.setScaleType(ImageView.ScaleType.CENTER_CROP);
      MaterialCardView frame = new MaterialCardView(a);
      frame.setRadius(a.dp(12));
      frame.setCardBackgroundColor(0xff221c1c);
      frame.addView(avatar);
      head.addView(frame, new LinearLayout.LayoutParams(a.dp(56), a.dp(56)));
      JSONObject playerCard = a.catalogue.skins.get(profile.optString("card"));
      if (playerCard != null) a.loadImage(avatar, a.catalogue.primary(playerCard));
      else avatar.setImageDrawable(a.icon(R.drawable.ic_switch_account));
      LinearLayout identity = a.column();
      identity.setPadding(a.dp(12), 0, a.dp(6), 0);
      head.addView(identity, new LinearLayout.LayoutParams(0, -2, 1));
      String label = Accounts.label(account);
      int hash = label.lastIndexOf('#');
      identity.addView(a.text(hash < 0 ? label : label.substring(0, hash), 17, MainActivity.TEXT));
      if (hash >= 0) identity.addView(a.text(label.substring(hash), 13, MainActivity.MUTED));
      identity.addView(
          a.text(
              profile.optInt("level") > 0
                  ? "Level " + profile.optInt("level")
                  : "Level unavailable",
              12,
              MainActivity.MUTED));
      LinearLayout badge = a.column();
      badge.setGravity(Gravity.CENTER);
      head.addView(badge, new LinearLayout.LayoutParams(a.dp(48), -2));
      ImageView fist = new ImageView(a);
      fist.setImageResource(R.drawable.ic_riot);
      fist.setColorFilter(MainActivity.PRIMARY);
      badge.addView(fist, new LinearLayout.LayoutParams(a.dp(27), a.dp(27)));
      TextView mainText = a.text("MAIN", 10, MainActivity.PRIMARY);
      badge.addView(mainText);
      badge.setVisibility(id.equals(main) ? View.VISIBLE : View.GONE);
      JSONObject ranks = profile.optJSONObject("ranks");
      LinearLayout ratings = new LinearLayout(a);
      ratings.setPadding(0, a.dp(12), 0, a.dp(6));
      content.addView(ratings);
      rank(
          a,
          ratings,
          ranks == null ? null : ranks.optJSONObject("current"),
          "CURRENT",
          ranks == null ? 0 : ranks.optInt("rr"));
      rank(
          a,
          ratings,
          ranks == null ? null : ranks.optJSONObject("peak"),
          "PEAK",
          ranks == null ? 0 : ranks.optInt("peakRR"));
      TextView state =
          a.text(
              account.optBoolean("expired")
                  ? "Reconnect to update this account"
                  : id.equals(selected) ? "Viewing this account" : "Tap to view shop",
              12,
              account.optBoolean("expired") ? MainActivity.PRIMARY : MainActivity.MUTED);
      content.addView(state);
      card.setOnClickListener(v -> a.switchAccount(id));
      head.setOnClickListener(v -> a.switchAccount(id));
      LinearLayout actions = new LinearLayout(a);
      content.addView(actions);
      actions.addView(
          a.action("Owned items", R.drawable.ic_inventory_2, () -> a.showOwned(id), false),
          new LinearLayout.LayoutParams(0, a.dp(44), 1));
      if (!id.equals(main))
        actions.addView(
            a.action(
                "Make main",
                R.drawable.ic_riot,
                () -> {
                  try {
                    Accounts.setMain(a, id);
                    ShopWidget.updateAll(a);
                    a.pages[3].filter();
                  } catch (Exception ignored) {
                  }
                },
                false),
            new LinearLayout.LayoutParams(0, a.dp(44), 1));
      LinearLayout.LayoutParams space = new LinearLayout.LayoutParams(-1, -2);
      space.setMargins(0, a.dp(12), 0, 0);
      body.addView(card, space);
    }
  }

  static void rank(MainActivity a, LinearLayout row, JSONObject rank, String label, int rr) {
    LinearLayout cell = a.column();
    cell.setGravity(Gravity.CENTER);
    row.addView(cell, new LinearLayout.LayoutParams(0, -2, 1));
    ImageView image = new ImageView(a);
    image.setScaleType(ImageView.ScaleType.FIT_CENTER);
    cell.addView(image, new LinearLayout.LayoutParams(a.dp(44), a.dp(44)));
    if (rank != null) a.loadImage(image, rank.optString("largeIcon"));
    String name = rank == null ? "Unavailable" : rank.optString("tierName", "Unrated");
    cell.addView(a.text(label, 10, MainActivity.MUTED));
    TextView text =
        a.text(
            name
                + (label.equals("CURRENT") && rank != null && ProfileData.order(rank) > 0
                    ? " · " + rr + " RR"
                    : ""),
            12,
            MainActivity.TEXT);
    text.setGravity(Gravity.CENTER);
    cell.addView(text);
  }
}
