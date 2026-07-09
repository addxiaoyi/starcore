# StarCore GUI 系统重构计划

## 🎯 目标

实现混合 GUI 系统，结合 TriumphGUI 箱子界面和 PacketEvents Anvil 输入框，提供最佳的原生体验。

---

## 📋 技术方案

### 方案概述

**主菜单系统**: TriumphGUI Chest GUI（箱子界面）  
**文本输入系统**: PacketEvents Anvil GUI（铁砧重命名界面）

### 为什么这样设计？

1. **Chest GUI 适合选择操作**
   - 直观的物品点击
   - 可以展示图标、名称、描述
   - 适合多选项菜单

2. **Anvil GUI 适合文本输入**
   - 原生输入体验
   - 无需客户端 Mod
   - 适合输入国家名称、金额等

3. **混合使用最优体验**
   - 像 ExcellentCrates 一样的实现方式
   - 充分利用两种 GUI 的优势

---

## 🏗️ 架构设计

### 1. GUI 提供者接口扩展

```java
public interface NationMenuProvider {
    // 打开主菜单（Chest GUI）
    void openMainMenu(Player player);
    
    // 打开文本输入（Anvil GUI）
    void openTextInput(Player player, String prompt, Consumer<String> callback);
    
    // 提供者类型
    String getProviderType();
    
    // 是否可用
    boolean isAvailable();
}
```

### 2. 实现类

#### TriumphChestMenuProvider (已有)
- 负责主菜单和所有选择操作
- 使用 TriumphGUI 箱子界面

#### PacketEventsAnvilProvider (新增)
- 负责所有文本输入操作
- 使用 PacketEvents 发送 Anvil GUI 数据包
- 监听玩家输入响应

---

## 📐 GUI 布局设计

### 主菜单布局（Chest GUI - 54格）

```
╔════════════════════════════════════════════════╗
║  [国家信息] [成员管理] [财政管理] [领土] [外交] ║  第1行：核心功能
║                                                ║
║  [国策树]   [科技树]   [军队]   [事件] [决议]  ║  第2行：发展系统
║                                                ║
║  [改名]     [设置]     [统计]   [帮助]         ║  第3行：工具功能
║                                                ║
║           [上一页]            [下一页]         ║  最后一行：导航
╚════════════════════════════════════════════════╝
```

### 需要 Anvil 输入的场景

1. **国家重命名**
   - 提示: "请输入新的国家名称"
   - 验证: 2-16字符，无特殊符号

2. **邀请玩家**
   - 提示: "请输入要邀请的玩家名称"
   - 验证: 在线玩家

3. **金额输入**
   - 提示: "请输入金额"
   - 验证: 正整数

4. **搜索功能**
   - 提示: "搜索成员、领土等..."
   - 无验证，纯搜索

---

## 🔧 实现步骤

### Phase 1: PacketEvents 集成 ✅

**任务:**
- [ ] 添加 PacketEvents 依赖到 pom.xml
- [ ] 创建 PacketEventsAnvilProvider 类
- [ ] 实现 Anvil GUI 数据包发送
- [ ] 实现输入响应监听

**文件:**
- `pom.xml` - 添加依赖
- `PacketEventsAnvilProvider.java` - 新建
- `AnvilInputCallback.java` - 回调接口

**技术点:**
- 使用 `PacketType.Play.Server.OPEN_WINDOW` 发送 Anvil 窗口
- 使用 `PacketType.Play.Client.WINDOW_CLICK` 监听点击
- 使用 `PacketType.Play.Client.RENAME_ITEM` 监听重命名

---

### Phase 2: TriumphGUI 增强 ✅

**任务:**
- [ ] 重新设计 TriumphChestMenuProvider
- [ ] 实现主菜单布局（54格）
- [ ] 添加所有功能按钮
- [ ] 集成 Anvil 输入回调

**文件:**
- `TriumphChestMenuProvider.java` - 重构
- `NationMenuItems.java` - 物品定义
- `MenuLayoutConfig.java` - 布局配置

