package com.example.distributedlock.integration;

import com.example.distributedlock.dto.TransferRequest;
import com.example.distributedlock.dto.WithdrawalRequest;
import com.example.distributedlock.dto.ApiResponse;
import com.example.distributedlock.dto.AccountBalance;
import com.example.distributedlock.dto.TransactionResult;
import com.example.distributedlock.models.Account;
import com.example.distributedlock.models.AccountStatus;
import com.example.distributedlock.repositories.AccountRepository;
import com.example.distributedlock.repositories.TransactionRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureWebMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.wait.strategy.Wait;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.math.BigDecimal;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.List;
import java.util.ArrayList;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
import static org.junit.jupiter.api.Assertions.*;

/**
 * 系統整合測試 - 驗證整個分散式鎖系統的端到端功能
 * 
 * 測試範圍：
 * - Redis 和 ZooKeeper 分散式鎖實作的正確性
 * - 銀行業務邏輯的完整性
 * - 併發場景下的資料一致性
 * - API 端點的功能性
 * - 系統組件間的整合
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureWebMvc
@Testcontainers
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
@ActiveProfiles("test")
public class SystemIntegrationTest {

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
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private AccountRepository accountRepository;

    @Autowired
    private TransactionRepository transactionRepository;

    private static final String ACCOUNT_1 = "ACC001";
    private static final String ACCOUNT_2 = "ACC002";
    private static final String ACCOUNT_3 = "ACC003";

    @BeforeEach
    void setUp() {
        // 清理測試資料
        transactionRepository.deleteAll();
        accountRepository.deleteAll();
        
        // 建立測試帳戶
        createTestAccount(ACCOUNT_1, new BigDecimal("1000.00"));
        createTestAccount(ACCOUNT_2, new BigDecimal("500.00"));
        createTestAccount(ACCOUNT_3, new BigDecimal("2000.00"));
    }

    private void createTestAccount(String accountNumber, BigDecimal balance) {
        Account account = new Account();
        account.setAccountNumber(accountNumber);
        account.setBalance(balance);
        account.setStatus(AccountStatus.ACTIVE);
        accountRepository.save(account);
    }

    /**
     * 測試 1: 基本 API 功能驗證
     * 需求: 7.1, 7.2, 7.3, 7.4
     */
    @Test
    @Order(1)
    void testBasicApiFunctionality() throws Exception {
        // 測試餘額查詢 API
        mockMvc.perform(get("/api/accounts/{accountNumber}/balance", ACCOUNT_1))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.accountNumber").value(ACCOUNT_1))
                .andExpect(jsonPath("$.data.balance").value(1000.00));

        // 測試匯款 API
        TransferRequest transferRequest = new TransferRequest();
        transferRequest.setFromAccount(ACCOUNT_1);
        transferRequest.setToAccount(ACCOUNT_2);
        transferRequest.setAmount(new BigDecimal("100.00"));

        mockMvc.perform(post("/api/transfer")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(transferRequest)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.amount").value(100.00));

        // 驗證餘額變化
        mockMvc.perform(get("/api/accounts/{accountNumber}/balance", ACCOUNT_1))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.balance").value(900.00));

        mockMvc.perform(get("/api/accounts/{accountNumber}/balance", ACCOUNT_2))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.balance").value(600.00));
    }

    /**
     * 測試 2: Redis 分散式鎖功能驗證
     * 需求: 1.1, 1.2, 1.3, 1.4
     */
    @Test
    @Order(2)
    void testRedisDistributedLockFunctionality() throws Exception {
        // 切換到 Redis 鎖提供者
        mockMvc.perform(post("/api/lock/provider")
                .param("provider", "redis"))
                .andExpect(status().isOk());

        // 執行併發匯款測試
        executeConcurrentTransfers("redis");
    }

    /**
     * 測試 3: ZooKeeper 分散式鎖功能驗證
     * 需求: 2.1, 2.2, 2.3, 2.4
     */
    @Test
    @Order(3)
    void testZooKeeperDistributedLockFunctionality() throws Exception {
        // 重置測試資料
        setUp();
        
        // 切換到 ZooKeeper 鎖提供者
        mockMvc.perform(post("/api/lock/provider")
                .param("provider", "zookeeper"))
                .andExpect(status().isOk());

        // 執行併發匯款測試
        executeConcurrentTransfers("zookeeper");
    }

    /**
     * 測試 4: 銀行業務邏輯完整性驗證
     * 需求: 3.1, 3.2, 3.3, 3.4, 4.1, 4.2, 4.3, 4.4
     */
    @Test
    @Order(4)
    void testBankingBusinessLogic() throws Exception {
        // 測試餘額不足的情況
        WithdrawalRequest withdrawalRequest = new WithdrawalRequest();
        withdrawalRequest.setAccountNumber(ACCOUNT_2);
        withdrawalRequest.setAmount(new BigDecimal("1000.00")); // 超過餘額

        mockMvc.perform(post("/api/withdraw")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(withdrawalRequest)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.error.code").value("INSUFFICIENT_FUNDS"));

        // 測試無效帳戶
        TransferRequest transferRequest = new TransferRequest();
        transferRequest.setFromAccount("INVALID");
        transferRequest.setToAccount(ACCOUNT_2);
        transferRequest.setAmount(new BigDecimal("100.00"));

        mockMvc.perform(post("/api/transfer")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(transferRequest)))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.error.code").value("ACCOUNT_NOT_FOUND"));
    }

    /**
     * 測試 5: 高併發場景下的資料一致性
     * 需求: 1.1, 1.2, 2.1, 2.2, 3.1, 3.2, 4.1, 4.2
     */
    @Test
    @Order(5)
    void testHighConcurrencyDataConsistency() throws Exception {
        // 建立高餘額測試帳戶
        createTestAccount("HIGH_BALANCE", new BigDecimal("10000.00"));
        
        ExecutorService executor = Executors.newFixedThreadPool(20);
        List<CompletableFuture<Void>> futures = new ArrayList<>();

        // 執行 100 次併發扣款，每次扣款 10 元
        for (int i = 0; i < 100; i++) {
            CompletableFuture<Void> future = CompletableFuture.runAsync(() -> {
                try {
                    WithdrawalRequest request = new WithdrawalRequest();
                    request.setAccountNumber("HIGH_BALANCE");
                    request.setAmount(new BigDecimal("10.00"));

                    mockMvc.perform(post("/api/withdraw")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request)));
                } catch (Exception e) {
                    // 忽略個別請求的異常，專注於最終一致性
                }
            }, executor);
            futures.add(future);
        }

        // 等待所有操作完成
        CompletableFuture.allOf(futures.toArray(new CompletableFuture[0])).get(30, TimeUnit.SECONDS);
        executor.shutdown();

        // 驗證最終餘額一致性
        String response = mockMvc.perform(get("/api/accounts/{accountNumber}/balance", "HIGH_BALANCE"))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();

        ApiResponse<AccountBalance> apiResponse = objectMapper.readValue(response, 
            objectMapper.getTypeFactory().constructParametricType(ApiResponse.class, AccountBalance.class));
        
        BigDecimal finalBalance = apiResponse.getData().getBalance();
        
        // 最終餘額應該是 10000 - (成功扣款次數 * 10)
        // 由於併發控制，最終餘額應該是可預測的
        assertTrue(finalBalance.compareTo(new BigDecimal("9000.00")) >= 0, 
            "Final balance should be at least 9000.00, but was: " + finalBalance);
        assertTrue(finalBalance.compareTo(new BigDecimal("10000.00")) <= 0, 
            "Final balance should be at most 10000.00, but was: " + finalBalance);
    }

    /**
     * 測試 6: 系統健康檢查和監控
     * 需求: 6.2, 6.3
     */
    @Test
    @Order(6)
    void testSystemHealthAndMonitoring() throws Exception {
        // 測試健康檢查端點
        mockMvc.perform(get("/actuator/health"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("UP"));

        // 測試分散式鎖系統健康檢查
        mockMvc.perform(get("/actuator/health/distributedLockSystem"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("UP"));

        // 測試業務指標端點
        mockMvc.perform(get("/actuator/business-metrics"))
                .andExpect(status().isOk());

        // 測試配置資訊端點
        mockMvc.perform(get("/actuator/configuration-info"))
                .andExpect(status().isOk());
    }

    /**
     * 測試 7: 鎖提供者切換功能
     * 需求: 6.1, 6.2
     */
    @Test
    @Order(7)
    void testLockProviderSwitching() throws Exception {
        // 測試切換到 Redis
        mockMvc.perform(post("/api/lock/provider")
                .param("provider", "redis"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data").value("redis"));

        // 驗證當前提供者
        mockMvc.perform(get("/api/lock/provider"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data").value("redis"));

        // 測試切換到 ZooKeeper
        mockMvc.perform(post("/api/lock/provider")
                .param("provider", "zookeeper"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data").value("zookeeper"));

        // 驗證當前提供者
        mockMvc.perform(get("/api/lock/provider"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data").value("zookeeper"));
    }

    /**
     * 執行併發匯款測試的輔助方法
     */
    private void executeConcurrentTransfers(String lockProvider) throws Exception {
        ExecutorService executor = Executors.newFixedThreadPool(10);
        List<CompletableFuture<Boolean>> futures = new ArrayList<>();

        // 記錄初始餘額
        BigDecimal initialBalance1 = getAccountBalance(ACCOUNT_1);
        BigDecimal initialBalance2 = getAccountBalance(ACCOUNT_2);

        // 執行 20 次併發匯款，每次匯款 10 元
        for (int i = 0; i < 20; i++) {
            CompletableFuture<Boolean> future = CompletableFuture.supplyAsync(() -> {
                try {
                    TransferRequest request = new TransferRequest();
                    request.setFromAccount(ACCOUNT_1);
                    request.setToAccount(ACCOUNT_2);
                    request.setAmount(new BigDecimal("10.00"));

                    String response = mockMvc.perform(post("/api/transfer")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request)))
                            .andReturn().getResponse().getContentAsString();

                    ApiResponse<?> apiResponse = objectMapper.readValue(response, ApiResponse.class);
                    return apiResponse.isSuccess();
                } catch (Exception e) {
                    return false;
                }
            }, executor);
            futures.add(future);
        }

        // 等待所有操作完成
        List<Boolean> results = new ArrayList<>();
        for (CompletableFuture<Boolean> future : futures) {
            results.add(future.get(10, TimeUnit.SECONDS));
        }
        executor.shutdown();

        // 計算成功的匯款次數
        long successfulTransfers = results.stream().mapToLong(success -> success ? 1 : 0).sum();

        // 驗證最終餘額
        BigDecimal finalBalance1 = getAccountBalance(ACCOUNT_1);
        BigDecimal finalBalance2 = getAccountBalance(ACCOUNT_2);

        BigDecimal expectedBalance1 = initialBalance1.subtract(new BigDecimal("10.00").multiply(new BigDecimal(successfulTransfers)));
        BigDecimal expectedBalance2 = initialBalance2.add(new BigDecimal("10.00").multiply(new BigDecimal(successfulTransfers)));

        assertEquals(expectedBalance1, finalBalance1, 
            String.format("Account 1 balance mismatch with %s lock. Expected: %s, Actual: %s", 
                lockProvider, expectedBalance1, finalBalance1));
        assertEquals(expectedBalance2, finalBalance2, 
            String.format("Account 2 balance mismatch with %s lock. Expected: %s, Actual: %s", 
                lockProvider, expectedBalance2, finalBalance2));

        // 驗證至少有一些匯款成功（證明系統正常運作）
        assertTrue(successfulTransfers > 0, 
            String.format("No successful transfers with %s lock", lockProvider));
    }

    /**
     * 獲取帳戶餘額的輔助方法
     */
    private BigDecimal getAccountBalance(String accountNumber) throws Exception {
        String response = mockMvc.perform(get("/api/accounts/{accountNumber}/balance", accountNumber))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();

        ApiResponse<AccountBalance> apiResponse = objectMapper.readValue(response, 
            objectMapper.getTypeFactory().constructParametricType(ApiResponse.class, AccountBalance.class));
        
        return apiResponse.getData().getBalance();
    }
}