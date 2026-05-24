package com.dummy.move.nim.uwms.mm.moves.services;

import com.dummy.move.nim.uwms.mm.moves.interfaces.CongestionProvider;
import com.dummy.move.nim.uwms.mm.moves.interfaces.IMoveOptimizer;
import com.dummy.move.nim.uwms.mm.moves.models.Move;
import com.dummy.move.nim.uwms.mm.moves.models.OptimizerRequest;
import com.dummy.move.nim.uwms.mm.moves.models.OptimizerResult;
import java.time.Instant;
import java.util.*;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * ScoringMoveOptimizer
 *
 * <p>Computes a weighted score for each candidate move and orders them.
 *
 * <p>Score formula: total = wPriority*priorityTerm + wAge*ageTerm + wType*typeBias -
 * wCongestion*congestion + wDistance*distanceTerm + sameZoneBonus (optional)
 *
 * <p>Details: - Enforces "minPriorityInBatch": only the lowest numeric priority group is scored.
 * (Defensive — already done in Java 8 pre-trim, but repeated here for safety.) - Age: uses
 * openedEpochSec when present, falls back to dispatchEpochSec. - Distance: relative normalization
 * within batch (min–max), ignored entirely on coldStart. - Type bias: allowedTypes = 1.0, others =
 * 0.7. - Congestion: subtract penalty, normalized [0..1]. - Same-zone: adds fixed bonus if user's
 * zone matches move's zoneId. - Fully null-safe; logs size, min priority, and top IDs for
 * debugging.
 *
 * <p>Note: Algorithm selection (Scoring vs OR-Tools) happens in JobService/factory, not here.
 */
public final class ScoringMoveOptimizer implements IMoveOptimizer {
  private static final Logger LOG = LoggerFactory.getLogger(ScoringMoveOptimizer.class);

