package com.dummy.move.nim.uwms.mm.moves.services;

import com.google.ortools.Loader;
import com.google.ortools.sat.CpModel;
import com.google.ortools.sat.CpSolver;
import com.google.ortools.sat.CpSolverStatus;
import com.google.ortools.sat.IntVar;
import com.google.ortools.sat.LinearExpr;
import com.dummy.move.nim.uwms.mm.moves.interfaces.IMoveSelector;
import java.util.*;

/**
 * OR-Tools subset selector (CP-SAT) for ortools-java:9.14.6206.
 *
 * <p>Features: - Objective: maximize sum(score_i * x_i), where x_i ∈ {0,1} - Hard constraints: *
 * sum(x_i) <= K * For any aisle with congestion >= threshold: sum(x_i for i in aisle) <=
 * congestedAisleMax - Double scores are dynamically scaled to long objective coefficients to
 * preserve precision. - Deterministic output ordered by descending score. - Safe fallbacks if
 * native libs fail or solver can't find a solution quickly.
 */
public class OrtoolsMoveSelectorI implements IMoveSelector {

  private final double congestedAisleThreshold;
  private final int congestedAisleMax;

  public OrtoolsMoveSelectorI(double congestedAisleThreshold, int congestedAisleMax) {
    this.congestedAisleThreshold = congestedAisleThreshold;
    this.congestedAisleMax = congestedAisleMax;

    // Initialize OR-Tools native libs
    Loader.loadNativeLibraries();
  }

  /** Call this once early in app boot; otherwise first call will trigger native load. */
  static {
    try {
      Loader.loadNativeLibraries();
    } catch (UnsatisfiedLinkError e) {
      // We'll fallback to greedy in selectTopK if needed, so don't crash class load.
      System.err.println("[OrtoolsMoveSelector] Failed to load OR-Tools natives: " + e);
    }
  }

  @Override
  public List<String> selectTopK(
      List<String> ids,
      Map<String, Double> scores,
      Map<String, String> aisleOf,
      Map<String, Double> congestionOf,
      int k) {

    // delegate to the richer overload with configured thresholds
    return selectTopK(
        ids,
        scores,
        aisleOf,
        congestionOf,
        k,
        congestedAisleThreshold,
        congestedAisleMax,
        0.05 /* default maxTimeSeconds, TODO: tweak as per need */);
  }

  /**
   * Select a subset of candidates using CP-SAT with constraints.
   *
   * @param candidateIds move IDs to consider
   * @param scores map moveId -> score (double)
   * @param aisleOf map moveId -> aisleId (nullable entries allowed)
   * @param congestionOf map aisleId -> congestion in [0..1] (1.0 = fully congested)
   * @param k number of items to select (global cap)
   * @param congestedAisleThreshold aisles with congestion >= threshold are "congested"
   * @param congestedAisleMax max items allowed from a single congested aisle
   * @param maxTimeSeconds solver time limit (e.g., 0.05 for 50ms)
   * @return ordered list (by score desc) of selected move IDs; falls back to greedy on failure
   */
  @Override
  public List<String> selectTopK(
      List<String> candidateIds,
      Map<String, Double> scores,
      Map<String, String> aisleOf,
      Map<String, Double> congestionOf,
      int k,
      double congestedAisleThreshold,
      int congestedAisleMax,
      double maxTimeSeconds) {

    // Basic guards
    if (candidateIds == null || candidateIds.isEmpty() || k <= 0) {
      return Collections.emptyList();
    }

    // If OR-Tools natives didn't load, fallback to greedy
    if (!nativesLoaded()) {
      return greedy(
          candidateIds,
          scores,
          k,
          congestedAisleThreshold,
          congestedAisleMax,
          aisleOf,
          congestionOf);
    }

    try {
      // --- Build CP-SAT model
      CpModel model = new CpModel();

      // Decision vars x_i ∈ {0,1}
      Map<String, IntVar> x = new HashMap<>();
      for (String id : candidateIds) {
        x.put(id, model.newBoolVar("x_" + id));
      }

      // Global cap: Σ x_i <= K
      model.addLessOrEqual(LinearExpr.sum(x.values().toArray(new IntVar[0])), k);

      // Per-aisle caps for "congested" aisles
      Map<String, List<IntVar>> byAisle = new HashMap<>();
      for (String id : candidateIds) {
        String aisle = aisleOf.get(id);
        if (aisle != null) {
          byAisle.computeIfAbsent(aisle, _k -> new ArrayList<>()).add(x.get(id));
        }
      }
      for (Map.Entry<String, List<IntVar>> e : byAisle.entrySet()) {
        double cong = clamp01(congestionOf.getOrDefault(e.getKey(), 0.0));
        if (cong >= congestedAisleThreshold) {
          model.addLessOrEqual(
              LinearExpr.sum(e.getValue().toArray(new IntVar[0])), congestedAisleMax);
        }
      }

      // Objective: maximize Σ (scaledScore_i * x_i)
      // Scale doubles -> longs safely to preserve relative ordering and avoid overflow.
      Map<String, Long> weights =
          scaleScores(scores, candidateIds, 1_000_000L); // target max abs weight ~1e6
      List<LinearExpr> terms = new ArrayList<>();
      for (String id : candidateIds) {
        long w = weights.getOrDefault(id, 0L);
        if (w != 0L) {
          terms.add(LinearExpr.term(x.get(id), w));
        }
      }

      if (terms.isEmpty()) {
        // All zeros? nothing to optimize -> greedy fallback
        return greedy(
            candidateIds,
            scores,
            k,
            congestedAisleThreshold,
            congestedAisleMax,
            aisleOf,
            congestionOf);
      }

      model.maximize(LinearExpr.sum(terms.toArray(new LinearExpr[0])));

      // Solve with small time limit
      CpSolver solver = new CpSolver();
      solver.getParameters().setMaxTimeInSeconds(maxTimeSeconds <= 0.0 ? 0.05 : maxTimeSeconds);

      CpSolverStatus status = solver.solve(model);
      List<String> picked = new ArrayList<>();

      if (status == CpSolverStatus.OPTIMAL || status == CpSolverStatus.FEASIBLE) {
        for (String id : candidateIds) {
          // Bool vars are IntVar in Java API; read as 0/1
          if (solver.value(x.get(id)) == 1L) {
            picked.add(id);
          }
        }
      } else {
        return greedy(
            candidateIds,
            scores,
            k,
            congestedAisleThreshold,
            congestedAisleMax,
            aisleOf,
            congestionOf);
      }

      // Deterministic order by raw score desc
      picked.sort(
          (a, b) -> Double.compare(scores.getOrDefault(b, 0.0), scores.getOrDefault(a, 0.0)));

      // Safety: cap to K
      if (picked.size() > k) picked = picked.subList(0, k);
      return picked;

    } catch (Throwable t) {
      // Any solver/native error -> greedy fallback
      System.err.println("[OrtoolsMoveSelector] Falling back to greedy due to error: " + t);
      return greedy(
          candidateIds,
          scores,
          k,
          congestedAisleThreshold,
          congestedAisleMax,
          aisleOf,
          congestionOf);
    }
  }

