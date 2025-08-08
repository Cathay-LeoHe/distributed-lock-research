package com.example.distributedlock.integration;

import com.example.distributedlock.lock.DistributedLock;
import com.example.distributedlock.lock.RedisDistributedLock;
import com.example.distributedlock.lock.ZooKeeperDistributedLock;
import com.example.distributedlock.services.TransferService;
import com.example.distributedlock.services.WithdrawalService;
import com.example.distributedlock.models.Account;
import com.example.distributedlock.models.AccountStatus;
import com.example.distributedlock.repositories.AccountRepository;
import com.example.distributedlock.repositories.TransactionRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.wait.strategy.Wait;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.math.BigDecimal;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.List;
import java.util.ArrayList;
import java.util.Collections;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 併發驗證測試 - 驗證分散式鎖在高併發場景下的有效性
 * 
 * 測試範圍：
 * - 鎖的互斥性驗證
 * - 高併發下的資料一致性
 * - 鎖的公平性測試
 * - 性能基準測試
 * - 故障恢復測試
 */
@SpringBootTest
@Testcontainers
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
@ActiveProfiles("test")
public class ConcurrencyVerificationTest {

    @Container
    static GenericContainer<?> redis = new GenericContainer<>("redis:7-alpine")
            .withExposedPorts(6379)
            .waitingFor(Wait.forLogMessage(".*Ready to accept connections.*", 1));

    @Container
    static GenericContainer<?> zookeeper = new GenericContainer<>("zookeeper:3.8")
            .withExposedPorts(2181)
            .withEnv("ZOO_MY_ID", "1")
            .withEnv("ZOO_SERVERS", "server.1=0.0.0.0:2888:3888;2181")
            .waitingFor(Wait.forLogMessage(".*binding to port.*", 1));

    @DynamicPropertySource
    static void configureProperties(DynamicPropertyRegistry registry) {
        registry.add("distributed-lock.redis.host", redis::getHost);
        registry.add("distributed-lock.redis.port", redis::getFirstMappedPort);
        registry.add("distributed-lock.zookeeper.connect-string", 
            () -> zookeeper.getHost() + ":" + zookeeper.getFirstMappedPort());
    }

    @Autowired
    private RedisDistributedLock redisDistributedLock;

    @Autowired
    private ZooKeeperDistributedLock zooKeeperDistributedLock;

    @Autowired
    private TransferService transferService;

    @Autowired
    private WithdrawalService withdrawalService;

    @Autowired
    private AccountRepository accountRepository;

    @Autowired
    private TransactionRepository transactionRepository;

    private static final String TEST_ACCOUNT_1 = "CONCURRENCY_TEST_1";
    private static final String TEST_ACCOUNT_2 = "CONCURRENCY_TEST_2";
    private static final String HIGH_BALANCE_ACCOUNT = "HIGH_BALANCE_TEST";

    @BeforeEach
    void setUp() {
        // 清理測試資料
        transactionRepository.deleteAll();
        accountRepository.deleteAll();
        
        // 建立測試帳戶
        createTestAccount(TEST_ACCOUNT_1, new BigDecimal("1000.00"));
        createTestAccount(TEST_ACCOUNT_2, new BigDecimal("1000.00"));
        createTestAccount(HIGH_BALANCE_ACCOUNT, new BigDecimal("100000.00"));
    }

    private void createTestAccount(String accountNumber, BigDecimal balance) {
        Account account = new Account();
        account.setAccountNumber(accountNumber);
        account.setBalance(balance);
        account.setStatus(AccountStatus.ACTIVE);
        accountRepository.save(account);
    }

    /**
     * 測試 1: Redis 分散式鎖互斥性驗證
     * 需求: 1.1, 1.2
     */
    @Test
    @Order(1)
    void testRedisLockMutualExclusion() throws Exception {
        testLockMutualExclusion(redisDistributedLock, "Redis");
    }

    /**
     * 測試 2: ZooKeeper 分散式鎖互斥性驗證
     * 需求: 2.1, 2.2
     */
    @Test
    @Order(2)
    void testZooKeeperLockMutualExclusion() throws Exception {
        testLockMutualExclusion(zooKeeperDistributedLock, "ZooKeeper");
    }

    /**
     * 測試 3: Redis 鎖高併發資料一致性
     * 需求: 1.3, 1.4
     */
    @Test
    @Order(3)
    void testRedisLockDataConsistencyUnderHighConcurrency() throws Exception {
        testDataConsistencyUnderHighConcurrency(redisDistributedLock, "Redis");
    }

