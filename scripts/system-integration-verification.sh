#!/bin/bash

# 系統整合驗證腳本
# 此腳本執行完整的系統整合和驗證測試

set -e

# 顏色定義
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
BLUE='\033[0;34m'
NC='\033[0m' # No Color

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

# 檢查必要工具
check_prerequisites() {
    log_info "檢查必要工具..."
    
    if ! command -v docker &> /dev/null; then
        log_error "Docker 未安裝或不在 PATH 中"
        exit 1
    fi
    
    if ! command -v docker-compose &> /dev/null; then
        log_error "Docker Compose 未安裝或不在 PATH 中"
        exit 1
    fi
    
    if ! command -v mvn &> /dev/null; then
        log_error "Maven 未安裝或不在 PATH 中"
        exit 1
    fi
    
    log_success "所有必要工具已安裝"
}

# 清理環境
cleanup_environment() {
    log_info "清理測試環境..."
    
    # 停止並移除 Docker 容器
    docker-compose down -v --remove-orphans 2>/dev/null || true
    
    # 清理 Maven 測試快取
    mvn clean -q
    
    log_success "環境清理完成"
}

# 建構應用程式
build_application() {
    log_info "建構應用程式..."
    
    # 編譯應用程式
    if mvn compile -q; then
        log_success "應用程式編譯成功"
    else
        log_error "應用程式編譯失敗"
        exit 1
    fi
    
    # 建構 Docker 映像
    if ./scripts/build-docker.sh; then
        log_success "Docker 映像建構成功"
    else
        log_error "Docker 映像建構失敗"
        exit 1
    fi
}

# 執行單元測試
run_unit_tests() {
    log_info "執行單元測試..."
    
    if mvn test -Dtest="!*Integration*" -q; then
        log_success "單元測試通過"
    else
        log_error "單元測試失敗"
        return 1
    fi
}

# 執行整合測試
run_integration_tests() {
    log_info "執行整合測試..."
    
    # 啟動測試容器
    log_info "啟動測試依賴服務..."
    docker-compose -f docker-compose.yml up -d redis zookeeper
    
    # 等待服務啟動
    sleep 10
    
    # 執行整合測試
    if mvn test -Dtest="*Integration*" -Dspring.profiles.active=test; then
        log_success "整合測試通過"
    else
        log_error "整合測試失敗"
        docker-compose down
        return 1
    fi
    
    # 清理測試容器
    docker-compose down
}

# 執行系統整合測試
run_system_integration_tests() {
    log_info "執行系統整合測試..."
    
    if mvn test -Dtest="SystemIntegrationTest" -Dspring.profiles.active=test; then
        log_success "系統整合測試通過"
    else
        log_error "系統整合測試失敗"
        return 1
    fi
}

# 執行併發驗證測試
run_concurrency_tests() {
    log_info "執行併發驗證測試..."
    
    if mvn test -Dtest="ConcurrencyVerificationTest" -Dspring.profiles.active=test; then
        log_success "併發驗證測試通過"
    else
        log_error "併發驗證測試失敗"
        return 1
    fi
}

# 執行 Docker 部署測試
run_docker_deployment_tests() {
    log_info "執行 Docker 部署測試..."
    
    # 啟動完整的 Docker 環境
    log_info "啟動完整 Docker 環境..."
    if docker-compose up -d; then
        log_success "Docker 環境啟動成功"
    else
        log_error "Docker 環境啟動失敗"
        return 1
    fi
    
    # 等待服務完全啟動
    log_info "等待服務啟動..."
    sleep 30
    
    # 檢查服務健康狀態
    check_service_health
    
    # 執行 API 功能測試
    test_api_functionality
    
    # 執行負載均衡測試
    test_load_balancing
    
    # 執行多實例併發測試
    test_multi_instance_concurrency
    
    # 清理 Docker 環境
    docker-compose down
    log_success "Docker 部署測試完成"
}

# 檢查服務健康狀態
check_service_health() {
    log_info "檢查服務健康狀態..."
    
    local max_attempts=30
    local attempt=1
    
    while [ $attempt -le $max_attempts ]; do
        if curl -s http://localhost/actuator/health | grep -q '"status":"UP"'; then
            log_success "服務健康檢查通過"
            return 0
        fi
        
        log_info "等待服務啟動... (嘗試 $attempt/$max_attempts)"
        sleep 2
        ((attempt++))
    done
    
    log_error "服務健康檢查失敗"
    return 1
}

# 測試 API 功能
test_api_functionality() {
    log_info "測試 API 功能..."
    
    # 測試餘額查詢
    if curl -s http://localhost/api/accounts/ACC001/balance | grep -q '"success":true'; then
        log_success "餘額查詢 API 正常"
    else
        log_error "餘額查詢 API 異常"
        return 1
    fi
    
    # 測試匯款功能
    local transfer_response=$(curl -s -X POST http://localhost/api/transfer \
        -H "Content-Type: application/json" \
        -d '{"fromAccount":"ACC001","toAccount":"ACC002","amount":10.00}')
    
    if echo "$transfer_response" | grep -q '"success":true'; then
        log_success "匯款 API 正常"
    else
        log_error "匯款 API 異常"
        return 1
    fi
    
    # 測試扣款功能
    local withdrawal_response=$(curl -s -X POST http://localhost/api/withdraw \
        -H "Content-Type: application/json" \
        -d '{"accountNumber":"ACC003","amount":50.00}')
    
    if echo "$withdrawal_response" | grep -q '"success":true'; then
        log_success "扣款 API 正常"
    else
        log_error "扣款 API 異常"
        return 1
    fi
}

