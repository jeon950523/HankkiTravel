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
    private String transportMode;
    private String parkingPreference;
    private String walkingBurdenPreference;
    private String transferPreference;
    private boolean stairsAvoidance;
    private Instant createdAt;
    private Instant updatedAt;

    public FamilyProfile(ProfileOwnership owner, String name, String transportMode, String parkingPreference,
            String walkingBurdenPreference, String transferPreference, boolean stairsAvoidance) {
        java.util.Objects.requireNonNull(owner);
        this.ownerUserId = owner.userId();
        this.ownerGuestId = owner.guestId();
        change(name, transportMode, parkingPreference, walkingBurdenPreference, transferPreference, stairsAvoidance);
    }

    public void change(String name, String transportMode, String parkingPreference, String walkingBurdenPreference,
            String transferPreference, boolean stairsAvoidance) {
        if (name == null || name.isBlank() || blank(transportMode) || blank(parkingPreference)
                || blank(walkingBurdenPreference) || blank(transferPreference)) {
            throw new IllegalArgumentException("가족 프로필의 필수 항목을 확인하세요.");
        }
        this.name = name.trim();
        this.transportMode = transportMode;
        this.parkingPreference = parkingPreference;
        this.walkingBurdenPreference = walkingBurdenPreference;
        this.transferPreference = transferPreference;
        this.stairsAvoidance = stairsAvoidance;
    }

    public ProfileOwnership ownership() { return new ProfileOwnership(ownerUserId, ownerGuestId); }
    private static boolean blank(String value) { return value == null || value.isBlank(); }
}
