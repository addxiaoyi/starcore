# StarCore 菜单系统开发者文档

## 架构概述

StarCore 使用**策略模式 + 工厂模式**实现可扩展的菜单系统，支持多种 GUI 框架。

```
NationMenuProvider (接口)
    ├── TriumphChestMenuProvider    (TriumphGUI 箱子界面)
    ├── FallbackChestMenuProvider   (聊天菜单降级方案)
    └── [自定义提供者]               (你的实现)

NationMenuFactory (工厂)
    └── 根据配置选择并创建提供者
```

## 创建自定义菜单提供者

### 1. 实现接口

```java
package dev.starcore.starcore.module.nation.gui;

import org.bukkit.entity.Player;

public class MyCustomMenuProvider implements NationMenuProvider {

    @Override
    public void openMainMenu(Player player) {
        // 打开你的自定义菜单
        // 例如: 使用其他 GUI 框架、网页界面、地图渲染等
    }

    @Override
    public String getProviderType() {
        return "MyCustom Menu System";
    }

    @Override
    public boolean isAvailable() {
        try {
            // 检查依赖是否可用
            Class.forName("your.custom.library.MainClass");
            return true;
        } catch (ClassNotFoundException e) {
            return false;
        }
    }
}
```

### 2. 注册到工厂

修改 `NationMenuFactory.java`:

```java
private NationMenuProvider createSpecificProvider(String type) {
    switch (type) {
        case "triumph":
            return tryCreateTriumph();
        case "mycustom":  // 添加你的类型
            return tryCreateMyCustom();
        case "fallback":
            return new FallbackChestMenuProvider(nationModule, messages);
        default:
            plugin.getLogger().warning("Unknown menu provider type: " + type);
            return null;
    }
}

private NationMenuProvider tryCreateMyCustom() {
    try {
        Class.forName("your.custom.library.MainClass");
        return new MyCustomMenuProvider(nationModule, messages);
    } catch (ClassNotFoundException e) {
        return null;
    }
}
```

### 3. 更新配置

在 `config.yml` 中添加新选项：

```yaml
menu:
  provider: mycustom  # 使用你的提供者
```

## TriumphGUI 菜单示例

### 基础菜单

```java
Gui gui = Gui.gui()
    .title(Component.text("菜单标题", NamedTextColor.GOLD))
    .rows(3)  // 3行 = 27个槽位
    .disableAllInteractions()  // 禁止拖动物品
    .create();

// 添加按钮
gui.setItem(13, createButton(Material.DIAMOND, "点击我", player -> {
    player.sendMessage("你点击了按钮！");
}));

gui.open(player);
```

### 创建按钮

```java
private GuiItem createButton(Material material, String name, Consumer<Player> action) {
    ItemStack item = new ItemStack(material);
    ItemMeta meta = item.getItemMeta();
    
    if (meta != null) {
        // 设置显示名称
        meta.displayName(Component.text(name, NamedTextColor.GREEN));
        
        // 添加描述
        meta.lore(List.of(
            Component.text("这是第一行描述", NamedTextColor.GRAY),
            Component.text("这是第二行描述", NamedTextColor.GRAY)
        ));
        
        item.setItemMeta(meta);
    }
    
    return new GuiItem(item, event -> {
        if (event.getWhoClicked() instanceof Player p) {
            action.accept(p);
        }
    });
}
```

### 分页菜单

```java
PaginatedGui gui = Gui.paginated()
    .title(Component.text("国家列表"))
    .rows(6)
    .pageSize(45)  // 每页显示数量
    .create();

// 添加物品
for (Nation nation : nations) {
    gui.addItem(createNationItem(nation));
}

// 导航按钮
gui.setItem(6, 3, createButton(Material.ARROW, "上一页", p -> gui.previous()));
gui.setItem(6, 7, createButton(Material.ARROW, "下一页", p -> gui.next()));

gui.open(player);
```

## 与 NationModule 交互

### 获取玩家的国家

```java
Optional<Nation> nationOpt = nationModule.getNationByMember(player.getUniqueId());

if (nationOpt.isEmpty()) {
    player.sendMessage(Component.text("你还没有加入国家", NamedTextColor.RED));
    return;
}

Nation nation = nationOpt.get();
```

### 获取国家列表

```java
Collection<Nation> allNations = nationModule.getAllNations();
```

### 获取领土信息

```java
Nation nation = ...;
List<Claim> claims = nationModule.getClaimsByNation(nation.id());
```

### 发送消息

