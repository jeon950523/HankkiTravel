package kr.hankkitravel.tourism.application;

import java.time.Instant;
import java.util.List;

public record TourismAdminSyncOverview(boolean operatorEnabled, String operationZone, int dailyUsedCalls,
        int dailyCallBudget, int remainingCalls, String budgetStatus, String activeScopeKey,
        Instant lastSyncAt, List<TourismAdminScopeStatus> scopes,
        List<TourismAdminFailureSummary> recentFailures) {}