  @Override
  public OptimizerResult prioritize(OptimizerRequest req, CongestionProvider congestion) {
    Objects.requireNonNull(req, "req");
    Objects.requireNonNull(req.candidates, "candidates");
    Objects.requireNonNull(req.weights, "weights");
    if (congestion == null) congestion = CongestionProvider.noOp();

    // ---- Min priority in batch (strict): keep only the lowest numeric priority present ----
    // TODO: MinPriority check - Check if we need extra check
    // If we keep it, it makes the worker more robust against “bad payloads.” That’s why it’s here.
    // ACTION: Convert it to a cheap guard that detects mixed priorities and logs (optionally
    // enforces behind a flag)
    Integer minPri = null;
    for (Move m : req.candidates) {
      final Integer p = m.getMovePriority();
      if (p != null) {
        if (minPri == null || p.intValue() < minPri.intValue()) minPri = p;
      }
    }
    final List<Move> pool;
    if (minPri != null) {
      final int minP = minPri.intValue();
      pool =
          req.candidates.stream()
              .filter(m -> m.getMovePriority() != null && m.getMovePriority().intValue() == minP)
              .collect(Collectors.toList());
    } else {
      // No priorities? compete everything (rare, but keep robust)
      pool = req.candidates;
    }

    final long nowSec = Instant.now().getEpochSecond();
    LOG.debug(
        "Scoring {} moves of {} total (minPriorityInBatch={}) user={} fac={} coldStart={} wDist={}",
        pool.size(),
        req.candidates.size(),
        minPri,
        req.userId,
        req.facilityNum,
        req.coldStart,
        req.weights.wDistance);
    /**
     * It’s a min–max normalization: Finds the min and max distance in the batch. Maps each distance
     * linearly into [0..1], where closest = 1.0 and farthest = 0.0. If distance is missing/null →
     * neutral 0.5 is used. If coldStart = true → distance term is ignored entirely (weight = 0).
     * Purpose: let distance act as a relative term (“closer = better”) regardless of absolute
     * meters, so scores across moves are comparable.
     */
    final Map<Integer, Double> distTermById = computeDistanceTerms(pool);

    final Map<Integer, Double> totals = new HashMap<Integer, Double>(pool.size());
    final Map<Integer, Map<String, Double>> breakdown =
        new LinkedHashMap<Integer, Map<String, Double>>(pool.size());

    for (Move move : pool) {
      final Map<String, Double> bd = new LinkedHashMap<String, Double>();
      double score = 0.0;

      // 1) Priority term: lower numeric priority => better (map to [0..1] via Normalization)
      final double pri = Normalization.normalizePriority(move.getMovePriority());
      score += req.weights.wPriority * pri;
      bd.put("priority", pri); //

      // 2) Age term: prefer older.
      // Use openedEpochSec when present; if missing, fall back to dispatchEpochSec.
      final Long openedSec = move.getOpenedEpochSec();
      final Long dispatchSec = move.getDispatchEpochSec();
      final Long observedStart = (openedSec != null ? openedSec : dispatchSec);
      final double age = Normalization.ageTerm(observedStart, nowSec);
      score += req.weights.wAge * age;
      bd.put("age", age);

      // 3) Type bias: allowed types get 1.0; others small discount.
      final String tname = move.getTypeNameOrNull();
      final double typeBias =
          (req.allowedTypes == null || tname == null)
              ? 1.0
              : (req.allowedTypes.contains(tname) ? 1.0 : 0.7);
      score += req.weights.wType * typeBias;
      bd.put("type", typeBias);

      // 4) Congestion penalty: higher congestion => subtract more
      final String aisle = move.getAisleId();
      final double cong = clamp01(congestion.effectiveCongestion(aisle, req.facilityNum));
      score -= req.weights.wCongestion * cong;
      bd.put("congestionFree", 1.0 - cong); // 1 = free, 0 = jammed

      // 5) Distance: ignored on coldStart; neutral 0.5 when unknown
      final double distTerm =
          distTermById.containsKey(move.getMoveId()) ? distTermById.get(move.getMoveId()) : 0.5;
      final double distWeight = req.coldStart ? 0.0 : req.weights.wDistance;
      score += distWeight * distTerm;
      bd.put("distance", distTerm);

      // 6) Same-zone micro bonus (only when both sides non-null and equal)
      // TODO: SameZoneID is coming from merchandise-movement service but warehouseAreaCOde is still
      // not- check if we need to add it ? do we need both
      // zoneID and warehouseAReaCode?

      final double bonus =
          (req.weights.sameWarehouseAreaBonus == null ? 0.0 : req.weights.sameWarehouseAreaBonus);

      boolean sameAreaOrZone = false;
      if (req.warehouseAreaCode != null
          && move.getWarehouseAreaCode() != null
          && req.warehouseAreaCode.equals(move.getWarehouseAreaCode())) {
        sameAreaOrZone = true;
      } else if (req.userZoneId != null
          && move.getZoneId() != null
          && req.userZoneId.equals(move.getZoneId())) {
        sameAreaOrZone = true;
      }

      if (bonus > 0.0 && sameAreaOrZone) {
        score += bonus;
        bd.put("sameZoneBonus", bonus);
      } else {
        bd.put("sameZoneBonus", 0.0);
      }

      totals.put(move.getMoveId(), score);
      breakdown.put(move.getMoveId(), bd);
    }

    // Sort within the min-priority pool:
    // score desc → lower priority (should be equal but keep tie guard) → older observedStart → id
    // asc
    final List<Integer> ordered =
        pool.stream()
            .sorted(
                (a, b) -> {
                  int c =
                      Double.compare(
                          getOrDefault(totals.get(b.getMoveId()), 0.0),
                          getOrDefault(totals.get(a.getMoveId()), 0.0));
                  if (c != 0) return c;

                  final int ap =
                      (a.getMovePriority() == null ? Integer.MAX_VALUE : a.getMovePriority());
                  final int bp =
                      (b.getMovePriority() == null ? Integer.MAX_VALUE : b.getMovePriority());
                  c = Integer.compare(ap, bp);
                  if (c != 0) return c;

                  final Long ao =
                      (a.getOpenedEpochSec() != null
                          ? a.getOpenedEpochSec()
                          : a.getDispatchEpochSec());
                  final Long bo =
                      (b.getOpenedEpochSec() != null
                          ? b.getOpenedEpochSec()
                          : b.getDispatchEpochSec());
                  final long aox = (ao == null ? Long.MAX_VALUE : ao);
                  final long box = (bo == null ? Long.MAX_VALUE : bo);
                  c = Long.compare(aox, box); // earlier (older) first
                  if (c != 0) return c;

                  if (isFppOrJump(a) && isFppOrJump(b)) {
                    c = compareRoute(a.getRouteNumber(), b.getRouteNumber());
                    if (c != 0) return c;
                    c = compareStop(a.getStopNumber(), b.getStopNumber());
                    if (c != 0) return c;
                  }
                  final int aid = (a.getMoveId() == null ? Integer.MAX_VALUE : a.getMoveId());
                  final int bid = (b.getMoveId() == null ? Integer.MAX_VALUE : b.getMoveId());
                  return Integer.compare(aid, bid);
                })
            .map(Move::getMoveId)
            .collect(Collectors.toList());

    final OptimizerResult out = new OptimizerResult();
    out.orderedMoveIds = ordered;
    out.scores = totals;
    out.scoreBreakdown = breakdown;

    if (LOG.isDebugEnabled()) {
      LOG.debug(
          "Scoring done: pool={} of total={} minPri={} coldStart={} topId={}",
          pool.size(),
          req.candidates.size(),
          minPri,
          req.coldStart,
          (ordered.isEmpty() ? null : ordered.get(0)));
    }
    return out;
  }

