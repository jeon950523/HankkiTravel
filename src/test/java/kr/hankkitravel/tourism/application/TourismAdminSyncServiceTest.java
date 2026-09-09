package kr.hankkitravel.tourism.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

import java.time.Instant;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Executor;
import kr.hankkitravel.tourism.model.TourismSyncCounters;
import kr.hankkitravel.tourism.model.TourismSyncRun;
import kr.hankkitravel.tourism.model.TourismSyncScope;
import kr.hankkitravel.tourism.model.TourismSyncStatus;
import kr.hankkitravel.tourism.persistence.TourismSyncMapper;
import org.junit.jupiter.api.Test;

class TourismAdminSyncServiceTest {
    @Test void unconfiguredOperatorAndExhaustedBudgetNeverDispatch() {
        var sync = mock(TourismSyncService.class);
        var runs = mock(TourismSyncMapper.class);
        var executor = new DeferredExecutor();
        when(runs.sumRemoteCallCount(any(), any())).thenReturn(0);
        var unconfigured = new TourismAdminSyncService(sync, runs,
                new TourismAdminSyncSettings(false, 5, ZoneId.of("Asia/Seoul")), executor);
        assertThat(unconfigured.request("JEJU_CITY_ATTRACTION").code())
                .isEqualTo("OPERATOR_CONFIGURATION_REQUIRED");

        when(runs.sumRemoteCallCount(any(), any())).thenReturn(5);
        var exhausted = new TourismAdminSyncService(sync, runs,
                new TourismAdminSyncSettings(true, 5, ZoneId.of("Asia/Seoul")), executor);
        assertThat(exhausted.request("JEJU_CITY_ATTRACTION").code()).isEqualTo("CALL_BUDGET_EXHAUSTED");
        assertThat(executor.tasks).isEmpty();
        verifyNoInteractions(sync);
    }

    @Test void acceptsOneScopeOnlyAndPassesTheCurrentRemainingAllowance() {
        var sync = mock(TourismSyncService.class);
        var runs = mock(TourismSyncMapper.class);
        var executor = new DeferredExecutor();
        when(runs.sumRemoteCallCount(any(), any())).thenReturn(3);
        var service = new TourismAdminSyncService(sync, runs,
                new TourismAdminSyncSettings(true, 10, ZoneId.of("Asia/Seoul")), executor);

        var accepted = service.request("JEJU_CITY_RESTAURANT");
        assertThat(accepted.accepted()).isTrue();
        assertThat(service.request("GYEONGJU_LODGING").code()).isEqualTo("SYNC_ALREADY_RUNNING");
        assertThat(executor.tasks).hasSize(1);

        executor.runNext();
        verify(sync).synchronize(TourismSyncScope.fromKey("JEJU_CITY_RESTAURANT"), 7);
        assertThat(service.request("INVALID_SCOPE").code()).isEqualTo("INVALID_SCOPE");
    }

    @Test void overviewReturnsAllNineScopesAndOnlySanitizedFailureCategories() {
        var sync = mock(TourismSyncService.class);
        var runs = mock(TourismSyncMapper.class);
        when(runs.sumRemoteCallCount(any(), any())).thenReturn(4);
        var failed = new TourismSyncRun(TourismSyncScope.fromKey("SEOGWIPO_LODGING"),
                Instant.parse("2026-09-09T01:00:00Z"));
        failed.complete(TourismSyncStatus.FAILED, TourismSyncCounters.failed(4, 0), "TIMEOUT",
                Instant.parse("2026-09-09T01:00:04Z"));
        when(runs.findRecentRuns(100)).thenReturn(List.of(failed));
        when(runs.findScopeStates()).thenReturn(List.of());
        var service = new TourismAdminSyncService(sync, runs,
                new TourismAdminSyncSettings(true, 10, ZoneId.of("Asia/Seoul")), Runnable::run);

        var overview = service.overview();
        assertThat(overview.lastSyncAt()).isEqualTo(Instant.parse("2026-09-09T01:00:04Z"));
        assertThat(overview.dailyUsedCalls()).isEqualTo(4);
        assertThat(overview.remainingCalls()).isEqualTo(6);
        assertThat(overview.budgetStatus()).isEqualTo("AVAILABLE");
        assertThat(overview.scopes()).hasSize(9);
        assertThat(overview.scopes()).anySatisfy(scope -> {
            assertThat(scope.scopeKey()).isEqualTo("SEOGWIPO_LODGING");
            assertThat(scope.latestStatus()).isEqualTo("FAILED");
            assertThat(scope.failureCategory()).isEqualTo("TIMEOUT");
        });
        assertThat(overview.recentFailures()).extracting(TourismAdminFailureSummary::failureCategory)
                .containsExactly("TIMEOUT");
    }

    private static final class DeferredExecutor implements Executor {
        private final List<Runnable> tasks = new ArrayList<>();
        @Override public void execute(Runnable command) { tasks.add(command); }
        void runNext() { tasks.removeFirst().run(); }
    }
}
