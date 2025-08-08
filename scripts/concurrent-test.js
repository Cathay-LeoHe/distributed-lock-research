#!/usr/bin/env node

/**
 * 高併發分散式鎖測試腳本
 * 使用 Newman 執行 Postman 集合，模擬多服務高併發場景
 */

const newman = require('newman');
const fs = require('fs');
const path = require('path');

// 測試配置
const TEST_CONFIG = {
    // 併發設定
    concurrent: {
        iterations: 20,        // 每個服務的請求次數
        parallelRuns: 3,       // 並行運行的服務數量
        delayRequest: 100      // 請求間延遲（毫秒）
    },
    
    // 測試參數
    testParams: {
        transferAmount: 50.00,
        fromAccount: 'ACC001',
        toAccount: 'ACC002'
    },
    
    // 服務端點
    services: [
        { name: 'App1', url: 'http://localhost:8081/api' },
        { name: 'App2', url: 'http://localhost:8082/api' },
        { name: 'App3', url: 'http://localhost:8083/api' }
    ]
};

// 結果統計
let testResults = {
    totalRequests: 0,
    successfulRequests: 0,
    failedRequests: 0,
    averageResponseTime: 0,
    errors: [],
    startTime: null,
    endTime: null
};

/**
 * 執行單個 Newman 測試
 */
function runNewmanTest(serviceConfig, iterationCount) {
    return new Promise((resolve, reject) => {
        const collectionPath = path.join(__dirname, '../postman/Distributed-Lock-Concurrent-Tests.postman_collection.json');
        const environmentPath = path.join(__dirname, '../postman/Distributed-Lock-Environment.postman_environment.json');
        
        console.log(`🚀 Starting ${iterationCount} iterations for ${serviceConfig.name}...`);
        
        newman.run({
            collection: collectionPath,
            environment: environmentPath,
            iterationCount: iterationCount,
            delayRequest: TEST_CONFIG.concurrent.delayRequest,
            reporters: ['cli', 'json'],
            reporter: {
                json: {
                    export: `./test-results/newman-${serviceConfig.name}-${Date.now()}.json`
                }
            },
            globals: [
                {
                    key: 'app_url',
                    value: serviceConfig.url,
                    type: 'string'
                },
                {
                    key: 'transfer_amount',
                    value: TEST_CONFIG.testParams.transferAmount.toString(),
                    type: 'string'
                },
                {
                    key: 'from_account',
                    value: TEST_CONFIG.testParams.fromAccount,
                    type: 'string'
                },
                {
                    key: 'to_account',
                    value: TEST_CONFIG.testParams.toAccount,
                    type: 'string'
                }
            ]
        }, (err, summary) => {
            if (err) {
                console.error(`❌ ${serviceConfig.name} test failed:`, err);
                testResults.errors.push({
                    service: serviceConfig.name,
                    error: err.message
                });
                reject(err);
            } else {
                console.log(`✅ ${serviceConfig.name} test completed`);
                
                // 更新統計
                testResults.totalRequests += summary.run.stats.requests.total;
                testResults.successfulRequests += summary.run.stats.requests.total - summary.run.stats.requests.failed;
                testResults.failedRequests += summary.run.stats.requests.failed;
                
                // 計算平均響應時間
                const avgResponseTime = summary.run.timings.responseAverage;
                testResults.averageResponseTime = 
                    (testResults.averageResponseTime + avgResponseTime) / 2;
                
                resolve(summary);
            }
        });
    });
}

/**
 * 執行併發測試
 */
