package kr.hankkitravel.profile.fixture;

// Deliberately invalid dependency: verifies the architecture rule rejects it.
public class ForbiddenController {
    private kr.hankkitravel.profile.persistence.FamilyProfileMapper dependency;
}
