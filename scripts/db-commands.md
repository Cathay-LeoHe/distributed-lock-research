# 資料庫操作命令參考

## 基本連接語法

```bash
# 基本格式
docker-compose exec postgres psql -U postgres -d distributed_lock -c "SQL_COMMAND"

# 互動模式
docker-compose exec postgres psql -U postgres -d distributed_lock
```

## 帳戶管理

### 查看帳戶餘額

```sql
-- 查看所有帳戶
SELECT account_number, balance, status, updated_at FROM accounts ORDER BY account_number;

-- 查看特定帳戶
SELECT * FROM accounts WHERE account_number = 'ACC001';

-- 查看多個特定帳戶
SELECT * FROM accounts WHERE account_number IN ('ACC001', 'ACC002');
```

### 更新帳戶餘額

```sql
-- 重置單個帳戶
UPDATE accounts SET balance = 10000.00, updated_at = CURRENT_TIMESTAMP WHERE account_number = 'ACC001';

-- 重置多個帳戶
UPDATE accounts SET balance = CASE 
    WHEN account_number = 'ACC001' THEN 10000.00
    WHEN account_number = 'ACC002' THEN 20000.00
    ELSE balance
END, updated_at = CURRENT_TIMESTAMP 
WHERE account_number IN ('ACC001', 'ACC002');

-- 批量調整餘額
UPDATE accounts SET balance = balance + 1000.00 WHERE account_number LIKE 'ACC%';
```

### 帳戶統計

```sql
-- 帳戶總數
SELECT COUNT(*) as total_accounts FROM accounts;

-- 各狀態帳戶統計
SELECT status, COUNT(*) as count FROM accounts GROUP BY status;

-- 餘額統計
SELECT 
    MIN(balance) as min_balance,
    MAX(balance) as max_balance,
    AVG(balance) as avg_balance,
    SUM(balance) as total_balance
FROM accounts;
```

## 交易管理

### 查看交易記錄

```sql
-- 最近交易
SELECT transaction_id, from_account, to_account, amount, status, lock_provider, created_at 
FROM transactions 
ORDER BY created_at DESC 
LIMIT 20;

-- 特定帳戶的交易
SELECT * FROM transactions 
WHERE from_account = 'ACC001' OR to_account = 'ACC001'
ORDER BY created_at DESC;

-- 特定時間範圍的交易
SELECT * FROM transactions 
WHERE created_at > NOW() - INTERVAL '1 hour'
ORDER BY created_at DESC;

-- 失敗的交易
SELECT * FROM transactions 
WHERE status = 'FAILED'
ORDER BY created_at DESC;
```

### 交易統計

```sql
-- 交易狀態統計
SELECT status, COUNT(*) as count FROM transactions GROUP BY status;

-- 鎖提供者統計
SELECT lock_provider, COUNT(*) as count 
FROM transactions 
WHERE lock_provider IS NOT NULL 
GROUP BY lock_provider;

-- 每小時交易量
SELECT 
    DATE_TRUNC('hour', created_at) as hour,
    COUNT(*) as transaction_count,
    COUNT(CASE WHEN status = 'COMPLETED' THEN 1 END) as successful,
    COUNT(CASE WHEN status = 'FAILED' THEN 1 END) as failed
FROM transactions 
WHERE created_at > NOW() - INTERVAL '24 hours'
GROUP BY hour 
ORDER BY hour DESC;

-- 交易金額統計
SELECT 
    COUNT(*) as total_transactions,
    SUM(amount) as total_amount,
    AVG(amount) as avg_amount,
    MIN(amount) as min_amount,
    MAX(amount) as max_amount
FROM transactions 
WHERE status = 'COMPLETED';
```

### 清理交易資料

```sql
-- 刪除所有交易記錄 (危險操作!)
DELETE FROM transactions;

-- 刪除舊交易記錄
DELETE FROM transactions WHERE created_at < NOW() - INTERVAL '7 days';

-- 刪除失敗的交易記錄
DELETE FROM transactions WHERE status = 'FAILED';
```

## 資料重置

### 完全重置為初始狀態

```sql
-- 1. 清除所有交易
DELETE FROM transactions;

-- 2. 重置帳戶餘額
UPDATE accounts SET 
    balance = CASE 
        WHEN account_number = 'ACC001' THEN 10000.00
        WHEN account_number = 'ACC002' THEN 20000.00
        WHEN account_number = 'ACC003' THEN 15000.00
        WHEN account_number = 'ACC004' THEN 5000.00
        WHEN account_number = 'ACC005' THEN 8000.00
        ELSE balance
    END,
    updated_at = CURRENT_TIMESTAMP,
    version = 0
WHERE account_number IN ('ACC001', 'ACC002', 'ACC003', 'ACC004', 'ACC005');
```

## 系統診斷

### 檢查資料一致性

```sql
-- 檢查是否有負餘額
SELECT * FROM accounts WHERE balance < 0;

-- 檢查交易金額與帳戶餘額的一致性（簡單版本）
WITH account_changes AS (
    SELECT 
        from_account as account,
        -SUM(amount) as change
    FROM transactions 
    WHERE status = 'COMPLETED' AND from_account IS NOT NULL
    GROUP BY from_account
    
    UNION ALL
    
    SELECT 
        to_account as account,
        SUM(amount) as change
    FROM transactions 
    WHERE status = 'COMPLETED' AND to_account IS NOT NULL
    GROUP BY to_account
)
SELECT 
    a.account_number,
    a.balance as current_balance,
    10000.00 + COALESCE(SUM(ac.change), 0) as expected_balance
FROM accounts a
LEFT JOIN account_changes ac ON a.account_number = ac.account
WHERE a.account_number IN ('ACC001', 'ACC002')
GROUP BY a.account_number, a.balance;
```

### 效能監控

```sql
-- 每分鐘交易統計
SELECT 
    DATE_TRUNC('minute', created_at) as minute,
    COUNT(*) as transactions,
    COUNT(CASE WHEN status = 'COMPLETED' THEN 1 END) as successful,
    AVG(EXTRACT(EPOCH FROM (updated_at - created_at))) as avg_duration_seconds
FROM transactions 
WHERE created_at > NOW() - INTERVAL '1 hour'
GROUP BY minute 
ORDER BY minute DESC;
```

## 快速命令

```bash
# 使用腳本
./scripts/db-reset.sh reset-accounts     # 重置 ACC001, ACC002 餘額
./scripts/db-reset.sh show-balances      # 顯示所有餘額
./scripts/db-reset.sh show-transactions  # 顯示最近交易
./scripts/db-reset.sh show-all          # 顯示完整狀態

# 直接 SQL
docker-compose exec postgres psql -U postgres -d distributed_lock -c "SELECT account_number, balance FROM accounts WHERE account_number IN ('ACC001', 'ACC002');"
```