package kr.hankkitravel.nutrition.persistence;

import kr.hankkitravel.nutrition.model.NutritionFood;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

@Mapper
public interface NutritionMatchingMapper {
    @Select("""
            SELECT id, source_food_id AS sourceFoodId, source_food_name AS sourceFoodName,
                normalized_food_name AS normalizedFoodName, category, reference_basis AS referenceBasis,
                reference_amount AS referenceAmount, reference_unit AS referenceUnit, energy_kcal AS energyKcal,
                carbohydrate_g AS carbohydrateG, sugar_g AS sugarG, protein_g AS proteinG, fat_g AS fatG,
                sodium_mg AS sodiumMg, source_dataset_name AS sourceDatasetName,
                source_dataset_version AS sourceDatasetVersion, source_institution AS sourceInstitution,
                source_generated_date AS sourceGeneratedDate, active
            FROM nutrition_foods WHERE id = #{nutritionFoodId} AND active = TRUE
            """)
    NutritionFood findActiveReference(@Param("nutritionFoodId") long nutritionFoodId);
}
