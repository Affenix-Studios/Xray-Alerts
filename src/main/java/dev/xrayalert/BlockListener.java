package dev.xrayalert;

import org.bukkit.GameMode;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

public final class BlockListener implements Listener {

    private record BlockKey(UUID world, int x, int y, int z) {
        static BlockKey of(Block b) {
            return new BlockKey(b.getWorld().getUID(), b.getX(), b.getY(), b.getZ());
        }
    }

    private final XRayAlert plugin;
    private final Map<BlockKey, Long> tracked = new HashMap<>();

    public BlockListener(XRayAlert plugin) {
        this.plugin = plugin;
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onBreak(BlockBreakEvent event) {
        Block block = event.getBlock();
        String key = plugin.oreKey(block.getType());
        if (key == null) {
            return;
        }

        Player player = event.getPlayer();
        if (player.hasPermission("xrayalert.bypass")) {
            return;
        }
        if (plugin.ignoreCreative() && player.getGameMode() == GameMode.CREATIVE) {
            return;
        }
        if (plugin.isIgnoredWorld(block.getWorld().getName())) {
            return;
        }

        if (!plugin.veinMode()) {
            plugin.alert(player, block, key, 1);
            return;
        }

        long now = System.currentTimeMillis();
        Long expiry = tracked.remove(BlockKey.of(block));
        if (expiry != null && expiry > now) {
            return;
        }

        Set<Block> vein = findVein(block, key);
        long until = now + plugin.veinTrackingMillis();
        for (Block b : vein) {
            if (!b.equals(block)) {
                tracked.put(BlockKey.of(b), until);
            }
        }
        plugin.alert(player, block, key, vein.size());
    }

    private Set<Block> findVein(Block start, String key) {
        int max = plugin.veinMaxSize();
        Set<Block> vein = new LinkedHashSet<>();
        Deque<Block> queue = new ArrayDeque<>();
        vein.add(start);
        queue.add(start);
        World world = start.getWorld();

        while (!queue.isEmpty() && vein.size() < max) {
            Block cur = queue.poll();
            for (int dx = -1; dx <= 1; dx++) {
                for (int dy = -1; dy <= 1; dy++) {
                    for (int dz = -1; dz <= 1; dz++) {
                        if (dx == 0 && dy == 0 && dz == 0) continue;
                        int nx = cur.getX() + dx;
                        int ny = cur.getY() + dy;
                        int nz = cur.getZ() + dz;
                        if (!world.isChunkLoaded(nx >> 4, nz >> 4)) continue;
                        Block n = world.getBlockAt(nx, ny, nz);
                        if (vein.contains(n)) continue;
                        if (!key.equals(plugin.oreKey(n.getType()))) continue;
                        vein.add(n);
                        queue.add(n);
                        if (vein.size() >= max) {
                            return vein;
                        }
                    }
                }
            }
        }
        return vein;
    }

    public void cleanup() {
        long now = System.currentTimeMillis();
        tracked.values().removeIf(exp -> exp <= now);
    }
}
