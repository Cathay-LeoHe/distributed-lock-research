package com.example.distributedlock.metrics;

import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Gauge;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.util.concurrent.atomic.AtomicLong;

/**
 * 指標整合服務
 * 負責整合和協調各種指標收集器
 */
@Service
public class MetricsIntegrationService {

    private static final Logger logger = LoggerFactory.getLogger(MetricsIntegrationService.class);

    private final MeterRegistry meterRegistry;
    private final LockMetrics lockMetrics;
    private final TransactionMetrics transactionMetrics;
    
    // 系統運行時間指標
    private final AtomicLong systemUptimeSeconds = new AtomicLong(0);
    private long applicationStartTime;

    public MetricsIntegrationService(MeterRegistry meterRegistry, 
                                   LockMetrics lockMetrics, 
                                   TransactionMetrics transactionMetrics) {
        this.meterRegistry = meterRegistry;
        this.lockMetrics = lockMetrics;
        this.transactionMetrics = transactionMetrics;
    }

    /**
     * 應用程式啟動完成後初始化指標
     */
    @EventListener(ApplicationReadyEvent.class)
    public void initializeMetrics() {
        applicationStartTime = System.currentTimeMillis();
        logger.info("初始化指標整合服務");
        
        // 註冊系統運行時間指標
        Gauge.builder("system.uptime.seconds", systemUptimeSeconds, AtomicLong::get)
            .description("系統運行時間（秒）")
            .register(meterRegistry);
        
        // 註冊複合指標
        registerCompositeMetrics();
        
        logger.info("指標整合服務初始化完成");
    }

    /**
     * 註冊複合指標
     */
    private void registerCompositeMetrics() {
        // 系統效率指標
        Gauge.builder("system.efficiency.score", this, MetricsIntegrationService::calculateSystemEfficiency)
            .description("系統效率評分 (0-100)")
            .register(meterRegistry);
        
        // 鎖競爭指標
        Gauge.builder("distributed.lock.contention.ratio", this, MetricsIntegrationService::calculateLockContentionRatio)
            .description("鎖競爭比率")
            .register(meterRegistry);
        
        // 交易吞吐量指標
        Gauge.builder("banking.transaction.throughput", this, MetricsIntegrationService::calculateTransactionThroughput)
            .description("交易吞吐量（每分鐘）")
            .register(meterRegistry);
    }

    /**
     * 定期更新系統運行時間
     */
    @Scheduled(fixedRate = 1000) // 每秒更新一次
    public void updateSystemUptime() {
        if (applicationStartTime > 0) {
            long uptimeSeconds = (System.currentTimeMillis() - applicationStartTime) / 1000;
            systemUptimeSeconds.set(uptimeSeconds);
        }
    }

    /**
     * 計算系統效率評分
     */
    private double calculateSystemEfficiency() {
        try {
            double lockSuccessRate = lockMetrics.getLockAcquisitionSuccessRate();
            double transactionSuccessRate = transactionMetrics.getTransactionSuccessRate();
            
            // 如果沒有數據，返回 100 分
            if (lockSuccessRate == 0.0 && transactionSuccessRate == 0.0) {
                return 100.0;
            }
            
            // 加權平均：鎖成功率 40%，交易成功率 60%
            return (lockSuccessRate * 0.4) + (transactionSuccessRate * 0.6);
            
        } catch (Exception e) {
            logger.warn("計算系統效率評分時發生異常", e);
            return 0.0;
        }
    }

    /**
     * 計算鎖競爭比率
     */
    private double calculateLockContentionRatio() {
        try {
            long totalAcquired = lockMetrics.getTotalAcquiredCount();
            long activeLocks = lockMetrics.getActiveLockCount();
            
            if (totalAcquired == 0) {
                return 0.0;
            }
            
            // 競爭比率 = 當前活躍鎖數 / 總獲取鎖數
            return (double) activeLocks / totalAcquired;
            
        } catch (Exception e) {
            logger.warn("計算鎖競爭比率時發生異常", e);
            return 0.0;
        }
    }

    /**
     * 計算交易吞吐量（每分鐘）
     */
    private double calculateTransactionThroughput() {
        try {
            long totalTransactions = transactionMetrics.getTotalTransactionCount();
            long uptimeSeconds = systemUptimeSeconds.get();
            
            if (uptimeSeconds == 0) {
                return 0.0;
            }
            
            // 吞吐量 = 總交易數 / 運行時間（分鐘）
            double uptimeMinutes = uptimeSeconds / 60.0;
            return uptimeMinutes > 0 ? totalTransactions / uptimeMinutes : 0.0;
            
        } catch (Exception e) {
            logger.warn("計算交易吞吐量時發生異常", e);
            return 0.0;
        }
    }

    /**
     * 獲取系統健康度摘要
     */
    public SystemHealthSummary getSystemHealthSummary() {
        return new SystemHealthSummary(
            calculateSystemEfficiency(),
            lockMetrics.getLockAcquisitionSuccessRate(),
            transactionMetrics.getTransactionSuccessRate(),
            calculateLockContentionRatio(),
            calculateTransactionThroughput(),
            systemUptimeSeconds.get()
        );
    }

    /**
     * 系統健康度摘要類別
     */
    public static class SystemHealthSummary {
        private final double systemEfficiency;
        private final double lockSuccessRate;
        private final double transactionSuccessRate;
        private final double lockContentionRatio;
        private final double transactionThroughput;
        private final long uptimeSeconds;

        public SystemHealthSummary(double systemEfficiency, double lockSuccessRate, 
                                 double transactionSuccessRate, double lockContentionRatio,
                                 double transactionThroughput, long uptimeSeconds) {
            this.systemEfficiency = systemEfficiency;
            this.lockSuccessRate = lockSuccessRate;
            this.transactionSuccessRate = transactionSuccessRate;
            this.lockContentionRatio = lockContentionRatio;
            this.transactionThroughput = transactionThroughput;
            this.uptimeSeconds = uptimeSeconds;
        }

        // Getters
        public double getSystemEfficiency() { return systemEfficiency; }
        public double getLockSuccessRate() { return lockSuccessRate; }
        public double getTransactionSuccessRate() { return transactionSuccessRate; }
        public double getLockContentionRatio() { return lockContentionRatio; }
        public double getTransactionThroughput() { return transactionThroughput; }
        public long getUptimeSeconds() { return uptimeSeconds; }

        @Override
        public String toString() {
            return String.format(
                "SystemHealthSummary{efficiency=%.2f%%, lockSuccess=%.2f%%, " +
                "transactionSuccess=%.2f%%, contention=%.4f, throughput=%.2f/min, uptime=%ds}",
                systemEfficiency, lockSuccessRate, transactionSuccessRate,
                lockContentionRatio, transactionThroughput, uptimeSeconds
            );
        }
    }
}