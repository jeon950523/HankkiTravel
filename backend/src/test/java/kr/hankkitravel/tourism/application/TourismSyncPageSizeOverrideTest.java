package kr.hankkitravel.tourism.application;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

@SpringBootTest(properties = "TOUR_API_PAGE_SIZE=37")
@ActiveProfiles("test")
class TourismSyncPageSizeOverrideTest {
    @Value("${hankki.tourism-sync.page-size}") int pageSize;

    @Test void tourApiPageSizeEnvironmentOverrideWinsOverTheDevelopmentDefault() {
        assertThat(pageSize).isEqualTo(37);
    }
}
