package com.example.distributedlock.services;

import com.example.distributedlock.dto.TransactionResult;
import com.example.distributedlock.exception.AccountNotFoundException;
import com.example.distributedlock.exception.AccountValidationException;
import com.example.distributedlock.exception.InsufficientFundsException;
import com.example.distributedlock.exception.LockAcquisitionException;
import com.example.distributedlock.factory.DistributedLockFactory;
import com.example.distributedlock.lock.DistributedLock;
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
import java.util.List;
import java.util.concurrent.TimeUnit;

/**
 * 扣款服務實作類別
 * 使用分散式鎖保護扣款操作，確保資料一致性
 */
@Service
@Transactional
public class WithdrawalService {
    
    private static final Logger logger = LoggerFactory.getLogger(WithdrawalService.class);
    
    // 鎖相關常數
    private static final long LOCK_WAIT_TIME = 10; // 等待鎖的時間（秒）
    private static final long LOCK_LEASE_TIME = 30; // 鎖持有時間（秒）
    private static final String LOCK_KEY_PREFIX = "account_lock:";
    
    private final AccountService accountService;
    private final TransactionRepository transactionRepository;
    private final DistributedLockFactory lockFactory;
    
    @Autowired
    public WithdrawalService(AccountService accountService, 
                            TransactionRepository transactionRepository,
                            DistributedLockFactory lockFactory) {
        this.accountService = accountService;
        this.transactionRepository = transactionRepository;
        this.lockFactory = lockFactory;
    }
    
    /**
     * 執行扣款操作
     * 使用分散式鎖保護帳戶操作，防止併發問題
     * 
     * @param accountNumber 扣款帳戶號碼
     * @param amount 扣款金額
     * @return 交易結果
     */
    public TransactionResult withdraw(String accountNumber, BigDecimal amount) {
        logger.info("開始扣款操作: 從帳戶 {} 扣款 {}", accountNumber, amount);
        
        // 參數驗證
        if (accountNumber == null || accountNumber.trim().isEmpty()) {
            return TransactionResult.failure("扣款帳戶號碼不能為空");
        }
        
        if (amount == null || amount.compareTo(BigDecimal.ZERO) <= 0) {
            return TransactionResult.failure("扣款金額必須大於零");
        }
        
        // 創建交易記錄
        Transaction transaction = new Transaction(accountNumber, null, amount, TransactionType.WITHDRAWAL);
        transaction.setLockProvider(lockFactory.getCurrentProvider());
        transaction.setDescription("扣款交易");
        
        try {
            // 保存初始交易記錄
            transaction = transactionRepository.save(transaction);
            logger.debug("創建交易記錄: {}", transaction.getTransactionId());
            
            // 執行帶鎖的扣款操作
            return executeWithdrawalWithLock(transaction);
            
        } catch (Exception e) {
            logger.error("扣款操作失敗: 帳戶 {}, 金額: {}", accountNumber, amount, e);
            
            // 更新交易狀態為失敗
            if (transaction.getTransactionId() != null) {
                try {
                    transaction.fail(e.getMessage());
                    transactionRepository.save(transaction);
                } catch (Exception saveException) {
                    logger.error("保存失敗交易記錄時發生異常", saveException);
                }
            }
            
            return TransactionResult.failure("扣款操作失敗: " + e.getMessage());
        }
    }
    
    /**
     * 執行帶分散式鎖的扣款操作
     * 
     * @param transaction 交易記錄
     * @return 交易結果
     */
    private TransactionResult executeWithdrawalWithLock(Transaction transaction) {
        String accountNumber = transaction.getFromAccount();
        BigDecimal amount = transaction.getAmount();
        
        // 獲取分散式鎖實例
        DistributedLock distributedLock = lockFactory.getDistributedLock();
        String lockKey = LOCK_KEY_PREFIX + accountNumber;
        
        logger.debug("嘗試獲取帳戶鎖: {}", lockKey);
        
        boolean lockAcquired = false;
        
        try {
            // 獲取帳戶鎖
            lockAcquired = distributedLock.tryLock(
                    lockKey, LOCK_WAIT_TIME, LOCK_LEASE_TIME, TimeUnit.SECONDS);
            
            if (!lockAcquired) {
                throw new LockAcquisitionException(lockKey, "無法獲取帳戶鎖: " + lockKey);
            }
            
            logger.debug("成功獲取帳戶鎖: {}", lockKey);
            
            // 開始處理交易
            transaction.startProcessing();
            transaction = transactionRepository.save(transaction);
            
            // 執行實際的扣款操作
            return performWithdrawal(transaction);
            
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            logger.error("獲取鎖時被中斷", e);
            transaction.fail("獲取鎖時被中斷");
            transactionRepository.save(transaction);
            return TransactionResult.failure("扣款操作被中斷");
            
        } catch (Exception e) {
            logger.error("執行扣款操作時發生異常", e);
            transaction.fail(e.getMessage());
            transactionRepository.save(transaction);
            return TransactionResult.failure("扣款操作失敗: " + e.getMessage());
            
        } finally {
            // 釋放鎖
            if (lockAcquired) {
                try {
                    distributedLock.unlock(lockKey);
                    logger.debug("釋放帳戶鎖: {}", lockKey);
                } catch (Exception e) {
                    logger.error("釋放帳戶鎖失敗: {}", lockKey, e);
                }
            }
        }
    }
    
