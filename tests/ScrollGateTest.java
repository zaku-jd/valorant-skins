package app.quietshop;

public class ScrollGateTest {
  public static void main(String[] args) {
    ScrollGate gate = new ScrollGate();
    gate.move(80);
    boolean hidden = gate.settle(false, false, 48);
    if (!hidden) throw new AssertionError("Hide intentional down scroll");
    gate.move(-8);
    if (!gate.settle(hidden, false, 48)) throw new AssertionError("Ignore small upward nudge");
    gate.move(-20);
    gate.move(12);
    if (!gate.settle(hidden, false, 48)) throw new AssertionError("Direction change resets travel");
    gate.move(-60);
    if (gate.settle(hidden, false, 48)) throw new AssertionError("Reveal intentional up scroll");
    gate.move(100);
    if (gate.settle(false, true, 48)) throw new AssertionError("Always reveal at top");
    if (gate.settle(false, false, 48))
      throw new AssertionError("No movement after layout must not toggle");
    System.out.println(
        "Scroll threshold, small reversal, direction reset and idle-layout checks passed.");
  }
}
