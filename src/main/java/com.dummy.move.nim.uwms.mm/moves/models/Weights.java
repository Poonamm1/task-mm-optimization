package com.dummy.move.nim.uwms.mm.moves.models;

// config/Weights.java
public final class Weights {
  public double wPriority;
  public double wAge;
  public double wType;
  public double wCongestion;
  public double wDistance;
  public Double sameWarehouseAreaBonus;

  // feature toggles
  public boolean enforceMinPriorityInBatch = true;

  /* -------- Profiles -------- */

  /** lastScannedLocation == null */
  public static Weights coldStartDefaults() {
    Weights w = new Weights();
    w.wPriority = 2.4; // strongest anchor
    w.wAge = 1.0; // lean on “older first”
    w.wType = 0.5; // slight bias to assigned types
    w.wCongestion = 0.7; // keep out of jams a bit
    w.wDistance = 0.0; // ignore distance on first hit
    w.sameWarehouseAreaBonus = 0.05; // tiny tie‑breaker at most
    w.enforceMinPriorityInBatch = true;
    return w;
  }

  /** normal runs (user has a last scan) */
  public static Weights normalDefaults() {
    Weights w = new Weights();
    w.wPriority = 1.9; // still the anchor
    w.wAge = 0.6; // moderate
    w.wType = 0.3; // small bias
    w.wCongestion = 0.7; // consistent penalty
    w.wDistance = 1.2; // distance matters again
    w.sameWarehouseAreaBonus = 0.10; // small nudge for same zone/area
    w.enforceMinPriorityInBatch = true;
    return w;
  }

  /** FPP & Jump at Atlas sites: activity recency > distance */
  // TODO: check if we should consider replen activity recency as well, we are passing open activity
  // for replenishment
  public static Weights atlasFppJumpDefaults() {
    Weights w = new Weights();
    w.wPriority = 1.7;
    w.wAge = 1.2; // give activity recency more say
    w.wType = 0.3;
    w.wCongestion = 0.7;
    w.wDistance = 0.6; // distance is secondary to recency
    w.sameWarehouseAreaBonus = 0.05;
    w.enforceMinPriorityInBatch = true;
    return w;
  }

  /** Replenishment: walk cost matters; steady congestion penalty */
  public static Weights replenDefaults() {
    Weights w = new Weights();
    w.wPriority = 1.9;
    w.wAge = 0.5;
    w.wType = 0.4;
    w.wCongestion = 0.9; // avoid clogged aisles
    w.wDistance = 1.0; // distance important but not overwhelming
    w.sameWarehouseAreaBonus = 0.10;
    w.enforceMinPriorityInBatch = true;
    return w;
  }
}
