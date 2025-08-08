package com.example.distributedlock.services;

import com.example.distributedlock.dto.TransactionResult;
import com.example.distributedlock.exception.AccountNotFoundException;
import com.example.distributedlock.exception.AccountValidationException;
import com.example.distributedlock.exception.InsufficientFundsException;
import com.example.distributedlock.exception.LockAcquisitionException;
import com.example.distributedlock.factory.DistributedLockFactory;
import com.example.distributedlock.lock.DistributedLock;
import com.example.distributedlock.metrics.TransactionMetrics;
import com.example.distributedlock.models.Account;
import com.example.distributedlock.models.Transaction;
import com.example.distributedlock.models.TransactionStatus;
import com.example.distributedlock.models.TransactionType;
import com.example.distributedlock.repositories.TransactionRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.TimeUnit;

/**
 * 匯款服務實作類別
 * 使用分散式鎖保護匯款操作，確保資料一致性
 */
@Service
@Transactional
public class TransferService {
    
    private static final Logger logger = LoggerFactory.getLogger(TransferService.class);
    
    // 鎖相關常數
    private static final long LOCK_WAIT_TIME = 10; // 等待鎖的時間（秒）
    private static final long LOCK_LEASE_TIME = 30; // 鎖持有時間（秒）
    private static final String LOCK_KEY_PREFIX = "account_lock:";
    
    private final AccountService accountService;
    private final TransactionRepository transactionRepository;
    private final DistributedLockFactory lockFactory;
    private final TransactionMetrics transactionMetrics;
    
    @Autowired
    public TransferService(AccountService accountService, 
                          TransactionRepository transactionRepository,
                          DistributedLockFactory lockFactory,
                          @Autowired(required = false) TransactionMetrics transactionMetrics) {
        this.accountService = accountService;
        this.transactionRepository = transactionRepository;
        this.lockFactory = lockFactory;
        this.transactionMetrics = transactionMetrics;
    }
    
