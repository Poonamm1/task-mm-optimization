package com.dummy.move.nim.uwms.mm.moves.interfaces;

import java.util.List;
import java.util.Map;

public interface IOrToolSelector {

  /**
   * Return a subset (size <= K) ordered by final score descending.
   *
   * @param candidateIds move IDs
   * @param scores map moveId -> score
   * @param aisleOf map moveId -> aisleId (may be null)
   * @param congestionOf map aisleId -> [0..1] congestion
   * @param k cap on #picks
   */
  public List<String> selectTopK(
      List<String> candidateIds,
      Map<String, Double> scores,
      Map<String, String> aisleOf,
      Map<String, Double> congestionOf,
      int k,
      double congestedAisleThreshold,
      int congestedAisleMax,
      double maxTimeSeconds);
}
