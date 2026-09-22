package kr.hankkitravel.profile.model.fixture;

// Deliberately invalid dependency: verifies the architecture rule rejects it.
public class ForbiddenPersistence {
    private kr.hankkitravel.profile.persistence.FamilyProfileMapper dependency;
}
