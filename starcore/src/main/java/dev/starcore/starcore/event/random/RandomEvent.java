package dev.starcore.starcore.event.random;

import dev.starcore.starcore.core.service.ServiceRegistry;
import dev.starcore.starcore.foundation.economy.InternalEconomyService;
import dev.starcore.starcore.util.ColorCodes;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Player;
import org.bukkit.Location;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * 随机事件类
 * 表示一个可以被触发的随机事件
 */
public class RandomEvent {

    private final String id;
    private final String name;
    private final String description;
    private final List<EventTrigger> triggers;
    private final List<EventEffect> effects;
    private final Map<String, EventResponse> responses;
    private final List<String> chainEvents;
    private final int cooldown;
    private final boolean global;
    private final int priority;

    public RandomEvent(String id, String name, String description,
                       List<EventTrigger> triggers, List<EventEffect> effects,
                       Map<String, EventResponse> responses, List<String> chainEvents,
                       int cooldown, boolean global, int priority) {
        this.id = id;
        this.name = name;
        this.description = description;
        this.triggers = triggers;
        this.effects = effects;
        this.responses = responses;
        this.chainEvents = chainEvents;
        this.cooldown = cooldown;
        this.global = global;
        this.priority = priority;
    }

    /**
     * 检查事件是否应该触发
     *
     * @param player 触发玩家
     * @param location 触发位置
     * @return 如果所有触发器都满足返回true
     */
    public boolean shouldTrigger(Player player, Location location) {
        if (triggers.isEmpty()) {
            return false;
        }
        for (EventTrigger trigger : triggers) {
            if (!trigger.check(player, location)) {
                return false;
            }
        }
        return true;
    }

    /**
     * 应用事件效果
     *
     * @param player 目标玩家
     * @param location 效果位置
     * @return 成功应用的效果数量
     */
    public int applyEffects(Player player, Location location) {
        int count = 0;
        for (EventEffect effect : effects) {
            if (effect.apply(player, location)) {
                count++;
            }
        }
        return count;
    }

    /**
     * 获取玩家的响应选项
     *
     * @return 响应选项映射
     */
    public Map<String, EventResponse> getResponses() {
        return new HashMap<>(responses);
    }

    /**
     * 执行玩家的响应
     *
     * @param responseId 响应ID
     * @param player 响应玩家
     * @param location 响应位置
     * @return 如果响应成功执行返回true
     */
    public boolean executeResponse(String responseId, Player player, Location location) {
        EventResponse response = responses.get(responseId);
        if (response == null) {
            return false;
        }
        return response.execute(player, location);
    }

    // Getters
    public String getId() {
        return id;
    }

    public String getName() {
        return name;
    }

    public String getDescription() {
        return description;
    }

    public List<EventTrigger> getTriggers() {
        return new ArrayList<>(triggers);
    }

    public List<EventEffect> getEffects() {
        return new ArrayList<>(effects);
    }

    public List<String> getChainEvents() {
        return new ArrayList<>(chainEvents);
    }

    public int getCooldown() {
        return cooldown;
    }

    public boolean isGlobal() {
        return global;
    }

    public int getPriority() {
        return priority;
    }

    /**
     * 事件响应类
     * 表示玩家对事件的一种应对措施
     */
    public static class EventResponse {
        private final String id;
        private final String name;
        private final String description;
        private final List<EventEffect> effects;
        private final List<String> chainEvents;
        private final Map<String, Object> requirements;
        private static InternalEconomyService economyService;
        private static ServiceRegistry serviceRegistry;
        private static JavaPlugin plugin;

        public EventResponse(String id, String name, String description,
                            List<EventEffect> effects, List<String> chainEvents,
                            Map<String, Object> requirements) {
            this.id = id;
            this.name = name;
            this.description = description;
            this.effects = effects;
            this.chainEvents = chainEvents;
            this.requirements = requirements;
        }

        /**
         * 设置经济服务（用于集成）
         */
        public static void setEconomyService(InternalEconomyService service) {
            economyService = service;
        }

        /**
         * 设置服务注册表
         */
        public static void setServiceRegistry(ServiceRegistry registry) {
            serviceRegistry = registry;
        }

        /**
         * 设置插件实例
         */
        public static void setPlugin(JavaPlugin pluginInstance) {
            plugin = pluginInstance;
        }

        /**
         * 获取经济服务（带延迟解析）
         */
        private static InternalEconomyService getEconomyService() {
            if (economyService != null) {
                return economyService;
            }
            // 尝试从服务注册表获取
            if (serviceRegistry != null) {
                return serviceRegistry.find(InternalEconomyService.class).orElse(null);
            }
            // 尝试从 Bukkit 服务管理器获取
            if (plugin != null) {
                return plugin.getServer().getServicesManager().load(InternalEconomyService.class);
            }
            return null;
        }

        /**
         * 注入所有服务
         */
        public static void injectServices(Object... services) {
            for (Object service : services) {
                if (service instanceof InternalEconomyService) {
                    economyService = (InternalEconomyService) service;
                } else if (service instanceof ServiceRegistry) {
                    serviceRegistry = (ServiceRegistry) service;
                } else if (service instanceof JavaPlugin) {
                    plugin = (JavaPlugin) service;
                }
            }
        }