    /**
     * 執行匯款操作
     * 使用分散式鎖保護雙帳戶操作，防止併發問題和死鎖
     * 
     * @param fromAccount 轉出帳戶號碼
     * @param toAccount 轉入帳戶號碼
     * @param amount 匯款金額
     * @return 交易結果
     */
    public TransactionResult transfer(String fromAccount, String toAccount, BigDecimal amount) {
        Instant startTime = Instant.now();
        logger.info("開始匯款操作: 從 {} 轉帳 {} 到 {}", fromAccount, amount, toAccount);
        
        // 記錄交易嘗試
        if (transactionMetrics != null) {
            transactionMetrics.recordTransactionAttempt("TRANSFER", amount);
        }
        
        // 參數驗證
        if (fromAccount == null || fromAccount.trim().isEmpty()) {
            Duration duration = Duration.between(startTime, Instant.now());
            if (transactionMetrics != null) {
                transactionMetrics.recordTransactionFailure("TRANSFER", amount, "validation_error", duration);
            }
            return TransactionResult.failure("轉出帳戶號碼不能為空");
        }
        
        if (toAccount == null || toAccount.trim().isEmpty()) {
            Duration duration = Duration.between(startTime, Instant.now());
            if (transactionMetrics != null) {
                transactionMetrics.recordTransactionFailure("TRANSFER", amount, "validation_error", duration);
            }
            return TransactionResult.failure("轉入帳戶號碼不能為空");
        }
        
        if (fromAccount.equals(toAccount)) {
            Duration duration = Duration.between(startTime, Instant.now());
            if (transactionMetrics != null) {
                transactionMetrics.recordTransactionFailure("TRANSFER", amount, "validation_error", duration);
            }
            return TransactionResult.failure("轉出帳戶和轉入帳戶不能相同");
        }
        
        if (amount == null || amount.compareTo(BigDecimal.ZERO) <= 0) {
            Duration duration = Duration.between(startTime, Instant.now());
            if (transactionMetrics != null) {
                transactionMetrics.recordTransactionFailure("TRANSFER", amount != null ? amount : BigDecimal.ZERO, "validation_error", duration);
            }
            return TransactionResult.failure("匯款金額必須大於零");
        }
        
        // 創建交易記錄
        Transaction transaction = new Transaction(fromAccount, toAccount, amount, TransactionType.TRANSFER);
        transaction.setLockProvider(lockFactory.getCurrentProvider());
        transaction.setDescription("匯款交易");
        
        try {
            // 保存初始交易記錄
            transaction = transactionRepository.save(transaction);
            logger.debug("創建交易記錄: {}", transaction.getTransactionId());
            
            // 執行帶鎖的匯款操作
            TransactionResult result = executeTransferWithLock(transaction);
            
            // 記錄交易結果
            Duration duration = Duration.between(startTime, Instant.now());
            if (transactionMetrics != null) {
                if (result.isSuccess()) {
                    transactionMetrics.recordTransactionSuccess("TRANSFER", amount, duration);
                } else {
                    transactionMetrics.recordTransactionFailure("TRANSFER", amount, "business_error", duration);
                }
            }
            
            return result;
            
        } catch (AccountNotFoundException e) {
            Duration duration = Duration.between(startTime, Instant.now());
            if (transactionMetrics != null) {
                transactionMetrics.recordTransactionFailure("TRANSFER", amount, "account_not_found", duration);
            }
            
            logger.error("匯款操作失敗: 從 {} 到 {}, 金額: {}", fromAccount, toAccount, amount, e);
            
            // 更新交易狀態為失敗
            if (transaction.getTransactionId() != null) {
                try {
                    transaction.fail(e.getMessage());
                    transactionRepository.save(transaction);
                } catch (Exception saveException) {
                    logger.error("保存失敗交易記錄時發生異常", saveException);
                }
            }
            
            return TransactionResult.failure("匯款操作失敗: " + e.getMessage());
            
        } catch (InsufficientFundsException e) {
            Duration duration = Duration.between(startTime, Instant.now());
            if (transactionMetrics != null) {
                transactionMetrics.recordTransactionFailure("TRANSFER", amount, "insufficient_funds", duration);
            }
            
            logger.error("匯款操作失敗: 從 {} 到 {}, 金額: {}", fromAccount, toAccount, amount, e);
            
            // 更新交易狀態為失敗
            if (transaction.getTransactionId() != null) {
                try {
                    transaction.fail(e.getMessage());
                    transactionRepository.save(transaction);
                } catch (Exception saveException) {
                    logger.error("保存失敗交易記錄時發生異常", saveException);
                }
            }
            
            return TransactionResult.failure("匯款操作失敗: " + e.getMessage());
            
        } catch (Exception e) {
            Duration duration = Duration.between(startTime, Instant.now());
            if (transactionMetrics != null) {
                transactionMetrics.recordTransactionFailure("TRANSFER", amount, "system_error", duration);
            }
            
            logger.error("匯款操作失敗: 從 {} 到 {}, 金額: {}", fromAccount, toAccount, amount, e);
            
            // 更新交易狀態為失敗
            if (transaction.getTransactionId() != null) {
                try {
                    transaction.fail(e.getMessage());
                    transactionRepository.save(transaction);
                } catch (Exception saveException) {
                    logger.error("保存失敗交易記錄時發生異常", saveException);
                }
            }
            
            return TransactionResult.failure("匯款操作失敗: " + e.getMessage());
        }
    }
    
