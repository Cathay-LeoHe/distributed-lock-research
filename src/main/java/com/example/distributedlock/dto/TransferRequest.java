package com.example.distributedlock.dto;

import com.example.distributedlock.validation.ValidBalance;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;

/**
 * 匯款請求 DTO
 */
public class TransferRequest {
    
    @NotBlank(message = "轉出帳戶不能為空")
    private String fromAccount;
    
    @NotBlank(message = "轉入帳戶不能為空")
    private String toAccount;
    
    @NotNull(message = "金額不能為空")
    @ValidBalance(message = "金額必須大於0")
    private BigDecimal amount;
    
    // 預設建構子
    public TransferRequest() {
    }
    
    // 建構子
    public TransferRequest(String fromAccount, String toAccount, BigDecimal amount) {
        this.fromAccount = fromAccount;
        this.toAccount = toAccount;
        this.amount = amount;
    }
    
    // Getters and Setters
    public String getFromAccount() {
        return fromAccount;
    }
    
    public void setFromAccount(String fromAccount) {
        this.fromAccount = fromAccount;
    }
    
    public String getToAccount() {
        return toAccount;
    }
    
    public void setToAccount(String toAccount) {
        this.toAccount = toAccount;
    }
    
    public BigDecimal getAmount() {
        return amount;
    }
    
    public void setAmount(BigDecimal amount) {
        this.amount = amount;
    }
    
    @Override
    public String toString() {
        return "TransferRequest{" +
                "fromAccount='" + fromAccount + '\'' +
                ", toAccount='" + toAccount + '\'' +
                ", amount=" + amount +
                '}';
    }
}