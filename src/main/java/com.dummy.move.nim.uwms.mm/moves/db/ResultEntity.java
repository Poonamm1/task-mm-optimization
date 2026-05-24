package com.dummy.move.nim.uwms.mm.moves.db;

import jakarta.persistence.*;
import java.time.Instant;
import lombok.Getter;
import lombok.Setter;

@Entity
@Table(name = "optimizer_results")
@Setter
@Getter
public class ResultEntity {
  @Id
  @Column(name = "context_key", length = 200)
  private String contextKey;

  @Column(name = "user_id", nullable = false, length = 64)
  private String userId;

  @Column(name = "facility_num", nullable = false)
  private Integer facilityNum;

  @Lob
  @Column(name = "result_json", nullable = false)
  private String resultJson;

  @Column(name = "computed_ts", nullable = false)
  private Instant computedTs;

  @Column(name = "ttl_seconds", nullable = false)
  private Integer ttlSeconds;

  @Lob
  @Column(name = "meta_json")
  private String metaJson;
}
