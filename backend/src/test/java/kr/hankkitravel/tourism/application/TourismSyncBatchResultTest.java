package kr.hankkitravel.tourism.application;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.ArrayList;
import kr.hankkitravel.tourism.model.TourismSyncBatchResult;
import kr.hankkitravel.tourism.model.TourismSyncBatchStatus;
import kr.hankkitravel.tourism.model.TourismSyncCounters;
import kr.hankkitravel.tourism.model.TourismSyncResult;
import kr.hankkitravel.tourism.model.TourismSyncScope;
import kr.hankkitravel.tourism.model.TourismSyncStatus;
import org.junit.jupiter.api.Test;

class TourismSyncBatchResultTest {
    @Test void eightSuccessfulScopesAndOneFailedScopeArePartialSuccessWithoutRollback() {
        var results = new ArrayList<TourismSyncResult>();
        for (int index = 0; index < TourismSyncScope.allMvpScopes().size(); index++) {
            var status = index == 8 ? TourismSyncStatus.FAILED : TourismSyncStatus.SUCCESS;
            var counters = status == TourismSyncStatus.SUCCESS ? new TourismSyncCounters(1, 1, 0, 0, 1, 0, 0)
                    : TourismSyncCounters.failed(1, 0);
            results.add(new TourismSyncResult(TourismSyncScope.allMvpScopes().get(index), status, counters,
                    status == TourismSyncStatus.SUCCESS ? null : "TIMEOUT"));
        }
        var batch = TourismSyncBatchResult.from(results);
        assertThat(batch.status()).isEqualTo(TourismSyncBatchStatus.PARTIAL_SUCCESS);
        assertThat(batch.scopeResults()).filteredOn(TourismSyncResult::successful).hasSize(8);
    }
}
