#!/bin/bash

# 快速併發測試腳本 - 簡化版本
# 用於快速驗證分散式鎖是否正常工作
#
# 測試說明：
# 1. 健康檢查 - 確認所有服務實例 (8081, 8082, 8083) 都處於健康狀態
# 2. 初始狀態檢查 - 獲取測試帳戶 ACC001 和 ACC002 的初始餘額
# 3. 併發轉帳測試 - 同時從 3 個服務實例發送 15 個轉帳請求 (每個實例 5 個)
#    - 每筆轉帳都是從 ACC001 轉 10 元到 ACC002
#    - 測試分散式鎖是否能正確防止併發問題
# 4. 結果分析 - 統計成功/失敗的轉帳次數
# 5. 資料一致性驗證 - 檢查轉出金額是否等於轉入金額
# 6. 跨服務一致性檢查 - 確認所有服務實例的帳戶餘額都一致
#
# 預期結果：
# - 在正確的分散式鎖實作下，應該只有部分轉帳成功（不是全部 15 筆）
# - 所有服務實例應該顯示相同的帳戶餘額
# - 轉出金額應該等於轉入金額，確保資料一致性

set -e

# 顏色定義
GREEN='\033[0;32m'
RED='\033[0;31m'
YELLOW='\033[1;33m'
NC='\033[0m'

# 配置
APP1_URL="http://localhost:8081/api"
APP2_URL="http://localhost:8082/api"
APP3_URL="http://localhost:8083/api"

echo -e "${GREEN}🚀 Quick Distributed Lock Test${NC}"
echo "=================================="
echo ""
echo "測試說明："
echo "1. 健康檢查 - 確認所有服務實例 (8081, 8082, 8083) 都處於健康狀態"
echo "2. 初始狀態檢查 - 獲取測試帳戶 ACC001 和 ACC002 的初始餘額"
echo "3. 併發轉帳測試 - 同時從 3 個服務實例發送 15 個轉帳請求 (每個實例 5 個)"
echo "   - 每筆轉帳都是從 ACC001 轉 10 元到 ACC002"
echo "   - 測試分散式鎖是否能正確防止併發問題"
echo "4. 結果分析 - 統計成功/失敗的轉帳次數"
echo "5. 資料一致性驗證 - 檢查轉出金額是否等於轉入金額"
echo "6. 跨服務一致性檢查 - 確認所有服務實例的帳戶餘額都一致"
echo ""
echo "預期結果："
echo "- 在正確的分散式鎖實作下，應該只有部分轉帳成功（不是全部 15 筆）"
echo "- 所有服務實例應該顯示相同的帳戶餘額"
echo "- 轉出金額應該等於轉入金額，確保資料一致性"
echo ""
echo "=================================="

# 檢查服務健康狀態
echo "Checking services..."
for url in "$APP1_URL" "$APP2_URL" "$APP3_URL"; do
    if curl -s -f "$url/actuator/health" > /dev/null; then
        echo -e "✅ $(echo $url | cut -d'/' -f3) is healthy"
    else
        echo -e "❌ $(echo $url | cut -d'/' -f3) is not healthy"
        exit 1
    fi
done

# 獲取初始餘額
echo -e "\n📊 Getting initial balances..."
INITIAL_ACC001=$(curl -s "$APP1_URL/accounts/ACC001/balance" | jq -r '.data.balance')
INITIAL_ACC002=$(curl -s "$APP1_URL/accounts/ACC002/balance" | jq -r '.data.balance')

echo "ACC001: $INITIAL_ACC001"
echo "ACC002: $INITIAL_ACC002"

# 執行併發轉帳（每個服務 5 個併發請求）
echo -e "\n🔄 Executing concurrent transfers..."

# 創建臨時文件來儲存響應
TEMP_DIR=$(mktemp -d)
SUCCESS_COUNT=0
FAILURE_COUNT=0

