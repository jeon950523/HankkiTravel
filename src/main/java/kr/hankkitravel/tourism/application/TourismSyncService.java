package kr.hankkitravel.tourism.application;

import java.time.Instant;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Pattern;
import kr.hankkitravel.shared.integration.IntegrationException;
import kr.hankkitravel.tourism.model.TourApiPage;
import kr.hankkitravel.tourism.model.TourismCachePlace;
import kr.hankkitravel.tourism.model.TourismPlace;
import kr.hankkitravel.tourism.model.TourismPlaceCached;
import kr.hankkitravel.tourism.model.TourismPlaceContentTypeChanged;
import kr.hankkitravel.tourism.model.TourismSyncCounters;
import kr.hankkitravel.tourism.model.TourismSyncBatchResult;
import kr.hankkitravel.tourism.model.TourismSyncResult;
import kr.hankkitravel.tourism.model.TourismSyncRun;
import kr.hankkitravel.tourism.model.TourismSyncScope;
import kr.hankkitravel.tourism.model.TourismSyncStatus;
import kr.hankkitravel.tourism.persistence.TourismCacheMapper;
import kr.hankkitravel.tourism.persistence.TourismSyncMapper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Fetches a complete remote scope outside a transaction, then atomically applies differential writes.
 * The current official contract does not state a timezone for modifiedtime, so it is compared lexically.
 */
@Service
public class TourismSyncService {
    private static final Pattern TOUR_TIME = Pattern.compile("\\d{14}");
    public static final double DEFAULT_SUSPICIOUS_SNAPSHOT_RETAIN_RATIO = 0.5;

    private final TourismSnapshotSource source;
    private final TourismCacheMapper places;
    private final TourismSyncMapper runs;
    private final ApplicationEventPublisher events;
    private final TransactionTemplate transactions;
    private final int pageSize;
    private final int maxPages;
    private final double suspiciousSnapshotRetainRatio;

    @Autowired
    public TourismSyncService(TourismSnapshotSource source, TourismCacheMapper places, TourismSyncMapper runs,
            ApplicationEventPublisher events, PlatformTransactionManager transactionManager,
            @Value("${hankki.tourism-sync.page-size:10}") int pageSize,
            @Value("${hankki.tourism-sync.max-pages:1000}") int maxPages,
            @Value("${hankki.tourism-sync.suspicious-snapshot-retain-ratio}") double suspiciousSnapshotRetainRatio) {
        if (pageSize < 1 || maxPages < 1) throw new IllegalArgumentException("Invalid sync paging config");
        if (suspiciousSnapshotRetainRatio <= 0 || suspiciousSnapshotRetainRatio > 1) {
            throw new IllegalArgumentException("이상 스냅샷 보존 비율은 0보다 크고 1 이하여야 합니다.");
        }
        this.source = source;
        this.places = places;
        this.runs = runs;
        this.events = events;
        this.transactions = new TransactionTemplate(transactionManager);
        this.suspiciousSnapshotRetainRatio = suspiciousSnapshotRetainRatio;
        this.pageSize = pageSize;
        this.maxPages = maxPages;
    }

    public TourismSyncService(TourismSnapshotSource source, TourismCacheMapper places, TourismSyncMapper runs,
            ApplicationEventPublisher events, PlatformTransactionManager transactionManager, int pageSize, int maxPages) {
        this(source, places, runs, events, transactionManager, pageSize, maxPages,
                DEFAULT_SUSPICIOUS_SNAPSHOT_RETAIN_RATIO);
    }

    public List<TourismSyncResult> synchronizeAllMvpScopes() {
        return TourismSyncScope.allMvpScopes().stream().map(this::synchronize).toList();
    }

    public TourismSyncBatchResult synchronizeAllMvpScopes(int maximumRemoteCalls) {
        if (maximumRemoteCalls < 0) throw new IllegalArgumentException("원격 호출 한도는 음수일 수 없습니다.");
        var results = new ArrayList<TourismSyncResult>();
        int remainingCalls = maximumRemoteCalls;
        for (var scope : TourismSyncScope.allMvpScopes()) {
            var result = synchronize(scope, remainingCalls);
            results.add(result);
            remainingCalls = Math.max(remainingCalls - result.counters().remoteCallCount(), 0);
        }
        return TourismSyncBatchResult.from(results);
    }

