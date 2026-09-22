package kr.hankkitravel.nutrition.model;

/** Request-scoped match result. It is deliberately not a persistence entity. */
public record MenuNutritionMatch(String rawMenuName, String normalizedMenuName, String sourceField,
        NutritionMatchDecision decision, String ruleVersion, NutritionFood referenceFood) {
    public MenuNutritionMatch {
        if (blank(rawMenuName) || blank(normalizedMenuName) || blank(sourceField)
                || decision == null || blank(ruleVersion)) {
            throw new IllegalArgumentException("런타임 메뉴 영양 매칭 정보가 필요합니다.");
        }
        boolean automatic = decision.matchLevel() == NutritionMatchLevel.HIGH
                || decision.matchLevel() == NutritionMatchLevel.MEDIUM;
        if (automatic && referenceFood == null) {
            throw new IllegalArgumentException("자동 활용 매칭에는 표준 음식 근거가 필요합니다.");
        }
        if (!automatic && referenceFood != null) {
            throw new IllegalArgumentException("근거 부족 매칭에는 영양 기준값을 연결하지 않습니다.");
        }
    }

    private static boolean blank(String value) { return value == null || value.isBlank(); }
}
