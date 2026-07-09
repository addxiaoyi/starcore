package dev.starcore.starcore.territory.listener;

import dev.starcore.starcore.core.service.ServiceRegistry;
import dev.starcore.starcore.module.nation.NationService;
import dev.starcore.starcore.module.nation.model.Nation;
import dev.starcore.starcore.module.nation.model.NationId;
import dev.starcore.starcore.module.war.WarService;
import dev.starcore.starcore.nation.relation.NationRelationManager;
import dev.starcore.starcore.territory.MultiChunkTerritory;
import dev.starcore.starcore.territory.permission.TerritoryPermissionManager;
import dev.starcore.starcore.territory.TerritoryPermission;
import dev.starcore.starcore.territory.service.TerritoryClaimService;
import org.bukkit.Chunk;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.player.PlayerInteractEvent;

import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Territory保护监听器
 * 保护Territory免受未授权的破坏和建造
 * 支持战争状态感知：敌国玩家在战争期间可以破坏/攻击敌方领土
 */
public class TerritoryProtectionListener implements Listener {

    private final TerritoryClaimService claimService;
    private final NationRelationManager relationManager;
    private final ServiceRegistry serviceRegistry;
    private final WarService warService;

    // 权限管理器缓存
    private final Map<UUID, TerritoryPermissionManager> permissionManagerCache = new ConcurrentHashMap<>();

    public TerritoryProtectionListener(TerritoryClaimService claimService,
                                      NationRelationManager relationManager,
                                      ServiceRegistry serviceRegistry) {
        this(claimService, relationManager, serviceRegistry, null);
    }

    public TerritoryProtectionListener(TerritoryClaimService claimService,
                                      NationRelationManager relationManager,
                                      ServiceRegistry serviceRegistry,
                                      WarService warService) {
        this.claimService = claimService;
        this.relationManager = relationManager;
        this.serviceRegistry = serviceRegistry;
        this.warService = warService;
    }

    /**
     * 获取 NationService
     */
    private NationService getNationService() {
        return serviceRegistry != null ? serviceRegistry.find(NationService.class).orElse(null) : null;
    }

    /**
     * 监听方块破坏
     */
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onBlockBreak(BlockBreakEvent event) {
        Player player = event.getPlayer();
        Block block = event.getBlock();
        Chunk chunk = block.getChunk();

        // 检查Territory
        MultiChunkTerritory territory = claimService.getTerritoryAt(chunk);
        if (territory == null) {
            return; // 荒野，允许
        }

        // 检查是否是敌国玩家在战争期间
        if (isEnemyInWar(player, territory)) {
            return; // 战争期间允许破坏敌方领土
        }

        // 获取权限管理器
        TerritoryPermissionManager pm = getTerritoryPermissionManager(territory);
        if (pm == null) {
            return;
        }

        // 检查权限
        UUID nationId = territory.getNationId();
        Set<UUID> allies = relationManager.getAllies(nationId);

        if (!pm.hasPermission(player, TerritoryPermission.BREAK, nationId, allies)) {
            event.setCancelled(true);
            player.sendMessage("§c你没有权限在此领土破坏方块");
        }
    }

    /**
     * 监听方块放置
     */
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onBlockPlace(BlockPlaceEvent event) {
        Player player = event.getPlayer();
        Block block = event.getBlock();
        Chunk chunk = block.getChunk();

        MultiChunkTerritory territory = claimService.getTerritoryAt(chunk);
        if (territory == null) {
            return;
        }

        // 检查是否是敌国玩家在战争期间
        if (isEnemyInWar(player, territory)) {
            return; // 战争期间允许在敌方领土放置
        }

        TerritoryPermissionManager pm = getTerritoryPermissionManager(territory);
        if (pm == null) {
            return;
        }

        UUID nationId = territory.getNationId();
        Set<UUID> allies = relationManager.getAllies(nationId);

        if (!pm.hasPermission(player, TerritoryPermission.BUILD, nationId, allies)) {
            event.setCancelled(true);
            player.sendMessage("§c你没有权限在此Territory放置方块");
        }
    }

    /**
     * 监听玩家交互
     */
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onPlayerInteract(PlayerInteractEvent event) {
        Player player = event.getPlayer();
        Block block = event.getClickedBlock();

        if (block == null) {
            return;
        }

        Chunk chunk = block.getChunk();
        MultiChunkTerritory territory = claimService.getTerritoryAt(chunk);

        if (territory == null) {
            return;
        }

        // 战争期间敌国玩家可以交互（攻击）
        if (isEnemyInWar(player, territory)) {
            return;
        }

        TerritoryPermissionManager pm = getTerritoryPermissionManager(territory);
        if (pm == null) {
            return;
        }

        UUID nationId = territory.getNationId();
        Set<UUID> allies = relationManager.getAllies(nationId);

        // 判断交互类型
        Material type = block.getType();
        TerritoryPermission permission = getInteractionPermission(type);

        if (permission != null && !pm.hasPermission(player, permission, nationId, allies)) {
            event.setCancelled(true);
            player.sendMessage("§c你没有权限在此Territory使用该物品");
        }
    }

