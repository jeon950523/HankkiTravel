package kr.hankkitravel.profile.application;

import java.util.List;

/** Read model for a guest-owned profile. It contains no authentication secret. */
public record FamilyProfileSnapshot(long profileId, String name, String transportMode, String parkingPreference,
        String walkingBurdenPreference, String transferPreference, boolean stairsAvoidance, List<Member> members) {
    public FamilyProfileSnapshot { members = List.copyOf(members); }

    public record Member(long memberId, String nickname, int continuousWalkingMinutes, String stairsPreference,
            List<String> mealCautions, boolean bloodSugarCare, List<String> allergenRestrictions,
            List<String> avoidedFoods) {
        public Member {
            mealCautions = List.copyOf(mealCautions);
            allergenRestrictions = List.copyOf(allergenRestrictions);
            avoidedFoods = List.copyOf(avoidedFoods);
        }
    }
}