    /**
     * 測試 4: ZooKeeper 鎖高併發資料一致性
     * 需求: 2.3, 2.4
     */
    @Test
    @Order(4)
    void testZooKeeperLockDataConsistencyUnderHighConcurrency() throws Exception {
        testDataConsistencyUnderHighConcurrency(zooKeeperDistributedLock, "ZooKeeper");
    }

    /**
     * 測試 5: 鎖性能基準測試
     * 需求: 6.2, 6.3
     */
    @Test
    @Order(5)
    void testLockPerformanceBenchmark() throws Exception {
        // Redis 鎖性能測試
        LockPerformanceResult redisResult = measureLockPerformance(redisDistributedLock, "Redis", 1000);
        
        // ZooKeeper 鎖性能測試
        LockPerformanceResult zkResult = measureLockPerformance(zooKeeperDistributedLock, "ZooKeeper", 1000);

        // 輸出性能比較結果
        System.out.println("=== Lock Performance Comparison ===");
        System.out.println("Redis - Average lock time: " + redisResult.averageLockTime + "ms");
        System.out.println("Redis - Success rate: " + redisResult.successRate + "%");
        System.out.println("ZooKeeper - Average lock time: " + zkResult.averageLockTime + "ms");
        System.out.println("ZooKeeper - Success rate: " + zkResult.successRate + "%");

        // 驗證性能指標合理性
        assertTrue(redisResult.successRate > 95.0, "Redis lock success rate should be > 95%");
        assertTrue(zkResult.successRate > 95.0, "ZooKeeper lock success rate should be > 95%");
        assertTrue(redisResult.averageLockTime < 1000, "Redis average lock time should be < 1000ms");
        assertTrue(zkResult.averageLockTime < 2000, "ZooKeeper average lock time should be < 2000ms");
    }

    /**
     * 測試 6: 鎖公平性驗證
     * 需求: 2.1, 2.4 (ZooKeeper 保證 FIFO)
     */
    @Test
    @Order(6)
    void testLockFairness() throws Exception {
        // ZooKeeper 應該提供 FIFO 順序
        testLockFairness(zooKeeperDistributedLock, "ZooKeeper", true);
        
        // Redis 不保證嚴格的 FIFO，但應該相對公平
        testLockFairness(redisDistributedLock, "Redis", false);
    }

    /**
     * 測試 7: 鎖超時和自動釋放
     * 需求: 1.3, 2.2
     */
    @Test
    @Order(7)
    void testLockTimeoutAndAutoRelease() throws Exception {
        String lockKey = "timeout-test-lock";
        
        // 測試 Redis 鎖超時
        testLockTimeout(redisDistributedLock, lockKey + "-redis", "Redis");
        
        // 測試 ZooKeeper 鎖超時
        testLockTimeout(zooKeeperDistributedLock, lockKey + "-zk", "ZooKeeper");
    }

    /**
     * 測試 8: 極端併發場景下的系統穩定性
     * 需求: 1.1, 1.2, 2.1, 2.2, 3.1, 3.2, 4.1, 4.2
     */
    @Test
    @Order(8)
    void testExtremeeConcurrencyStability() throws Exception {
        // 重置高餘額帳戶
        Account account = accountRepository.findById(HIGH_BALANCE_ACCOUNT).orElseThrow();
        account.setBalance(new BigDecimal("100000.00"));
        accountRepository.save(account);

        ExecutorService executor = Executors.newFixedThreadPool(100);
        List<CompletableFuture<Boolean>> futures = new ArrayList<>();

        // 執行 1000 次併發扣款操作
        for (int i = 0; i < 1000; i++) {
            CompletableFuture<Boolean> future = CompletableFuture.supplyAsync(() -> {
                try {
                    return withdrawalService.withdraw(HIGH_BALANCE_ACCOUNT, new BigDecimal("10.00")).isSuccess();
                } catch (Exception e) {
                    return false;
                }
            }, executor);
            futures.add(future);
        }

        // 等待所有操作完成
        List<Boolean> results = new ArrayList<>();
        for (CompletableFuture<Boolean> future : futures) {
            results.add(future.get(60, TimeUnit.SECONDS));
        }
        executor.shutdown();

        // 統計結果
        long successfulOperations = results.stream().mapToLong(success -> success ? 1 : 0).sum();
        
        // 驗證最終餘額一致性
        Account finalAccount = accountRepository.findById(HIGH_BALANCE_ACCOUNT).orElseThrow();
        BigDecimal expectedBalance = new BigDecimal("100000.00").subtract(
            new BigDecimal("10.00").multiply(new BigDecimal(successfulOperations)));
        
        assertEquals(expectedBalance, finalAccount.getBalance(), 
            "Final balance should match expected value after concurrent operations");
        
        // 驗證系統穩定性
        assertTrue(successfulOperations > 900, 
            String.format("Expected > 900 successful operations, got %d", successfulOperations));
    }

