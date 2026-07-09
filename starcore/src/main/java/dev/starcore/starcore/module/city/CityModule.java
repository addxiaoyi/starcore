package dev.starcore.starcore.module.city;

import dev.starcore.starcore.core.StarCoreContext;
import dev.starcore.starcore.core.module.ModuleMetadata;
import dev.starcore.starcore.core.module.StarCoreModule;
import dev.starcore.starcore.core.module.ModuleLayer;
import dev.starcore.starcore.foundation.economy.EconomyService;
import dev.starcore.starcore.foundation.message.MessageService;
import dev.starcore.starcore.module.city.gui.CityMenuListener;
import dev.starcore.starcore.module.nation.NationService;
import dev.starcore.starcore.module.nation.NationModule;
import dev.starcore.starcore.title.StarCorePlaceholderExpansion;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.List;

/**
 * 城市模块
 * 提供城市创建、管理、成员系统
 */
public final class CityModule implements StarCoreModule {

    private static final ModuleMetadata METADATA = new ModuleMetadata(
        "city",
        "城市系统",
        ModuleLayer.FEATURE,
        List.of("nation"),
        List.of(),
        "提供城市创建、管理、成员系统"
    );

    private CityService cityService;
    private CityCommand cityCommand;
    private CityMenuListener cityMenuListener;
    private CityPlaceholderExpansion placeholderExpansion;

    @Override
    public ModuleMetadata metadata() {
        return METADATA;
    }

    @Override
    public void enable(StarCoreContext context) {
        JavaPlugin plugin = context.plugin();

        // 获取 NationService（由 nation 模块提供）
        NationService nationService = context.serviceRegistry()
            .find(NationService.class)
            .orElseThrow(() -> new IllegalStateException("NationModule is required for CityModule"));

        // 获取 EconomyService
        EconomyService economyService = context.serviceRegistry()
            .find(EconomyService.class)
            .orElse(null);

        MessageService messages = context.serviceRegistry()
            .require(MessageService.class);

        // 创建存储
        CityStateStorage storage = new JsonCityStorage(plugin);

        // 创建服务
        this.cityService = new CityService(storage, nationService, plugin);

        // 注册服务到服务注册表
        context.serviceRegistry().register(CityService.class, cityService);

        // 注册命令
        this.cityCommand = new CityCommand(cityService, nationService, messages);
        bindCommand(plugin, "city", cityCommand);

        // 注册 GUI 菜单
        if (economyService != null) {
            this.cityMenuListener = new CityMenuListener(cityService, nationService, economyService, plugin);
            plugin.getServer().getPluginManager().registerEvents(cityMenuListener, plugin);
            bindCommand(plugin, "citymenu", (sender, command, label, args) -> {
                if (sender instanceof org.bukkit.entity.Player player) {
                    cityMenuListener.openMenu(player);
                } else {
                    sender.sendMessage(net.kyori.adventure.text.Component.text("只有玩家可以使用此命令", net.kyori.adventure.text.format.NamedTextColor.RED));
                }
                return true;
            });
            plugin.getLogger().info("CityMenuListener registered");
        } else {
            plugin.getLogger().info("CityMenuListener skipped: EconomyService not available");
        }

        // 注册 PlaceholderAPI 扩展
        try {
            Class.forName("me.clip.placeholderapi.PlaceholderAPI");
            this.placeholderExpansion = new CityPlaceholderExpansion(context.serviceRegistry());
            this.placeholderExpansion.register();
            plugin.getLogger().info("City PlaceholderAPI expansion registered");
        } catch (ClassNotFoundException e) {
            plugin.getLogger().info("PlaceholderAPI not found, city placeholders disabled");
        }

        plugin.getLogger().info("CityModule enabled: " + cityService.getAllCities().size() + " cities loaded");
    }

    @Override
    public void disable(StarCoreContext context) {
        if (cityService != null) {
            cityService.saveAll();
        }
        if (placeholderExpansion != null) {
            placeholderExpansion.unregister();
        }
        context.plugin().getLogger().info("CityModule disabled");
    }

    private void bindCommand(JavaPlugin plugin, String name, org.bukkit.command.CommandExecutor executor) {
        var cmd = plugin.getCommand(name);
        if (cmd == null) {
            plugin.getLogger().warning("Command '" + name + "' not declared in plugin.yml");
            return;
        }
        cmd.setExecutor(executor);
        if (executor instanceof org.bukkit.command.TabCompleter tabCompleter) {
            cmd.setTabCompleter(tabCompleter);
        }
    }
}
