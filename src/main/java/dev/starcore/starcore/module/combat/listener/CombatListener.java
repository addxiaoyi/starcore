package dev.starcore.starcore.module.combat.listener;

import dev.starcore.starcore.module.combat.CombatService;
import dev.starcore.starcore.module.combat.config.CombatConfig;
import dev.starcore.starcore.module.combat.model.CombatSession.CombatEndReason;
import dev.starcore.starcore.module.combat.model.CombatTag.CombatTagType;
import dev.starcore.starcore.module.diplomacy.DiplomacyService;
import dev.starcore.starcore.module.nation.NationService;
import dev.starcore.starcore.module.war.WarService;
import dev.starcore.starcore.foundation.protection.ExternalProtectionService;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.entity.Projectile;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.EntityDamageEvent.DamageCause;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.player.PlayerCommandPreprocessEvent;
import org.bukkit.event.player.PlayerDropItemEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerRespawnEvent;
import org.bukkit.inventory.EquipmentSlot;

import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 战斗事件监听器 - 处理战斗相关事件
 */
public final class CombatListener implements Listener {
    private final CombatService combatService;
    private final CombatConfig config;
    private final Optional<NationService> nationService;
    private final Optional<DiplomacyService> diplomacyService;
    private final Optional<WarService> warService;
    private final Optional<ExternalProtectionService> externalProtectionService;

    // 伤害冷却（防止重复触发）
    private final Map<UUID, Long> damageCooldowns = new ConcurrentHashMap<>();
    private static final long DAMAGE_COOLDOWN_MS = 100; // 100ms冷却

    public CombatListener(
        CombatService combatService,
        CombatConfig config,
        Optional<NationService> nationService,
        Optional<DiplomacyService> diplomacyService,
        Optional<WarService> warService,
        Optional<ExternalProtectionService> externalProtectionService
    ) {
        this.combatService = combatService;
        this.config = config;
        this.nationService = nationService;
        this.diplomacyService = diplomacyService;
        this.warService = warService;
        this.externalProtectionService = externalProtectionService;
    }

    /**
     * 玩家受伤事件 - 记录战斗标记
     */
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onEntityDamage(EntityDamageByEntityEvent event) {
        // 只处理玩家受伤
        if (!(event.getEntity() instanceof Player victim)) {
            return;
        }

        // 检查伤害冷却
        if (isOnCooldown(victim.getUniqueId())) {
            return;
        }

        // 获取攻击者
        Player attacker = getAttacker(event);
        if (attacker == null) {
            // 非玩家攻击 - 检查是否应该标记
            handleNonPlayerDamage(victim, event);
            return;
        }

        // 检查是否为有效PVP
        if (!shouldAllowPvp(victim, attacker)) {
            return;
        }

        // 记录伤害和战斗标记
        combatService.recordDamage(attacker, victim, event.getFinalDamage());

        // 发送战斗标记通知
        if (config.isCombatTagNotificationEnabled()) {
            sendCombatTagNotification(victim, attacker);
        }
    }

    /**
     * 玩家死亡事件 - 处理战斗死亡
     */
    @EventHandler(priority = EventPriority.MONITOR)
    public void onPlayerDeath(PlayerDeathEvent event) {
        Player victim = event.getEntity();
        Player killer = victim.getKiller();

        // 记录击杀
        if (killer != null) {
            combatService.recordKill(killer.getUniqueId(), victim.getUniqueId(), CombatEndReason.KILL);

            // 发送死亡消息
            if (config.isCombatTagNotificationEnabled()) {
                String message = ChatColor.translateAlternateColorCodes('&',
                    config.getCombatDeathMessage()
                        .replace("%killer%", killer.getName())
                        .replace("%victim%", victim.getName())
                );
                event.setDeathMessage(message);
            }
        } else {
            // 非玩家击杀
            combatService.recordKill(null, victim.getUniqueId(), CombatEndReason.KILL);
        }

        // 清除战斗标记
        combatService.untagPlayer(victim.getUniqueId());
    }

    /**
     * 玩家退出事件 - 检查是否在战斗中
     */
    @EventHandler(priority = EventPriority.HIGH)
    public void onPlayerQuit(PlayerQuitEvent event) {
        Player player = event.getPlayer();
        UUID playerId = player.getUniqueId();

        // 检查是否启用战斗退出阻止
        if (!config.isCombatLogoutEnabled()) {
            return;
        }

        // 检查玩家是否在战斗标记中
        if (combatService.isTagged(playerId)) {
            // 阻止退出 - 延迟处理
            Bukkit.getScheduler().runTaskLater(
                Bukkit.getPluginManager().getPlugin("StarCore"),
                () -> {
                    // 如果玩家仍然在线且仍处于战斗状态
                    Player onlinePlayer = Bukkit.getPlayer(playerId);
                    if (onlinePlayer != null && onlinePlayer.isOnline() && combatService.isTagged(playerId)) {
                        // 强制处理 - 记录为退出死亡
                        combatService.recordKill(null, playerId, CombatEndReason.LOGOUT);
                        combatService.untagPlayer(playerId);
                    }
                },
                1L
            );
        }

        // 保存玩家战斗状态
        combatService.savePlayerState(playerId);
    }

