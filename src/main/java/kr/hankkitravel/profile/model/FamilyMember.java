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
    private Instant createdAt;
    private Instant updatedAt;

    public FamilyMember(FamilyProfileId profileId, String nickname, int sortOrder) {
        if (nickname == null || nickname.isBlank() || sortOrder < 0) throw new IllegalArgumentException("구성원 이름과 순서를 확인하세요.");
        this.profileId = java.util.Objects.requireNonNull(profileId).value();
        this.nickname = nickname;
        this.sortOrder = sortOrder;
    }
}
