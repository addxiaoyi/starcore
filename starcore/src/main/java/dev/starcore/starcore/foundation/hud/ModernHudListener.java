package dev.starcore.starcore.foundation.hud;

import org.bukkit.Bukkit;
import org.bukkit.Color;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.Plugin;
import org.bukkit.scheduler.BukkitRunnable;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 现代 HUD 菜单事件监听器
 * 处理菜单点击事件和动态粒子效果
 */
public class ModernHudListener implements org.bukkit.event.Listener {

    private static ModernHudListener instance;
    private final Plugin plugin;

    // 打开的菜单映射
    private final Map<UUID, ModernHudMenu> openMenus = new ConcurrentHashMap<>();

    // 粒子动画任务
    private final Map<UUID, BukkitRunnable> particleTasks = new ConcurrentHashMap<>();

    // 背景粒子配置
    private boolean globalParticlesEnabled = true;
    private ParticleType particleType = ParticleType.SPARKLE;
    private double particleSpeed = 0.02;
    private int particleDensity = 3;

    private ModernHudListener(Plugin plugin) {
        this.plugin = plugin;
    }

    /**
     * 初始化监听器
     */
    public static synchronized ModernHudListener initialize(Plugin plugin) {
        if (instance == null) {
            instance = new ModernHudListener(plugin);
            plugin.getServer().getPluginManager().registerEvents(instance, plugin);
        }
        return instance;
    }

    /**
     * 获取实例
     */
    public static ModernHudListener getInstance() {
        if (instance == null) {
            throw new IllegalStateException("ModernHudListener not initialized");
        }
        return instance;
    }

    // ==================== 菜单注册 ====================

    public static void register(Player player, ModernHudMenu menu) {
        getInstance().openMenus.put(player.getUniqueId(), menu);
    }

    public static void unregister(Player player) {
        ModernHudListener listener = getInstance();
        listener.openMenus.remove(player.getUniqueId());

        // 取消粒子动画
        BukkitRunnable task = listener.particleTasks.remove(player.getUniqueId());
        if (task != null) {
            task.cancel();
        }
    }

    public static ModernHudMenu getMenu(Player player) {
        return getInstance().openMenus.get(player.getUniqueId());
    }

    // ==================== 事件处理 ====================

