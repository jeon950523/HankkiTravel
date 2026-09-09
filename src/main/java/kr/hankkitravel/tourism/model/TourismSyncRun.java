package kr.hankkitravel.tourism.model;

import java.time.Instant;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class TourismSyncRun {
    private Long id;
    private String scopeKey;
    private String lDongRegnCd;
    private String lDongSignguCd;
    private String contentTypeId;
    private Instant startedAt;
    private Instant completedAt;
    private String status;
    private int remoteCallCount;
    private int fetchedCount;
    private int insertedCount;
    private int updatedCount;
    private int unchangedCount;
    private int deactivatedCount;
    private int failedCount;
    private String failureCategory;

    public TourismSyncRun(TourismSyncScope scope, Instant startedAt) {
        this.scopeKey = scope.key();
        this.lDongRegnCd = scope.lDongRegnCd();
        this.lDongSignguCd = scope.lDongSignguCd();
        this.contentTypeId = scope.contentTypeId();
        this.startedAt = startedAt;
        this.status = TourismSyncStatus.RUNNING.name();
    }

    public void complete(TourismSyncStatus status, TourismSyncCounters counters,
            String failureCategory, Instant completedAt) {
        if (status == TourismSyncStatus.RUNNING) throw new IllegalArgumentException("A run must finish");
        this.status = status.name();
        this.remoteCallCount = counters.remoteCallCount();
        this.fetchedCount = counters.fetchedCount();
        this.insertedCount = counters.insertedCount();
        this.updatedCount = counters.updatedCount();
        this.unchangedCount = counters.unchangedCount();
        this.deactivatedCount = counters.deactivatedCount();
        this.failedCount = counters.failedCount();
        this.failureCategory = failureCategory;
        this.completedAt = completedAt;
    }
}
