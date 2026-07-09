package dev.starcore.starcore.module.weather.gui;

import dev.starcore.starcore.module.nation.NationService;
import dev.starcore.starcore.module.nation.model.Nation;
import dev.starcore.starcore.module.nation.model.NationId;
import dev.starcore.starcore.module.weather.WeatherControlService;
import dev.starcore.starcore.module.weather.WeatherForecastService;
import dev.starcore.starcore.module.weather.model.WeatherForecastEntry;
import dev.starcore.starcore.module.weather.model.WeatherResourceModifier;
import dev.starcore.starcore.module.weather.model.WeatherType;
import dev.starcore.starcore.module.weather.model.WorldWeatherState;
import dev.starcore.starcore.util.MessageUtil;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;

import java.util.*;

/**
 * 天气管理 GUI
 */
public class WeatherMenu {

    private final WeatherControlService weatherService;
    private final WeatherForecastService forecastService;
    private final NationService nationService;

    // GUI 标题
    private static final String MAIN_MENU_TITLE = MessageUtil.GOLD + MessageUtil.BOLD + "天气控制面板";
    private static final String FORECAST_TITLE = MessageUtil.GOLD + MessageUtil.BOLD + "天气预报";
    private static final String WEATHER_SELECT_TITLE = MessageUtil.GOLD + MessageUtil.BOLD + "选择天气";
    private static final String WORLD_SELECT_TITLE = MessageUtil.GOLD + MessageUtil.BOLD + "选择世界";
    private static final String RESOURCE_EFFECT_TITLE = MessageUtil.GOLD + MessageUtil.BOLD + "资源影响";

    // GUI 大小
    private static final int MAIN_MENU_SIZE = 27;
    private static final int FORECAST_SIZE = 54;
    private static final int WEATHER_SELECT_SIZE = 36;
    private static final int WORLD_SELECT_SIZE = 54;

    public WeatherMenu(
            WeatherControlService weatherService,
            WeatherForecastService forecastService,
            NationService nationService) {
        this.weatherService = weatherService;
        this.forecastService = forecastService;
        this.nationService = nationService;
    }

