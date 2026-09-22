package kr.hankkitravel.tourism.model;

/** MVP legal-dong scopes. JEJU is intentionally split into Jeju City and Seogwipo. */
public enum TourismRegion {
    JEJU_CITY("50", "110"),
    SEOGWIPO("50", "130"),
    GYEONGJU("47", "130");

    private final String lDongRegnCd;
    private final String lDongSignguCd;

    TourismRegion(String lDongRegnCd, String lDongSignguCd) {
        this.lDongRegnCd = lDongRegnCd;
        this.lDongSignguCd = lDongSignguCd;
    }

    public String lDongRegnCd() { return lDongRegnCd; }
    public String lDongSignguCd() { return lDongSignguCd; }
}
