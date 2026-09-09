package kr.hankkitravel.tourism.model;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/** One independently recoverable remote snapshot: legal-dong region plus content type. */
public record TourismSyncScope(TourismRegion region, TourismContentType contentType) {
    public TourismSyncScope {
        Objects.requireNonNull(region, "region");
        Objects.requireNonNull(contentType, "contentType");
    }

    public String key() { return region.name() + "_" + contentType.name(); }
    public String lDongRegnCd() { return region.lDongRegnCd(); }
    public String lDongSignguCd() { return region.lDongSignguCd(); }
    public String contentTypeId() { return contentType.code(); }

    public static List<TourismSyncScope> allMvpScopes() {
        var scopes = new ArrayList<TourismSyncScope>();
        for (var region : TourismRegion.values()) {
            for (var contentType : TourismContentType.values()) {
                scopes.add(new TourismSyncScope(region, contentType));
            }
        }
        return List.copyOf(scopes);
}
    }