async function runConcurrentTests() {
    console.log('🎯 Starting Distributed Lock Concurrent Tests');
    console.log('=' .repeat(50));
    console.log(`Configuration:`);
    console.log(`  - Iterations per service: ${TEST_CONFIG.concurrent.iterations}`);
    console.log(`  - Parallel services: ${TEST_CONFIG.concurrent.parallelRuns}`);
    console.log(`  - Transfer amount: $${TEST_CONFIG.testParams.transferAmount}`);
    console.log(`  - From account: ${TEST_CONFIG.testParams.fromAccount}`);
    console.log(`  - To account: ${TEST_CONFIG.testParams.toAccount}`);
    console.log('=' .repeat(50));
    
    testResults.startTime = new Date();
    
    // 創建結果目錄
    const resultsDir = './test-results';
    if (!fs.existsSync(resultsDir)) {
        fs.mkdirSync(resultsDir, { recursive: true });
    }
    
    try {
        // 並行執行多個服務的測試
        const testPromises = TEST_CONFIG.services.map(service => 
            runNewmanTest(service, TEST_CONFIG.concurrent.iterations)
        );
        
        const results = await Promise.allSettled(testPromises);
        
        testResults.endTime = new Date();
        
        // 分析結果
        console.log('\n📊 Test Results Summary');
        console.log('=' .repeat(50));
        console.log(`Total execution time: ${testResults.endTime - testResults.startTime}ms`);
        console.log(`Total requests: ${testResults.totalRequests}`);
        console.log(`Successful requests: ${testResults.successfulRequests}`);
        console.log(`Failed requests: ${testResults.failedRequests}`);
        console.log(`Success rate: ${((testResults.successfulRequests / testResults.totalRequests) * 100).toFixed(2)}%`);
        console.log(`Average response time: ${testResults.averageResponseTime.toFixed(2)}ms`);
        
        if (testResults.errors.length > 0) {
            console.log('\n❌ Errors encountered:');
            testResults.errors.forEach(error => {
                console.log(`  - ${error.service}: ${error.error}`);
            });
        }
        
        // 保存詳細結果
        const detailedResults = {
            config: TEST_CONFIG,
            results: testResults,
            testSummaries: results
        };
        
        fs.writeFileSync(
            path.join(resultsDir, `concurrent-test-summary-${Date.now()}.json`),
            JSON.stringify(detailedResults, null, 2)
        );
        
        console.log(`\n📁 Detailed results saved to: ${resultsDir}/`);
        
    } catch (error) {
        console.error('❌ Concurrent test execution failed:', error);
        process.exit(1);
    }
}

/**
 * 驗證資料一致性
 */
async function verifyDataConsistency() {
    console.log('\n🔍 Verifying data consistency...');
    
    const axios = require('axios');
    
    try {
        // 從不同服務獲取帳戶餘額
        const balanceChecks = await Promise.all([
            axios.get(`${TEST_CONFIG.services[0].url}/accounts/${TEST_CONFIG.testParams.fromAccount}/balance`),
            axios.get(`${TEST_CONFIG.services[1].url}/accounts/${TEST_CONFIG.testParams.fromAccount}/balance`),
            axios.get(`${TEST_CONFIG.services[2].url}/accounts/${TEST_CONFIG.testParams.fromAccount}/balance`),
            axios.get(`${TEST_CONFIG.services[0].url}/accounts/${TEST_CONFIG.testParams.toAccount}/balance`),
            axios.get(`${TEST_CONFIG.services[1].url}/accounts/${TEST_CONFIG.testParams.toAccount}/balance`),
            axios.get(`${TEST_CONFIG.services[2].url}/accounts/${TEST_CONFIG.testParams.toAccount}/balance`)
        ]);
        
        const fromBalances = balanceChecks.slice(0, 3).map(response => response.data.data.balance);
        const toBalances = balanceChecks.slice(3, 6).map(response => response.data.data.balance);
        
        console.log(`${TEST_CONFIG.testParams.fromAccount} balances across services:`, fromBalances);
        console.log(`${TEST_CONFIG.testParams.toAccount} balances across services:`, toBalances);
        
        // 檢查一致性
        const fromConsistent = fromBalances.every(balance => balance === fromBalances[0]);
        const toConsistent = toBalances.every(balance => balance === toBalances[0]);
        
        if (fromConsistent && toConsistent) {
            console.log('✅ Data consistency verified - all services show identical balances');
        } else {
            console.log('❌ Data inconsistency detected!');
            console.log('This indicates potential issues with the distributed lock mechanism');
        }
        
    } catch (error) {
        console.error('❌ Failed to verify data consistency:', error.message);
    }
}

// 主執行函數
async function main() {
    try {
        await runConcurrentTests();
        await verifyDataConsistency();
        
        console.log('\n🎉 Concurrent testing completed successfully!');
        
    } catch (error) {
        console.error('❌ Test execution failed:', error);
        process.exit(1);
    }
}

// 如果直接執行此腳本
if (require.main === module) {
    main();
}

module.exports = {
    runConcurrentTests,
    verifyDataConsistency,
    TEST_CONFIG
};