package dev.starcore.starcore.pet;

import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.*;
import java.util.stream.Collectors;

/**
 * 宠物命令处理
 */
public class PetCommand implements CommandExecutor, TabCompleter {

    private final PetService petService;
    private final PetShopGUI shopGUI;
    private final PetListGUI listGUI;
    private final PetDetailGUI detailGUI;

    private final Map<UUID, UUID> pendingRenamePet;

    public PetCommand(PetService petService, PetShopGUI shopGUI, PetListGUI listGUI, PetDetailGUI detailGUI) {
        this.petService = petService;
        this.shopGUI = shopGUI;
        this.listGUI = listGUI;
        this.detailGUI = detailGUI;
        this.pendingRenamePet = new HashMap<>();
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command,
                            @NotNull String label, @NotNull String[] args) {

        if (!(sender instanceof Player player)) {
            sender.sendMessage("§c该命令只能由玩家使用！");
            return true;
        }

        if (args.length == 0) {
            sendHelp(player);
            return true;
        }

        String subCommand = args[0].toLowerCase();

        switch (subCommand) {
            case "list", "pets" -> openPetList(player);
            case "shop", "store" -> openShop(player);
            case "summon", "call" -> summonPet(player, args);
            case "despawn", "dismiss" -> despawnPet(player, args);
            case "feed" -> feedPet(player, args);
            case "rename" -> startRename(player, args);
            case "info", "detail" -> showPetInfo(player, args);
            case "upgrade", "evolve" -> upgradePet(player, args);
            case "give" -> givePet(player, args);
            case "delete", "remove" -> deletePet(player, args);
            case "stats" -> showStats(player);
            default -> sendHelp(player);
        }

        return true;
    }

    /**
     * 发送帮助信息
     */
    private void sendHelp(Player player) {
        player.sendMessage("§6§l========== 宠物系统 ==========");
        // 分隔
        player.sendMessage("§e/pet list §7- 打开宠物列表");
        player.sendMessage("§e/pet shop §7- 打开宠物商店");
        player.sendMessage("§e/pet summon [名称] §7- 召唤宠物");
        player.sendMessage("§e/pet despawn [名称] §7- 收起宠物");
        player.sendMessage("§e/pet feed §7- 喂养宠物");
        player.sendMessage("§e/pet rename <新名称> §7- 重命名宠物");
        player.sendMessage("§e/pet info [名称] §7- 查看宠物详情");
        player.sendMessage("§e/pet upgrade §7- 升级稀有度");
        player.sendMessage("§e/pet stats §7- 查看宠物统计");
        // 分隔
        player.sendMessage("§7快捷指令:");
        player.sendMessage("§7/petlist, /petshop, /myshop");
    }

    /**
     * 打开宠物列表
     */
    private void openPetList(Player player) {
        listGUI.openList(player);
    }

    /**
     * 打开宠物商店
     */
    private void openShop(Player player) {
        shopGUI.openShop(player);
    }

    /**
     * 召唤宠物
     */
    private void summonPet(Player player, String[] args) {
        PlayerPets playerPets = petService.getPlayerPets(player.getUniqueId());

        if (args.length > 1) {
            // 按名称召唤
            String petName = String.join(" ", Arrays.copyOfRange(args, 1, args.length));
            for (Pet pet : playerPets.getAllPets()) {
                if (pet.getName().equalsIgnoreCase(petName)) {
                    if (petService.summonPet(player.getUniqueId(), pet.getPetId())) {
                        player.sendMessage("§a成功召唤宠物: " + pet.getName());
                    } else {
                        player.sendMessage("§c召唤宠物失败！");
                    }
                    return;
                }
            }
            player.sendMessage("§c未找到名为 " + petName + " 的宠物！");
        } else {
            // 召唤活动宠物
            Pet activePet = playerPets.getActivePet();
            if (activePet != null) {
                if (petService.summonPet(player.getUniqueId(), activePet.getPetId())) {
                    player.sendMessage("§a成功召唤宠物: " + activePet.getName());
                } else {
                    player.sendMessage("§c召唤宠物失败！");
                }
            } else {
                player.sendMessage("§c你没有设置活动宠物！请使用 /pet list 设置");
            }
        }
    }

    /**
     * 收起宠物
     */
    private void despawnPet(Player player, String[] args) {
        PlayerPets playerPets = petService.getPlayerPets(player.getUniqueId());

        if (args.length > 1) {
            String petName = String.join(" ", Arrays.copyOfRange(args, 1, args.length));
            for (Pet pet : playerPets.getAllPets()) {
                if (pet.getName().equalsIgnoreCase(petName)) {
                    if (petService.despawnPet(player.getUniqueId(), pet.getPetId())) {
                        player.sendMessage("§a已收起宠物: " + pet.getName());
                    } else {
                        player.sendMessage("§c宠物未召唤！");
                    }
                    return;
                }
            }
            player.sendMessage("§c未找到名为 " + petName + " 的宠物！");
        } else {
            // 收起所有
            petService.despawnAllPets(player.getUniqueId());
            player.sendMessage("§a已收起所有宠物！");
        }
    }

