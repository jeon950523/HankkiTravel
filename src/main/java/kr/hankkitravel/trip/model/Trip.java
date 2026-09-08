package kr.hankkitravel.trip.model;

import java.time.Instant;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import kr.hankkitravel.profile.model.FamilyProfileId;

@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Trip {
    private Long id;
    private Long profileId;
    private String status;
    private Instant createdAt;
    private Instant updatedAt;

    public Trip(FamilyProfileId profileId, String status) {
        if (status == null || status.isBlank()) throw new IllegalArgumentException("여행 상태가 필요합니다.");
        this.profileId = java.util.Objects.requireNonNull(profileId).value();
        this.status = status;
    }
}
