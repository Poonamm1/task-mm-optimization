package com.dummy.move.nim.uwms.mm.moves.repositores;

import com.dummy.move.nim.uwms.mm.moves.db.optimizerjobsEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

@Repository
public interface JobRepo extends JpaRepository<optimizerjobsEntity, Long> {

  @Query(
      value =
          "SELECT TOP (1) * "
              + "FROM optimizer_jobs WITH (READPAST, UPDLOCK, ROWLOCK) "
              + "WHERE status='QUEUED' "
              + "ORDER BY created_ts ASC",
      nativeQuery = true)
  optimizerjobsEntity selectNextForUpdate();

  @Modifying
  @Query(
      value =
          "UPDATE optimizer_jobs SET status='RUNNING', updated_ts=SYSUTCDATETIME() "
              + "WHERE id=:id AND status='QUEUED'",
      nativeQuery = true)
  int markRunning(@Param("id") Long id);

  @Modifying
  @Query(
      value =
          "UPDATE optimizer_jobs SET status='DONE', updated_ts=SYSUTCDATETIME(), error=NULL "
              + "WHERE id=:id",
      nativeQuery = true)
  int markDone(@Param("id") Long id);

  @Modifying
  @Query(
      value =
          "UPDATE optimizer_jobs "
              + "SET failed_attempts = failed_attempts + 1, "
              + "    status = CASE WHEN failed_attempts + 1 >= :maxRetries THEN 'FAILED' ELSE 'QUEUED' END, "
              + "    updated_ts = SYSUTCDATETIME(), "
              + "    error = :err "
              + "WHERE id=:id",
      nativeQuery = true)
  int failOnce(@Param("id") Long id, @Param("maxRetries") int maxRetries, @Param("err") String err);
}
