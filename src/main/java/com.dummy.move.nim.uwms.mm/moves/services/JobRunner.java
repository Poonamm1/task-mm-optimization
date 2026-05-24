package com.dummy.move.nim.uwms.mm.moves.services;

import com.dummy.move.nim.uwms.mm.configurations.WorkerProps;
import com.dummy.move.nim.uwms.mm.moves.db.optimizerjobsEntity;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
public class JobRunner {
  private static final Logger log = LoggerFactory.getLogger(JobRunner.class);

  private final JobService jobService;
  private final WorkerProps props;

  public JobRunner(JobService jobService, WorkerProps props) {
    this.jobService = jobService;
    this.props = props;
  }

  @Scheduled(fixedDelayString = "${optimizer.worker.ms:500}")
  public void tick() {
    int processed = 0;
    for (; processed < props.getWorker().getBatchSize(); processed++) {
      optimizerjobsEntity job =
          jobService.claimOne(); // claim the job - prevents double processing.
      if (job == null) break; // no more work in the queue
      jobService.runOne(
          job); // executes the job -  process one job atomically - parse → optimize → filter active
      // → write optimizer_results → mark DONE
    }
    if (processed > 0) log.debug("Processed {} job(s) in this tick", processed);
  }
}
