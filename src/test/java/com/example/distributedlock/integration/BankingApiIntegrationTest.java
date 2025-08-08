package com.example.distributedlock.integration;

import com.example.distributedlock.dto.TransferRequest;
import com.example.distributedlock.dto.WithdrawalRequest;
import com.example.distributedlock.models.Account;
import com.example.distributedlock.models.AccountStatus;
import com.example.distributedlock.repositories.AccountRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureWebMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.context.WebApplicationContext;

import java.math.BigDecimal;
import java.time.LocalDateTime;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * 銀行 API 整合測試
 */
@SpringBootTest
@AutoConfigureWebMvc
@ActiveProfiles("test")
@Transactional
class BankingApiIntegrationTest {
    
    @Autowired
    private WebApplicationContext webApplicationContext;
    
    @Autowired
    private AccountRepository accountRepository;
    
    @Autowired
    private ObjectMapper objectMapper;
    
    private MockMvc mockMvc;
    
    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.webAppContextSetup(webApplicationContext).build();
        
        // 創建測試帳戶
        Account account1 = new Account();
        account1.setAccountNumber("TEST001");
        account1.setBalance(new BigDecimal("1000.00"));
        account1.setStatus(AccountStatus.ACTIVE);
        account1.setCreatedAt(LocalDateTime.now());
        account1.setUpdatedAt(LocalDateTime.now());
        account1.setVersion(1L);
        accountRepository.save(account1);
        
        Account account2 = new Account();
        account2.setAccountNumber("TEST002");
        account2.setBalance(new BigDecimal("500.00"));
        account2.setStatus(AccountStatus.ACTIVE);
        account2.setCreatedAt(LocalDateTime.now());
        account2.setUpdatedAt(LocalDateTime.now());
        account2.setVersion(1L);
        accountRepository.save(account2);
    }
    
    @Test
    void testGetBalanceSuccess() throws Exception {
        // 執行測試
        mockMvc.perform(get("/api/accounts/TEST001/balance"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.message").value("查詢成功"))
                .andExpect(jsonPath("$.data.accountNumber").value("TEST001"))
                .andExpect(jsonPath("$.data.balance").value(1000.00))
                .andExpect(jsonPath("$.data.status").value("ACTIVE"));
    }
    
    @Test
    void testGetBalanceAccountNotFound() throws Exception {
        // 執行測試
        mockMvc.perform(get("/api/accounts/NONEXISTENT/balance"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.message").value("帳戶不存在"));
    }
    
    @Test
    void testTransferValidationError() throws Exception {
        // 準備無效的請求資料
        TransferRequest request = new TransferRequest("", "TEST002", new BigDecimal("-100.00"));
        
        // 執行測試
        mockMvc.perform(post("/api/transfer")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.message").value("請求參數驗證失敗"))
                .andExpect(jsonPath("$.data.error").value("VALIDATION_ERROR"))
                .andExpect(jsonPath("$.data.status").value(400))
                .andExpect(jsonPath("$.data.details").isArray());
    }
    
    @Test
    void testWithdrawValidationError() throws Exception {
        // 準備無效的請求資料
        WithdrawalRequest request = new WithdrawalRequest("", BigDecimal.ZERO);
        
        // 執行測試
        mockMvc.perform(post("/api/withdraw")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.message").value("請求參數驗證失敗"))
                .andExpect(jsonPath("$.data.error").value("VALIDATION_ERROR"))
                .andExpect(jsonPath("$.data.status").value(400));
    }
    
    @Test
    void testApiResponseFormat() throws Exception {
        // 測試成功回應格式
        mockMvc.perform(get("/api/accounts/TEST001/balance"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").exists())
                .andExpect(jsonPath("$.message").exists())
                .andExpect(jsonPath("$.data").exists())
                .andExpect(jsonPath("$.timestamp").exists());
        
        // 測試錯誤回應格式
        mockMvc.perform(get("/api/accounts/NONEXISTENT/balance"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.message").exists())
                .andExpect(jsonPath("$.timestamp").exists());
    }
}