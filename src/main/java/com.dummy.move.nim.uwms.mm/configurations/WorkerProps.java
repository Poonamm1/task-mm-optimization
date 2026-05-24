package com.dummy.move.nim.uwms.mm.configurations;

import jakarta.persistence.criteria.Selection;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * Optimizer worker runtime configuration (Java 17 side).
 *
 * <p>Prefix: optimizer.*
 *
 * <p>Major sections: - modelVersion / ttlSeconds - worker.* : loop and retry knobs - selection.* :
 * algorithm selection (greedy / ortools / auto) and per-algo knobs
 */
@Component
@ConfigurationProperties(prefix = "optimizer")
public class WorkerProps {

  /** Human-readable model/config label stamped into result meta. E.g. "v5a-2025-08-23". */
  private String modelVersion;

  /** How long a computed result is considered “fresh” by the reader (seconds). */
  private int ttlSeconds = 90;

  /** Worker loop settings (poll rate, batch size, retry cap). */
  private Worker worker = new Worker();

  /** Algorithm selection and per-algo tuning. */
  private Selection selection = new Selection();

  // ----------------------- NESTED TYPES -----------------------

  public static class Worker {
    /** Polling delay between scheduler ticks (ms). */
    private long ms = 500;

    /** How many jobs to process per tick. */
    private int batchSize = 10;

    /** Max failures before a job is marked FAILED. */
    private int maxRetries = 3;

    // Getters / Setters
    public long getMs() {
      return ms;
    }

    public void setMs(long ms) {
      this.ms = ms;
    }

    public int getBatchSize() {
      return batchSize;
    }

    public void setBatchSize(int batchSize) {
      this.batchSize = batchSize;
    }

    public int getMaxRetries() {
      return maxRetries;
    }

    public void setMaxRetries(int maxRetries) {
      this.maxRetries = maxRetries;
    }
  }

  /**
   * Algorithm selection block.
   *
   * <p>impl: "greedy", "ortools", or "auto" - greedy : fast, scoring-based - ortools :
   * combinatorial solver (respect constraints), time-boxed - auto : choose per request (e.g., by
   * candidate count or congestion); worker code decides
   *
   * <p>k: pre-top-K that the selector keeps (headroom above UI page size).
   *
   * <p>greedy.* and ortools.* groups hold per-algorithm knobs.
   */
  public static class Selection {

    /** Turn the selector stage on/off. If disabled we default to greedy scoring. */
    private boolean enabled = true;

    /** "greedy", "ortools", or "auto". */
    private String impl = "greedy";

    /** How many candidates to keep before final ordering (>= UI limit to keep headroom). */
    private int k = 200;

    /** Heuristic: consider an aisle "congested" when congestion >= threshold. */
    private double congestedAisleThreshold = 0.80;

    /** Heuristic: cap how many congested aisles we tolerate in the chosen set. */
    private int congestedAisleMax = 1;

    /** Maximum solver time (seconds) when impl = "ortools" or auto picks OR-Tools. */
    private double solverMaxTimeSeconds = 0.05;

    /** Greedy-specific knobs. */
    private Greedy greedy = new Greedy();

    /** OR-Tools-specific knobs. */
    private OrTools ortools = new OrTools();

    // ---- sub-groups ----

    public static class Greedy {
      /** Optional: soft cap to avoid too many picks from the same aisle back-to-back. */
      private int maxConsecutiveSameAisle = 3;

      /** Optional: weight to penalize aisle repeats within a short window. */
      private double repeatAislePenalty = 0.10;

      public int getMaxConsecutiveSameAisle() {
        return maxConsecutiveSameAisle;
      }

      public void setMaxConsecutiveSameAisle(int v) {
        this.maxConsecutiveSameAisle = v;
      }

      public double getRepeatAislePenalty() {
        return repeatAislePenalty;
      }