    /**
     * 玩家重生事件 - 清理战斗状态
     */
    @EventHandler(priority = EventPriority.MONITOR)
    public void onPlayerRespawn(PlayerRespawnEvent event) {
        Player player = event.getPlayer();
        UUID playerId = player.getUniqueId();

        // 清除战斗标记
        combatService.untagPlayer(playerId);
    }

    /**
     * 玩家移动事件 - 检查是否离开战斗区域
     */
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onPlayerMove(PlayerMoveEvent event) {
        // 只检查跨方块移动
        if (isSameBlock(event.getFrom(), event.getTo())) {
            return;
        }

        Player player = event.getPlayer();
        UUID playerId = player.getUniqueId();

        // 检查战斗标记
        if (!combatService.isTagged(playerId)) {
            return;
        }

        // 检查是否在安全区
        if (isInSafeZone(event.getTo())) {
            // 可以选择是否阻止移动或只是警告
            // 这里选择警告
            if (config.isCombatTagNotificationEnabled()) {
                player.sendMessage(ChatColor.YELLOW + "警告: 你正在离开战斗区域！");
            }
        }
    }

    /**
     * 玩家丢弃物品事件 - 战斗时禁止丢弃
     */
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onPlayerDropItem(PlayerDropItemEvent event) {
        Player player = event.getPlayer();

        // 检查是否在战斗中
        if (combatService.isInCombat(player.getUniqueId())) {
            // 检查配置是否禁止战斗时丢弃物品
            if (config.isCombatLogoutEnabled()) { // 复用配置
                event.setCancelled(true);
                player.sendMessage(ChatColor.RED + "你正处于战斗状态，无法丢弃物品！");
            }
        }
    }

    /**
     * 玩家使用物品事件 - 战斗时禁止使用某些物品
     */
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onPlayerInteract(PlayerInteractEvent event) {
        // 检查是否是副手交互，避免双触发
        if (event.getHand() != EquipmentSlot.HAND) {
            return;
        }

        // 检查玩家是否在战斗中
        if (!combatService.isInCombat(event.getPlayer().getUniqueId())) {
            return;
        }

        Player player = event.getPlayer();
        UUID playerId = player.getUniqueId();

        // 检查是否启用了战斗物品限制
        if (!config.isCombatItemRestrictionEnabled()) {
            return;
        }

        // 获取玩家手中的物品
        if (event.getItem() == null) {
            return;
        }

        org.bukkit.Material material = event.getItem().getType();
        String itemName = event.getItem().getType().name();

        // 战斗时禁止使用的物品列表
        boolean isBlocked = false;
        String blockReason = "";

        // 1. 检查金苹果和附魔金苹果
        if (material == org.bukkit.Material.GOLDEN_APPLE) {
            // 附魔金苹果是特殊变种
            if (event.getItem().hasItemMeta() && event.getItem().getItemMeta().hasEnchants()) {
                isBlocked = true;
                blockReason = "附魔金苹果";
            } else if (config.isBlockGoldenAppleInCombat()) {
                isBlocked = true;
                blockReason = "金苹果";
            }
        }

        // 2. 检查药水（如果配置了）
        if (config.isBlockPotionsInCombat()) {
            if (material == org.bukkit.Material.POTION ||
                material == org.bukkit.Material.SPLASH_POTION ||
                material == org.bukkit.Material.LINGERING_POTION) {
                isBlocked = true;
                blockReason = "药水";
            }
        }

        // 3. 检查食物（如果配置了）
        if (config.isBlockFoodInCombat()) {
            if (material == org.bukkit.Material.GOLDEN_APPLE ||
                material == org.bukkit.Material.ENCHANTED_GOLDEN_APPLE ||
                material == org.bukkit.Material.HONEY_BOTTLE ||
                material == org.bukkit.Material.MILK_BUCKET) {
                isBlocked = true;
                blockReason = "特定食物";
            }
        }

        // 4. 检查其他特殊物品
        if (config.getBlockedItemsInCombat().contains(itemName)) {
            isBlocked = true;
            blockReason = itemName;
        }

        // 如果物品被禁止，取消事件
        if (isBlocked) {
            event.setCancelled(true);
            player.sendMessage(ChatColor.RED + "你正处于战斗状态，无法使用" + blockReason + "！");
        }
    }

    /**
     * 玩家命令预处理 - 战斗时禁止某些命令
     */
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onCommandPreprocess(PlayerCommandPreprocessEvent event) {
        Player player = event.getPlayer();
        String command = event.getMessage().toLowerCase();

        // 检查是否在战斗中
        if (!combatService.isInCombat(player.getUniqueId())) {
            return;
        }

        // 战斗时禁止的命令
        String[] blockedCommands = {"/home", "/sethome", "/spawn", "/teleport", "/tp", "/warp", "/world"};

        for (String blocked : blockedCommands) {
            if (command.startsWith(blocked)) {
                event.setCancelled(true);
                player.sendMessage(ChatColor.RED + "你正处于战斗状态，无法使用此命令！");
                return;
            }
        }
    }

