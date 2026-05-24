package com.dummy.move.nim.uwms.mm.moves.interfaces;

public interface CongestionProvider {
  /** returns [0..1] penalty weight for aisle at now */
  double effectiveCongestion(String aisleId, int facilityNum);

  static CongestionProvider noOp() {
    return (a, f) -> 0.0;
  }
}
