package kr.hankkitravel.tourism.model;

/** Current TourAPI v4.4 content type contract, centralized for sync scopes. */
public enum TourismContentType {
    ATTRACTION("12"),
    LODGING("32"),
    RESTAURANT("39");

    private final String code;

    TourismContentType(String code) { this.code = code; }

    public String code() { return code; }
    public boolean isRestaurant() { return this == RESTAURANT; }
}
