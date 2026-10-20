package app.quietshop;

import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.view.*;
import android.widget.*;
import androidx.appcompat.app.AlertDialog;
import androidx.recyclerview.widget.*;
import com.google.android.material.card.MaterialCardView;
import com.google.android.material.checkbox.MaterialCheckBox;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import java.util.*;
import org.json.*;

final class Pickers {
  static void rarity(MainActivity a, MainActivity.Page p) {
    LinearLayout body = a.column();
    body.setPadding(a.dp(12), a.dp(8), a.dp(12), a.dp(16));
    AlertDialog dialog =
        new MaterialAlertDialogBuilder(a).setTitle("Rarity").setView(body).create();
    body.addView(
        a.action(
            "All rarities",
            R.drawable.ic_grid_view,
            () -> {
              p.rarity = "All rarities";
              p.rarityChip.setText("Rarity");
              p.filter();
              dialog.dismiss();
            },
            false));
    MaterialCheckBox pass = new MaterialCheckBox(a);
    pass.setText("Hide battle-pass skins");
    pass.setTextColor(MainActivity.TEXT);
    pass.setChecked(Store.prefs(a).getBoolean("hidePass", true));
    pass.setOnCheckedChangeListener(
        (button, on) -> {
          Store.prefs(a).edit().putBoolean("hidePass", on).apply();
          p.filter();
        });
    body.addView(pass);
    GridLayout grid = new GridLayout(a);
    grid.setColumnCount(3);
    body.addView(grid);
    for (String name : new String[] {"Select", "Deluxe", "Premium", "Exclusive", "Ultra"}) {
      LinearLayout cell = a.column();
      cell.setGravity(Gravity.CENTER);
      cell.setPadding(a.dp(6), a.dp(12), a.dp(6), a.dp(12));
      ImageView icon = new ImageView(a);
      cell.addView(icon, new LinearLayout.LayoutParams(a.dp(20), a.dp(20)));
      for (JSONObject t : a.catalogue.tiers.values())
        if (t.optString("displayName").startsWith(name))
          a.loadImage(icon, t.optString("displayIcon"));
      TextView text =
          a.text(name, 12, name.equals(p.rarity) ? MainActivity.PRIMARY : MainActivity.TEXT);
      text.setPadding(0, a.dp(6), 0, 0);
      cell.addView(text);
      GridLayout.LayoutParams size = new GridLayout.LayoutParams();
      size.width = 0;
      size.columnSpec = GridLayout.spec(GridLayout.UNDEFINED, 1f);
      size.setMargins(a.dp(2), a.dp(2), a.dp(2), a.dp(2));
      grid.addView(cell, size);
      cell.setOnClickListener(
          v -> {
            p.rarity = name;
            p.rarityChip.setText(name);
            p.filter();
            dialog.dismiss();
          });
    }
    dialog.show();
    if (dialog.getWindow() != null) dialog.getWindow().setLayout(a.dp(320), -2);
  }

