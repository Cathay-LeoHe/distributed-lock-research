package com.example.distributedlock.services;

import com.example.distributedlock.dto.AccountBalance;
import com.example.distributedlock.exception.AccountNotFoundException;
import com.example.distributedlock.exception.AccountValidationException;
import com.example.distributedlock.exception.InsufficientFundsException;
import com.example.distributedlock.models.Account;
import com.example.distributedlock.models.AccountStatus;
import com.example.distributedlock.repositories.AccountRepository;
import com.example.distributedlock.validation.ValidationResult;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

/**
 * 帳戶服務實作類別
 * 提供帳戶管理的核心業務邏輯
 */
@Service
@Transactional
public class AccountService {
    
    private static final Logger logger = LoggerFactory.getLogger(AccountService.class);
    
    private final AccountRepository accountRepository;
    
    @Autowired
    public AccountService(AccountRepository accountRepository) {
        this.accountRepository = accountRepository;
    }
    
    /**
     * 根據帳戶號碼查詢帳戶
     * 
     * @param accountNumber 帳戶號碼
     * @return 帳戶實體
     * @throws AccountNotFoundException 當帳戶不存在時
     */
    @Transactional(readOnly = true)
    public Account findByAccountNumber(String accountNumber) {
        logger.debug("查詢帳戶: {}", accountNumber);
        
        if (accountNumber == null || accountNumber.trim().isEmpty()) {
            throw new IllegalArgumentException("帳戶號碼不能為空");
        }
        
        return accountRepository.findById(accountNumber)
                .orElseThrow(() -> new AccountNotFoundException(accountNumber));
    }
    
    /**
     * 使用悲觀鎖查詢帳戶（用於併發控制）
     * 
     * @param accountNumber 帳戶號碼
     * @return 帳戶實體
     * @throws AccountNotFoundException 當帳戶不存在時
     */
    public Account findByAccountNumberWithLock(String accountNumber) {
        logger.debug("使用悲觀鎖查詢帳戶: {}", accountNumber);
        
        if (accountNumber == null || accountNumber.trim().isEmpty()) {
            throw new IllegalArgumentException("帳戶號碼不能為空");
        }
        
        return accountRepository.findByAccountNumberWithLock(accountNumber)
                .orElseThrow(() -> new AccountNotFoundException(accountNumber));
    }
    
    /**
     * 查詢帳戶餘額
     * 
     * @param accountNumber 帳戶號碼
     * @return 帳戶餘額資訊
     * @throws AccountNotFoundException 當帳戶不存在時
     */
    @Transactional(readOnly = true)
    public AccountBalance getAccountBalance(String accountNumber) {
        logger.debug("查詢帳戶餘額: {}", accountNumber);
        
        Account account = findByAccountNumber(accountNumber);
        
        return new AccountBalance(
                account.getAccountNumber(),
                account.getBalance(),
                account.getStatus(),
                account.getUpdatedAt()
        );
    }
    
    /**
     * 檢查帳戶餘額是否足夠
     * 
     * @param accountNumber 帳戶號碼
     * @param amount 所需金額
     * @return true 如果餘額足夠
     * @throws AccountNotFoundException 當帳戶不存在時
     * @throws InsufficientFundsException 當餘額不足時
     */
    @Transactional(readOnly = true)
    public boolean checkSufficientBalance(String accountNumber, BigDecimal amount) {
        logger.debug("檢查帳戶 {} 餘額是否足夠支付 {}", accountNumber, amount);
        
        if (amount == null || amount.compareTo(BigDecimal.ZERO) <= 0) {
            throw new IllegalArgumentException("檢查金額必須大於零");
        }
        
        Account account = findByAccountNumber(accountNumber);
        
        if (!account.hasSufficientBalance(amount)) {
            throw new InsufficientFundsException(
                    accountNumber, 
                    amount, 
                    account.getBalance()
            );
        }
        
        return true;
    }
    
    /**
     * 檢查帳戶是否可以進行交易
     * 
     * @param accountNumber 帳戶號碼
     * @return true 如果帳戶可以進行交易
     * @throws AccountNotFoundException 當帳戶不存在時
     * @throws AccountValidationException 當帳戶狀態不允許交易時
     */
    @Transactional(readOnly = true)
    public boolean canPerformTransaction(String accountNumber) {
        logger.debug("檢查帳戶 {} 是否可以進行交易", accountNumber);
        
        Account account = findByAccountNumber(accountNumber);
        
        if (!account.canPerformTransaction()) {
            throw new AccountValidationException(
                    String.format("帳戶 %s 狀態為 %s，無法進行交易", 
                            accountNumber, account.getStatusDescription()),
                    accountNumber
            );
        }
        
        return true;
    }
    
