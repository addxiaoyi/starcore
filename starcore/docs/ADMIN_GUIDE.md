# StarCore 管理员指南

## 🎮 服务器状态

- **地址**: localhost:25566
- **版本**: Paper 1.21.11
- **StarCore**: 1.0.0
- **状态**: ✅ 运行中

---

## 💰 经济系统

### 管理员命令

```bash
# 查看玩家余额
/starcore economy balance <玩家>
/sc 经济 余额 <玩家>

# 设置玩家余额
/starcore economy set <玩家> <金额>
/sc 经济 设置 <玩家> 10000

# 给予玩家金钱
/starcore economy give <玩家> <金额>
/sc 钱 给 add 5000

# 扣除玩家金钱
/starcore economy take <玩家> <金额>
/sc 经济 扣 <玩家> 1000
```

### 示例

```bash
# 给自己 10000 星尘
/sc economy set add 10000

# 给玩家 Steve 增加 5000 星尘
/sc economy give Steve 5000

# 查看玩家余额
/sc eco balance Steve
```

### 权限

- `starcore.economy.admin` - 经济管理权限

---

## 📊 PlaceholderAPI 变量

### 经济变量

```
%starcore_balance%              # 玩家余额 (例: 1234.56)
%starcore_balance_formatted%    # 格式化余额 (例: 1.2K, 5.6M)
```

### PvP 统计变量

```
%starcore_kills%                # 击杀数
%starcore_deaths%               # 死亡数
%starcore_assists%              # 助攻数
%starcore_kd%                   # K/D 比率 (保留2位小数)
%starcore_kda%                  # KDA 比率
%starcore_killstreak%           # 当前连杀数
%starcore_best_killstreak%      # 最高连杀记录
%starcore_duel_wins%            # 决斗胜场
%starcore_duel_losses%          # 决斗败场
%starcore_duel_winrate%         # 决斗胜率 (百分比)
%starcore_damage_dealt%         # 总伤害输出
%starcore_damage_taken%         # 总承受伤害
```

### 使用示例

**计分板显示:**
```yaml
# 在 scoreboard 插件配置中使用
lines:
  - "&e余额: &f%starcore_balance_formatted%"
  - "&cK/D: &f%starcore_kd%"
  - "&a连杀: &f%starcore_killstreak%"
```

**聊天格式:**
```yaml
# 在聊天插件配置中使用
format: "&7[&6%starcore_balance_formatted%&7] &f%player_name%: %message%"
```

---

## 🏛️ 国家管理

### 玩家命令

```bash
# 创建国家
/starcore nation create <国家名称>
/sc 国 创建 <名称>

# 查看国家信息
/starcore nation info
/sc 国 信息

# 打开国家管理菜单 (需要先加入国家)
/menu
/星核菜单
```

### 管理员命令

```bash
# 强制删除国家
/starcore nation delete <国家名称>

# 查看所有国家
/starcore nation list
```

---

## 🎯 菜单系统

### 功能

- ✅ TriumphGUI 箱子界面
- ✅ 国家信息查看
- ✅ 领土管理
- 🚧 成员管理 (开发中)
- 🚧 国库管理 (开发中)

### 配置

编辑 `plugins/STARCORE/config.yml`:

```yaml
menu:
  provider: triumph  # 选项: auto, triumph, fallback
```

- `auto` - 自动检测最佳提供者
- `triumph` - 强制使用 TriumphGUI 箱子界面
- `fallback` - 使用聊天菜单

---

## 🔧 常用管理命令

### 重载插件

```bash
/starcore reload
```

### 调试信息

```bash
/starcore debug
```

### 查看模块状态

```bash
/starcore status
```

---

## 📝 日志位置

```
plugins/STARCORE/
├── config.yml          # 主配置文件
├── messages.yml        # 消息配置
├── starcore.db         # 数据库 (SQLite)
└── logs/              # 日志文件
```

服务器日志:
```
logs/latest.log
```

---

## 🐛 故障排除

### 菜单打不开

1. 确认玩家已加入国家: `/sc nation info`
2. 检查权限: `starcore.nation.member`
3. 查看日志: `tail -50 logs/latest.log | grep menu`

### PlaceholderAPI 变量不显示

1. 确认 PlaceholderAPI 已安装
2. 检查注册状态: 日志中应有 `PlaceholderAPI expansion registered`
3. 测试变量: `/papi parse me %starcore_balance%`

### 经济命令无权限

1. 给予管理员权限: `/lp user <玩家> permission set starcore.economy.admin true`
2. 或使用 OP: `/op <玩家>`

---

## 📚 相关文档

- [用户使用指南](MENU_GUIDE.md)
- [开发者文档](MENU_DEVELOPER.md)
- [技术架构文档](MENU_SYSTEM.md)

---

**版本**: StarCore 1.0.0  
**最后更新**: 2026-06-22
