# STARCORE 测试部署包

**目标：** 快速部署测试  
**内容：** 配置文件和集成示例  

---

## 📁 文件结构

```
plugins/
├── STARCORE.jar
└── STARCORE/
    ├── config.yml
    ├── essentials/
    │   ├── warps.yml
    │   └── players/
    │       └── <UUID>.yml
    └── lang/
        └── messages_zh_cn.yml
```

---

## 📄 config.yml 模板

```yaml
# STARCORE 配置文件
# 版本: 0.3.0

# 基础设置
settings:
  language: zh_cn
  debug: false

# Essentials 设置
essentials:
  # 传送系统
  teleport:
    # 冷却时间（秒）
    cooldowns:
      spawn: 3
      home: 3
      warp: 3
      tpa: 5
      back: 3
    
    # 最大家园数量
    max-homes: 5
    
    # 传送时是否取消（移动/受伤）
    cancel-on-move: true
    cancel-on-damage: true
  
  # 昵称系统
  nickname:
    # 最小长度
    min-length: 2
    # 最大长度
    max-length: 16
    # 允许的字符
    allowed-chars: "a-zA-Z0-9_§ "
  
  # 社交系统
  social:
    # 消息历史保留数量
    message-history: 10
    # 是否显示间谍模式（管理员看到所有私聊）
    social-spy: false

# 数据保存
data:
  # 自动保存间隔（分钟）
  auto-save-interval: 5
  # 使用数据库（如果为false则使用YAML）
  use-database: false
  # 数据库设置
  database:
    type: sqlite
    host: localhost
    port: 3306
    database: starcore
    username: root
    password: password

# 性能设置
performance:
  # 异步保存数据
  async-save: true
  # 缓存大小
  cache-size: 1000

# 集成设置
integrations:
  # Vault经济
  vault:
    enabled: true
  
  # 生物堆叠
  mob-stacking:
    enabled: true
    # 优先使用的插件（stackmob/mobstacker/rosestacker）
    preferred: stackmob
```

---

## 📄 plugin.yml 完整版

```yaml
name: STARCORE
version: '${project.version}'
main: dev.starcore.starcore.StarCorePlugin
api-version: '1.21'
author: STARCORE Team
description: AI-Driven Next-Gen Minecraft National Strategy Plugin
website: https://github.com/starcore/starcore
load: STARTUP

# 依赖
softdepend:
  - Vault
  - PlaceholderAPI
  - LuckPerms
  - FancyNpcs
  - ItemsAdder
  - CraftEngine
  - ProtocolLib
  - StackMob
  - MobStacker
  - RoseStacker

# Folia 支持
folia-supported: true

# 命令
commands:
  # 主命令
  starcore:
    description: STARCORE main command
    usage: /<command> [help|status|reload]
    aliases: [sc, stc]
  
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

# 权限
permissions:
  # 基础权限
  starcore.command:
    description: 使用STARCORE基础命令
    default: true
    
  starcore.admin:
    description: 管理员权限
    default: op
  
  # Essentials 权限
  starcore.essentials.*:
    description: 所有Essentials权限
    default: op
    children:
      starcore.nick: true
      starcore.nick.color: true
      starcore.warp.set: true
      starcore.warp.delete: true
  
  starcore.nick:
    description: 设置昵称
    default: true
    
  starcore.nick.color:
    description: 在昵称中使用颜色代码
    default: op
    
  starcore.warp.set:
    description: 设置公共传送点
    default: op
    
  starcore.warp.delete:
    description: 删除公共传送点
    default: op
    
  starcore.teleport.bypass:
    description: 绕过传送冷却
    default: op
```

---

## 🔧 快速集成代码

如果您的主类还不完整，可以使用这个最小化版本进行测试：

