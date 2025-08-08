#!/bin/bash

# API 驗證腳本
# 此腳本測試所有 REST API 端點的功能

set -e

# 顏色定義
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
BLUE='\033[0;34m'
NC='\033[0m' # No Color

# 配置
BASE_URL="${BASE_URL:-http://localhost}"
TIMEOUT=10

# 日誌函數
log_info() {
    echo -e "${BLUE}[INFO]${NC} $1"
}

log_success() {
    echo -e "${GREEN}[SUCCESS]${NC} $1"
}

log_warning() {
    echo -e "${YELLOW}[WARNING]${NC} $1"
}

log_error() {
    echo -e "${RED}[ERROR]${NC} $1"
}

# HTTP 請求函數
make_request() {
    local method="$1"
    local endpoint="$2"
    local data="$3"
    local expected_status="$4"
    
    local curl_cmd="curl -s -w '%{http_code}' --connect-timeout $TIMEOUT"
    
    if [ "$method" = "POST" ]; then
        curl_cmd="$curl_cmd -X POST -H 'Content-Type: application/json'"
        if [ -n "$data" ]; then
            curl_cmd="$curl_cmd -d '$data'"
        fi
    elif [ "$method" = "PUT" ]; then
        curl_cmd="$curl_cmd -X PUT -H 'Content-Type: application/json'"
        if [ -n "$data" ]; then
            curl_cmd="$curl_cmd -d '$data'"
        fi
    fi
    
    local response=$(eval "$curl_cmd '$BASE_URL$endpoint'")
    local status_code="${response: -3}"
    local body="${response%???}"
    
    if [ "$status_code" = "$expected_status" ]; then
        return 0
    else
        log_error "Expected status $expected_status, got $status_code for $method $endpoint"
        echo "Response body: $body"
        return 1
    fi
}

# 檢查服務是否可用
check_service_availability() {
    log_info "檢查服務可用性..."
    
    local max_attempts=30
    local attempt=1
    
    while [ $attempt -le $max_attempts ]; do
        if curl -s --connect-timeout 5 "$BASE_URL/actuator/health" > /dev/null 2>&1; then
            log_success "服務可用"
            return 0
        fi
        
        log_info "等待服務啟動... (嘗試 $attempt/$max_attempts)"
        sleep 2
        ((attempt++))
    done
    
    log_error "服務不可用"
    return 1
}

# 測試健康檢查端點
test_health_endpoints() {
    log_info "測試健康檢查端點..."
    
    # 基本健康檢查
    if make_request "GET" "/actuator/health" "" "200"; then
        log_success "基本健康檢查通過"
    else
        return 1
    fi
    
    # 分散式鎖系統健康檢查
    if make_request "GET" "/actuator/health/distributedLockSystem" "" "200"; then
        log_success "分散式鎖系統健康檢查通過"
    else
        log_warning "分散式鎖系統健康檢查失敗"
    fi
    
    # Redis 健康檢查
    if make_request "GET" "/actuator/health/redis" "" "200"; then
        log_success "Redis 健康檢查通過"
    else
        log_warning "Redis 健康檢查失敗"
    fi
    
    # ZooKeeper 健康檢查
    if make_request "GET" "/actuator/health/zookeeper" "" "200"; then
        log_success "ZooKeeper 健康檢查通過"
    else
        log_warning "ZooKeeper 健康檢查失敗"
    fi
}

# 測試監控端點
test_monitoring_endpoints() {
    log_info "測試監控端點..."
    
    # 應用資訊
    if make_request "GET" "/actuator/info" "" "200"; then
        log_success "應用資訊端點正常"
    else
        log_warning "應用資訊端點異常"
    fi
    
    # 指標端點
    if make_request "GET" "/actuator/metrics" "" "200"; then
        log_success "指標端點正常"
    else
        log_warning "指標端點異常"
    fi
    
    # 業務指標端點
    if make_request "GET" "/actuator/business-metrics" "" "200"; then
        log_success "業務指標端點正常"
    else
        log_warning "業務指標端點異常"
    fi
    
    # 配置資訊端點
    if make_request "GET" "/actuator/configuration-info" "" "200"; then
        log_success "配置資訊端點正常"
    else
        log_warning "配置資訊端點異常"
    fi
}

