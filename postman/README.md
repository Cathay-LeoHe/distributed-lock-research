# 分散式鎖高併發測試指南

本目錄包含了用於測試分散式鎖系統在高併發場景下表現的 Postman 集合和相關腳本。

## 📁 文件結構

```
postman/
├── Distributed-Lock-Concurrent-Tests.postman_collection.json  # 主要測試集合
├── Distributed-Lock-Environment.postman_environment.json      # 環境配置
└── README.md                                                  # 本說明文件

scripts/
├── concurrent-test.js          # Node.js 併發測試腳本
└── run-concurrent-tests.sh     # Shell 併發測試腳本
```

## 🚀 快速開始

### 方法一：使用 Postman GUI

1. **導入集合和環境**：
   ```bash
   # 在 Postman 中導入以下文件：
   # - Distributed-Lock-Concurrent-Tests.postman_collection.json
   # - Distributed-Lock-Environment.postman_environment.json
   ```

2. **配置環境變數**：
   - 確保所有服務 URL 正確指向你的應用程式實例
   - 調整測試參數（轉帳金額、帳戶等）

3. **執行測試**：
   - 選擇 "Distributed Lock Environment" 環境
   - 運行整個集合或特定的測試組

### 方法二：使用 Newman 命令行

1. **安裝依賴**：
   ```bash
   npm install newman
   # 或
   npm run install-deps
   ```

2. **執行基本測試**：
   ```bash
   npm run test:postman
   ```

3. **執行負載測試**：
   ```bash
   npm run test:load
   ```

### 方法三：使用 Node.js 腳本

1. **安裝依賴**：
   ```bash
   npm install
   ```

2. **執行併發測試**：
   ```bash
   npm run test:concurrent
   ```

### 方法四：使用 Shell 腳本

1. **安裝系統依賴**：
   ```bash
   # Ubuntu/Debian
   sudo apt-get install jq bc curl
   
   # macOS
   brew install jq bc
   ```

2. **執行測試**：
   ```bash
   chmod +x scripts/run-concurrent-tests.sh
   npm run test:shell
   ```

## 🧪 測試場景

### 1. 設置測試 (Setup Tests)
- 檢查所有應用程式實例的健康狀態
- 獲取測試帳戶的初始餘額
- 驗證系統準備就緒

### 2. 併發轉帳測試 (Concurrent Transfer Tests)
- **App1 to App1**: 直接向 App1 發送併發請求
- **App2 to App2**: 直接向 App2 發送併發請求  
- **App3 to App3**: 直接向 App3 發送併發請求
- **Load Balancer**: 通過負載均衡器發送混合請求

### 3. 驗證測試 (Verification Tests)
- 檢查最終帳戶餘額
- 驗證資料一致性
- 確認總金額守恆

### 4. 鎖狀態監控 (Lock Status Monitoring)
- 查看各服務的分散式鎖狀態
- 監控鎖獲取統計
- 檢查鎖提供者可用性

### 5. 性能指標 (Performance Metrics)
- 收集業務指標
- 分析 Prometheus 指標
- 評估系統性能

## ⚙️ 配置參數

### 環境變數

| 變數名稱 | 預設值 | 說明 |
|---------|--------|------|
| `app1_url` | `http://localhost:8081/api` | App1 服務 URL |
| `app2_url` | `http://localhost:8082/api` | App2 服務 URL |
| `app3_url` | `http://localhost:8083/api` | App3 服務 URL |
| `lb_url` | `http://localhost:8080/api` | 負載均衡器 URL |
| `transfer_amount` | `100.00` | 單次轉帳金額 |
| `test_account_from` | `ACC001` | 轉出帳戶 |
| `test_account_to` | `ACC002` | 轉入帳戶 |

### 測試參數

```javascript
// 在 concurrent-test.js 中可調整的參數
const TEST_CONFIG = {
    concurrent: {
        iterations: 20,        // 每個服務的請求次數
        parallelRuns: 3,       // 並行運行的服務數量
        delayRequest: 100      // 請求間延遲（毫秒）
    },
    testParams: {
        transferAmount: 50.00, // 轉帳金額
        fromAccount: 'ACC001', // 轉出帳戶
        toAccount: 'ACC002'    // 轉入帳戶
    }
};
```

