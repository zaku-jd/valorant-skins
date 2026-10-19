package app.quietshop;

/** Shared rotation arithmetic, kept independent of Android for host-side checks. */
final class SkinLogic {
  static long expiresAt(long checkedAt, long remainingSeconds) {
    return checkedAt + Math.max(0, remainingSeconds) * 1000L;
  }

  static long remaining(long end, long now) {
    return Math.max(0, (end - now + 999) / 1000);
  }

  static boolean current(long checked, long end, long now) {
    return checked > 0 && now < end;
  }

  static int priceSort(int price, boolean descending) {
    if (price <= 0 || price == Integer.MAX_VALUE) return Integer.MAX_VALUE;
    return descending ? -price : price;
  }

  static long rotation(long end) {
    return Math.round(end / 3600000.0);
  }

  static double chance(int pool, int desired, int slots) {
    if (pool <= 0 || desired <= 0 || slots <= 0) return 0;
    desired = Math.min(pool, desired);
    slots = Math.min(pool, slots);
    double miss = 1;
    for (int i = 0; i < slots; i++) miss *= (double) Math.max(0, pool - desired - i) / (pool - i);
    return 1 - miss;
  }

  static double expectedDays(double p) {
    return p > 0 ? 1 / p : Double.POSITIVE_INFINITY;
  }

  static long nextDelay(long due, long now) {
    return Math.max(60000L, due - now);
  }
}
