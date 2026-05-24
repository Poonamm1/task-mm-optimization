package com.dummy.move.nim.uwms.mm.moves.services;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.dummy.move.nim.uwms.mm.configurations.WeightsConfig;
import com.dummy.move.nim.uwms.mm.configurations.WorkerProps;
import com.dummy.move.nim.uwms.mm.moves.db.optimizerjobsEntity;
import com.dummy.move.nim.uwms.mm.moves.interfaces.CongestionProvider;
import com.dummy.move.nim.uwms.mm.moves.interfaces.IMoveOptimizer;
import com.dummy.move.nim.uwms.mm.moves.interfaces.IMoveSelector;
import com.dummy.move.nim.uwms.mm.moves.models.ActiveMoveFilter;
import com.dummy.move.nim.uwms.mm.moves.models.Move;
import com.dummy.move.nim.uwms.mm.moves.models.OptimizerRequest;
import com.dummy.move.nim.uwms.mm.moves.models.OptimizerResult;
import com.dummy.move.nim.uwms.mm.moves.repositores.JobRepo;
import com.dummy.move.nim.uwms.mm.moves.repositores.ResultRepo;
import io.micrometer.common.lang.Nullable;
import java.time.Instant;
import java.util.*;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * JobService
 *
 * <p>Pipeline per job: 1) Parse request & ensure weights profile (coldStart vs normal) 2) Score all
 * candidates (ScoringMoveOptimizer) 3) (Optional) Selector (Greedy / OR-Tools): pick top-K under
 * constraints 4) Filter out non-active moves (OPEN / ASSIGNED / CHOSEN-only-for-this-user) 5)
 * Attach meta & upsert to optimizer_results 6) Mark job DONE / FAILED
 */
@Service
public class JobService {
  private static final Logger log = LoggerFactory.getLogger(JobService.class);

  private final JobRepo jobs;
  private final ResultRepo results;
  private final ObjectMapper mapper = new ObjectMapper();

  // Stage 1: scoring (always)
  private final IMoveOptimizer optimizer = new ScoringMoveOptimizer();

  // Optional Stage 2: constrained selection
  @Nullable private final IMoveSelector selector; // injected by config; may be null if disabled

  private final CongestionProvider congestion;
  private final WorkerProps props;
  private final WeightsConfig weightsCfg;
  private final ActiveMoveFilter activeMoveFilter;

  public JobService(
      JobRepo jobs,
      ResultRepo results,
      CongestionProvider congestion,
      WorkerProps props,
      WeightsConfig weightsCfg,
      ActiveMoveFilter activeMoveFilter,
      @Nullable IMoveSelector selector // wire Greedy or ORTools by config; may be null
      ) {
    this.jobs = jobs;
    this.results = results;
    this.congestion = congestion;
    this.props = props;
    this.weightsCfg = weightsCfg;
    this.activeMoveFilter = activeMoveFilter;
    this.selector = selector;
  }

  /** SELECT … WITH (READPAST, UPDLOCK, ROWLOCK) must run in the same TX as the status flip. */
  @Transactional
  public optimizerjobsEntity claimOne() {
    optimizerjobsEntity j = jobs.selectNextForUpdate();
    if (j == null) return null;
    int ok = jobs.markRunning(j.getId());
    return (ok == 1) ? j : null;
  }

