# StarCore 菜单系统架构文档

## 概述

StarCore 国家管理系统支持多种菜单提供者，通过工厂模式实现可配置切换。当前版本主要支持 **TriumphGUI**（箱子界面）作为稳定的菜单实现。

## 架构设计

### 核心接口

**`NationMenuProvider`** - 菜单提供者接口
```java
public interface NationMenuProvider {
    void openMainMenu(Player player);
    String getProviderType();
    boolean isAvailable();
}
```

### 实现类

#### 1. TriumphChestMenuProvider ✅ **推荐使用**
- **类型**: 箱子界面 GUI
- **依赖**: TriumphGUI 3.1.10
- **状态**: ✅ 完全支持，已测试
- **特点**: 
  - 稳定可靠
  - 兼容性好
  - 自动适配 Paper Adventure API
  - 无需额外配置

#### 2. FallbackChestMenuProvider ✅
- **类型**: 纯聊天菜单
- **依赖**: 无
- **状态**: ✅ 始终可用
- **特点**: 作为降级方案，当没有 GUI 框架可用时自动启用

#### 3. ProtocolLibAnvilMenuProvider ⚠️ **暂不支持**
- **类型**: 原生铁砧输入界面
- **依赖**: ProtocolLib 5.3.0+
- **状态**: ⚠️ 因类加载器限制暂时禁用
- **问题**: Paper 插件类加载器隔离导致无法在运行时访问 ProtocolLib 类

#### 4. NightCoreDialogMenuProvider ⚠️ **暂不支持**
- **类型**: 原生对话框输入
- **依赖**: NightCore 2.8.3+
- **状态**: ⚠️ API 不匹配，暂时禁用
- **问题**: `DialogHandler.onInput()` 方法签名变更

## 配置方式

### config.yml

```yaml
menu:
  # 菜单提供者类型
  # 可选值: auto (自动检测), triumph (箱子GUI), fallback (聊天菜单)
  provider: auto
```

### 优先级

当 `provider: auto` 时，按以下顺序检测：
1. TriumphGUI（如果可用）
2. Fallback（聊天菜单，始终可用）

## 使用示例

### 打开主菜单

```java
// 在 NationModule 中
menuProvider.openMainMenu(player);
```

### TriumphGUI 菜单特点

- ✅ 箱子界面，易于操作
- ✅ 支持物品图标和描述
- ✅ 可配置菜单大小和标题
- ✅ 响应式点击处理

## 技术限制与解决方案

### 1. ProtocolLib 类加载问题

**问题描述**:
- ProtocolLib 作为 `provided` scope 依赖
- Paper 插件类加载器隔离机制
- 运行时 `ClassNotFoundException: com.comphenix.protocol.ProtocolLibrary`

**尝试的解决方案**:
```java
// 尝试 1: 从 ProtocolLib 插件的 ClassLoader 加载
Plugin protocolLib = getServer().getPluginManager().getPlugin("ProtocolLib");
ClassLoader loader = protocolLib.getClass().getClassLoader();
Class<?> clazz = loader.loadClass("com.comphenix.protocol.ProtocolLibrary");
// ❌ 失败: 后续代码仍然无法访问类

// 尝试 2: 添加到 plugin.yml depend
depend: [ProtocolLib]
// ❌ 失败: 会强制要求 ProtocolLib，不符合可选依赖设计
```

**根本原因**:
`ProtocolLibAnvilMenuProvider` 在编译时就 import 了 ProtocolLib 的类，这些类引用会在类加载时被解析。即使我们能动态加载 ProtocolLib 的类，StarCore 自己的类仍然无法找到这些依赖。

**可能的解决方案**（未实现）:
1. 完全使用反射 API 调用 ProtocolLib（工作量大）
2. 使用独立的插件扩展模式
3. 使用 Paper 的 `libraries` 加载器

### 2. NightCore API 不匹配

**问题描述**:
```java
// 期望的 API (旧版本)
DialogHandler {
    void onInput(Player player, String input);
}

// 实际的 API (NightCore 2.8.3)
DialogHandler {
    void onInput(Dialog dialog, WrappedInput input);
}
```

**解决方案**: 需要重写适配新版本 API

### 3. TriumphGUI Adventure API 序列化

**问题描述**:
```java
// 错误使用方式
GuiItem item = ItemBuilder.from(Material.DIAMOND)
    .name(Component.text("国家管理"))  // UnsupportedOperationException
    .asGuiItem();
```

**解决方案**:
```java
// 正确方式：使用 Paper 原生 API
ItemStack item = new ItemStack(Material.DIAMOND);
ItemMeta meta = item.getItemMeta();
if (meta != null) {
    meta.displayName(Component.text("国家管理", NamedTextColor.GREEN));
    item.setItemMeta(meta);
}
GuiItem guiItem = new GuiItem(item, event -> { ... });
```

## 部署状态

### 测试服务器
- **地址**: localhost:25566
- **版本**: Paper 1.21.11-69
- **插件状态**: ✅ StarCore 1.0.0 已启用
- **菜单系统**: TriumphGUI (Chest)

### 编译信息
- **构建时间**: 2026-06-22 16:29:16
- **Maven 输出**: target/starcore-0.1.0-SNAPSHOT.jar (28 MB)

## 未来改进方向

1. **ProtocolLib 支持**
   - 研究使用反射完全动态调用
   - 或创建独立的扩展插件

2. **NightCore 支持**
   - 适配 NightCore 2.8.3+ API
   - 实现新的 `DialogHandler` 接口

3. **更多菜单类型**
   - Anvil GUI (需要 ProtocolLib)
   - Sign GUI
   - Book GUI

## 相关文件

### 核心类
- `NationMenuProvider.java` - 接口定义
- `NationMenuFactory.java` - 工厂类
- `TriumphChestMenuProvider.java` - TriumphGUI 实现
- `FallbackChestMenuProvider.java` - 聊天菜单实现

### 禁用的类（`.disabled` 后缀）
- `ProtocolLibAnvilMenuProvider.java.disabled`
- `NightCoreDialogMenuProvider.java.disabled`

### 配置文件
- `src/main/resources/config.yml` - 菜单配置模板
- `plugins/STARCORE/config.yml` - 运行时配置

## 常见问题

### Q: 为什么不支持 ProtocolLib？
A: Paper 的插件类加载器隔离机制导致无法在运行时访问其他插件的类。需要完全重写为反射调用才能支持。

### Q: 如何切换菜单类型？
A: 修改 `config.yml` 中的 `menu.provider` 配置项，然后重启服务器。

### Q: TriumphGUI 可以自定义样式吗？
A: 可以，在 `TriumphChestMenuProvider.java` 中修改菜单布局、图标和颜色。

### Q: 聊天菜单有什么限制？
A: 聊天菜单只能显示命令列表，没有交互式操作，用户需要手动输入命令。

---

**文档版本**: 1.0  
**最后更新**: 2026-06-22  
**作者**: StarCore Team
