package com.dummy.move.nim.uwms.mm.moves.models;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.dummy.move.nim.uwms.mm.domain.constants.MoveType;
import lombok.Getter;
import lombok.Setter;

/**
 * Candidate move considered by the optimizer (Phase 5A payload).
 *
 * <p>Notes: - We carry time fields as ISO-8601 strings (UTC recommended) so the API doesn't have to
 * decide units. The worker can parse these into epoch seconds. - For FPP/JUMP/REPLEN, openedIso
 * should be the first "OPEN" activity timestamp. - dispatchIso is optional (used by parity logic at
 * non‑Atlas sites; you can still log it). - distanceMeters is optional. If null, the optimizer will
 * assume a neutral distance term. - x/y are optional coordinates (meters or site units) if you
 * later want worker-side distance.
 */
@Getter
@Setter
@JsonInclude(JsonInclude.Include.NON_NULL)
public final class Move {

  /** Numeric movement id. */
  private Integer moveId;

  /** Domain move type (e.g., FULLPULL, JUMP, REPLEN...). */
  private MoveType type;

  /** Lower number = more urgent. */
  private Integer movePriority;

  /** First OPEN activity time, ISO-8601 (e.g., "2025-08-14T10:24:42.210Z"). */
  public Long openedEpochSec; // nullable

  public Long dispatchEpochSec; // nullable
  /** Aisle (for congestion penalties). */
  private String aisleId;

  /** Warehouse zone / area (for same-zone bonus). */
  private String zoneId;

  /** Optional precomputed distance from the user's anchor (meters). */
  private Double distanceMeters;

  /** Optional coordinates if you later compute distance server-side. */
  private Double x;

  private Double y;

  private String routeNumber;
  private Short stopNumber;
  private Integer warehouseAreaCode;

  public boolean hasCoords() {
    return x != null && y != null;
  }

  public String getTypeNameOrNull() {
    return (type == null ? null : type.name());
  }
}
