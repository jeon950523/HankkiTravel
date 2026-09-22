package kr.hankkitravel.profile.model;

import java.time.Instant;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class FamilyMember {
    private Long id;
    private Long profileId;
    private String nickname;
    private int sortOrder;
    private int continuousWalkingMinutes;
    private String stairsPreference;
    private Instant createdAt;
    private Instant updatedAt;

    public FamilyMember(FamilyProfileId profileId, String nickname, int sortOrder, int continuousWalkingMinutes,
            String stairsPreference) {
        if (nickname == null || nickname.isBlank() || sortOrder < 0 || continuousWalkingMinutes < 0
                || stairsPreference == null || stairsPreference.isBlank()) {
            throw new IllegalArgumentException("구성원 정보와 순서를 확인하세요.");
        }
        this.profileId = java.util.Objects.requireNonNull(profileId).value();
        this.nickname = nickname.trim();
        this.sortOrder = sortOrder;
        this.continuousWalkingMinutes = continuousWalkingMinutes;
        this.stairsPreference = stairsPreference;
    }
}
