package com.example.distributedlock.repositories;

import com.example.distributedlock.models.Account;
import com.example.distributedlock.models.AccountStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import jakarta.persistence.LockModeType;
import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

/**
 * 帳戶資料存取介面
 */
@Repository
public interface AccountRepository extends JpaRepository<Account, String> {
    
    /**
     * 根據帳戶狀態查詢帳戶
     */
    List<Account> findByStatus(AccountStatus status);
    
    /**
     * 查詢餘額大於指定金額的帳戶
     */
    @Query("SELECT a FROM Account a WHERE a.balance >= :minBalance")
    List<Account> findAccountsWithMinBalance(@Param("minBalance") BigDecimal minBalance);
    
    /**
     * 使用悲觀鎖查詢帳戶（用於併發控制）
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT a FROM Account a WHERE a.accountNumber = :accountNumber")
    Optional<Account> findByAccountNumberWithLock(@Param("accountNumber") String accountNumber);
    
    /**
     * 檢查帳戶是否存在且為活躍狀態
     */
    @Query("SELECT COUNT(a) > 0 FROM Account a WHERE a.accountNumber = :accountNumber AND a.status = 'ACTIVE'")
    boolean existsActiveAccount(@Param("accountNumber") String accountNumber);
}