package dev.starcore.starcore.module.nation.gui;

import com.github.retrooper.packetevents.PacketEvents;
import com.github.retrooper.packetevents.event.PacketListenerAbstract;
import com.github.retrooper.packetevents.event.PacketListenerPriority;
import com.github.retrooper.packetevents.event.PacketReceiveEvent;
import com.github.retrooper.packetevents.protocol.packettype.PacketType;
import com.github.retrooper.packetevents.wrapper.play.client.WrapperPlayClientClickWindow;
import com.github.retrooper.packetevents.wrapper.play.client.WrapperPlayClientCloseWindow;
import com.github.retrooper.packetevents.wrapper.play.client.WrapperPlayClientNameItem;
import com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerOpenWindow;
import com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerSetSlot;
import com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerWindowItems;
import io.github.retrooper.packetevents.util.SpigotConversionUtil;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.plugin.Plugin;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;

/**
 * PacketEvents Anvil GUI 提供者
 * 使用数据包实现原生铁砧重命名界面
 */
public class PacketEventsAnvilProvider {
    private final Plugin plugin;
    private final Map<UUID, AnvilSession> sessions = new ConcurrentHashMap<>();
    private final AnvilPacketListener listener;

    // Anvil 容器类型 (Paper 1.20.5+)
    private static final int ANVIL_CONTAINER_TYPE = 7;

    public PacketEventsAnvilProvider(Plugin plugin) {
        this.plugin = plugin;
        this.listener = new AnvilPacketListener();
        PacketEvents.getAPI().getEventManager().registerListener(listener);
        plugin.getLogger().info("PacketEventsAnvilProvider initialized");
    }

    /**
     * 打开 Anvil 输入界面
     *
     * @param player      玩家
     * @param title       铁砧界面标题
     * @param defaultText 默认文本（预填充）
     * @param onConfirm   确认回调（参数为玩家输入的文本）
     * @param onCancel    取消回调
     */
    public void openAnvilInput(Player player, String title, String defaultText,
                              Consumer<String> onConfirm, Runnable onCancel) {

        sessions.remove(player.getUniqueId());

        AnvilSession session = new AnvilSession(
            player.getUniqueId(),
            defaultText != null ? defaultText : "",
            onConfirm,
            onCancel
        );
        sessions.put(player.getUniqueId(), session);

        // 1. 发送打开 Anvil 窗口数据包
        WrapperPlayServerOpenWindow openPacket = new WrapperPlayServerOpenWindow(
            session.windowId,
            ANVIL_CONTAINER_TYPE,
            Component.text(title)
        );
        PacketEvents.getAPI().getProtocolManager().sendPacket(player, openPacket);

        // 2. 创建左槽物品（预填充文本的纸）
        ItemStack leftItem = new ItemStack(Material.PAPER);
        ItemMeta meta = leftItem.getItemMeta();
        if (meta != null) {
            meta.displayName(Component.text(session.initialText));
            leftItem.setItemMeta(meta);
        }

        var peLeftItem = SpigotConversionUtil.fromBukkitItemStack(leftItem);

        // 3. 发送物品到左槽（槽位 0）
        WrapperPlayServerSetSlot setSlotPacket = new WrapperPlayServerSetSlot(
            session.windowId,
            0,
            0,
            peLeftItem
        );
        PacketEvents.getAPI().getProtocolManager().sendPacket(player, setSlotPacket);

        // 4. 发送窗口物品列表（确保客户端正确渲染）
        List<com.github.retrooper.packetevents.protocol.item.ItemStack> items = new ArrayList<>();
        items.add(peLeftItem);
        items.add(null);
        items.add(null);
        WrapperPlayServerWindowItems windowItemsPacket = new WrapperPlayServerWindowItems(
            session.windowId,
            0,
            items,
            com.github.retrooper.packetevents.protocol.item.ItemStack.EMPTY
        );
        PacketEvents.getAPI().getProtocolManager().sendPacket(player, windowItemsPacket);

        plugin.getLogger().info("Anvil GUI opened for " + player.getName() + ", initialText: '" + session.initialText + "'");
    }

    /**
     * 检查玩家是否有活动会话
     */
    public boolean hasSession(UUID playerId) {
        return sessions.containsKey(playerId);
    }

    /**
     * 获取玩家的当前输入文本
     */
    public String getCurrentText(UUID playerId) {
        AnvilSession session = sessions.get(playerId);
        return session != null ? session.currentText : "";
    }

