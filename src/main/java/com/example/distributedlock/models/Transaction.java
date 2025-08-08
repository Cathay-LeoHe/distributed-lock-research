package com.example.distributedlock.models;

import com.example.distributedlock.validation.ValidTransactionAmount;
import com.example.distributedlock.validation.ValidTransactionData;
import com.example.distributedlock.validation.ValidTransactionStatus;
import com.example.distributedlock.validation.ValidationResult;
import jakarta.persistence.*;

import jakarta.validation.constraints.NotNull;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.GenericGenerator;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 交易實體類別
 */
@Entity
@Table(name = "transactions")
@ValidTransactionData
public class Transaction {
    
    @Id
    @GeneratedValue(generator = "uuid")
    @GenericGenerator(name = "uuid", strategy = "uuid2")
    @Column(name = "transaction_id", length = 36)
    private String transactionId;
    
    @Column(name = "from_account", length = 50)
    private String fromAccount;
    
    @Column(name = "to_account", length = 50)
    private String toAccount;
    
    @NotNull(message = "交易金額不能為空")
    @ValidTransactionAmount(min = "0.01", max = "1000000.00")
    @Column(name = "amount", precision = 19, scale = 2)
    private BigDecimal amount;
    
    @Enumerated(EnumType.STRING)
    @Column(name = "type", length = 20)
    private TransactionType type;
    
    @Enumerated(EnumType.STRING)
    @ValidTransactionStatus(message = "交易狀態無效")
    @Column(name = "status", length = 20)
    private TransactionStatus status;
    
    @CreationTimestamp
    @Column(name = "created_at")
    private LocalDateTime createdAt;
    
    @Column(name = "lock_provider", length = 20)
    private String lockProvider;
    
    @Column(name = "description", length = 255)
    private String description;
    
    // 預設建構子
    public Transaction() {
        this.status = TransactionStatus.PENDING;
    }
    
    // 建構子
    public Transaction(String fromAccount, String toAccount, BigDecimal amount, TransactionType type) {
        this();
        this.fromAccount = fromAccount;
        this.toAccount = toAccount;
        this.amount = amount;
        this.type = type;
    }
    
    // Getters and Setters
    public String getTransactionId() {
        return transactionId;
    }
    
    public void setTransactionId(String transactionId) {
        this.transactionId = transactionId;
    }
    
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
    
    public TransactionType getType() {
        return type;
    }
    
    public void setType(TransactionType type) {
        this.type = type;
    }
    
    public TransactionStatus getStatus() {
        return status;
    }
    
    public void setStatus(TransactionStatus status) {
        this.status = status;
    }
    
    public LocalDateTime getCreatedAt() {
        return createdAt;
    }
    
    public void setCreatedAt(LocalDateTime createdAt) {
        this.createdAt = createdAt;
    }
    
    public String getLockProvider() {
        return lockProvider;
    }
    
    public void setLockProvider(String lockProvider) {
        this.lockProvider = lockProvider;
    }
    
    public String getDescription() {
        return description;
    }
    
    public void setDescription(String description) {
        this.description = description;
    }
    
    /**
     * 檢查交易是否為轉帳類型
     */
    public boolean isTransfer() {
        return TransactionType.TRANSFER.equals(this.type);
    }
    
    /**
     * 檢查交易是否為扣款類型
     */
    public boolean isWithdrawal() {
        return TransactionType.WITHDRAWAL.equals(this.type);
    }
    
    /**
     * 檢查交易是否為存款類型
     */
    public boolean isDeposit() {
        return TransactionType.DEPOSIT.equals(this.type);
    }
    
    /**
     * 檢查交易是否已完成
     */
    public boolean isCompleted() {
        return TransactionStatus.COMPLETED.equals(this.status);
    }
    
    /**
     * 檢查交易是否失敗
     */
    public boolean isFailed() {
        return TransactionStatus.FAILED.equals(this.status);
    }
    
    /**
     * 檢查交易是否正在處理中
     */
    public boolean isProcessing() {
        return TransactionStatus.PROCESSING.equals(this.status);
    }
    
    /**
     * 檢查交易是否待處理
     */
    public boolean isPending() {
        return TransactionStatus.PENDING.equals(this.status);
    }
    
    /**
     * 檢查交易是否已取消
     */
    public boolean isCancelled() {
        return TransactionStatus.CANCELLED.equals(this.status);
    }
    
    /**
     * 開始處理交易（狀態轉換：PENDING -> PROCESSING）
     */
    public void startProcessing() {
        if (!isPending()) {
            throw new IllegalStateException("只有待處理狀態的交易才能開始處理");
        }
        this.status = TransactionStatus.PROCESSING;
    }
    
    /**
     * 完成交易（狀態轉換：PROCESSING -> COMPLETED）
     */
    public void complete() {
        if (!isProcessing()) {
            throw new IllegalStateException("只有處理中狀態的交易才能完成");
        }
        this.status = TransactionStatus.COMPLETED;
    }
    
    /**
     * 交易失敗（狀態轉換：PENDING/PROCESSING -> FAILED）
     */
    public void fail(String reason) {
        if (isCompleted() || isCancelled()) {
            throw new IllegalStateException("已完成或已取消的交易不能設為失敗");
        }
        this.status = TransactionStatus.FAILED;
        if (reason != null && !reason.trim().isEmpty()) {
            this.description = (this.description != null ? this.description + "; " : "") + "失敗原因: " + reason;
        }
    }
    
