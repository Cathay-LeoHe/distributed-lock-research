#!/bin/bash

# Test script for Docker setup verification
# Tests all components of the distributed lock system

set -e

# Configuration
BASE_URL="http://localhost"
REDIS_URL="http://localhost:8080"
ZOOKEEPER_URL="http://localhost:8090"
DYNAMIC_URL="http://localhost:8888"
TIMEOUT=30

# Colors for output
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
BLUE='\033[0;34m'
NC='\033[0m' # No Color

# Test results
TESTS_PASSED=0
TESTS_FAILED=0

# Functions
log_info() {
    echo -e "${BLUE}[INFO]${NC} $1"
}

log_success() {
    echo -e "${GREEN}[PASS]${NC} $1"
    TESTS_PASSED=$((TESTS_PASSED + 1))
}

log_error() {
    echo -e "${RED}[FAIL]${NC} $1"
    TESTS_FAILED=$((TESTS_FAILED + 1))
}

log_warning() {
    echo -e "${YELLOW}[WARN]${NC} $1"
}

log_header() {
    echo ""
    echo -e "${BLUE}========================================${NC}"
    echo -e "${BLUE}$1${NC}"
    echo -e "${BLUE}========================================${NC}"
}

# Test function
test_endpoint() {
    local url="$1"
    local description="$2"
    local expected_status="${3:-200}"
    local timeout="${4:-10}"
    
    if curl -f -s --max-time "$timeout" -o /dev/null -w "%{http_code}" "$url" | grep -q "$expected_status"; then
        log_success "$description"
        return 0
    else
        log_error "$description"
        return 1
    fi
}

# Test JSON endpoint
test_json_endpoint() {
    local url="$1"
    local description="$2"
    local timeout="${3:-10}"
    
    local response=$(curl -f -s --max-time "$timeout" "$url" 2>/dev/null)
    if [[ $? -eq 0 ]] && echo "$response" | jq . >/dev/null 2>&1; then
        log_success "$description"
        return 0
    else
        log_error "$description"
        return 1
    fi
}

# Test with specific headers
test_with_headers() {
    local url="$1"
    local headers="$2"
    local description="$3"
    local timeout="${4:-10}"
    
    if curl -f -s --max-time "$timeout" $headers -o /dev/null "$url"; then
        log_success "$description"
        return 0
    else
        log_error "$description"
        return 1
    fi
}

# Wait for services to be ready
wait_for_services() {
    log_header "Waiting for Services to Start"
    
    local services=("$BASE_URL/health" "$BASE_URL/actuator/health")
    local max_wait=120
    local wait_time=0
    
    for service in "${services[@]}"; do
        log_info "Waiting for $service..."
        while ! curl -f -s --max-time 5 "$service" >/dev/null 2>&1; do
            if [[ $wait_time -ge $max_wait ]]; then
                log_error "Timeout waiting for $service"
                return 1
            fi
            sleep 5
            wait_time=$((wait_time + 5))
            echo -n "."
        done
        echo ""
        log_success "$service is ready"
    done
}

# Test basic connectivity
test_basic_connectivity() {
    log_header "Testing Basic Connectivity"
    
    test_endpoint "$BASE_URL/health" "Nginx health check"
    test_json_endpoint "$BASE_URL/actuator/health" "Application health check"
    test_endpoint "$REDIS_URL/actuator/health" "Redis-specific routing"
    test_endpoint "$ZOOKEEPER_URL/actuator/health" "ZooKeeper-specific routing"
    test_endpoint "$DYNAMIC_URL/actuator/health" "Dynamic routing"
}

# Test load balancing
test_load_balancing() {
    log_header "Testing Load Balancing"
    
    local responses=()
    local unique_responses=0
    
    log_info "Making 10 requests to test load balancing..."
    
    for i in {1..10}; do
        local response=$(curl -s --max-time 5 "$BASE_URL/actuator/info" 2>/dev/null | jq -r '.instance_id // "unknown"' 2>/dev/null || echo "unknown")
        responses+=("$response")
        echo -n "."
    done
    echo ""
    
    # Count unique responses
    unique_responses=$(printf '%s\n' "${responses[@]}" | sort -u | wc -l)
    
    if [[ $unique_responses -gt 1 ]]; then
        log_success "Load balancing working (got $unique_responses different instances)"
    else
        log_warning "Load balancing may not be working properly (got $unique_responses unique responses)"
    fi
}

