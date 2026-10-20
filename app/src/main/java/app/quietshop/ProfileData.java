package app.quietshop;

import java.util.*;
import org.json.*;

final class ProfileData {
  interface Ranks {
    JSONObject get(String season, int tier);
  }

  static int order(JSONObject rank) {
    String name = rank.optString("tierName");
    String[] divisions = {
      "IRON", "BRONZE", "SILVER", "GOLD", "PLATINUM", "DIAMOND", "ASCENDANT", "IMMORTAL", "RADIANT"
    };
    for (int i = 0; i < divisions.length; i++)
      if (name.startsWith(divisions[i])) {
        int level =
            name.matches(".*[123]$") ? Character.digit(name.charAt(name.length() - 1), 10) : 1;
        return (i + 1) * 3 + level;
      }
    return 0;
  }

  static JSONObject ranks(JSONObject mmr, String current, Ranks lookup) throws JSONException {
    JSONObject skills = mmr.optJSONObject("QueueSkills"),
        comp = skills == null ? null : skills.optJSONObject("competitive"),
        seasons = comp == null ? null : comp.optJSONObject("SeasonalInfoBySeasonID");
    JSONObject now = seasons == null ? null : seasons.optJSONObject(current),
        peak = lookup.get(current, 0);
    int score = 0, rr = 0;
    if (seasons != null)
      for (Iterator<String> it = seasons.keys(); it.hasNext(); ) {
        String id = it.next();
        JSONObject season = seasons.optJSONObject(id);
        if (season == null) continue;
        Set<Integer> tiers = new HashSet<>();
        tiers.add(season.optInt("CompetitiveTier"));
        JSONObject wins = season.optJSONObject("WinsByTier");
        if (wins != null)
          for (Iterator<String> keys = wins.keys(); keys.hasNext(); ) {
            String key = keys.next();
            try {
              if (wins.optInt(key) > 0) tiers.add(Integer.parseInt(key));
            } catch (NumberFormatException ignored) {
            }
          }
        for (int t : tiers) {
          JSONObject r = lookup.get(id, t);
          int value = order(r),
              rating = t == season.optInt("CompetitiveTier") ? season.optInt("RankedRating") : 0;
          if (value > score || (value == score && rating > rr)) {
            peak = r;
            score = value;
            rr = rating;
          }
        }
      }
    return new JSONObject()
        .put("current", lookup.get(current, now == null ? 0 : now.optInt("CompetitiveTier")))
        .put("rr", now == null ? 0 : now.optInt("RankedRating"))
        .put("peak", peak)
        .put("peakRR", rr);
  }
}
