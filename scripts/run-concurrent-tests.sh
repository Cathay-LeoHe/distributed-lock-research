#!/bin/bash

# 高併發分散式鎖測試腳本
# 使用 curl 模擬多服務併發請求

set -e

# 顏色定義
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
BLUE='\033[0;34m'
NC='\033[0m' # No Color

# 測試配置
APP1_URL="http://localhost:8081/api"
APP2_URL="http://localhost:8082/api"
APP3_URL="http://localhost:8083/api"
LB_URL="http://localhost:8080/api"

TRANSFER_AMOUNT="50.00"
FROM_ACCOUNT="ACC001"
TO_ACCOUNT="ACC002"
CONCURRENT_REQUESTS=10

# 創建結果目錄
RESULTS_DIR="./test-results"
mkdir -p "$RESULTS_DIR"

# 日誌文件
LOG_FILE="$RESULTS_DIR/concurrent-test-$(date +%Y%m%d-%H%M%S).log"

# 日誌函數
log_info() {
    echo -e "${BLUE}[INFO]${NC} $1" | tee -a "$LOG_FILE"
}

log_success() {
    echo -e "${GREEN}[SUCCESS]${NC} $1" | tee -a "$LOG_FILE"
}

log_warning() {
    echo -e "${YELLOW}[WARNING]${NC} $1" | tee -a "$LOG_FILE"
}

log_error() {
    echo -e "${RED}[ERROR]${NC} $1" | tee -a "$LOG_FILE"
}

# 檢查服務健康狀態
check_service_health() {
    local service_name=$1
    local service_url=$2
    
    log_info "Checking $service_name health..."
    
    if curl -s -f "$service_url/actuator/health" > /dev/null; then
        log_success "$service_name is healthy"
        return 0
    else
        log_error "$service_name is not healthy"
        return 1
    fi
}

# 獲取帳戶餘額
get_balance() {
    local service_url=$1
    local account=$2
    
    local response=$(curl -s "$service_url/accounts/$account/balance")
    local balance=$(echo "$response" | jq -r '.data.balance // "ERROR"')
    
    if [ "$balance" != "ERROR" ]; then
        echo "$balance"
    else
        echo "0"
    fi
}

# 執行匯款請求
execute_transfer() {
    local service_url=$1
    local service_name=$2
    local request_id=$3
    
    # 使用秒級時間戳，避免毫秒計算問題
    local start_time=$(date +%s)
    
    local response=$(curl -s -w "%{http_code}" -X POST "$service_url/transfer" \
        -H "Content-Type: application/json" \
        -d "{
            \"fromAccount\": \"$FROM_ACCOUNT\",
            \"toAccount\": \"$TO_ACCOUNT\",
            \"amount\": $TRANSFER_AMOUNT
        }")
    
    local end_time=$(date +%s)
    
    # 計算持續時間（秒）
    local duration=$((end_time - start_time))
    
    local http_code="${response: -3}"
    local body="${response%???}"
    
    local success=$(echo "$body" | jq -r '.success // false')
    local message=$(echo "$body" | jq -r '.message // "Unknown"')
    
    echo "$service_name,$request_id,$http_code,$success,$message,$duration" >> "$RESULTS_DIR/transfer-results.csv"
    
    if [ "$http_code" = "200" ] && [ "$success" = "true" ]; then
        log_success "$service_name Request #$request_id: SUCCESS (${duration}s)"
        return 0
    else
        log_warning "$service_name Request #$request_id: FAILED - $message (${duration}s)"
        return 1
    fi
}

# 併發執行匯款測試
run_concurrent_transfers() {
    log_info "Starting concurrent transfer tests..."
    log_info "Configuration:"
    log_info "  - Transfer amount: $TRANSFER_AMOUNT"
    log_info "  - From account: $FROM_ACCOUNT"
    log_info "  - To account: $TO_ACCOUNT"
    log_info "  - Concurrent requests per service: $CONCURRENT_REQUESTS"
    
    # 創建 CSV 標題
    echo "Service,RequestID,HttpCode,Success,Message,Duration" > "$RESULTS_DIR/transfer-results.csv"
    
    # 記錄初始餘額
    local initial_from_balance=$(get_balance "$APP1_URL" "$FROM_ACCOUNT")
    local initial_to_balance=$(get_balance "$APP1_URL" "$TO_ACCOUNT")
    
    log_info "Initial balances:"
    log_info "  - $FROM_ACCOUNT: $initial_from_balance"
    log_info "  - $TO_ACCOUNT: $initial_to_balance"
    
    # 併發執行測試
    local pids=()
    
    # App1 併發請求
    for i in $(seq 1 $CONCURRENT_REQUESTS); do
        execute_transfer "$APP1_URL" "App1" "$i" &
        pids+=($!)
    done
    
    # App2 併發請求
    for i in $(seq 1 $CONCURRENT_REQUESTS); do
        execute_transfer "$APP2_URL" "App2" "$i" &
        pids+=($!)
    done
    
    # App3 併發請求
    for i in $(seq 1 $CONCURRENT_REQUESTS); do
        execute_transfer "$APP3_URL" "App3" "$i" &
        pids+=($!)
    done
    
    # 負載均衡器併發請求
    for i in $(seq 1 $CONCURRENT_REQUESTS); do
        execute_transfer "$LB_URL" "LoadBalancer" "$i" &
        pids+=($!)
    done
    
    # 等待所有請求完成
    log_info "Waiting for all requests to complete..."
    for pid in "${pids[@]}"; do
        wait "$pid"
    done
    
    log_success "All concurrent requests completed"
    
    # 等待一段時間讓系統穩定
    sleep 2
    
    # 記錄最終餘額
    local final_from_balance=$(get_balance "$APP1_URL" "$FROM_ACCOUNT")
    local final_to_balance=$(get_balance "$APP1_URL" "$TO_ACCOUNT")
    
    log_info "Final balances:"
    log_info "  - $FROM_ACCOUNT: $final_from_balance"
    log_info "  - $TO_ACCOUNT: $final_to_balance"
    
    # 計算實際轉帳次數
    local actual_transfers=$(echo "scale=2; ($initial_from_balance - $final_from_balance) / $TRANSFER_AMOUNT" | bc)
    
    log_info "Analysis:"
    log_info "  - Expected max transfers: $((CONCURRENT_REQUESTS * 4))"
    log_info "  - Actual successful transfers: $actual_transfers"
    log_info "  - Total amount transferred: $(echo "scale=2; $initial_from_balance - $final_from_balance" | bc)"
}