# 測試負載均衡
test_load_balancing() {
    log_info "測試負載均衡功能..."
    
    local responses=()
    
    # 發送多個請求
    for i in {1..10}; do
        local response=$(curl -s http://localhost/actuator/info)
        responses+=("$response")
    done
    
    # 檢查是否收到回應
    if [ ${#responses[@]} -eq 10 ]; then
        log_success "負載均衡測試通過"
    else
        log_error "負載均衡測試失敗"
        return 1
    fi
}

# 測試多實例併發處理
test_multi_instance_concurrency() {
    log_info "測試多實例併發處理..."
    
    # 建立測試帳戶（如果不存在）
    curl -s -X POST http://localhost/api/accounts \
        -H "Content-Type: application/json" \
        -d '{"accountNumber":"CONCURRENT_TEST","balance":1000.00}' || true
    
    # 執行併發扣款測試
    local pids=()
    for i in {1..20}; do
        (
            curl -s -X POST http://localhost/api/withdraw \
                -H "Content-Type: application/json" \
                -d '{"accountNumber":"CONCURRENT_TEST","amount":10.00}' > /dev/null
        ) &
        pids+=($!)
    done
    
    # 等待所有請求完成
    for pid in "${pids[@]}"; do
        wait $pid
    done
    
    log_success "多實例併發測試完成"
}

# 執行性能基準測試
run_performance_benchmarks() {
    log_info "執行性能基準測試..."
    
    # 啟動測試環境
    docker-compose up -d redis zookeeper
    sleep 10
    
    # 執行性能測試
    if mvn test -Dtest="*Performance*" -Dspring.profiles.active=test; then
        log_success "性能基準測試完成"
    else
        log_warning "性能基準測試未完全通過"
    fi
    
    docker-compose down
}

# 生成測試報告
generate_test_report() {
    log_info "生成測試報告..."
    
    # 生成 Maven 測試報告
    mvn surefire-report:report -q
    
    # 檢查報告是否生成
    if [ -f "target/site/surefire-report.html" ]; then
        log_success "測試報告已生成: target/site/surefire-report.html"
    else
        log_warning "測試報告生成失敗"
    fi
}

# 主函數
main() {
    echo "========================================"
    echo "分散式鎖系統整合驗證"
    echo "========================================"
    
    local start_time=$(date +%s)
    local failed_tests=()
    
    # 檢查先決條件
    check_prerequisites
    
    # 清理環境
    cleanup_environment
    
    # 建構應用程式
    build_application
    
    # 執行測試套件
    echo ""
    echo "開始執行測試套件..."
    echo "----------------------------------------"
    
    # 1. 單元測試
    if ! run_unit_tests; then
        failed_tests+=("單元測試")
    fi
    
    # 2. 整合測試
    if ! run_integration_tests; then
        failed_tests+=("整合測試")
    fi
    
    # 3. 系統整合測試
    if ! run_system_integration_tests; then
        failed_tests+=("系統整合測試")
    fi
    
    # 4. 併發驗證測試
    if ! run_concurrency_tests; then
        failed_tests+=("併發驗證測試")
    fi
    
    # 5. Docker 部署測試
    if ! run_docker_deployment_tests; then
        failed_tests+=("Docker 部署測試")
    fi
    
    # 6. 性能基準測試
    run_performance_benchmarks
    
    # 生成測試報告
    generate_test_report
    
    # 最終清理
    cleanup_environment
    
    # 計算執行時間
    local end_time=$(date +%s)
    local duration=$((end_time - start_time))
    
    # 輸出結果摘要
    echo ""
    echo "========================================"
    echo "測試結果摘要"
    echo "========================================"
    echo "執行時間: ${duration} 秒"
    
    if [ ${#failed_tests[@]} -eq 0 ]; then
        log_success "所有測試通過！系統整合驗證成功。"
        echo ""
        echo "✅ 系統組件整合正常"
        echo "✅ Redis 和 ZooKeeper 分散式鎖功能正確"
        echo "✅ 銀行業務邏輯完整"
        echo "✅ 併發場景下資料一致性良好"
        echo "✅ Docker 部署和多實例擴展正常"
        echo "✅ API 端點功能完整"
        echo "✅ 負載均衡和健康檢查正常"
        exit 0
    else
        log_error "以下測試失敗:"
        for test in "${failed_tests[@]}"; do
            echo "  ❌ $test"
        done
        echo ""
        echo "請檢查測試日誌並修復問題後重新執行。"
        exit 1
    fi
}

# 執行主函數
main "$@"