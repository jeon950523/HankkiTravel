package kr.hankkitravel.tourism.application;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import kr.hankkitravel.shared.integration.IntegrationException;
import kr.hankkitravel.shared.integration.IntegrationFailure;
import kr.hankkitravel.tourism.model.TourApiPage;
import kr.hankkitravel.tourism.model.TourismContentType;
import kr.hankkitravel.tourism.model.TourismPlace;
import kr.hankkitravel.tourism.model.TourismRegion;
import kr.hankkitravel.tourism.model.TourismSyncScope;
import kr.hankkitravel.tourism.persistence.TourismCacheMapper;
import kr.hankkitravel.tourism.model.TourismSyncStatus;
import kr.hankkitravel.tourism.persistence.TourismSyncMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Transactional;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.mysql.MySQLContainer;

@Testcontainers
@SpringBootTest
@ActiveProfiles("test")
@Transactional
class TourismSyncPersistenceTest {
    @Container
    static final MySQLContainer MYSQL = new MySQLContainer(
            "mysql@sha256:c831a0f11348d402b43d77453e17d770be2eef356615a2823fe0f5a0d6c8b9af")
            .withDatabaseName("hankki_p11_test").withUsername("sync_test").withPassword(UUID.randomUUID().toString())
            .withCommand("--default-time-zone=+00:00", "--character-set-server=utf8mb4");

    @DynamicPropertySource
    static void database(DynamicPropertyRegistry properties) {
        String url = MYSQL.getJdbcUrl() + "?connectionTimeZone=UTC&forceConnectionTimeZoneToSession=true";
        properties.add("spring.datasource.url", () -> url);
        properties.add("spring.datasource.username", MYSQL::getUsername);
        properties.add("spring.datasource.password", MYSQL::getPassword);
        properties.add("spring.datasource.driver-class-name", () -> "com.mysql.cj.jdbc.Driver");
    }

    @Autowired TourismCacheMapper places;
    @Autowired TourismSyncMapper runs;
    @Autowired ApplicationEventPublisher events;
    @Autowired PlatformTransactionManager transactionManager;
    @Autowired JdbcTemplate jdbc;

