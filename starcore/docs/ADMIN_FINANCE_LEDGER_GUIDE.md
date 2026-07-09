# STARCORE 管理员财政查账指南

本文面向 RPG 服务器服主、管理员和值班运营。目标是快速查清“哪个国家在什么时间因为哪个操作获得或花费了多少钱”，并能把结果导出给管理组复核。

## 快速入口

命令端：

```text
/sc ev a <国家> [筛选] [时间窗口] [页码] [每页数量]
/sc ev ex <国家> [筛选] [时间窗口] [csv|json]
```

完整命令同样可用：

```text
/starcore event audit <国家> [筛选] [时间窗口] [页码] [每页数量]
/starcore event export <国家> [筛选] [时间窗口] [csv|json]
```

网页端：

```text
/sc m w
/starcore map web
```

打开个人地图链接后，在国家详情的“财政概览”区域使用分类、时间范围、分页、导出 CSV、导出 JSON。

## 常用查账命令

查看国家最近财政账本：

```text
/sc ev a 星河商会 finance
```

查看最近 24 小时资源区块产出：

```text
/sc ev a 星河商会 resource-income 24h
```

查看最近 7 天玩家税收：

```text
/sc ev a 星河商会 tax 7d
```

查看金库支出第 2 页，每页 25 条：

```text
/sc ev a 星河商会 withdraw 2 25
```

导出最近 7 天完整财政账本 CSV：

```text
/sc ev ex 星河商会 finance 7d csv
```

导出最近 24 小时资源产出 JSON：

```text
/sc ev ex 星河商会 resource-income 24h json
```

## 筛选词

| 筛选 | 中文别名 | 说明 |
|---|---|---|
| `finance` | `财政` / `账本` | 财政与资源相关粗粒度账本，兼容旧用法 |
| `resource-income` | `资源产出` | 资源区块刷新给国家金库带来的收入 |
| `income` | `日常收入` | 国家日常收入结算 |
| `reward` | `任务奖励` | RPG 任务、副本、活动等外部插件奖励入账 |
| `tax` | `税收` | 玩家税收结算 |
| `deposit` | `存入` | 管理员或系统存入金库 |
| `withdraw` | `支出` | 金库支出 |
| `treasury` | `金库` | 所有 `treasury.*` 事件 |
| `resource` | `资源` | 所有 `resource.*` 事件 |
| `all` | `全部` | 全部国家事件 |

## 时间窗口

时间窗口是相对当前时间的范围：

| 写法 | 含义 |
|---|---|
| `24h` | 最近 24 小时 |
| `7d` | 最近 7 天 |
| `30d` | 最近 30 天 |
| `60m` | 最近 60 分钟 |
| `1天` | 最近 1 天 |
| `7天` | 最近 7 天 |
| 不填 | 不限制时间 |

命令端 JSON 导出会写入：

- `range`: 例如 `24h`、`7d`、`all`
- `from`: 实际开始时间
- `to`: 空字符串，表示导出当前时刻
- `total`: 本次导出的事件数量

## 导出文件

命令端导出目录：

```text
plugins/STARCORE/exports/events/
```

测试环境没有插件数据目录时，导出到：

```text
target/starcore-event-exports-test/
```

命令端 CSV 文件名格式：

```text
starcore-event-<国家名>-<国家ID前8位>-<filter>-<range>-<timestamp>.csv
```

网页财政 CSV 文件名格式：

```text
starcore-finance-<国家名>-<国家ID前8位>-<filter>-<range>.csv
```

CSV 默认写入 UTF-8 BOM，方便 Windows Excel 正确识别中文。可在 `config.yml` 关闭：

```yaml
map:
  web:
    finance-export:
      csv-bom-enabled: false
```

## 分类规则配置

命令端 `/sc ev audit/export` 和网页地图财政流水共用 `ledger.categories`。`event-types` 是精确匹配，`prefixes` 是前缀匹配；接入 RPG 任务、副本、活动收益时，推荐把明确事件类型加入对应 `event-types`。

示例：把自定义 RPG 资源奖励归到 `resource-income` / `资源产出`：

```yaml
ledger:
  categories:
    resource-income:
      event-types:
        - treasury.resource-income
        - treasury.rpg-resource-bonus
      prefixes: []
```

配置里的分类名也会进入 `/sc ev audit <国家> ...` 和 `/sc ev export <国家> ...` 的 Tab 补全。建议分类名使用小写英文和连字符，例如 `rpg-bonus`、`season-pass`，这样命令、网页参数和导出文件名都更稳定。

## CSV 字段

命令端 CSV 字段：

```text
nation_id,nation_name,filter,range,from,to,event_id,occurred_at,type,localized_type,message,amount,actor,reason,balance,context
```

网页财政 CSV 字段：

```text
nation_id,nation_name,filter,range,from,to,event_id,occurred_at,type,message,amount,actor,reason,balance,context
```

常用字段解释：

| 字段 | 说明 |
|---|---|
| `amount` | 本条账本涉及的金额 |
| `actor` | 操作人或系统来源 |
| `reason` | 入账/支出的原因 |
| `balance` | 事件记录时的金库余额 |
| `context` | 原始结构化上下文，排查问题时保留 |

## RPG 插件接入建议

外部任务、副本、活动插件应优先调用 STARCORE API 的 `TreasuryRewardService` 写入 `treasury.reward`，而不是模拟玩家执行命令。这样账本会稳定归类到 `reward` / `任务奖励`。

参考：

```text
docs/RPG_PLUGIN_API_INTEGRATION.md
```

## 常见排查

看不到导出命令：

- 确认执行者有 `starcore.admin` 权限。
- 控制台默认可执行。

导出为空：

- 确认国家名正确。
- 先用 `finance` 粗筛，再切到 `tax`、`withdraw` 等细分筛选。
- 放宽时间窗口，例如从 `24h` 改成 `7d`。

Excel 打开中文乱码：

- 确认 `map.web.finance-export.csv-bom-enabled: true`。
- 重新导出 CSV。

网页能查，命令查不到：

- 确认筛选词一致，例如网页“资源产出”对应命令 `resource-income`。
- 命令端相对时间窗口只支持 `24h`、`7d`、`30d`、`60m`、`1天` 这类写法；网页自定义时间适合精确起止时间。

只想看最近发生了什么：

```text
/sc ev a 星河商会 finance 24h 1 10
```

要给管理组留证据：

```text
/sc ev ex 星河商会 finance 7d csv
/sc ev ex 星河商会 finance 7d json
```
