package dev.starcore.starcore.pet;

import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * 宠物事件监听器
 */
public class PetListener implements Listener {

    private final PetService petService;
    private final PetShopGUI shopGUI;
    private final PetListGUI listGUI;
    private final PetDetailGUI detailGUI;
    private final PetCommand petCommand;

    // 待重命名状态
    private final Map<UUID, UUID> renamingPet;

    public PetListener(PetService petService, PetShopGUI shopGUI, PetListGUI listGUI,
                       PetDetailGUI detailGUI, PetCommand petCommand) {
        this.petService = petService;
        this.shopGUI = shopGUI;
        this.listGUI = listGUI;
        this.detailGUI = detailGUI;
        this.petCommand = petCommand;
        this.renamingPet = new HashMap<>();
    }

    /**
     * 玩家加入事件
     */
    @EventHandler
    public void onPlayerJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();
        UUID playerId = player.getUniqueId();

        // 恢复已召唤的宠物
        Bukkit.getScheduler().runTaskLater(petService.getConfig().getDefaultMaxPets() > 0 ?
            Bukkit.getPluginManager().getPlugin("StarCore") : null, () -> {
            PlayerPets playerPets = petService.getPlayerPets(playerId);
            for (Pet pet : playerPets.getAllPets()) {
                if (pet.isSummoned()) {
                    petService.summonPet(playerId, pet.getPetId());
                }
            }
        }, 20L);
    }

    /**
     * 玩家退出事件
     */
    @EventHandler
    public void onPlayerQuit(PlayerQuitEvent event) {
        Player player = event.getPlayer();
        petService.handlePlayerQuit(player.getUniqueId());
    }

    /**
     * 实体伤害事件 - 宠物加成
     */
    @EventHandler
    public void onEntityDamage(EntityDamageByEntityEvent event) {
        if (!(event.getDamager() instanceof Player player)) {
            return;
        }

        UUID playerId = player.getUniqueId();
        Map<String, Double> bonuses = petService.getPetBonuses(playerId);

        // 给予伤害加成
        Double damageBonus = bonuses.get("damage");
        if (damageBonus != null && damageBonus > 0) {
            // 宠物给予的攻击加成
        }
    }

    /**
     * 实体死亡事件 - 宠物获得经验
     */
    @EventHandler
    public void onEntityDeath(EntityDeathEvent event) {
        Player player = event.getEntity().getKiller();
        if (player == null) {
            return;
        }

        UUID playerId = player.getUniqueId();
        PlayerPets playerPets = petService.getPlayerPets(playerId);
        Pet activePet = playerPets.getActivePet();

        if (activePet != null && activePet.isSummoned()) {
            // 根据击杀给予经验
            long exp = event.getDroppedExp();
            long bonusExp = (long) (exp * 0.1 * petService.getConfig().getKillExpMultiplier());

            if (bonusExp > 0) {
                activePet.addExperience(bonusExp);
                playerPets.addTotalExp(bonusExp);

                // 显示经验提示
                if (bonusExp >= 10) {
                    player.sendMessage("§b[宠物] §a+" + bonusExp + " 经验");
                }
            }
        }
    }

    /**
     * 玩家与实体交互事件
     */
    @EventHandler
    public void onPlayerInteractEntity(PlayerInteractEntityEvent event) {
        Player player = event.getPlayer();
        Entity entity = event.getRightClicked();

        // 检查是否是宠物实体
        if (!entity.hasMetadata("StarCorePetOwner")) {
            return;
        }

        String ownerId = entity.getMetadata("StarCorePetOwner").get(0).asString();
        if (!ownerId.equals(player.getUniqueId().toString())) {
            player.sendMessage("§c这不是你的宠物！");
            return;
        }

        // 双击宠物打开详情
        if (event.getHand() == EquipmentSlot.HAND) {
            PlayerPets playerPets = petService.getPlayerPets(player.getUniqueId());
            for (Pet pet : playerPets.getAllPets()) {
                if (pet.isSummoned() && pet.getEntityUuid() != null &&
                    pet.getEntityUuid().equals(entity.getUniqueId())) {
                    detailGUI.openDetail(player, pet.getPetId());
                    event.setCancelled(true);
                    return;
                }
            }
        }
    }

    /**
     * 玩家交互事件 - 喂养宠物
     */
    @EventHandler
    public void onPlayerInteract(PlayerInteractEvent event) {
        Player player = event.getPlayer();

        // 检查是否在重命名状态
        UUID pendingRename = renamingPet.get(player.getUniqueId());
        if (pendingRename != null) {
            if (event.getAction() == Action.LEFT_CLICK_AIR || event.getAction() == Action.LEFT_CLICK_BLOCK) {
                renamingPet.remove(player.getUniqueId());
                player.sendMessage("§c已取消重命名！");
                return;
            }

            // 右键打开详情
            PlayerPets playerPets = petService.getPlayerPets(player.getUniqueId());
            Pet pet = playerPets.getPet(pendingRename);
            if (pet != null) {
                detailGUI.openDetail(player, pendingRename);
                renamingPet.remove(player.getUniqueId());
            }
        }
    }

    /**
     * 背包点击事件
     */
    @EventHandler
    public void onInventoryClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player player)) {
            return;
        }

        String title = event.getView().getTitle();

        // 处理宠物商店
        if (title.equals("宠物商店")) {
            event.setCancelled(true);
            handleShopClick(player, event);
            return;
        }

        // 处理宠物列表
        if (title.equals("我的宠物")) {
            event.setCancelled(true);
            handleListClick(player, event);
            return;
        }

        // 处理宠物详情
        if (title.equals("宠物详情")) {
            event.setCancelled(true);
            handleDetailClick(player, event);
        }
    }

    /**
     * 处理商店点击
     */
    private void handleShopClick(Player player, InventoryClickEvent event) {
        int slot = event.getRawSlot();

        // 底部按钮
        if (slot >= 45 && slot <= 53) {
            if (slot == 53) { // 关闭
                player.closeInventory();
            }
            return;
        }

        // 宠物物品区域 (9-44)
        if (slot >= 9 && slot < 45) {
            ItemStack item = event.getCurrentItem();
            if (item == null || !item.hasItemMeta() || !item.getItemMeta().hasDisplayName()) {
                return;
            }

            String petName = item.getItemMeta().getDisplayName().replace("§f", "");

            // 查找对应的宠物类型
            for (PetType type : PetType.values()) {
                if (type.getDisplayName().equals(petName)) {
                    // 左键购买普通
                    if (event.isLeftClick()) {
                        shopGUI.purchasePet(player, type, PetRarity.COMMON);
                    }
                    return;
                }
            }
        }
    }

    /**
     * 处理列表点击
     */
    private void handleListClick(Player player, InventoryClickEvent event) {
        int slot = event.getRawSlot();

        // 底部按钮
        if (slot >= 45) {
            ItemStack item = event.getCurrentItem();
            if (item == null || !item.hasItemMeta()) return;

            String name = item.getItemMeta().getDisplayName();
            if (name.contains("召唤所有")) {
                PlayerPets playerPets = petService.getPlayerPets(player.getUniqueId());
                for (Pet pet : playerPets.getAllPets()) {
                    if (!pet.isSummoned()) {
                        petService.summonPet(player.getUniqueId(), pet.getPetId());
                    }
                }
                player.sendMessage("§a已召唤所有宠物！");
            } else if (name.contains("收起所有")) {
                petService.despawnAllPets(player.getUniqueId());
                player.sendMessage("§a已收起所有宠物！");
            } else if (name.contains("商店")) {
                shopGUI.openShop(player);
            } else if (name.contains("关闭")) {
                player.closeInventory();
            }
            return;
        }

        // 宠物区域
        if (slot >= 9 && slot < 45) {
            ItemStack item = event.getCurrentItem();
            if (item == null || !item.hasItemMeta() || !item.getItemMeta().hasLore()) {
                return;
            }

            // 从lore获取宠物信息
            String petName = item.getItemMeta().getDisplayName();
            petName = petName.replaceAll("§.", "").replace("[", "").replace("]", "");

            // 提取名称（不含稀有度颜色代码）
            PlayerPets playerPets = petService.getPlayerPets(player.getUniqueId());
            for (Pet pet : playerPets.getAllPets()) {
                if (pet.getName().equals(petName) ||
                    item.getItemMeta().getDisplayName().contains(pet.getName())) {

                    if (event.isLeftClick()) {
                        if (pet.isSummoned()) {
                            petService.despawnPet(player.getUniqueId(), pet.getPetId());
                            player.sendMessage("§a已收起宠物: " + pet.getName());
                        } else {
                            petService.summonPet(player.getUniqueId(), pet.getPetId());
                            player.sendMessage("§a已召唤宠物: " + pet.getName());
                        }
                    } else if (event.isRightClick()) {
                        detailGUI.openDetail(player, pet.getPetId());
                    }
                    return;
                }
            }
        }
    }

    /**
     * 处理详情点击
     */
    private void handleDetailClick(Player player, InventoryClickEvent event) {
        int slot = event.getRawSlot();
        ItemStack item = event.getCurrentItem();
        if (item == null || !item.hasItemMeta()) return;

        String name = item.getItemMeta().getDisplayName();

        // 返回按钮
        if (name.contains("返回")) {
            listGUI.openList(player);
            return;
        }

        // 关闭按钮
        if (name.contains("关闭")) {
            player.closeInventory();
            return;
        }

        // 获取当前查看的宠物
        PlayerPets playerPets = petService.getPlayerPets(player.getUniqueId());

        // 从lore中获取宠物ID（这里简化处理，实际应该存储在NBT中）
        // 暂时使用活动宠物
        Pet pet = playerPets.getActivePet();
        if (pet == null) {
            player.sendMessage("§c未找到宠物信息！");
            return;
        }

        // 操作按钮处理
        if (name.contains("收起宠物")) {
            petService.despawnPet(player.getUniqueId(), pet.getPetId());
            player.sendMessage("§a已收起宠物！");
            player.closeInventory();
        } else if (name.contains("召唤宠物")) {
            petService.summonPet(player.getUniqueId(), pet.getPetId());
            player.sendMessage("§a已召唤宠物！");
            player.closeInventory();
        } else if (name.contains("重命名")) {
            player.closeInventory();
            player.sendMessage("§e请输入新名称: /pet rename <名称>");
        } else if (name.contains("喂食")) {
            if (petService.feedPet(player.getUniqueId(), pet.getPetId(),
                    new ItemStack(Material.GOLDEN_APPLE))) {
                player.sendMessage("§a成功喂养宠物！");
            } else {
                player.sendMessage("§c喂养失败！宠物可能不需要进食！");
            }
        } else if (name.contains("稀有度升级")) {
            if (petService.upgradePetRarity(player.getUniqueId(), pet.getPetId())) {
                player.sendMessage("§a升级成功！");
            } else {
                player.sendMessage("§c升级失败！");
            }
        }
    }

    /**
     * 设置待重命名宠物
     */
    public void setRenamingPet(UUID playerId, UUID petId) {
        renamingPet.put(playerId, petId);
    }
}