**功能清单:**
```java
主菜单:
  - 国家信息 (查看) → Chest GUI 子页面
  - 成员管理 (点击) → Chest GUI 子页面
    - 添加成员 → Anvil 输入玩家名
  - 财政管理 (点击) → Chest GUI 子页面
    - 存款 → Anvil 输入金额
    - 取款 → Anvil 输入金额
  - 领土管理 (点击) → Chest GUI 子页面
  - 外交关系 (点击) → Chest GUI 子页面
  - 国家改名 (点击) → Anvil 输入新名称
```

---

### Phase 3: 菜单工厂更新 ✅

**任务:**
- [ ] 更新 NationMenuFactory
- [ ] 移除 NightCore Dialog 支持
- [ ] 设置 TriumphGUI 为默认

**修改:**
```java
public NationMenuProvider createProvider(String preferredType) {
    // 优先使用 TriumphGUI
    if (type.equals("auto") || type.equals("triumph")) {
        return new TriumphChestMenuProvider(nationModule, messages, anvilProvider);
    }
    
    // Fallback
    return new FallbackChestMenuProvider(nationModule, messages);
}
```

---

### Phase 4: 配置文件更新 ✅

**任务:**
- [ ] 更新 config.yml 菜单配置
- [ ] 添加 GUI 布局配置
- [ ] 添加物品材质配置

**config.yml:**
```yaml
menu:
  # 菜单系统类型
  provider: auto  # auto, triumph, fallback
  
  # 主菜单设置
  main-menu:
    title: "§6§l{nation_name} §8| §7国家管理"
    size: 54  # 6行
    
  # 物品图标配置
  icons:
    nation-info:
      material: PLAYER_HEAD
      name: "§e§l国家信息"
      lore:
        - "§7点击查看国家详情"
    
    member-management:
      material: PLAYER_HEAD
      name: "§b§l成员管理"
      lore:
        - "§7管理国家成员"
        - "§7添加、移除、设置权限"
    
    treasury:
      material: GOLD_INGOT
      name: "§6§l财政管理"
      lore:
        - "§7国库余额: §e{balance}"
        - "§7点击进行存取款"
    
    # ... 更多图标配置
```

---

### Phase 5: 测试与优化 ⏳

**任务:**
- [ ] 单元测试：Anvil 输入验证
- [ ] 集成测试：完整菜单流程
- [ ] 性能测试：大量玩家同时打开菜单
- [ ] UI/UX 测试：用户体验优化

**测试用例:**
1. 打开主菜单 → 验证布局正确
2. 点击"改名" → Anvil 输入 → 验证改名成功
3. 点击"添加成员" → Anvil 输入 → 验证邀请发送
4. 点击"存款" → Anvil 输入金额 → 验证余额变化
5. 点击"取款" → Anvil 输入金额 → 验证余额变化

---

## 📦 依赖管理

### 添加 PacketEvents 到 pom.xml

```xml
<repositories>
    <repository>
        <id>codemc-snapshots</id>
        <url>https://repo.codemc.io/repository/maven-snapshots/</url>
    </repository>
</repositories>

<dependencies>
    <!-- PacketEvents -->
    <dependency>
        <groupId>com.github.retrooper</groupId>
        <artifactId>packetevents-spigot</artifactId>
        <version>2.5.0</version>
        <scope>provided</scope>
    </dependency>
</dependencies>
```

### plugin.yml 依赖声明

```yaml
depend:
  - PacketEvents  # 必需
softdepend:
  - PlaceholderAPI
  - Vault
```

---

## 🎨 用户体验设计

### 交互流程示例

#### 场景 1: 国家改名

```
1. 玩家执行 /menu
2. 打开 Chest GUI 主菜单（54格箱子界面）
3. 点击"改名"物品（金色告示牌）
4. 关闭 Chest GUI
5. 打开 Anvil GUI（铁砧界面）
   - 左槽: 纸（物品名: 当前国家名）
   - 输入框: 预填充当前名称
   - 输出槽: 金色告示牌（物品名: "确认改名"）
6. 玩家在输入框输入新名称
7. 点击输出槽确认
8. 关闭 Anvil GUI
9. 显示成功消息
10. 重新打开 Chest GUI 主菜单
```

#### 场景 2: 添加成员

```
1. 主菜单 → 点击"成员管理"
2. 打开成员管理子菜单（Chest GUI）
   - 显示当前成员列表
   - 底部有"添加成员"按钮
3. 点击"添加成员"
4. 打开 Anvil GUI
   - 提示: "请输入玩家名称"
5. 玩家输入名称
6. 点击确认
7. 发送邀请
8. 返回成员管理菜单
```