    /**
     * 获取攻击者（处理投射物）
     */
    private Player getAttacker(EntityDamageByEntityEvent event) {
        Entity damager = event.getDamager();

        if (damager instanceof Player) {
            return (Player) damager;
        }

        if (damager instanceof Projectile projectile) {
            if (projectile.getShooter() instanceof Player shooter) {
                return shooter;
            }
        }

        return null;
    }

    /**
     * 检查是否应该允许PVP
     */
    private boolean shouldAllowPvp(Player victim, Player attacker) {
        // 检查PvP是否启用
        if (!config.isPvPEnabled()) {
            return false;
        }

        // 检查是否在同一国家（友军PVP）
        if (nationService.isPresent()) {
            Optional<dev.starcore.starcore.module.nation.model.Nation> victimNation =
                nationService.get().getNationByMember(victim.getUniqueId());
            Optional<dev.starcore.starcore.module.nation.model.Nation> attackerNation =
                nationService.get().getNationByMember(attacker.getUniqueId());

            if (victimNation.isPresent() && attackerNation.isPresent()) {
                if (victimNation.get().id().value().equals(attackerNation.get().id().value())) {
                    // 同一国家
                    return config.isFriendlyFireEnabled();
                }
            }
        }

        // 检查安全区
        if (!config.isPvPEnabledInSafeZones()) {
            if (isInSafeZone(victim.getLocation()) || isInSafeZone(attacker.getLocation())) {
                return false;
            }
        }

        // 检查战斗区域配置
        if (isInCombatZone(victim.getLocation()) && !isPvPEnabledInCombatZone(victim.getLocation())) {
            return false;
        }

        return true;
    }

    /**
     * 处理非玩家伤害（如生物攻击）
     */
    private void handleNonPlayerDamage(Player victim, EntityDamageByEntityEvent event) {
        // 标记玩家受到生物攻击
        Entity damager = event.getDamager();

        // 只有特定伤害来源才标记
        DamageCause cause = event.getCause();
        if (cause == DamageCause.ENTITY_ATTACK || cause == DamageCause.ENTITY_SWEEP_ATTACK) {
            // 生物攻击
            combatService.tagPlayer(victim.getUniqueId(), damager.getUniqueId(), CombatTagType.MOB);
        }
    }

    /**
     * 发送战斗标记通知
     */
    private void sendCombatTagNotification(Player victim, Player attacker) {
        long remainingSeconds = combatService.getPlayerState(victim.getUniqueId())
            .map(state -> state.getCombatTagRemainingSeconds())
            .orElse(0L);

        String message = ChatColor.translateAlternateColorCodes('&',
            config.getCombatTagMessage()
                .replace("%time%", remainingSeconds + "秒")
                .replace("%attacker%", attacker.getName())
        );

        victim.sendMessage(message);
    }

    /**
     * 检查是否在安全区
     */
    private boolean isInSafeZone(org.bukkit.Location location) {
        // 检查配置的CombatZone
        for (CombatConfig.CombatZone zone : config.getCombatZones().values()) {
            if (zone.isInside(location.getWorld().getName(), location.getX(), location.getZ())) {
                if (zone.type() == CombatConfig.CombatZoneType.SAFE_ZONE) {
                    return true;
                }
            }
        }

        // 检查外部保护插件（如 GriefPrevention, WorldGuard, CoreProtect 等）
        if (externalProtectionService.isPresent()) {
            if (externalProtectionService.get().isProtectedAt(location)) {
                return true;
            }
        }

        return false;
    }

    /**
     * 检查是否在战斗区域
     */
    private boolean isInCombatZone(org.bukkit.Location location) {
        for (CombatConfig.CombatZone zone : config.getCombatZones().values()) {
            if (zone.isInside(location.getWorld().getName(), location.getX(), location.getZ())) {
                return true;
            }
        }
        return false;
    }

    /**
     * 检查战斗区域是否启用PvP
     */
    private boolean isPvPEnabledInCombatZone(org.bukkit.Location location) {
        for (CombatConfig.CombatZone zone : config.getCombatZones().values()) {
            if (zone.isInside(location.getWorld().getName(), location.getX(), location.getZ())) {
                return zone.pvpEnabled();
            }
        }
        return true; // 默认启用
    }

    /**
     * 检查冷却
     */
    private boolean isOnCooldown(UUID playerId) {
        long now = System.currentTimeMillis();
        Long lastHit = damageCooldowns.get(playerId);

        if (lastHit != null && now - lastHit < DAMAGE_COOLDOWN_MS) {
            return true;
        }

        damageCooldowns.put(playerId, now);
        return false;
    }

    /**
     * 检查两个位置是否为同一方块
     */
    private boolean isSameBlock(org.bukkit.Location from, org.bukkit.Location to) {
        if (from == null || to == null) {
            return true;
        }
        return from.getBlockX() == to.getBlockX()
            && from.getBlockY() == to.getBlockY()
            && from.getBlockZ() == to.getBlockZ()
            && from.getWorld().equals(to.getWorld());
    }
}