```java
// 使用 MessageService 发送本地化消息
messages.sendMessage(player, "nation.menu.title", nation.name());

// 或直接发送
player.sendMessage(Component.text("消息内容", NamedTextColor.GREEN));
```

## 高级功能

### 动态更新菜单

```java
public class LiveStatsMenu {
    private final Gui gui;
    private BukkitTask updateTask;
    
    public void open(Player player) {
        gui.open(player);
        
        // 每秒更新一次
        updateTask = Bukkit.getScheduler().runTaskTimer(plugin, () -> {
            updateStats();
        }, 0L, 20L);
    }
    
    private void updateStats() {
        // 更新显示的统计数据
        gui.updateItem(13, createStatsItem());
    }
    
    public void close() {
        if (updateTask != null) {
            updateTask.cancel();
        }
    }
}
```

### 确认对话框

```java
private void openConfirmDialog(Player player, String message, Runnable onConfirm) {
    Gui gui = Gui.gui()
        .title(Component.text("确认操作"))
        .rows(3)
        .create();
    
    gui.setItem(12, createButton(Material.GREEN_WOOL, "确认", p -> {
        onConfirm.run();
        p.closeInventory();
    }));
    
    gui.setItem(14, createButton(Material.RED_WOOL, "取消", Player::closeInventory));
    
    gui.open(player);
}
```

### 输入界面（使用 Anvil GUI）

由于 ProtocolLib 类加载限制，目前推荐使用聊天输入：

```java
private void requestInput(Player player, String prompt, Consumer<String> callback) {
    player.sendMessage(Component.text(prompt, NamedTextColor.YELLOW));
    player.sendMessage(Component.text("请在聊天框输入内容", NamedTextColor.GRAY));
    
    // 注册聊天监听器
    // 实现略...
}
```

## 常见问题

### Q: 为什么物品显示名称不生效？

确保使用 Paper 原生 API：

```java
// ❌ 错误（会抛出 UnsupportedOperationException）
ItemBuilder.from(Material.DIAMOND)
    .name(Component.text("名称"))
    .build();

// ✅ 正确
ItemStack item = new ItemStack(Material.DIAMOND);
ItemMeta meta = item.getItemMeta();
meta.displayName(Component.text("名称"));
item.setItemMeta(meta);
```

### Q: 如何防止玩家拿走菜单中的物品？

```java
Gui gui = Gui.gui()
    .disableAllInteractions()  // 禁用所有交互
    .create();
```

或者单独处理：

```java
GuiItem item = new GuiItem(itemStack, event -> {
    event.setCancelled(true);  // 取消事件
    // 你的逻辑
});
```

### Q: 菜单可以播放音效吗？

可以：

```java
GuiItem item = new GuiItem(itemStack, event -> {
    Player player = (Player) event.getWhoClicked();
    player.playSound(player.getLocation(), Sound.UI_BUTTON_CLICK, 1.0f, 1.0f);
    // 你的逻辑
});
```

## 测试菜单

### 单元测试

```java
@Test
public void testMenuProvider() {
    NationMenuProvider provider = new TriumphChestMenuProvider(nationModule, messages);
    
    assertTrue(provider.isAvailable());
    assertEquals("TriumphGUI (Chest)", provider.getProviderType());
}
```

### 手动测试

1. 编译插件: `mvn clean package`
2. 部署到测试服务器
3. 使用测试账号登录
4. 执行 `/menu` 命令
5. 检查菜单是否正常显示

## 性能优化

### 缓存菜单实例

```java
private final Map<UUID, Gui> cachedMenus = new HashMap<>();

public void openMenu(Player player) {
    Gui gui = cachedMenus.computeIfAbsent(player.getUniqueId(), uuid -> createMenu(player));
    gui.open(player);
}
```

### 异步加载数据

```java
public void openMenu(Player player) {
    Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
        // 异步加载数据
        List<Nation> nations = loadNationsFromDatabase();
        
        // 回到主线程更新 GUI
        Bukkit.getScheduler().runTask(plugin, () -> {
            Gui gui = createMenuWithData(nations);
            gui.open(player);
        });
    });
}
```

## 参考资源

- [TriumphGUI 官方文档](https://triumphteam.dev/library/triumph-gui/)
- [Paper API 文档](https://docs.papermc.io/)
- [Adventure API 文档](https://docs.adventure.kyori.net/)

## 贡献

欢迎提交 Pull Request 添加新的菜单提供者或改进现有实现！

---

**文档版本**: 1.0  
**最后更新**: 2026-06-22
