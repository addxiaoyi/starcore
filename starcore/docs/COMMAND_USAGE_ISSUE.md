# 命令 Usage 消息缺失问题报告

**问题**: 菜单按钮点击时显示原始消息键名（如 `command.nation.info-usage`）而非友好的用法提示
**根本原因**: 61 个 `command.xxx.usage` 消息键在语言文件中缺失

## 问题分析

1. **菜单按钮配置** (`nation-menu.yml` 等) 包含各种命令
2. 玩家点击按钮时，命令被发送到服务器
3. 如果命令参数不完整，命令处理器调用 `msg("command.xxx.usage")`
4. `SimpleMessageService.format()` 找不到该键，返回原始键名
5. 玩家看到 `command.xxx-usage` 而非友好的用法提示

## 缺失的消息键列表 (61个)

| 模块 | 缺失键 |
|------|--------|
| nation | usage, usage-extended, info-usage, city-usage, city-create-usage |
| event | usage, list-usage, audit-usage, export-usage, record-usage, clear-usage |
| resource | usage, status-usage, districts-usage, grant-usage, consume-usage |
| epoch | usage |
| time-sync | usage |
| economy | usage, balance-usage, give-usage, take-usage, set-usage |
| resolution | usage, list-usage, sign-usage, cancel-usage, info-usage, history-usage |
| map | usage, confirm-usage, cancel-usage |
| government | usage, propose-usage |
| diplomacy | usage, status-usage, list-usage, set-usage, propose-usage |
| treasury | usage, status-usage, deposit-usage, withdraw-usage, income-usage, reward-usage, tax-usage |
| policy | usage, status-usage, set-usage |
| technology | usage, status-usage, unlock-usage, revoke-usage |
| war | usage, declare-usage, end-usage |
| officer | usage, list-usage, info-usage, appoint-usage, remove-usage |

## 修复方案

在 `lang/messages_zh_cn.yml` 中添加所有缺失的 usage 消息。

## 验证方法

1. 编译项目: `mvn clean package`
2. 启动服务器
3. 打开国家菜单
4. 点击任意按钮，检查是否显示友好消息而非原始键名
