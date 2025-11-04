#!/bin/bash
# Performance Verification Script
# This script helps verify the performance improvements

echo "=== SmartSpawner Performance Optimization Verification ==="
echo ""
echo "This script helps you verify the performance improvements."
echo "Follow these steps to benchmark the changes:"
echo ""
echo "1. Enable debug logging in your plugin config:"
echo "   debug: true"
echo ""
echo "2. Add timing code to createSpawnerInfoItem() method:"
echo ""
cat << 'EOF'
   public ItemStack createSpawnerInfoItem(Player player, SpawnerData spawner) {
       long startTime = System.nanoTime();
       try {
           // ... existing code ...
       } finally {
           long elapsed = System.nanoTime() - startTime;
           if (plugin.isDebugMode()) {
               plugin.getLogger().info(String.format(
                   "createSpawnerInfoItem took %.3fms (cache: %s)", 
                   elapsed / 1_000_000.0,
                   baseSpawnerInfoCache.containsKey(baseCacheKey) ? "HIT" : "MISS"
               ));
           }
       }
   }
EOF
echo ""
echo "3. Restart your server and open spawner GUIs"
echo ""
echo "4. Check server logs for timing information:"
echo "   Expected results:"
echo "   - First open (MISS): ~0.13ms"
echo "   - Subsequent opens (HIT): ~0.04ms with timer, ~0.01ms without"
echo ""
echo "5. Monitor memory usage:"
echo "   - Watch heap size with /plugins/SmartSpawner/debug command (if available)"
echo "   - Memory increase should be minimal (~400KB max for busy server)"
echo ""
echo "6. Test cache invalidation:"
echo "   - Run /spawner reload"
echo "   - Verify caches rebuild correctly"
echo "   - Check player disconnect clears caches"
echo ""
echo "=== Performance Targets ==="
echo "✓ createSpawnerInfoItem(): ≤0.05ms (cached)"
echo "✓ Overall GUI open: ≤0.08ms"
echo "✓ Memory overhead: <500KB"
echo "✓ Cache hit rate: >90% after warmup"
echo ""
echo "=== Thread Safety Test ==="
echo "1. Have 10+ players open spawner GUIs simultaneously"
echo "2. Check for any ConcurrentModificationException in logs"
echo "3. Verify all GUIs render correctly"
echo ""
echo "=== Bedrock Player Test ==="
echo "1. Have Bedrock player (via Floodgate) open spawner GUI"
echo "2. Verify they see Material.SPAWNER instead of player head"
echo "3. Check cache correctly stores Bedrock player status"
echo ""