  /** Optimize → optionally select → filter → upsert results → mark DONE/FAILED (one TX). */
  @Transactional
  public void runOne(optimizerjobsEntity job) {
    try {
      // -------- 1) Parse payload & ensure weights are present --------
      OptimizerRequest req = mapper.readValue(job.getPayloadJson(), OptimizerRequest.class);
      if (req.weights == null) {
        req.weights = req.coldStart ? weightsCfg.coldStart() : weightsCfg.normal();
      }

      // Helpful stats for meta
      final int candidateCount = (req.candidates == null ? 0 : req.candidates.size());
      final String weightsProfile = deriveWeightsProfile(req.coldStart, req.weights);

      // -------- 2) Score (always) --------
      long tStart = System.nanoTime();
      OptimizerResult res = optimizer.prioritize(req, congestion);
      long scoreMs = (System.nanoTime() - tStart) / 1_000_000L;

      // -------- 3) (Optional) constrained selection (top-K under caps) --------
      List<Integer> ordered =
          (res.orderedMoveIds == null ? Collections.emptyList() : res.orderedMoveIds);

      if (isSelectionEnabled() && selector != null && !ordered.isEmpty()) {
        final int k = Math.min(props.getSelection().getK(), ordered.size());
        if (k > 0 && k < ordered.size()) {
          // Build selector inputs from the request + scoring outputs
          final Map<String, Double> scoreById = new HashMap<>();
          final Map<String, String> aisleOf = new HashMap<>();
          final Set<String> aisles = new HashSet<>();
          final List<String> idStrings = new ArrayList<>(ordered.size());

          // Use the scored set as the universe; keep maps for selector
          final Map<Integer, Double> scored =
              (res.scores == null ? Collections.emptyMap() : res.scores);
          final Map<Integer, String> aisleByIntId = indexAislesById(req.candidates);

          for (Integer id : ordered) {
            if (id == null) continue;
            final String sid = id.toString();
            idStrings.add(sid);
            scoreById.put(sid, safeDouble(scored.get(id)));
            final String aisle = aisleByIntId.get(id);
            if (aisle != null) {
              aisleOf.put(sid, aisle);
              aisles.add(aisle);
            }
          }

          // Build congestion map once
          final Map<String, Double> congestionOf = new HashMap<>();
          for (String a : aisles) {
            double c = clamp01(congestion.effectiveCongestion(a, req.facilityNum));
            congestionOf.put(a, c);
          }

          // Selector params from config
          double thr = props.getSelection().getCongestedAisleThreshold();
          int maxPerAisle = props.getSelection().getCongestedAisleMax();
          double tLimit = props.getSelection().getSolverMaxTimeSeconds();

          long tSel = System.nanoTime();
          List<String> pickedIds =
              selector.selectTopK(
                  idStrings, scoreById, aisleOf, congestionOf, k, thr, maxPerAisle, tLimit);
          long selMs = (System.nanoTime() - tSel) / 1_000_000L;

          if (pickedIds != null && !pickedIds.isEmpty()) {
            // Keep order returned by selector (should already be score-desc stable)
            ordered =
                pickedIds.stream()
                    .map(JobService::parseIntOrNull)
                    .filter(Objects::nonNull)
                    .collect(Collectors.toList());
            res.orderedMoveIds = ordered;
            log.info(
                "Selector[{}] picked {} of {} in {} ms (k={}, thr={}, maxPerAisle={})",
                props.getSelection().getImpl(),
                ordered.size(),
                idStrings.size(),
                selMs,
                k,
                thr,
                maxPerAisle);
          } else {
            log.warn(
                "Selector[{}] returned empty; keeping scorer order (k={})",
                props.getSelection().getImpl(),
                k);
          }
        } else {
          log.debug(
              "Selector enabled but skipped (k={} ordered={})",
              props.getSelection().getK(),
              ordered.size());
        }
      } else {
        if (!isSelectionEnabled()) {
          log.debug("Selector disabled by config.");
        } else if (selector == null) {
          log.warn("Selector config says enabled but selector bean is null; skipping selection.");
        }
      }

      final int beforeFilter = (res.orderedMoveIds == null ? 0 : res.orderedMoveIds.size());

      // -------- 4) Filter out non-active (OPEN/ASSIGNED/CHOSEN-for-this-user only) --------
      res.orderedMoveIds = activeMoveFilter.keepActive(res.orderedMoveIds);
      final int afterFilter = (res.orderedMoveIds == null ? 0 : res.orderedMoveIds.size());
      final int filteredOut = Math.max(0, beforeFilter - afterFilter);

      if (filteredOut > 0) {
        log.info("Active filter trimmed {} moves (ctxKey={})", filteredOut, job.getContextKey());
      } else {
        log.debug("Active filter no-op (ctxKey={}, size={})", job.getContextKey(), afterFilter);
      }

      // -------- 5) Attach result metadata & upsert --------
      Map<String, Object> meta = new LinkedHashMap<>();
      safePut(meta, "modelVersion", props.getModelVersion());
      safePut(meta, "weightsProfile", weightsProfile); // "coldStart" / "normal"
      safePut(meta, "candidateCount", candidateCount);
      safePut(meta, "orderedCount", afterFilter);
      safePut(meta, "filteredOut", filteredOut);
      safePut(meta, "selectionEnabled", Boolean.valueOf(isSelectionEnabled()));
      if (isSelectionEnabled()) {
        safePut(meta, "selectorImpl", props.getSelection().getImpl());
        safePut(meta, "selectorK", props.getSelection().getK());
        safePut(meta, "congestedAisleThreshold", props.getSelection().getCongestedAisleThreshold());
        safePut(meta, "congestedAisleMax", props.getSelection().getCongestedAisleMax());
      }
      safePut(meta, "scoreMs", scoreMs);
      safePut(meta, "ttlSeconds", props.getTtlSeconds());
      res.meta = meta;

      String resJson = mapper.writeValueAsString(res);
      String metaJson = toJsonOrNull(meta);
      Instant now = Instant.now();

      int updated =
          results.updateResult(
              job.getContextKey(),
              job.getUserId(),
              job.getFacilityNum(),
              resJson,
              now,
              props.getTtlSeconds(),
              metaJson);
      if (updated == 0) {
        results.insertResult(
            job.getContextKey(),
            job.getUserId(),
            job.getFacilityNum(),
            resJson,
            now,
            props.getTtlSeconds(),
            metaJson);
      }

      // -------- 6) Done --------
      jobs.markDone(job.getId());
      log.info(
          "Job {} DONE ctxKey={} ordered={} ttl={}",
          job.getId(),
          job.getContextKey(),
          afterFilter,
          props.getTtlSeconds());

    } catch (Exception e) {
      log.error("Job {} FAILED ctxKey={}: {}", job.getId(), job.getContextKey(), e.toString(), e);
      jobs.failOnce(job.getId(), props.getWorker().getMaxRetries(), truncate(e.getMessage(), 1000));
    }
  }