# 驗證資料一致性
verify_data_consistency() {
    log_info "Verifying data consistency across services..."
    
    # 從不同服務獲取餘額
    local app1_from_balance=$(get_balance "$APP1_URL" "$FROM_ACCOUNT")
    local app2_from_balance=$(get_balance "$APP2_URL" "$FROM_ACCOUNT")
    local app3_from_balance=$(get_balance "$APP3_URL" "$FROM_ACCOUNT")
    
    local app1_to_balance=$(get_balance "$APP1_URL" "$TO_ACCOUNT")
    local app2_to_balance=$(get_balance "$APP2_URL" "$TO_ACCOUNT")
    local app3_to_balance=$(get_balance "$APP3_URL" "$TO_ACCOUNT")
    
    log_info "$FROM_ACCOUNT balances across services:"
    log_info "  - App1: $app1_from_balance"
    log_info "  - App2: $app2_from_balance"
    log_info "  - App3: $app3_from_balance"
    
    log_info "$TO_ACCOUNT balances across services:"
    log_info "  - App1: $app1_to_balance"
    log_info "  - App2: $app2_to_balance"
    log_info "  - App3: $app3_to_balance"
    
    # 檢查一致性
    if [ "$app1_from_balance" = "$app2_from_balance" ] && [ "$app2_from_balance" = "$app3_from_balance" ] && \
       [ "$app1_to_balance" = "$app2_to_balance" ] && [ "$app2_to_balance" = "$app3_to_balance" ]; then
        log_success "✅ Data consistency verified - all services show identical balances"
    else
        log_error "❌ Data inconsistency detected!"
        log_error "This indicates potential issues with the distributed lock mechanism"
    fi
}

# 生成測試報告
generate_report() {
    log_info "Generating test report..."
    
    local total_requests=$(tail -n +2 "$RESULTS_DIR/transfer-results.csv" | wc -l)
    local successful_requests=$(tail -n +2 "$RESULTS_DIR/transfer-results.csv" | grep ",true," | wc -l)
    local failed_requests=$((total_requests - successful_requests))
    local success_rate=$(echo "scale=2; $successful_requests * 100 / $total_requests" | bc)
    
    # 計算平均響應時間
    local avg_response_time=$(tail -n +2 "$RESULTS_DIR/transfer-results.csv" | cut -d',' -f6 | awk '{sum+=$1; count++} END {if(count>0) print sum/count; else print 0}')
    
    local report_file="$RESULTS_DIR/test-report.txt"
    
    cat > "$report_file" << EOF
=== Distributed Lock Concurrent Test Report ===
Generated: $(date)

Test Configuration:
- Transfer Amount: $TRANSFER_AMOUNT
- From Account: $FROM_ACCOUNT
- To Account: $TO_ACCOUNT
- Concurrent Requests per Service: $CONCURRENT_REQUESTS
- Total Services: 4 (App1, App2, App3, LoadBalancer)

Results Summary:
- Total Requests: $total_requests
- Successful Requests: $successful_requests
- Failed Requests: $failed_requests
- Success Rate: $success_rate%
- Average Response Time: ${avg_response_time}ms

Detailed Results:
See transfer-results.csv for individual request details

Log File: $LOG_FILE
EOF
    
    log_success "Test report generated: $report_file"
    
    # 顯示摘要
    echo ""
    echo "=== Test Summary ==="
    echo "Total Requests: $total_requests"
    echo "Successful: $successful_requests"
    echo "Failed: $failed_requests"
    echo "Success Rate: $success_rate%"
    echo "Average Response Time: ${avg_response_time}ms"
}

# 主函數
main() {
    log_info "Starting Distributed Lock Concurrent Tests"
    log_info "=========================================="
    
    # 檢查依賴
    if ! command -v jq &> /dev/null; then
        log_error "jq is required but not installed. Please install jq first."
        exit 1
    fi
    
    if ! command -v bc &> /dev/null; then
        log_error "bc is required but not installed. Please install bc first."
        exit 1
    fi
    
    # 檢查服務健康狀態
    check_service_health "App1" "$APP1_URL" || exit 1
    check_service_health "App2" "$APP2_URL" || exit 1
    check_service_health "App3" "$APP3_URL" || exit 1
    check_service_health "LoadBalancer" "$LB_URL" || exit 1
    
    # 執行併發測試
    run_concurrent_transfers
    
    # 驗證資料一致性
    verify_data_consistency
    
    # 生成報告
    generate_report
    
    log_success "🎉 Concurrent testing completed successfully!"
    log_info "Results saved to: $RESULTS_DIR"
}

# 執行主函數
main "$@"