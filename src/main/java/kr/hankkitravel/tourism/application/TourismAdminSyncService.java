package kr.hankkitravel.tourism.application;

import java.time.Instant;
import java.time.LocalDate;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Executor;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.atomic.AtomicReference;
import kr.hankkitravel.tourism.model.TourismSyncRun;
import kr.hankkitravel.tourism.model.TourismSyncScope;
import kr.hankkitravel.tourism.model.TourismSyncScopeState;
import kr.hankkitravel.tourism.persistence.TourismSyncMapper;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;

/**
 * Operator dispatch boundary. It never retries automatically and accepts one scope at a time, so
 * a repeated click cannot create a retry storm or run competing snapshots.
 */
@Service
public class TourismAdminSyncService {
    private static final int RECENT_RUN_LIMIT = 100;
    private static final int FAILURE_LIMIT = 8;

    private final TourismSyncService sync;
    private final TourismSyncMapper runs;
    private final TourismAdminSyncSettings settings;
    private final Executor executor;
    private final AtomicReference<String> activeScopeKey = new AtomicReference<>();
    private final AtomicReference<String> lastFullSyncStatus = new AtomicReference<>();

    public TourismAdminSyncService(TourismSyncService sync, TourismSyncMapper runs,
            TourismAdminSyncSettings settings, @Qualifier("tourismAdminSyncExecutor") Executor executor) {
        this.sync = sync;
        this.runs = runs;
        this.settings = settings;
        this.executor = executor;
    }

    public TourismAdminSyncOverview overview() {
        var window = currentDayWindow();
        int usedCalls = runs.sumRemoteCallCount(window.from(), window.until());
        var recentRuns = runs.findRecentRuns(RECENT_RUN_LIMIT);
        var latestByScope = latestByScope(recentRuns);
        var checkpoints = checkpointsByScope(runs.findScopeStates());
        int remaining = Math.max(settings.dailyCallBudget() - usedCalls, 0);
        var statuses = TourismSyncScope.allMvpScopes().stream().map(scope -> {
            var latest = latestByScope.get(scope.key());
            var checkpoint = checkpoints.get(scope.key());
            return new TourismAdminScopeStatus(scope.key(), regionName(scope), contentTypeName(scope),
                    latest == null ? null : latest.getStatus(), latest == null ? null : latest.getStartedAt(),
                    checkpoint == null ? null : checkpoint.getLastSuccessfulSyncAt(),
                    latest == null ? 0 : latest.getRemoteCallCount(),
                    latest == null ? null : latest.getFailureCategory());
        }).toList();
        var failures = recentRuns.stream().filter(run -> "FAILED".equals(run.getStatus()) || "SUSPICIOUS".equals(run.getStatus())).limit(FAILURE_LIMIT)
                .map(run -> new TourismAdminFailureSummary(run.getScopeKey(), run.getStartedAt(),
                        run.getFailureCategory(), run.getRemoteCallCount())).toList();
        return new TourismAdminSyncOverview(settings.operationallyEnabled(), settings.operationZone().getId(),
                usedCalls, settings.dailyCallBudget(), remaining, budgetStatus(usedCalls), activeScopeKey.get(), lastFullSyncStatus.get(),
                recentRuns.isEmpty() ? null : lastSyncedAt(recentRuns.getFirst()), statuses, failures);
    }

    public TourismAdminSyncDispatch request(String scopeKey) {
        final TourismSyncScope scope;
        if (TourismSyncScope.isAllMvpScopesKey(scopeKey)) {
            return requestAllMvpScopes();
        }
        try {
            scope = TourismSyncScope.fromKey(scopeKey);
        } catch (IllegalArgumentException exception) {
            return new TourismAdminSyncDispatch(false, "INVALID_SCOPE", scopeKey);
        }
        if (!settings.operationallyEnabled()) {
            return new TourismAdminSyncDispatch(false, "OPERATOR_CONFIGURATION_REQUIRED", scope.key());
        }
        if (!activeScopeKey.compareAndSet(null, scope.key())) {
            return new TourismAdminSyncDispatch(false, "SYNC_ALREADY_RUNNING", scope.key());
        }
        int availableCalls = remainingCalls();
        if (availableCalls <= 0) {
            activeScopeKey.compareAndSet(scope.key(), null);
            return new TourismAdminSyncDispatch(false, "CALL_BUDGET_EXHAUSTED", scope.key());
        }
        try {
            executor.execute(() -> synchronizeWithCurrentAllowance(scope));
            return new TourismAdminSyncDispatch(true, "QUEUED", scope.key());
        } catch (RejectedExecutionException exception) {
            activeScopeKey.compareAndSet(scope.key(), null);
            return new TourismAdminSyncDispatch(false, "SYNC_ALREADY_RUNNING", scope.key());
        }
    }

