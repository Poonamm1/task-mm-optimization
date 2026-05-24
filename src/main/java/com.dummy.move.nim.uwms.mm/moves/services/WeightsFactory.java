package com.dummy.move.nim.uwms.mm.moves.services;

import com.dummy.move.nim.uwms.mm.moves.models.Weights;

public final class WeightsFactory {
  private WeightsFactory() {}
  // TODO: check if we want to add it or not - in future after POC
  // we will add if we have diff weight for diff moveType
  public static Weights choose(
      boolean coldStart, java.util.Set<String> allowedTypes, boolean isAtlasSite) {
    if (coldStart) return Weights.coldStartDefaults();

    boolean fppOrJump =
        allowedTypes != null
            && (allowedTypes.contains("FULLPULL") || allowedTypes.contains("JUMP"));

    if (fppOrJump && isAtlasSite) return Weights.atlasFppJumpDefaults();

    // If we want: when a user is “Replen only”, use replenDefaults().
    boolean replenOnly =
        allowedTypes != null
            && !allowedTypes.isEmpty()
            && allowedTypes.stream().allMatch(t -> t.equals("PREVREPLEM") || t.equals("REQREPLEN"));
    if (replenOnly) return Weights.replenDefaults();

    return Weights.normalDefaults();
  }
}