    /**
     * 打开主菜单
     */
    public void openMainMenu(Player player) {
        Nation nation = getPlayerNation(player);
        if (nation == null) {
            player.sendMessage(MessageUtil.ERROR + "你还没有加入任何国家");
            return;
        }

        Inventory gui = Bukkit.createInventory(null, MAIN_MENU_SIZE, MAIN_MENU_TITLE);

        // 检查权限
        boolean hasPermission = weatherService.hasWeatherControlPermission(nation.id());
        var permission = weatherService.getPermission(nation.id());

        // 天气设置按钮
        gui.setItem(10, createMenuItem(
            Material.CLOCK,
            MessageUtil.YELLOW + MessageUtil.BOLD + "当前天气",
            Arrays.asList(
                MessageUtil.INFO + "当前天气: " + weatherService.getCurrentWeather(nation.id()).getIcon() + " " +
                    weatherService.getCurrentWeather(nation.id()).getDisplayName(),
                "",
                MessageUtil.SUCCESS + "点击查看天气预报"
            )
        ));

        // 设置天气按钮（需要权限）
        if (hasPermission) {
            gui.setItem(12, createMenuItem(
                Material.BLAZE_POWDER,
                MessageUtil.YELLOW + MessageUtil.BOLD + "设置天气",
                Arrays.asList(
                    MessageUtil.INFO + "权限等级: " + permission.getDescription(),
                    "",
                    MessageUtil.SUCCESS + "点击选择天气类型"
                )
            ));
        } else {
            gui.setItem(12, createMenuItem(
                Material.BARRIER,
                MessageUtil.RED + MessageUtil.BOLD + "设置天气",
                Arrays.asList(
                    MessageUtil.INFO + "你的国家没有天气控制权限",
                    "",
                    MessageUtil.YELLOW + "升级权限以解锁此功能"
                )
            ));
        }

        // 世界管理按钮
        gui.setItem(14, createMenuItem(
            Material.MAP,
            MessageUtil.YELLOW + MessageUtil.BOLD + "世界管理",
            Arrays.asList(
                MessageUtil.INFO + "管理天气控制的世界",
                "",
                MessageUtil.SUCCESS + "点击打开世界管理"
            )
        ));

        // 资源影响按钮
        gui.setItem(16, createMenuItem(
            Material.GOLD_INGOT,
            MessageUtil.YELLOW + MessageUtil.BOLD + "资源影响",
            Arrays.asList(
                MessageUtil.INFO + "查看天气对资源的影响",
                "",
                MessageUtil.SUCCESS + "点击查看详情"
            )
        ));

        // 天气预报按钮
        gui.setItem(20, createMenuItem(
            Material.PAPER,
            MessageUtil.YELLOW + MessageUtil.BOLD + "天气预报",
            Arrays.asList(
                MessageUtil.INFO + "查看未来7天天气预报",
                "",
                MessageUtil.SUCCESS + "点击查看"
            )
        ));

        // 自动天气开关
        boolean autoWeather = weatherService.isAutoWeather(nation.id());
        gui.setItem(22, createMenuItem(
            autoWeather ? Material.LIME_DYE : Material.GRAY_DYE,
            MessageUtil.YELLOW + MessageUtil.BOLD + "自动天气",
            Arrays.asList(
                MessageUtil.INFO + "状态: " + (autoWeather ? MessageUtil.SUCCESS + "启用" : MessageUtil.ERROR + "禁用"),
                "",
                MessageUtil.INFO + "启用后天气将自动变化",
                "",
                MessageUtil.SUCCESS + "点击切换"
            )
        ));

        // 权限信息
        gui.setItem(24, createMenuItem(
            Material.BOOK,
            MessageUtil.YELLOW + MessageUtil.BOLD + "权限信息",
            Arrays.asList(
                MessageUtil.INFO + "当前权限: " + permission.getDescription(),
                "",
                getPermissionDetails(permission)
            )
        ));

        // 填充边框
        fillBorder(gui);

        player.openInventory(gui);
    }

    /**
     * 打开天气预报界面
     */
    public void openForecastMenu(Player player) {
        Nation nation = getPlayerNation(player);
        if (nation == null) {
            player.sendMessage("§c你还没有加入任何国家");
            return;
        }

        List<WeatherForecastEntry> forecast = forecastService.getForecast(nation.id());

        Inventory gui = Bukkit.createInventory(null, FORECAST_SIZE, FORECAST_TITLE);

        // 设置标题显示国家名称
        gui.setItem(4, createMenuItem(
            Material.PAPER,
            "§e§l" + nation.name() + " 天气预报",
            Arrays.asList(
                "§7未来" + forecast.size() + "天的天气预报"
            )
        ));

        // 添加天气预报条目
        int slot = 18;
        for (int i = 0; i < Math.min(forecast.size(), 7); i++) {
            WeatherForecastEntry entry = forecast.get(i);
            WeatherType weather = entry.getWeather();

            List<String> lore = new ArrayList<>();
            lore.add("§7日期: " + entry.getFormattedDate());
            lore.add("");
            lore.add("§e天气: " + weather.getIcon() + " " + weather.getDisplayName());
            lore.add("");

            // 显示概率
            lore.add("§7各天气概率:");
            entry.getProbabilities().forEach((type, prob) -> {
                int percent = (int) Math.round(prob * 100);
                String symbol = type == weather ? "§a" : "§7";
                lore.add(symbol + type.getIcon() + " " + type.getDisplayName() + ": " + percent + "%");
            });

            lore.add("");
            lore.add("§a点击查看资源影响");

            gui.setItem(slot++, createMenuItem(
                getWeatherMaterial(weather),
                "§e§l第" + (i + 1) + "天",
                lore
            ));
        }

        // 返回按钮
        gui.setItem(49, createMenuItem(
            Material.ARROW,
            "§c§l返回",
            Arrays.asList("§7返回主菜单")
        ));

        fillForecastBorder(gui);
        player.openInventory(gui);
    }

