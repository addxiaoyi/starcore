package dev.starcore.starcore.module.nation.gui;

import com.comphenix.protocol.PacketType;
import com.comphenix.protocol.ProtocolLibrary;
import com.comphenix.protocol.events.PacketAdapter;
import com.comphenix.protocol.events.PacketContainer;
import com.comphenix.protocol.events.PacketEvent;
import com.comphenix.protocol.wrappers.WrappedChatComponent;
import dev.starcore.starcore.module.nation.NationModule;
import dev.starcore.starcore.module.nation.model.Nation;
import dev.starcore.starcore.foundation.message.MessageService;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.Plugin;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;

/**
 * ProtocolLib Anvil GUI menu provider - uses native anvil interface
 */
public class ProtocolLibAnvilMenuProvider implements NationMenuProvider {

    private final NationModule nationModule;
    private final MessageService messageService;
    private final Plugin plugin;
    private final Map<UUID, AnvilSession> activeSessions = new ConcurrentHashMap<>();
    private int nextWindowId = 100;

    public ProtocolLibAnvilMenuProvider(NationModule nationModule, MessageService messageService, Plugin plugin) {
        this.nationModule = nationModule;
        this.messageService = messageService;
        this.plugin = plugin;
        registerPacketListener();
    }

    private void registerPacketListener() {
        ProtocolLibrary.getProtocolManager().addPacketListener(new PacketAdapter(
                plugin,
                PacketType.Play.Client.ITEM_NAME
        ) {
            @Override
            public void onPacketReceiving(PacketEvent event) {
                Player player = event.getPlayer();
                AnvilSession session = activeSessions.get(player.getUniqueId());

                if (session != null) {
                    String input = event.getPacket().getStrings().read(0);

                    // Schedule for next tick to avoid threading issues
                    plugin.getServer().getScheduler().runTask(plugin, () -> {
                        session.callback.accept(input);
                        activeSessions.remove(player.getUniqueId());
                        player.closeInventory();
                    });
                }
            }
        });
    }

    @Override
    public void openMainMenu(Player player) {
        player.sendMessage(Component.text("=== 国家系统菜单 ===", NamedTextColor.GOLD));
        player.sendMessage(Component.text("请选择操作：", NamedTextColor.YELLOW));
        player.sendMessage(Component.text("1. 创建国家 - /nation menu create", NamedTextColor.GREEN));
        player.sendMessage(Component.text("2. 加入国家 - /nation menu join", NamedTextColor.GREEN));
        player.sendMessage(Component.text("3. 查看我的国家 - /nation info", NamedTextColor.GREEN));
        player.sendMessage(Component.text("4. 国家管理 - /nation menu manage", NamedTextColor.GREEN));
        player.sendMessage(Component.text("5. 领地管理 - /nation menu claim", NamedTextColor.GREEN));
        player.sendMessage(Component.text("==================", NamedTextColor.GOLD));
    }

    @Override
    public void openSubMenu(Player player, Nation nation, String submenuId) {
        player.sendMessage(Component.text("⚠ 子菜单功能暂不可用，请使用命令操作", NamedTextColor.RED));
    }

    public void openCreateNationAnvil(Player player) {
        openAnvilInput(player, "输入国家名称", input -> {
            if (input != null && !input.trim().isEmpty()) {
                plugin.getServer().dispatchCommand(player, "nation create " + input.trim());
            } else {
                player.sendMessage(Component.text("国家名称不能为空", NamedTextColor.RED));
            }
        });
    }

    public void openJoinNationAnvil(Player player) {
        openAnvilInput(player, "输入国家名称", input -> {
            if (input != null && !input.trim().isEmpty()) {
                plugin.getServer().dispatchCommand(player, "nation join " + input.trim());
            } else {
                player.sendMessage(Component.text("国家名称不能为空", NamedTextColor.RED));
            }
        });
    }

    public void openInviteMemberAnvil(Player player) {
        openAnvilInput(player, "输入玩家名称", input -> {
            if (input != null && !input.trim().isEmpty()) {
                plugin.getServer().dispatchCommand(player, "nation invite " + input.trim());
            } else {
                player.sendMessage(Component.text("玩家名称不能为空", NamedTextColor.RED));
            }
        });
    }

    public void openKickMemberAnvil(Player player) {
        openAnvilInput(player, "输入玩家名称", input -> {
            if (input != null && !input.trim().isEmpty()) {
                plugin.getServer().dispatchCommand(player, "nation kick " + input.trim());
            } else {
                player.sendMessage(Component.text("玩家名称不能为空", NamedTextColor.RED));
            }
        });
    }

    public void openTransferOwnershipAnvil(Player player, Runnable afterSuccess) {
        openAnvilInput(player, "输入玩家名称", input -> {
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
        });
    }

    private void openAnvilInput(Player player, String title, Consumer<String> callback) {
        try {
            int windowId = nextWindowId++;
            AnvilSession session = new AnvilSession(windowId, callback);
            activeSessions.put(player.getUniqueId(), session);

            // Create open window packet
            PacketContainer openWindow = ProtocolLibrary.getProtocolManager()
                    .createPacket(PacketType.Play.Server.OPEN_WINDOW);

            openWindow.getIntegers().write(0, windowId);
            openWindow.getStrings().write(0, "minecraft:anvil");
            openWindow.getChatComponents().write(0, WrappedChatComponent.fromText(title));

            // Send open window packet
            ProtocolLibrary.getProtocolManager().sendServerPacket(player, openWindow);

            // Create window items packet
            PacketContainer windowItems = ProtocolLibrary.getProtocolManager()
                    .createPacket(PacketType.Play.Server.WINDOW_ITEMS);

            windowItems.getIntegers().write(0, windowId);

            ItemStack[] items = new ItemStack[3];
            items[0] = new ItemStack(Material.PAPER); // Input slot
            items[1] = null; // Material slot (not used)
            items[2] = null; // Output slot

            windowItems.getItemArrayModifier().write(0, items);

            // Send window items packet
            ProtocolLibrary.getProtocolManager().sendServerPacket(player, windowItems);

        } catch (Exception e) {
            player.sendMessage(Component.text("无法打开输入界面", NamedTextColor.RED));
            plugin.getLogger().warning("Failed to open anvil GUI: " + e.getMessage());
            activeSessions.remove(player.getUniqueId());
        }
    }

    @Override
    public String getProviderType() {
        return "ProtocolLib AnvilGUI";
    }

    @Override
    public boolean isAvailable() {
        try {
            Class.forName("com.comphenix.protocol.ProtocolLibrary");
            org.bukkit.plugin.Plugin protocolLib = plugin.getServer().getPluginManager().getPlugin("ProtocolLib");
            boolean enabled = protocolLib != null && protocolLib.isEnabled();

            plugin.getLogger().info("[DEBUG] ProtocolLib availability check: plugin=" + protocolLib +
                ", enabled=" + (protocolLib != null ? protocolLib.isEnabled() : "null") +
                ", result=" + enabled);

            return enabled;
        } catch (ClassNotFoundException e) {
            plugin.getLogger().warning("ProtocolLib class not found: " + e.getMessage());
            return false;
        } catch (Exception e) {
            plugin.getLogger().warning("ProtocolLib check failed: " + e.getClass().getName() + ": " + e.getMessage());
            return false;
        }
    }

    private static class AnvilSession {
        final int windowId;
        final Consumer<String> callback;

        AnvilSession(int windowId, Consumer<String> callback) {
            this.windowId = windowId;
            this.callback = callback;
        }
    }
}
