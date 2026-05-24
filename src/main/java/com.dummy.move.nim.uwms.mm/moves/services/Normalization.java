package com.dummy.move.nim.uwms.mm.moves.services;

public final class Normalization {
  private Normalization() {}

  public static double normalizePriority(Integer p) {
    if (p == null) return 0.5;
    // simple bounded mapping for POC: 5 is best → 1.0, 30 is worst → ~0.0
    int clamped = Math.max(5, Math.min(30, p));
    double span = 30 - 5;
    return 1.0 - ((clamped - 5) / span);
  }

  // maps age to [0..1]; null -> neutral 0.5

  /**
   * @param observedStartSecOrNull
   * @param nowSec
   * @return ageTerm(observedStart, now) age = now - observedStart. Uses formula: age / (age + T)
   *     with T ~ 3600s (1 hour). This curve → saturates near 1 for very old moves, ~0.5 for
   *     neutral, ~0 for very fresh. Purpose: “older moves deserve to bubble up,” but with
   *     diminishing returns
   */
  public static double ageTerm(Long observedStartSecOrNull, long nowSec) {
    if (observedStartSecOrNull == null) return 0.5;
    long age = Math.max(0L, nowSec - observedStartSecOrNull.longValue());
    // simple saturating curve: age/(age+T); choose T ~ 1hr = 3600s (tune later)
    double term = age / (age + 3600.0);
    return clamp01(term);
  }

  private static double clamp01(double v) {
    return v < 0 ? 0 : v > 1 ? 1 : v;
  }
}
