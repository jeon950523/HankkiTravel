package kr.hankkitravel.tourism.model;

public record TourismSyncResult(TourismSyncScope scope, TourismSyncStatus status,
        TourismSyncCounters counters, String failureCategory) {
    public boolean successful() { return status == TourismSyncStatus.SUCCESS; }
}