    /**
     * 打开天气选择界面
     */
    public void openWeatherSelectMenu(Player player, String worldName) {
        Nation nation = getPlayerNation(player);
        if (nation == null) {
            player.sendMessage("§c你还没有加入任何国家");
            return;
        }

        var permission = weatherService.getPermission(nation.id());

        Inventory gui = Bukkit.createInventory(null, WEATHER_SELECT_SIZE, WEATHER_SELECT_TITLE);

        // 设置世界名称
        gui.setItem(4, createMenuItem(
            Material.MAP,
            "§e§l选择天气 - " + worldName,
            Arrays.asList(
                "§7选择要设置的天气类型"
            )
        ));

        // 天气按钮
        WeatherType[] weatherTypes = WeatherType.values();
        int slot = 10;

        for (WeatherType weather : weatherTypes) {
            boolean canUse = canUseWeather(permission, weather);

            if (canUse) {
                List<String> lore = new ArrayList<>();
                lore.add("§7天气: " + weather.getIcon() + " " + weather.getDisplayName());
                lore.add("");

                // 显示资源影响
                Map<String, Double> modifiers = weatherService.getResourceModifiers(weather);
                lore.add("§7资源影响:");
                modifiers.forEach((resource, mod) -> {
                    int percent = (int) Math.round(mod * 100);
                    String color = percent > 100 ? "§a" : percent < 100 ? "§c" : "§7";
                    lore.add("  " + resource + ": " + color + (percent - 100) + "%");
                });

                gui.setItem(slot, createMenuItem(
                    getWeatherMaterial(weather),
                    "§e§l" + weather.getDisplayName(),
                    lore,
                    weather.name() + ":" + worldName
                ));
            } else {
                gui.setItem(slot, createMenuItem(
                    Material.BARRIER,
                    "§c§l" + weather.getDisplayName(),
                    Arrays.asList(
                        "§7权限不足",
                        "§c需要更高权限才能使用"
                    )
                ));
            }

            slot++;
            if (slot % 9 == 0) {
                slot += 2;
            }
        }

        // 返回按钮
        gui.setItem(31, createMenuItem(
            Material.ARROW,
            "§c§l返回",
            Arrays.asList("§7返回主菜单")
        ));

        fillBorder(gui);
        player.openInventory(gui);
    }

    /**
     * 打开世界选择界面
     */
    public void openWorldSelectMenu(Player player) {
        Nation nation = getPlayerNation(player);
        if (nation == null) {
            player.sendMessage("§c你还没有加入任何国家");
            return;
        }

        Map<String, WorldWeatherState> worlds = weatherService.getAllWorldWeatherStates();

        Inventory gui = Bukkit.createInventory(null, WORLD_SELECT_SIZE, WORLD_SELECT_TITLE);

        gui.setItem(4, createMenuItem(
            Material.MAP,
            "§e§l世界天气管理",
            Arrays.asList(
                "§7已注册世界: " + worlds.size(),
                "",
                "§a点击世界查看详情"
            )
        ));

        // 添加世界按钮
        int slot = 18;
        for (Map.Entry<String, WorldWeatherState> entry : worlds.entrySet()) {
            String worldName = entry.getKey();
            WorldWeatherState state = entry.getValue();
            boolean isControlled = state.isControlled() && state.getControlledByNation().equals(nation.id());
            boolean isFree = !state.isControlled();

            List<String> lore = new ArrayList<>();
            lore.add("§7当前天气: " + state.getCurrentWeather().getIcon() + " " +
                    state.getCurrentWeather().getDisplayName());

            if (isControlled) {
                lore.add("");
                lore.add("§a✓ 你的国家正在控制这个世界");
                lore.add("§7点击进行天气控制");
            } else if (isFree) {
                lore.add("");
                lore.add("§e这个世界暂未被控制");
                lore.add("§a点击申请控制");
            } else {
                lore.add("");
                lore.add("§c已被其他势力控制");
                lore.add("§7控制者: " + state.getControlledByNation());
            }

            Material material = isControlled ? Material.LIME_STAINED_GLASS_PANE :
                               isFree ? Material.YELLOW_STAINED_GLASS_PANE :
                               Material.RED_STAINED_GLASS_PANE;

            gui.setItem(slot++, createMenuItem(
                material,
                "§e§l" + worldName,
                lore,
                "world:" + worldName
            ));
        }

        // 返回按钮
        gui.setItem(49, createMenuItem(
            Material.ARROW,
            "§c§l返回",
            Arrays.asList("§7返回主菜单")
        ));

        fillForecastBorder(gui);
        player.openInventory(gui);
    }

