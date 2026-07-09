package dev.starcore.starcore.social.mail;

import dev.starcore.starcore.core.StarCoreContext;
import dev.starcore.starcore.core.database.DatabaseService;
import dev.starcore.starcore.core.module.ModuleLayer;
import dev.starcore.starcore.core.module.ModuleMetadata;
import dev.starcore.starcore.core.module.StarCoreModule;
import dev.starcore.starcore.core.persistence.PersistenceService;
import dev.starcore.starcore.core.scheduler.StarCoreScheduler;
import dev.starcore.starcore.social.mail.attachment.MailAttachmentService;
import dev.starcore.starcore.social.mail.attachment.command.MailAttachmentCommand;
import dev.starcore.starcore.social.mail.attachment.gui.MailAttachmentGuiListener;
import dev.starcore.starcore.social.mail.attachment.impl.MailAttachmentServiceImpl;
import dev.starcore.starcore.social.mail.command.MailCommand;
import dev.starcore.starcore.social.mail.gui.MailGuiListener;
import org.bukkit.Bukkit;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.PluginCommand;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;

import java.util.List;
import java.util.UUID;

/**
 * 邮件模块
 *
 * 功能：
 * 1. 邮件发送接收
 * 2. 附件系统
 * 3. 已读未读状态
 * 4. 邮件过期清理
 * 5. GUI界面
 */
public final class MailModule implements StarCoreModule {

    private Plugin plugin;
    private MailService mailService;
    private MailGuiListener mailGuiListener;
    private MailAttachmentService attachmentService;
    private MailAttachmentGuiListener attachmentGuiListener;
    private SqlMailStorage sqlStorage;

    /**
     * 获取邮件附件服务
     */
    public MailAttachmentService attachmentService() {
        return attachmentService;
    }

    /**
     * 获取邮件附件 GUI 监听器
     */
    public MailAttachmentGuiListener attachmentGuiListener() {
        return attachmentGuiListener;
    }

    @Override
    public ModuleMetadata metadata() {
        return new ModuleMetadata(
            "mail", "邮件系统", ModuleLayer.FEATURE,
            List.of(), List.of(),
            "站内邮件系统（发送/接收/附件/已读未读/过期清理/GUI）"
        );
    }

    public MailService mailService() {
        return mailService;
    }

    public MailGuiListener mailGuiListener() {
        return mailGuiListener;
    }

    @Override
    public void enable(StarCoreContext context) {
        this.plugin = context.plugin();

        // 创建邮件服务
        this.mailService = new MailService(
            context.databaseService(),
            context.persistenceService(),
            plugin
        );

        // 初始化邮件服务
        mailService.initialize();

        // 初始化邮件附件服务
        initializeAttachmentService(context);

        // 加载所有在线玩家的邮件
        loadOnlinePlayerMails();

        // 注册玩家事件监听
        registerPlayerListeners();

        // 注册命令
        registerCommands();

        // 注册定时保存任务
        registerPeriodicSave();

        // 注册定时未读邮件提示
        registerUnreadMailNotifier();

        plugin.getLogger().info("邮件模块已启用（发送/接收/附件/已读未读/过期清理/GUI）");
    }

    /**
     * 初始化邮件附件服务
     */
    private void initializeAttachmentService(StarCoreContext context) {
        this.attachmentService = new MailAttachmentServiceImpl(
            context.databaseService(),
            context.scheduler(),
            mailService,
            plugin.getLogger()
        );

        // 创建附件 GUI 监听器
        this.attachmentGuiListener = new MailAttachmentGuiListener(mailService, attachmentService);

        plugin.getLogger().info("邮件附件服务已初始化");
    }

    @Override
    public void disable(StarCoreContext context) {
        // 保存所有邮件数据
        if (mailService != null) {
            mailService.saveAll();
        }
        plugin.getLogger().info("邮件模块已禁用，数据已保存");
    }

    /**
     * 加载所有在线玩家的邮件
     */
    private void loadOnlinePlayerMails() {
        for (Player player : Bukkit.getOnlinePlayers()) {
            mailService.loadMailsForPlayer(player.getUniqueId());
        }
    }

    /**
     * 注册玩家事件监听
     */
    private void registerPlayerListeners() {
        // 创建 GUI 监听器
        mailGuiListener = new MailGuiListener(mailService);
        Bukkit.getPluginManager().registerEvents(mailGuiListener, plugin);

        // 注册玩家加入/离开监听
        Bukkit.getPluginManager().registerEvents(new org.bukkit.event.Listener() {
            @org.bukkit.event.EventHandler
            public void onPlayerJoin(org.bukkit.event.player.PlayerJoinEvent event) {
                // 加载玩家邮件
                mailService.loadMailsForPlayer(event.getPlayer().getUniqueId());

                // 发送未读邮件提示
                String hint = mailService.getUnreadMailHint(event.getPlayer().getUniqueId());
                if (!hint.isEmpty()) {
                    event.getPlayer().sendMessage(hint);
                }
            }

            @org.bukkit.event.EventHandler
            public void onPlayerQuit(org.bukkit.event.player.PlayerQuitEvent event) {
                // 清理玩家邮件缓存
                mailService.cleanup(event.getPlayer().getUniqueId());
            }
        }, plugin);
    }

    /**
     * 注册命令
     */
    private void registerCommands() {
        // 创建邮件命令实例
        MailCommand mailCommand = new MailCommand(mailService);

        bind("mail", mailCommand);
        bind("mailbox", mailCommand);
        bind("email", mailCommand);
        bind("mailgui", mailCommand);

        // 注册邮件附件命令
        if (attachmentService != null) {
            MailAttachmentCommand attachmentCommand = new MailAttachmentCommand(mailService, attachmentService);
            bind("mailattachment", attachmentCommand);
            plugin.getLogger().info("邮件附件命令已注册");
        }
    }

    private void bind(String name, CommandExecutor executor) {
        PluginCommand cmd = plugin.getServer().getPluginCommand(name);
        if (cmd != null) {
            cmd.setExecutor(executor);
            if (executor instanceof TabCompleter tc) {
                cmd.setTabCompleter(tc);
            }
        }
    }

    /**
     * 注册定时保存任务（每5分钟自动保存）
     */
    private void registerPeriodicSave() {
        Bukkit.getScheduler().runTaskTimerAsynchronously(plugin, () -> {
            try {
                mailService.saveAll();
            } catch (Exception e) {
                plugin.getLogger().warning("定时保存邮件数据失败: " + e.getMessage());
            }
        }, 6000L, 6000L); // 5分钟后开始，每5分钟执行
    }

    /**
     * 注册未读邮件定时提示（每10分钟检查）
     */
    private void registerUnreadMailNotifier() {
        Bukkit.getScheduler().runTaskTimer(plugin, () -> {
            for (Player player : Bukkit.getOnlinePlayers()) {
                String hint = mailService.getUnreadMailHint(player.getUniqueId());
                if (!hint.isEmpty()) {
                    // 只在玩家发送消息时提示
                }
            }
        }, 200L, 12000L); // 10秒后开始，每10分钟执行
    }
}
