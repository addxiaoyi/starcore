# StarCore 命令参考手册

## 基础命令 /starcore (别名: /sc, /stc)

| 命令 | 说明 | 权限 |
|------|------|------|
| /sc | 显示帮助 | - |
| /sc status | 查看插件状态 | - |
| /sc modules | 查看已启用模块 | - |
| /sc reload | 重载配置 | starcore.admin |

---

## 国家命令 /sc nation (别名: /sc n)

### 国家基础操作

| 命令 | 说明 | 权限 | 费用 |
|------|------|------|------|
| /sc n create | 创建国家 | starcore.nation.create | 10000 |
| /sc n info | 查看国家信息 | - | 免费 |
| /sc n list | 国家列表 | - | 免费 |
| /sc n online | 在线成员 | - | 免费 |

### 国家管理

| 命令 | 说明 | 权限 |
|------|------|------|
| /sc n delete | 删除国家 | starcore.nation.manage |
| /sc n invite | 邀请玩家 | starcore.nation.invite |
| /sc n kick | 踢出玩家 | starcore.nation.manage |
| /sc n leave | 离开国家 | - |
| /sc n accept | 接受邀请 | - |
| /sc n deny | 拒绝邀请 | - |

---

## 领土命令 /sc claim

| 命令 | 说明 | 权限 |
|------|------|------|
| /sc claim tool | 获取圈地工具 | starcore.claim.tool |
| /sc claim confirm | 确认圈地 | starcore.claim.confirm |
| /sc claim cancel | 取消圈地 | - |
| /sc claim list | 领地列表 | - |
| /sc claim here | 查看归属 | - |
| /sc claim remove | 移除领地 | starcore.claim.remove |

### 圈地流程

1. /sc claim tool - 获取圈地工具
2. 左键选择第一个角
3. 右键选择第二个角
4. 系统显示预览和价格
5. /sc claim confirm - 确认圈地

---

## 政体命令 /sc government

| 命令 | 说明 | 权限 |
|------|------|------|
| /sc gov set | 设置政体 | starcore.government.set |
| /sc gov info | 查看政体 | - |

### 政体类型

| 类型 | 说明 |
|------|------|
| monarchy | 君主制 |
| republic | 共和制 |
| dictatorship | 独裁制 |
| theocracy | 神权制 |
| democracy | 民主制 |
| oligarchy | 寡头制 |
| federation | 联邦制 |

---

## 外交命令 /sc diplomacy

| 命令 | 说明 | 权限 |
|------|------|------|
| /sc dip ally | 联盟 | starcore.diplomacy.ally |
| /sc dip war | 宣战 | starcore.diplomacy.war |
| /sc dip truce | 停战 | starcore.diplomacy.truce |
| /sc dip neutral | 中立 | starcore.diplomacy.neutral |
| /sc dip list | 外交列表 | - |
| /sc dip requests | 待处理请求 | - |

---

## 财政命令 /sc treasury

| 命令 | 说明 | 权限 |
|------|------|------|
| /sc treasury deposit | 存款 | - |
| /sc treasury withdraw | 取款 | starcore.treasury.withdraw |
| /sc treasury balance | 余额 | - |
| /sc treasury top | 国库排行 | - |
| /sc treasury log | 交易记录 | starcore.treasury.log |

---

## 官员命令 /sc officer

| 命令 | 说明 | 权限 |
|------|------|------|
| /sc officer appoint | 任命官员 | starcore.officer.appoint |
| /sc officer remove | 罢免官员 | starcore.officer.remove |
| /sc officer list | 官员列表 | - |
| /sc officer powers | 权限列表 | - |

### 职位类型

| 职位 | 命令参数 |
|------|----------|
| 大将军 | marshal |
| 大臣 | minister |
| 顾问 | advisor |
| 将领 | general |

---

## 政策命令 /sc policy

| 命令 | 说明 | 权限 |
|------|------|------|
| /sc policy list | 政策列表 | - |
| /sc policy activate | 激活政策 | starcore.policy.activate |
| /sc policy deactivate | 停用政策 | starcore.policy.deactivate |
| /sc policy active | 激活中政策 | - |

---

## 科技命令 /sc tech

| 命令 | 说明 | 权限 |
|------|------|------|
| /sc tech research | 研究科技 | starcore.tech.research |
| /sc tech list | 科技列表 | - |
| /sc tech tree | 科技树 | - |
| /sc tech status | 研究状态 | - |

---

## 资源命令 /sc resource

| 命令 | 说明 | 权限 |
|------|------|------|
| /sc resource list | 资源列表 | - |
| /sc resource collect | 采集资源 | - |
| /sc resource districts | 资源区 | - |
| /sc resource income | 收入统计 | - |

---

## 称号命令 /sc title

| 命令 | 说明 | 权限 |
|------|------|------|
| /sc title set | 设置称号 | starcore.title.set |
| /sc title remove | 移除称号 | starcore.title.remove |
| /sc title list | 称号列表 | - |

---

## 社交命令 /sc social

