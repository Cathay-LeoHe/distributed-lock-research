package com.example.distributedlock.models;

import com.example.distributedlock.validation.ValidAccountNumber;
import com.example.distributedlock.validation.ValidAccountStatus;
import com.example.distributedlock.validation.ValidBalance;
import com.example.distributedlock.validation.ValidationResult;
import jakarta.persistence.*;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 帳戶實體類別
 */
@Entity
@Table(name = "accounts")
public class Account {
    
    @Id
    @NotBlank(message = "帳戶號碼不能為空")
    @ValidAccountNumber(message = "帳戶號碼格式不正確")
    @Column(name = "account_number", length = 50)
    private String accountNumber;
    
    @NotNull(message = "餘額不能為空")
    @ValidBalance(message = "餘額必須為非負數且不超過限額")
    @Column(name = "balance", precision = 19, scale = 2)
    private BigDecimal balance;
    
    @Enumerated(EnumType.STRING)
    @ValidAccountStatus(message = "帳戶狀態無效")
    @Column(name = "status", length = 20)
    private AccountStatus status;
    
    @CreationTimestamp
    @Column(name = "created_at")
    private LocalDateTime createdAt;
    
    @UpdateTimestamp
    @Column(name = "updated_at")
    private LocalDateTime updatedAt;
    
    @Version
    @Column(name = "version")
    private Long version;
    
    // 預設建構子
    public Account() {
        this.status = AccountStatus.ACTIVE;
        this.balance = BigDecimal.ZERO;
    }
    
    // 建構子
    public Account(String accountNumber, BigDecimal balance) {
        this();
        this.accountNumber = accountNumber;
        this.balance = balance;
    }
    
    // Getters and Setters
    public String getAccountNumber() {
        return accountNumber;
    }
    
    public void setAccountNumber(String accountNumber) {
        this.accountNumber = accountNumber;
    }
    
    public BigDecimal getBalance() {
        return balance;
    }
    
    public void setBalance(BigDecimal balance) {
        this.balance = balance;
    }
    
    public AccountStatus getStatus() {
        return status;
    }
    
    public void setStatus(AccountStatus status) {
        this.status = status;
    }
    
    public LocalDateTime getCreatedAt() {
        return createdAt;
    }
    
    public void setCreatedAt(LocalDateTime createdAt) {
        this.createdAt = createdAt;
    }
    
    public LocalDateTime getUpdatedAt() {
        return updatedAt;
    }
    
    public void setUpdatedAt(LocalDateTime updatedAt) {
        this.updatedAt = updatedAt;
    }
    
    public Long getVersion() {
        return version;
    }
    
    public void setVersion(Long version) {
        this.version = version;
    }
    
    /**
     * 檢查帳戶是否為活躍狀態
     */
    public boolean isActive() {
        return AccountStatus.ACTIVE.equals(this.status);
    }
    
    /**
     * 檢查帳戶是否被凍結
     */
    public boolean isFrozen() {
        return AccountStatus.FROZEN.equals(this.status);
    }
    
    /**
     * 檢查帳戶是否已關閉
     */
    public boolean isClosed() {
        return AccountStatus.CLOSED.equals(this.status);
    }
    
    /**
     * 檢查餘額是否足夠
     */
    public boolean hasSufficientBalance(BigDecimal amount) {
        if (amount == null || amount.compareTo(BigDecimal.ZERO) <= 0) {
            return false;
        }
        return this.balance.compareTo(amount) >= 0;
    }
    
    /**
     * 檢查帳戶狀態是否允許交易
     */
    public boolean canPerformTransaction() {
        return isActive();
    }
    
    /**
     * 扣款操作（不直接修改餘額，返回新的餘額值）
     */
    public BigDecimal calculateBalanceAfterDebit(BigDecimal amount) {
        if (amount == null || amount.compareTo(BigDecimal.ZERO) <= 0) {
            throw new IllegalArgumentException("扣款金額必須大於零");
        }
        if (!hasSufficientBalance(amount)) {
            throw new IllegalArgumentException("餘額不足");
        }
        return this.balance.subtract(amount);
    }
    
    /**
     * 存款操作（不直接修改餘額，返回新的餘額值）
     */
    public BigDecimal calculateBalanceAfterCredit(BigDecimal amount) {
        if (amount == null || amount.compareTo(BigDecimal.ZERO) <= 0) {
            throw new IllegalArgumentException("存款金額必須大於零");
        }
        return this.balance.add(amount);
    }
    
