#!/bin/bash

# 資料庫重置腳本
# 提供常用的資料庫操作命令

set -e

# 顏色定義
GREEN='\033[0;32m'
RED='\033[0;31m'
YELLOW='\033[1;33m'
BLUE='\033[0;34m'
NC='\033[0m'

# Docker Compose 執行 PostgreSQL 命令的基本語法
DB_EXEC="docker-compose exec postgres psql -U postgres -d distributed_lock -c"

echo -e "${BLUE}📊 Database Management Script${NC}"
echo "=================================="

# 顯示使用方法
show_usage() {
    echo -e "${YELLOW}使用方法:${NC}"
    echo "  $0 [選項]"
    echo ""
    echo -e "${YELLOW}選項:${NC}"
    echo "  reset-accounts    重置 ACC001 和 ACC002 為初始值"
    echo "  show-balances     顯示所有帳戶餘額"
    echo "  show-transactions 顯示交易記錄"
    echo "  clear-transactions 清除所有交易記錄"
    echo "  show-all          顯示完整資料庫狀態"
    echo "  custom           執行自定義 SQL 命令"
    echo "  help             顯示此幫助信息"
    echo ""
    echo -e "${YELLOW}直接 SQL 語法範例:${NC}"
    echo "# 基本命令格式："
    echo "docker-compose exec postgres psql -U postgres -d distributed_lock -c \"SQL_COMMAND\""
    echo ""
}

# 重置帳戶餘額為初始值
reset_accounts() {
    echo -e "${BLUE}🔄 重置帳戶餘額為初始值...${NC}"
    
    # 重置 ACC001 = 10000.00
    $DB_EXEC "UPDATE accounts SET balance = 10000.00, updated_at = CURRENT_TIMESTAMP WHERE account_number = 'ACC001';"
    
    # 重置 ACC002 = 20000.00  
    $DB_EXEC "UPDATE accounts SET balance = 20000.00, updated_at = CURRENT_TIMESTAMP WHERE account_number = 'ACC002';"
    
    echo -e "${GREEN}✅ 帳戶餘額已重置${NC}"
    show_balances
}

# 顯示所有帳戶餘額
show_balances() {
    echo -e "${BLUE}💰 帳戶餘額:${NC}"
    $DB_EXEC "SELECT account_number, balance, status, updated_at FROM accounts ORDER BY account_number;"
}

# 顯示交易記錄
show_transactions() {
    echo -e "${BLUE}📋 最近的交易記錄 (最新 20 筆):${NC}"
    $DB_EXEC "SELECT transaction_id, from_account, to_account, amount, status, lock_provider, created_at FROM transactions ORDER BY created_at DESC LIMIT 20;"
}

# 清除所有交易記錄
clear_transactions() {
    echo -e "${YELLOW}⚠️  警告: 即將刪除所有交易記錄${NC}"
    read -p "確定要繼續嗎? (y/N): " -n 1 -r
    echo
    if [[ $REPLY =~ ^[Yy]$ ]]; then
        $DB_EXEC "DELETE FROM transactions;"
        echo -e "${GREEN}✅ 交易記錄已清除${NC}"
    else
        echo -e "${YELLOW}❌ 操作已取消${NC}"
    fi
}

# 顯示完整資料庫狀態
show_all() {
    echo -e "${BLUE}📊 完整資料庫狀態:${NC}"
    echo ""
    
    echo -e "${YELLOW}帳戶總數:${NC}"
    $DB_EXEC "SELECT COUNT(*) as total_accounts FROM accounts;"
    echo ""
    
    echo -e "${YELLOW}交易總數:${NC}"
    $DB_EXEC "SELECT COUNT(*) as total_transactions FROM transactions;"
    echo ""
    
    echo -e "${YELLOW}各狀態交易統計:${NC}"
    $DB_EXEC "SELECT status, COUNT(*) as count FROM transactions GROUP BY status;"
    echo ""
    
    echo -e "${YELLOW}鎖提供者統計:${NC}"
    $DB_EXEC "SELECT lock_provider, COUNT(*) as count FROM transactions WHERE lock_provider IS NOT NULL GROUP BY lock_provider;"
    echo ""
    
    show_balances
}

# 執行自定義 SQL
custom_sql() {
    echo -e "${YELLOW}輸入 SQL 命令 (按 Enter 結束):${NC}"
    read -r sql_command
    
    if [ -n "$sql_command" ]; then
        echo -e "${BLUE}執行: $sql_command${NC}"
        $DB_EXEC "$sql_command"
    else
        echo -e "${YELLOW}❌ 沒有輸入 SQL 命令${NC}"
    fi
}

# 主要邏輯
case "${1:-help}" in
    reset-accounts)
        reset_accounts
        ;;
    show-balances)
        show_balances
        ;;
    show-transactions)
        show_transactions
        ;;
    clear-transactions)
        clear_transactions
        ;;
    show-all)
        show_all
        ;;
    custom)
        custom_sql
        ;;
    help|*)
        show_usage
        ;;
esac

echo ""
echo -e "${BLUE}常用 SQL 命令參考:${NC}"
echo ""
echo -e "${YELLOW}# 查看特定帳戶餘額${NC}"
echo "docker-compose exec postgres psql -U postgres -d distributed_lock -c \"SELECT * FROM accounts WHERE account_number = 'ACC001';\""
echo ""
echo -e "${YELLOW}# 更新帳戶餘額${NC}"
echo "docker-compose exec postgres psql -U postgres -d distributed_lock -c \"UPDATE accounts SET balance = 5000.00 WHERE account_number = 'ACC001';\""
echo ""
echo -e "${YELLOW}# 查看最近交易${NC}"
echo "docker-compose exec postgres psql -U postgres -d distributed_lock -c \"SELECT * FROM transactions ORDER BY created_at DESC LIMIT 10;\""
echo ""
echo -e "${YELLOW}# 統計成功/失敗交易${NC}"
echo "docker-compose exec postgres psql -U postgres -d distributed_lock -c \"SELECT status, COUNT(*) FROM transactions GROUP BY status;\""
echo ""
echo -e "${YELLOW}# 查看特定時間範圍的交易${NC}"
echo "docker-compose exec postgres psql -U postgres -d distributed_lock -c \"SELECT * FROM transactions WHERE created_at > NOW() - INTERVAL '1 hour';\""