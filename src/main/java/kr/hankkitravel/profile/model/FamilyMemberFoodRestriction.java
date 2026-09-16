package kr.hankkitravel.profile.model;

public record FamilyMemberFoodRestriction(long familyMemberId, String restrictionType,
        String normalizedValue, String displayValue) { }
