package com.dummy.move.nim.uwms.mm.moves.models;

import com.dummy.move.nim.uwms.mm.moves.repositores.MoveStatusDao;
import java.util.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

// task-mm-optimization (Java 17)
@Component
public class ActiveMoveFilter {

  @Value("${optimizer.results.filterNonActive:true}")
  private boolean
      filterNonActive; // TODO: Probably we don't need this config because we always want to filter
  // the active only

  // Map to DB codes (set via config). Example: OPEN=1, ASSIGNED=8, CHOSEN=7
  @Value("${optimizer.results.activeStatusCodes:1,8,7}")
  private String activeCodesCsv;

  private final MoveStatusDao dao;

  public ActiveMoveFilter(MoveStatusDao dao) {
    this.dao = dao;
  }

  public List<Integer> keepActive(List<Integer> orderedMoveIds) {
    if (!filterNonActive || orderedMoveIds == null || orderedMoveIds.isEmpty())
      return orderedMoveIds;

    Set<Long> ids = new HashSet<>();
    for (Integer i : orderedMoveIds) if (i != null) ids.add(i.longValue());

    Map<Long, Integer> statusById = dao.findStatusByIds(ids);

    Set<Integer> activeCodes = new HashSet<>();
    for (String s : activeCodesCsv.split(",")) {
      String t = s.trim();
      if (!t.isEmpty()) activeCodes.add(Integer.valueOf(t));
    }

    List<Integer> out = new ArrayList<>(orderedMoveIds.size());
    for (Integer id : orderedMoveIds) {
      if (id == null) continue;
      Integer code = statusById.get(id.longValue());
      if (code != null && activeCodes.contains(code)) out.add(id);
    }
    return out;
  }
}