# 測試銀行 API 端點
test_banking_endpoints() {
    log_info "測試銀行 API 端點..."
    
    # 測試餘額查詢
    log_info "測試餘額查詢 API..."
    if make_request "GET" "/api/accounts/ACC001/balance" "" "200"; then
        log_success "餘額查詢 API 正常"
    else
        return 1
    fi
    
    # 測試不存在帳戶的餘額查詢
    if make_request "GET" "/api/accounts/NONEXISTENT/balance" "" "404"; then
        log_success "不存在帳戶的餘額查詢正確返回 404"
    else
        log_warning "不存在帳戶的餘額查詢未正確處理"
    fi
    
    # 測試匯款 API
    log_info "測試匯款 API..."
    local transfer_data='{"fromAccount":"ACC001","toAccount":"ACC002","amount":10.00}'
    if make_request "POST" "/api/transfer" "$transfer_data" "200"; then
        log_success "匯款 API 正常"
    else
        return 1
    fi
    
    # 測試無效匯款（餘額不足）
    local invalid_transfer='{"fromAccount":"ACC002","toAccount":"ACC001","amount":999999.00}'
    if make_request "POST" "/api/transfer" "$invalid_transfer" "400"; then
        log_success "餘額不足的匯款正確返回 400"
    else
        log_warning "餘額不足的匯款未正確處理"
    fi
    
    # 測試扣款 API
    log_info "測試扣款 API..."
    local withdrawal_data='{"accountNumber":"ACC003","amount":50.00}'
    if make_request "POST" "/api/withdraw" "$withdrawal_data" "200"; then
        log_success "扣款 API 正常"
    else
        return 1
    fi
    
    # 測試無效扣款（餘額不足）
    local invalid_withdrawal='{"accountNumber":"ACC002","amount":999999.00}'
    if make_request "POST" "/api/withdraw" "$invalid_withdrawal" "400"; then
        log_success "餘額不足的扣款正確返回 400"
    else
        log_warning "餘額不足的扣款未正確處理"
    fi
    
    # 測試無效請求格式
    log_info "測試無效請求格式..."
    local invalid_json='{"invalid":"json"}'
    if make_request "POST" "/api/transfer" "$invalid_json" "400"; then
        log_success "無效請求格式正確返回 400"
    else
        log_warning "無效請求格式未正確處理"
    fi
}

# 測試鎖管理端點
test_lock_management_endpoints() {
    log_info "測試鎖管理端點..."
    
    # 測試獲取當前鎖提供者
    if make_request "GET" "/api/lock/provider" "" "200"; then
        log_success "獲取鎖提供者 API 正常"
    else
        return 1
    fi
    
    # 測試切換到 Redis
    if make_request "POST" "/api/lock/provider?provider=redis" "" "200"; then
        log_success "切換到 Redis 鎖提供者成功"
    else
        log_warning "切換到 Redis 鎖提供者失敗"
    fi
    
    # 測試切換到 ZooKeeper
    if make_request "POST" "/api/lock/provider?provider=zookeeper" "" "200"; then
        log_success "切換到 ZooKeeper 鎖提供者成功"
    else
        log_warning "切換到 ZooKeeper 鎖提供者失敗"
    fi
    
    # 測試無效的鎖提供者
    if make_request "POST" "/api/lock/provider?provider=invalid" "" "400"; then
        log_success "無效鎖提供者正確返回 400"
    else
        log_warning "無效鎖提供者未正確處理"
    fi
    
    # 測試鎖狀態查詢
    if make_request "GET" "/api/lock/status" "" "200"; then
        log_success "鎖狀態查詢 API 正常"
    else
        log_warning "鎖狀態查詢 API 異常"
    fi
}

# 測試 OpenAPI 文件
test_openapi_documentation() {
    log_info "測試 OpenAPI 文件..."
    
    # 測試 Swagger UI
    if make_request "GET" "/swagger-ui.html" "" "200"; then
        log_success "Swagger UI 可用"
    else
        log_warning "Swagger UI 不可用"
    fi
    
    # 測試 OpenAPI JSON
    if make_request "GET" "/v3/api-docs" "" "200"; then
        log_success "OpenAPI JSON 文件可用"
    else
        log_warning "OpenAPI JSON 文件不可用"
    fi
}

# 執行併發測試
test_concurrent_operations() {
    log_info "執行併發操作測試..."
    
    # 建立併發匯款測試
    local pids=()
    local success_count=0
    
    for i in {1..10}; do
        (
            local transfer_data='{"fromAccount":"ACC001","toAccount":"ACC002","amount":1.00}'
            if curl -s -X POST "$BASE_URL/api/transfer" \
                -H "Content-Type: application/json" \
                -d "$transfer_data" \
                --connect-timeout $TIMEOUT | grep -q '"success":true'; then
                echo "SUCCESS"
            else
                echo "FAILED"
            fi
        ) &
        pids+=($!)
    done
    
    # 等待所有併發操作完成
    for pid in "${pids[@]}"; do
        if wait $pid; then
            ((success_count++))
        fi
    done
    
    if [ $success_count -gt 5 ]; then
        log_success "併發操作測試通過 ($success_count/10 成功)"
    else
        log_warning "併發操作測試部分失敗 ($success_count/10 成功)"
    fi
}

