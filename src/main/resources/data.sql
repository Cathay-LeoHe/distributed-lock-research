-- 分散式鎖研究專案 - 通用示範資料初始化腳本
-- 此腳本會在應用程式啟動時自動執行，創建基本的示範帳戶和初始餘額
-- 注意：具體的環境資料請參考 data-{profile}.sql 檔案

-- 清理現有資料（如果存在）
-- 使用條件刪除以避免表格不存在的錯誤

-- 插入基本示範帳戶資料
INSERT INTO accounts (account_number, balance, status, created_at, updated_at, version) VALUES
('ACC001', 10000.00, 'ACTIVE', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 0),
('ACC002', 20000.00, 'ACTIVE', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 0),
('ACC003', 15000.00, 'ACTIVE', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 0),
('ACC004', 5000.00, 'ACTIVE', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 0),
('ACC005', 8000.00, 'ACTIVE', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 0)
ON CONFLICT (account_number) DO NOTHING;

-- 插入基本示範交易記錄
INSERT INTO transactions (transaction_id, from_account, to_account, amount, type, status, created_at, lock_provider, description) VALUES
('TXN001', 'ACC001', 'ACC002', 500.00, 'TRANSFER', 'COMPLETED', CURRENT_TIMESTAMP - INTERVAL '1' DAY, 'redis', '基本示範轉帳交易'),
('TXN002', 'ACC003', NULL, 200.00, 'WITHDRAWAL', 'COMPLETED', CURRENT_TIMESTAMP - INTERVAL '2' DAY, 'zookeeper', '基本示範扣款交易'),
('TXN003', 'ACC002', 'ACC004', 1000.00, 'TRANSFER', 'COMPLETED', CURRENT_TIMESTAMP - INTERVAL '3' DAY, 'redis', '基本示範轉帳交易')
ON CONFLICT (transaction_id) DO NOTHING;

-- 顯示初始化完成訊息
-- SELECT 'Basic demo data initialization completed successfully' AS message;