    /**
     * 執行實際的扣款操作
     * 
     * @param transaction 交易記錄
     * @return 交易結果
     */
    private TransactionResult performWithdrawal(Transaction transaction) {
        String accountNumber = transaction.getFromAccount();
        BigDecimal amount = transaction.getAmount();
        
        try {
            logger.debug("開始執行扣款業務邏輯: 從帳戶 {} 扣款 {}", accountNumber, amount);
            
            // 1. 驗證帳戶
            Account account = accountService.findByAccountNumber(accountNumber);
            accountService.canPerformTransaction(accountNumber);
            accountService.checkSufficientBalance(accountNumber, amount);
            
            // 2. 執行扣款操作
            BigDecimal newBalance = account.calculateBalanceAfterDebit(amount);
            
            // 3. 更新帳戶餘額
            accountService.updateAccountBalance(account, newBalance);
            
            // 4. 完成交易
            transaction.complete();
            transaction = transactionRepository.save(transaction);
            
            logger.info("扣款操作成功完成: 交易ID {}, 帳戶 {} 扣款 {}", 
                    transaction.getTransactionId(), accountNumber, amount);
            
            // 5. 返回成功結果
            TransactionResult result = TransactionResult.success(
                    transaction.getTransactionId(), amount, accountNumber, null);
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
            logger.error("執行扣款業務邏輯時發生未預期的異常", e);
            throw new RuntimeException("扣款操作失敗", e);
        }
    }
    
    /**
     * 檢查扣款操作的前置條件
     * 
     * @param accountNumber 扣款帳戶號碼
     * @param amount 扣款金額
     * @return 檢查結果，如果有問題則返回錯誤訊息
     */
    @Transactional(readOnly = true)
    public String validateWithdrawalPreconditions(String accountNumber, BigDecimal amount) {
        try {
            // 基本參數驗證
            if (accountNumber == null || accountNumber.trim().isEmpty()) {
                return "扣款帳戶號碼不能為空";
            }
            
            if (amount == null || amount.compareTo(BigDecimal.ZERO) <= 0) {
                return "扣款金額必須大於零";
            }
            
            // 檢查帳戶
            if (!accountService.isActiveAccount(accountNumber)) {
                return "扣款帳戶不存在或不是活躍狀態";
            }
            
            // 檢查餘額（不拋出異常，只返回檢查結果）
            try {
                accountService.checkSufficientBalance(accountNumber, amount);
            } catch (InsufficientFundsException e) {
                return "帳戶餘額不足";
            }
            
            return null; // 所有檢查都通過
            
        } catch (Exception e) {
            logger.error("驗證扣款前置條件時發生異常", e);
            return "驗證扣款條件時發生異常: " + e.getMessage();
        }
    }
    
    /**
     * 查詢指定帳戶的扣款交易記錄
     * 
     * @param accountNumber 帳戶號碼
     * @return 扣款交易記錄列表
     */
    @Transactional(readOnly = true)
    public List<Transaction> getWithdrawalHistory(String accountNumber) {
        logger.debug("查詢帳戶 {} 的扣款交易記錄", accountNumber);
        
        if (accountNumber == null || accountNumber.trim().isEmpty()) {
            throw new IllegalArgumentException("帳戶號碼不能為空");
        }
        
        return transactionRepository.findByAccountNumber(accountNumber)
                .stream()
                .filter(Transaction::isWithdrawal)
                .toList();
    }
    
    /**
     * 統計指定帳戶的成功扣款交易數量
     * 
     * @param accountNumber 帳戶號碼
     * @return 成功扣款交易數量
     */
    @Transactional(readOnly = true)
    public long countSuccessfulWithdrawals(String accountNumber) {
        logger.debug("統計帳戶 {} 的成功扣款交易數量", accountNumber);
        
        if (accountNumber == null || accountNumber.trim().isEmpty()) {
            throw new IllegalArgumentException("帳戶號碼不能為空");
        }
        
        return transactionRepository.countTransactionsByAccountAndStatus(
                accountNumber, TransactionStatus.COMPLETED);
    }
    