    /**
     * 執行帶分散式鎖的匯款操作
     * 
     * @param transaction 交易記錄
     * @return 交易結果
     */
    private TransactionResult executeTransferWithLock(Transaction transaction) {
        String fromAccount = transaction.getFromAccount();
        String toAccount = transaction.getToAccount();
        BigDecimal amount = transaction.getAmount();
        
        // 獲取分散式鎖實例
        DistributedLock distributedLock = lockFactory.getDistributedLock();
        
        // 計算鎖的順序，防止死鎖（按字典序排序）
        List<String> lockKeys = getSortedLockKeys(fromAccount, toAccount);
        String firstLockKey = lockKeys.get(0);
        String secondLockKey = lockKeys.get(1);
        
        logger.debug("獲取鎖的順序: {} -> {}", firstLockKey, secondLockKey);
        
        boolean firstLockAcquired = false;
        boolean secondLockAcquired = false;
        
        try {
            // 按順序獲取鎖，防止死鎖
            firstLockAcquired = distributedLock.tryLock(
                    firstLockKey, LOCK_WAIT_TIME, LOCK_LEASE_TIME, TimeUnit.SECONDS);
            
            if (!firstLockAcquired) {
                throw new LockAcquisitionException(firstLockKey, "無法獲取第一個帳戶鎖: " + firstLockKey);
            }
            
            secondLockAcquired = distributedLock.tryLock(
                    secondLockKey, LOCK_WAIT_TIME, LOCK_LEASE_TIME, TimeUnit.SECONDS);
            
            if (!secondLockAcquired) {
                throw new LockAcquisitionException(secondLockKey, "無法獲取第二個帳戶鎖: " + secondLockKey);
            }
            
            logger.debug("成功獲取雙帳戶鎖: {} 和 {}", firstLockKey, secondLockKey);
            
            // 開始處理交易
            transaction.startProcessing();
            transaction = transactionRepository.save(transaction);
            
            // 執行實際的匯款操作
            return performTransfer(transaction);
            
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            logger.error("獲取鎖時被中斷", e);
            transaction.fail("獲取鎖時被中斷");
            transactionRepository.save(transaction);
            return TransactionResult.failure("匯款操作被中斷");
            
        } catch (Exception e) {
            logger.error("執行匯款操作時發生異常", e);
            transaction.fail(e.getMessage());
            transactionRepository.save(transaction);
            return TransactionResult.failure("匯款操作失敗: " + e.getMessage());
            
        } finally {
            // 釋放鎖（按相反順序釋放）
            if (secondLockAcquired) {
                try {
                    distributedLock.unlock(secondLockKey);
                    logger.debug("釋放第二個帳戶鎖: {}", secondLockKey);
                } catch (Exception e) {
                    logger.error("釋放第二個帳戶鎖失敗: {}", secondLockKey, e);
                }
            }
            
            if (firstLockAcquired) {
                try {
                    distributedLock.unlock(firstLockKey);
                    logger.debug("釋放第一個帳戶鎖: {}", firstLockKey);
                } catch (Exception e) {
                    logger.error("釋放第一個帳戶鎖失敗: {}", firstLockKey, e);
                }
            }
        }
    }
    
    /**
     * 執行實際的匯款操作
     * 
     * @param transaction 交易記錄
     * @return 交易結果
     */
    private TransactionResult performTransfer(Transaction transaction) {
        String fromAccount = transaction.getFromAccount();
        String toAccount = transaction.getToAccount();
        BigDecimal amount = transaction.getAmount();
        
        try {
            logger.debug("開始執行匯款業務邏輯: 從 {} 轉帳 {} 到 {}", fromAccount, amount, toAccount);
            
            // 1. 驗證轉出帳戶
            Account fromAccountEntity = accountService.findByAccountNumber(fromAccount);
            accountService.canPerformTransaction(fromAccount);
            accountService.checkSufficientBalance(fromAccount, amount);
            
            // 2. 驗證轉入帳戶
            Account toAccountEntity = accountService.findByAccountNumber(toAccount);
            accountService.canPerformTransaction(toAccount);
            
            // 3. 執行轉帳操作
            BigDecimal newFromBalance = fromAccountEntity.calculateBalanceAfterDebit(amount);
            BigDecimal newToBalance = toAccountEntity.calculateBalanceAfterCredit(amount);
            
            // 4. 更新帳戶餘額
            accountService.updateAccountBalance(fromAccountEntity, newFromBalance);
            accountService.updateAccountBalance(toAccountEntity, newToBalance);
            
            // 5. 完成交易
            transaction.complete();
            transaction = transactionRepository.save(transaction);
            
            logger.info("匯款操作成功完成: 交易ID {}, 從 {} 轉帳 {} 到 {}", 
                    transaction.getTransactionId(), fromAccount, amount, toAccount);
            
            // 6. 返回成功結果
            TransactionResult result = TransactionResult.success(
                    transaction.getTransactionId(), amount, fromAccount, toAccount);
            result.setLockProvider(transaction.getLockProvider());
            
            return result;
            
        } catch (AccountNotFoundException e) {
            logger.error("帳戶不存在: {}", e.getMessage());
            throw e;
        } catch (AccountValidationException e) {
            logger.error("帳戶驗證失敗: {}", e.getMessage());
            throw e;
        } catch (InsufficientFundsException e) {
            logger.error("餘額不足: {}", e.getMessage());
            throw e;
        } catch (Exception e) {
            logger.error("執行匯款業務邏輯時發生未預期的異常", e);
            throw new RuntimeException("匯款操作失敗", e);
        }
    }
    
