# STARCORE 集成测试指南

**目标：** 将 EssentialsModule 集成到主插件并测试  
**时间：** 0.5天  
**状态：** 🚀 开始集成

---

## 📋 集成步骤

### 步骤 1：更新 plugin.yml

需要添加所有新命令到 `plugin.yml`：

```yaml
commands:
  # 现有命令...
  starcore:
    description: STARCORE main command
    # ...

  # ========== Essentials 命令 ==========
  
  # 社交命令
  msg:
    description: 发送私聊消息
    usage: /<command> <玩家> <消息>
    aliases: [tell, whisper, m, t, w]
    
  reply:
    description: 回复最近的私聊
    usage: /<command> <消息>
    aliases: [r]
    
  ignore:
    description: 屏蔽玩家的消息
    usage: /<command> <玩家>
    
  unignore:
    description: 取消屏蔽玩家
    usage: /<command> <玩家>
  
  # 昵称命令
  nick:
    description: 设置或移除昵称
    usage: /<command> <昵称|off>
    permission: starcore.nick
    
  realname:
    description: 查询昵称对应的真实玩家名
    usage: /<command> <昵称>
  
  # Warp传送点命令
  warp:
    description: 传送到公共传送点
    usage: /<command> <名称>
    
  setwarp:
    description: 设置公共传送点
    usage: /<command> <名称>
    permission: starcore.warp.set
    
  delwarp:
    description: 删除公共传送点
    usage: /<command> <名称>
    permission: starcore.warp.delete
    
  warps:
    description: 列出所有公共传送点
    usage: /<command>

permissions:
  # 现有权限...
  
  # Essentials 权限
  starcore.nick:
    description: 允许设置昵称
    default: true
    
  starcore.nick.color:
    description: 允许在昵称中使用颜色代码
    default: op
    
  starcore.warp.set:
    description: 允许设置传送点
    default: op
    
  starcore.warp.delete:
    description: 允许删除传送点
    default: op
```

---

### 步骤 2：修改主类集成 EssentialsModule

在 `StarCorePlugin.java` 中添加：

```java
package dev.starcore.starcore;

import dev.starcore.starcore.core.scheduler.FoliaCompatScheduler;
import dev.starcore.starcore.essentials.EssentialsModule;
import dev.starcore.starcore.foundation.message.MessageService;
import dev.starcore.starcore.integration.mobstack.MobStackIntegration;
import dev.starcore.starcore.integration.vault.VaultIntegration;
import org.bukkit.plugin.java.JavaPlugin;

public final class StarCorePlugin extends JavaPlugin {
    
    // 核心组件
    private FoliaCompatScheduler scheduler;
    private MessageService messages;
    
    // 模块
    private EssentialsModule essentialsModule;
    
    // 集成
    private VaultIntegration vaultIntegration;
    private MobStackIntegration mobStackIntegration;
    
    @Override
    public void onEnable() {
        getLogger().info("正在启用 STARCORE...");
        
        // 1. 初始化核心组件
        this.scheduler = new FoliaCompatScheduler(this);
        this.messages = new MessageService(this); // 假设已有
        
        // 2. 初始化集成
        initializeIntegrations();
        
        // 3. 初始化模块
        initializeModules();
        
        getLogger().info("✅ STARCORE 已成功启用！");
    }
    
    @Override
    public void onDisable() {
        getLogger().info("正在禁用 STARCORE...");
        
        // 禁用模块
        if (essentialsModule != null) {
            essentialsModule.disable();
        }
        
        // 注销集成
        if (vaultIntegration != null) {
            vaultIntegration.unregister();
        }
        
        getLogger().info("✅ STARCORE 已禁用");
    }
    
    /**
     * 初始化集成
     */
    private void initializeIntegrations() {
        // Vault 集成
        if (vaultIntegration != null) { // 假设已有 EconomyService
            vaultIntegration = new VaultIntegration(this, null);
            vaultIntegration.register();
        }
        
        // 生物堆叠集成
        mobStackIntegration = new MobStackIntegration(this);
        mobStackIntegration.init();
    }
    
    /**
     * 初始化模块
     */
    private void initializeModules() {
        // Essentials 模块
        essentialsModule = new EssentialsModule(this, scheduler, messages);
        essentialsModule.enable();
    }
    
    // Getter 方法
    public FoliaCompatScheduler getScheduler() {
        return scheduler;
    }
    
    public EssentialsModule getEssentialsModule() {
        return essentialsModule;
    }
    
    public MobStackIntegration getMobStackIntegration() {
        return mobStackIntegration;
    }
}
```

---

### 步骤 3：编译项目

```bash
# 在项目根目录执行
mvn clean package

# 或者如果使用 Gradle
gradle clean build
```

**预期结果：**
- ✅ 编译成功
- ✅ 生成 jar 文件在 `target/` 目录

---

### 步骤 4：部署到测试服务器

```bash
# 1. 停止服务器
stop

# 2. 复制插件
cp target/starcore-0.1.0-SNAPSHOT.jar /path/to/server/plugins/

# 3. 安装依赖插件（可选）
# - StackMob (推荐)
# - Vault (如果使用经济集成)

# 4. 启动服务器
start
```

---

## 🧪 功能测试清单

### 测试 1：基础启动 ✅

**操作：**
1. 启动服务器
2. 检查控制台日志

