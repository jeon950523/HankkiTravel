package kr.hankkitravel.tourism.application;

import java.time.ZoneId;
import java.util.concurrent.Executor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

@Configuration
public class TourismAdminSyncConfiguration {
    @Bean
    TourismAdminSyncSettings tourismAdminSyncSettings(
            @Value("${hankki.tourism-sync.admin.enabled:false}") boolean enabled,
            @Value("${hankki.tourism-sync.admin.daily-call-budget:0}") int dailyCallBudget,
            @Value("${hankki.tourism-sync.admin.operation-zone:Asia/Seoul}") String operationZone) {
        return new TourismAdminSyncSettings(enabled, dailyCallBudget, ZoneId.of(operationZone));
    }

    @Bean(name = "tourismAdminSyncExecutor")
    Executor tourismAdminSyncExecutor() {
        var executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(1);
        executor.setMaxPoolSize(1);
        executor.setQueueCapacity(0);
        executor.setThreadNamePrefix("tourism-admin-sync-");
        executor.setWaitForTasksToCompleteOnShutdown(true);
        executor.initialize();
        return executor;
    }
}
