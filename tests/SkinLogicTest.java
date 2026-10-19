package app.quietshop;

import java.util.*;

public class SkinLogicTest {
  static void check(boolean value, String message) {
    if (!value) throw new AssertionError(message);
  }

  public static void main(String[] args) {
    long checked = 1735689600000L, end = SkinLogic.expiresAt(checked, 86400);
    check(end == checked + 86400000L, "Rotation must expire 24 hours after sample");
    check(SkinLogic.remaining(end, end - 1001) == 2, "Countdown rounds partial seconds up");
    check(SkinLogic.remaining(end, end + 10000) == 0, "Expired countdown cannot be negative");
    check(!SkinLogic.current(checked, end, end), "At the refresh boundary cached items are stale");
    check(!SkinLogic.current(0, 100000, 100), "An uninitialized shop cannot be current");
    List<Integer> prices = new ArrayList<>(List.of(875, 0, 2475, 1775));
    prices.sort(Comparator.comparingInt(p -> SkinLogic.priceSort(p, true)));
    check(
        prices.equals(List.of(2475, 1775, 875, 0)),
        "Unknown prices must stay last when descending");
    prices.sort(Comparator.comparingInt(p -> SkinLogic.priceSort(p, false)));
    check(
        prices.equals(List.of(875, 1775, 2475, 0)), "Unknown prices must stay last when ascending");
    check(
        Math.abs(SkinLogic.chance(100, 1, 4) - 0.04) < 1e-10,
        "A particular skin is four out of the pool");
    check(
        Math.abs(SkinLogic.chance(5, 2, 4) - 1) < 1e-10,
        "Four draws from five must include one of two desired skins");
    check(
        SkinLogic.chance(100, 0, 4) == 0 && SkinLogic.chance(0, 1, 4) == 0,
        "Empty selections/pools must not create bogus odds");
    check(
        Math.abs(SkinLogic.expectedDays(0.04) - 25) < 1e-10,
        "Geometric mean wait must use reciprocal probability");
    check(
        SkinLogic.rotation(end - 800) == SkinLogic.rotation(end + 800),
        "Network-second jitter must not duplicate a rotation");
    java.time.ZoneId zone = java.time.ZoneId.of("Europe/Kyiv");
    java.time.ZonedDateTime local = java.time.ZonedDateTime.of(2026, 10, 25, 2, 0, 0, 0, zone);
    long utcDue = local.toInstant().toEpochMilli() + 86400000;
    check(
        SkinLogic.nextDelay(utcDue, local.toInstant().toEpochMilli()) == 86400000,
        "Scheduling must stay on UTC expiry through autumn DST");
    check(
        SkinLogic.nextDelay(100, 1000) == 60000,
        "Overdue work must be bounded and avoid a tight loop");
    System.out.println(
        "Rotation expiry/deduplication, DST scheduling, probabilities, waiting times and"
            + " price-order checks passed.");
  }
}
