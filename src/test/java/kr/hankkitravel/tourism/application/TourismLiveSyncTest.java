package kr.hankkitravel.tourism.application;
import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import org.springframework.beans.factory.annotation.Value;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

/** Runs only with the live Maven profile and writes real TourAPI cache data to the configured local DB. */
@Tag("live")
@SpringBootTest
class TourismLiveSyncTest {
    @Autowired TourismSyncService sync;
    @Value("${DATA_GO_KR_SERVICE_KEY:}") String serviceKey;

    @Test void synchronizesAllNineMvpScopesOnce() throws Exception {
        assumeTrue(!serviceKey.isBlank(),
                "SKIPPED_SECRET_NOT_PRESENT: DATA_GO_KR_SERVICE_KEY");

        var results = sync.synchronizeAllMvpScopes();
        for (var result : results) {
            var c = result.counters();
            System.out.printf("P1_1_LIVE_SYNC scope=%s status=%s calls=%d fetched=%d inserted=%d updated=%d unchanged=%d deactivated=%d failed=%d category=%s%n",
                    result.scope().key(), result.status(), c.remoteCallCount(), c.fetchedCount(),
                    c.insertedCount(), c.updatedCount(), c.unchangedCount(), c.deactivatedCount(),
                    c.failedCount(), result.failureCategory());
        }
        assertThat(results).allSatisfy(result -> assertThat(result.successful())
                .as("scope=%s, failure=%s", result.scope().key(), result.failureCategory()).isTrue());
    }
}
