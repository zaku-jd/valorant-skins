package app.quietshop;

/** Accumulates intentional scrolling; layout changes are committed only once scrolling is idle. */
final class ScrollGate {
  private int travel;

  void move(int dy) {
    if (dy == 0) return;
    if (Integer.signum(dy) != Integer.signum(travel)) travel = 0;
    travel += dy;
  }

  boolean settle(boolean hidden, boolean atTop, int threshold) {
    boolean next =
        atTop ? false : travel >= threshold ? true : travel <= -threshold ? false : hidden;
    travel = 0;
    return next;
  }
}
