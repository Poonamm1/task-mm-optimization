package com.dummy.move.nim.uwms.mm.moves.repositores;

import com.dummy.move.nim.uwms.mm.moves.db.ResultEntity;
import java.time.Instant;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

@Repository
public interface ResultRepo extends JpaRepository<ResultEntity, String> {

  @Modifying
  @Query(
      value =
          "UPDATE optimizer_results "
              + "SET user_id=:userId, facility_num=:facilityNum, result_json=:resultJson, "
              + "    computed_ts=:computedTs, ttl_seconds=:ttlSeconds, meta_json=:metaJson "
              + "WHERE context_key=:contextKey",
      nativeQuery = true)
  int updateResult(
      @Param("contextKey") String contextKey,
      @Param("userId") String userId,
      @Param("facilityNum") Integer facilityNum,
      @Param("resultJson") String resultJson,
      @Param("computedTs") Instant computedTs,
      @Param("ttlSeconds") Integer ttlSeconds,
      @Param("metaJson") String metaJson);

  @Modifying
  @Query(
      value =
          "INSERT INTO optimizer_results "
              + "(context_key, user_id, facility_num, result_json, computed_ts, ttl_seconds, meta_json) "
              + "VALUES (:contextKey, :userId, :facilityNum, :resultJson, :computedTs, :ttlSeconds, :metaJson)",
      nativeQuery = true)
  int insertResult(
      @Param("contextKey") String contextKey,
      @Param("userId") String userId,
      @Param("facilityNum") Integer facilityNum,
      @Param("resultJson") String resultJson,
      @Param("computedTs") Instant computedTs,
      @Param("ttlSeconds") Integer ttlSeconds,
      @Param("metaJson") String metaJson);
}