# Test API endpoints
test_api_endpoints() {
    log_header "Testing API Endpoints"
    
    # Test account balance endpoint
    test_json_endpoint "$BASE_URL/api/accounts/ACC001/balance" "Account balance API"
    
    # Test transfer endpoint (POST)
    local transfer_data='{"fromAccount":"ACC001","toAccount":"ACC002","amount":100.00}'
    if curl -f -s --max-time 10 -X POST -H "Content-Type: application/json" -d "$transfer_data" "$BASE_URL/api/transfer" >/dev/null 2>&1; then
        log_success "Transfer API (POST)"
    else
        log_error "Transfer API (POST)"
    fi
    
    # Test withdrawal endpoint (POST)
    local withdrawal_data='{"accountNumber":"ACC001","amount":50.00}'
    if curl -f -s --max-time 10 -X POST -H "Content-Type: application/json" -d "$withdrawal_data" "$BASE_URL/api/withdraw" >/dev/null 2>&1; then
        log_success "Withdrawal API (POST)"
    else
        log_error "Withdrawal API (POST)"
    fi
}

# Test lock provider routing
test_lock_provider_routing() {
    log_header "Testing Lock Provider Routing"
    
    # Test Redis routing with header
    test_with_headers "$DYNAMIC_URL/actuator/health" "-H 'X-Lock-Provider: redis'" "Redis provider routing"
    
    # Test ZooKeeper routing with header
    test_with_headers "$DYNAMIC_URL/actuator/health" "-H 'X-Lock-Provider: zookeeper'" "ZooKeeper provider routing"
    
    # Test default routing
    test_endpoint "$DYNAMIC_URL/actuator/health" "Default provider routing"
}

# Test failover
test_failover() {
    log_header "Testing Failover Capability"
    
    log_info "Testing failover requires manual container stopping"
    log_info "This test will be skipped in automated testing"
    log_warning "Manual test: Stop one app container and verify requests still work"
}

# Test performance
test_performance() {
    log_header "Testing Performance"
    
    log_info "Running basic performance test..."
    
    # Simple performance test with curl
    local start_time=$(date +%s)
    local success_count=0
    local total_requests=50
    
    for i in $(seq 1 $total_requests); do
        if curl -f -s --max-time 5 "$BASE_URL/actuator/health" >/dev/null 2>&1; then
            success_count=$((success_count + 1))
        fi
        echo -n "."
    done
    echo ""
    
    local end_time=$(date +%s)
    local duration=$((end_time - start_time))
    local success_rate=$((success_count * 100 / total_requests))
    
    log_info "Performance test results:"
    echo "  - Total requests: $total_requests"
    echo "  - Successful requests: $success_count"
    echo "  - Success rate: $success_rate%"
    echo "  - Duration: ${duration}s"
    echo "  - Requests per second: $((total_requests / duration))"
    
    if [[ $success_rate -ge 95 ]]; then
        log_success "Performance test passed (${success_rate}% success rate)"
    else
        log_error "Performance test failed (${success_rate}% success rate)"
    fi
}

# Test monitoring endpoints
test_monitoring() {
    log_header "Testing Monitoring Endpoints"
    
    test_json_endpoint "$BASE_URL/actuator/health" "Health endpoint"
    test_json_endpoint "$BASE_URL/actuator/info" "Info endpoint"
    test_json_endpoint "$BASE_URL/actuator/metrics" "Metrics endpoint"
    
    # Test Prometheus metrics if available
    if curl -f -s --max-time 5 "$BASE_URL/actuator/prometheus" >/dev/null 2>&1; then
        log_success "Prometheus metrics endpoint"
    else
        log_warning "Prometheus metrics endpoint not available"
    fi
}

# Generate test report
generate_report() {
    log_header "Test Report"
    
    local total_tests=$((TESTS_PASSED + TESTS_FAILED))
    local success_rate=0
    
    if [[ $total_tests -gt 0 ]]; then
        success_rate=$((TESTS_PASSED * 100 / total_tests))
    fi
    
    echo "Test Summary:"
    echo "  - Total tests: $total_tests"
    echo "  - Passed: $TESTS_PASSED"
    echo "  - Failed: $TESTS_FAILED"
    echo "  - Success rate: $success_rate%"
    echo ""
    
    if [[ $TESTS_FAILED -eq 0 ]]; then
        log_success "All tests passed! Docker setup is working correctly."
        return 0
    else
        log_error "Some tests failed. Please check the configuration."
        return 1
    fi
}

# Main test execution
main() {
    log_header "Distributed Lock Docker Setup Test"
    
    # Check if jq is available
    if ! command -v jq &> /dev/null; then
        log_warning "jq is not installed. JSON parsing tests may fail."
    fi
    
    # Wait for services
    if ! wait_for_services; then
        log_error "Services are not ready. Exiting."
        exit 1
    fi
    
    # Run tests
    test_basic_connectivity
    test_load_balancing
    test_api_endpoints
    test_lock_provider_routing
    test_monitoring
    test_performance
    test_failover
    
    # Generate report
    generate_report
}

# Run main function
main "$@"