  // --------- Helpers ---------

  /** Dynamic score scaling: doubles -> longs with bounded max abs coefficient. */
  private static Map<String, Long> scaleScores(
      Map<String, Double> scores, List<String> ids, long targetMaxAbsWeight) {

    double maxAbs = 0.0;
    for (String id : ids) {
      double s = Math.abs(scores.getOrDefault(id, 0.0));
      if (s > maxAbs) maxAbs = s;
    }
    if (maxAbs <= 0.0) {
      // everything is zero
      Map<String, Long> z = new HashMap<>();
      for (String id : ids) z.put(id, 0L);
      return z;
    }

    // scale = target / maxAbs, but cap to avoid overflow on long
    double scale = (double) targetMaxAbsWeight / maxAbs;
    // guard against NaN/inf
    if (!Double.isFinite(scale) || scale <= 0.0) scale = 1_000.0;

    Map<String, Long> out = new HashMap<>(ids.size());
    for (String id : ids) {
      double s = scores.getOrDefault(id, 0.0);
      long w = Math.round(s * scale);
      out.put(id, w);
    }
    return out;
  }

  /** Greedy fallback with per-aisle cap for congested aisles. */
  private static List<String> greedy(
      List<String> ids,
      Map<String, Double> scores,
      int k,
      double congestedAisleThreshold,
      int congestedAisleMax,
      Map<String, String> aisleOf,
      Map<String, Double> congestionOf) {

    List<String> sorted = new ArrayList<>(ids);
    sorted.sort((a, b) -> Double.compare(scores.getOrDefault(b, 0.0), scores.getOrDefault(a, 0.0)));

    Map<String, Integer> cntByAisle = new HashMap<>();
    List<String> picked = new ArrayList<>(Math.min(k, sorted.size()));

    for (String id : sorted) {
      if (picked.size() >= k) break;

      String aisle = aisleOf.get(id);
      double cong = (aisle == null) ? 0.0 : clamp01(congestionOf.getOrDefault(aisle, 0.0));

      if (aisle != null && cong >= congestedAisleThreshold) {
        int used = cntByAisle.getOrDefault(aisle, 0);
        if (used >= congestedAisleMax) continue; // enforce cap
        cntByAisle.put(aisle, used + 1);
        picked.add(id);
      } else {
        picked.add(id);
      }
    }

    // Final deterministic order by score desc
    picked.sort((a, b) -> Double.compare(scores.getOrDefault(b, 0.0), scores.getOrDefault(a, 0.0)));

    if (picked.size() > k) picked = picked.subList(0, k);
    return picked;
  }

  private static boolean nativesLoaded() {
    // Quick probe: try constructing a tiny model. If it explodes, natives are likely missing.
    try {
      CpModel m = new CpModel();
      m.newBoolVar("probe");
      return true;
    } catch (Throwable t) {
      return false;
    }
  }

  private static double clamp01(double v) {
    if (v < 0.0) return 0.0;
    if (v > 1.0) return 1.0;
    return v;
  }
}