    /**
     * 打开资源影响界面
     */
    public void openResourceEffectMenu(Player player, WeatherType weather) {
        Inventory gui = Bukkit.createInventory(null, WEATHER_SELECT_SIZE, RESOURCE_EFFECT_TITLE);

        gui.setItem(4, createMenuItem(
            getWeatherMaterial(weather),
            "§e§l" + weather.getIcon() + " " + weather.getDisplayName() + " 资源影响",
            Arrays.asList(
                "§7当前天气对各类资源的影响"
            )
        ));

        // 资源类型显示
        String[] resourceTypes = {
            WeatherResourceModifier.MINERAL,
            WeatherResourceModifier.AGRICULTURAL,
            WeatherResourceModifier.ENERGY,
            WeatherResourceModifier.LUXURY,
            WeatherResourceModifier.INDUSTRIAL,
            WeatherResourceModifier.STRATEGIC
        };

        Material[] materials = {
            Material.IRON_INGOT,
            Material.WHEAT,
            Material.COAL,
            Material.DIAMOND,
            Material.GOLD_INGOT,
            Material.EMERALD
        };

        int slot = 10;
        for (int i = 0; i < resourceTypes.length; i++) {
            String resource = resourceTypes[i];
            double modifier = weatherService.getResourceModifier(weather).getModifier(resource);
            int percent = (int) Math.round(modifier * 100);

            String status;
            if (percent > 100) {
                status = "§a+" + (percent - 100) + "% §7(增强)";
            } else if (percent < 100) {
                status = "§c" + (percent - 100) + "% §7(减弱)";
            } else {
                status = "§7±0% §7(无影响)";
            }

            List<String> lore = new ArrayList<>();
            lore.add("§7影响: " + status);
            lore.add("");
            lore.add("§7基础产出 100 单位时:");
            lore.add("§7  实际产出: §e" + Math.round(100 * modifier) + " 单位");

            gui.setItem(slot++, createMenuItem(
                materials[i],
                "§e§l" + capitalizeFirst(resource),
                lore
            ));

            if (i == 5) {
                break;
            }
        }

        // 返回按钮
        gui.setItem(31, createMenuItem(
            Material.ARROW,
            "§c§l返回",
            Arrays.asList("§7返回主菜单")
        ));

        fillBorder(gui);
        player.openInventory(gui);
    }

    // PersistentDataContainer key for storing action data
    private static final NamespacedKey ACTION_KEY = new NamespacedKey("starcore", "weather_action");
    private static final NamespacedKey WORLD_KEY = new NamespacedKey("starcore", "weather_world");

    /**
     * 从物品元数据获取存储的操作类型
     */
    private String getStoredAction(ItemMeta meta) {
        var pdc = meta.getPersistentDataContainer();
        var stored = pdc.get(ACTION_KEY, PersistentDataType.STRING);
        return stored != null ? stored : "";
    }

