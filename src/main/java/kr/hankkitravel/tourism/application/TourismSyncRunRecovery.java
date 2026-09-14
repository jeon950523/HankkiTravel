package kr.hankkitravel.tourism.application;

import java.time.Instant;
import kr.hankkitravel.tourism.persistence.TourismSyncMapper;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

/** A restarted single-instance process cannot own a prior process's RUNNING scope. */
@Component
public class TourismSyncRunRecovery implements ApplicationRunner {
    private final TourismSyncMapper runs;

    public TourismSyncRunRecovery(TourismSyncMapper runs) {
        this.runs = runs;
    }

    @Override
    public void run(ApplicationArguments args) {
        reconcileInterruptedRuns();
    }

    public int reconcileInterruptedRuns() {
        return runs.reconcileInterruptedRuns(Instant.now());
    }
}
