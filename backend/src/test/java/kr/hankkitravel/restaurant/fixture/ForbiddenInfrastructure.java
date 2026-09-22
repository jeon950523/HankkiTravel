package kr.hankkitravel.restaurant.fixture;

// Deliberately invalid dependency: verifies the architecture rule rejects it.
public class ForbiddenInfrastructure {
    private kr.hankkitravel.tourism.persistence.StoredTourismPlaceMapper dependency;
}