| 命令 | 说明 | 权限 |
|------|------|------|
| /sc social friend | 添加好友 | - |
| /sc social unfriend | 删除好友 | - |
| /sc social friends | 好友列表 | - |
| /sc social mail | 发送邮件 | - |
| /sc social mail inbox | 收件箱 | - |

---

## 传送命令

| 命令 | 说明 | 权限 |
|------|------|------|
| /sc home set | 设置家 | - |
| /sc home | 回家 | - |
| /sc warp list | 传送点列表 | - |
| /sc warp | 传送 | - |
| /sc tpa | 传送请求 | - |
| /sc tpaccept | 接受传送 | - |
| /sc tpdeny | 拒绝传送 | - |

---

## 经济命令 /sc money

| 命令 | 说明 | 权限 |
|------|------|------|
| /sc money balance | 查看余额 | - |
| /sc money pay | 转账 | - |
| /sc money top | 财富排行 | - |

---

## 管理命令

| 命令 | 说明 | 权限 |
|------|------|------|
| /sc admin nation | 国家管理 | starcore.admin |
| /sc admin db | 数据库管理 | starcore.admin |
| /sc admin cache | 缓存管理 | starcore.admin |
| /sc admin module | 模块管理 | starcore.admin |
| /sc admin debug | 调试模式 | starcore.admin |

---

## PlaceholderAPI 占位符

| 占位符 | 说明 |
|--------|------|
| %starcore_nation% | 所属国家 |
| %starcore_nation_color% | 国家颜色 |
| %starcore_nation_rank% | 国家排名 |
| %starcore_nation_members% | 成员数量 |
| %starcore_nation_claims% | 领土数量 |
| %starcore_treasury% | 国库余额 |
| %starcore_title% | 玩家称号 |
| %starcore_balance% | 玩家余额 |
### 圈地流程

1. **方式一：快速圈地**
   /sc n cl
   站在你想圈定的区块，直接输入命令即可。

2. **方式二：批量圈地（使用圈地工具）**
   /sc n t          # 获取圈地工具
   - 左键点击第一个角落的方块
   - 右键点击对角线的方块
   - 系统显示预览和价格
   - /sc n ok 确认圈地
   - /sc n x 取消操作

3. **查看领地**
   /sc n ls         # 查看领地列表
   /sc n here       # 查看当前区块主人

**费用说明：**
- 圈地费用 = 基础费用 + 距离加成 + 生物群系加成
- 距离世界中心越远，费用越高
- 不同生物群系价格不同

---

## 城邦命令

### /sc n city (别名: /sc nation city)

| 命令 | 说明 | 权限 |
|------|------|------|
| /sc n city c <名称> | 创建城邦 | starcore.city.create |
| /sc n city ls | 查看城邦列表 | - |
| /sc n city info <名称> | 查看城邦信息 | - |

---

## 经济命令

### /sc economy (别名: /sc eco, /sc 经济)

| 命令 | 说明 | 别名 | 权限 |
|------|------|------|------|
| /sc eco b | 查看个人余额 | balance | - |
| /sc eco g <玩家> <金额> | 给予金钱 | give | starcore.economy.admin |
| /sc eco t <玩家> <金额> | 扣除金钱 | take | starcore.economy.admin |
| /sc eco s <玩家> <金额> | 设置余额 | set | starcore.economy.admin |
| /sc eco top | 财富排行榜 | - | - |
| /sc eco pay <玩家> <金额> | 转账给玩家 | - | - |

---

## 国库命令

### /sc treasury (别名: /sc tr, /sc 国库)

| 命令 | 说明 | 别名 | 权限 |
|------|------|------|------|
| /sc tr s | 查看国库状态 | status | - |
| /sc tr d <金额> | 存入国库 | deposit | - |
| /sc tr w <金额> | 从国库取款 | withdraw | starcore.treasury.withdraw |
| /sc tr tax | 查看税收记录 | - | - |
| /sc tr top | 国库排行 | - | - |

---

## 政体命令

### /sc government (别名: /sc gov, /sc 政体)

| 命令 | 说明 | 别名 | 权限 |
|------|------|------|------|
| /sc gov i | 查看当前政体 | info | - |
| /sc gov set <类型> | 设置政体 | set | starcore.government.set |

### 政体类型

| 类型 | 中文名 | 说明 |
|------|--------|------|
| monarchy | 君主制 | 君主拥有最高权力 |
| republic | 共和制 | 多人协商决策 |
| dictatorship | 独裁制 | 单一领导人 |
| theocracy | 神权制 | 宗教领袖统治 |
| democracy | 民主制 | 全民投票决策 |
| oligarchy | 寡头制 | 精英集团统治 |
| federation | 联邦制 | 多城邦联盟 |

---

## 外交命令

### /sc diplomacy (别名: /sc dip, /sc 外交)

| 命令 | 说明 | 别名 | 权限 |
|------|------|------|------|
| /sc dip s | 查看外交状态 | status | - |
| /sc dip set <国家> <关系> | 设置外交关系 | set | starcore.diplomacy.set |
| /sc dip ally <国家> | 联盟 | ally | starcore.diplomacy.ally |
| /sc dip war <国家> | 宣战 | war | starcore.diplomacy.war |
| /sc dip truce <国家> | 停战 | truce | starcore.diplomacy.truce |
| /sc dip neutral <国家> | 中立 | neutral | starcore.diplomacy.neutral |
| /sc dip ls | 外交列表 | list | - |