**预期结果：**
```
[STARCORE] 正在启用 STARCORE...
[STARCORE] ✅ 检测到 Paper 环境，使用传统调度器
[STARCORE] ✅ 生物堆叠: StackMob 集成已启用
[STARCORE] 正在启用 Essentials 模块...
[STARCORE] 正在加载 Essentials 数据...
[STARCORE] ✅ Essentials 数据加载完成
[STARCORE] 已注册 Essentials 命令
[STARCORE] ✅ Essentials 模块已启用
[STARCORE] ✅ STARCORE 已成功启用！
```

---

### 测试 2：社交命令 ✅

**测试 /msg：**
```
玩家1: /msg 玩家2 你好
预期: 玩家1和玩家2都收到私聊消息
```

**测试 /reply：**
```
玩家2: /reply 你好啊
预期: 回复消息发送给玩家1
```

**测试 /ignore：**
```
玩家2: /ignore 玩家1
玩家1: /msg 玩家2 测试
预期: 玩家1收到"该玩家屏蔽了你"
```

**测试 /unignore：**
```
玩家2: /unignore 玩家1
玩家1: /msg 玩家2 测试
预期: 消息正常发送
```

---

### 测试 3：昵称命令 ✅

**测试设置昵称：**
```
玩家1: /nick TestNick
预期: 显示"已设置昵称为: TestNick"
预期: TAB列表显示新昵称
```

**测试颜色昵称（需要权限）：**
```
OP: /nick §6Golden§fNick
预期: 成功设置带颜色的昵称
```

**测试移除昵称：**
```
玩家1: /nick off
预期: 显示"已移除昵称"
预期: TAB列表恢复真实名称
```

**测试查询真名：**
```
玩家2: /realname TestNick
预期: 显示"昵称 TestNick 的真实名称是: Player1"
```

---

### 测试 4：Warp命令 ✅

**测试设置传送点（管理员）：**
```
OP: /setwarp spawn
预期: 显示"已设置传送点: spawn"
```

**测试列出传送点：**
```
玩家: /warps
预期: 显示所有传送点列表
```

**测试传送：**
```
玩家: /warp spawn
预期: 传送到spawn位置
预期: 显示"已传送到: spawn"
```

**测试删除传送点（管理员）：**
```
OP: /delwarp spawn
预期: 显示"已删除传送点: spawn"
```

---

### 测试 5：数据持久化 ✅

**测试自动保存：**
```
1. 设置昵称: /nick Test
2. 等待5分钟
3. 检查日志: 应显示"正在保存 Essentials 数据..."
```

**测试玩家退出保存：**
```
1. 设置昵称
2. 退出服务器
3. 重新进入
4. 检查昵称是否保留
```

**测试Warp持久化：**
```
1. 设置传送点: /setwarp test
2. 重启服务器
3. /warps 检查传送点是否存在
```

---

### 测试 6：权限系统 ✅

**测试无权限玩家：**
```
普通玩家: /setwarp test
预期: "你没有权限设置传送点"
```

**测试颜色权限：**
```
无权限玩家: /nick §6Test
预期: "你没有权限使用颜色代码"
```

---

### 测试 7：Tab补全 ✅

**测试玩家名补全：**
```
/msg <TAB>
预期: 显示在线玩家列表
```

**测试Warp名补全：**
```
/warp <TAB>
预期: 显示所有传送点
```

---

### 测试 8：错误处理 ✅

**测试无效命令：**
```
/msg
预期: 显示用法提示
```

**测试不存在的传送点：**
```
/warp notexist
预期: "传送点不存在: notexist"
```

**测试离线玩家：**
```
/msg OfflinePlayer 消息
预期: "玩家不在线: OfflinePlayer"
```

---

### 测试 9：性能测试 ✅

**测试大量数据：**
```
1. 创建20个传送点
2. 设置多个家园
3. 测试昵称
4. 检查TPS: /tps 或 /spark tps
预期: TPS保持在19.5+
```

**测试并发：**
```
1. 多个玩家同时发送私聊
2. 检查消息是否正确
3. 检查是否有错误日志
```

---

### 测试 10：生物堆叠集成 ✅

**测试检测：**
```
控制台日志应显示:
"✅ 生物堆叠: StackMob 集成已启用"
或
"⚠️ 未检测到生物堆叠插件"
```

---

## 📊 测试记录表

| 测试项 | 状态 | 备注 |
|--------|------|------|
| 基础启动 | ⬜ 未测试 | |
| 社交命令 | ⬜ 未测试 | |
| 昵称命令 | ⬜ 未测试 | |
| Warp命令 | ⬜ 未测试 | |
| 数据持久化 | ⬜ 未测试 | |
| 权限系统 | ⬜ 未测试 | |
| Tab补全 | ⬜ 未测试 | |
| 错误处理 | ⬜ 未测试 | |
| 性能测试 | ⬜ 未测试 | |
| 生物堆叠 | ⬜ 未测试 | |

---

## 🐛 问题追踪

### 发现的问题

| ID | 描述 | 严重性 | 状态 |
|----|------|--------|------|
| - | - | - | - |

---

## ✅ 测试完成标准

- ✅ 所有10项测试通过
- ✅ 无严重Bug
- ✅ TPS保持正常
- ✅ 数据正确保存
- ✅ 命令正常工作
- ✅ 权限正确控制

---

## 📝 下一步

### 测试通过后

✅ 标记为生产就绪  
✅ 更新文档  
✅ 继续开发P1或P2

### 发现问题后

🔧 记录问题  
🔧 修复Bug  
🔧 重新测试

---

**文档版本：** 1.0  
**创建时间：** 2026-06-15  
**状态：** 准备测试

开始测试吧！🧪