    /**
     * 獲取排序後的鎖鍵值列表，防止死鎖
     * 
     * @param account1 帳戶1
     * @param account2 帳戶2
     * @return 排序後的鎖鍵值列表
     */
    private List<String> getSortedLockKeys(String account1, String account2) {
        String lockKey1 = LOCK_KEY_PREFIX + account1;
        String lockKey2 = LOCK_KEY_PREFIX + account2;
        
        // 按字典序排序，確保所有執行緒都以相同順序獲取鎖
        if (lockKey1.compareTo(lockKey2) < 0) {
            return Arrays.asList(lockKey1, lockKey2);
        } else {
            return Arrays.asList(lockKey2, lockKey1);
        }
    }
    
    /**
     * 檢查匯款操作的前置條件
     * 
     * @param fromAccount 轉出帳戶號碼
     * @param toAccount 轉入帳戶號碼
     * @param amount 匯款金額
     * @return 檢查結果，如果有問題則返回錯誤訊息
     */
    @Transactional(readOnly = true)
    public String validateTransferPreconditions(String fromAccount, String toAccount, BigDecimal amount) {
        try {
            // 基本參數驗證
            if (fromAccount == null || fromAccount.trim().isEmpty()) {
                return "轉出帳戶號碼不能為空";
            }
            
            if (toAccount == null || toAccount.trim().isEmpty()) {
                return "轉入帳戶號碼不能為空";
            }
            
            if (fromAccount.equals(toAccount)) {
                return "轉出帳戶和轉入帳戶不能相同";
            }
            
            if (amount == null || amount.compareTo(BigDecimal.ZERO) <= 0) {
                return "匯款金額必須大於零";
            }
            
            // 檢查轉出帳戶
            if (!accountService.isActiveAccount(fromAccount)) {
                return "轉出帳戶不存在或不是活躍狀態";
            }
            
            // 檢查轉入帳戶
            if (!accountService.isActiveAccount(toAccount)) {
                return "轉入帳戶不存在或不是活躍狀態";
            }
            
            // 檢查餘額（不拋出異常，只返回檢查結果）
            try {
                accountService.checkSufficientBalance(fromAccount, amount);
            } catch (InsufficientFundsException e) {
                return "轉出帳戶餘額不足";
            }
            
            return null; // 所有檢查都通過
            
        } catch (Exception e) {
            logger.error("驗證匯款前置條件時發生異常", e);
            return "驗證匯款條件時發生異常: " + e.getMessage();
        }
    }
    
    /**
     * 查詢指定帳戶的匯款交易記錄
     * 
     * @param accountNumber 帳戶號碼
     * @return 匯款交易記錄列表
     */
    @Transactional(readOnly = true)
    public List<Transaction> getTransferHistory(String accountNumber) {
        logger.debug("查詢帳戶 {} 的匯款交易記錄", accountNumber);
        
        if (accountNumber == null || accountNumber.trim().isEmpty()) {
            throw new IllegalArgumentException("帳戶號碼不能為空");
        }
        
        return transactionRepository.findByAccountNumber(accountNumber)
                .stream()
                .filter(Transaction::isTransfer)
                .toList();
    }
    
    /**
     * 統計指定帳戶的成功匯款交易數量
     * 
     * @param accountNumber 帳戶號碼
     * @return 成功匯款交易數量
     */
    @Transactional(readOnly = true)
    public long countSuccessfulTransfers(String accountNumber) {
        logger.debug("統計帳戶 {} 的成功匯款交易數量", accountNumber);
        
        if (accountNumber == null || accountNumber.trim().isEmpty()) {
            throw new IllegalArgumentException("帳戶號碼不能為空");
        }
        
        return transactionRepository.countTransactionsByAccountAndStatus(
                accountNumber, TransactionStatus.COMPLETED);
    }
    
    /**
     * 檢查分散式鎖的健康狀態
     * 
     * @return 鎖健康狀態描述
     */
    public String checkLockHealth() {
        try {
            DistributedLock distributedLock = lockFactory.getDistributedLock();
            String testLockKey = "health_check_lock_" + System.currentTimeMillis();
            
            // 嘗試獲取和釋放測試鎖
            boolean acquired = distributedLock.tryLock(testLockKey, 1, 5, TimeUnit.SECONDS);
            if (acquired) {
                distributedLock.unlock(testLockKey);
                return "分散式鎖健康狀態良好，提供者: " + lockFactory.getCurrentProvider();
            } else {
                return "分散式鎖健康檢查失敗：無法獲取測試鎖";
            }
            
        } catch (Exception e) {
            logger.error("分散式鎖健康檢查時發生異常", e);
            return "分散式鎖健康檢查異常: " + e.getMessage();
        }
    }
}