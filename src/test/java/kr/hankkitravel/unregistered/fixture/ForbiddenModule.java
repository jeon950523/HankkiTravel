package kr.hankkitravel.unregistered.fixture;

// Deliberately invalid dependency: verifies the architecture rule rejects it.
public class ForbiddenModule {
    private kr.hankkitravel.shared.geo.Coordinates dependency;
}