      public void setRepeatAislePenalty(double v) {
        this.repeatAislePenalty = v;
      }
    }

    public static class OrTools {
      /** Enable/disable OR-Tools usage (in case the environment is missing native bindings). */
      private boolean enabled = true;

      /** Solver time limit (seconds). If <=0, solver runs with its internal default. */
      private double maxTimeSeconds = 0.05;

      /** Optional: search limit to bound exploration (implementation-specific). */
      private long maxNodes = 0L;

      /** Optional: strategy hint (implementation-specific). */
      private String searchStrategy = "AUTOMATIC";

      public boolean isEnabled() {
        return enabled;
      }

      public void setEnabled(boolean enabled) {
        this.enabled = enabled;
      }

      public double getMaxTimeSeconds() {
        return maxTimeSeconds;
      }

      public void setMaxTimeSeconds(double maxTimeSeconds) {
        this.maxTimeSeconds = maxTimeSeconds;
      }

      public long getMaxNodes() {
        return maxNodes;
      }

      public void setMaxNodes(long maxNodes) {
        this.maxNodes = maxNodes;
      }

      public String getSearchStrategy() {
        return searchStrategy;
      }

      public void setSearchStrategy(String searchStrategy) {
        this.searchStrategy = searchStrategy;
      }
    }

    // Getters / Setters
    public boolean isEnabled() {
      return enabled;
    }

    public void setEnabled(boolean enabled) {
      this.enabled = enabled;
    }

    public String getImpl() {
      return impl;
    }

    public void setImpl(String impl) {
      this.impl = impl;
    }

    public int getK() {
      return k;
    }

    public void setK(int k) {
      this.k = k;
    }

    public double getCongestedAisleThreshold() {
      return congestedAisleThreshold;
    }

    public void setCongestedAisleThreshold(double v) {
      this.congestedAisleThreshold = v;
    }

    public int getCongestedAisleMax() {
      return congestedAisleMax;
    }

    public void setCongestedAisleMax(int congestedAisleMax) {
      this.congestedAisleMax = congestedAisleMax;
    }

    public double getSolverMaxTimeSeconds() {
      return solverMaxTimeSeconds;
    }

    public void setSolverMaxTimeSeconds(double solverMaxTimeSeconds) {
      this.solverMaxTimeSeconds = solverMaxTimeSeconds;
    }

    public Greedy getGreedy() {
      return greedy;
    }

    public void setGreedy(Greedy greedy) {
      this.greedy = greedy;
    }

    public OrTools getOrtools() {
      return ortools;
    }

    public void setOrtools(OrTools ortools) {
      this.ortools = ortools;
    }
  }

  // ----------------------- GETTERS / SETTERS -----------------------

  /**
   * Returns configured modelVersion; if not set, tries MANIFEST Implementation-Version, then
   * OPTIMIZER_MODEL_VERSION env var; finally "unknown".
   */
  public String getModelVersion() {
    if (modelVersion != null && !modelVersion.trim().isEmpty()) {
      return modelVersion;
    }
    String fromManifest = WorkerProps.class.getPackage().getImplementationVersion();
    if (fromManifest != null && !fromManifest.trim().isEmpty()) return fromManifest;

    String fromEnv = System.getenv("OPTIMIZER_MODEL_VERSION");
    if (fromEnv != null && !fromEnv.trim().isEmpty()) return fromEnv;

    return "unknown";
  }

  public void setModelVersion(String modelVersion) {
    this.modelVersion = modelVersion;
  }

  public int getTtlSeconds() {
    return ttlSeconds;
  }

  public void setTtlSeconds(int ttlSeconds) {
    this.ttlSeconds = ttlSeconds;
  }

  public Worker getWorker() {
    return worker;
  }

  public void setWorker(Worker worker) {
    this.worker = worker;
  }

  public Selection getSelection() {
    return selection;
  }

  public void setSelection(Selection selection) {
    this.selection = selection;
  }
}