    /**
     * 从物品元数据获取存储的世界名
     */
    private String getStoredWorld(ItemMeta meta) {
        var pdc = meta.getPersistentDataContainer();
        return pdc.get(WORLD_KEY, PersistentDataType.STRING);
    }

    /**
     * 处理菜单点击
     */
    public boolean handleClick(Player player, ItemStack item) {
        if (item == null || !item.hasItemMeta()) {
            return false;
        }

        ItemMeta meta = item.getItemMeta();
        if (!meta.hasDisplayName()) {
            return false;
        }

        String displayName = org.bukkit.ChatColor.stripColor(meta.getDisplayName());

        // 返回按钮
        if (displayName.contains("返回")) {
            openMainMenu(player);
            return true;
        }

        // 检查是否是特殊点击 (使用 PersistentDataContainer)
        String action = getStoredAction(meta);
        String worldName = getStoredWorld(meta);
        if (action != null && !action.isEmpty()) {
            if (action.equals("weather") && worldName != null) {
                return handleWeatherSet(player, worldName, worldName);
            } else if (action.equals("world")) {
                return handleWorldClick(player, worldName);
            }
        }

        // 主菜单按钮
        if (displayName.contains("当前天气") || displayName.contains("天气预报")) {
            openForecastMenu(player);
            return true;
        }

        if (displayName.contains("设置天气")) {
            Nation nation = getPlayerNation(player);
            if (nation != null && !weatherService.hasWeatherControlPermission(nation.id())) {
                player.sendMessage("§c你的国家没有天气控制权限");
                return true;
            }
            openWorldSelectMenu(player);
            return true;
        }

        if (displayName.contains("世界管理")) {
            openWorldSelectMenu(player);
            return true;
        }

        if (displayName.contains("资源影响")) {
            Nation nation = getPlayerNation(player);
            if (nation != null) {
                WeatherType current = weatherService.getCurrentWeather(nation.id());
                openResourceEffectMenu(player, current);
            }
            return true;
        }

        if (displayName.contains("自动天气")) {
            Nation nation = getPlayerNation(player);
            if (nation != null) {
                boolean current = weatherService.isAutoWeather(nation.id());
                weatherService.setAutoWeather(nation.id(), !current);
                openMainMenu(player);
            }
            return true;
        }

        return false;
    }

    /**
     * 处理天气设置
     */
    private boolean handleWeatherSet(Player player, String weatherName, String worldName) {
        Nation nation = getPlayerNation(player);
        if (nation == null) {
            player.sendMessage("§c你还没有加入任何国家");
            return true;
        }

        WeatherType weather = WeatherType.fromName(weatherName);
        boolean success = weatherService.setWeather(nation.id(), worldName, weather);

        if (success) {
            player.sendMessage("§a成功将 " + worldName + " 的天气设置为 " +
                    weather.getIcon() + " " + weather.getDisplayName());
            openWeatherSelectMenu(player, worldName);
        } else {
            player.sendMessage("§c设置天气失败，可能还在冷却中");
        }

        return true;
    }

    /**
     * 处理世界点击
     */
    private boolean handleWorldClick(Player player, String worldName) {
        Nation nation = getPlayerNation(player);
        if (nation == null) {
            player.sendMessage("§c你还没有加入任何国家");
            return true;
        }

        WorldWeatherState state = weatherService.getWorldWeatherState(worldName);
        if (state == null) {
            player.sendMessage("§c世界未找到");
            return true;
        }

        if (!weatherService.hasWeatherControlPermission(nation.id())) {
            player.sendMessage("§c你的国家没有天气控制权限");
            return true;
        }

        if (state.isControlled() && state.getControlledByNation().equals(nation.id())) {
            // 打开天气选择
            openWeatherSelectMenu(player, worldName);
        } else if (!state.isControlled()) {
            // 申请控制
            weatherService.controlWorld(nation.id(), worldName);
            player.sendMessage("§a成功申请控制 " + worldName);
            openWeatherSelectMenu(player, worldName);
        } else {
            player.sendMessage("§c这个世界已被其他势力控制");
        }

        return true;
    }