    public TourismSyncResult synchronize(TourismSyncScope scope) {
        return synchronize(scope, Integer.MAX_VALUE);
    }

    /**
     * The operator surface supplies the remaining daily allowance. The ordinary P1.1 boundary
     * remains unbounded by an operator budget, while an interrupted budgeted snapshot applies no
     * partial writes because fetching finishes before the transaction starts.
     */
    public TourismSyncResult synchronize(TourismSyncScope scope, int maximumRemoteCalls) {
        if (maximumRemoteCalls < 0) throw new IllegalArgumentException("원격 호출 한도는 음수일 수 없습니다.");
        var run = new TourismSyncRun(scope, Instant.now());
        runs.insertRun(run);
        int remoteCalls = 0;
        int fetched = 0;
        try {
            var snapshot = new ArrayList<TourismCachePlace>();
            var allContentIds = new HashSet<String>();
            var pageFingerprints = new HashSet<String>();
            int expectedTotal = -1;
            for (int pageNo = 1; ; pageNo++) {
                if (pageNo > maxPages) throw new SnapshotException("INCOMPLETE_PAGINATION");
                if (remoteCalls >= maximumRemoteCalls) throw new SnapshotException("CALL_BUDGET_EXHAUSTED");
                remoteCalls++;
                TourApiPage page = source.fetch(scope, pageNo, pageSize);
                validatePage(scope, page, pageNo, expectedTotal, allContentIds, pageFingerprints);
                if (expectedTotal < 0) expectedTotal = page.totalCount();
                for (TourismPlace item : page.items()) {
                    snapshot.add(TourismCachePlace.from(item));
                }
                fetched = snapshot.size();
                if (fetched > expectedTotal) throw new SnapshotException("INCOMPLETE_PAGINATION");
                if (fetched == expectedTotal) break;
                if (page.items().isEmpty()) throw new SnapshotException("INCOMPLETE_PAGINATION");
            }
            if (fetched != expectedTotal) throw new SnapshotException("INCOMPLETE_PAGINATION");
            int completedCalls = remoteCalls;
            int completedFetched = fetched;
            if (isSuspiciousSnapshot(scope, completedFetched)) {
                return suspicious(run, scope, completedCalls, completedFetched);
            }
            var counters = transactions.execute(status -> apply(scope, snapshot, completedCalls, completedFetched, run));
            return new TourismSyncResult(scope, TourismSyncStatus.SUCCESS, counters, null);
        } catch (SnapshotException exception) {
            return fail(run, scope, remoteCalls, fetched, exception.category);
        } catch (IntegrationException exception) {
            return fail(run, scope, remoteCalls, fetched, exception.failure().name());
        } catch (RuntimeException exception) {
            return fail(run, scope, remoteCalls, fetched, "DATABASE_OR_UNEXPECTED");
        }
    }

    private TourismSyncCounters apply(TourismSyncScope scope, List<TourismCachePlace> snapshot,
            int remoteCalls, int fetched, TourismSyncRun run) {
        int inserted = 0;
        int updated = 0;
        int unchanged = 0;
        int deactivated = 0;
        var seen = new HashSet<String>();
        for (var incoming : snapshot) {
            seen.add(incoming.getContentId());
            var existing = places.findByContentId(incoming.getContentId());
            if (existing == null) {
                places.insert(incoming);
                inserted++;
                publishRestaurant(incoming.getId(), scope);
                continue;
            }
            if (!existing.hasKnownSourceVersion()) {
                if (!existing.isActive()) {
                    places.update(incoming);
                    updated++;
                    publishCacheMutation(existing, incoming, scope);
                } else {
                    unchanged++;
                }
                continue;
            }
            if (existing.compareSourceVersion(incoming) > 0) {
                unchanged++;
                continue;
            }
            if (!existing.isActive() || !existing.sameProjection(incoming)) {
                places.update(incoming);
                updated++;
                publishCacheMutation(existing, incoming, scope);
            } else {
                unchanged++;
            }
        }
        for (var existing : places.findActiveByScope(scope.lDongRegnCd(), scope.lDongSignguCd(), scope.contentTypeId())) {
            if (!seen.contains(existing.getContentId())) deactivated += places.deactivate(existing.getId());
        }
        var counters = new TourismSyncCounters(remoteCalls, fetched, inserted, updated, unchanged, deactivated, 0);
        run.complete(TourismSyncStatus.SUCCESS, counters, null, Instant.now());
        runs.completeRun(run);
        runs.recordSuccessfulScope(scope.key(), scope.lDongRegnCd(), scope.lDongSignguCd(),
                scope.contentTypeId(), run.getCompletedAt(), run.getId());
        return counters;
    }

