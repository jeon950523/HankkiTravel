package kr.hankkitravel.nutrition.persistence;

import kr.hankkitravel.nutrition.model.NutritionImportRun;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Options;
import org.apache.ibatis.annotations.Update;

@Mapper
public interface NutritionImportRunMapper {
    @Insert("""
            INSERT INTO nutrition_import_runs (source_dataset_name, source_dataset_version, source_row_count,
                imported_count, excluded_count, status, failure_summary, started_at, completed_at)
            VALUES (#{sourceDatasetName}, #{sourceDatasetVersion}, #{sourceRowCount}, #{importedCount},
                #{excludedCount}, #{status}, #{failureSummary}, #{startedAt}, #{completedAt})
            """)
    @Options(useGeneratedKeys = true, keyProperty = "id")
    int insert(NutritionImportRun run);

    @Update("""
            UPDATE nutrition_import_runs SET imported_count = #{importedCount}, excluded_count = #{excludedCount},
                status = #{status}, failure_summary = #{failureSummary}, completed_at = #{completedAt}
            WHERE id = #{id}
            """)
    int complete(NutritionImportRun run);
}
