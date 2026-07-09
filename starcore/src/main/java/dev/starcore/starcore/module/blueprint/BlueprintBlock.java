package dev.starcore.starcore.module.blueprint;

import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.BlockState;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.io.Serializable;
import java.util.Objects;

/**
 * 方块数据
 * 存储蓝图中的单个方块信息
 */
public class BlueprintBlock implements Serializable {
    private static final long serialVersionUID = 1L;

    private final int x;
    private final int y;
    private final int z;
    private final String material;
    private final byte data;
    private final String blockState; // NBT数据序列化
    private final String tileEntityData; // 方块实体数据

    public BlueprintBlock(int x, int y, int z, Material material, byte data) {
        this.x = x;
        this.y = y;
        this.z = z;
        this.material = material.name();
        this.data = data;
        this.blockState = null;
        this.tileEntityData = null;
    }

    public BlueprintBlock(int x, int y, int z, Material material, byte data, String blockState) {
        this.x = x;
        this.y = y;
        this.z = z;
        this.material = material.name();
        this.data = data;
        this.blockState = blockState;
        this.tileEntityData = null;
    }

    public BlueprintBlock(int x, int y, int z, String material, byte data, String blockState, String tileEntityData) {
        this.x = x;
        this.y = y;
        this.z = z;
        this.material = material;
        this.data = data;
        this.blockState = blockState;
        this.tileEntityData = tileEntityData;
    }

    public static BlueprintBlock fromBlock(Block block) {
        BlockState state = block.getState();
        Material material = block.getType();
        byte data = block.getBlockData().getAsString().getBytes()[0]; // 简单处理

        String tileEntityData = null;
        // 如果是方块实体，尝试获取其数据
        if (state instanceof org.bukkit.block.Sign sign) {
            tileEntityData = serializeSignLines(sign);
        } else if (state instanceof org.bukkit.block.Chest chest) {
            tileEntityData = serializeChestItems(chest);
        } else if (state instanceof org.bukkit.block.Container container) {
            tileEntityData = serializeContainerItems(container);
        }

        return new BlueprintBlock(
            block.getX(),
            block.getY(),
            block.getZ(),
            material.name(),
            data,
            serializeBlockState(state),
            tileEntityData
        );
    }

    public int getX() {
        return x;
    }

    public int getY() {
        return y;
    }

    public int getZ() {
        return z;
    }

    public int getRelativeX() {
        return x;
    }

    public int getRelativeY() {
        return y;
    }

    public int getRelativeZ() {
        return z;
    }

    public Material getMaterial() {
        try {
            return Material.valueOf(material);
        } catch (IllegalArgumentException e) {
            return Material.AIR;
        }
    }

    public String getMaterialName() {
        return material;
    }

    public byte getData() {
        return data;
    }

    public String getBlockState() {
        return blockState;
    }

    public String getTileEntityData() {
        return tileEntityData;
    }

    public BlockVector3 getPosition() {
        return BlockVector3.at(x, y, z);
    }

    public boolean hasBlockState() {
        return blockState != null && !blockState.isEmpty();
    }

    public boolean hasTileEntityData() {
        return tileEntityData != null && !tileEntityData.isEmpty();
    }

    /**
     * 将方块应用到世界
     */
    public boolean applyToWorld(World world, int offsetX, int offsetY, int offsetZ) {
        try {
            int targetX = x + offsetX;
            int targetY = y + offsetY;
            int targetZ = z + offsetZ;

            Block block = world.getBlockAt(targetX, targetY, targetZ);
            Material mat = getMaterial();

            if (mat == Material.AIR || mat == Material.CAVE_AIR || mat == Material.VOID_AIR) {
                block.setType(Material.AIR);
                return true;
            }

            block.setType(mat, false);
            // Note: In Paper 1.21+, use Block.setBlockData() instead of setData()
            try {
                org.bukkit.block.data.BlockData blockData = Bukkit.createBlockData(mat);
                block.setBlockData(blockData, false);
            } catch (Exception e) {
                block.setType(mat, false);
            }

            // 恢复方块实体数据
            if (hasTileEntityData()) {
                applyTileEntityData(block);
            }

            return true;
        } catch (Exception e) {
            return false;
        }
    }

