# Anti-Dupe Security Patch - Test Scenarios

This document provides detailed test scenarios to verify the item duplication exploit has been patched.

## Prerequisites

- SmartSpawner 1.5.5+ installed
- Test server with spawners configured
- At least 2 test players for concurrent testing
- Ability to simulate network conditions (optional)

---

## Test Scenario 1: Normal Single-Page Drop

**Objective:** Verify legitimate drops work correctly

**Setup:**
1. Place a spawner with loot config enabled
2. Wait for items to accumulate in virtual inventory
3. Open spawner storage GUI as player

**Steps:**
1. Click "Drop Page" button
2. Wait for items to drop
3. Check ground for dropped items
4. Reopen storage GUI

**Expected Results:**
- ✅ All items from page dropped to ground
- ✅ Items removed from virtual inventory
- ✅ No items duplicated
- ✅ Page updated correctly
- ✅ Log shows: `transaction_result: SUCCESS`

**Pass Criteria:** 
- Item count in world = Item count that was on page
- Virtual inventory item count decreased correctly
- No duplicate items

---

## Test Scenario 2: Force Close Inventory During Drop (PRIMARY EXPLOIT TEST)

**Objective:** Verify race condition exploit is patched

**Setup:**
1. Place a spawner with valuable items (e.g., diamonds)
2. Open spawner storage GUI
3. Prepare to press ESC key rapidly

**Steps:**
1. Click "Drop Page" button
2. **IMMEDIATELY** press ESC to close inventory (within 50ms)
3. Check ground for dropped items
4. Reopen storage GUI and check virtual inventory

**Expected Results (Patched):**
- ✅ Transaction cancelled OR completed atomically
- ✅ No duplicate items (items either dropped OR in storage, never both)
- ✅ Lock released properly
- ✅ Log shows: `LOCK_FAILED_CONCURRENT_DROP` or `SUCCESS`

**FAIL Criteria (Vulnerable):**
- ❌ Items appear both on ground AND in storage (DUPE!)
- ❌ Virtual inventory not updated but items dropped

---

## Test Scenario 3: Spam Click Drop Button

**Objective:** Verify rate limiting prevents exploit attempts

**Setup:**
1. Open spawner storage with items
2. Prepare auto-clicker or rapid manual clicking

**Steps:**
1. Click "Drop Page" button 20 times rapidly (as fast as possible)
2. Monitor server logs
3. Count successful drops

**Expected Results:**
- ✅ Maximum 1 drop executed
- ✅ Subsequent clicks ignored during cooldown
- ✅ Rate limiting triggers after 10 attempts/minute
- ✅ Log shows: Rate limit warnings after threshold
- ✅ No items duplicated

**Metrics:**
- Cooldown period: 500ms between drops
- Max attempts: 10 per minute
- All attempts logged with timestamps

---

## Test Scenario 4: Server Lag Simulation

**Objective:** Verify transaction completes correctly under lag

**Setup:**
1. Install lag simulation plugin OR use `/minecraft:debug start`
2. Reduce server TPS to ~10 (high lag)
3. Open spawner storage

**Steps:**
1. With server lagging, click "Drop Page"
2. Wait for transaction to complete or timeout
3. Check for duplicates

**Expected Results:**
- ✅ Transaction completes within timeout (5 seconds) OR times out safely
- ✅ No items duplicated regardless of lag
- ✅ Lock released after timeout if needed
- ✅ Rollback occurs if VirtualInventory update fails

**Timeout Behavior:**
- Max transaction time: 5000ms
- Stuck lock detection: Automatic release
- Log shows timeout warnings if applicable

---

## Test Scenario 5: Player Disconnect Mid-Transaction

**Objective:** Verify locks are released on disconnect

**Setup:**
1. Open spawner storage with items
2. Prepare to forcefully disconnect

**Steps:**
1. Click "Drop Page" button
2. **IMMEDIATELY** disconnect client (Alt+F4, network disconnect, etc.)
3. Reconnect and check spawner storage
4. Check server logs

**Expected Results:**
- ✅ Lock released on disconnect
- ✅ Transaction cancelled or completed
- ✅ No hung transactions
- ✅ No items duplicated
- ✅ Log shows player disconnect cleanup

**Verification:**
```
Server logs should show:
"Player <name> closed inventory during active drop transaction - cancelling"
OR
Transaction completed before disconnect
```

---

## Test Scenario 6: Multiple Players Concurrent Drops

**Objective:** Verify per-player locks work independently

**Setup:**
1. Create 3+ spawners with items
2. Have 3 players each open different spawner storage GUIs

**Steps:**
1. All 3 players click "Drop Page" at exactly the same time
2. Wait for all transactions to complete
3. Verify each player's drops

**Expected Results:**
- ✅ All 3 transactions succeed independently
- ✅ No cross-contamination between players
- ✅ Each player's locks work separately
- ✅ No items duplicated for any player

**Concurrency Test:**
- Each player has independent lock
- Locks don't interfere with each other
- All transactions complete successfully

---

## Test Scenario 7: VirtualInventory Update Failure Simulation

**Objective:** Verify rollback mechanism works

