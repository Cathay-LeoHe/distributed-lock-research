package com.example.distributedlock.metrics;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import io.micrometer.core.instrument.Gauge;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.time.Duration;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.DoubleAdder;
import java.util.concurrent.ConcurrentHashMap;
import java.util.Map;

/**
 * 銀行交易指標收集器
 * 收集交易成功率、處理時間和業務相關指標
 */
@Component
public class TransactionMetrics {

    private static final Logger logger = LoggerFactory.getLogger(TransactionMetrics.class);

    private final MeterRegistry meterRegistry;
    
    // 交易計數器
    private final Counter transactionAttempts;
    private final Counter transactionSuccess;
    private final Counter transactionFailures;
    private final Counter insufficientFundsErrors;
    private final Counter accountNotFoundErrors;
    private final Counter validationErrors;
    
    // 交易計時器
    private final Timer transactionProcessingTimer;
    private final Timer transferProcessingTimer;
    private final Timer withdrawalProcessingTimer;
    
    // 原子計數器和累加器
    private final AtomicLong totalTransactions = new AtomicLong(0);
    private final AtomicLong successfulTransactions = new AtomicLong(0);
    private final AtomicLong failedTransactions = new AtomicLong(0);
    private final DoubleAdder totalTransactionAmount = new DoubleAdder();
    private final DoubleAdder totalTransferAmount = new DoubleAdder();
    private final DoubleAdder totalWithdrawalAmount = new DoubleAdder();
    
    // 按交易類型分組的指標
    private final Map<String, Counter> transactionTypeCounters = new ConcurrentHashMap<>();
    private final Map<String, Timer> transactionTypeTimers = new ConcurrentHashMap<>();

    public TransactionMetrics(MeterRegistry meterRegistry) {
        this.meterRegistry = meterRegistry;
        
        // 初始化交易計數器
        this.transactionAttempts = Counter.builder("banking.transaction.attempts")
            .description("銀行交易嘗試次數")
            .register(meterRegistry);
            
        this.transactionSuccess = Counter.builder("banking.transaction.success")
            .description("銀行交易成功次數")
            .register(meterRegistry);
            
        this.transactionFailures = Counter.builder("banking.transaction.failures")
            .description("銀行交易失敗次數")
            .register(meterRegistry);
            
        this.insufficientFundsErrors = Counter.builder("banking.transaction.errors.insufficient_funds")
            .description("餘額不足錯誤次數")
            .register(meterRegistry);
            
        this.accountNotFoundErrors = Counter.builder("banking.transaction.errors.account_not_found")
            .description("帳戶不存在錯誤次數")
            .register(meterRegistry);
            
        this.validationErrors = Counter.builder("banking.transaction.errors.validation")
            .description("交易驗證錯誤次數")
            .register(meterRegistry);
        
        // 初始化交易計時器
        this.transactionProcessingTimer = Timer.builder("banking.transaction.processing.duration")
            .description("銀行交易處理耗時")
            .register(meterRegistry);
            
        this.transferProcessingTimer = Timer.builder("banking.transfer.processing.duration")
            .description("匯款交易處理耗時")
            .register(meterRegistry);
            
        this.withdrawalProcessingTimer = Timer.builder("banking.withdrawal.processing.duration")
            .description("扣款交易處理耗時")
            .register(meterRegistry);
        
        // 初始化 Gauge
        Gauge.builder("banking.transaction.total.count", totalTransactions, AtomicLong::get)
            .description("總交易數量")
            .register(meterRegistry);
            
        Gauge.builder("banking.transaction.success.count", successfulTransactions, AtomicLong::get)
            .description("成功交易數量")
            .register(meterRegistry);
            
        Gauge.builder("banking.transaction.failed.count", failedTransactions, AtomicLong::get)
            .description("失敗交易數量")
            .register(meterRegistry);
            
        Gauge.builder("banking.transaction.total.amount", totalTransactionAmount, DoubleAdder::sum)
            .description("總交易金額")
            .register(meterRegistry);
            
        Gauge.builder("banking.transfer.total.amount", totalTransferAmount, DoubleAdder::sum)
            .description("總匯款金額")
            .register(meterRegistry);
            
        Gauge.builder("banking.withdrawal.total.amount", totalWithdrawalAmount, DoubleAdder::sum)
            .description("總扣款金額")
            .register(meterRegistry);
    }

    /**
     * 記錄交易嘗試
     */
    public void recordTransactionAttempt(String transactionType, BigDecimal amount) {
        transactionAttempts.increment();
        totalTransactions.incrementAndGet();
        getTransactionTypeCounter(transactionType, "attempts").increment();
        
        logger.debug("記錄交易嘗試: type={}, amount={}", transactionType, amount);
    }