# 併發執行轉帳請求並將結果儲存到臨時文件
for i in {1..5}; do
    curl -s -X POST "$APP1_URL/transfer" \
        -H "Content-Type: application/json" \
        -d '{"fromAccount":"ACC001","toAccount":"ACC002","amount":10}' \
        > "$TEMP_DIR/app1_$i.json" &
    
    curl -s -X POST "$APP2_URL/transfer" \
        -H "Content-Type: application/json" \
        -d '{"fromAccount":"ACC001","toAccount":"ACC002","amount":10}' \
        > "$TEMP_DIR/app2_$i.json" &
    
    curl -s -X POST "$APP3_URL/transfer" \
        -H "Content-Type: application/json" \
        -d '{"fromAccount":"ACC001","toAccount":"ACC002","amount":10}' \
        > "$TEMP_DIR/app3_$i.json" &
done

# 等待所有請求完成
wait

echo "Processing results..."

# 處理和顯示結果
for file in "$TEMP_DIR"/*.json; do
    if [ -f "$file" ]; then
        filename=$(basename "$file")
        success=$(jq -r '.success // false' "$file" 2>/dev/null)
        
        if [ "$success" = "true" ]; then
            SUCCESS_COUNT=$((SUCCESS_COUNT + 1))
            transaction_id=$(jq -r '.data.transactionId // "N/A"' "$file" 2>/dev/null)
            lock_provider=$(jq -r '.data.lockProvider // "N/A"' "$file" 2>/dev/null)
            echo -e "  ✅ $filename: ${GREEN}SUCCESS${NC} (TxID: ${transaction_id:0:8}..., Provider: $lock_provider)"
        else
            FAILURE_COUNT=$((FAILURE_COUNT + 1))
            error_msg=$(jq -r '.message // "Unknown error"' "$file" 2>/dev/null)
            # 縮短錯誤訊息
            short_error=$(echo "$error_msg" | cut -c1-80)
            if [ ${#error_msg} -gt 80 ]; then
                short_error="${short_error}..."
            fi
            echo -e "  ❌ $filename: ${RED}FAILED${NC} - $short_error"
        fi
    fi
done

echo -e "\n📊 Transfer Results Summary:"
echo -e "  ${GREEN}✅ Successful: $SUCCESS_COUNT${NC}"
echo -e "  ${RED}❌ Failed: $FAILURE_COUNT${NC}"
echo -e "  📝 Total Requests: $((SUCCESS_COUNT + FAILURE_COUNT))"

# 清理臨時文件
rm -rf "$TEMP_DIR"

echo "All requests completed, waiting for system to stabilize..."
sleep 3

# 獲取最終餘額
echo -e "\n📊 Getting final balances..."
FINAL_ACC001=$(curl -s "$APP1_URL/accounts/ACC001/balance" | jq -r '.data.balance')
FINAL_ACC002=$(curl -s "$APP1_URL/accounts/ACC002/balance" | jq -r '.data.balance')

echo "ACC001: $FINAL_ACC001"
echo "ACC002: $FINAL_ACC002"

# 計算轉帳次數
TRANSFERRED=$(echo "$INITIAL_ACC001 - $FINAL_ACC001" | bc)
RECEIVED=$(echo "$FINAL_ACC002 - $INITIAL_ACC002" | bc)

echo -e "\n📈 Analysis:"
echo "Amount transferred from ACC001: $TRANSFERRED"
echo "Amount received by ACC002: $RECEIVED"

# 驗證資料一致性
if [ "$TRANSFERRED" = "$RECEIVED" ]; then
    echo -e "${GREEN}✅ Data consistency maintained!${NC}"
    echo "Successful transfers: $(echo "$TRANSFERRED / 10" | bc)"
else
    echo -e "${RED}❌ Data inconsistency detected!${NC}"
    echo "This indicates a problem with the distributed lock mechanism"
fi

# 檢查跨服務一致性
echo -e "\n🔍 Checking cross-service consistency..."
ACC001_APP2=$(curl -s "$APP2_URL/accounts/ACC001/balance" | jq -r '.data.balance')
ACC001_APP3=$(curl -s "$APP3_URL/accounts/ACC001/balance" | jq -r '.data.balance')

if [ "$FINAL_ACC001" = "$ACC001_APP2" ] && [ "$ACC001_APP2" = "$ACC001_APP3" ]; then
    echo -e "${GREEN}✅ All services show consistent balances${NC}"
else
    echo -e "${RED}❌ Services show inconsistent balances${NC}"
    echo "App1: $FINAL_ACC001, App2: $ACC001_APP2, App3: $ACC001_APP3"
fi

echo -e "\n🎉 Quick test completed!"