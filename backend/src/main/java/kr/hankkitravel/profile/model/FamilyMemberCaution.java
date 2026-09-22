package kr.hankkitravel.profile.model;

/** A non-medical meal consideration selected for one family member. */
public record FamilyMemberCaution(long familyMemberId, String caution) {
    public FamilyMemberCaution {
        if (familyMemberId <= 0 || caution == null || caution.isBlank()) {
            throw new IllegalArgumentException("구성원 주의요소를 확인하세요.");
        }
    }
}
