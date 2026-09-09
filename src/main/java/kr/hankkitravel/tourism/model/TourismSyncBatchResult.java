package kr.hankkitravel.tourism.model;

import java.util.List;

/** Aggregates independently committed scope runs; successful scope caches are never rolled back. */
public record TourismSyncBatchResult(TourismSyncBatchStatus status, List<TourismSyncResult> scopeResults) {
    public TourismSyncBatchResult {
        if (scopeResults == null || scopeResults.isEmpty()) {
            throw new IllegalArgumentException("전체 동기화 결과에는 Scope 결과가 필요합니다.");
        }
        scopeResults = List.copyOf(scopeResults);
    }

    public static TourismSyncBatchResult from(List<TourismSyncResult> results) {
        long successful = results.stream().filter(TourismSyncResult::successful).count();
        var status = successful == results.size() ? TourismSyncBatchStatus.SUCCESS
                : successful > 0 ? TourismSyncBatchStatus.PARTIAL_SUCCESS : TourismSyncBatchStatus.FAILED;
        return new TourismSyncBatchResult(status, results);
    }
}
