package com.example.distributedlock.exception;

import com.example.distributedlock.dto.ApiResponse;
import com.example.distributedlock.dto.ErrorResponse;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.ConstraintViolationException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

/**
 * 全域異常處理器
 * 統一處理應用程式中的各種異常
 */
@RestControllerAdvice
public class GlobalExceptionHandler {
    
    private static final Logger logger = LoggerFactory.getLogger(GlobalExceptionHandler.class);
    
    /**
     * 處理參數驗證異常
     */
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ApiResponse<ErrorResponse>> handleValidationException(
            MethodArgumentNotValidException ex, HttpServletRequest request) {
        
        logger.warn("參數驗證失敗: {}", ex.getMessage());
        
        List<String> details = new ArrayList<>();
        for (FieldError error : ex.getBindingResult().getFieldErrors()) {
            details.add(error.getField() + ": " + error.getDefaultMessage());
        }
        
        ErrorResponse errorResponse = new ErrorResponse(
            "VALIDATION_ERROR",
            "請求參數驗證失敗",
            HttpStatus.BAD_REQUEST.value(),
            request.getRequestURI(),
            details
        );
        
        return ResponseEntity.badRequest()
            .body(ApiResponse.failure("請求參數驗證失敗", errorResponse));
    }
    
    /**
     * 處理約束違反異常
     */
    @ExceptionHandler(ConstraintViolationException.class)
    public ResponseEntity<ApiResponse<ErrorResponse>> handleConstraintViolationException(
            ConstraintViolationException ex, HttpServletRequest request) {
        
        logger.warn("約束驗證失敗: {}", ex.getMessage());
        
        List<String> details = ex.getConstraintViolations().stream()
            .map(ConstraintViolation::getMessage)
            .collect(Collectors.toList());
        
        ErrorResponse errorResponse = new ErrorResponse(
            "CONSTRAINT_VIOLATION",
            "資料約束驗證失敗",
            HttpStatus.BAD_REQUEST.value(),
            request.getRequestURI(),
            details
        );
        
        return ResponseEntity.badRequest()
            .body(ApiResponse.failure("資料約束驗證失敗", errorResponse));
    }
    
    /**
     * 處理參數類型不匹配異常
     */
    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public ResponseEntity<ApiResponse<ErrorResponse>> handleTypeMismatchException(
            MethodArgumentTypeMismatchException ex, HttpServletRequest request) {
        
        logger.warn("參數類型不匹配: {}", ex.getMessage());
        
        String message = String.format("參數 '%s' 的值 '%s' 無法轉換為 %s 類型", 
            ex.getName(), ex.getValue(), ex.getRequiredType().getSimpleName());
        
        ErrorResponse errorResponse = new ErrorResponse(
            "TYPE_MISMATCH",
            message,
            HttpStatus.BAD_REQUEST.value(),
            request.getRequestURI()
        );
        
        return ResponseEntity.badRequest()
            .body(ApiResponse.failure("參數類型錯誤", errorResponse));
    }
    
    /**
     * 處理業務異常
     */
    @ExceptionHandler(BusinessException.class)
    public ResponseEntity<ApiResponse<ErrorResponse>> handleBusinessException(
            BusinessException ex, HttpServletRequest request) {
        
        logger.warn("業務異常: {}", ex.getMessage());
        
        ErrorResponse errorResponse = new ErrorResponse(
            ex.getErrorCode(),
            ex.getMessage(),
            HttpStatus.BAD_REQUEST.value(),
            request.getRequestURI()
        );
        
        return ResponseEntity.badRequest()
            .body(ApiResponse.failure(ex.getMessage(), errorResponse));
    }
    
    /**
     * 處理帳戶不存在異常
     */
    @ExceptionHandler(AccountNotFoundException.class)
    public ResponseEntity<ApiResponse<ErrorResponse>> handleAccountNotFoundException(
            AccountNotFoundException ex, HttpServletRequest request) {
        
        logger.warn("帳戶不存在: {}", ex.getMessage());
        
        ErrorResponse errorResponse = new ErrorResponse(
            "ACCOUNT_NOT_FOUND",
            ex.getMessage(),
            HttpStatus.NOT_FOUND.value(),
            request.getRequestURI()
        );
        
        return ResponseEntity.status(HttpStatus.NOT_FOUND)
            .body(ApiResponse.failure(ex.getMessage(), errorResponse));
    }
    
    /**
     * 處理餘額不足異常
     */
    @ExceptionHandler(InsufficientFundsException.class)
    public ResponseEntity<ApiResponse<ErrorResponse>> handleInsufficientFundsException(
            InsufficientFundsException ex, HttpServletRequest request) {
        
        logger.warn("餘額不足: {}", ex.getMessage());
        
        ErrorResponse errorResponse = new ErrorResponse(
            "INSUFFICIENT_FUNDS",
            ex.getMessage(),
            HttpStatus.BAD_REQUEST.value(),
            request.getRequestURI()
        );
        
        return ResponseEntity.badRequest()
            .body(ApiResponse.failure(ex.getMessage(), errorResponse));
    }
    
    /**
     * 處理分散式鎖異常
     */
    @ExceptionHandler(LockException.class)
    public ResponseEntity<ApiResponse<ErrorResponse>> handleLockException(
            LockException ex, HttpServletRequest request) {
        
        logger.error("分散式鎖異常: {}", ex.getMessage(), ex);
        
        ErrorResponse errorResponse = new ErrorResponse(
            "LOCK_ERROR",
            "系統繁忙，請稍後再試",
            HttpStatus.SERVICE_UNAVAILABLE.value(),
            request.getRequestURI()
        );
        
        return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
            .body(ApiResponse.failure("系統繁忙，請稍後再試", errorResponse));
    }
    
    /**
     * 處理一般異常
     */
    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiResponse<ErrorResponse>> handleGeneralException(
            Exception ex, HttpServletRequest request) {
        
        logger.error("系統異常: {}", ex.getMessage(), ex);
        
        ErrorResponse errorResponse = new ErrorResponse(
            "INTERNAL_SERVER_ERROR",
            "系統內部錯誤",
            HttpStatus.INTERNAL_SERVER_ERROR.value(),
            request.getRequestURI()
        );
        
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
            .body(ApiResponse.failure("系統內部錯誤", errorResponse));
    }
}