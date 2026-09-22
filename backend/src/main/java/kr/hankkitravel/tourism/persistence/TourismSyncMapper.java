package kr.hankkitravel.tourism.persistence;

import java.time.Instant;
import java.util.List;
import kr.hankkitravel.tourism.model.TourismSyncRun;
import kr.hankkitravel.tourism.model.TourismSyncScopeState;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Options;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

@Mapper
public interface TourismSyncMapper {
    @Insert("""
            INSERT INTO tourism_sync_runs (scope_key, l_dong_regn_cd, l_dong_signgu_cd, content_type_id,
                started_at, status, remote_call_count, fetched_count, inserted_count, updated_count,
                unchanged_count, deactivated_count, failed_count)
            VALUES (#{scopeKey}, #{lDongRegnCd}, #{lDongSignguCd}, #{contentTypeId}, #{startedAt}, #{status},
                0, 0, 0, 0, 0, 0, 0)
            """)
    @Options(useGeneratedKeys = true, keyProperty = "id")
    int insertRun(TourismSyncRun run);

    @Update("""
            UPDATE tourism_sync_runs SET completed_at = #{completedAt}, status = #{status},
                remote_call_count = #{remoteCallCount}, fetched_count = #{fetchedCount},
                inserted_count = #{insertedCount}, updated_count = #{updatedCount},
                unchanged_count = #{unchangedCount}, deactivated_count = #{deactivatedCount},
                failed_count = #{failedCount}, failure_category = #{failureCategory}
            WHERE id = #{id}
            """)
    int completeRun(TourismSyncRun run);

    @Update("""
            UPDATE tourism_sync_runs SET status = 'FAILED', completed_at = #{completedAt},
                failure_category = 'INTERRUPTED'
            WHERE status = 'RUNNING'
            """)
    int reconcileInterruptedRuns(@Param("completedAt") Instant completedAt);

    @Insert("""
            INSERT INTO tourism_sync_scope_states (scope_key, l_dong_regn_cd, l_dong_signgu_cd, content_type_id,
                last_successful_sync_at, last_successful_run_id)
            VALUES (#{scopeKey}, #{lDongRegnCd}, #{lDongSignguCd}, #{contentTypeId}, #{completedAt}, #{runId})
            ON DUPLICATE KEY UPDATE last_successful_sync_at = VALUES(last_successful_sync_at),
                last_successful_run_id = VALUES(last_successful_run_id)
            """)
    int recordSuccessfulScope(@Param("scopeKey") String scopeKey, @Param("lDongRegnCd") String lDongRegnCd,
            @Param("lDongSignguCd") String lDongSignguCd, @Param("contentTypeId") String contentTypeId,
            @Param("completedAt") Instant completedAt, @Param("runId") long runId);

    @Select("""
            SELECT id, scope_key AS scopeKey, l_dong_regn_cd AS lDongRegnCd, l_dong_signgu_cd AS lDongSignguCd,
                content_type_id AS contentTypeId, started_at AS startedAt, completed_at AS completedAt, status,
                remote_call_count AS remoteCallCount, fetched_count AS fetchedCount, inserted_count AS insertedCount,
                updated_count AS updatedCount, unchanged_count AS unchangedCount,
                deactivated_count AS deactivatedCount, failed_count AS failedCount,
                failure_category AS failureCategory
            FROM tourism_sync_runs ORDER BY started_at DESC LIMIT #{limit}
            """)
    List<TourismSyncRun> findRecentRuns(@Param("limit") int limit);

    @Select("""
            SELECT scope_key AS scopeKey, last_successful_sync_at AS lastSuccessfulSyncAt,
                last_successful_run_id AS lastSuccessfulRunId
            FROM tourism_sync_scope_states
            """)
    List<TourismSyncScopeState> findScopeStates();


    @Select("""
            SELECT successful_run.fetched_count
            FROM tourism_sync_scope_states state
            JOIN tourism_sync_runs successful_run ON successful_run.id = state.last_successful_run_id
            WHERE state.scope_key = #{scopeKey}
            """)
    Integer findLastSuccessfulFetchedCount(@Param("scopeKey") String scopeKey);
    @Select("""
            SELECT COALESCE(SUM(remote_call_count), 0) FROM tourism_sync_runs
            WHERE started_at >= #{from} AND started_at < #{until}
            """)
    int sumRemoteCallCount(@Param("from") Instant from, @Param("until") Instant until);
}