    @org.bukkit.event.EventHandler(priority = org.bukkit.event.EventPriority.HIGH)
    public void onInventoryClick(org.bukkit.event.inventory.InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player player)) {
            return;
        }

        ModernHudMenu menu = openMenus.get(player.getUniqueId());
        if (menu == null) {
            return;
        }

        Inventory clickedInventory = event.getClickedInventory();
        if (clickedInventory == null || !clickedInventory.equals(menu.getInventory())) {
            return;
        }

        event.setCancelled(true);

        int slot = event.getRawSlot();
        if (slot < 0 || slot >= menu.getSize()) {
            return;
        }

        ItemStack item = clickedInventory.getItem(slot);
        if (item == null || item.getType() == Material.AIR) {
            return;
        }

        // 处理点击
        menu.handleClick(player, slot, item);
    }

    @org.bukkit.event.EventHandler(priority = org.bukkit.event.EventPriority.HIGH)
    public void onInventoryClose(org.bukkit.event.inventory.InventoryCloseEvent event) {
        if (!(event.getPlayer() instanceof Player player)) {
            return;
        }

        ModernHudMenu menu = openMenus.remove(player.getUniqueId());
        if (menu != null) {
            menu.handleClose();
        }

        // 取消粒子动画
        BukkitRunnable task = particleTasks.remove(player.getUniqueId());
        if (task != null) {
            task.cancel();
        }
    }

    @org.bukkit.event.EventHandler(priority = org.bukkit.event.EventPriority.MONITOR)
    public void onPlayerMove(org.bukkit.event.player.PlayerMoveEvent event) {
        if (event.getFrom().equals(event.getTo())) {
            return;
        }

        Player player = event.getPlayer();
        ModernHudMenu menu = openMenus.get(player.getUniqueId());
        if (menu != null && menu.isOpen() && globalParticlesEnabled) {
            // 玩家移动时增强粒子效果
            spawnMenuParticles(player, particleDensity * 2);
        }
    }

    // ==================== 粒子动画 ====================

    /**
     * 启动动态背景粒子效果
     */
    public void startParticleEffect(Player player) {
        if (!globalParticlesEnabled) {
            return;
        }

        // 取消现有任务
        BukkitRunnable existing = particleTasks.get(player.getUniqueId());
        if (existing != null) {
            existing.cancel();
        }

        BukkitRunnable task = new BukkitRunnable() {
            private int tick = 0;

            @Override
            public void run() {
                if (!player.isOnline() || !openMenus.containsKey(player.getUniqueId())) {
                    cancel();
                    particleTasks.remove(player.getUniqueId());
                    return;
                }

                spawnMenuParticles(player, particleDensity);
                tick++;
            }
        };

        particleTasks.put(player.getUniqueId(), task);
        task.runTaskTimer(plugin, 0L, 3L); // 每3tick执行一次
    }

    /**
     * 停止玩家的粒子效果
     */
    public void stopParticleEffect(Player player) {
        BukkitRunnable task = particleTasks.remove(player.getUniqueId());
        if (task != null) {
            task.cancel();
        }
    }

    /**
     * 生成菜单粒子效果
     */
    private void spawnMenuParticles(Player player, int count) {
        Location loc = player.getLocation().add(0, 1.5, 0);

        switch (particleType) {
            case SPARKLE -> spawnSparkleParticles(player, loc, count);
            case FIREFLY -> spawnFireflyParticles(player, loc, count);
            case AURORA -> spawnAuroraParticles(player, loc, count);
            case STARDUST -> spawnStardustParticles(player, loc, count);
            case BUBBLE -> spawnBubbleParticles(player, loc, count);
            case NONE -> { /* 不生成粒子 */ }
        }
    }

    /**
     * 闪烁粒子效果
     */
    private void spawnSparkleParticles(Player player, Location loc, int count) {
        Random random = new Random();

        for (int i = 0; i < count; i++) {
            double x = (random.nextDouble() - 0.5) * 2.5;
            double y = random.nextDouble() * 2.0;
            double z = (random.nextDouble() - 0.5) * 2.5;

            Location particleLoc = loc.clone().add(x, y, z);

            // 彩色闪烁
            int colorIndex = random.nextInt(5);
            Color[] colors = {Color.WHITE, Color.fromRGB(255, 215, 0), Color.fromRGB(0, 255, 255),
                Color.fromRGB(255, 182, 193), Color.fromRGB(173, 255, 47)};

            player.spawnParticle(Particle.DUST, particleLoc, 1,
                0.05, 0.05, 0.05, particleSpeed,
                new org.bukkit.Particle.DustOptions(colors[colorIndex], 1));
        }

        // 添加一些星光粒子
        if (random.nextInt(5) == 0) {
            player.spawnParticle(Particle.END_ROD,
                loc.clone().add(0, 2.0 + random.nextDouble(), 0),
                1, 0.1, 0.1, 0.1, 0.01);
        }
    }

    /**
     * 萤火虫粒子效果
     */
    private void spawnFireflyParticles(Player player, Location loc, int count) {
        Random random = new Random();

        for (int i = 0; i < count; i++) {
            double angle = random.nextDouble() * Math.PI * 2;
            double radius = 1.0 + random.nextDouble() * 1.0;
            double height = random.nextDouble() * 2.5;

            double x = Math.cos(angle) * radius;
            double z = Math.sin(angle) * radius;

            Location particleLoc = loc.clone().add(x, height, z);

            // 黄色/绿色萤火虫
            player.spawnParticle(Particle.WITCH, particleLoc, 1,
                0.02, 0.02, 0.02, particleSpeed);
        }
    }

    /**
     * 极光粒子效果
     */
    private void spawnAuroraParticles(Player player, Location loc, int count) {
        Random random = new Random();

        for (int i = 0; i < count; i++) {
            double angle = random.nextDouble() * Math.PI * 2;
            double radius = 1.5;
            double height = 0.5 + random.nextDouble() * 1.5;

            double x = Math.cos(angle) * radius;
            double z = Math.sin(angle) * radius;

            Location particleLoc = loc.clone().add(x, height, z);

            // 彩虹色极光
            float hue = (float) (random.nextDouble() * 0.3 + 0.5);
            // 简单地从预定义颜色中选择
            Color color = switch ((int)(hue * 6) % 6) {
                case 0 -> Color.RED;
                case 1 -> Color.ORANGE;
                case 2 -> Color.YELLOW;
                case 3 -> Color.GREEN;
                case 4 -> Color.BLUE;
                default -> Color.PURPLE;
            };

            player.spawnParticle(Particle.DUST, particleLoc, 1,
                0.05, 0.05, 0.05, particleSpeed,
                new org.bukkit.Particle.DustOptions(color, 2));
        }
    }

    /**
     * 星尘粒子效果
     */
    private void spawnStardustParticles(Player player, Location loc, int count) {
        Random random = new Random();

        for (int i = 0; i < count; i++) {
            double x = (random.nextDouble() - 0.5) * 3.0;
            double y = random.nextDouble() * 3.0;
            double z = (random.nextDouble() - 0.5) * 3.0;

            Location particleLoc = loc.clone().add(x, y, z);

            // 金色星尘
            player.spawnParticle(Particle.FIREWORK, particleLoc, 1,
                0.02, 0.02, 0.02, particleSpeed);
            player.spawnParticle(Particle.END_ROD, particleLoc, 1,
                0.01, 0.01, 0.01, 0.005);
        }
    }

    /**
     * 气泡粒子效果
     */
    private void spawnBubbleParticles(Player player, Location loc, int count) {
        Random random = new Random();

        for (int i = 0; i < count; i++) {
            double x = (random.nextDouble() - 0.5) * 2.0;
            double y = random.nextDouble() * 2.5;
            double z = (random.nextDouble() - 0.5) * 2.0;

            Location particleLoc = loc.clone().add(x, y, z);

            // 蓝色气泡
            player.spawnParticle(Particle.ITEM_SNOWBALL, particleLoc, 1,
                0.05, 0.05, 0.05, particleSpeed);

            // 偶尔添加水花
            if (random.nextInt(10) == 0) {
                player.spawnParticle(Particle.SPLASH, particleLoc, 2,
                    0.02, 0.02, 0.02, 0.01);
            }
        }
    }

    // ==================== 配置方法 ====================

    public void setGlobalParticlesEnabled(boolean enabled) {
        this.globalParticlesEnabled = enabled;

        if (!enabled) {
            // 取消所有粒子任务
            particleTasks.values().forEach(BukkitRunnable::cancel);
            particleTasks.clear();
        }
    }

    public void setParticleType(ParticleType type) {
        this.particleType = type;
    }

    public void setParticleSpeed(double speed) {
        this.particleSpeed = Math.max(0.001, Math.min(0.1, speed));
    }

    public void setParticleDensity(int density) {
        this.particleDensity = Math.max(1, Math.min(10, density));
    }

    public boolean isGlobalParticlesEnabled() {
        return globalParticlesEnabled;
    }

    public ParticleType getParticleType() {
        return particleType;
    }

    // ==================== 清理 ====================

    public void shutdown() {
        // 取消所有任务
        particleTasks.values().forEach(BukkitRunnable::cancel);
        particleTasks.clear();
        openMenus.clear();
        instance = null;
    }

    // ==================== 粒子类型枚举 ====================

    public enum ParticleType {
        NONE("无"),
        SPARKLE("闪烁"),
        FIREFLY("萤火虫"),
        AURORA("极光"),
        STARDUST("星尘"),
        BUBBLE("气泡");

        private final String displayName;

        ParticleType(String displayName) {
            this.displayName = displayName;
        }

        public String getDisplayName() {
            return displayName;
        }
    }
}