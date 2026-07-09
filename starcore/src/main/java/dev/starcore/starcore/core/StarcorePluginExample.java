package dev.starcore.starcore.core;

import org.bukkit.plugin.java.JavaPlugin;

/**
 * STARCORE 主类示例
 * 展示完美的启动流程
 */
public class StarcorePluginExample extends JavaPlugin {

    @Override
    public void onEnable() {
        long startTime = System.currentTimeMillis();

        // 1. 打印启动横幅
        StarCoreBanner.printBanner(getDescription().getVersion());

        // 2. 打印性能信息
        StarCoreBanner.printPerformanceInfo();

        // 3. 打印核心特性
        StarCoreBanner.printFeatures();

        // 4. 开始加载
        StarCoreBanner.printLoadingInfo();

        try {
            // 5. 加载各个模块
            loadModules();

            // 6. 计算加载时间
            long loadTime = System.currentTimeMillis() - startTime;

            // 7. 打印统计信息
            StarCoreBanner.printStatistics(
                10,  // 总模块数
                10,  // 已加载模块数
                45,  // 命令数
                50,  // 成就数
                loadTime
            );

            // 8. 打印启动成功
            StarCoreBanner.printStartupSuccess(getDescription().getVersion());

        } catch (Exception e) {
            // 启动失败
            StarCoreBanner.printStartupFailure(e.getMessage());
            getServer().getPluginManager().disablePlugin(this);
        }
    }

    @Override
    public void onDisable() {
        // 1. 打印关闭信息
        StarCoreBanner.printShutdown();

        // 2. 保存数据
        saveData();

        // 3. 打印关闭完成
        StarCoreBanner.printShutdownComplete();
    }

    private void loadModules() {
        // 模拟加载模块
        String[] modules = {
            "Essentials 模块",
            "经济系统",
            "PvP 系统",
            "决斗系统",
            "成就系统",
            "公会系统",
            "好友系统",
            "GUI 系统",
            "缓存系统",
            "数据库系统"
        };

        for (String module : modules) {
            try {
                // 模拟加载
                Thread.sleep(50);
                StarCoreBanner.printModuleLoad(module, true);
            } catch (Exception e) {
                StarCoreBanner.printModuleLoad(module, false);
            }
        }
    }

    private void saveData() {
        try {
            Thread.sleep(100);
            StarCoreBanner.printSuccess("玩家数据已保存");
            StarCoreBanner.printSuccess("配置文件已保存");
            StarCoreBanner.printSuccess("缓存已清理");
        } catch (Exception e) {
            StarCoreBanner.printError("保存数据失败: " + e.getMessage());
        }
    }
}
