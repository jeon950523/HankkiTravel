package kr.hankkitravel.tourism.model;

public record TourismSyncCounters(int remoteCallCount, int fetchedCount, int insertedCount,
        int updatedCount, int unchangedCount, int deactivatedCount, int failedCount) {
    public TourismSyncCounters {
        if (remoteCallCount < 0 || fetchedCount < 0 || insertedCount < 0 || updatedCount < 0
                || unchangedCount < 0 || deactivatedCount < 0 || failedCount < 0) {
            throw new IllegalArgumentException("Sync counters cannot be negative");
        }
    }

    public static TourismSyncCounters failed(int remoteCalls, int fetched) {
        return new TourismSyncCounters(remoteCalls, fetched, 0, 0, 0, 0, 1);
    }
}
