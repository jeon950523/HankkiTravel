package kr.hankkitravel.tourism.persistence;

import java.time.Instant;
import kr.hankkitravel.tourism.model.TourismSyncRun;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Options;
import org.apache.ibatis.annotations.Param;
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
}
