package com.example.distributedlock.repositories;

import com.example.distributedlock.models.Transaction;
import com.example.distributedlock.models.TransactionStatus;
import com.example.distributedlock.models.TransactionType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 交易資料存取介面
 */
@Repository
public interface TransactionRepository extends JpaRepository<Transaction, String> {
    
    /**
     * 根據帳戶號碼查詢交易記錄
     */
    @Query("SELECT t FROM Transaction t WHERE t.fromAccount = :accountNumber OR t.toAccount = :accountNumber ORDER BY t.createdAt DESC")
    List<Transaction> findByAccountNumber(@Param("accountNumber") String accountNumber);
    
    /**
     * 根據交易狀態查詢交易記錄
     */
    List<Transaction> findByStatus(TransactionStatus status);
    
    /**
     * 根據交易類型查詢交易記錄
     */
    List<Transaction> findByType(TransactionType type);
    
    /**
     * 查詢指定時間範圍內的交易記錄
     */
    @Query("SELECT t FROM Transaction t WHERE t.createdAt BETWEEN :startTime AND :endTime ORDER BY t.createdAt DESC")
    List<Transaction> findByCreatedAtBetween(@Param("startTime") LocalDateTime startTime, 
                                           @Param("endTime") LocalDateTime endTime);
    
    /**
     * 根據鎖提供者查詢交易記錄
     */
    List<Transaction> findByLockProvider(String lockProvider);
    
    /**
     * 統計指定帳戶的交易數量
     */
    @Query("SELECT COUNT(t) FROM Transaction t WHERE (t.fromAccount = :accountNumber OR t.toAccount = :accountNumber) AND t.status = :status")
    long countTransactionsByAccountAndStatus(@Param("accountNumber") String accountNumber, 
                                           @Param("status") TransactionStatus status);
}