    /**
     * 喂养宠物
     */
    private void feedPet(Player player, String[] args) {
        PlayerPets playerPets = petService.getPlayerPets(player.getUniqueId());

        if (player.getItemInHand() == null) {
            player.sendMessage("§c请手持食物来喂养宠物！");
            return;
        }

        Pet targetPet = null;

        if (args.length > 1) {
            String petName = String.join(" ", Arrays.copyOfRange(args, 1, args.length));
            for (Pet pet : playerPets.getAllPets()) {
                if (pet.getName().equalsIgnoreCase(petName)) {
                    targetPet = pet;
                    break;
                }
            }
        } else {
            targetPet = playerPets.getActivePet();
        }

        if (targetPet == null) {
            player.sendMessage("§c没有可喂养的宠物！");
            return;
        }

        if (petService.feedPet(player.getUniqueId(), targetPet.getPetId(), player.getItemInHand())) {
            player.sendMessage("§a成功喂养宠物 " + targetPet.getName() + "！");
            player.sendMessage("§7宠物恢复了 30% 生命值并获得经验！");

            // 消耗食物
            if (player.getGameMode() != org.bukkit.GameMode.CREATIVE) {
                player.getItemInHand().setAmount(player.getItemInHand().getAmount() - 1);
            }
        } else {
            player.sendMessage("§c喂养失败！宠物可能不需要进食（每1小时可喂一次）");
        }
    }

    /**
     * 开始重命名
     */
    private void startRename(Player player, String[] args) {
        if (args.length < 2) {
            player.sendMessage("§c请输入新名称: /pet rename <新名称>");
            return;
        }

        PlayerPets playerPets = petService.getPlayerPets(player.getUniqueId());
        Pet targetPet = playerPets.getActivePet();

        if (targetPet == null) {
            player.sendMessage("§c你没有设置活动宠物！");
            return;
        }

        String newName = String.join(" ", Arrays.copyOfRange(args, 1, args.length));
        if (newName.length() > 16) {
            player.sendMessage("§c宠物名称不能超过16个字符！");
            return;
        }

        petService.renamePet(player.getUniqueId(), targetPet.getPetId(), newName);
        player.sendMessage("§a已将宠物重命名为: §e" + newName);
    }

    /**
     * 查看宠物详情
     */
    private void showPetInfo(Player player, String[] args) {
        PlayerPets playerPets = petService.getPlayerPets(player.getUniqueId());

        Pet targetPet = null;

        if (args.length > 1) {
            String petName = String.join(" ", Arrays.copyOfRange(args, 1, args.length));
            for (Pet pet : playerPets.getAllPets()) {
                if (pet.getName().equalsIgnoreCase(petName)) {
                    targetPet = pet;
                    break;
                }
            }
        } else {
            targetPet = playerPets.getActivePet();
        }

        if (targetPet == null) {
            player.sendMessage("§c没有可查看的宠物！");
            return;
        }

        detailGUI.openDetail(player, targetPet.getPetId());
    }

    /**
     * 升级宠物稀有度
     */
    private void upgradePet(Player player, String[] args) {
        PlayerPets playerPets = petService.getPlayerPets(player.getUniqueId());
        Pet targetPet = playerPets.getActivePet();

        if (targetPet == null) {
            player.sendMessage("§c你没有设置活动宠物！");
            return;
        }

        if (petService.upgradePetRarity(player.getUniqueId(), targetPet.getPetId())) {
            player.sendMessage("§a成功将宠物升级为 " + targetPet.getRarity().getColoredName() + " §a！");
            player.sendMessage("§7属性已大幅提升！");
        } else {
            player.sendMessage("§c升级失败！可能是金币不足或已达到最高稀有度！");
        }
    }

    /**
     * 给予宠物（管理员命令）
     */
    private void givePet(Player player, String[] args) {
        if (!player.hasPermission("starcore.pet.give")) {
            player.sendMessage("§c你没有权限使用此命令！");
            return;
        }

        if (args.length < 3) {
            player.sendMessage("§c用法: /pet give <玩家> <宠物类型> [稀有度]");
            return;
        }

        org.bukkit.entity.Player target = org.bukkit.Bukkit.getPlayer(args[1]);
        if (target == null) {
            player.sendMessage("§c玩家不在线！");
            return;
        }

        PetType petType = PetType.fromString(args[2]);
        if (petType == null) {
            player.sendMessage("§c未知的宠物类型！");
            return;
        }

        PetRarity rarity = PetRarity.COMMON;
        if (args.length > 3) {
            rarity = PetRarity.fromString(args[3]);
        }

        Pet pet = new Pet(target.getUniqueId(), petType, rarity);
        if (petService.getPlayerPets(target.getUniqueId()).addPet(pet)) {
            player.sendMessage("§a成功给予 " + target.getName() + " 一只 " + rarity.getColoredName() + " " + petType.getDisplayName());
            target.sendMessage("§a你收到了一只 " + rarity.getColoredName() + " " + petType.getDisplayName() + " §a宠物！");
        } else {
            player.sendMessage("§c该玩家已达到最大宠物数量！");
        }
    }

