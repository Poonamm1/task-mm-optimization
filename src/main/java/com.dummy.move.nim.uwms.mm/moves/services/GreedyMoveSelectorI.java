package com.dummy.move.nim.uwms.mm.moves.services;

import com.dummy.move.nim.uwms.mm.moves.interfaces.IMoveSelector;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class GreedyMoveSelectorI implements IMoveSelector {

  private final double congestedAisleThreshold;
  private final int congestedAisleMax;

  public GreedyMoveSelectorI(double congestedAisleThreshold, int congestedAisleMax) {
    this.congestedAisleThreshold = congestedAisleThreshold;
    this.congestedAisleMax = congestedAisleMax;
  }

  @Override
  public List<String> selectTopK(
      List<String> candidateIds,
      Map<String, Double> scores,
      Map<String, String> aisleOf,
      Map<String, Double> congestionOf,
      int k) {

    // sort by score desc first
    List<String> sorted = new ArrayList<>(candidateIds);
    sorted.sort((a, b) -> Double.compare(scores.getOrDefault(b, 0.0), scores.getOrDefault(a, 0.0)));

    Map<String, Integer> countByAisle = new HashMap<>();
    List<String> picked = new ArrayList<>(Math.min(k, sorted.size()));

    for (String id : sorted) {
      if (picked.size() >= k) break;

      String aisle = aisleOf.get(id);
      double cong = (aisle == null) ? 0.0 : clamp01(congestionOf.getOrDefault(aisle, 0.0));

      if (aisle != null && cong >= congestedAisleThreshold) {
        int used = countByAisle.getOrDefault(aisle, 0);
        if (used >= congestedAisleMax) {
          continue; // skip to enforce cap
        }
        countByAisle.put(aisle, used + 1);
        picked.add(id);
      } else {
        // uncongested aisle or unknown -> no per-aisle cap
        picked.add(id);
      }
    }

    // ensure deterministic final order by score
    picked.sort((a, b) -> Double.compare(scores.getOrDefault(b, 0.0), scores.getOrDefault(a, 0.0)));

    return picked;
  }

  private static double clamp01(double v) {
    if (v < 0.0) return 0.0;
    if (v > 1.0) return 1.0;
    return v;
  }
}