    /**
     * 更新玩家的当前输入文本
     */
    public void updateCurrentText(UUID playerId, String text) {
        AnvilSession session = sessions.get(playerId);
        if (session != null) {
            session.currentText = text;
        }
    }

    /**
     * 关闭并清理会话
     */
    public void closeSession(UUID playerId) {
        sessions.remove(playerId);
    }

    /**
     * 打开转让所有权 Anvil 输入
     */
    public void openTransferOwnershipAnvil(Player player, Runnable afterSuccess) {
        openAnvilInput(player, "§6§l转让国家所有权", null, input -> {
            if (input != null && !input.trim().isEmpty()) {
                plugin.getServer().dispatchCommand(player, "nation transfer " + input.trim());
                if (afterSuccess != null) {
                    plugin.getServer().getScheduler().runTaskLater(plugin, afterSuccess, 1L);
                }
            } else {
                player.sendMessage(Component.text("玩家名称不能为空", NamedTextColor.RED));
                if (afterSuccess != null) {
                    plugin.getServer().getScheduler().runTaskLater(plugin, afterSuccess, 1L);
                }
            }
        }, () -> {
            player.sendMessage(Component.text("已取消转让", NamedTextColor.YELLOW));
        });
    }

    /**
     * 卸载监听器
     */
    public void unregister() {
        if (listener != null) {
            PacketEvents.getAPI().getEventManager().unregisterListener(listener);
        }
        sessions.clear();
    }

    /**
     * Anvil 会话
     */
    private static class AnvilSession {
        final UUID playerId;
        final int windowId;
        final String initialText;
        final Consumer<String> onConfirm;
        final Runnable onCancel;
        String currentText;

        AnvilSession(UUID playerId, String initialText, Consumer<String> onConfirm, Runnable onCancel) {
            this.playerId = playerId;
            this.windowId = 100;
            this.initialText = initialText;
            this.currentText = initialText;
            this.onConfirm = onConfirm;
            this.onCancel = onCancel;
        }
    }

    /**
     * 数据包监听器
     */
    private class AnvilPacketListener extends PacketListenerAbstract {

        public AnvilPacketListener() {
            super(PacketListenerPriority.HIGH);
        }

        @Override
        public void onPacketReceive(PacketReceiveEvent event) {
            if (!(event.getPlayer() instanceof Player player)) {
                return;
            }

            UUID playerId = player.getUniqueId();
            AnvilSession session = sessions.get(playerId);

            if (session == null) {
                return;
            }

            // 监听重命名（Paper 1.20.5+: NAME_ITEM）
            if (event.getPacketType() == PacketType.Play.Client.NAME_ITEM) {
                event.setCancelled(true);
                try {
                    WrapperPlayClientNameItem namePacket = new WrapperPlayClientNameItem(event);
                    String renamedText = namePacket.getItemName();
                    session.currentText = renamedText != null ? renamedText : "";
                    plugin.getLogger().fine("Anvil input updated: '" + session.currentText + "'");
                } catch (Exception e) {
                    plugin.getLogger().warning("Failed to read NAME_ITEM: " + e.getMessage());
                }
            }

            // 监听窗口点击（确认输出槽）
            else if (event.getPacketType() == PacketType.Play.Client.CLICK_WINDOW) {
                WrapperPlayClientClickWindow clickPacket = new WrapperPlayClientClickWindow(event);

                if (clickPacket.getWindowId() == session.windowId && clickPacket.getSlot() == 2) {
                    event.setCancelled(true);

                    String finalText = session.currentText;
                    Consumer<String> callback = session.onConfirm;
                    Runnable cancelCallback = session.onCancel;

                    // 关闭会话
                    sessions.remove(playerId);

                    plugin.getServer().getScheduler().runTask(plugin, () -> {
                        player.closeInventory();
                        callback.accept(finalText);
                    });
                }
            }

            // 监听窗口关闭（取消）
            else if (event.getPacketType() == PacketType.Play.Client.CLOSE_WINDOW) {
                WrapperPlayClientCloseWindow closePacket = new WrapperPlayClientCloseWindow(event);

                if (closePacket.getWindowId() == session.windowId) {
                    Runnable cancelCallback = session.onCancel;

                    sessions.remove(playerId);

                    plugin.getServer().getScheduler().runTask(plugin, () -> {
                        cancelCallback.run();
                    });
                }
            }
        }
    }
}
