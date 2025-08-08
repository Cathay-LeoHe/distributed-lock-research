package com.example.distributedlock.controllers;

import com.example.distributedlock.dto.*;
import com.example.distributedlock.exception.AccountNotFoundException;
import com.example.distributedlock.exception.InsufficientFundsException;
import com.example.distributedlock.models.AccountStatus;
import com.example.distributedlock.models.TransactionStatus;
import com.example.distributedlock.services.BankingService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.time.LocalDateTime;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * BankingController 測試
 */
@WebMvcTest(BankingController.class)
class BankingControllerTest {
    
    @Autowired
    private MockMvc mockMvc;
    
    @MockBean
    private BankingService bankingService;
    
    @Autowired
    private ObjectMapper objectMapper;
    
    @Test
    void testTransferSuccess() throws Exception {
        // 準備測試資料
        TransferRequest request = new TransferRequest("ACC001", "ACC002", new BigDecimal("100.00"));
        TransactionResult result = TransactionResult.success("TXN001", new BigDecimal("100.00"), "ACC001", "ACC002");
        
        when(bankingService.transfer(anyString(), anyString(), any(BigDecimal.class)))
            .thenReturn(result);
        
        // 執行測試
        mockMvc.perform(post("/api/transfer")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.message").value("匯款成功"))
                .andExpect(jsonPath("$.data.transactionId").value("TXN001"))
                .andExpect(jsonPath("$.data.amount").value(100.00))
                .andExpect(jsonPath("$.data.fromAccount").value("ACC001"))
                .andExpect(jsonPath("$.data.toAccount").value("ACC002"));
    }
    
    @Test
    void testTransferFailure() throws Exception {
        // 準備測試資料
        TransferRequest request = new TransferRequest("ACC001", "ACC002", new BigDecimal("100.00"));
        TransactionResult result = TransactionResult.failure("餘額不足", TransactionStatus.FAILED);
        
        when(bankingService.transfer(anyString(), anyString(), any(BigDecimal.class)))
            .thenReturn(result);
        
        // 執行測試
        mockMvc.perform(post("/api/transfer")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.message").value("餘額不足"));
    }
    
    @Test
    void testTransferValidationError() throws Exception {
        // 準備無效的測試資料
        TransferRequest request = new TransferRequest("", "ACC002", new BigDecimal("-100.00"));
        
        // 執行測試
        mockMvc.perform(post("/api/transfer")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.message").value("請求參數驗證失敗"));
    }
    
    @Test
    void testWithdrawSuccess() throws Exception {
        // 準備測試資料
        WithdrawalRequest request = new WithdrawalRequest("ACC001", new BigDecimal("50.00"));
        TransactionResult result = TransactionResult.success("TXN002", new BigDecimal("50.00"), "ACC001", null);
        
        when(bankingService.withdraw(anyString(), any(BigDecimal.class)))
            .thenReturn(result);
        
        // 執行測試
        mockMvc.perform(post("/api/withdraw")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.message").value("扣款成功"))
                .andExpect(jsonPath("$.data.transactionId").value("TXN002"))
                .andExpect(jsonPath("$.data.amount").value(50.00));
    }
    
    @Test
    void testWithdrawFailure() throws Exception {
        // 準備測試資料
        WithdrawalRequest request = new WithdrawalRequest("ACC001", new BigDecimal("1000.00"));
        TransactionResult result = TransactionResult.failure("餘額不足", TransactionStatus.FAILED);
        
        when(bankingService.withdraw(anyString(), any(BigDecimal.class)))
            .thenReturn(result);
        
        // 執行測試
        mockMvc.perform(post("/api/withdraw")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.message").value("餘額不足"));
    }
    
    @Test
    void testGetBalanceSuccess() throws Exception {
        // 準備測試資料
        AccountBalance balance = new AccountBalance("ACC001", new BigDecimal("500.00"), 
            AccountStatus.ACTIVE, LocalDateTime.now());
        
        when(bankingService.getBalance(anyString()))
            .thenReturn(balance);
        
        // 執行測試
        mockMvc.perform(get("/api/accounts/ACC001/balance"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.message").value("查詢成功"))
                .andExpect(jsonPath("$.data.accountNumber").value("ACC001"))
                .andExpect(jsonPath("$.data.balance").value(500.00))
                .andExpect(jsonPath("$.data.status").value("ACTIVE"));
    }
    
    @Test
    void testGetBalanceNotFound() throws Exception {
        // 準備測試資料
        when(bankingService.getBalance(anyString()))
            .thenReturn(null);
        
        // 執行測試
        mockMvc.perform(get("/api/accounts/NONEXISTENT/balance"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.message").value("帳戶不存在"));
    }
    
    @Test
    void testTransferException() throws Exception {
        // 準備測試資料
        TransferRequest request = new TransferRequest("ACC001", "ACC002", new BigDecimal("100.00"));
        
        when(bankingService.transfer(anyString(), anyString(), any(BigDecimal.class)))
            .thenThrow(new RuntimeException("系統錯誤"));
        
        // 執行測試
        mockMvc.perform(post("/api/transfer")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.message").value("系統內部錯誤"));
    }
}