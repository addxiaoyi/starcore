package dev.starcore.starcore.module.army.exercise;

import dev.starcore.starcore.core.StarCoreContext;
import dev.starcore.starcore.core.module.ModuleLayer;
import dev.starcore.starcore.core.module.ModuleMetadata;
import dev.starcore.starcore.core.module.StarCoreModule;
import dev.starcore.starcore.foundation.message.MessageService;
import dev.starcore.starcore.module.army.exercise.command.ExerciseCommand;
import dev.starcore.starcore.module.army.exercise.listener.ExerciseListener;
import dev.starcore.starcore.module.nation.NationService;
import org.bukkit.plugin.Plugin;

import java.util.List;

/**
 * 演习模块
 * 提供军事演习的创建、管理、执行等功能
 */
public final class ExerciseModule implements StarCoreModule {
    private static final ModuleMetadata METADATA = new ModuleMetadata(
        "exercise",
        "演习系统",
        ModuleLayer.MODULE,
        List.of("nation", "army"),
        List.of(ExerciseService.class),
        "Provides military exercise creation, management and execution."
    );

    private Plugin plugin;
    private NationService nationService;
    private MessageService messages;

    private ExerciseService exerciseService;
    private ExerciseCommand command;
    private ExerciseListener listener;

    @Override
    public ModuleMetadata metadata() {
        return METADATA;
    }

    @Override
    public void enable(StarCoreContext context) {
        this.plugin = context.plugin();
        this.nationService = context.serviceRegistry().require(NationService.class);
        this.messages = context.serviceRegistry().require(MessageService.class);

        // 从配置读取演习配置
        var config = context.plugin().getConfig().getConfigurationSection("exercise");
        ExerciseConfig exerciseConfig = ExerciseConfig.fromConfig(config);

        // 初始化演习服务
        exerciseService = new ExerciseServiceImpl(
            plugin,
            nationService,
            messages,
            exerciseConfig
        );

        // 初始化命令处理器
        command = new ExerciseCommand(exerciseService, nationService, messages);
        var exerciseCmd = plugin.getServer().getPluginCommand("exercise");
        if (exerciseCmd != null) {
            exerciseCmd.setExecutor(command);
            exerciseCmd.setTabCompleter(command);
        }

        // 注册事件监听器
        listener = new ExerciseListener(exerciseService, nationService, messages);
        plugin.getServer().getPluginManager().registerEvents(listener, plugin);
    }

    @Override
    public void disable(StarCoreContext context) {
        // 清理资源
        if (exerciseService instanceof ExerciseServiceImpl impl) {
            impl.shutdown();
            exerciseService = null;
        }

        if (listener != null) {
            listener = null;
        }

        if (command != null) {
            command = null;
        }
    }

    public ExerciseService getExerciseService() {
        return exerciseService;
    }

    public ExerciseCommand getCommand() {
        return command;
    }

    public ExerciseListener getListener() {
        return listener;
    }
}