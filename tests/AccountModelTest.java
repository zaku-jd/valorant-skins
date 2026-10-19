package app.quietshop;

import org.json.*;

public class AccountModelTest {
  static void check(boolean b, String why) {
    if (!b) throw new AssertionError(why);
  }

  public static void main(String[] args) throws Exception {
    JSONObject legacy =
        new JSONObject().put("access", "legacy-token").put("cookies", "original-cookie");
    JSONObject root = AccountModel.migrate(legacy, "account-a", "eu");
    check(
        root.getJSONObject("accounts")
            .getJSONObject("account-a")
            .getString("cookies")
            .equals("original-cookie"),
        "Migration must preserve renewal cookies");
    check(!legacy.has("accounts"), "Migration must not create a cyclic container");
    AccountModel.upsert(
        root, new JSONObject().put("id", "account-b").put("access", "second-token"));
    AccountModel.upsert(
        root, new JSONObject().put("id", "account-a").put("access", "renewed-token"));
    check(root.getJSONObject("accounts").length() == 2, "Reconnecting must retain other accounts");
    check(
        root.getJSONObject("accounts")
            .getJSONObject("account-b")
            .getString("access")
            .equals("second-token"),
        "Renewal must not overwrite a different account");
    JSONObject stable = AccountModel.migrate(root, "", "na");
    check(stable.getJSONObject("accounts").length() == 2, "Migration must be idempotent");
    boolean rejected = false;
    try {
      AccountModel.upsert(root, new JSONObject().put("id", "../unsafe"));
    } catch (IllegalArgumentException e) {
      rejected = true;
    }
    check(rejected, "Unsafe IDs must be rejected before file-path construction");
    System.out.println(
        "Legacy-session migration, multiple accounts, renewal isolation and ID checks passed.");
  }
}
