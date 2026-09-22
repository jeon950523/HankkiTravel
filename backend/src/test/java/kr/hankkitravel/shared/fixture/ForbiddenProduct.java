package kr.hankkitravel.shared.fixture;

// Deliberately invalid dependency: verifies the architecture rule rejects it.
public class ForbiddenProduct {
    private kr.hankkitravel.identity.model.User dependency;
}
