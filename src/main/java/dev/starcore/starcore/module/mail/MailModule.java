package dev.starcore.starcore.module.mail;

import dev.starcore.starcore.core.StarCoreContext;
import dev.starcore.starcore.core.database.DatabaseService;
import dev.starcore.starcore.core.module.ModuleLayer;
import dev.starcore.starcore.core.module.ModuleMetadata;
import dev.starcore.starcore.core.module.StarCoreModule;
import dev.starcore.starcore.core.persistence.PersistenceService;
import dev.starcore.starcore.core.scheduler.StarCoreScheduler;
import dev.starcore.starcore.foundation.message.MessageService;
import dev.starcore.starcore.module.mail.attachment.AttachmentCommand;
import dev.starcore.starcore.module.mail.attachment.AttachmentGui;
import dev.starcore.starcore.module.mail.attachment.MailAttachmentService;
import dev.starcore.starcore.module.mail.attachment.MailAttachmentServiceImpl;
import org.bukkit.plugin.Plugin;

import java.util.List;

/**
 * 邮件模块
 * 提供玩家间邮件通信和附件功能
 */
public final class MailModule implements StarCoreModule, MailAttachmentService {

    private static final ModuleMetadata METADATA = new ModuleMetadata(
        "mail",
        "邮件系统",
        ModuleLayer.MODULE,
        List.of(),
        List.of(MailAttachmentService.class),
        "Provides player mail system with attachments."
    );

    private Plugin plugin;
    private DatabaseService databaseService;
    private StarCoreScheduler scheduler;
    private MessageService messages;
    private PersistenceService persistenceService;
    private MailAttachmentServiceImpl mailService;
    private AttachmentCommand attachmentCommand;
    private AttachmentGui attachmentGui;

    @Override
    public ModuleMetadata metadata() {
        return METADATA;
    }

    @Override
    public void enable(StarCoreContext context) {
        this.plugin = context.plugin();
        this.databaseService = context.databaseService();
        this.scheduler = context.scheduler();
        this.messages = context.serviceRegistry().require(MessageService.class);
        this.persistenceService = context.serviceRegistry().find(PersistenceService.class).orElse(null);

        // 初始化邮件服务（内部会初始化存储）
        this.mailService = new MailAttachmentServiceImpl(
            databaseService,
            scheduler,
            plugin,
            plugin.getLogger()
        );

        // 注册服务
        context.serviceRegistry().register(MailAttachmentService.class, this);

        // 初始化GUI
        this.attachmentGui = new AttachmentGui(plugin, mailService);

        // 初始化命令
        this.attachmentCommand = new AttachmentCommand(mailService);

        // 注册事件监听器
        plugin.getServer().getPluginManager().registerEvents(attachmentGui, plugin);

        // 注册命令
        registerCommand();

        plugin.getLogger().info("邮件模块已启用");
    }

    private void registerCommand() {
        var command = plugin.getServer().getPluginCommand("mail");
        if (command != null) {
            command.setExecutor(attachmentCommand);
            command.setTabCompleter(attachmentCommand);
        }
    }

    @Override
    public void disable(StarCoreContext context) {
        // 保存数据
        if (mailService != null) {
            mailService.saveAll();
        }
        plugin.getLogger().info("邮件模块已禁用");
    }

    // ==================== MailAttachmentService 实现 ====================

    @Override
    public boolean sendMailWithAttachment(org.bukkit.entity.Player sender, String recipient, String subject,
                                          String message, java.util.List<org.bukkit.inventory.ItemStack> items) {
        return mailService.sendMailWithAttachment(sender, recipient, subject, message, items);
    }

    @Override
    public java.util.concurrent.CompletableFuture<Boolean> sendMailWithAttachmentAsync(
            org.bukkit.entity.Player sender, String recipient, String subject,
            String message, java.util.List<org.bukkit.inventory.ItemStack> items) {
        return mailService.sendMailWithAttachmentAsync(sender, recipient, subject, message, items);
    }

    @Override
    public java.util.List<org.bukkit.inventory.ItemStack> getMailAttachments(java.util.UUID mailId) {
        return mailService.getMailAttachments(mailId);
    }

    @Override
    public MailAttachmentService.AttachmentClaimResult claimAttachment(org.bukkit.entity.Player player, java.util.UUID mailId) {
        return mailService.claimAttachment(player, mailId);
    }

    @Override
    public java.util.concurrent.CompletableFuture<MailAttachmentService.AttachmentClaimResult> claimAttachmentAsync(
            org.bukkit.entity.Player player, java.util.UUID mailId) {
        return mailService.claimAttachmentAsync(player, mailId);
    }

    @Override
    public java.util.List<MailAttachmentService.AttachmentClaimResult> claimAllAttachments(org.bukkit.entity.Player player) {
        return mailService.claimAllAttachments(player);
    }

    @Override
    public boolean deleteMail(org.bukkit.entity.Player player, java.util.UUID mailId) {
        return mailService.deleteMail(player, mailId);
    }

    @Override
    public int getUnreadCount(java.util.UUID playerId) {
        return mailService.getUnreadCount(playerId);
    }

    @Override
    public java.util.List<MailAttachmentService.MailAttachment> getPlayerMails(java.util.UUID playerId) {
        return mailService.getPlayerMails(playerId);
    }

    @Override
    public int getUnclaimedAttachmentCount(java.util.UUID playerId) {
        return mailService.getUnclaimedAttachmentCount(playerId);
    }

    /**
     * 内部获取邮件服务（供命令使用）
     */
    public MailAttachmentServiceImpl getMailService() {
        return mailService;
    }

    /**
     * 内部获取GUI（供命令使用）
     */
    public AttachmentGui getAttachmentGui() {
        return attachmentGui;
    }
}