    /**
     * 驗證餘額是否在有效範圍內
     */
    public boolean isBalanceValid() {
        return this.balance != null && this.balance.compareTo(BigDecimal.ZERO) >= 0;
    }
    
    /**
     * 驗證帳戶的完整性和業務規則
     */
    public ValidationResult validateAccount() {
        if (accountNumber == null || accountNumber.trim().isEmpty()) {
            return ValidationResult.failure("帳戶號碼不能為空");
        }
        
        if (!isValidAccountNumber(accountNumber)) {
            return ValidationResult.failure("帳戶號碼格式不正確");
        }
        
        if (!isBalanceValid()) {
            return ValidationResult.failure("帳戶餘額無效");
        }
        
        if (status == null) {
            return ValidationResult.failure("帳戶狀態不能為空");
        }
        
        return ValidationResult.success();
    }
    
    /**
     * 驗證狀態轉換的業務規則
     */
    public ValidationResult validateStatusTransition(AccountStatus newStatus) {
        if (newStatus == null) {
            return ValidationResult.failure("新狀態不能為空");
        }
        
        if (this.status == newStatus) {
            return ValidationResult.failure("帳戶已經是" + newStatus.getDescription() + "狀態");
        }
        
        return switch (this.status) {
            case ACTIVE -> {
                if (newStatus == AccountStatus.FROZEN || 
                    newStatus == AccountStatus.INACTIVE || 
                    newStatus == AccountStatus.CLOSED) {
                    yield ValidationResult.success();
                }
                yield ValidationResult.failure("活躍帳戶只能轉換為凍結、非活躍或關閉狀態");
            }
            case INACTIVE -> {
                if (newStatus == AccountStatus.ACTIVE || newStatus == AccountStatus.CLOSED) {
                    yield ValidationResult.success();
                }
                yield ValidationResult.failure("非活躍帳戶只能轉換為活躍或關閉狀態");
            }
            case FROZEN -> {
                if (newStatus == AccountStatus.ACTIVE || newStatus == AccountStatus.CLOSED) {
                    yield ValidationResult.success();
                }
                yield ValidationResult.failure("凍結帳戶只能轉換為活躍或關閉狀態");
            }
            case CLOSED -> ValidationResult.failure("已關閉的帳戶不能轉換到其他狀態");
        };
    }
    
    /**
     * 檢查帳戶是否可以被凍結
     */
    public boolean canBeFrozen() {
        return isActive() || AccountStatus.INACTIVE.equals(this.status);
    }
    
    /**
     * 檢查帳戶是否可以被關閉
     */
    public boolean canBeClosed() {
        return !isClosed();
    }
    
    /**
     * 檢查帳戶是否可以被重新啟用
     */
    public boolean canBeReactivated() {
        return AccountStatus.INACTIVE.equals(this.status) || isFrozen();
    }
    
    /**
     * 獲取帳戶狀態描述
     */
    public String getStatusDescription() {
        return this.status != null ? this.status.getDescription() : "未知狀態";
    }
    
    /**
     * 驗證帳戶號碼格式
     */
    public static boolean isValidAccountNumber(String accountNumber) {
        return accountNumber != null && 
               accountNumber.trim().length() > 0 && 
               accountNumber.length() <= 50 &&
               accountNumber.matches("^[A-Za-z0-9-]+$");
    }
    
    /**
     * 創建新帳戶的靜態工廠方法
     */
    public static Account createNewAccount(String accountNumber, BigDecimal initialBalance) {
        if (!isValidAccountNumber(accountNumber)) {
            throw new IllegalArgumentException("帳戶號碼格式不正確");
        }
        if (initialBalance == null || initialBalance.compareTo(BigDecimal.ZERO) < 0) {
            throw new IllegalArgumentException("初始餘額不能為負數");
        }
        return new Account(accountNumber, initialBalance);
    }
    
    @Override
    public String toString() {
        return "Account{" +
                "accountNumber='" + accountNumber + '\'' +
                ", balance=" + balance +
                ", status=" + status +
                ", version=" + version +
                '}';
    }
    
    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        Account account = (Account) o;
        return accountNumber != null ? accountNumber.equals(account.accountNumber) : account.accountNumber == null;
    }
    
    @Override
    public int hashCode() {
        return accountNumber != null ? accountNumber.hashCode() : 0;
    }
}