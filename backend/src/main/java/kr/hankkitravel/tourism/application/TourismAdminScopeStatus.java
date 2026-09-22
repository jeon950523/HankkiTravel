package kr.hankkitravel.tourism.application;

import java.time.Instant;

public record TourismAdminScopeStatus(String scopeKey, String regionName, String contentTypeName,
        String latestStatus, Instant lastAttemptAt, Instant lastSuccessfulSyncAt,
        int latestRemoteCallCount, String failureCategory) {}