    /**
     * 恢复到原始位置
     */
    public boolean applyToWorld(World world) {
        return applyToWorld(world, 0, 0, 0);
    }

    private void applyTileEntityData(Block block) {
        BlockState state = block.getState();

        if (tileEntityData != null && tileEntityData.startsWith("SIGN:")) {
            if (state instanceof org.bukkit.block.Sign sign) {
                String[] lines = tileEntityData.substring(5).split("\\|", 4);
                for (int i = 0; i < Math.min(lines.length, 4); i++) {
                    org.bukkit.ChatColor.translateAlternateColorCodes('&', lines[i]);
                    sign.line(i, net.kyori.adventure.text.Component.text(lines[i]));
                }
                sign.update();
            }
        } else if (tileEntityData != null && tileEntityData.startsWith("CHEST:")) {
            if (state instanceof org.bukkit.block.Chest chest) {
                applyChestItems(chest, tileEntityData.substring(6));
            }
        }
    }

    private static String serializeSignLines(org.bukkit.block.Sign sign) {
        StringBuilder sb = new StringBuilder("SIGN:");
        for (int i = 0; i < 4; i++) {
            if (i > 0) sb.append("|");
            net.kyori.adventure.text.Component line = sign.line(i);
            if (line != null) {
                sb.append(net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer.legacySection().serialize(line));
            }
        }
        return sb.toString();
    }

    private static String serializeChestItems(org.bukkit.block.Chest chest) {
        StringBuilder sb = new StringBuilder("CHEST:");
        org.bukkit.inventory.ItemStack[] items = chest.getInventory().getContents();
        boolean hasItems = false;
        for (int i = 0; i < items.length; i++) {
            if (items[i] != null && items[i].getType() != Material.AIR) {
                hasItems = true;
                sb.append(i).append(":").append(items[i].getType().name())
                  .append(":").append(items[i].getAmount());
                if (items[i].hasItemMeta()) {
                    sb.append(":").append(items[i].getItemMeta().hasDisplayName());
                }
                sb.append(";");
            }
        }
        return hasItems ? sb.toString() : null;
    }

    private static String serializeContainerItems(org.bukkit.block.Container container) {
        StringBuilder sb = new StringBuilder("CONTAINER:");
        org.bukkit.inventory.ItemStack[] items = container.getInventory().getContents();
        for (int i = 0; i < items.length; i++) {
            if (items[i] != null && items[i].getType() != Material.AIR) {
                sb.append(i).append(":").append(items[i].getType().name())
                  .append(":").append(items[i].getAmount()).append(";");
            }
        }
        return sb.length() > 10 ? sb.toString() : null;
    }

    private static void applyChestItems(org.bukkit.block.Chest chest, String data) {
        if (data == null || data.isEmpty()) return;
        try {
            String[] items = data.split(";");
            for (String item : items) {
                String[] parts = item.split(":");
                if (parts.length >= 3) {
                    int slot = Integer.parseInt(parts[0]);
                    Material mat = Material.valueOf(parts[1]);
                    int amount = Integer.parseInt(parts[2]);
                    org.bukkit.inventory.ItemStack stack = new org.bukkit.inventory.ItemStack(mat, amount);
                    chest.getInventory().setItem(slot, stack);
                }
            }
            chest.update();
        } catch (Exception ignored) {
        }
    }

    private static String serializeBlockState(BlockState state) {
        // 简单序列化：直接使用类名
        return state.getClass().getSimpleName();
    }

    /**
     * 创建蓝图方块的物品表示
     */
    public ItemStack toItemStack() {
        ItemStack item = new ItemStack(getMaterial());
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.displayName(net.kyori.adventure.text.Component.text("Blueprint Block: " + material));
            item.setItemMeta(meta);
        }
        return item;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        BlueprintBlock that = (BlueprintBlock) o;
        return x == that.x && y == that.y && z == that.z;
    }

    @Override
    public int hashCode() {
        return Objects.hash(x, y, z);
    }

    @Override
    public String toString() {
        return "BlueprintBlock{" +
               "pos=(" + x + "," + y + "," + z + ")" +
               ", material=" + material +
               ", data=" + data +
               '}';
    }
}
