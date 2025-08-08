package com.example.distributedlock.exception;

import com.example.distributedlock.controllers.BankingController;
import com.example.distributedlock.dto.TransferRequest;
import com.example.distributedlock.dto.WithdrawalRequest;
import com.example.distributedlock.services.BankingService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * GlobalExceptionHandler 測試
 */
@WebMvcTest(BankingController.class)
class GlobalExceptionHandlerTest {
    
    @Autowired
    private MockMvc mockMvc;
    
    @MockBean
    private BankingService bankingService;
    
    @Autowired
    private ObjectMapper objectMapper;
    
    @Test
    void testValidationException() throws Exception {
        // 準備無效的請求資料
        TransferRequest request = new TransferRequest("", "ACC002", new BigDecimal("-100.00"));
        
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
    void testAccountNotFoundException() throws Exception {
        // 準備測試資料
        when(bankingService.getBalance(anyString()))
            .thenThrow(new AccountNotFoundException("ACC999"));
        
        // 執行測試
        mockMvc.perform(get("/api/accounts/ACC999/balance"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.data.error").value("ACCOUNT_NOT_FOUND"))
                .andExpect(jsonPath("$.data.status").value(404));
    }
    
    @Test
    void testInsufficientFundsException() throws Exception {
        // 準備測試資料
        WithdrawalRequest request = new WithdrawalRequest("ACC001", new BigDecimal("1000.00"));
        
        when(bankingService.withdraw(anyString(), any(BigDecimal.class)))
            .thenThrow(new InsufficientFundsException("ACC001", new BigDecimal("1000.00"), new BigDecimal("500.00")));
        
        // 執行測試
        mockMvc.perform(post("/api/withdraw")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.data.error").value("INSUFFICIENT_FUNDS"))
                .andExpect(jsonPath("$.data.status").value(400));
    }
    
    @Test
    void testLockException() throws Exception {
        // 準備測試資料
        TransferRequest request = new TransferRequest("ACC001", "ACC002", new BigDecimal("100.00"));
        
        when(bankingService.transfer(anyString(), anyString(), any(BigDecimal.class)))
            .thenThrow(new LockException("無法獲取分散式鎖"));
        
        // 執行測試
        mockMvc.perform(post("/api/transfer")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.message").value("系統繁忙，請稍後再試"))
                .andExpect(jsonPath("$.data.error").value("LOCK_ERROR"))
                .andExpect(jsonPath("$.data.status").value(503));
    }
    
    @Test
    void testGeneralException() throws Exception {
        // 準備測試資料
        TransferRequest request = new TransferRequest("ACC001", "ACC002", new BigDecimal("100.00"));
        
        when(bankingService.transfer(anyString(), anyString(), any(BigDecimal.class)))
            .thenThrow(new RuntimeException("未預期的系統錯誤"));
        
        // 執行測試
        mockMvc.perform(post("/api/transfer")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.message").value("系統內部錯誤"));
    }
    
    @Test
    void testTypeMismatchException() throws Exception {
        // 執行測試 - 傳送無效的路徑參數類型
        mockMvc.perform(get("/api/accounts/{accountNumber}/balance", ""))
                .andExpect(status().isNotFound()); // Spring 會返回 404 而不是類型錯誤
    }
}