    private TourismAdminSyncDispatch requestAllMvpScopes() {
        String scopeKey = TourismSyncScope.ALL_MVP_SCOPES_KEY;
        if (!settings.operationallyEnabled()) {
            return new TourismAdminSyncDispatch(false, "OPERATOR_CONFIGURATION_REQUIRED", scopeKey);
        }
        if (!activeScopeKey.compareAndSet(null, scopeKey)) {
            return new TourismAdminSyncDispatch(false, "SYNC_ALREADY_RUNNING", scopeKey);
        }
        int availableCalls = remainingCalls();
        if (availableCalls <= 0) {
            activeScopeKey.compareAndSet(scopeKey, null);
            return new TourismAdminSyncDispatch(false, "CALL_BUDGET_EXHAUSTED", scopeKey);
        }
        try {
            executor.execute(this::synchronizeAllWithCurrentAllowance);
            return new TourismAdminSyncDispatch(true, "QUEUED", scopeKey);
        } catch (RejectedExecutionException exception) {
            activeScopeKey.compareAndSet(scopeKey, null);
            return new TourismAdminSyncDispatch(false, "SYNC_ALREADY_RUNNING", scopeKey);
        }
    }
    private void synchronizeWithCurrentAllowance(TourismSyncScope scope) {
        try {
            int availableCalls = remainingCalls();
            if (availableCalls > 0) sync.synchronize(scope, availableCalls);
        } finally {
            activeScopeKey.compareAndSet(scope.key(), null);
        }
    }

    private void synchronizeAllWithCurrentAllowance() {
        try {
            int availableCalls = remainingCalls();
            if (availableCalls > 0) {
                lastFullSyncStatus.set(sync.synchronizeAllMvpScopes(availableCalls).status().name());
            }
        } finally {
            activeScopeKey.compareAndSet(TourismSyncScope.ALL_MVP_SCOPES_KEY, null);
        }
    }
    private Instant lastSyncedAt(TourismSyncRun run) {


        return run.getCompletedAt() == null ? run.getStartedAt() : run.getCompletedAt();
    }


    private int remainingCalls() {
        var window = currentDayWindow();
        return Math.max(settings.dailyCallBudget() - runs.sumRemoteCallCount(window.from(), window.until()), 0);
    }

    private DayWindow currentDayWindow() {
        var today = LocalDate.now(settings.operationZone());
        return new DayWindow(today.atStartOfDay(settings.operationZone()).toInstant(),
                today.plusDays(1).atStartOfDay(settings.operationZone()).toInstant());
    }

    private Map<String, TourismSyncRun> latestByScope(List<TourismSyncRun> recentRuns) {
        var latest = new HashMap<String, TourismSyncRun>();
        recentRuns.stream().sorted(Comparator.comparing(TourismSyncRun::getStartedAt).reversed())
                .forEach(run -> latest.putIfAbsent(run.getScopeKey(), run));
        return latest;
    }

    private Map<String, TourismSyncScopeState> checkpointsByScope(List<TourismSyncScopeState> states) {
        var checkpoints = new HashMap<String, TourismSyncScopeState>();
        for (var state : states) checkpoints.put(state.getScopeKey(), state);
        return checkpoints;
    }

    private String budgetStatus(int usedCalls) {
        if (!settings.operationallyEnabled()) return "OPERATOR_CONFIGURATION_REQUIRED";
        return usedCalls >= settings.dailyCallBudget() ? "EXHAUSTED" : "AVAILABLE";
    }

    private String regionName(TourismSyncScope scope) {
        return switch (scope.region()) {
            case JEJU_CITY -> "제주시";
            case SEOGWIPO -> "서귀포시";
            case GYEONGJU -> "경주시";
        };
    }

    private String contentTypeName(TourismSyncScope scope) {
        return switch (scope.contentType()) {
            case ATTRACTION -> "관광지";
            case LODGING -> "숙박";
            case RESTAURANT -> "음식점";
        };
    }

    private record DayWindow(Instant from, Instant until) {}
}
