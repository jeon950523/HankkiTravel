package kr.hankkitravel.tourism.model;

/** Raw and normalized menu candidate held only while serving a live TourAPI request. */
public record TourismMenuCandidate(String rawMenuName, String normalizedMenuName, String sourceField) {
    public TourismMenuCandidate {
        if (blank(rawMenuName) || blank(normalizedMenuName) || blank(sourceField)) {
            throw new IllegalArgumentException("메뉴 후보 정보가 필요합니다.");
        }
    }
    private static boolean blank(String value) { return value == null || value.isBlank(); }
}
