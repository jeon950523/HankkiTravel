package kr.hankkitravel.profile.model;

public record FamilyProfileId(long value) {
    public FamilyProfileId {
        if (value <= 0) throw new IllegalArgumentException("양수 내부 ID가 필요합니다.");
    }
}
