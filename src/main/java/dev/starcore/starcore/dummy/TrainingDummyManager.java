package dev.starcore.starcore.dummy;
import dev.starcore.starcore.util.ColorCodes;

import java.util.Optional;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.entity.ArmorStand;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.player.PlayerInteractAtEntityEvent;
import org.bukkit.plugin.Plugin;
import org.bukkit.scheduler.BukkitRunnable;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 训练假人管理器
 * 使用 Paper API 和 ArmorStand 实现
 */
public final class TrainingDummyManager implements Listener {
    private final Plugin plugin;

    // 所有假人
    private final Map<UUID, TrainingDummy> dummies = new ConcurrentHashMap<>();

    // 实体 -> 假人映射
    private final Map<UUID, UUID> entityToDummy = new ConcurrentHashMap<>();

    public TrainingDummyManager(Plugin plugin) {
        this.plugin = plugin;
    }

    /**
     * 初始化
     */
    public void init() {
        plugin.getServer().getPluginManager().registerEvents(this, plugin);

        // 启动 Tick 任务
        new BukkitRunnable() {
            @Override
            public void run() {
                tick();
            }
        }.runTaskTimer(plugin, 0L, 1L);

        plugin.getLogger().info("✅ 训练假人系统已启用");
    }

    /**
     * 创建假人
     */
    public TrainingDummy createDummy(String name, Location location) {
        UUID id = UUID.randomUUID();
        TrainingDummy dummy = new TrainingDummy(id, name, location);
        dummies.put(id, dummy);
        return dummy;
    }

    /**
     * 生成假人（使用 ArmorStand）
     */
    public boolean spawnDummy(UUID dummyId) {
        TrainingDummy dummy = dummies.get(dummyId);
        if (dummy == null || dummy.isSpawned()) {
            return false;
        }

        try {
            Location loc = dummy.getLocation();
            ArmorStand stand = loc.getWorld().spawn(loc, ArmorStand.class, entity -> {
                entity.setCustomName(dummy.getName());
                entity.setCustomNameVisible(true);
                entity.setGravity(false);
                entity.setInvulnerable(dummy.isInvulnerable());
                entity.setCanPickupItems(false);
                entity.setArms(true);
                entity.setBasePlate(true);
                entity.setVisible(true);

                // 设置装备
                if (dummy.getHelmet() != null) entity.setHelmet(dummy.getHelmet());
                if (dummy.getChestplate() != null) entity.setChestplate(dummy.getChestplate());
                if (dummy.getLeggings() != null) entity.setLeggings(dummy.getLeggings());
                if (dummy.getBoots() != null) entity.setBoots(dummy.getBoots());
                if (dummy.getMainHand() != null) {
                    entity.getEquipment().setItemInMainHand(dummy.getMainHand());
                }
                if (dummy.getOffHand() != null) {
                    entity.getEquipment().setItemInOffHand(dummy.getOffHand());
                }
            });

            dummy.setNpcEntity(stand);
            dummy.setSpawned(true);
            entityToDummy.put(stand.getUniqueId(), dummyId);

            return true;

        } catch (Exception e) {
            plugin.getLogger().warning("生成假人失败: " + e.getMessage());
            return false;
        }
    }

    /**
     * 取消生成假人
     */
    public boolean despawnDummy(UUID dummyId) {
        TrainingDummy dummy = dummies.get(dummyId);
        if (dummy == null || !dummy.isSpawned()) {
            return false;
        }

        try {
            Object entity = dummy.getNpcEntity();
            if (entity instanceof Entity bukkitEntity && !bukkitEntity.isDead()) {
                entityToDummy.remove(bukkitEntity.getUniqueId());
                bukkitEntity.remove();
            }

            dummy.setNpcEntity(null);
            dummy.setSpawned(false);
            return true;

        } catch (Exception e) {
            plugin.getLogger().warning("取消生成假人失败: " + e.getMessage());
            return false;
        }
    }

    /**
     * 删除假人
     */
    public boolean removeDummy(UUID dummyId) {
        TrainingDummy dummy = dummies.get(dummyId);
        if (dummy == null) {
            return false;
        }

        if (dummy.isSpawned()) {
            despawnDummy(dummyId);
        }

        dummies.remove(dummyId);
        return true;
    }

