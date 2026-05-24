package com.dummy.move.nim.uwms.mm.moves.services;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "optimizer.selector")
@Setter
@Getter
public class OptimizerSelectionProps {
  /** Use OR-Tools CP-SAT if true; otherwise use greedy. */
  // TODO: Add proper config for selector
  private boolean useOrtools = false;

  /** How many items to return to the API (top K). */
  private int k = 10;

  /** If an aisle's effective congestion >= threshold, apply per-aisle cap. */
  private double congestedAisleThreshold = 0.75;

  /** Max picks allowed from a "congested" aisle. */
  private int congestedAisleMax = 1;
}