### 外交关系类型

| 关系 | 中文名 |
|------|--------|
| neutral | 中立 |
| friendly | 友好 |
| allied | 同盟 |
| hostile | 敌对 |
| war | 战争 |
| vassal | 附庸 |

---

## 战争命令

### /sc war (别名: /sc w, /sc 战争)

| 命令 | 说明 | 别名 | 权限 |
|------|------|------|------|
| /sc w s | 查看战争状态 | status | - |
| /sc w d <国家> <原因> | 宣战 | declare | starcore.war.declare |
| /sc w e <国家> | 结束战争 | end | starcore.war.end |

---

## 官员命令

### /sc officer (别名: /sc off, /sc 官员)

| 命令 | 说明 | 别名 | 权限 |
|------|------|------|------|
| /sc off s | 查看官员列表 | status | - |
| /sc off a <玩家> <职位> | 任命官员 | appoint | starcore.officer.appoint |
| /sc off rm <玩家> | 罢免官员 | remove | starcore.officer.remove |

### 官员职位

| 职位 | 英文名 | 主要权限 |
|------|--------|----------|
| 大将军 | marshal | 战争指挥 |
| 财政大臣 | treasurer | 国库管理 |
| 外交官 | diplomat | 外交关系 |
| 内务大臣 | steward | 国策科技 |

---

## 国策命令

### /sc policy (别名: /sc po, /sc 国策)

| 命令 | 说明 | 别名 | 权限 |
|------|------|------|------|
| /sc po t | 打开国策树 GUI | tree | - |
| /sc po s | 查看已激活国策 | status | - |
| /sc po set <国策ID> | 激活国策 | - | starcore.policy.activate |
| /sc po x <国策ID> | 停用国策 | - | starcore.policy.deactivate |

---

## 科技命令

### /sc technology (别名: /sc tech, /sc 科技)

| 命令 | 说明 | 别名 | 权限 |
|------|------|------|------|
| /sc tech t | 打开科技树 GUI | tree | - |
| /sc tech s | 查看科技状态 | status | - |
| /sc tech u <科技ID> | 研究科技 | unlock | starcore.tech.unlock |
| /sc tech x <科技ID> | 废除科技 | - | starcore.tech.revoke |

---

## 资源命令

### /sc resource (别名: /sc rsc, /sc 资源)

| 命令 | 说明 | 别名 | 权限 |
|------|------|------|------|
| /sc rsc s | 查看资源状态 | status | - |
| /sc rsc d | 查看资源区块列表 | districts | - |
| /sc rsc i [坐标] | 查看区块资源详情 | inspect | - |
| /sc rsc m [坐标] | 迁移资源区块 | migrate | - |
| /sc rsc c | 采集资源 | collect | - |

---

## 地图命令

### /sc map (别名: /sc m, /sc 地图)

| 命令 | 说明 | 别名 | 权限 |
|------|------|------|------|
| /sc m s | 查看地图状态 | status | - |
| /sc m w | 获取网页地图链接 | web | - |
| /sc m ok <编号> | 确认网页圈地 | confirm | starcore.claim.confirm |

---

## 事件命令

### /sc event (别名: /sc ev, /sc 事件)

| 命令 | 说明 | 别名 | 权限 |
|------|------|------|------|
| /sc ev ls | 查看事件列表 | list | - |
| /sc ev a <国家> <类型> <时间> | 添加事件 | add | starcore.event.add |
| /sc ev ex <国家> <类型> <时间> [格式] | 导出事件 | export | starcore.event.export |

---

## 纪元命令

### /sc era (别名: /sc 纪元)

| 命令 | 说明 | 别名 | 权限 |
|------|------|------|------|
| /sc era s | 查看纪元状态 | status | - |
| /sc era next | 进入下一纪元 | advance | starcore.epoch.advance |

---

## 时间同步命令

### /sc time (别名: /sc tm, /sc 时间)

| 命令 | 说明 | 别名 | 权限 |
|------|------|------|------|
| /sc tm s | 查看同步状态 | status | - |
| /sc tm on | 启用时间同步 | enable | starcore.time.enable |
| /sc tm off | 禁用时间同步 | disable | starcore.time.disable |

---

## PlaceholderAPI 占位符

| 占位符 | 说明 | 示例 |
|--------|------|------|
| %starcore_balance% | 玩家余额 | 1234.56 |
| %starcore_title% | 玩家称号 | 国王 |
| %starcore_kills% | 击杀数 | 156 |
| %starcore_deaths% | 死亡数 | 23 |
| %starcore_kd% | K/D 比率 | 6.78 |
| %starcore_nation% | 所属国家 | 天朝 |
| %starcore_nation_rank% | 国家排名 | 5 |
| %starcore_nation_members% | 成员数量 | 12 |
| %starcore_nation_claims% | 领土数量 | 45 |
| %starcore_treasury% | 国库余额 | 50000.0 |

---

**文档版本:** v1.0  
**最后更新:** 2026-06-24