**Setup:**
1. This requires code modification or mock testing
2. Modify `SpawnerData.removeItemsAndUpdateSellValue()` to return `false`

**Steps:**
1. Click "Drop Page" button
2. Observe behavior when VirtualInventory update fails

**Expected Results:**
- ✅ Rollback triggered
- ✅ Items restored to GUI slots
- ✅ No items dropped in world
- ✅ Player receives "drop_failed" message
- ✅ Log shows: `ATOMIC_DROP_FAILED`

**Rollback Verification:**
- Original items restored to exact same slots
- Virtual inventory unchanged
- No world drops occurred

---

## Test Scenario 8: Broken Spawner During Drop

**Objective:** Verify spawner validation prevents exploits

**Setup:**
1. Open spawner storage with items
2. Have second player ready to break spawner

**Steps:**
1. Player 1: Click "Drop Page"
2. Player 2: Break spawner IMMEDIATELY
3. Player 1: Check transaction result

**Expected Results:**
- ✅ Transaction cancelled if spawner broken before completion
- ✅ Validation catches broken spawner
- ✅ Log shows: `PRE_DROP_VALIDATION_FAILED`
- ✅ No duplicate items

**Validation Check:**
```java
if (!isSpawnerValid(spawner)) {
    return false; // Transaction cancelled
}
```

---

## Test Scenario 9: High Ping Player (Network Latency)

**Objective:** Verify patch works with high-latency connections

**Setup:**
1. Simulate 200-500ms network latency
2. Use VPN or network emulator for realistic conditions

**Steps:**
1. With 200ms+ ping, click "Drop Page"
2. Monitor transaction completion
3. Check for duplicates

**Expected Results:**
- ✅ Transaction completes normally
- ✅ Latency doesn't affect atomicity
- ✅ No race condition exploitable via lag
- ✅ All items accounted for correctly

**Note:** Server-side atomicity ensures client latency cannot create exploits

---

## Test Scenario 10: Rapid Page Switching During Drop

**Objective:** Verify page changes don't interfere with drops

**Setup:**
1. Spawner with multiple pages of items
2. Open storage GUI on page 1

**Steps:**
1. Click "Drop Page" on page 1
2. IMMEDIATELY click "Next Page" button
3. Try to click "Drop Page" again on page 2

**Expected Results:**
- ✅ First drop completes or gets cancelled
- ✅ Second drop is prevented by lock/cooldown
- ✅ No items duplicated across pages
- ✅ Page state updates correctly

---

## Automated Test Suite (Future Enhancement)

```java
// Example JUnit test structure (not included in current patch)
@Test
public void testDropPageItems_normalOperation() {
    // Test scenario 1
}

@Test
public void testDropPageItems_inventoryCloseDuringDrop() {
    // Test scenario 2 - PRIMARY EXPLOIT TEST
}

@Test
public void testDropPageItems_rateLimit() {
    // Test scenario 3
}

@Test
public void testDropPageItems_rollback() {
    // Test scenario 7
}
```

---

## Monitoring & Verification

### Log Patterns to Monitor

**Successful Drop:**
```
[INFO] SPAWNER_DROP_PAGE_ITEMS: player=TestPlayer, result=SUCCESS, items=64
```

**Exploit Attempt Detected:**
```
[WARNING] SPAWNER_DROP_PAGE_ITEMS: player=TestPlayer, result=FAILED, reason=LOCK_FAILED_CONCURRENT_DROP
[WARNING] Player <uuid> exceeded max drop attempts per minute (11/10)
```

**Rollback Triggered:**
```
[INFO] Rolled back drop transaction - restored 45 items to GUI
[WARNING] SPAWNER_DROP_PAGE_ITEMS: player=TestPlayer, result=FAILED, reason=ATOMIC_DROP_FAILED
```

### Key Metrics

Track these metrics over 24 hours after deployment:

1. **Drop Success Rate:** Should be >99% for legitimate users
2. **Rollback Frequency:** Should be <0.1% (indicates VirtualInventory issues)
3. **Rate Limit Triggers:** Monitor for false positives
4. **Lock Timeouts:** Should be 0 under normal conditions

---

## Reporting Issues

If any test scenario fails:

1. **Capture logs** showing the failure
2. **Record steps** to reproduce
3. **Note server version**, player count, TPS
4. **Check for** plugins that might interfere
5. **Report to** GitHub Issues with all above information

---

## Summary Checklist

Before considering patch verified, ensure:

- [ ] Test Scenario 1 passes (normal operation)
- [ ] Test Scenario 2 passes (PRIMARY - race condition exploit)
- [ ] Test Scenario 3 passes (rate limiting)
- [ ] Test Scenario 4 passes (server lag)
- [ ] Test Scenario 5 passes (disconnect)
- [ ] Test Scenario 6 passes (concurrent players)
- [ ] Test Scenario 8 passes (broken spawner)
- [ ] Logs show proper security metadata
- [ ] No performance degradation observed
- [ ] No false positives for legitimate users

---

**Test Document Version:** 1.0  
**Last Updated:** 2025-11-03  
**Patch Version:** 1.5.5
