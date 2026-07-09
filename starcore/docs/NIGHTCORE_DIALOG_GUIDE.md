# NightCore Dialog 原生菜单系统使用指南

## 🎯 概述

StarCore 现已支持 **NightCore Dialog 原生输入框菜单**，提供类似 ExcellentCrates 的弹窗输入体验，无需打开箱子界面。

---

## ✅ 系统状态

- ✅ **NightCore UI Dialog 反射实现成功**
- ✅ **自动检测并优先使用**
- ✅ **菜单命令已注册**
- ✅ **服务器运行在 localhost:25566**

**日志确认:**
```
[StarCore] NightCore UI Dialog reflection initialized successfully
[StarCore] Auto-detected menu provider: NightCore UI Dialog (Reflection)
[StarCore] STARCORE menu command registered.
```

---

## 🚀 快速开始

### 1. 创建国家

```bash
/sc nation create 我的国家
```

### 2. 打开原生对话框菜单

```bash
/menu
```

或使用别名：
```bash
/星核菜单
```

### 3. 在聊天框输入选项

当菜单打开后，会显示：

```
=== 我的国家 管理菜单 ===
1. 查看国家信息
2. 管理成员
3. 查看国库
4. 领土管理
5. 外交关系
输入 cancel 取消
```

**直接在聊天框输入数字或关键词：**
- 输入 `1` - 查看国家详细信息
- 输入 `2` - 查看成员列表
- 输入 `3` - 查看国库（开发中）
- 输入 `4` - 查看领土统计
- 输入 `5` - 查看外交关系（开发中）
- 输入 `cancel` 或 `取消` - 关闭菜单

---

## ⚙️ 配置

编辑 `plugins/STARCORE/config.yml`:

```yaml
menu:
  # 菜单提供者类型
  provider: auto  # 推荐，自动选择最佳提供者
  
  # 可选值：
  # - auto: 自动检测（优先级：NightCore Dialog > TriumphGUI > Fallback）
  # - nightcore: 强制使用 NightCore Dialog 原生输入框
  # - triumph: 强制使用 TriumphGUI 箱子界面
  # - fallback: 使用聊天菜单
```

---

## 🔧 技术实现

### 反射架构

由于 Paper 1.21+ 的插件类加载器隔离，StarCore 使用**纯反射**实现 NightCore Dialog 支持：

1. **动态类加载** - 从 NightCore 的类加载器加载 Dialog API
2. **动态代理** - 使用 `Proxy.newProxyInstance()` 实现 `DialogHandler` 接口
3. **反射调用** - 通过反射调用 `Dialog.Builder` 构造器和方法

**关键代码：**
```java
// 构造器: new Dialog.Builder(Player player, DialogHandler handler)
Constructor<?> builderConstructor = dialogBuilderClass.getConstructor(
    org.bukkit.entity.Player.class, 
    dialogHandlerClass
);

// 创建 DialogHandler 代理
Object handler = Proxy.newProxyInstance(
    nightCoreLoader,
    new Class<?>[]{dialogHandlerClass},
    (proxy, method, args) -> {
        if (method.getName().equals("onInput")) {
            String input = (String) args[1];
            // 处理输入
        }
        return null;
    }
);

// 构建并显示对话框
Object builder = builderConstructor.newInstance(player, handler);
setPromptMethod.invoke(builder, "菜单提示");
buildMethod.invoke(builder); // 自动打开
```

### 为什么不直接依赖 NightCore？

Paper 1.21+ 的插件类加载器是**完全隔离**的：
- ❌ 即使在 `pom.xml` 中添加依赖，运行时也无法访问其他插件的类
- ❌ `provided` scope 只在编译时有效
- ✅ 必须使用反射绕过类加载器限制

---

## 📋 功能状态

| 功能 | 状态 | 说明 |
|------|------|------|
| 查看国家信息 | ✅ 完成 | 显示国家名称、创建者、成员数、领土数 |
| 管理成员 | 🚧 部分完成 | 显示成员列表，邀请/踢出功能开发中 |
| 查看国库 | 🚧 开发中 | 金库系统集成中 |
| 领土管理 | ✅ 完成 | 显示已占领区块数 |
| 外交关系 | 🚧 开发中 | 外交系统集成中 |

---

## 🐛 故障排除

### 问题：输入 `/menu` 没有反应

**可能原因：**
1. 玩家未加入国家

**解决方案：**
```bash
# 先创建或加入国家
/sc nation create 测试国家

# 然后再打开菜单
/menu
```

### 问题：提示"NightCore Dialog 不可用"

**可能原因：**
1. NightCore 插件未安装
2. NightCore 版本不兼容

**解决方案：**
```bash
# 检查 NightCore 是否已加载
/plugins

# 如果没有，下载 NightCore 2.8.3+
# 或在配置中切换到 TriumphGUI
```

**切换到箱子界面：**
```yaml
menu:
  provider: triumph  # 使用 TriumphGUI 箱子界面
```

### 问题：菜单打开后无法输入

**检查：**
- 确认你在**聊天框**输入，而不是命令框
- 输入应该是**纯数字**或**关键词**，不需要 `/`

---

## 📊 系统要求

- ✅ Paper 1.21.11 或更高版本
- ✅ NightCore 2.8.3 或更高版本（可选，推荐）
- ✅ Java 21+

**备用方案：**
- 如果 NightCore 不可用，自动降级到 TriumphGUI 箱子界面
- 如果 TriumphGUI 也不可用，自动降级到聊天菜单

---

## 💡 提示

1. **原生体验** - NightCore Dialog 提供最接近原版的输入体验
2. **无需箱子** - 不会打开箱子界面，不影响玩家背包
3. **快速输入** - 直接在聊天框输入，响应迅速
4. **智能降级** - 如果 NightCore 不可用，自动切换到其他提供者

---

## 📚 相关文档

- [管理员完整指南](ADMIN_GUIDE.md)
- [用户使用指南](MENU_GUIDE.md)
- [技术架构文档](MENU_SYSTEM.md)

---

**版本**: StarCore 1.0.0  
**最后更新**: 2026-06-22  
**菜单提供者**: NightCore UI Dialog (Reflection)