  /* ================= helpers ================= */

  private boolean isSelectionEnabled() {
    return props.getSelection() != null && props.getSelection().isEnabled();
  }

  private static Map<Integer, String> indexAislesById(List<Move> moves) {
    if (moves == null || moves.isEmpty()) return Collections.emptyMap();
    Map<Integer, String> m = new HashMap<>(moves.size());
    for (Move mv : moves) {
      if (mv == null || mv.getMoveId() == null) continue;
      m.put(mv.getMoveId(), mv.getAisleId());
    }
    return m;
  }

  private static Double safeDouble(@Nullable Double v) {
    return (v == null ? 0.0 : v.doubleValue());
  }

  private static Integer parseIntOrNull(String s) {
    if (s == null) return null;
    try {
      return Integer.valueOf(s);
    } catch (NumberFormatException ignore) {
      return null;
    }
  }

  private static String toJsonOrNull(@Nullable Object o) {
    if (o == null) return null;
    try {
      return new ObjectMapper().writeValueAsString(o);
    } catch (Exception e) {
      return null;
    }
  }

  private static String deriveWeightsProfile(boolean coldStart, Object weights) {
    // If your Weights has profileName, prefer that. Derived for now:
    return coldStart ? "coldStart" : "normal";
  }

  private static double clamp01(double v) {
    if (v < 0.0) return 0.0;
    if (v > 1.0) return 1.0;
    return v;
  }

  private static void safePut(Map<String, Object> m, String k, @Nullable Object v) {
    if (k != null && v != null) m.put(k, v);
  }

  private static String truncate(String s, int max) {
    if (s == null) return null;
    return s.length() <= max ? s : s.substring(0, max);
  }
}
