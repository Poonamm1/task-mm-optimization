package com.dummy.move.nim.uwms.mm.moves.repositores;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class MoveStatusDao {
  private final NamedParameterJdbcTemplate jdbc;

  public MoveStatusDao(NamedParameterJdbcTemplate jdbc) {
    this.jdbc = jdbc;
  }

  public Map<Long, Integer> findStatusByIds(Set<Long> ids) {
    if (ids == null || ids.isEmpty()) return java.util.Collections.emptyMap();
    MapSqlParameterSource p = new MapSqlParameterSource();
    p.addValue("ids", ids);
    return jdbc.query(
        "SELECT id, status " + "FROM mdse_movement_request " + "WHERE id IN (:ids)",
        p,
        rs -> {
          Map<Long, Integer> out = new HashMap<>();
          while (rs.next()) {
            out.put(rs.getLong(1), rs.getInt(2));
          }
          return out;
        });
  }
}
