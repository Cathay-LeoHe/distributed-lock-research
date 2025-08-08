package com.example.distributedlock.health;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.redisson.api.RBucket;
import org.redisson.api.RedissonClient;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

/**
 * Redis 健康檢查組件測試類別
 */
@ExtendWith(MockitoExtension.class)
class RedisHealthIndicatorTest {

    @Mock
    private RedissonClient redissonClient;

    @Mock
    private RBucket<Object> rBucket;

    private RedisHealthIndicator redisHealthIndicator;

    @BeforeEach
    void setUp() {
        redisHealthIndicator = new RedisHealthIndicator(redissonClient);
        lenient().when(redissonClient.getBucket(anyString())).thenReturn(rBucket);
    }

    @Test
    void testCheckHealth_Success() {
        // Given
        when(redissonClient.isShutdown()).thenReturn(false);
        when(rBucket.get()).thenReturn("ping");

        // When
        RedisHealthIndicator.HealthStatus result = redisHealthIndicator.checkHealth();

        // Then
        assertTrue(result.isHealthy());
        assertEquals("Connected", result.getStatus());
        assertEquals("Read/Write operation successful", result.getMessage());
        
        verify(redissonClient).isShutdown();
        verify(redissonClient, times(3)).getBucket(anyString());
        verify(rBucket).set("ping");
        verify(rBucket).get();
        verify(rBucket).delete();
    }

    @Test
    void testCheckHealth_ClientShutdown() {
        // Given
        when(redissonClient.isShutdown()).thenReturn(true);

        // When
        RedisHealthIndicator.HealthStatus result = redisHealthIndicator.checkHealth();

        // Then
        assertFalse(result.isHealthy());
        assertEquals("Client is shutdown", result.getStatus());
        assertEquals("Redisson client has been shutdown", result.getMessage());
        
        verify(redissonClient).isShutdown();
        verify(redissonClient, never()).getBucket(anyString());
    }

    @Test
    void testCheckHealth_ReadWriteOperationFailed() {
        // Given
        when(redissonClient.isShutdown()).thenReturn(false);
        when(rBucket.get()).thenReturn("wrong_value");

        // When
        RedisHealthIndicator.HealthStatus result = redisHealthIndicator.checkHealth();

        // Then
        assertFalse(result.isHealthy());
        assertEquals("Connection issue", result.getStatus());
        assertEquals("Read/Write operation failed", result.getMessage());
        
        verify(redissonClient).isShutdown();
        verify(redissonClient, times(3)).getBucket(anyString());
        verify(rBucket).set("ping");
        verify(rBucket).get();
        verify(rBucket).delete();
    }

    @Test
    void testCheckHealth_Exception() {
        // Given
        when(redissonClient.isShutdown()).thenReturn(false);
        when(rBucket.get()).thenThrow(new RuntimeException("Connection error"));

        // When
        RedisHealthIndicator.HealthStatus result = redisHealthIndicator.checkHealth();

        // Then
        assertFalse(result.isHealthy());
        assertEquals("Connection failed", result.getStatus());
        assertEquals("Connection error", result.getMessage());
        
        verify(redissonClient).isShutdown();
        verify(redissonClient, times(2)).getBucket(anyString());
        verify(rBucket).set("ping");
        verify(rBucket).get();
    }

    @Test
    void testHealthStatus_ToString() {
        // Given
        RedisHealthIndicator.HealthStatus healthStatus = 
            new RedisHealthIndicator.HealthStatus(true, "Connected", "All good");

        // When
        String result = healthStatus.toString();

        // Then
        assertEquals("HealthStatus{healthy=true, status='Connected', message='All good'}", result);
    }
}