    /**
     * 計算指定帳戶的總扣款金額
     * 
     * @param accountNumber 帳戶號碼
     * @return 總扣款金額
     */
    @Transactional(readOnly = true)
    public BigDecimal getTotalWithdrawalAmount(String accountNumber) {
        logger.debug("計算帳戶 {} 的總扣款金額", accountNumber);
        
        if (accountNumber == null || accountNumber.trim().isEmpty()) {
            throw new IllegalArgumentException("帳戶號碼不能為空");
        }
        
        List<Transaction> withdrawals = getWithdrawalHistory(accountNumber);
        
        return withdrawals.stream()
                .filter(Transaction::isCompleted)
                .map(Transaction::getAmount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }
    
    /**
     * 檢查帳戶是否可以進行指定金額的扣款
     * 
     * @param accountNumber 帳戶號碼
     * @param amount 扣款金額
     * @return true 如果可以扣款
     */
    @Transactional(readOnly = true)
    public boolean canWithdraw(String accountNumber, BigDecimal amount) {
        try {
            return validateWithdrawalPreconditions(accountNumber, amount) == null;
        } catch (Exception e) {
            logger.error("檢查扣款條件時發生異常", e);
            return false;
        }
    }
    
    /**
     * 批量扣款操作
     * 對多個帳戶進行扣款，使用分散式鎖確保原子性
     * 
     * @param withdrawalRequests 扣款請求列表
     * @return 批量扣款結果
     */
    public BatchWithdrawalResult batchWithdraw(List<WithdrawalRequest> withdrawalRequests) {
        if (withdrawalRequests == null || withdrawalRequests.isEmpty()) {
            return new BatchWithdrawalResult(0, 0, "批量扣款請求列表不能為空");
        }
        
        logger.info("開始批量扣款操作，共 {} 筆請求", withdrawalRequests.size());
        
        int successCount = 0;
        int failureCount = 0;
        StringBuilder errorMessages = new StringBuilder();
        
        for (WithdrawalRequest request : withdrawalRequests) {
            try {
                TransactionResult result = withdraw(request.getAccountNumber(), request.getAmount());
                if (result.isSuccess()) {
                    successCount++;
                } else {
                    failureCount++;
                    errorMessages.append(String.format("帳戶 %s: %s; ", 
                            request.getAccountNumber(), result.getMessage()));
                }
            } catch (Exception e) {
                failureCount++;
                errorMessages.append(String.format("帳戶 %s: %s; ", 
                        request.getAccountNumber(), e.getMessage()));
                logger.error("批量扣款中單筆操作失敗", e);
            }
        }
        
        logger.info("批量扣款操作完成: 成功 {} 筆，失敗 {} 筆", successCount, failureCount);
        
        return new BatchWithdrawalResult(successCount, failureCount, 
                errorMessages.length() > 0 ? errorMessages.toString() : null);
    }
    
    /**
     * 扣款請求類別
     */
    public static class WithdrawalRequest {
        private String accountNumber;
        private BigDecimal amount;
        
        public WithdrawalRequest() {}
        
        public WithdrawalRequest(String accountNumber, BigDecimal amount) {
            this.accountNumber = accountNumber;
            this.amount = amount;
        }
        
        public String getAccountNumber() {
            return accountNumber;
        }
        
        public void setAccountNumber(String accountNumber) {
            this.accountNumber = accountNumber;
        }
        
        public BigDecimal getAmount() {
            return amount;
        }
        
        public void setAmount(BigDecimal amount) {
            this.amount = amount;
        }
    }
    
    /**
     * 批量扣款結果類別
     */
    public static class BatchWithdrawalResult {
        private int successCount;
        private int failureCount;
        private String errorMessages;
        
        public BatchWithdrawalResult(int successCount, int failureCount, String errorMessages) {
            this.successCount = successCount;
            this.failureCount = failureCount;
            this.errorMessages = errorMessages;
        }
        
        public int getSuccessCount() {
            return successCount;
        }
        
        public int getFailureCount() {
            return failureCount;
        }
        
        public String getErrorMessages() {
            return errorMessages;
        }
        
        public boolean isAllSuccess() {
            return failureCount == 0;
        }
        
        public int getTotalCount() {
            return successCount + failureCount;
        }
    }
    
    /**
     * 檢查分散式鎖的健康狀態
     * 
     * @return 鎖健康狀態描述
     */
    public String checkLockHealth() {
        try {
            DistributedLock distributedLock = lockFactory.getDistributedLock();
            String testLockKey = "withdrawal_health_check_lock_" + System.currentTimeMillis();
            
            // 嘗試獲取和釋放測試鎖
            boolean acquired = distributedLock.tryLock(testLockKey, 1, 5, TimeUnit.SECONDS);
            if (acquired) {
                distributedLock.unlock(testLockKey);
                return "扣款服務分散式鎖健康狀態良好，提供者: " + lockFactory.getCurrentProvider();
            } else {
                return "扣款服務分散式鎖健康檢查失敗：無法獲取測試鎖";
            }
            
        } catch (Exception e) {
            logger.error("扣款服務分散式鎖健康檢查時發生異常", e);
            return "扣款服務分散式鎖健康檢查異常: " + e.getMessage();
        }
    }
}