    @Test void sourceModifiedRawControlsStalenessAndDifferentialRestaurantCacheMutations() {
        var scope = new TourismSyncScope(TourismRegion.JEJU_CITY, TourismContentType.RESTAURANT);
        var source = new ScriptedSource();
        source.page(1, page(1, 2, place(scope, "r-1", "처음", "20260101010101"),
                place(scope, "r-2", "둘째", "20260101010101")));
        var sync = service(source, 2);

        var initial = sync.synchronize(scope);
        assertThat(initial.successful()).isTrue();
        assertThat(initial.counters().insertedCount()).isEqualTo(2);
        assertThat(jdbc.queryForObject("SELECT source_modified_raw FROM tourism_places WHERE content_id = 'r-1'", String.class)).isEqualTo("20260101010101");
        assertThat(runs.findScopeStates()).singleElement().satisfies(state -> {
            assertThat(state.getScopeKey()).isEqualTo(scope.key());
            assertThat(state.getLastSuccessfulSyncAt()).isNotNull();
            assertThat(state.getLastSuccessfulRunId()).isPositive();
        });
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM tourism_sync_scope_states", Integer.class)).isEqualTo(1);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM restaurants", Integer.class)).isEqualTo(2);
        LocalDateTime firstUpdatedAt = jdbc.queryForObject("SELECT updated_at FROM tourism_places WHERE content_id = 'r-1'", LocalDateTime.class);
        var noOp = sync.synchronize(scope);
        assertThat(noOp.counters().updatedCount()).isZero();
        assertThat(noOp.counters().unchangedCount()).isEqualTo(2);
        assertThat(jdbc.queryForObject("SELECT updated_at FROM tourism_places WHERE content_id = 'r-1'", LocalDateTime.class))
                .isEqualTo(firstUpdatedAt);

        source.page(1, page(1, 2, place(scope, "r-1", "처음", "20260101010101"),
                place(scope, "r-2", "수정됨", "20260101010102")));
        var modified = sync.synchronize(scope);
        assertThat(modified.counters().updatedCount()).isEqualTo(1);
        assertThat(jdbc.queryForObject("SELECT title FROM tourism_places WHERE content_id = 'r-2'", String.class)).isEqualTo("수정됨");

        source.page(1, page(1, 2, place(scope, "r-1", "처음", "20260101010101"),
                place(scope, "r-2", "과거값", "20250101010101")));
        var stale = sync.synchronize(scope);
        assertThat(stale.counters().updatedCount()).isZero();
        assertThat(jdbc.queryForObject("SELECT title FROM tourism_places WHERE content_id = 'r-2'", String.class)).isEqualTo("수정됨");

        source.page(1, page(1, 1, place(scope, "r-1", "처음", "20260101010101")));
        assertThat(sync.synchronize(scope).counters().deactivatedCount()).isEqualTo(1);
        assertThat(jdbc.queryForObject("SELECT active FROM tourism_places WHERE content_id = 'r-2'", Boolean.class)).isFalse();

        source.page(1, page(1, 2, place(scope, "r-1", "처음", "20260101010101"),
                place(scope, "r-2", "복구", "20260101010103")));
        assertThat(sync.synchronize(scope).counters().updatedCount()).isEqualTo(1);
        assertThat(jdbc.queryForObject("SELECT active FROM tourism_places WHERE content_id = 'r-2'", Boolean.class)).isTrue();
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM restaurants", Integer.class)).isEqualTo(2);
    }

    @Test void callBudgetExhaustionNeverAppliesAPartialSnapshot() {
        var scope = new TourismSyncScope(TourismRegion.GYEONGJU, TourismContentType.ATTRACTION);
        var source = new ScriptedSource();
        source.page(1, page(1, 1, place(scope, "budget-1", "기준", "20260101010101")));
        var sync = service(source, 1);
        assertThat(sync.synchronize(scope).successful()).isTrue();

        source.page(1, page(1, 2, place(scope, "budget-1", "부분 수정", "20260101010102")))
                .page(2, page(2, 2, place(scope, "budget-2", "새 항목", "20260101010102")));
        var exhausted = sync.synchronize(scope, 1);
        assertThat(exhausted.failureCategory()).isEqualTo("CALL_BUDGET_EXHAUSTED");
        assertThat(exhausted.counters().remoteCallCount()).isEqualTo(1);
        assertThat(jdbc.queryForObject("SELECT title FROM tourism_places WHERE content_id = 'budget-1'", String.class)).isEqualTo("기준");
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM tourism_places WHERE content_id = 'budget-2'", Integer.class)).isZero();
    }

    @Test void scopeIsolationAndIncompletePagesNeverDeactivateExistingCache() {
        var jeju = new TourismSyncScope(TourismRegion.JEJU_CITY, TourismContentType.ATTRACTION);
        var seogwipo = new TourismSyncScope(TourismRegion.SEOGWIPO, TourismContentType.LODGING);
        var good = new ScriptedSource();
        good.page(1, page(1, 1, place(jeju, "a-1", "관광지", "20260101010101")));
        var sync = service(good, 1);
        assertThat(sync.synchronize(jeju).successful()).isTrue();
        good.page(1, page(1, 1, place(seogwipo, "l-1", "숙소", "20260101010101")));
        assertThat(sync.synchronize(seogwipo).successful()).isTrue();
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM restaurants", Integer.class)).isZero();

        var baseline = new ScriptedSource();
        baseline.page(1, page(1, 2, place(jeju, "a-1", "관광지", "20260101010101")))
                .page(2, page(2, 2, place(jeju, "a-2", "관광지2", "20260101010101")));
        sync = service(baseline, 1);
        assertThat(sync.synchronize(jeju).successful()).isTrue();

        var incomplete = new ScriptedSource();
        incomplete.page(1, page(1, 2, place(jeju, "a-1", "관광지", "20260101010101"))).failAt(2);
        var failed = service(incomplete, 1).synchronize(jeju);
        assertThat(failed.successful()).isFalse();
        assertThat(failed.failureCategory()).isEqualTo("TIMEOUT");
        assertThat(failed.counters().deactivatedCount()).isZero();
        assertThat(jdbc.queryForObject("SELECT active FROM tourism_places WHERE content_id = 'a-2'", Boolean.class)).isTrue();
        assertThat(jdbc.queryForObject("SELECT active FROM tourism_places WHERE content_id = 'l-1'", Boolean.class)).isTrue();
    }

    @Test void malformedAndDuplicatePagesFailBeforeAnyMutation() {
        var scope = new TourismSyncScope(TourismRegion.GYEONGJU, TourismContentType.ATTRACTION);
        var source = new ScriptedSource();
        source.page(1, page(1, 1, place(scope, "g-1", "기준", "20260101010101")));
        var sync = service(source, 1);
        assertThat(sync.synchronize(scope).successful()).isTrue();

        source.page(1, page(1, 1, place(scope, "g-1", "잘못된 수정일", "bad")));
        var malformed = sync.synchronize(scope);
        assertThat(malformed.failureCategory()).isEqualTo("MALFORMED_ITEM");
        assertThat(jdbc.queryForObject("SELECT title FROM tourism_places WHERE content_id = 'g-1'", String.class)).isEqualTo("기준");

        source.page(1, page(1, 2, place(scope, "g-1", "기준", "20260101010101")))
                .page(2, page(2, 2, place(scope, "g-1", "중복", "20260101010102")));
        var duplicate = sync.synchronize(scope);
        assertThat(duplicate.failureCategory()).isEqualTo("INCOMPLETE_PAGINATION");
        assertThat(duplicate.counters().deactivatedCount()).isZero();
    }

    @Test void suspiciousSnapshotKeepsThePriorCacheAndNeverDeactivatesMissingItems() {
        var scope = new TourismSyncScope(TourismRegion.JEJU_CITY, TourismContentType.ATTRACTION);
        var source = new ScriptedSource();
        source.page(1, page(1, 429, snapshot(scope, "stable", 429, "20260101010101")));
        var sync = service(source, 500, TourismSyncService.DEFAULT_SUSPICIOUS_SNAPSHOT_RETAIN_RATIO);
        assertThat(sync.synchronize(scope).status()).isEqualTo(TourismSyncStatus.SUCCESS);

        source.page(1, page(1, 429, snapshot(scope, "stable", 429, "20260101010101")));
        var unchanged = sync.synchronize(scope);
        assertThat(unchanged.status()).isEqualTo(TourismSyncStatus.SUCCESS);
        assertThat(runs.findLastSuccessfulFetchedCount(scope.key())).isEqualTo(429);

        source.page(1, page(1, 0));
        var empty = sync.synchronize(scope);
        assertThat(empty.failureCategory()).isEqualTo("SUSPICIOUS_SNAPSHOT_SHRINK");
        assertThat(empty.status()).isEqualTo(TourismSyncStatus.SUSPICIOUS);
        assertThat(empty.counters().deactivatedCount()).isZero();
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM tourism_places WHERE active = TRUE", Integer.class)).isEqualTo(429);
        assertThat(runs.findRecentRuns(100).getFirst().getFailureCategory()).isEqualTo("SUSPICIOUS_SNAPSHOT_SHRINK");

        source.page(1, page(1, 200, snapshot(scope, "stable", 200, "20260101010102")));
        var sharpDecrease = sync.synchronize(scope);
        assertThat(sharpDecrease.status()).isEqualTo(TourismSyncStatus.SUSPICIOUS);
        assertThat(sharpDecrease.counters().deactivatedCount()).isZero();
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM tourism_places WHERE active = TRUE", Integer.class)).isEqualTo(429);

        source.page(1, page(1, 400, snapshot(scope, "stable", 400, "20260101010103")));
        var modestChange = sync.synchronize(scope);
        assertThat(modestChange.status()).isEqualTo(TourismSyncStatus.SUCCESS);
        assertThat(modestChange.counters().deactivatedCount()).isEqualTo(29);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM tourism_places WHERE active = TRUE", Integer.class)).isEqualTo(400);
    }

    @Test void contentTypeChangeUpdatesCanonicalTypeAndRemovesRestaurantSpecialization() {
        var restaurantScope = new TourismSyncScope(TourismRegion.JEJU_CITY, TourismContentType.RESTAURANT);
        var attractionScope = new TourismSyncScope(TourismRegion.JEJU_CITY, TourismContentType.ATTRACTION);
        var source = new ScriptedSource();
        source.page(1, page(1, 1, place(restaurantScope, "X", "음식점 기준", "20260101010101")));
        var sync = service(source, 1);
        assertThat(sync.synchronize(restaurantScope).successful()).isTrue();
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM restaurants", Integer.class)).isEqualTo(1);

        source.page(1, page(1, 1, place(attractionScope, "X", "관광지 변경", "20260101010102")));
        assertThat(sync.synchronize(attractionScope).successful()).isTrue();
        assertThat(jdbc.queryForObject("SELECT content_type_id FROM tourism_places WHERE content_id = 'X'", String.class))
                .isEqualTo("12");
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM restaurants", Integer.class)).isZero();
    }

    private TourismSyncService service(ScriptedSource source, int pageSize) {
        return new TourismSyncService(source, places, runs, events, transactionManager, pageSize, 10);
    }

    private TourismSyncService service(ScriptedSource source, int pageSize, double suspiciousSnapshotRetainRatio) {
        return new TourismSyncService(source, places, runs, events, transactionManager, pageSize, 10,
                suspiciousSnapshotRetainRatio);
    }

    private static TourApiPage page(int number, int total, TourismPlace... places) {
        return new TourApiPage(List.of(places), number, Math.max(1, places.length), total);
    }

    private static TourismPlace place(TourismSyncScope scope, String id, String title, String modified) {
        return new TourismPlace(id, scope.contentTypeId(), title, "주소", null, null, null, null, null, null,
                null, scope.lDongRegnCd(), scope.lDongSignguCd(), null, null, null, "20260101000000", modified, null);
    }
    private static TourismPlace[] snapshot(TourismSyncScope scope, String prefix, int count, String modified) {
        var places = new TourismPlace[count];
        for (int index = 0; index < count; index++) {
            places[index] = place(scope, prefix + "-" + index, "fixture " + index, modified);
        }
        return places;
    }


    private static final class ScriptedSource implements TourismSnapshotSource {
        private final Map<Integer, TourApiPage> pages = new HashMap<>();
        private final Set<Integer> failures = new java.util.HashSet<>();

        ScriptedSource page(int number, TourApiPage response) { pages.put(number, response); return this; }
        ScriptedSource failAt(int page) { failures.add(page); return this; }

        @Override public TourApiPage fetch(TourismSyncScope scope, int pageNo, int numOfRows) {
            if (failures.contains(pageNo)) throw new IntegrationException("TOUR_API", IntegrationFailure.TIMEOUT);
            return pages.get(pageNo);
        }
    }
}