        /**
         * 检查玩家是否满足响应要求
         *
         * @param player 玩家
         * @return 如果满足要求返回true
         */
        @SuppressWarnings("unchecked")
        public boolean meetsRequirements(Player player) {
            if (requirements == null || requirements.isEmpty()) {
                return true;
            }

            // 检查金币需求
            if (requirements.containsKey("money")) {
                double requiredMoney = ((Number) requirements.get("money")).doubleValue();
                InternalEconomyService econ = getEconomyService();
                if (econ != null) {
                    if (!econ.has(player.getUniqueId(), java.math.BigDecimal.valueOf(requiredMoney))) {
                        player.sendMessage(String.format("§c需要 %.2f 金币才能响应此事件", requiredMoney));
                        return false;
                    }
                }
            }

            // 检查权限需求
            if (requirements.containsKey("permission")) {
                String permission = (String) requirements.get("permission");
                if (!player.hasPermission(permission)) {
                    player.sendMessage("§c你没有权限响应此事件");
                    return false;
                }
            }

            // 检查物品需求
            if (requirements.containsKey("items")) {
                Object itemsObj = requirements.get("items");
                if (itemsObj instanceof List) {
                    List<Map<String, Object>> items = (List<Map<String, Object>>) itemsObj;
                    for (Map<String, Object> itemConfig : items) {
                        if (!checkItemRequirement(player, itemConfig)) {
                            return false;
                        }
                    }
                }
            }

            return true;
        }

        /**
         * 检查单个物品需求
         */
        private boolean checkItemRequirement(Player player, Map<String, Object> itemConfig) {
            String materialName = (String) itemConfig.get("material");
            int requiredAmount = ((Number) itemConfig.getOrDefault("amount", 1)).intValue();

            try {
                Material material = Material.valueOf(materialName.toUpperCase());
                int playerCount = 0;

                for (ItemStack item : player.getInventory().getContents()) {
                    if (item != null && item.getType() == material) {
                        playerCount += item.getAmount();
                    }
                }

                if (playerCount < requiredAmount) {
                    player.sendMessage(String.format("§c需要 %d 个 %s 才能响应此事件（你拥有 %d 个）",
                        requiredAmount, formatMaterialName(materialName), playerCount));
                    return false;
                }
                return true;
            } catch (Exception e) {
                player.sendMessage("§c物品需求检查失败: " + materialName);
                return false;
            }
        }

        /**
         * 消耗物品（从玩家背包中移除）
         */
        public void consumeRequiredItems(Player player) {
            if (!requirements.containsKey("items")) {
                return;
            }

            Object itemsObj = requirements.get("items");
            if (itemsObj instanceof List) {
                @SuppressWarnings("unchecked")
                List<Map<String, Object>> items = (List<Map<String, Object>>) itemsObj;
                for (Map<String, Object> itemConfig : items) {
                    consumeItem(player, itemConfig);
                }
            }
        }

        /**
         * 消耗单个物品
         */
        private void consumeItem(Player player, Map<String, Object> itemConfig) {
            String materialName = (String) itemConfig.get("material");
            int amount = ((Number) itemConfig.getOrDefault("amount", 1)).intValue();

            try {
                Material material = Material.valueOf(materialName.toUpperCase());
                int remaining = amount;

                for (int i = 0; i < player.getInventory().getContents().length && remaining > 0; i++) {
                    ItemStack item = player.getInventory().getContents()[i];
                    if (item != null && item.getType() == material) {
                        int removeAmount = Math.min(item.getAmount(), remaining);
                        item.setAmount(item.getAmount() - removeAmount);
                        remaining -= removeAmount;

                        if (item.getAmount() <= 0) {
                            player.getInventory().setItem(i, null);
                        }
                    }
                }
            } catch (Exception e) {
                // 忽略错误
            }
        }

        /**
         * 格式化物品名称
         */
        private String formatMaterialName(String materialName) {
            if (materialName == null) return "未知物品";
            String[] parts = materialName.toLowerCase().split("_");
            StringBuilder sb = new StringBuilder();
            for (String part : parts) {
                if (sb.length() > 0) sb.append(" ");
                sb.append(part.substring(0, 1).toUpperCase()).append(part.substring(1));
            }
            return sb.toString();
        }

        /**
         * 执行响应效果
         *
         * @param player 响应玩家
         * @param location 响应位置
         * @return 如果所有效果成功应用返回true
         */
        public boolean execute(Player player, Location location) {
            if (!meetsRequirements(player)) {
                return false;
            }

            // 消耗需求物品
            consumeRequiredItems(player);

            // 消耗金币
            if (requirements.containsKey("money")) {
                double requiredMoney = ((Number) requirements.get("money")).doubleValue();
                InternalEconomyService econ = getEconomyService();
                if (econ != null) {
                    econ.withdraw(player.getUniqueId(), java.math.BigDecimal.valueOf(requiredMoney));
                }
            }

            for (EventEffect effect : effects) {
                if (!effect.apply(player, location)) {
                    return false;
                }
            }
            return true;
        }

        // Getters
        public String getId() {
            return id;
        }

        public String getName() {
            return name;
        }

        public String getDescription() {
            return description;
        }

        public List<EventEffect> getEffects() {
            return new ArrayList<>(effects);
        }

        public List<String> getChainEvents() {
            return new ArrayList<>(chainEvents);
        }

        public Map<String, Object> getRequirements() {
            return new HashMap<>(requirements);
        }
    }
}
