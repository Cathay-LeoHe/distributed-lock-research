-- 本地開發環境 - 示範資料初始化腳本
-- 此腳本包含更多測試資料，適合本地開發和測試

-- 清理現有資料（如果存在）
DELETE FROM transactions;
DELETE FROM accounts;

-- 插入本地開發環境的示範帳戶資料
INSERT INTO accounts (account_number, balance, status, created_at, updated_at, version) VALUES
-- 活躍帳戶
('ACC001', 10000.00, 'ACTIVE', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 0),
('ACC002', 20000.00, 'ACTIVE', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 0),
('ACC003', 15000.00, 'ACTIVE', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 0),
('ACC004', 5000.00, 'ACTIVE', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 0),
('ACC005', 8000.00, 'ACTIVE', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 0),
('ACC006', 25000.00, 'ACTIVE', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 0),
('ACC007', 12000.00, 'ACTIVE', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 0),
('ACC009', 18000.00, 'ACTIVE', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 0),

-- 測試用特殊狀態帳戶
('ACC008', 3000.00, 'INACTIVE', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 0),
('ACC010', 7500.00, 'FROZEN', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 0),
('ACC011', 0.00, 'ACTIVE', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 0), -- 零餘額帳戶
('ACC012', 1.00, 'ACTIVE', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 0), -- 最小餘額帳戶
('ACC013', 999999.99, 'ACTIVE', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 0), -- 高餘額帳戶

-- 測試用關閉帳戶
('ACC014', 0.00, 'CLOSED', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 0);

-- 插入本地開發環境的示範交易記錄
INSERT INTO transactions (transaction_id, from_account, to_account, amount, type, status, created_at, lock_provider, description) VALUES
-- 成功的轉帳交易
('TXN001', 'ACC001', 'ACC002', 500.00, 'TRANSFER', 'COMPLETED', CURRENT_TIMESTAMP - INTERVAL '1' DAY, 'redis', '示範轉帳交易 - Redis鎖'),
('TXN002', 'ACC002', 'ACC004', 1000.00, 'TRANSFER', 'COMPLETED', CURRENT_TIMESTAMP - INTERVAL '3' DAY, 'redis', '示範轉帳交易 - Redis鎖'),
('TXN003', 'ACC006', 'ACC007', 2000.00, 'TRANSFER', 'COMPLETED', CURRENT_TIMESTAMP - INTERVAL '5' DAY, 'zookeeper', '示範轉帳交易 - ZooKeeper鎖'),
('TXN007', 'ACC009', 'ACC003', 800.00, 'TRANSFER', 'COMPLETED', CURRENT_TIMESTAMP - INTERVAL '7' DAY, 'zookeeper', '示範轉帳交易 - ZooKeeper鎖'),

-- 成功的扣款交易
('TXN004', 'ACC003', NULL, 200.00, 'WITHDRAWAL', 'COMPLETED', CURRENT_TIMESTAMP - INTERVAL '2' DAY, 'zookeeper', '示範扣款交易 - ZooKeeper鎖'),
('TXN005', 'ACC005', NULL, 300.00, 'WITHDRAWAL', 'COMPLETED', CURRENT_TIMESTAMP - INTERVAL '4' DAY, 'redis', '示範扣款交易 - Redis鎖'),
('TXN008', 'ACC004', NULL, 150.00, 'WITHDRAWAL', 'COMPLETED', CURRENT_TIMESTAMP - INTERVAL '8' DAY, 'redis', '示範扣款交易 - Redis鎖'),

-- 失敗的交易（用於測試錯誤處理）
('TXN006', 'ACC001', NULL, 100.00, 'WITHDRAWAL', 'FAILED', CURRENT_TIMESTAMP - INTERVAL '6' DAY, 'redis', '示範失敗交易; 失敗原因: 測試失敗場景'),
('TXN009', 'ACC011', 'ACC001', 100.00, 'TRANSFER', 'FAILED', CURRENT_TIMESTAMP - INTERVAL '9' DAY, 'redis', '示範失敗交易; 失敗原因: 餘額不足'),
('TXN010', 'ACC008', 'ACC001', 500.00, 'TRANSFER', 'FAILED', CURRENT_TIMESTAMP - INTERVAL '10' DAY, 'zookeeper', '示範失敗交易; 失敗原因: 帳戶非活躍狀態'),

-- 不同狀態的交易（用於測試狀態管理）
('TXN011', 'ACC001', 'ACC002', 250.00, 'TRANSFER', 'PENDING', CURRENT_TIMESTAMP - INTERVAL '1' HOUR, 'redis', '待處理的轉帳交易'),
('TXN012', 'ACC003', NULL, 75.00, 'WITHDRAWAL', 'PROCESSING', CURRENT_TIMESTAMP - INTERVAL '30' MINUTE, 'zookeeper', '處理中的扣款交易'),
('TXN013', 'ACC005', 'ACC006', 1200.00, 'TRANSFER', 'CANCELLED', CURRENT_TIMESTAMP - INTERVAL '2' HOUR, 'redis', '已取消的轉帳交易; 取消原因: 用戶主動取消');

-- 顯示初始化完成訊息
SELECT 'Local development demo data initialization completed successfully' AS message;