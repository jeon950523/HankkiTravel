package kr.hankkitravel.tourism.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import java.util.HashSet;
import java.util.Set;
import kr.hankkitravel.tourism.model.TourismContentType;
import kr.hankkitravel.tourism.model.TourismPlace;
import kr.hankkitravel.tourism.model.TourismRegion;
import kr.hankkitravel.tourism.model.TourismSyncScope;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;

@Tag("live")
@SpringBootTest
class TourismPageSizeLiveVerificationTest {
    @Autowired TourismSnapshotSource source;
    @Value("${DATA_GO_KR_SERVICE_KEY:}") String serviceKey;

    @Test void verifiesPageSizeOneHundredForOneRepresentativeScopeWithoutRepeatedFullSync() {
        assumeTrue(!serviceKey.isBlank(), "SKIPPED_SECRET_NOT_PRESENT: DATA_GO_KR_SERVICE_KEY");
        var scope = new TourismSyncScope(TourismRegion.JEJU_CITY, TourismContentType.ATTRACTION);
        var legacyPage = source.fetch(scope, 1, 10);
        var firstHundred = source.fetch(scope, 1, 100);
        assertThat(firstHundred.totalCount()).isEqualTo(legacyPage.totalCount());
        assertThat(firstHundred.pageNo()).isEqualTo(1);

        Set<String> contentIds = new HashSet<>();
        for (TourismPlace item : firstHundred.items()) {
            assertThat(contentIds.add(item.contentId())).isTrue();
        }
        int actualCalls = 1;
        for (int pageNo = 2; contentIds.size() < firstHundred.totalCount(); pageNo++) {
            var page = source.fetch(scope, pageNo, 100);
            actualCalls++;
            assertThat(page.totalCount()).isEqualTo(firstHundred.totalCount());
            assertThat(page.pageNo()).isEqualTo(pageNo);
            for (TourismPlace item : page.items()) {
                assertThat(contentIds.add(item.contentId())).isTrue();
            }
            assertThat(page.items()).isNotEmpty();
        }
        assertThat(contentIds).hasSize(firstHundred.totalCount());
        int legacyExpectedCalls = (firstHundred.totalCount() + 9) / 10;
        int expectedCalls = (firstHundred.totalCount() + 99) / 100;
        assertThat(actualCalls).isEqualTo(expectedCalls);
        System.out.printf("P1_2_1_PAGE_SIZE scope=%s pageSize=100 total=%d legacyExpectedCalls=%d actualCalls=%d%n",
                scope.key(), firstHundred.totalCount(), legacyExpectedCalls, actualCalls);
        assertThat(actualCalls).isLessThanOrEqualTo(legacyExpectedCalls);
    }
}
