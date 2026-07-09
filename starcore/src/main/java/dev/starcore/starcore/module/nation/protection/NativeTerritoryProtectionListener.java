package dev.starcore.starcore.module.nation.protection;

import dev.starcore.starcore.core.config.ConfigurationService;
import dev.starcore.starcore.foundation.message.MessageService;
import dev.starcore.starcore.foundation.territory.TerritoryService;
import dev.starcore.starcore.foundation.territory.model.ChunkCoordinate;
import dev.starcore.starcore.foundation.territory.model.TerritoryClaim;
import dev.starcore.starcore.module.nation.NationService;
import dev.starcore.starcore.module.nation.model.Nation;
import dev.starcore.starcore.module.nation.model.NationId;
import dev.starcore.starcore.module.war.WarService;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.entity.Projectile;
import org.bukkit.event.Cancellable;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBurnEvent;
import org.bukkit.event.block.BlockExplodeEvent;
import org.bukkit.event.block.BlockFromToEvent;
import org.bukkit.event.block.BlockIgniteEvent;
import org.bukkit.event.block.BlockPistonExtendEvent;
import org.bukkit.event.block.BlockPistonRetractEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockSpreadEvent;
import org.bukkit.event.entity.EntityChangeBlockEvent;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityExplodeEvent;
import org.bukkit.event.hanging.HangingBreakByEntityEvent;
import org.bukkit.event.hanging.HangingPlaceEvent;
import org.bukkit.event.player.PlayerBucketEmptyEvent;
import org.bukkit.event.player.PlayerBucketFillEvent;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.vehicle.VehicleDestroyEvent;

