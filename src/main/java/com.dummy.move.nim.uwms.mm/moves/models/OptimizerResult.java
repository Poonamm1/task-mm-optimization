package com.dummy.move.nim.uwms.mm.moves.models;

import static com.fasterxml.jackson.annotation.JsonInclude.Include.NON_NULL;

import com.fasterxml.jackson.annotation.JsonInclude;

public final class OptimizerResult {
  public java.util.List<Integer> orderedMoveIds;
  public java.util.Map<Integer, Double> scores;
  public java.util.Map<Integer, java.util.Map<String, Double>> scoreBreakdown;

  // NEW (optional, ignored by older readers)
  @JsonInclude(NON_NULL)
  public java.util.Map<String, Object> meta;
}
