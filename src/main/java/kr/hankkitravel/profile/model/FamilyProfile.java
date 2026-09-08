package kr.hankkitravel.profile.model;

import java.time.Instant;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import kr.hankkitravel.identity.model.ProfileOwnership;

@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class FamilyProfile {
    private Long id;
    private Long ownerUserId;
    private Long ownerGuestId;
    private String name;
    private Instant createdAt;
    private Instant updatedAt;

    public FamilyProfile(ProfileOwnership owner, String name) {
        java.util.Objects.requireNonNull(owner);
        if (name == null || name.isBlank()) throw new IllegalArgumentException("프로필 이름이 필요합니다.");
        this.ownerUserId = owner.userId();
        this.ownerGuestId = owner.guestId();
        this.name = name;
    }

    public ProfileOwnership ownership() { return new ProfileOwnership(ownerUserId, ownerGuestId); }
}
