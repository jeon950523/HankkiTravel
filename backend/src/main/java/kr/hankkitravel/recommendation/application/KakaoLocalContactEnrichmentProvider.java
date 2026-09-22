package kr.hankkitravel.recommendation.application;

import kr.hankkitravel.shared.geo.Coordinates;
import kr.hankkitravel.shared.integration.IntegrationException;

public final class KakaoLocalContactEnrichmentProvider implements ContactEnrichmentProvider {
    private final SearchSource source; private final KakaoLocalStrictMatcher matcher;
    public KakaoLocalContactEnrichmentProvider(SearchSource source, KakaoLocalStrictMatcher matcher) {
        this.source = source; this.matcher = matcher;
    }
    @Override public Result enrich(String name, String address, Coordinates coordinates) {
        try { return matcher.match(name, address, coordinates, source.search(name, coordinates)); }
        catch (IntegrationException exception) { return Result.unavailable(); }
    }
}
