package com.example.distributedlock.config;

import com.example.distributedlock.models.Account;
import com.example.distributedlock.models.AccountStatus;
import com.example.distributedlock.models.Transaction;
import com.example.distributedlock.models.TransactionStatus;
import com.example.distributedlock.models.TransactionType;
import com.example.distributedlock.repositories.AccountRepository;
import com.example.distributedlock.repositories.TransactionRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

/**
 * 資料初始化組件
 * 程式化初始化示範資料，支持不同環境的資料初始化策略
 */
@Component
@Order(1000) // 確保在其他初始化組件之後執行
public class DataInitializer implements CommandLineRunner {

    private static final Logger logger = LoggerFactory.getLogger(DataInitializer.class);

    private final DataInitializationProperties dataProperties;
    private final AccountRepository accountRepository;
    private final TransactionRepository transactionRepository;

    @Autowired
    public DataInitializer(DataInitializationProperties dataProperties,
                          AccountRepository accountRepository,
                          TransactionRepository transactionRepository) {
        this.dataProperties = dataProperties;
        this.accountRepository = accountRepository;
        this.transactionRepository = transactionRepository;
    }

    @Override
    public void run(String... args) throws Exception {
        if (!dataProperties.getInitialization().isEnabled()) {
            logger.info("Data initialization is disabled");
            return;
        }

        logger.info("Starting data initialization...");

        try {
            initializeData();
            logger.info("Data initialization completed successfully");
        } catch (Exception e) {
            logger.error("Data initialization failed", e);
            throw e;
        }
    }

    /**
     * 初始化資料的主要方法
     */
    private void initializeData() {
        // 檢查是否已有資料
        long accountCount = accountRepository.count();
        if (accountCount > 0) {
            logger.info("Database already contains {} accounts, skipping initialization", accountCount);
            return;
        }

        // 創建示範帳戶
        if (dataProperties.getInitialization().isCreateSampleAccounts()) {
            createSampleAccounts();
        }

        // 創建示範交易記錄
        createSampleTransactions();

        logger.info("Created {} accounts and {} transactions", 
                   accountRepository.count(), 
                   transactionRepository.count());
    }

    /**
     * 創建示範帳戶
     */
    private void createSampleAccounts() {
        logger.info("Creating sample accounts...");

        List<DataInitializationProperties.InitializationProperties.SampleAccount> sampleAccounts = 
            dataProperties.getInitialization().getSampleAccounts();

        if (sampleAccounts.isEmpty()) {
            // 如果配置中沒有指定示範帳戶，使用預設帳戶
            createDefaultSampleAccounts();
        } else {
            // 使用配置中指定的示範帳戶
            createConfiguredSampleAccounts(sampleAccounts);
        }
    }

    /**
     * 創建預設示範帳戶
     */
    private void createDefaultSampleAccounts() {
        Account[] defaultAccounts = {
            createAccount("ACC001", new BigDecimal("10000.00"), AccountStatus.ACTIVE),
            createAccount("ACC002", new BigDecimal("20000.00"), AccountStatus.ACTIVE),
            createAccount("ACC003", new BigDecimal("15000.00"), AccountStatus.ACTIVE),
            createAccount("ACC004", new BigDecimal("5000.00"), AccountStatus.ACTIVE),
            createAccount("ACC005", new BigDecimal("8000.00"), AccountStatus.ACTIVE),
            createAccount("ACC006", new BigDecimal("25000.00"), AccountStatus.ACTIVE),
            createAccount("ACC007", new BigDecimal("12000.00"), AccountStatus.ACTIVE),
            createAccount("ACC008", new BigDecimal("3000.00"), AccountStatus.INACTIVE),
            createAccount("ACC009", new BigDecimal("18000.00"), AccountStatus.ACTIVE),
            createAccount("ACC010", new BigDecimal("7500.00"), AccountStatus.FROZEN)
        };

        for (Account account : defaultAccounts) {
            accountRepository.save(account);
            logger.debug("Created account: {} with balance: {}", 
                        account.getAccountNumber(), account.getBalance());
        }

        logger.info("Created {} default sample accounts", defaultAccounts.length);
    }

    /**
     * 創建配置中指定的示範帳戶
     */
    private void createConfiguredSampleAccounts(
            List<DataInitializationProperties.InitializationProperties.SampleAccount> sampleAccounts) {
        
        for (DataInitializationProperties.InitializationProperties.SampleAccount sampleAccount : sampleAccounts) {
            Account account = createAccount(
                sampleAccount.getAccountNumber(), 
                sampleAccount.getBalance(), 
                AccountStatus.ACTIVE
            );
            
            accountRepository.save(account);
            logger.debug("Created configured account: {} with balance: {}", 
                        account.getAccountNumber(), account.getBalance());
        }

        logger.info("Created {} configured sample accounts", sampleAccounts.size());
    }