    private void publishRestaurant(long tourismPlaceId, TourismSyncScope scope) {
        if (scope.contentType().isRestaurant()) {
            events.publishEvent(new TourismPlaceCached(tourismPlaceId, scope.contentType()));
        }
    }

    private void publishCacheMutation(TourismCachePlace existing, TourismCachePlace incoming, TourismSyncScope scope) {
        if (!existing.getContentTypeId().equals(incoming.getContentTypeId())) {
            events.publishEvent(new TourismPlaceContentTypeChanged(existing.getId(), existing.getContentTypeId(),
                    incoming.getContentTypeId()));
        }
        publishRestaurant(existing.getId(), scope);
    }

    private TourismSyncResult fail(TourismSyncRun run, TourismSyncScope scope, int remoteCalls,
            int fetched, String category) {
        var counters = TourismSyncCounters.failed(remoteCalls, fetched);
        run.complete(TourismSyncStatus.FAILED, counters, category, Instant.now());
        runs.completeRun(run);
        return new TourismSyncResult(scope, TourismSyncStatus.FAILED, counters, category);
    }

    private boolean isSuspiciousSnapshot(TourismSyncScope scope, int fetched) {
        Integer previousFetched = runs.findLastSuccessfulFetchedCount(scope.key());
        return previousFetched != null && previousFetched > 0
                && (double) fetched / previousFetched < suspiciousSnapshotRetainRatio;
    }

    private TourismSyncResult suspicious(TourismSyncRun run, TourismSyncScope scope, int remoteCalls, int fetched) {
        var counters = TourismSyncCounters.suspicious(remoteCalls, fetched);
        run.complete(TourismSyncStatus.SUSPICIOUS, counters, "SUSPICIOUS_SNAPSHOT_SHRINK", Instant.now());
        runs.completeRun(run);
        return new TourismSyncResult(scope, TourismSyncStatus.SUSPICIOUS, counters, "SUSPICIOUS_SNAPSHOT_SHRINK");
    }

    private void validatePage(TourismSyncScope scope, TourApiPage page, int requestedPage,
            int expectedTotal, Set<String> allContentIds, Set<String> fingerprints) {
        if (page.pageNo() != requestedPage || (expectedTotal >= 0 && page.totalCount() != expectedTotal)) {
            throw new SnapshotException("INCOMPLETE_PAGINATION");
        }
        var pageIds = new ArrayList<String>();
        for (var item : page.items()) {
            validateItem(scope, item);
            if (!allContentIds.add(item.contentId())) throw new SnapshotException("INCOMPLETE_PAGINATION");
            pageIds.add(item.contentId());
        }
        if (!fingerprints.add(String.join("|", pageIds))) throw new SnapshotException("INCOMPLETE_PAGINATION");
    }

    private void validateItem(TourismSyncScope scope, TourismPlace item) {
        if (item == null || blank(item.contentId()) || blank(item.contentTypeId()) || blank(item.title())
                || !scope.contentTypeId().equals(item.contentTypeId())
                || !scope.lDongRegnCd().equals(item.lDongRegnCd())
                || !scope.lDongSignguCd().equals(item.lDongSignguCd())
                || item.modifiedTime() == null || !TOUR_TIME.matcher(item.modifiedTime()).matches()) {
            throw new SnapshotException("MALFORMED_ITEM");
        }
    }

    private boolean blank(String value) { return value == null || value.isBlank(); }

    private static final class SnapshotException extends RuntimeException {
        private final String category;
        private SnapshotException(String category) { this.category = category; }
    }
}
