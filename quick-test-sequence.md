# Quick Test Sequence Diagram

This diagram shows the flow of the distributed lock testing process based on `scripts/quick-test.sh`.

```mermaid
sequenceDiagram
    participant Script as Test Script
    participant App1 as App1 (8081)
    participant App2 as App2 (8082)
    participant App3 as App3 (8083)
    participant ACC001 as Account ACC001
    participant ACC002 as Account ACC002

    Script->>Script: Start Quick Distributed Lock Test
    
    Note over Script,App3: Health Check Phase
    Script->>App1: GET /api/actuator/health
    App1-->>Script: Health Status
    Script->>App2: GET /api/actuator/health
    App2-->>Script: Health Status
    Script->>App3: GET /api/actuator/health
    App3-->>Script: Health Status
    
    Note over Script,App3: Initial Balance Check
    Script->>App1: GET /api/accounts/ACC001/balance
    App1-->>Script: Initial ACC001 Balance
    Script->>App1: GET /api/accounts/ACC002/balance
    App1-->>Script: Initial ACC002 Balance
    
    Note over Script,App3: Concurrent Transfer Execution (15 requests)
    par 5 requests to App1
        loop 5 times
            Script->>App1: POST /api/transfer (ACC001→ACC002, $10)
        end
    and 5 requests to App2
        loop 5 times
            Script->>App2: POST /api/transfer (ACC001→ACC002, $10)
        end
    and 5 requests to App3
        loop 5 times
            Script->>App3: POST /api/transfer (ACC001→ACC002, $10)
        end
    end
    
    App1-->>ACC001: Update balance (with distributed lock)
    App1-->>ACC002: Update balance (with distributed lock)
    App2-->>ACC001: Update balance (with distributed lock)
    App2-->>ACC002: Update balance (with distributed lock)
    App3-->>ACC001: Update balance (with distributed lock)
    App3-->>ACC002: Update balance (with distributed lock)
    
    Script->>Script: Wait for all requests to complete
    Script->>Script: Sleep 3 seconds for system stabilization
    
    Note over Script,App3: Final Balance Verification
    Script->>App1: GET /api/accounts/ACC001/balance
    App1-->>Script: Final ACC001 Balance
    Script->>App1: GET /api/accounts/ACC002/balance
    App1-->>Script: Final ACC002 Balance
    
    Note over Script,App3: Cross-Service Consistency Check
    Script->>App2: GET /api/accounts/ACC001/balance
    App2-->>Script: ACC001 Balance from App2
    Script->>App3: GET /api/accounts/ACC001/balance
    App3-->>Script: ACC001 Balance from App3
    
    Script->>Script: Compare all balances for consistency
    Script->>Script: Calculate and verify transferred amounts
    Script->>Script: Report test results
```

## Test Flow Description

1. **Health Check Phase**: Verifies all three application instances are running and responsive
2. **Initial Balance Retrieval**: Gets starting balances for accounts ACC001 and ACC002
3. **Concurrent Transfer Execution**: Sends 15 parallel transfer requests (5 to each service instance)
4. **System Stabilization**: Waits for all operations to complete and system to stabilize
5. **Final Balance Verification**: Retrieves final balances after all transfers
6. **Cross-Service Consistency Check**: Verifies all services show consistent account balances
7. **Result Analysis**: Calculates transferred amounts and validates distributed lock effectiveness