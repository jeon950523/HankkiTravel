package kr.hankkitravel.nutrition.model;

public record NutritionMatchDecision(Long nutritionFoodId, NutritionMatchLevel matchLevel,
        String matchMethod, NutritionReviewState reviewState, String evidence) {
    public NutritionMatchDecision {
        if (matchLevel == null || matchMethod == null || matchMethod.isBlank() || reviewState == null) {
            throw new IllegalArgumentException("영양 매칭 결과 정보가 필요합니다.");
        }
        if ((matchLevel == NutritionMatchLevel.HIGH || matchLevel == NutritionMatchLevel.MEDIUM)
                && nutritionFoodId == null) throw new IllegalArgumentException("자동 활용 매칭에는 표준 음식 근거가 필요합니다.");
    }
}
