package kr.hankkitravel.tourism.model;

import java.math.BigDecimal;
import java.util.Objects;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import kr.hankkitravel.shared.geo.Coordinates;

/** Tourism-owned local cache projection. sourceModifiedRaw stays lexical by design. */
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class TourismCachePlace {
    private Long id;
    private String contentId;
    private String contentTypeId;
    private String title;
    private String address;
    private String addressDetail;
    private String tel;
    private String zipcode;
    private String lDongRegnCd;
    private String lDongSignguCd;
    private BigDecimal longitude;
    private BigDecimal latitude;
    private String firstImage;
    private String firstImage2;
    private String cpyrhtDivCd;
    private String lclsSystm1;
    private String lclsSystm2;
    private String lclsSystm3;
    private String sourceCreatedRaw;
    private String sourceModifiedRaw;
    private boolean active;

    private TourismCachePlace(TourismPlace source) {
        this.contentId = required(source.contentId());
        this.contentTypeId = required(source.contentTypeId());
        this.title = required(source.title());
        this.address = blankToNull(source.address());
        this.addressDetail = blankToNull(source.addressDetail());
        this.tel = blankToNull(source.tel());
        this.zipcode = blankToNull(source.zipcode());
        this.lDongRegnCd = blankToNull(source.lDongRegnCd());
        this.lDongSignguCd = blankToNull(source.lDongSignguCd());
        this.longitude = source.coordinates() == null ? null : source.coordinates().longitude();
        this.latitude = source.coordinates() == null ? null : source.coordinates().latitude();
        this.firstImage = blankToNull(source.firstImage());
        this.firstImage2 = blankToNull(source.firstImage2());
        this.cpyrhtDivCd = blankToNull(source.cpyrhtDivCd());
        this.lclsSystm1 = blankToNull(source.lclsSystm1());
        this.lclsSystm2 = blankToNull(source.lclsSystm2());
        this.lclsSystm3 = blankToNull(source.lclsSystm3());
        this.sourceCreatedRaw = source.createdTime();
        this.sourceModifiedRaw = source.modifiedTime();
        this.active = true;
    }

    public static TourismCachePlace from(TourismPlace source) { return new TourismCachePlace(source); }

    public boolean sameProjection(TourismCachePlace other) {
        return Objects.equals(contentTypeId, other.contentTypeId) && Objects.equals(title, other.title)
                && Objects.equals(address, other.address) && Objects.equals(addressDetail, other.addressDetail)
                && Objects.equals(tel, other.tel) && Objects.equals(zipcode, other.zipcode)
                && Objects.equals(lDongRegnCd, other.lDongRegnCd) && Objects.equals(lDongSignguCd, other.lDongSignguCd)
                && decimalEquals(longitude, other.longitude) && decimalEquals(latitude, other.latitude)
                && Objects.equals(firstImage, other.firstImage) && Objects.equals(firstImage2, other.firstImage2)
                && Objects.equals(cpyrhtDivCd, other.cpyrhtDivCd) && Objects.equals(lclsSystm1, other.lclsSystm1)
                && Objects.equals(lclsSystm2, other.lclsSystm2) && Objects.equals(lclsSystm3, other.lclsSystm3)
                && Objects.equals(sourceCreatedRaw, other.sourceCreatedRaw)
                && Objects.equals(sourceModifiedRaw, other.sourceModifiedRaw);
    }

    public boolean hasKnownSourceVersion() { return sourceModifiedRaw != null && !sourceModifiedRaw.isBlank(); }

    public int compareSourceVersion(TourismCachePlace other) {
        return sourceModifiedRaw.compareTo(other.sourceModifiedRaw);
    }

    public boolean isRestaurant() { return TourismContentType.RESTAURANT.code().equals(contentTypeId); }

    private static boolean decimalEquals(BigDecimal left, BigDecimal right) {
        return left == null ? right == null : right != null && left.compareTo(right) == 0;
    }

    private static String required(String value) {
        var normalized = blankToNull(value);
        if (normalized == null) throw new IllegalArgumentException("관광지 식별자와 제목이 필요합니다.");
        return normalized;
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}