    /**
     * 获取假人
     */
    public Optional<TrainingDummy> getDummy(UUID dummyId) {
        return Optional.ofNullable(dummies.get(dummyId));
    }

    /**
     * 通过实体获取假人
     */
    public Optional<TrainingDummy> getDummyByEntity(Entity entity) {
        UUID dummyId = entityToDummy.get(entity.getUniqueId());
        if (dummyId != null) {
            return getDummy(dummyId);
        }
        return Optional.empty();
    }

    /**
     * 检查是否为假人
     */
    public boolean isDummy(Entity entity) {
        return entityToDummy.containsKey(entity.getUniqueId());
    }

    /**
     * 获取所有假人
     */
    public Collection<TrainingDummy> getAllDummies() {
        return new ArrayList<>(dummies.values());
    }

    /**
     * Tick 更新
     */
    private void tick() {
        for (TrainingDummy dummy : dummies.values()) {
            if (!dummy.isSpawned()) {
                continue;
            }

            // 显示血量条
            if (dummy.isShowHealthBar()) {
                updateHealthBar(dummy);
            }

            // 自动重生
            if (dummy.isDead() && dummy.isAutoRespawn()) {
                long timeSinceDeath = System.currentTimeMillis() - dummy.getLastHitTime();
                if (timeSinceDeath >= dummy.getRespawnDelay()) {
                    dummy.resetHealth();
                    dummy.resetStats();
                }
            }
        }
    }

    /**
     * 更新血量条显示
     */
    private void updateHealthBar(TrainingDummy dummy) {
        Object entity = dummy.getNpcEntity();
        if (entity instanceof Entity bukkitEntity) {
            String healthBar = dummy.getHealthBar();
            bukkitEntity.setCustomName(dummy.getName() + " " + healthBar);
        }
    }

    /**
     * 处理伤害
     */
    @EventHandler
    public void onDamage(EntityDamageByEntityEvent event) {
        if (!(event.getDamager() instanceof Player attacker)) {
            return;
        }

        Optional<TrainingDummy> dummyOpt = getDummyByEntity(event.getEntity());
        if (dummyOpt.isEmpty()) {
            return;
        }

        TrainingDummy dummy = dummyOpt.get();

        // 记录伤害
        dummy.recordHit(event.getFinalDamage(), attacker.getUniqueId());

        // 显示伤害数字
        attacker.sendActionBar(String.format("§c-%.1f ❤", event.getFinalDamage()));

        // 如果是无敌的，取消伤害但记录统计
        if (dummy.isInvulnerable()) {
            event.setCancelled(true);
        }

        // 检查死亡
        if (dummy.isDead() && !dummy.isAutoRespawn()) {
            // 发送统计信息
            sendStats(attacker, dummy);
        }
    }

    /**
     * 发送统计信息
     */
    private void sendStats(Player player, TrainingDummy dummy) {
        player.sendMessage("§6========== 训练统计 ==========");
        player.sendMessage("§e假人: " + dummy.getName());
        player.sendMessage("§e总伤害: §c" + dummy.getTotalDamageReceived());
        player.sendMessage("§e命中次数: §f" + dummy.getHitCount());
        player.sendMessage(String.format("§e平均伤害: §c%.2f", dummy.getAverageDamage()));
        player.sendMessage(String.format("§eDPS: §c%.2f", dummy.getDPS()));
        player.sendMessage("§6============================");
    }

    /**
     * 处理交互
     */
    @EventHandler
    public void onInteract(PlayerInteractAtEntityEvent event) {
        Optional<TrainingDummy> dummyOpt = getDummyByEntity(event.getRightClicked());
        if (dummyOpt.isEmpty()) {
            return;
        }

        TrainingDummy dummy = dummyOpt.get();
        Player player = event.getPlayer();

        // 显示统计信息
        sendStats(player, dummy);
    }

    /**
     * 关闭
     */
    public void shutdown() {
        for (TrainingDummy dummy : new ArrayList<>(dummies.values())) {
            if (dummy.isSpawned()) {
                despawnDummy(dummy.getId());
            }
        }

        dummies.clear();
        entityToDummy.clear();

        plugin.getLogger().info("训练假人系统已关闭");
    }
}