    /**
     * 取消交易（狀態轉換：PENDING -> CANCELLED）
     */
    public void cancel(String reason) {
        if (!isPending()) {
            throw new IllegalStateException("只有待處理狀態的交易才能取消");
        }
        this.status = TransactionStatus.CANCELLED;
        if (reason != null && !reason.trim().isEmpty()) {
            this.description = (this.description != null ? this.description + "; " : "") + "取消原因: " + reason;
        }
    }
    
    /**
     * 檢查交易狀態轉換是否有效
     */
    public boolean canTransitionTo(TransactionStatus newStatus) {
        if (newStatus == null) {
            return false;
        }
        
        return switch (this.status) {
            case PENDING -> newStatus == TransactionStatus.PROCESSING || 
                           newStatus == TransactionStatus.CANCELLED ||
                           newStatus == TransactionStatus.FAILED;
            case PROCESSING -> newStatus == TransactionStatus.COMPLETED || 
                              newStatus == TransactionStatus.FAILED;
            case COMPLETED, FAILED, CANCELLED -> false; // 終態，不能再轉換
        };
    }
    
    /**
     * 驗證交易資料的完整性
     */
    public boolean isValid() {
        if (amount == null || amount.compareTo(BigDecimal.ZERO) <= 0) {
            return false;
        }
        
        return switch (type) {
            case TRANSFER -> fromAccount != null && !fromAccount.trim().isEmpty() &&
                           toAccount != null && !toAccount.trim().isEmpty() &&
                           !fromAccount.equals(toAccount);
            case WITHDRAWAL -> fromAccount != null && !fromAccount.trim().isEmpty() &&
                             toAccount == null;
            case DEPOSIT -> toAccount != null && !toAccount.trim().isEmpty() &&
                           fromAccount == null;
        };
    }
    
    /**
     * 獲取交易涉及的帳戶列表
     */
    public java.util.List<String> getInvolvedAccounts() {
        java.util.List<String> accounts = new java.util.ArrayList<>();
        if (fromAccount != null && !fromAccount.trim().isEmpty()) {
            accounts.add(fromAccount);
        }
        if (toAccount != null && !toAccount.trim().isEmpty()) {
            accounts.add(toAccount);
        }
        return accounts;
    }
    
    /**
     * 驗證交易的完整性和業務規則
     */
    public ValidationResult validateTransaction() {
        if (amount == null || amount.compareTo(BigDecimal.ZERO) <= 0) {
            return ValidationResult.failure("交易金額必須大於零");
        }
        
        if (type == null) {
            return ValidationResult.failure("交易類型不能為空");
        }
        
        if (status == null) {
            return ValidationResult.failure("交易狀態不能為空");
        }
        
        // 根據交易類型驗證帳戶資訊
        return switch (type) {
            case TRANSFER -> validateTransferData();
            case WITHDRAWAL -> validateWithdrawalData();
            case DEPOSIT -> validateDepositData();
        };
    }
    
    /**
     * 驗證轉帳交易資料
     */
    private ValidationResult validateTransferData() {
        if (fromAccount == null || fromAccount.trim().isEmpty()) {
            return ValidationResult.failure("轉帳交易必須指定轉出帳戶");
        }
        
        if (toAccount == null || toAccount.trim().isEmpty()) {
            return ValidationResult.failure("轉帳交易必須指定轉入帳戶");
        }
        
        if (fromAccount.equals(toAccount)) {
            return ValidationResult.failure("轉出帳戶和轉入帳戶不能相同");
        }
        
        return ValidationResult.success();
    }
    
    /**
     * 驗證扣款交易資料
     */
    private ValidationResult validateWithdrawalData() {
        if (fromAccount == null || fromAccount.trim().isEmpty()) {
            return ValidationResult.failure("扣款交易必須指定扣款帳戶");
        }
        
        if (toAccount != null && !toAccount.trim().isEmpty()) {
            return ValidationResult.failure("扣款交易不應該指定轉入帳戶");
        }
        
        return ValidationResult.success();
    }
    
    /**
     * 驗證存款交易資料
     */
    private ValidationResult validateDepositData() {
        if (toAccount == null || toAccount.trim().isEmpty()) {
            return ValidationResult.failure("存款交易必須指定存款帳戶");
        }
        
        if (fromAccount != null && !fromAccount.trim().isEmpty()) {
            return ValidationResult.failure("存款交易不應該指定轉出帳戶");
        }
        
        return ValidationResult.success();
    }
    
    /**
     * 驗證狀態轉換的業務規則
     */
    public ValidationResult validateStatusTransition(TransactionStatus newStatus) {
        if (newStatus == null) {
            return ValidationResult.failure("新狀態不能為空");
        }
        
        if (this.status == newStatus) {
            return ValidationResult.failure("交易已經是" + newStatus.getDescription() + "狀態");
        }
        
        if (!canTransitionTo(newStatus)) {
            return ValidationResult.failure(
                "無效的狀態轉換：從 " + this.status.getDescription() + 
                " 到 " + newStatus.getDescription()
            );
        }
        
        return ValidationResult.success();
    }
    
    @Override
    public String toString() {
        return "Transaction{" +
                "transactionId='" + transactionId + '\'' +
                ", fromAccount='" + fromAccount + '\'' +
                ", toAccount='" + toAccount + '\'' +
                ", amount=" + amount +
                ", type=" + type +
                ", status=" + status +
                ", lockProvider='" + lockProvider + '\'' +
                '}';
    }
    
    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        Transaction that = (Transaction) o;
        return transactionId != null ? transactionId.equals(that.transactionId) : that.transactionId == null;
    }
    
    @Override
    public int hashCode() {
        return transactionId != null ? transactionId.hashCode() : 0;
    }
}