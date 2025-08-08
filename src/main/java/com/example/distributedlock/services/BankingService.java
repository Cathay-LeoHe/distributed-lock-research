package com.example.distributedlock.services;

import com.example.distributedlock.dto.AccountBalance;
import com.example.distributedlock.dto.TransactionResult;

import java.math.BigDecimal;

/**
 * 銀行業務服務介面
 * 定義銀行核心業務操作
 */
public interface BankingService {
    
    /**
     * 匯款操作
     * 
     * @param fromAccount 轉出帳戶號碼
     * @param toAccount 轉入帳戶號碼
     * @param amount 匯款金額
     * @return 交易結果
     */
    TransactionResult transfer(String fromAccount, String toAccount, BigDecimal amount);
    
    /**
     * 扣款操作
     * 
     * @param account 帳戶號碼
     * @param amount 扣款金額
     * @return 交易結果
     */
    TransactionResult withdraw(String account, BigDecimal amount);
    
    /**
     * 查詢帳戶餘額
     * 
     * @param account 帳戶號碼
     * @return 帳戶餘額資訊
     */
    AccountBalance getBalance(String account);
}