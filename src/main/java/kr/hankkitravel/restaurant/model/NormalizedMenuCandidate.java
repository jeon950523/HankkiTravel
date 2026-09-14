package kr.hankkitravel.restaurant.model;

public record NormalizedMenuCandidate(String rawMenuName, String normalizedMenuName, String sourceField) {
    public NormalizedMenuCandidate {
        if (rawMenuName == null || rawMenuName.isBlank() || normalizedMenuName == null || normalizedMenuName.isBlank()
                || sourceField == null || sourceField.isBlank()) throw new IllegalArgumentException("메뉴 원문과 정규화 이름이 필요합니다.");
    }
}