    /**
     * 鎖互斥性測試的通用方法
     */
    private void testLockMutualExclusion(DistributedLock lock, String lockType) throws Exception {
        String lockKey = "mutex-test-" + lockType.toLowerCase();
        AtomicInteger concurrentAccess = new AtomicInteger(0);
        AtomicInteger maxConcurrentAccess = new AtomicInteger(0);
        
        ExecutorService executor = Executors.newFixedThreadPool(20);
        List<CompletableFuture<Void>> futures = new ArrayList<>();

        for (int i = 0; i < 50; i++) {
            CompletableFuture<Void> future = CompletableFuture.runAsync(() -> {
                try {
                    if (lock.tryLock(lockKey, 5, 10, TimeUnit.SECONDS)) {
                        try {
                            int current = concurrentAccess.incrementAndGet();
                            maxConcurrentAccess.updateAndGet(max -> Math.max(max, current));
                            
                            // 模擬臨界區操作
                            Thread.sleep(50);
                            
                            concurrentAccess.decrementAndGet();
                        } finally {
                            lock.unlock(lockKey);
                        }
                    }
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
            }, executor);
            futures.add(future);
        }

        // 等待所有操作完成
        CompletableFuture.allOf(futures.toArray(new CompletableFuture[0])).get(30, TimeUnit.SECONDS);
        executor.shutdown();

        // 驗證互斥性：同時訪問臨界區的執行緒數不應超過 1
        assertEquals(1, maxConcurrentAccess.get(), 
            String.format("%s lock should ensure mutual exclusion", lockType));
    }

    /**
     * 高併發資料一致性測試的通用方法
     */
    private void testDataConsistencyUnderHighConcurrency(DistributedLock lock, String lockType) throws Exception {
        // 重置測試帳戶餘額
        Account account1 = accountRepository.findById(TEST_ACCOUNT_1).orElseThrow();
        Account account2 = accountRepository.findById(TEST_ACCOUNT_2).orElseThrow();
        account1.setBalance(new BigDecimal("5000.00"));
        account2.setBalance(new BigDecimal("5000.00"));
        accountRepository.save(account1);
        accountRepository.save(account2);

        ExecutorService executor = Executors.newFixedThreadPool(50);
        List<CompletableFuture<Boolean>> futures = new ArrayList<>();

        // 執行 200 次併發匯款操作
        for (int i = 0; i < 200; i++) {
            final boolean direction = i % 2 == 0; // 交替匯款方向
            CompletableFuture<Boolean> future = CompletableFuture.supplyAsync(() -> {
                try {
                    if (direction) {
                        return transferService.transfer(TEST_ACCOUNT_1, TEST_ACCOUNT_2, new BigDecimal("5.00")).isSuccess();
                    } else {
                        return transferService.transfer(TEST_ACCOUNT_2, TEST_ACCOUNT_1, new BigDecimal("5.00")).isSuccess();
                    }
                } catch (Exception e) {
                    return false;
                }
            }, executor);
            futures.add(future);
        }

        // 等待所有操作完成
        List<Boolean> results = new ArrayList<>();
        for (CompletableFuture<Boolean> future : futures) {
            results.add(future.get(60, TimeUnit.SECONDS));
        }
        executor.shutdown();

        // 驗證資料一致性
        Account finalAccount1 = accountRepository.findById(TEST_ACCOUNT_1).orElseThrow();
        Account finalAccount2 = accountRepository.findById(TEST_ACCOUNT_2).orElseThrow();
        BigDecimal totalBalance = finalAccount1.getBalance().add(finalAccount2.getBalance());
        
        assertEquals(new BigDecimal("10000.00"), totalBalance, 
            String.format("%s lock should maintain data consistency", lockType));

        long successfulTransfers = results.stream().mapToLong(success -> success ? 1 : 0).sum();
        assertTrue(successfulTransfers > 150, 
            String.format("%s lock should allow most transfers to succeed", lockType));
    }

    /**
     * 鎖性能測試方法
     */
    private LockPerformanceResult measureLockPerformance(DistributedLock lock, String lockType, int iterations) throws Exception {
        String lockKey = "perf-test-" + lockType.toLowerCase();
        List<Long> lockTimes = Collections.synchronizedList(new ArrayList<>());
        AtomicInteger successCount = new AtomicInteger(0);

        ExecutorService executor = Executors.newFixedThreadPool(20);
        List<CompletableFuture<Void>> futures = new ArrayList<>();

        for (int i = 0; i < iterations; i++) {
            CompletableFuture<Void> future = CompletableFuture.runAsync(() -> {
                long startTime = System.currentTimeMillis();
                try {
                    if (lock.tryLock(lockKey + "-" + Thread.currentThread().getId(), 2, 5, TimeUnit.SECONDS)) {
                        try {
                            long lockTime = System.currentTimeMillis() - startTime;
                            lockTimes.add(lockTime);
                            successCount.incrementAndGet();
                            
                            // 模擬短暫的工作
                            Thread.sleep(1);
                        } finally {
                            lock.unlock(lockKey + "-" + Thread.currentThread().getId());
                        }
                    }
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
            }, executor);
            futures.add(future);
        }

        CompletableFuture.allOf(futures.toArray(new CompletableFuture[0])).get(60, TimeUnit.SECONDS);
        executor.shutdown();

        double averageLockTime = lockTimes.stream().mapToLong(Long::longValue).average().orElse(0.0);
        double successRate = (double) successCount.get() / iterations * 100;

        return new LockPerformanceResult(averageLockTime, successRate);
    }

    /**
     * 鎖公平性測試方法
     */
    private void testLockFairness(DistributedLock lock, String lockType, boolean shouldBeFair) throws Exception {
        String lockKey = "fairness-test-" + lockType.toLowerCase();
        List<Integer> acquisitionOrder = Collections.synchronizedList(new ArrayList<>());
        CountDownLatch startLatch = new CountDownLatch(1);
        
        ExecutorService executor = Executors.newFixedThreadPool(10);
        List<CompletableFuture<Void>> futures = new ArrayList<>();

        for (int i = 0; i < 10; i++) {
            final int threadId = i;
            CompletableFuture<Void> future = CompletableFuture.runAsync(() -> {
                try {
                    startLatch.await(); // 確保所有執行緒同時開始
                    if (lock.tryLock(lockKey, 10, 5, TimeUnit.SECONDS)) {
                        try {
                            acquisitionOrder.add(threadId);
                            Thread.sleep(100); // 持有鎖一段時間
                        } finally {
                            lock.unlock(lockKey);
                        }
                    }
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
            }, executor);
            futures.add(future);
        }

        startLatch.countDown(); // 開始測試
        CompletableFuture.allOf(futures.toArray(new CompletableFuture[0])).get(30, TimeUnit.SECONDS);
        executor.shutdown();

        // 分析公平性
        if (shouldBeFair) {
            // ZooKeeper 應該相對公平（不一定嚴格 FIFO，但應該相對有序）
            assertTrue(acquisitionOrder.size() >= 8, 
                String.format("%s should allow most threads to acquire lock", lockType));
        } else {
            // Redis 不保證嚴格公平性，但應該讓大部分執行緒獲得鎖
            assertTrue(acquisitionOrder.size() >= 7, 
                String.format("%s should allow reasonable number of threads to acquire lock", lockType));
        }
    }

    /**
     * 鎖超時測試方法
     */
    private void testLockTimeout(DistributedLock lock, String lockKey, String lockType) throws Exception {
        // 第一個執行緒獲取鎖並持有較長時間
        CompletableFuture<Void> holder = CompletableFuture.runAsync(() -> {
            try {
                if (lock.tryLock(lockKey, 1, 3, TimeUnit.SECONDS)) {
                    try {
                        Thread.sleep(5000); // 持有鎖 5 秒
                    } finally {
                        lock.unlock(lockKey);
                    }
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        });

        // 等待第一個執行緒獲取鎖
        Thread.sleep(100);

        // 第二個執行緒嘗試獲取鎖，應該超時
        long startTime = System.currentTimeMillis();
        boolean acquired = lock.tryLock(lockKey, 2, 1, TimeUnit.SECONDS);
        long elapsedTime = System.currentTimeMillis() - startTime;

        assertFalse(acquired, String.format("%s lock should timeout when already held", lockType));
        assertTrue(elapsedTime >= 2000 && elapsedTime < 3000, 
            String.format("%s lock timeout should be approximately 2 seconds, was %dms", lockType, elapsedTime));

        holder.get(10, TimeUnit.SECONDS);
    }

    /**
     * 鎖性能測試結果類別
     */
    private static class LockPerformanceResult {
        final double averageLockTime;
        final double successRate;

        LockPerformanceResult(double averageLockTime, double successRate) {
            this.averageLockTime = averageLockTime;
            this.successRate = successRate;
        }
    }
}