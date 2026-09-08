package kr.hankkitravel.tourism.model;

import java.time.Instant;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import kr.hankkitravel.shared.geo.Coordinates;

@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class StoredTourismPlace {
    private Long id;
    private String contentId;
    private String contentTypeId;
    private String title;
    private String lDongRegnCd;
    private String lDongSignguCd;
    private java.math.BigDecimal longitude;
    private java.math.BigDecimal latitude;
    private Instant sourceModifiedAt;
    private boolean active;
    private Instant createdAt;
    private Instant updatedAt;

    public StoredTourismPlace(String contentId, String contentTypeId, String title,
            String lDongRegnCd, String lDongSignguCd, Coordinates coordinates, Instant sourceModifiedAt) {
        if (contentId == null || contentId.isBlank() || contentTypeId == null || contentTypeId.isBlank()
                || title == null || title.isBlank()) throw new IllegalArgumentException("관광지 식별자와 이름이 필요합니다.");
        this.contentId = contentId;
        this.contentTypeId = contentTypeId;
        this.title = title;
        this.lDongRegnCd = lDongRegnCd;
        this.lDongSignguCd = lDongSignguCd;
        this.longitude = coordinates == null ? null : coordinates.longitude();
        this.latitude = coordinates == null ? null : coordinates.latitude();
        this.sourceModifiedAt = sourceModifiedAt;
        this.active = true;
    }
}
