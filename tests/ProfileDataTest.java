package app.quietshop;

import org.json.*;

public class ProfileDataTest {
  public static void main(String[] args) throws Exception {
    JSONObject old =
        new JSONObject()
            .put("CompetitiveTier", 21)
            .put("RankedRating", 54)
            .put("WinsByTier", new JSONObject().put("22", 2));
    JSONObject now = new JSONObject().put("CompetitiveTier", 20).put("RankedRating", 10);
    JSONObject seasons = new JSONObject().put("old", old).put("current", now);
    JSONObject mmr =
        new JSONObject()
            .put(
                "QueueSkills",
                new JSONObject()
                    .put("competitive", new JSONObject().put("SeasonalInfoBySeasonID", seasons)));
    ProfileData.Ranks lookup =
        (season, tier) -> {
          try {
            return new JSONObject()
                .put(
                    "tierName",
                    tier == 0
                        ? "UNRANKED"
                        : season.equals("old") ? "IMMORTAL " + (tier - 20) : "DIAMOND 3");
          } catch (Exception e) {
            throw new RuntimeException(e);
          }
        };
    JSONObject result = ProfileData.ranks(mmr, "current", lookup);
    if (!result.getJSONObject("peak").getString("tierName").equals("IMMORTAL 2"))
      throw new AssertionError("Peak includes won tiers");
    if (result.getInt("rr") != 10) throw new AssertionError("Current rating");
    result = ProfileData.ranks(mmr, "new-season", lookup);
    if (!result.getJSONObject("current").getString("tierName").equals("UNRANKED"))
      throw new AssertionError("Do not call previous act rank current");
    if (ProfileData.order(new JSONObject().put("tierName", "ASCENDANT 3"))
        >= ProfileData.order(new JSONObject().put("tierName", "IMMORTAL 1")))
      throw new AssertionError("Cross-era rank ordering");
    System.out.println(
        "Current-season rank, historical won-tier peak and cross-era ordering passed.");
  }
}