    /**
     * 創建帳戶實體的輔助方法
     */
    private Account createAccount(String accountNumber, BigDecimal balance, AccountStatus status) {
        Account account = new Account();
        account.setAccountNumber(accountNumber);
        account.setBalance(balance);
        account.setStatus(status);
        return account;
    }

    /**
     * 創建示範交易記錄
     */
    private void createSampleTransactions() {
        logger.info("Creating sample transactions...");

        // 只有在有帳戶的情況下才創建交易記錄
        if (accountRepository.count() == 0) {
            logger.warn("No accounts found, skipping transaction creation");
            return;
        }

        Transaction[] sampleTransactions = {
            createTransaction("ACC001", "ACC002", new BigDecimal("500.00"), 
                            TransactionType.TRANSFER, TransactionStatus.COMPLETED, 
                            "redis", "示範轉帳交易", LocalDateTime.now().minusDays(1)),
            
            createTransaction("ACC003", null, new BigDecimal("200.00"), 
                            TransactionType.WITHDRAWAL, TransactionStatus.COMPLETED, 
                            "zookeeper", "示範扣款交易", LocalDateTime.now().minusDays(2)),
            
            createTransaction("ACC002", "ACC004", new BigDecimal("1000.00"), 
                            TransactionType.TRANSFER, TransactionStatus.COMPLETED, 
                            "redis", "示範轉帳交易", LocalDateTime.now().minusDays(3)),
            
            createTransaction("ACC005", null, new BigDecimal("300.00"), 
                            TransactionType.WITHDRAWAL, TransactionStatus.COMPLETED, 
                            "redis", "示範扣款交易", LocalDateTime.now().minusDays(4)),
            
            createTransaction("ACC006", "ACC007", new BigDecimal("2000.00"), 
                            TransactionType.TRANSFER, TransactionStatus.COMPLETED, 
                            "zookeeper", "示範轉帳交易", LocalDateTime.now().minusDays(5)),
            
            createTransaction("ACC001", null, new BigDecimal("100.00"), 
                            TransactionType.WITHDRAWAL, TransactionStatus.FAILED, 
                            "redis", "示範失敗交易; 失敗原因: 測試失敗場景", LocalDateTime.now().minusDays(6)),
            
            createTransaction("ACC009", "ACC003", new BigDecimal("800.00"), 
                            TransactionType.TRANSFER, TransactionStatus.COMPLETED, 
                            "zookeeper", "示範轉帳交易", LocalDateTime.now().minusDays(7)),
            
            createTransaction("ACC004", null, new BigDecimal("150.00"), 
                            TransactionType.WITHDRAWAL, TransactionStatus.COMPLETED, 
                            "redis", "示範扣款交易", LocalDateTime.now().minusDays(8))
        };

        for (Transaction transaction : sampleTransactions) {
            transactionRepository.save(transaction);
            logger.debug("Created transaction: {} from {} to {} amount: {}", 
                        transaction.getTransactionId(), 
                        transaction.getFromAccount(), 
                        transaction.getToAccount(), 
                        transaction.getAmount());
        }

        logger.info("Created {} sample transactions", sampleTransactions.length);
    }

    /**
     * 創建交易實體的輔助方法
     */
    private Transaction createTransaction(String fromAccount, String toAccount, BigDecimal amount,
                                       TransactionType type, TransactionStatus status,
                                       String lockProvider, String description, LocalDateTime createdAt) {
        Transaction transaction = new Transaction();
        transaction.setFromAccount(fromAccount);
        transaction.setToAccount(toAccount);
        transaction.setAmount(amount);
        transaction.setType(type);
        transaction.setStatus(status);
        transaction.setLockProvider(lockProvider);
        transaction.setDescription(description);
        transaction.setCreatedAt(createdAt);
        return transaction;
    }

    /**
     * 環境特定的資料初始化器
     */
    @Component
    @Profile("local")
    @Order(1001)
    public static class LocalDataInitializer implements CommandLineRunner {
        
        private static final Logger logger = LoggerFactory.getLogger(LocalDataInitializer.class);
        private final AccountRepository accountRepository;
        private final TransactionRepository transactionRepository;
        
