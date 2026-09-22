package kr.hankkitravel.tourism.model;

import java.time.Instant;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/** Persisted successful checkpoint used by the operator-only sync overview. */
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class TourismSyncScopeState {
    private String scopeKey;
    private Instant lastSuccessfulSyncAt;
    private long lastSuccessfulRunId;
}