    // ==================== 辅助方法 ====================

    private Nation getPlayerNation(Player player) {
        if (nationService == null || player == null) {
            return null;
        }
        return nationService.nationOf(player.getUniqueId()).orElse(null);
    }

    private boolean canUseWeather(dev.starcore.starcore.module.weather.model.NationWeatherPermission permission, WeatherType weather) {
        return switch (permission) {
            case NONE -> false;
            case CONTROL_BASIC -> weather == WeatherType.CLEAR || weather == WeatherType.RAIN;
            case CONTROL_ADVANCED -> weather != WeatherType.STORM;
            case CONTROL_FULL -> true;
        };
    }

    private ItemStack createMenuItem(Material material, String name, List<String> lore) {
        return createMenuItem(material, name, lore, null, null);
    }

    private ItemStack createMenuItem(Material material, String name, List<String> lore, String actionKey) {
        return createMenuItem(material, name, lore, actionKey, null);
    }

    private ItemStack createMenuItem(Material material, String name, List<String> lore, String actionKey, String worldName) {
        ItemStack item = new ItemStack(material);
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.setDisplayName(name);
            meta.setLore(lore);
            // 使用 PersistentDataContainer 替代已弃用的 localizedName
            var pdc = meta.getPersistentDataContainer();
            if (actionKey != null) {
                pdc.set(ACTION_KEY, PersistentDataType.STRING, actionKey);
            }
            if (worldName != null) {
                pdc.set(WORLD_KEY, PersistentDataType.STRING, worldName);
            }
            item.setItemMeta(meta);
        }
        return item;
    }

    private Material getWeatherMaterial(WeatherType weather) {
        return switch (weather) {
            case CLEAR -> Material.SUNFLOWER;
            case RAIN -> Material.WATER_BUCKET;
            case THUNDER -> Material.LIGHTNING_ROD;
            case SNOW -> Material.SNOW_BLOCK;
            case STORM -> Material.BLAZE_POWDER;
        };
    }

    private String getPermissionDetails(dev.starcore.starcore.module.weather.model.NationWeatherPermission permission) {
        return switch (permission) {
            case NONE -> "§c无权限控制天气";
            case CONTROL_BASIC -> "§a可控制: 晴天、小雨";
            case CONTROL_ADVANCED -> "§a可控制: 晴天、小雨、雷暴、降雪";
            case CONTROL_FULL -> "§a可控制: 所有天气类型";
        };
    }

    private String capitalizeFirst(String str) {
        if (str == null || str.isEmpty()) {
            return str;
        }
        return str.substring(0, 1).toUpperCase() + str.substring(1);
    }

    private void fillBorder(Inventory gui) {
        Material borderMaterial = Material.BLACK_STAINED_GLASS_PANE;
        ItemStack border = new ItemStack(borderMaterial);
        ItemMeta meta = border.getItemMeta();
        if (meta != null) {
            meta.setDisplayName(" ");
            border.setItemMeta(meta);
        }

        // 第一行和最后一行
        for (int i = 0; i < 9; i++) {
            gui.setItem(i, border);
            gui.setItem(i + 18, border);
        }

        // 两侧
        gui.setItem(9, border);
        gui.setItem(17, border);
    }

    private void fillForecastBorder(Inventory gui) {
        Material borderMaterial = Material.BLACK_STAINED_GLASS_PANE;
        ItemStack border = new ItemStack(borderMaterial);
        ItemMeta meta = border.getItemMeta();
        if (meta != null) {
            meta.setDisplayName(" ");
            border.setItemMeta(meta);
        }

        // 第一行和最后两行
        for (int i = 0; i < 9; i++) {
            gui.setItem(i, border);
            gui.setItem(i + 45, border);
        }

        // 两侧
        for (int i = 0; i < 54; i += 9) {
            gui.setItem(i, border);
            gui.setItem(i + 8, border);
        }
    }
}