    /**
     * 删除宠物
     */
    private void deletePet(Player player, String[] args) {
        if (args.length < 2) {
            player.sendMessage("§c用法: /pet delete <宠物名称>");
            return;
        }

        PlayerPets playerPets = petService.getPlayerPets(player.getUniqueId());
        String petName = String.join(" ", Arrays.copyOfRange(args, 1, args.length));

        for (Pet pet : playerPets.getAllPets()) {
            if (pet.getName().equalsIgnoreCase(petName)) {
                // 如果已召唤，先收起
                if (pet.isSummoned()) {
                    petService.despawnPet(player.getUniqueId(), pet.getPetId());
                }

                playerPets.removePet(pet.getPetId());
                player.sendMessage("§a已删除宠物: " + pet.getName());
                return;
            }
        }

        player.sendMessage("§c未找到名为 " + petName + " 的宠物！");
    }

    /**
     * 显示宠物统计
     */
    private void showStats(Player player) {
        PlayerPets playerPets = petService.getPlayerPets(player.getUniqueId());

        player.sendMessage("§6§l========== 宠物统计 ==========");
        // 分隔
        player.sendMessage("§7宠物数量: §f" + playerPets.getPetCount() + "/" + playerPets.getMaxPets());
        player.sendMessage("§7总战斗力: §c" + String.format("%.1f", playerPets.getTotalPower()));
        player.sendMessage("§7已召唤: §a" + playerPets.getAllPets().stream().filter(Pet::isSummoned).count());

        // 分类统计
        long companions = playerPets.getPetsByCategory(PetCategory.COMPANION).size();
        long mounts = playerPets.getPetsByCategory(PetCategory.MOUNT).size();
        long flying = playerPets.getPetsByCategory(PetCategory.FLYING).size();
        long aquatic = playerPets.getPetsByCategory(PetCategory.AQUATIC).size();
        long special = playerPets.getPetsByCategory(PetCategory.SPECIAL).size();

        // 分隔
        player.sendMessage("§7伴侣型: §f" + companions);
        player.sendMessage("§7骑乘型: §f" + mounts);
        player.sendMessage("§7飞行型: §f" + flying);
        player.sendMessage("§7水生型: §f" + aquatic);
        player.sendMessage("§7特殊型: §f" + special);

        // 总经验
        // 分隔
        player.sendMessage("§7总获得经验: §b" + playerPets.getTotalExp());
    }

    @Override
    public @Nullable List<String> onTabComplete(@NotNull CommandSender sender, @NotNull Command command,
                                                  @NotNull String alias, @NotNull String[] args) {

        if (!(sender instanceof Player player)) {
            return Collections.emptyList();
        }

        if (args.length == 1) {
            return filterStartingWith(args[0], "list", "shop", "summon", "despawn",
                    "feed", "rename", "info", "upgrade", "stats", "give", "delete");
        }

        String subCommand = args[0].toLowerCase();

        switch (subCommand) {
            case "summon", "despawn", "feed", "info" -> {
                if (args.length == 2) {
                    PlayerPets playerPets = petService.getPlayerPets(player.getUniqueId());
                    return playerPets.getAllPets().stream()
                            .map(Pet::getName)
                            .filter(name -> name.toLowerCase().startsWith(args[1].toLowerCase()))
                            .collect(Collectors.toList());
                }
            }
            case "give" -> {
                if (args.length == 2) {
                    return org.bukkit.Bukkit.getOnlinePlayers().stream()
                            .map(Player::getName)
                            .filter(name -> name.toLowerCase().startsWith(args[1].toLowerCase()))
                            .collect(Collectors.toList());
                } else if (args.length == 3) {
                    return filterStartingWith(args[2],
                        Arrays.stream(PetType.values()).map(Enum::name).toArray(String[]::new));
                } else if (args.length == 4) {
                    return filterStartingWith(args[3],
                        Arrays.stream(PetRarity.values()).map(Enum::name).toString().split(","));
                }
            }
        }

        return Collections.emptyList();
    }

    /**
     * 过滤以指定字符串开头的选项
     */
    private List<String> filterStartingWith(String prefix, String... options) {
        return Arrays.stream(options)
                .filter(opt -> opt.toLowerCase().startsWith(prefix.toLowerCase()))
                .collect(Collectors.toList());
    }

    /**
     * 设置待重命名宠物
     */
    public void setPendingRename(UUID playerId, UUID petId) {
        pendingRenamePet.put(playerId, petId);
    }

    /**
     * 获取待重命名宠物
     */
    public UUID getPendingRename(UUID playerId) {
        return pendingRenamePet.remove(playerId);
    }
}