## 📊 結果分析

### 成功指標
- ✅ **資料一致性**: 所有服務顯示相同的帳戶餘額
- ✅ **總金額守恆**: 轉出金額 = 轉入金額
- ✅ **無重複轉帳**: 併發請求不會導致重複處理
- ✅ **響應時間合理**: 平均響應時間 < 5 秒

### 失敗指標
- ❌ **資料不一致**: 不同服務顯示不同餘額
- ❌ **金額不守恆**: 總金額發生變化
- ❌ **重複轉帳**: 同一請求被處理多次
- ❌ **響應超時**: 請求響應時間過長

### 測試報告

測試完成後，結果將保存在 `test-results/` 目錄中：

```
test-results/
├── concurrent-test-summary-[timestamp].json    # 詳細測試結果
├── newman-App1-[timestamp].json                # Newman App1 結果
├── newman-App2-[timestamp].json                # Newman App2 結果
├── newman-App3-[timestamp].json                # Newman App3 結果
├── transfer-results.csv                        # 轉帳請求詳細記錄
├── test-report.txt                             # 測試摘要報告
└── concurrent-test-[timestamp].log             # 詳細日誌
```

## 🔧 故障排除

### 常見問題

1. **連接失敗**:
   ```bash
   # 檢查服務是否運行
   docker-compose ps
   
   # 檢查服務健康狀態
   curl http://localhost:8081/api/actuator/health
   ```

2. **Newman 未找到**:
   ```bash
   # 全局安裝 Newman
   npm install -g newman
   
   # 或使用本地安裝
   npx newman --version
   ```

3. **權限錯誤**:
   ```bash
   # 給腳本執行權限
   chmod +x scripts/run-concurrent-tests.sh
   ```

4. **依賴缺失**:
   ```bash
   # 安裝系統依賴
   sudo apt-get install jq bc curl  # Ubuntu/Debian
   brew install jq bc               # macOS
   ```

### 調試技巧

1. **啟用詳細日誌**:
   ```bash
   # 在 Newman 中啟用詳細輸出
   newman run collection.json --verbose
   ```

2. **單獨測試服務**:
   ```bash
   # 測試單個服務
   curl -X POST http://localhost:8081/api/transfer \
     -H "Content-Type: application/json" \
     -d '{"fromAccount":"ACC001","toAccount":"ACC002","amount":100}'
   ```

3. **檢查鎖狀態**:
   ```bash
   # 查看分散式鎖狀態
   curl http://localhost:8081/api/lock-management/status
   ```

## 📈 性能調優建議

### 測試參數調整

1. **低併發測試** (驗證功能):
   ```javascript
   iterations: 5,
   parallelRuns: 2,
   delayRequest: 500
   ```

2. **中等併發測試** (性能測試):
   ```javascript
   iterations: 20,
   parallelRuns: 3,
   delayRequest: 100
   ```

3. **高併發測試** (壓力測試):
   ```javascript
   iterations: 50,
   parallelRuns: 4,
   delayRequest: 50
   ```

### 監控建議

1. **系統資源監控**:
   - CPU 使用率
   - 記憶體使用量
   - 網路 I/O
   - 磁碟 I/O

2. **應用程式指標**:
   - 響應時間
   - 錯誤率
   - 吞吐量
   - 鎖獲取成功率

3. **分散式鎖指標**:
   - 鎖獲取延遲
   - 鎖持有時間
   - 鎖競爭情況
   - 鎖釋放成功率

## 🤝 貢獻指南

歡迎提交改進建議和 bug 報告！請確保：

1. 測試腳本在你的環境中正常運行
2. 添加適當的錯誤處理和日誌記錄
3. 更新相關文件
4. 遵循現有的代碼風格

## 📞 支援

如果遇到問題，請：

1. 檢查本文件的故障排除部分
2. 查看 `test-results/` 目錄中的日誌文件
3. 在專案 GitHub 頁面提交 issue
4. 聯繫開發團隊獲取支援