package kr.hankkitravel.tourism.api;

import kr.hankkitravel.tourism.application.TourismAdminSyncDispatch;
import kr.hankkitravel.tourism.application.TourismAdminSyncOverview;
import kr.hankkitravel.tourism.application.TourismAdminSyncService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Basic-authenticated operator surface; no user-facing TourAPI proxy is exposed here. */
@RestController
@RequestMapping("/api/admin/tourism-sync")
public class TourismAdminSyncController {
    private final TourismAdminSyncService adminSync;

    public TourismAdminSyncController(TourismAdminSyncService adminSync) {
        this.adminSync = adminSync;
    }

    @GetMapping("/status")
    public TourismAdminSyncOverview status() {
        return adminSync.overview();
    }

    @PostMapping("/runs")
    public ResponseEntity<TourismAdminSyncDispatch> trigger(@RequestBody TourismAdminSyncRequest request) {
        var dispatch = adminSync.request(request.scopeKey());
        return ResponseEntity.status(dispatch.accepted() ? HttpStatus.ACCEPTED : HttpStatus.CONFLICT).body(dispatch);
    }
}
