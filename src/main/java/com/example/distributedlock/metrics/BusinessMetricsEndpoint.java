package com.example.distributedlock.metrics;

import org.springframework.boot.actuate.endpoint.annotation.Endpoint;
import org.springframework.boot.actuate.endpoint.annotation.ReadOperation;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.Map;

/**
 * 業務指標端點
 * 提供分散式鎖和交易相關的業務指標
 */
@Component
@Endpoint(id = "business-metrics")
public class BusinessMetricsEndpoint {

    private final LockMetrics lockMetrics;
    private final TransactionMetrics transactionMetrics;
    private final MetricsIntegrationService integrationService;

    public BusinessMetricsEndpoint(LockMetrics lockMetrics, 
                                 TransactionMetrics transactionMetrics,
                                 MetricsIntegrationService integrationService) {
        this.lockMetrics = lockMetrics;
        this.transactionMetrics = transactionMetrics;
        this.integrationService = integrationService;
    }

    @ReadOperation
    public Map<String, Object> businessMetrics() {
        Map<String, Object> metrics = new HashMap<>();
        
        // 添加時間戳
        metrics.put("timestamp", LocalDateTime.now().format(DateTimeFormatter.ISO_LOCAL_DATE_TIME));
        
        // 分散式鎖指標
        Map<String, Object> lockMetricsData = new HashMap<>();
        lockMetricsData.put("activeLocks", lockMetrics.getActiveLockCount());
        lockMetricsData.put("totalAcquired", lockMetrics.getTotalAcquiredCount());
        lockMetricsData.put("totalReleased", lockMetrics.getTotalReleasedCount());
        lockMetricsData.put("acquisitionSuccessRate", String.format("%.2f%%", lockMetrics.getLockAcquisitionSuccessRate()));
        lockMetricsData.put("releaseSuccessRate", String.format("%.2f%%", lockMetrics.getLockReleaseSuccessRate()));
        
        metrics.put("distributedLock", lockMetricsData);
        
        // 交易指標
        Map<String, Object> transactionMetricsData = new HashMap<>();
        transactionMetricsData.put("totalTransactions", transactionMetrics.getTotalTransactionCount());
        transactionMetricsData.put("successfulTransactions", transactionMetrics.getSuccessfulTransactionCount());
        transactionMetricsData.put("failedTransactions", transactionMetrics.getFailedTransactionCount());
        transactionMetricsData.put("successRate", String.format("%.2f%%", transactionMetrics.getTransactionSuccessRate()));
        transactionMetricsData.put("failureRate", String.format("%.2f%%", transactionMetrics.getTransactionFailureRate()));
        transactionMetricsData.put("averageAmount", String.format("%.2f", transactionMetrics.getAverageTransactionAmount()));
        
        metrics.put("transactions", transactionMetricsData);
        
        // 系統健康度摘要
        MetricsIntegrationService.SystemHealthSummary healthSummary = integrationService.getSystemHealthSummary();
        Map<String, Object> healthScore = new HashMap<>();
        healthScore.put("systemEfficiency", String.format("%.2f%%", healthSummary.getSystemEfficiency()));
        healthScore.put("lockSuccessRate", String.format("%.2f%%", healthSummary.getLockSuccessRate()));
        healthScore.put("transactionSuccessRate", String.format("%.2f%%", healthSummary.getTransactionSuccessRate()));
        healthScore.put("lockContentionRatio", String.format("%.4f", healthSummary.getLockContentionRatio()));
        healthScore.put("transactionThroughput", String.format("%.2f/min", healthSummary.getTransactionThroughput()));
        healthScore.put("uptimeSeconds", healthSummary.getUptimeSeconds());
        
        // 健康度等級
        double efficiency = healthSummary.getSystemEfficiency();
        String healthGrade;
        if (efficiency >= 95) {
            healthGrade = "EXCELLENT";
        } else if (efficiency >= 85) {
            healthGrade = "GOOD";
        } else if (efficiency >= 70) {
            healthGrade = "FAIR";
        } else if (efficiency >= 50) {
            healthGrade = "POOR";
        } else {
            healthGrade = "CRITICAL";
        }
        healthScore.put("grade", healthGrade);
        
        metrics.put("systemHealth", healthScore);
        
        return metrics;
    }
}