  /* ---------------- helpers ---------------- */

  /** Build normalized distance terms (0..1, 1 = closest). Neutral 0.5 when missing. */
  private static Map<Integer, Double> computeDistanceTerms(List<Move> moves) {
    double min = Double.POSITIVE_INFINITY;
    double max = Double.NEGATIVE_INFINITY;
    boolean any = false;

    for (Move m : moves) {
      final Double d = m.getDistanceMeters();
      if (d != null && d > 0.0) {
        any = true;
        if (d < min) min = d;
        if (d > max) max = d;
      }
    }
    if (!any || !(max > min)) {
      return Collections.emptyMap();
    }

    final double span = max - min;
    final Map<Integer, Double> out = new HashMap<Integer, Double>(moves.size());
    for (Move m : moves) {
      final Double d = m.getDistanceMeters();
      if (d == null || d <= 0.0) continue; // others get neutral 0.5 implicitly
      double norm = 1.0 - ((d - min) / span); // smaller = better → higher term
      out.put(m.getMoveId(), clamp01(norm));
    }
    return out;
  }

  private static double getOrDefault(Double v, double dflt) {
    return (v == null ? dflt : v.doubleValue());
  }

  private static double clamp01(double v) {
    if (v < 0.0) return 0.0;
    if (v > 1.0) return 1.0;
    return v;
  }

  private static boolean isFppOrJump(Move m) {
    if (m == null || m.getType() == null) return false;
    switch (m.getType()) {
      case FULLPULL:
      case JUMP:
        return true;
      default:
        return false;
    }
  }

  private static int compareRoute(String a, String b) {
    if (a == null && b == null) return 0;
    if (a == null) return 1; // nulls last
    if (b == null) return -1;
    // try numeric compare if both are integers
    try {
      long na = Long.parseLong(a.trim());
      long nb = Long.parseLong(b.trim());
      return Long.compare(na, nb);
    } catch (NumberFormatException ignore) {
      // lexicographic, case-insensitive
      return a.compareToIgnoreCase(b);
    }
  }

  private static int compareStop(Short a, Short b) {
    if (a == null && b == null) return 0;
    if (a == null) return 1; // nulls last
    if (b == null) return -1;
    return Short.compare(a, b);
  }
}
