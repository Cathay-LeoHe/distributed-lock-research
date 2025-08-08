package com.example.distributedlock.dto;

import com.example.distributedlock.validation.ValidBalance;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;

/**
 * 扣款請求 DTO
 */
public class WithdrawalRequest {
    
    @NotBlank(message = "帳戶號碼不能為空")
    private String accountNumber;
    
    @NotNull(message = "金額不能為空")
    @ValidBalance(message = "金額必須大於0")
    private BigDecimal amount;
    
    // 預設建構子
    public WithdrawalRequest() {
    }
    
    // 建構子
    public WithdrawalRequest(String accountNumber, BigDecimal amount) {
        this.accountNumber = accountNumber;
        this.amount = amount;
    }
    
    // Getters and Setters
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
    
    @Override
    public String toString() {
        return "WithdrawalRequest{" +
                "accountNumber='" + accountNumber + '\'' +
                ", amount=" + amount +
                '}';
    }
}