        @Autowired
        public LocalDataInitializer(AccountRepository accountRepository, 
                                   TransactionRepository transactionRepository) {
            this.accountRepository = accountRepository;
            this.transactionRepository = transactionRepository;
        }
        
        @Override
        public void run(String... args) throws Exception {
            logger.info("Local environment specific data initialization");
            
            // 本地環境特定的初始化邏輯
            createLocalTestAccounts();
            
            logger.info("Local environment initialization completed. Total accounts: {}, Total transactions: {}", 
                       accountRepository.count(), transactionRepository.count());
        }
        
        private void createLocalTestAccounts() {
            // 創建一些本地測試專用的帳戶
            if (!accountRepository.existsById("TEST001")) {
                Account testAccount1 = createAccount("TEST001", new BigDecimal("100.00"), AccountStatus.ACTIVE);
                Account testAccount2 = createAccount("TEST002", new BigDecimal("0.01"), AccountStatus.ACTIVE);
                Account testAccount3 = createAccount("TEST003", new BigDecimal("999999.99"), AccountStatus.ACTIVE);
                
                accountRepository.save(testAccount1);
                accountRepository.save(testAccount2);
                accountRepository.save(testAccount3);
                
                logger.debug("Created local test accounts: TEST001, TEST002, TEST003");
            }
        }
        
        private Account createAccount(String accountNumber, BigDecimal balance, AccountStatus status) {
            Account account = new Account();
            account.setAccountNumber(accountNumber);
            account.setBalance(balance);
            account.setStatus(status);
            return account;
        }
    }

    /**
     * Docker 環境特定的資料初始化器
     */
    @Component
    @Profile("docker")
    @Order(1001)
    public static class DockerDataInitializer implements CommandLineRunner {
        
        private static final Logger logger = LoggerFactory.getLogger(DockerDataInitializer.class);
        private final AccountRepository accountRepository;
        private final TransactionRepository transactionRepository;
        
        @Autowired
        public DockerDataInitializer(AccountRepository accountRepository, 
                                    TransactionRepository transactionRepository) {
            this.accountRepository = accountRepository;
            this.transactionRepository = transactionRepository;
        }
        
        @Override
        public void run(String... args) throws Exception {
            logger.info("Docker environment specific data initialization");
            
            // Docker 環境特定的初始化邏輯
            createDockerLoadTestAccounts();
            
            logger.info("Docker environment initialization completed. Total accounts: {}, Total transactions: {}", 
                       accountRepository.count(), transactionRepository.count());
        }
        
        private void createDockerLoadTestAccounts() {
            // 創建一些用於負載測試的帳戶
            String[] loadTestAccounts = {"LOAD001", "LOAD002", "LOAD003", "LOAD004", "LOAD005"};
            
            for (String accountNumber : loadTestAccounts) {
                if (!accountRepository.existsById(accountNumber)) {
                    Account loadTestAccount = createAccount(accountNumber, new BigDecimal("100000.00"), AccountStatus.ACTIVE);
                    accountRepository.save(loadTestAccount);
                    logger.debug("Created Docker load test account: {}", accountNumber);
                }
            }
        }
        
        private Account createAccount(String accountNumber, BigDecimal balance, AccountStatus status) {
            Account account = new Account();
            account.setAccountNumber(accountNumber);
            account.setBalance(balance);
            account.setStatus(status);
            return account;
        }
    }

    /**
     * 生產環境特定的資料初始化器
     */
    @Component
    @Profile("prod")
    @Order(1001)
    public static class ProductionDataInitializer implements CommandLineRunner {
        
        private static final Logger logger = LoggerFactory.getLogger(ProductionDataInitializer.class);
        private final DataInitializationProperties dataProperties;
        
        @Autowired
        public ProductionDataInitializer(DataInitializationProperties dataProperties) {
            this.dataProperties = dataProperties;
        }
        
        @Override
        public void run(String... args) throws Exception {
            logger.info("Production environment specific data initialization");
            
            // 生產環境特定的初始化邏輯
            // 注意：生產環境通常不應該自動初始化示範資料
            if (dataProperties.getInitialization().isEnabled()) {
                logger.warn("Production environment detected but data initialization is enabled - this may not be intended");
            } else {
                logger.info("Production environment detected - data initialization is properly disabled");
            }
            
            // 可以在這裡添加生產環境必要的初始化邏輯，例如：
            // - 檢查必要的系統帳戶是否存在
            // - 驗證資料庫結構
            // - 初始化系統配置等
            
            logger.info("Production environment initialization completed");
        }
    }
}