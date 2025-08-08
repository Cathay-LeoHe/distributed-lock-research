package com.example.distributedlock.controllers;

import com.example.distributedlock.config.LockConfiguration;
import com.example.distributedlock.factory.DistributedLockFactory;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * 鎖管理控制器測試類別
 */
@WebMvcTest(LockManagementController.class)
class LockManagementControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private LockConfiguration.LockManager lockManager;

    @Autowired
    private ObjectMapper objectMapper;

    @Test
    void testGetLockStatus() throws Exception {
        // Given
        LockConfiguration.LockProviderStatus status = new LockConfiguration.LockProviderStatus(
                "redis", 5, true, false);
        DistributedLockFactory.LockStatistics stats = new DistributedLockFactory.LockStatistics("redis", 5);
        
        when(lockManager.getProviderStatus()).thenReturn(status);
        when(lockManager.getLockStatistics()).thenReturn(stats);
        when(lockManager.getSupportedProviders()).thenReturn(new String[]{"redis", "zookeeper"});

        // When & Then
        mockMvc.perform(get("/api/lock-management/status"))
                .andExpect(status().isOk())
                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.currentProvider").value("redis"))
                .andExpect(jsonPath("$.activeLocks").value(5))
                .andExpect(jsonPath("$.redisAvailable").value(true))
                .andExpect(jsonPath("$.zookeeperAvailable").value(false));

        verify(lockManager).getProviderStatus();
        verify(lockManager).getLockStatistics();
        verify(lockManager).getSupportedProviders();
    }

    @Test
    void testGetLockStatusException() throws Exception {
        // Given
        when(lockManager.getProviderStatus()).thenThrow(new RuntimeException("Test exception"));

        // When & Then
        mockMvc.perform(get("/api/lock-management/status"))
                .andExpect(status().isInternalServerError())
                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.error").exists());
    }

    @Test
    void testSwitchLockProvider() throws Exception {
        // Given
        LockManagementController.SwitchProviderRequest request = 
                new LockManagementController.SwitchProviderRequest("zookeeper");
        
        when(lockManager.getCurrentProvider()).thenReturn("redis");
        when(lockManager.switchProvider("zookeeper")).thenReturn(true);

        // When & Then
        mockMvc.perform(post("/api/lock-management/switch")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.message").exists())
                .andExpect(jsonPath("$.previousProvider").value("redis"))
                .andExpect(jsonPath("$.currentProvider").value("zookeeper"));

        verify(lockManager).getCurrentProvider();
        verify(lockManager).switchProvider("zookeeper");
    }

    @Test
    void testSwitchLockProviderSameProvider() throws Exception {
        // Given
        LockManagementController.SwitchProviderRequest request = 
                new LockManagementController.SwitchProviderRequest("redis");
        
        when(lockManager.getCurrentProvider()).thenReturn("redis");

        // When & Then
        mockMvc.perform(post("/api/lock-management/switch")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.message").value("鎖提供者未改變"))
                .andExpect(jsonPath("$.currentProvider").value("redis"));

        verify(lockManager).getCurrentProvider();
        verify(lockManager, never()).switchProvider(anyString());
    }

    @Test
    void testSwitchLockProviderEmptyProvider() throws Exception {
        // Given
        LockManagementController.SwitchProviderRequest request = 
                new LockManagementController.SwitchProviderRequest("");

        // When & Then
        mockMvc.perform(post("/api/lock-management/switch")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.error").value("鎖提供者不能為空"));

        verify(lockManager, never()).switchProvider(anyString());
    }

    @Test
    void testSwitchLockProviderFailed() throws Exception {
        // Given
        LockManagementController.SwitchProviderRequest request = 
                new LockManagementController.SwitchProviderRequest("zookeeper");
        
        when(lockManager.getCurrentProvider()).thenReturn("redis");
        when(lockManager.switchProvider("zookeeper")).thenReturn(false);

        // When & Then
        mockMvc.perform(post("/api/lock-management/switch")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isInternalServerError())
                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.error").exists());

        verify(lockManager).switchProvider("zookeeper");
    }

    @Test
    void testSwitchLockProviderException() throws Exception {
        // Given
        LockManagementController.SwitchProviderRequest request = 
                new LockManagementController.SwitchProviderRequest("zookeeper");
        
        when(lockManager.getCurrentProvider()).thenReturn("redis");
        when(lockManager.switchProvider("zookeeper")).thenThrow(new RuntimeException("Switch failed"));

        // When & Then
        mockMvc.perform(post("/api/lock-management/switch")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isInternalServerError())
                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.error").exists());
    }

    @Test
    void testCheckProviderAvailability() throws Exception {
        // Given
        when(lockManager.isProviderAvailable("redis")).thenReturn(true);

        // When & Then
        mockMvc.perform(get("/api/lock-management/check/redis"))
                .andExpect(status().isOk())
                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.provider").value("redis"))
                .andExpect(jsonPath("$.available").value(true));

        verify(lockManager).isProviderAvailable("redis");
    }

    @Test
    void testCheckProviderAvailabilityException() throws Exception {
        // Given
        when(lockManager.isProviderAvailable("redis")).thenThrow(new RuntimeException("Check failed"));

        // When & Then
        mockMvc.perform(get("/api/lock-management/check/redis"))
                .andExpect(status().isInternalServerError())
                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.provider").value("redis"))
                .andExpect(jsonPath("$.error").exists());
    }

    @Test
    void testGetSupportedProviders() throws Exception {
        // Given
        when(lockManager.getSupportedProviders()).thenReturn(new String[]{"redis", "zookeeper"});
        when(lockManager.getCurrentProvider()).thenReturn("redis");
        when(lockManager.isProviderAvailable("redis")).thenReturn(true);
        when(lockManager.isProviderAvailable("zookeeper")).thenReturn(false);

        // When & Then
        mockMvc.perform(get("/api/lock-management/providers"))
                .andExpect(status().isOk())
                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.supportedProviders").isArray())
                .andExpect(jsonPath("$.supportedProviders.length()").value(2))
                .andExpect(jsonPath("$.currentProvider").value("redis"))
                .andExpect(jsonPath("$.availability.redis").value(true))
                .andExpect(jsonPath("$.availability.zookeeper").value(false));

        verify(lockManager).getSupportedProviders();
        verify(lockManager).getCurrentProvider();
        verify(lockManager).isProviderAvailable("redis");
        verify(lockManager).isProviderAvailable("zookeeper");
    }

    @Test
    void testGetSupportedProvidersException() throws Exception {
        // Given
        when(lockManager.getSupportedProviders()).thenThrow(new RuntimeException("Get providers failed"));

        // When & Then
        mockMvc.perform(get("/api/lock-management/providers"))
                .andExpect(status().isInternalServerError())
                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.error").exists());
    }

    @Test
    void testSwitchProviderRequestToString() {
        // Given
        LockManagementController.SwitchProviderRequest request = 
                new LockManagementController.SwitchProviderRequest("redis");

        // When
        String result = request.toString();

        // Then
        assertEquals("SwitchProviderRequest{provider='redis'}", result);
    }
}