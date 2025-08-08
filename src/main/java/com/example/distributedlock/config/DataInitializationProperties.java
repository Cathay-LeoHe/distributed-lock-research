package com.example.distributedlock.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.PositiveOrZero;
import java.math.BigDecimal;
import java.util.List;
import java.util.ArrayList;

/**
 * 資料初始化配置屬性
 */
@ConfigurationProperties(prefix = "data")
@Validated
public class DataInitializationProperties {

    @Valid
    private InitializationProperties initialization = new InitializationProperties();

    public InitializationProperties getInitialization() {
        return initialization;
    }

    public void setInitialization(InitializationProperties initialization) {
        this.initialization = initialization;
    }

    /**
     * 初始化配置
     */
    public static class InitializationProperties {
        private boolean enabled = true;

        private boolean createSampleAccounts = true;

        @Valid
        private List<SampleAccount> sampleAccounts = new ArrayList<>();

        // Getters and Setters
        public boolean isEnabled() {
            return enabled;
        }

        public void setEnabled(boolean enabled) {
            this.enabled = enabled;
        }

        public boolean isCreateSampleAccounts() {
            return createSampleAccounts;
        }

        public void setCreateSampleAccounts(boolean createSampleAccounts) {
            this.createSampleAccounts = createSampleAccounts;
        }

        public List<SampleAccount> getSampleAccounts() {
            return sampleAccounts;
        }

        public void setSampleAccounts(List<SampleAccount> sampleAccounts) {
            this.sampleAccounts = sampleAccounts;
        }

        /**
         * 範例帳戶配置
         */
        public static class SampleAccount {
            @NotBlank(message = "Sample account number cannot be blank")
            private String accountNumber;

            @PositiveOrZero(message = "Sample account balance must be non-negative")
            private BigDecimal balance = BigDecimal.ZERO;

            // Getters and Setters
            public String getAccountNumber() {
                return accountNumber;
            }

            public void setAccountNumber(String accountNumber) {
                this.accountNumber = accountNumber;
            }

            public BigDecimal getBalance() {
                return balance;
            }

            public void setBalance(BigDecimal balance) {
                this.balance = balance;
            }
        }
    }
}