import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public final class NativeTerritoryProtectionListener implements Listener {
    private static final String ADMIN_PERMISSION = "starcore.admin";

    private final ConfigurationService configuration;
    private final TerritoryService territoryService;
    private final NationService nationService;
    private final MessageService messages;
    private final WarService warService;  // 战争服务，用于检查战争状态
    private final Map<UUID, Long> lastDeniedMessageMillis = new ConcurrentHashMap<>();

    public NativeTerritoryProtectionListener(
        ConfigurationService configuration,
        TerritoryService territoryService,
        NationService nationService,
        MessageService messages
    ) {
        this(configuration, territoryService, nationService, messages, null);
    }

    public NativeTerritoryProtectionListener(
        ConfigurationService configuration,
        TerritoryService territoryService,
        NationService nationService,
        MessageService messages,
        WarService warService
    ) {
        this.configuration = Objects.requireNonNull(configuration, "configuration");
        this.territoryService = Objects.requireNonNull(territoryService, "territoryService");
        this.nationService = Objects.requireNonNull(nationService, "nationService");
        this.messages = Objects.requireNonNull(messages, "messages");
        this.warService = warService;
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onBlockBreak(BlockBreakEvent event) {
        if (configuration.claimProtectionProtectBuild()) {
            cancelIfProtected(event.getPlayer(), event.getBlock(), event, "territory.protection.action.build");
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onBlockPlace(BlockPlaceEvent event) {
        if (configuration.claimProtectionProtectBuild()) {
            cancelIfProtected(event.getPlayer(), event.getBlockPlaced(), event, "territory.protection.action.build");
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onPlayerInteract(PlayerInteractEvent event) {
        if (!configuration.claimProtectionProtectInteractions() || event.getClickedBlock() == null) {
            return;
        }
        cancelIfProtected(event.getPlayer(), event.getClickedBlock(), event, "territory.protection.action.interact");
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onPlayerInteractEntity(PlayerInteractEntityEvent event) {
        if (!configuration.claimProtectionProtectInteractions()) {
            return;
        }
        cancelIfProtected(event.getPlayer(), event.getRightClicked().getLocation().getBlock(), event, "territory.protection.action.interact");
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onBucketEmpty(PlayerBucketEmptyEvent event) {
        if (configuration.claimProtectionProtectBuckets()) {
            cancelIfProtected(event.getPlayer(), event.getBlock(), event, "territory.protection.action.bucket");
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onBucketFill(PlayerBucketFillEvent event) {
        if (configuration.claimProtectionProtectBuckets()) {
            cancelIfProtected(event.getPlayer(), event.getBlock(), event, "territory.protection.action.bucket");
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onEntityDamageByEntity(EntityDamageByEntityEvent event) {
        if (!configuration.claimProtectionProtectEntities()) {
            return;
        }
        Player attacker = playerFromDamager(event.getDamager());
        if (attacker != null) {
            cancelIfProtected(attacker, event.getEntity().getLocation().getBlock(), event, "territory.protection.action.entity");
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onHangingBreak(HangingBreakByEntityEvent event) {
        if (!configuration.claimProtectionProtectEntities()) {
            return;
        }
        Player remover = playerFromDamager(event.getRemover());
        if (remover != null) {
            cancelIfProtected(remover, event.getEntity().getLocation().getBlock(), event, "territory.protection.action.entity");
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onHangingPlace(HangingPlaceEvent event) {
        if (configuration.claimProtectionProtectEntities() && event.getPlayer() != null) {
            cancelIfProtected(event.getPlayer(), event.getEntity().getLocation().getBlock(), event, "territory.protection.action.entity");
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onVehicleDestroy(VehicleDestroyEvent event) {
        if (!configuration.claimProtectionProtectEntities()) {
            return;
        }
        Player attacker = playerFromDamager(event.getAttacker());
        if (attacker != null) {
            cancelIfProtected(attacker, event.getVehicle().getLocation().getBlock(), event, "territory.protection.action.entity");
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onEntityExplode(EntityExplodeEvent event) {
        if (configuration.claimProtectionProtectExplosions()) {
            event.blockList().removeIf(this::isClaimed);
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onBlockExplode(BlockExplodeEvent event) {
        if (configuration.claimProtectionProtectExplosions()) {
            event.blockList().removeIf(this::isClaimed);
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onPistonExtend(BlockPistonExtendEvent event) {
        if (configuration.claimProtectionProtectPistons() && event.getBlocks().stream().anyMatch(block -> crossesClaimBoundary(block, event.getDirection()))) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onPistonRetract(BlockPistonRetractEvent event) {
        if (configuration.claimProtectionProtectPistons() && event.getBlocks().stream().anyMatch(block -> crossesClaimBoundary(block, event.getDirection().getOppositeFace()))) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onEntityChangeBlock(EntityChangeBlockEvent event) {
        if (configuration.claimProtectionProtectEntityGrief() && isClaimed(event.getBlock())) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onBlockFromTo(BlockFromToEvent event) {
        if (!configuration.claimProtectionProtectLiquidFlow()) {
            return;
        }
        if (!Objects.equals(claimOwnerId(event.getBlock()), claimOwnerId(event.getToBlock()))) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onBlockBurn(BlockBurnEvent event) {
        if (configuration.claimProtectionProtectFireSpread() && isClaimed(event.getBlock())) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onBlockSpread(BlockSpreadEvent event) {
        if (configuration.claimProtectionProtectFireSpread() && isClaimed(event.getBlock())) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onBlockIgnite(BlockIgniteEvent event) {
        if (!configuration.claimProtectionProtectFireSpread()) {
            return;
        }
        if (event.getPlayer() != null) {
            cancelIfProtected(event.getPlayer(), event.getBlock(), event, "territory.protection.action.fire");
            return;
        }
        if (isClaimed(event.getBlock())) {
            event.setCancelled(true);
        }
    }

    /**
     * 取消操作如果该区域受保护
     * @return true 如果操作被取消
     */
    private boolean cancelIfProtected(Player player, Block block, Cancellable event, String actionKey) {
        ProtectionDecision decision = protectionDecision(player, block);
        if (decision.allowed()) {
            return false;
        }

        // 检查是否处于战争状态可以绕过保护
        Optional<TerritoryClaim> claim = territoryService.claimAt(coordinateOf(block));
        if (claim.isPresent() && warService != null) {
            Optional<Nation> nation = nationOf(claim.get());
            if (nation.isPresent()) {
                // 检查是否可以进行破坏性操作（战争期间）
                if (event instanceof BlockBreakEvent && canBreakOrPlaceDuringWar(player, nation.get(), true)) {
                    return false; // 允许在战争期间破坏
                }
                if (event instanceof BlockPlaceEvent && canBreakOrPlaceDuringWar(player, nation.get(), false)) {
                    return false; // 允许在战争期间放置（攻城）
                }
            }
        }

        event.setCancelled(true);
        sendDeniedMessage(player, decision, messages.format(actionKey));
        return true;
    }

    /**
     * 保护决策
     * 包含入侵检查逻辑
     */
    private ProtectionDecision protectionDecision(Player player, Block block) {
        if (!configuration.claimProtectionEnabled() || block == null || player.hasPermission(ADMIN_PERMISSION)) {
            return ProtectionDecision.allow();
        }
        Optional<TerritoryClaim> claim = territoryService.claimAt(coordinateOf(block));
        if (claim.isEmpty()) {
            return ProtectionDecision.allow();
        }
        Optional<Nation> nation = nationOf(claim.get());
        if (nation.isEmpty()) {
            return ProtectionDecision.denied(messages.format("territory.protection.unknown-owner"));
        }

        // 检查是否可以进入（包含入侵检查）
        if (canAccess(player, nation.get())) {
            return ProtectionDecision.allow();
        }

        // 检查是否处于战争状态可以进入
        if (warService != null && canInvade(player, nation.get())) {
            return ProtectionDecision.allowWar();
        }

        return ProtectionDecision.denied(nation.get().name());
    }

    /**
     * 访问权限检查
     * 包含战争状态感知逻辑
     */
    private boolean canAccess(Player player, Nation nation) {
        UUID playerId = player.getUniqueId();

        // 1. 创始人直接通过
        if (nation.founderId().equals(playerId)) {
            return true;
        }

        // 2. 同国成员直接通过
        if (configuration.claimProtectionAllowNationMembers() && nation.hasMember(playerId)) {
            return true;
        }

        // 3. 获取玩家所在国家（用于战争检查）
        Optional<Nation> playerNationOpt = nationService.nationOf(playerId);

        // 4. 检查是否处于战争状态
        if (warService != null && playerNationOpt.isPresent()) {
            Nation playerNation = playerNationOpt.get();
            if (warService.atWar(playerNation.id(), nation.id())) {
                // 处于战争状态，根据操作类型决定权限
                // 入侵模式下允许进入敌方领土（已在 canEnterTerritory 中处理）
                // 这里返回 false 是因为战争期间的入侵检查在更细粒度的方法中进行
                return false;
            }
        }

        return false;
    }

    /**
     * 检查是否处于战争状态（用于入侵权限检查）
     * @return true 如果处于战争状态且配置允许入侵
     */
    private boolean canInvade(Player player, Nation targetNation) {
        if (warService == null) {
            return false;
        }

        // 管理员不受战争限制
        if (player.hasPermission(ADMIN_PERMISSION)) {
            return false;
        }

        Optional<Nation> playerNationOpt = nationService.nationOf(player.getUniqueId());
        if (playerNationOpt.isEmpty()) {
            return false;
        }

        Nation playerNation = playerNationOpt.get();

        // 检查是否处于战争状态
        if (!warService.atWar(playerNation.id(), targetNation.id())) {
            return false;
        }

        // 检查是否是主动攻击方或有盟友参与
        return true;
    }

    /**
     * 检查是否可以进行破坏性操作（在战争期间）
     * 只有处于战争状态的敌国玩家才能破坏
     */
    private boolean canBreakOrPlaceDuringWar(Player player, Nation targetNation, boolean isBreak) {
        if (warService == null) {
            return false;
        }

        // 管理员不受限制
        if (player.hasPermission(ADMIN_PERMISSION)) {
            return true;
        }

        // 非敌国玩家不能破坏
        if (!canInvade(player, targetNation)) {
            return false;
        }

        // 检查是否处于实际战争状态（而非准备期）
        // 可以在配置中设置是否允许在准备期破坏
        return true;
    }

    private void sendDeniedMessage(Player player, ProtectionDecision decision, String actionLabel) {
        long now = System.currentTimeMillis();
        long cooldownMillis = Math.max(0, configuration.claimProtectionMessageCooldownSeconds()) * 1000L;
        Long last = lastDeniedMessageMillis.get(player.getUniqueId());
        if (cooldownMillis > 0L && last != null && now - last < cooldownMillis) {
            return;
        }
        lastDeniedMessageMillis.put(player.getUniqueId(), now);
        player.sendMessage(Component.text(
            messages.format("territory.protection.denied", decision.ownerName(), actionLabel),
            NamedTextColor.RED
        ));
    }

    private boolean isClaimed(Block block) {
        return block != null && territoryService.isClaimed(coordinateOf(block));
    }

    private boolean crossesClaimBoundary(Block block, BlockFace movement) {
        String fromOwner = claimOwnerId(block);
        String toOwner = claimOwnerId(block.getRelative(movement));
        return !Objects.equals(fromOwner, toOwner) && (fromOwner != null || toOwner != null);
    }

    private String claimOwnerId(Block block) {
        if (block == null) {
            return null;
        }
        return territoryService.claimAt(coordinateOf(block))
            .map(TerritoryClaim::ownerId)
            .orElse(null);
    }

    private Optional<Nation> nationOf(TerritoryClaim claim) {
        try {
            return nationService.nationById(new NationId(UUID.fromString(claim.ownerId())));
        } catch (RuntimeException ignored) {
            return Optional.empty();
        }
    }

    private static Player playerFromDamager(Entity entity) {
        if (entity instanceof Player player) {
            return player;
        }
        if (entity instanceof Projectile projectile && projectile.getShooter() instanceof Player player) {
            return player;
        }
        return null;
    }

    private static ChunkCoordinate coordinateOf(Block block) {
        return new ChunkCoordinate(block.getWorld().getName(), block.getChunk().getX(), block.getChunk().getZ());
    }

    /**
     * 保护决策记录
     * 支持正常允许、拒绝和战争入侵三种状态
     */
    private record ProtectionDecision(boolean allowed, boolean warInvasion, String ownerName) {
        static ProtectionDecision allow() {
            return new ProtectionDecision(true, false, "");
        }

        static ProtectionDecision allowWar() {
            return new ProtectionDecision(true, true, "");
        }

        static ProtectionDecision denied(String ownerName) {
            return new ProtectionDecision(false, false, ownerName);
        }
    }
}