    /**
     * 記錄交易成功
     */
    public void recordTransactionSuccess(String transactionType, BigDecimal amount, Duration duration) {
        transactionSuccess.increment();
        successfulTransactions.incrementAndGet();
        transactionProcessingTimer.record(duration);
        totalTransactionAmount.add(amount.doubleValue());
        
        getTransactionTypeCounter(transactionType, "success").increment();
        getTransactionTypeTimer(transactionType).record(duration);
        
        // 按交易類型記錄金額
        if ("TRANSFER".equals(transactionType)) {
            transferProcessingTimer.record(duration);
            totalTransferAmount.add(amount.doubleValue());
        } else if ("WITHDRAWAL".equals(transactionType)) {
            withdrawalProcessingTimer.record(duration);
            totalWithdrawalAmount.add(amount.doubleValue());
        }
        
        logger.debug("記錄交易成功: type={}, amount={}, duration={}ms", 
                    transactionType, amount, duration.toMillis());
    }

    /**
     * 記錄交易失敗
     */
    public void recordTransactionFailure(String transactionType, BigDecimal amount, String errorType, Duration duration) {
        transactionFailures.increment();
        failedTransactions.incrementAndGet();
        getTransactionTypeCounter(transactionType, "failures").increment();
        
        // 按錯誤類型記錄
        switch (errorType.toLowerCase()) {
            case "insufficient_funds":
                insufficientFundsErrors.increment();
                break;
            case "account_not_found":
                accountNotFoundErrors.increment();
                break;
            case "validation_error":
                validationErrors.increment();
                break;
            default:
                // 其他錯誤類型
                Counter.builder("banking.transaction.errors.other")
                    .description("其他交易錯誤")
                    .tag("error.type", errorType)
                    .register(meterRegistry)
                    .increment();
                break;
        }
        
        logger.debug("記錄交易失敗: type={}, amount={}, error={}, duration={}ms", 
                    transactionType, amount, errorType, duration.toMillis());
    }

    /**
     * 記錄帳戶餘額查詢
     */
    public void recordBalanceQuery(String accountNumber, Duration duration) {
        Timer balanceQueryTimer = Timer.builder("banking.balance.query.duration")
            .description("帳戶餘額查詢耗時")
            .register(meterRegistry);
        balanceQueryTimer.record(duration);
        
        Counter balanceQueryCounter = Counter.builder("banking.balance.query.count")
            .description("帳戶餘額查詢次數")
            .register(meterRegistry);
        balanceQueryCounter.increment();
        
        logger.debug("記錄餘額查詢: account={}, duration={}ms", accountNumber, duration.toMillis());
    }

    /**
     * 獲取按交易類型分組的計數器
     */
    private Counter getTransactionTypeCounter(String transactionType, String operation) {
        String key = transactionType + "." + operation;
        return transactionTypeCounters.computeIfAbsent(key, k -> 
            Counter.builder("banking.transaction." + operation + ".by.type")
                .description("按類型分組的交易" + operation + "指標")
                .tag("transaction.type", transactionType)
                .register(meterRegistry)
        );
    }

    /**
     * 獲取按交易類型分組的計時器
     */
    private Timer getTransactionTypeTimer(String transactionType) {
        return transactionTypeTimers.computeIfAbsent(transactionType, k -> 
            Timer.builder("banking.transaction.duration.by.type")
                .description("按類型分組的交易處理耗時")
                .tag("transaction.type", transactionType)
                .register(meterRegistry)
        );
    }

    /**
     * 獲取交易成功率
     */
    public double getTransactionSuccessRate() {
        long total = totalTransactions.get();
        long successful = successfulTransactions.get();
        return total > 0 ? ((double) successful / total) * 100.0 : 0.0;
    }

    /**
     * 獲取交易失敗率
     */
    public double getTransactionFailureRate() {
        long total = totalTransactions.get();
        long failed = failedTransactions.get();
        return total > 0 ? ((double) failed / total) * 100.0 : 0.0;
    }

    /**
     * 獲取平均交易金額
     */
    public double getAverageTransactionAmount() {
        long total = successfulTransactions.get();
        double totalAmount = totalTransactionAmount.sum();
        return total > 0 ? totalAmount / total : 0.0;
    }

    /**
     * 獲取總交易數量
     */
    public long getTotalTransactionCount() {
        return totalTransactions.get();
    }

    /**
     * 獲取成功交易數量
     */
    public long getSuccessfulTransactionCount() {
        return successfulTransactions.get();
    }

    /**
     * 獲取失敗交易數量
     */
    public long getFailedTransactionCount() {
        return failedTransactions.get();
    }
}