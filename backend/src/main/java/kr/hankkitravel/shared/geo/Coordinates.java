package kr.hankkitravel.shared.geo;

import java.math.BigDecimal;

public record Coordinates(BigDecimal longitude, BigDecimal latitude) {
    public Coordinates {
        if (longitude == null || latitude == null
                || longitude.abs().compareTo(BigDecimal.valueOf(180)) > 0
                || latitude.abs().compareTo(BigDecimal.valueOf(90)) > 0) {
            throw new IllegalArgumentException("유효한 WGS84 경도/위도가 필요합니다.");
        }
    }
}