  static void lines(MainActivity a, MainActivity.Page p) {
    TreeSet<String> sorted = new TreeSet<>(String.CASE_INSENSITIVE_ORDER);
    for (JSONObject skin : a.catalogue.skins.values())
      if (!skin.optBoolean("accessory")) sorted.add(skin.optString("line"));
    List<String> lines = new ArrayList<>();
    lines.add("All lines");
    lines.addAll(sorted);
    DialogShell shell = new DialogShell(a, "Skinlines");
    RecyclerView list = new RecyclerView(a);
    int columns = a.getResources().getConfiguration().screenWidthDp >= 600 ? 3 : 2;
    GridLayoutManager manager = new GridLayoutManager(a, columns);
    list.setLayoutManager(manager);
    list.setClipToPadding(false);
    list.setPadding(a.dp(8), 0, a.dp(26), a.dp(20));
    shell.frame.addView(list, new FrameLayout.LayoutParams(-1, -1));
    list.setAdapter(
        new RecyclerView.Adapter<LineHolder>() {
          public int getItemCount() {
            return lines.size();
          }

          public LineHolder onCreateViewHolder(ViewGroup parent, int type) {
            return new LineHolder(a);
          }

          public void onBindViewHolder(LineHolder holder, int position) {
            String name = lines.get(position);
            holder.title.setText(name.equals("All lines") ? "All skinlines" : name);
            holder.card.setStrokeColor(name.equals(p.line) ? MainActivity.PRIMARY : 0xff332222);
            a.loadImage(holder.image, a.catalogue.themeIcons.getOrDefault(name, ""));
            holder.card.setOnClickListener(
                v -> {
                  p.line = name;
                  p.lineChip.setText(name.equals("All lines") ? "Skinline" : name);
                  p.filter();
                  shell.dialog.dismiss();
                });
          }
        });
    LinkedHashMap<String, Integer> positions = new LinkedHashMap<>();
    for (int i = 1; i < lines.size(); i++) {
      String letter = lines.get(i).substring(0, 1).toUpperCase(Locale.ROOT);
      if (!letter.matches("[A-Z]")) letter = "#";
      positions.putIfAbsent(letter, i);
    }
    List<String> letters = new ArrayList<>(positions.keySet());
    TextView rail = a.text(String.join("\n", letters), 10, MainActivity.MUTED);
    rail.setGravity(Gravity.CENTER);
    rail.setLineSpacing(0, 1.05f);
    FrameLayout.LayoutParams rp = new FrameLayout.LayoutParams(a.dp(26), -1, Gravity.RIGHT);
    shell.frame.addView(rail, rp);
    TextView bubble = a.text("", 32, MainActivity.TEXT);
    bubble.setGravity(Gravity.CENTER);
    bubble.setTypeface(null, Typeface.BOLD);
    GradientDrawable shape = new GradientDrawable();
    shape.setColor(0xee332222);
    shape.setCornerRadius(a.dp(40));
    bubble.setBackground(shape);
    bubble.setVisibility(View.GONE);
    shell.frame.addView(bubble, new FrameLayout.LayoutParams(a.dp(76), a.dp(76), Gravity.CENTER));
    final String[] last = {""};
    rail.setOnTouchListener(
        (v, event) -> {
          if (event.getActionMasked() == MotionEvent.ACTION_UP
              || event.getActionMasked() == MotionEvent.ACTION_CANCEL) {
            bubble.setVisibility(View.GONE);
            last[0] = "";
            v.performClick();
            return true;
          }
          if (letters.isEmpty()) return true;
          float lineHeight = rail.getPaint().getFontSpacing() * 1.05f,
              total = lineHeight * letters.size();
          float top = (rail.getHeight() - total) / 2;
          int i =
              Math.max(0, Math.min(letters.size() - 1, (int) ((event.getY() - top) / lineHeight)));
          String letter = letters.get(i);
          if (!letter.equals(last[0])) {
            last[0] = letter;
            manager.scrollToPositionWithOffset(positions.get(letter), 0);
            v.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK);
          }
          bubble.setText(letter);
          bubble.setVisibility(View.VISIBLE);
          return true;
        });
    shell.show();
  }

  static class LineHolder extends RecyclerView.ViewHolder {
    final MaterialCardView card;
    final ImageView image;
    final TextView title;

    LineHolder(MainActivity a) {
      super(new MaterialCardView(a));
      card = (MaterialCardView) itemView;
      card.setRadius(a.dp(16));
      card.setCardBackgroundColor(MainActivity.SURFACE);
      card.setStrokeWidth(a.dp(1));
      RecyclerView.LayoutParams size = new RecyclerView.LayoutParams(-1, a.dp(140));
      size.setMargins(a.dp(5), a.dp(5), a.dp(5), a.dp(5));
      card.setLayoutParams(size);
      LinearLayout body = a.column();
      body.setGravity(Gravity.CENTER);
      body.setPadding(a.dp(8), a.dp(8), a.dp(8), a.dp(8));
      card.addView(body);
      image = new ImageView(a);
      image.setScaleType(ImageView.ScaleType.FIT_CENTER);
      body.addView(image, new LinearLayout.LayoutParams(-1, a.dp(78)));
      title = a.text("", 13, MainActivity.TEXT);
      title.setGravity(Gravity.CENTER);
      title.setMaxLines(2);
      body.addView(title);
    }
  }

  static class DialogShell {
    final AlertDialog dialog;
    final FrameLayout frame;

    DialogShell(MainActivity a, String title) {
      LinearLayout body = a.column();
      body.setBackgroundColor(MainActivity.BG);
      LinearLayout head = new LinearLayout(a);
      head.setGravity(Gravity.CENTER_VERTICAL);
      dialog = new MaterialAlertDialogBuilder(a).setView(body).create();
      head.addView(
          a.action("Close", R.drawable.ic_close, dialog::dismiss, true),
          new LinearLayout.LayoutParams(a.dp(48), a.dp(48)));
      head.addView(a.text(title, 20, MainActivity.TEXT));
      body.addView(head);
      frame = new FrameLayout(a);
      body.addView(frame, new LinearLayout.LayoutParams(-1, 0, 1));
    }

    void show() {
      dialog.show();
      if (dialog.getWindow() != null) {
        dialog.getWindow().setBackgroundDrawableResource(android.R.color.black);
        dialog.getWindow().setLayout(-1, -1);
      }
    }
  }
}
