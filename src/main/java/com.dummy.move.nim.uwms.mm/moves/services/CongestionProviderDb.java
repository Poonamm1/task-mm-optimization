package com.dummy.move.nim.uwms.mm.moves.services;

import com.dummy.move.nim.uwms.mm.moves.db.PenaltyConfigRow;
import com.dummy.move.nim.uwms.mm.moves.interfaces.ICongestionProvider;
import com.dummy.move.nim.uwms.mm.moves.repositores.PenaltyConfigRepo;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Optional;
import org.springframework.stereotype.Component;

@Component
public class CongestionProviderDb implements ICongestionProvider {
  private final PenaltyConfigRepo repo;
  private final Clock clock;

  public CongestionProviderDb(PenaltyConfigRepo repo, Clock clock) {
    this.repo = repo;
    this.clock = clock;
  }

  @Override
  public double penalty(String aisleId) {
    if (aisleId == null) return 0.0;
    Optional<PenaltyConfigRow> rowOpt = repo.findById(aisleId);
    if (!rowOpt.isPresent()) return 0.0;

    PenaltyConfigRow row = rowOpt.get();
    Instant now = Instant.now(clock);
    long ageSec = Math.max(0L, Duration.between(row.getUpdatedTs(), now).getSeconds());
    int cool = Math.max(1, row.getCoolDownSec() == null ? 600 : row.getCoolDownSec());

    // Linear decay: 100% at t=0, 0% at t >= cool_down_sec
    double decay = Math.max(0.0, 1.0 - ((double) ageSec / (double) cool));
    double pct =
        Math.max(0, Math.min(100, row.getCongestionPct() == null ? 0 : row.getCongestionPct()));
    return (pct / 100.0) * decay; // 0.0 .. 1.0
  }
}
