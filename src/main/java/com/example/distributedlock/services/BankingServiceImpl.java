package com.example.distributedlock.services;

import com.example.distributedlock.dto.AccountBalance;
import com.example.distributedlock.dto.TransactionResult;
import com.example.distributedlock.exception.AccountNotFoundException;
import com.example.distributedlock.models.Account;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;

/**
 * 銀行業務服務實作
 * 協調各個業務服務組件
 */
@Service
public class BankingServiceImpl implements BankingService {
    
    private static final Logger logger = LoggerFactory.getLogger(BankingServiceImpl.class);
    
    private final AccountService accountService;
    private final TransferService transferService;
    private final WithdrawalService withdrawalService;
    
    @Autowired
    public BankingServiceImpl(AccountService accountService, 
                             TransferService transferService,
                             WithdrawalService withdrawalService) {
        this.accountService = accountService;
        this.transferService = transferService;
        this.withdrawalService = withdrawalService;
    }
    
    @Override
    public TransactionResult transfer(String fromAccount, String toAccount, BigDecimal amount) {
        logger.info("執行匯款操作: 從 {} 轉帳 {} 到 {}", fromAccount, amount, toAccount);
        
        try {
            // 驗證輸入參數
            if (fromAccount == null || fromAccount.trim().isEmpty()) {
                return TransactionResult.failure("轉出帳戶不能為空");
            }
            if (toAccount == null || toAccount.trim().isEmpty()) {
                return TransactionResult.failure("轉入帳戶不能為空");
            }
            if (amount == null || amount.compareTo(BigDecimal.ZERO) <= 0) {
                return TransactionResult.failure("匯款金額必須大於0");
            }
            if (fromAccount.equals(toAccount)) {
                return TransactionResult.failure("轉出帳戶和轉入帳戶不能相同");
            }
            
            // 檢查帳戶是否存在
            Account fromAcc = accountService.findByAccountNumber(fromAccount);
            if (fromAcc == null) {
                return TransactionResult.failure("轉出帳戶不存在: " + fromAccount);
            }
            
            Account toAcc = accountService.findByAccountNumber(toAccount);
            if (toAcc == null) {
                return TransactionResult.failure("轉入帳戶不存在: " + toAccount);
            }
            
            // 執行匯款操作
            TransactionResult result = transferService.transfer(fromAccount, toAccount, amount);
            
            if (result.isSuccess()) {
                logger.info("匯款操作成功: 交易ID={}", result.getTransactionId());
            } else {
                logger.warn("匯款操作失敗: {}", result.getMessage());
            }
            
            return result;
            
        } catch (Exception e) {
            logger.error("匯款操作異常", e);
            return TransactionResult.failure("匯款操作失敗: " + e.getMessage());
        }
    }
    
    @Override
    public TransactionResult withdraw(String account, BigDecimal amount) {
        logger.info("執行扣款操作: 從帳戶 {} 扣款 {}", account, amount);
        
        try {
            // 驗證輸入參數
            if (account == null || account.trim().isEmpty()) {
                return TransactionResult.failure("帳戶號碼不能為空");
            }
            if (amount == null || amount.compareTo(BigDecimal.ZERO) <= 0) {
                return TransactionResult.failure("扣款金額必須大於0");
            }
            
            // 檢查帳戶是否存在
            Account acc = accountService.findByAccountNumber(account);
            if (acc == null) {
                return TransactionResult.failure("帳戶不存在: " + account);
            }
            
            // 執行扣款操作
            TransactionResult result = withdrawalService.withdraw(account, amount);
            
            if (result.isSuccess()) {
                logger.info("扣款操作成功: 交易ID={}", result.getTransactionId());
            } else {
                logger.warn("扣款操作失敗: {}", result.getMessage());
            }
            
            return result;
            
        } catch (Exception e) {
            logger.error("扣款操作異常", e);
            return TransactionResult.failure("扣款操作失敗: " + e.getMessage());
        }
    }
    
    @Override
    public AccountBalance getBalance(String account) {
        logger.info("查詢帳戶餘額: {}", account);
        
        try {
            // 驗證輸入參數
            if (account == null || account.trim().isEmpty()) {
                logger.warn("帳戶號碼為空");
                return null;
            }
            
            // 查詢帳戶資訊
            Account acc = accountService.findByAccountNumber(account);
            if (acc == null) {
                logger.warn("帳戶不存在: {}", account);
                return null;
            }
            
            // 轉換為 DTO
            AccountBalance balance = new AccountBalance(
                acc.getAccountNumber(),
                acc.getBalance(),
                acc.getStatus(),
                acc.getUpdatedAt()
            );
            
            logger.info("查詢餘額成功: 帳戶={}, 餘額={}", account, balance.getBalance());
            return balance;
            
        } catch (Exception e) {
            logger.error("查詢餘額異常", e);
            return null;
        }
    }
}