---

## 🔍 技术细节

### PacketEvents Anvil GUI 实现

```java
public class PacketEventsAnvilProvider {
    
    public void openAnvilInput(Player player, String prompt, 
                              Consumer<String> onConfirm, 
                              Runnable onCancel) {
        
        // 1. 创建 Anvil GUI 数据包
        WrapperPlayServerOpenWindow packet = new WrapperPlayServerOpenWindow(
            1, // 窗口ID
            0, // Anvil 容器类型
            Component.text(prompt)
        );
        
        // 2. 发送数据包给玩家
        PacketEvents.getAPI().getProtocolManager()
            .sendPacket(player, packet);
        
        // 3. 监听玩家输入
        PacketEvents.getAPI().getEventManager()
            .registerListener(new PacketListenerAdapter() {
                @Override
                public void onPacketReceive(PacketReceiveEvent event) {
                    if (event.getPacketType() == PacketType.Play.Client.RENAME_ITEM) {
                        WrapperPlayClientRenameItem wrapper = 
                            new WrapperPlayClientRenameItem(event);
                        String input = wrapper.getItemName();
                        
                        // 回调
                        onConfirm.accept(input);
                        
                        // 取消监听
                        event.getTasksAfterSend().add(this::unregister);
                    }
                }
            });
    }
}
```

### TriumphGUI 集成调用

```java
public class TriumphChestMenuProvider {
    private final PacketEventsAnvilProvider anvilProvider;
    
    public void openMainMenu(Player player) {
        Gui gui = Gui.gui()
            .title(Component.text("国家管理"))
            .rows(6)
            .create();
        
        // "改名"按钮
        gui.setItem(10, ItemBuilder.from(Material.NAME_TAG)
            .name(Component.text("§e国家改名"))
            .asGuiItem(event -> {
                event.setCancelled(true);
                player.closeInventory();
                
                // 打开 Anvil 输入
                anvilProvider.openAnvilInput(
                    player,
                    "请输入新的国家名称",
                    newName -> {
                        // 处理改名
                        nationModule.renameNation(nationId, newName);
                        player.sendMessage("§a改名成功！");
                        
                        // 重新打开主菜单
                        openMainMenu(player);
                    },
                    () -> {
                        // 取消，重新打开主菜单
                        openMainMenu(player);
                    }
                );
            })
        );
        
        gui.open(player);
    }
}
```

---

## 📊 时间估算

| 阶段 | 任务 | 预计时间 |
|------|------|----------|
| Phase 1 | PacketEvents 集成 | 2-3 小时 |
| Phase 2 | TriumphGUI 增强 | 3-4 小时 |
| Phase 3 | 菜单工厂更新 | 0.5 小时 |
| Phase 4 | 配置文件更新 | 1 小时 |
| Phase 5 | 测试与优化 | 2-3 小时 |
| **总计** |  | **8-11.5 小时** |

---

## 🎯 优先级

### P0 (必须完成)
- [ ] PacketEvents 依赖添加
- [ ] PacketEventsAnvilProvider 基础实现
- [ ] TriumphGUI 主菜单布局
- [ ] 国家改名功能（Anvil 输入）

### P1 (高优先级)
- [ ] 成员管理（添加成员 Anvil 输入）
- [ ] 财政管理（金额输入 Anvil）
- [ ] 完整的主菜单功能

### P2 (中优先级)
- [ ] 配置文件自定义图标
- [ ] 错误处理和验证
- [ ] 用户体验优化

### P3 (低优先级)
- [ ] 搜索功能
- [ ] 动画效果
- [ ] 国际化支持

---

## 🚀 下一步行动

**立即开始:**
1. 添加 PacketEvents 依赖
2. 创建 PacketEventsAnvilProvider 基础框架
3. 实现简单的 Anvil 输入测试

**测试命令:**
```bash
# 测试 Anvil 输入
/menu test-anvil

# 打开主菜单
/menu
```

---

**文档版本**: 1.0  
**创建时间**: 2026-06-22  
**最后更新**: 2026-06-22  
**状态**: 📋 待实现
