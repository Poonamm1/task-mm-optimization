package com.dummy.move.nim.uwms.mm.moves.utils;

public final class TimeUtil {
  private TimeUtil() {}

  public static Long parseIsoToEpochSec(String iso) {
    if (iso == null || iso.isEmpty()) return null;
    try {
      return java.time.Instant.parse(iso).getEpochSecond();
    } catch (Exception ignore) {
      return null;
    }
  }
}
