package kr.hankkitravel.nutrition.persistence;

import java.util.List;
import kr.hankkitravel.nutrition.model.NutritionFood;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

@Mapper
public interface NutritionFoodMapper {
    @Update("""
            UPDATE nutrition_foods SET active = FALSE
            WHERE source_dataset_name = #{datasetName} AND source_dataset_version <> #{datasetVersion} AND active = TRUE
            """)
    int deactivateOtherVersions(@Param("datasetName") String datasetName, @Param("datasetVersion") String datasetVersion);

    @Insert({"<script>", """
            INSERT INTO nutrition_foods (source_food_id, source_food_name, normalized_food_name, category,
                reference_basis, reference_amount, reference_unit, energy_kcal, carbohydrate_g, sugar_g, protein_g,
                fat_g, sodium_mg, source_dataset_name, source_dataset_version, source_institution,
                source_generated_date, active)
            VALUES
            """, "<foreach collection='foods' item='food' separator=','>", """
            (#{food.sourceFoodId}, #{food.sourceFoodName}, #{food.normalizedFoodName}, #{food.category},
                #{food.referenceBasis}, #{food.referenceAmount}, #{food.referenceUnit}, #{food.energyKcal},
                #{food.carbohydrateG}, #{food.sugarG}, #{food.proteinG}, #{food.fatG}, #{food.sodiumMg},
                #{food.sourceDatasetName}, #{food.sourceDatasetVersion}, #{food.sourceInstitution},
                #{food.sourceGeneratedDate}, TRUE)
            """, "</foreach>", """
            ON DUPLICATE KEY UPDATE source_food_name = VALUES(source_food_name),
                normalized_food_name = VALUES(normalized_food_name), category = VALUES(category),
                reference_basis = VALUES(reference_basis), reference_amount = VALUES(reference_amount),
                reference_unit = VALUES(reference_unit), energy_kcal = VALUES(energy_kcal),
                carbohydrate_g = VALUES(carbohydrate_g), sugar_g = VALUES(sugar_g), protein_g = VALUES(protein_g),
                fat_g = VALUES(fat_g), sodium_mg = VALUES(sodium_mg), source_institution = VALUES(source_institution),
                source_generated_date = VALUES(source_generated_date), active = TRUE
            """, "</script>"})
    int upsertBatch(@Param("foods") List<NutritionFood> foods);

    @Select("""
            SELECT id, source_food_id AS sourceFoodId, source_food_name AS sourceFoodName,
                normalized_food_name AS normalizedFoodName, category, reference_basis AS referenceBasis,
                reference_amount AS referenceAmount, reference_unit AS referenceUnit, energy_kcal AS energyKcal,
                carbohydrate_g AS carbohydrateG, sugar_g AS sugarG, protein_g AS proteinG, fat_g AS fatG,
                sodium_mg AS sodiumMg, source_dataset_name AS sourceDatasetName,
                source_dataset_version AS sourceDatasetVersion, source_institution AS sourceInstitution,
                source_generated_date AS sourceGeneratedDate, active
            FROM nutrition_foods WHERE active = TRUE AND normalized_food_name = #{normalizedFoodName}
            ORDER BY id
            """)
    List<NutritionFood> findActiveByNormalizedName(@Param("normalizedFoodName") String normalizedFoodName);

    @Select("""
            SELECT COUNT(*) FROM nutrition_foods
            WHERE source_dataset_name = #{datasetName} AND source_dataset_version = #{datasetVersion} AND active = TRUE
            """)
    long countActiveByDatasetVersion(@Param("datasetName") String datasetName, @Param("datasetVersion") String datasetVersion);
    @Select("""
            SELECT normalized_food_name FROM nutrition_foods
            WHERE active = TRUE AND normalized_food_name LIKE CONCAT('%', #{token}, '%')
            GROUP BY normalized_food_name ORDER BY normalized_food_name LIMIT 8
            """)
    List<String> findCandidateNamesByToken(@Param("token") String token);
}
