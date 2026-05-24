package com.dummy.move.nim.uwms.mm.moves.models;

import com.dummy.move.nim.uwms.mm.domain.constants.MoveType;
import java.util.List;

public final class OptimizerRequest {
  public String userId;
  public int facilityNum;
  public boolean coldStart; // when lastScannedLocation == null
  public List<MoveType> allowedTypes; // from task_assignment
  public List<Move> candidates; // already filtered by assignment & site
  public Weights weights; // loaded by caller
  public Integer userZoneId;
  public Integer warehouseAreaCode;
  public boolean isAtlasSite;
}
