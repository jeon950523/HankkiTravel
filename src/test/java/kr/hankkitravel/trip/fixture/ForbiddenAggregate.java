package kr.hankkitravel.trip.fixture;

// Deliberately invalid dependency: verifies the architecture rule rejects it.
public class ForbiddenAggregate {
    private kr.hankkitravel.profile.model.FamilyProfile dependency;
}
