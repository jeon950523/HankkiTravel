package kr.hankkitravel.tourism.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;

import java.util.List;
import java.util.UUID;
import kr.hankkitravel.restaurant.application.RestaurantSpecializationListener;
import kr.hankkitravel.restaurant.persistence.RestaurantSpecializationMapper;
import kr.hankkitravel.tourism.model.TourApiPage;
import kr.hankkitravel.tourism.model.TourismContentType;
import kr.hankkitravel.tourism.model.TourismPlace;
import kr.hankkitravel.tourism.model.TourismPlaceContentTypeChanged;
import kr.hankkitravel.tourism.model.TourismRegion;
import kr.hankkitravel.tourism.model.TourismSyncScope;
import kr.hankkitravel.tourism.persistence.TourismCacheMapper;
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
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.mysql.MySQLContainer;

@Testcontainers
@SpringBootTest
@ActiveProfiles("test")
class TourismSyncTransactionRollbackTest {
    @Container
    static final MySQLContainer MYSQL = new MySQLContainer(
            "mysql@sha256:c831a0f11348d402b43d77453e17d770be2eef356615a2823fe0f5a0d6c8b9af")
            .withDatabaseName("hankki_p122_rollback").withUsername("sync_test").withPassword(UUID.randomUUID().toString())
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

    @Test void restaurantSpecializationDeleteFailureRollsBackCanonicalTypeChange() {
        var restaurantScope = new TourismSyncScope(TourismRegion.JEJU_CITY, TourismContentType.RESTAURANT);
        var attractionScope = new TourismSyncScope(TourismRegion.JEJU_CITY, TourismContentType.ATTRACTION);
        var source = new ScriptedSource(restaurantScope, "rollback-X", "음식점 기준", "20260101010101");
        assertThat(service(source, events).synchronize(restaurantScope).successful()).isTrue();
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM restaurants", Integer.class)).isEqualTo(1);

        var restaurants = mock(RestaurantSpecializationMapper.class);
        doThrow(new IllegalStateException("delete failure")).when(restaurants).deleteByTourismPlaceId(anyLong());
        var listener = new RestaurantSpecializationListener(restaurants);
        ApplicationEventPublisher failingEvents = event -> {
            if (event instanceof TourismPlaceContentTypeChanged changed) listener.on(changed);
        };
        source.replace(attractionScope, "rollback-X", "관광지 변경", "20260101010102");
        var changed = service(source, failingEvents).synchronize(attractionScope);

        assertThat(changed.failureCategory()).isEqualTo("DATABASE_OR_UNEXPECTED");
        assertThat(jdbc.queryForObject("SELECT content_type_id FROM tourism_places WHERE content_id = 'rollback-X'", String.class))
                .isEqualTo("39");
        assertThat(jdbc.queryForObject("SELECT title FROM tourism_places WHERE content_id = 'rollback-X'", String.class))
                .isEqualTo("음식점 기준");
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM restaurants", Integer.class)).isEqualTo(1);
    }

    private TourismSyncService service(TourismSnapshotSource source, ApplicationEventPublisher publisher) {
        return new TourismSyncService(source, places, runs, publisher, transactionManager, 1, 10);
    }

    private static final class ScriptedSource implements TourismSnapshotSource {
        private TourApiPage page;

        ScriptedSource(TourismSyncScope scope, String id, String title, String modified) {
            replace(scope, id, title, modified);
        }

        void replace(TourismSyncScope scope, String id, String title, String modified) {
            page = new TourApiPage(List.of(new TourismPlace(id, scope.contentTypeId(), title, "주소", null, null,
                    null, null, null, null, null, scope.lDongRegnCd(), scope.lDongSignguCd(), null, null, null,
                    "20260101000000", modified, null)), 1, 1, 1);
        }

        @Override public TourApiPage fetch(TourismSyncScope scope, int pageNo, int numOfRows) { return page; }
    }
}
