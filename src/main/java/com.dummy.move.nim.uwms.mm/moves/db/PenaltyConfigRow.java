package com.dummy.move.nim.uwms.mm.moves.db;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import lombok.Getter;
import lombok.Setter;

@Entity
@Table(name = "optimizer_penalty_config")
@Getter
@Setter
public class PenaltyConfigRow {
  @Id
  @Column(name = "aisle_id", length = 64)
  private String aisleId;

  @Column(name = "congestion_pct", nullable = false)
  private Integer congestionPct;

  @Column(name = "cool_down_sec", nullable = false)
  private Integer coolDownSec;

  @Column(name = "updated_ts", nullable = false)
  private Instant updatedTs;
}
