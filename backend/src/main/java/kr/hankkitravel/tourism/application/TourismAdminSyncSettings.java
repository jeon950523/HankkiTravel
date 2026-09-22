package kr.hankkitravel.tourism.application;

import java.time.ZoneId;

/** Server-local operator settings. Values are never returned from an API or logged. */
public record TourismAdminSyncSettings(boolean enabled, int dailyCallBudget, ZoneId operationZone) {
    public TourismAdminSyncSettings {
        if (dailyCallBudget < 0) throw new IllegalArgumentException("일일 호출 budget은 음수일 수 없습니다.");
    }

    public boolean operationallyEnabled() {
        return enabled && dailyCallBudget > 0;
    }
}