    /**
     * 监听PvP
     */
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onEntityDamage(EntityDamageByEntityEvent event) {
        if (!(event.getEntity() instanceof Player victim)) {
            return;
        }

        if (!(event.getDamager() instanceof Player attacker)) {
            return;
        }

        Chunk chunk = victim.getLocation().getChunk();
        MultiChunkTerritory territory = claimService.getTerritoryAt(chunk);

        if (territory == null) {
            return; // 荒野，允许
        }

        // 检查Territory类型
        if (!territory.getType().isPvpAllowed()) {
            event.setCancelled(true);
            attacker.sendMessage("§c" + territory.getType().getDisplayName() + " 禁止PvP");
            return;
        }

        // 战争期间敌国玩家可以在敌方领土进行PvP
        if (isEnemyInWar(attacker, territory)) {
            return; // 允许
        }

        TerritoryPermissionManager pm = getTerritoryPermissionManager(territory);
        if (pm == null) {
            return;
        }

        UUID nationId = territory.getNationId();
        Set<UUID> allies = relationManager.getAllies(nationId);

        // 检查PvP权限
        if (!pm.hasPermission(attacker, TerritoryPermission.PVP, nationId, allies)) {
            event.setCancelled(true);
            attacker.sendMessage("§c此Territory禁止PvP");
        }
    }

    /**
     * 检查玩家是否是敌国玩家且处于战争状态
     */
    private boolean isEnemyInWar(Player player, MultiChunkTerritory territory) {
        if (warService == null || territory.getNationId() == null) {
            return false;
        }

        // 获取玩家所在国家
        NationService nationService = getNationService();
        if (nationService == null) {
            return false;
        }

        Optional<Nation> playerNationOpt = nationService.nationOf(player.getUniqueId());
        if (playerNationOpt.isEmpty()) {
            return false; // 无国家玩家不能入侵
        }

        Nation playerNation = playerNationOpt.get();
        UUID targetNationId = territory.getNationId();

        // 检查是否处于战争状态
        return warService.atWar(playerNation.id(), new NationId(targetNationId));
    }

    /**
     * 获取Territory权限管理器（带缓存）
     */
    private TerritoryPermissionManager getTerritoryPermissionManager(MultiChunkTerritory territory) {
        UUID territoryId = territory.getId();
        return permissionManagerCache.computeIfAbsent(territoryId, id -> {
            NationService nationService = getNationService();
            return new TerritoryPermissionManager(territoryId, nationService);
        });
    }

    /**
     * 根据方块类型获取所需权限
     */
    private TerritoryPermission getInteractionPermission(Material type) {
        return switch (type) {
            // 门类 - 使用门
            case OAK_DOOR, SPRUCE_DOOR, BIRCH_DOOR, JUNGLE_DOOR,
                 ACACIA_DOOR, DARK_OAK_DOOR, IRON_DOOR,
                 OAK_TRAPDOOR, SPRUCE_TRAPDOOR, BIRCH_TRAPDOOR,
                 JUNGLE_TRAPDOOR, ACACIA_TRAPDOOR, DARK_OAK_TRAPDOOR,
                 IRON_TRAPDOOR, OAK_FENCE_GATE, SPRUCE_FENCE_GATE,
                 BIRCH_FENCE_GATE, JUNGLE_FENCE_GATE, ACACIA_FENCE_GATE,
                 DARK_OAK_FENCE_GATE -> TerritoryPermission.INTERACT;

            // 开关类（拉杆、按钮）- 使用物品权限
            case LEVER, STONE_BUTTON, OAK_BUTTON, SPRUCE_BUTTON,
                 BIRCH_BUTTON, JUNGLE_BUTTON, ACACIA_BUTTON,
                 DARK_OAK_BUTTON -> TerritoryPermission.ITEM_USE;

            // 容器类（箱子、熔炉等）
            case CHEST, TRAPPED_CHEST, BARREL, SHULKER_BOX,
                 FURNACE, BLAST_FURNACE, SMOKER,
                 CRAFTING_TABLE, ENCHANTING_TABLE,
                 BREWING_STAND, ANVIL -> TerritoryPermission.CONTAINER;

            default -> null;
        };
    }
}
