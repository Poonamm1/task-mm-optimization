package com.dummy.move.nim.uwms.mm.moves.db;

import jakarta.persistence.*;
import java.time.Instant;
import lombok.Getter;
import lombok.Setter;

@Entity
@Table(name = "optimizer_jobs")
@Getter
@Setter
public class optimizerjobsEntity {
  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @Column(name = "context_key", nullable = false, unique = true, length = 200)
  private String contextKey;

  @Column(name = "user_id", nullable = false, length = 64)
  private String userId;

  @Column(name = "facility_num", nullable = false)
  private Integer facilityNum;

  @Lob
  @Column(name = "payload_json", nullable = false)
  private String payloadJson;

  @Column(name = "status", nullable = false, length = 16)
  private String status; // QUEUED, RUNNING, DONE, FAILED

  @Column(name = "failed_attempts", nullable = false)
  private Integer failedAttempts;

  @Column(name = "created_ts", nullable = false)
  private Instant createdTs;

  @Column(name = "updated_ts")
  private Instant updatedTs;

  @Lob
  @Column(name = "error")
  private String error;
}