    /**
     * 創建新帳戶
     * 
     * @param accountNumber 帳戶號碼
     * @param initialBalance 初始餘額
     * @return 創建的帳戶
     * @throws AccountValidationException 當帳戶資料無效時
     */
    public Account createAccount(String accountNumber, BigDecimal initialBalance) {
        logger.info("創建新帳戶: {}, 初始餘額: {}", accountNumber, initialBalance);
        
        // 檢查帳戶是否已存在
        if (accountRepository.existsById(accountNumber)) {
            throw new AccountValidationException("帳戶 " + accountNumber + " 已存在", accountNumber);
        }
        
        // 創建帳戶
        Account account = Account.createNewAccount(accountNumber, initialBalance);
        
        // 驗證帳戶
        ValidationResult validationResult = account.validateAccount();
        if (!validationResult.isValid()) {
            throw new AccountValidationException("帳戶驗證失敗: " + validationResult.getErrorMessage(), accountNumber);
        }
        
        Account savedAccount = accountRepository.save(account);
        logger.info("成功創建帳戶: {}", savedAccount.getAccountNumber());
        
        return savedAccount;
    }
    
    /**
     * 更新帳戶狀態
     * 
     * @param accountNumber 帳戶號碼
     * @param newStatus 新狀態
     * @return 更新後的帳戶
     * @throws AccountNotFoundException 當帳戶不存在時
     * @throws AccountValidationException 當狀態轉換無效時
     */
    public Account updateAccountStatus(String accountNumber, AccountStatus newStatus) {
        logger.info("更新帳戶 {} 狀態為 {}", accountNumber, newStatus);
        
        Account account = findByAccountNumber(accountNumber);
        
        // 驗證狀態轉換
        ValidationResult validationResult = account.validateStatusTransition(newStatus);
        if (!validationResult.isValid()) {
            throw new AccountValidationException(
                    "狀態轉換失敗: " + validationResult.getErrorMessage(),
                    accountNumber
            );
        }
        
        account.setStatus(newStatus);
        Account updatedAccount = accountRepository.save(account);
        
        logger.info("成功更新帳戶 {} 狀態為 {}", accountNumber, newStatus.getDescription());
        
        return updatedAccount;
    }
    
    /**
     * 凍結帳戶
     * 
     * @param accountNumber 帳戶號碼
     * @return 更新後的帳戶
     */
    public Account freezeAccount(String accountNumber) {
        logger.info("凍結帳戶: {}", accountNumber);
        return updateAccountStatus(accountNumber, AccountStatus.FROZEN);
    }
    
    /**
     * 解凍帳戶（重新啟用）
     * 
     * @param accountNumber 帳戶號碼
     * @return 更新後的帳戶
     */
    public Account unfreezeAccount(String accountNumber) {
        logger.info("解凍帳戶: {}", accountNumber);
        return updateAccountStatus(accountNumber, AccountStatus.ACTIVE);
    }
    
    /**
     * 關閉帳戶
     * 
     * @param accountNumber 帳戶號碼
     * @return 更新後的帳戶
     */
    public Account closeAccount(String accountNumber) {
        logger.info("關閉帳戶: {}", accountNumber);
        
        Account account = findByAccountNumber(accountNumber);
        
        // 檢查帳戶餘額，如果有餘額則不能關閉
        if (account.getBalance().compareTo(BigDecimal.ZERO) > 0) {
            throw new AccountValidationException(
                    String.format("帳戶 %s 仍有餘額 %s，無法關閉", 
                            accountNumber, account.getBalance()),
                    accountNumber
            );
        }
        
        return updateAccountStatus(accountNumber, AccountStatus.CLOSED);
    }
    
    /**
     * 查詢所有活躍帳戶
     * 
     * @return 活躍帳戶列表
     */
    @Transactional(readOnly = true)
    public List<Account> findActiveAccounts() {
        logger.debug("查詢所有活躍帳戶");
        return accountRepository.findByStatus(AccountStatus.ACTIVE);
    }
    
    /**
     * 查詢餘額大於指定金額的帳戶
     * 
     * @param minBalance 最小餘額
     * @return 符合條件的帳戶列表
     */
    @Transactional(readOnly = true)
    public List<Account> findAccountsWithMinBalance(BigDecimal minBalance) {
        logger.debug("查詢餘額大於 {} 的帳戶", minBalance);
        
        if (minBalance == null) {
            throw new IllegalArgumentException("最小餘額不能為空");
        }
        
        return accountRepository.findAccountsWithMinBalance(minBalance);
    }
    
    /**
     * 檢查帳戶是否存在且為活躍狀態
     * 
     * @param accountNumber 帳戶號碼
     * @return true 如果帳戶存在且為活躍狀態
     */
    @Transactional(readOnly = true)
    public boolean isActiveAccount(String accountNumber) {
        logger.debug("檢查帳戶 {} 是否為活躍狀態", accountNumber);
        
        if (accountNumber == null || accountNumber.trim().isEmpty()) {
            return false;
        }
        
        return accountRepository.existsActiveAccount(accountNumber);
    }
    
    /**
     * 更新帳戶餘額（內部方法，用於交易操作）
     * 注意：此方法不進行業務驗證，調用前需確保已進行適當的檢查
     * 
     * @param account 帳戶實體
     * @param newBalance 新餘額
     * @return 更新後的帳戶
     */
    protected Account updateAccountBalance(Account account, BigDecimal newBalance) {
        logger.debug("更新帳戶 {} 餘額從 {} 到 {}", 
                account.getAccountNumber(), account.getBalance(), newBalance);
        
        if (newBalance == null || newBalance.compareTo(BigDecimal.ZERO) < 0) {
            throw new IllegalArgumentException("新餘額不能為負數");
        }
        
        account.setBalance(newBalance);
        return accountRepository.save(account);
    }
}