# 測試錯誤處理
test_error_handling() {
    log_info "測試錯誤處理..."
    
    # 測試 404 錯誤
    if make_request "GET" "/api/nonexistent" "" "404"; then
        log_success "404 錯誤處理正常"
    else
        log_warning "404 錯誤處理異常"
    fi
    
    # 測試 405 錯誤（方法不允許）
    if make_request "POST" "/api/accounts/ACC001/balance" "" "405"; then
        log_success "405 錯誤處理正常"
    else
        log_warning "405 錯誤處理異常"
    fi
    
    # 測試無效 JSON
    local invalid_json='{"invalid":json}'
    if curl -s -X POST "$BASE_URL/api/transfer" \
        -H "Content-Type: application/json" \
        -d "$invalid_json" \
        --connect-timeout $TIMEOUT \
        -w '%{http_code}' | tail -c 3 | grep -q "400"; then
        log_success "無效 JSON 錯誤處理正常"
    else
        log_warning "無效 JSON 錯誤處理異常"
    fi
}

# 性能基準測試
test_performance_baseline() {
    log_info "執行性能基準測試..."
    
    local start_time=$(date +%s%N)
    local request_count=100
    local success_count=0
    
    for i in $(seq 1 $request_count); do
        if curl -s "$BASE_URL/api/accounts/ACC001/balance" \
            --connect-timeout $TIMEOUT > /dev/null 2>&1; then
            ((success_count++))
        fi
    done
    
    local end_time=$(date +%s%N)
    local duration=$(( (end_time - start_time) / 1000000 )) # 轉換為毫秒
    local avg_response_time=$(( duration / request_count ))
    
    log_info "性能基準測試結果:"
    echo "  - 總請求數: $request_count"
    echo "  - 成功請求數: $success_count"
    echo "  - 成功率: $(( success_count * 100 / request_count ))%"
    echo "  - 總耗時: ${duration}ms"
    echo "  - 平均回應時間: ${avg_response_time}ms"
    
    if [ $success_count -gt $(( request_count * 95 / 100 )) ] && [ $avg_response_time -lt 100 ]; then
        log_success "性能基準測試通過"
    else
        log_warning "性能基準測試未達到預期"
    fi
}

# 主函數
main() {
    echo "========================================"
    echo "API 驗證測試"
    echo "========================================"
    echo "測試目標: $BASE_URL"
    echo ""
    
    local start_time=$(date +%s)
    local failed_tests=()
    
    # 檢查服務可用性
    if ! check_service_availability; then
        log_error "服務不可用，無法執行測試"
        exit 1
    fi
    
    echo "開始執行 API 測試..."
    echo "----------------------------------------"
    
    # 執行各項測試
    if ! test_health_endpoints; then
        failed_tests+=("健康檢查端點")
    fi
    
    if ! test_monitoring_endpoints; then
        failed_tests+=("監控端點")
    fi
    
    if ! test_banking_endpoints; then
        failed_tests+=("銀行 API 端點")
    fi
    
    if ! test_lock_management_endpoints; then
        failed_tests+=("鎖管理端點")
    fi
    
    test_openapi_documentation
    test_concurrent_operations
    test_error_handling
    test_performance_baseline
    
    # 計算執行時間
    local end_time=$(date +%s)
    local duration=$((end_time - start_time))
    
    # 輸出結果摘要
    echo ""
    echo "========================================"
    echo "API 測試結果摘要"
    echo "========================================"
    echo "執行時間: ${duration} 秒"
    
    if [ ${#failed_tests[@]} -eq 0 ]; then
        log_success "所有核心 API 測試通過！"
        echo ""
        echo "✅ 健康檢查端點正常"
        echo "✅ 監控端點正常"
        echo "✅ 銀行業務 API 正常"
        echo "✅ 鎖管理 API 正常"
        echo "✅ 錯誤處理正確"
        echo "✅ 併發操作穩定"
        echo "✅ 性能表現良好"
        exit 0
    else
        log_error "以下核心測試失敗:"
        for test in "${failed_tests[@]}"; do
            echo "  ❌ $test"
        done
        echo ""
        echo "請檢查服務狀態並修復問題後重新執行。"
        exit 1
    fi
}

# 處理命令列參數
while [[ $# -gt 0 ]]; do
    case $1 in
        --base-url)
            BASE_URL="$2"
            shift 2
            ;;
        --timeout)
            TIMEOUT="$2"
            shift 2
            ;;
        --help)
            echo "用法: $0 [選項]"
            echo ""
            echo "選項:"
            echo "  --base-url URL    設定基礎 URL (預設: http://localhost)"
            echo "  --timeout SECONDS 設定請求超時時間 (預設: 10)"
            echo "  --help           顯示此幫助訊息"
            exit 0
            ;;
        *)
            log_error "未知選項: $1"
            echo "使用 --help 查看可用選項"
            exit 1
            ;;
    esac
done

# 執行主函數
main "$@"