```java
package dev.starcore.starcore;

import dev.starcore.starcore.core.scheduler.FoliaCompatScheduler;
import dev.starcore.starcore.essentials.EssentialsModule;
import dev.starcore.starcore.integration.mobstack.MobStackIntegration;
import org.bukkit.plugin.java.JavaPlugin;

/**
 * STARCORE 主类（测试版本）
 */
public final class StarCorePlugin extends JavaPlugin {
    
    private FoliaCompatScheduler scheduler;
    private EssentialsModule essentialsModule;
    private MobStackIntegration mobStackIntegration;
    
    @Override
    public void onEnable() {
        getLogger().info("=================================");
        getLogger().info("  STARCORE v" + getDescription().getVersion());
        getLogger().info("  正在启动...");
        getLogger().info("=================================");
        
        // 保存默认配置
        saveDefaultConfig();
        
        // 初始化调度器
        this.scheduler = new FoliaCompatScheduler(this);
        
        // 生物堆叠集成
        this.mobStackIntegration = new MobStackIntegration(this);
        mobStackIntegration.init();
        
        // Essentials 模块
        this.essentialsModule = new EssentialsModule(
            this,
            scheduler,
            null  // 简化版：暂时不需要 MessageService
        );
        essentialsModule.enable();
        
        getLogger().info("=================================");
        getLogger().info("  ✅ STARCORE 启动成功！");
        getLogger().info("=================================");
    }
    
    @Override
    public void onDisable() {
        getLogger().info("正在禁用 STARCORE...");
        
        if (essentialsModule != null) {
            essentialsModule.disable();
        }
        
        getLogger().info("✅ STARCORE 已禁用");
    }
    
    public FoliaCompatScheduler getScheduler() {
        return scheduler;
    }
    
    public EssentialsModule getEssentialsModule() {
        return essentialsModule;
    }
}
```

---

## 🚀 部署步骤

### 1. 准备服务器

```bash
# 安装 Paper 1.21+
# 下载地址: https://papermc.io/downloads

# 可选：安装依赖插件
# - StackMob: https://modrinth.com/project/EqaioisK
# - Vault: https://www.spigotmc.org/resources/vault.34315/
```

---

### 2. 编译插件

```bash
# 进入项目目录
cd /path/to/starcore

# 使用 Maven 编译
mvn clean package -DskipTests

# 生成的文件位于
target/starcore-0.1.0-SNAPSHOT.jar
```

---

### 3. 部署文件

```bash
# 停止服务器
stop

# 复制插件
cp target/starcore-0.1.0-SNAPSHOT.jar server/plugins/STARCORE.jar

# 创建配置目录（首次运行会自动创建）
# mkdir -p server/plugins/STARCORE/essentials/players
```

---

### 4. 启动测试

```bash
# 启动服务器
start

# 或使用 screen/tmux
screen -S minecraft
cd server
java -Xms2G -Xmx4G -jar paper.jar nogui
```

---

### 5. 检查日志

启动后应该看到：

```
[STARCORE] =================================
[STARCORE]   STARCORE v0.3.0
[STARCORE]   正在启动...
[STARCORE] =================================
[STARCORE] ✅ 检测到 Paper 环境，使用传统调度器
[STARCORE] ✅ 生物堆叠: StackMob 集成已启用
[STARCORE] 正在启用 Essentials 模块...
[STARCORE] 正在加载 Essentials 数据...
[STARCORE] ✅ Essentials 数据加载完成
[STARCORE] 已注册 Essentials 命令
[STARCORE] ✅ Essentials 模块已启用
[STARCORE] =================================
[STARCORE]   ✅ STARCORE 启动成功！
[STARCORE] =================================
```

---

## 🧪 快速测试命令

进入服务器后执行：

```bash
# 测试社交
/msg <玩家> 你好
/reply 你好

# 测试昵称
/nick 测试昵称
/nick off

# 测试Warp（需要OP权限）
/setwarp spawn
/warps
/warp spawn
/delwarp spawn
```

---

## 📊 测试检查表

- [ ] 插件成功加载
- [ ] 无错误日志
- [ ] 命令可以执行
- [ ] Tab补全正常
- [ ] 权限检查正常
- [ ] 数据正确保存
- [ ] TPS正常（19.5+）

---

## 🐛 常见问题

### 问题1：找不到 MessageService

**解决：**
暂时在 EssentialsModule 构造函数中忽略它：
```java
public EssentialsModule(Plugin plugin, FoliaCompatScheduler scheduler, MessageService messages) {
    this.plugin = plugin;
    this.scheduler = scheduler;
    // this.messages = messages; // 暂时注释
}
```

### 问题2：找不到 Module 接口

**解决：**
创建简单的 Module 接口：
```java
package dev.starcore.starcore.core.module;

public interface Module {
    void enable();
    void disable();
}
```

### 问题3：命令未注册

**检查：**
1. plugin.yml 中是否定义了命令
2. 命令名称是否正确
3. 检查服务器日志

---

**准备就绪！开始测试吧！** 🚀

有问题随时报告，我会帮助修复！
