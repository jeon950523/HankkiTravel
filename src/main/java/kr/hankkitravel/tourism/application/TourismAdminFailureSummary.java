package kr.hankkitravel.tourism.application;

import java.time.Instant;

public record TourismAdminFailureSummary(String scopeKey, Instant startedAt, String failureCategory,
        int remoteCallCount) {}
