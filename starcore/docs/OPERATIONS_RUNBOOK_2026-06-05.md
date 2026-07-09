# STARCORE 运维手册

日期: 2026-06-05

这份文档面向服主、运维和后续继续开发的人，目标只有一个：让 STARCORE 的安装、备份、升级、回滚和验证都走同一套可重复流程。

## 运行目录边界

先把目录边界说清楚，后面所有备份和恢复都基于这两块：

1. `plugins/STARCORE`
   - 主配置：`config.yml`
   - 中文语言文件：`messages_zh_cn.yml`
   - SQLite 数据库：`starcore.db`、`starcore.db-wal`、`starcore.db-shm`
   - 各模块运行态目录：如 `nation/`、`resource/`、`territory/`、`policy/`、`resolution/`
   - 可再生缓存：`cache/avatars`、`cache/terrain/v5`

2. `plugins/map`
   - 地图静态导出目录
   - 默认来自 `config.yml` 里的 `map.export-directory: ..`
   - 它不是主数据库目录，而是地图网页导出产物目录

当前默认情况下，STARCORE 的“主数据”和“地图网页导出”是分开的，所以不要只备份其中一个。

## 首次安装

1. 把 [`target/starcore-0.1.0-SNAPSHOT.jar`](/D:/qwq/项目/mapadd/starcore/target/starcore-0.1.0-SNAPSHOT.jar) 放进服务端 `plugins` 目录。
2. 启动一次 Paper，让 STARCORE 生成 `plugins/STARCORE/config.yml` 和 `messages_zh_cn.yml`。
3. 检查这些关键项：
   - `database.*`
   - `map.export-directory`
   - `map.web.public-url`
   - `integrations.protectorapi.*`
4. 如果服务器对外提供地图网页，再确认 `plugins/map` 已生成。

## 备份

推荐直接使用脚本：

[`scripts/backup-starcore-runtime.ps1`](/D:/qwq/项目/mapadd/starcore/scripts/backup-starcore-runtime.ps1)
[`scripts/restore-starcore-runtime.ps1`](/D:/qwq/项目/mapadd/starcore/scripts/restore-starcore-runtime.ps1)
[`scripts/check-starcore-database-settings.ps1`](/D:/qwq/项目/mapadd/starcore/scripts/check-starcore-database-settings.ps1)
[`scripts/sync-protectorapi-reference.ps1`](/D:/qwq/项目/mapadd/starcore/scripts/sync-protectorapi-reference.ps1)
[`scripts/check-protectorapi-reference.ps1`](/D:/qwq/项目/mapadd/starcore/scripts/check-protectorapi-reference.ps1)
[`scripts/selfcheck-starcore-runtime-tools.ps1`](/D:/qwq/项目/mapadd/starcore/scripts/selfcheck-starcore-runtime-tools.ps1)
[`scripts/import-starcore-spark-profile.ps1`](/D:/qwq/项目/mapadd/starcore/scripts/import-starcore-spark-profile.ps1)
[`scripts/verify-starcore-release.ps1`](/D:/qwq/项目/mapadd/starcore/scripts/verify-starcore-release.ps1)

### 常用例子

备份本仓库默认测试服的 STARCORE 运行态：

```powershell
.\scripts\backup-starcore-runtime.ps1
```

备份指定生产服目录，并额外打包 zip：

```powershell
.\scripts\backup-starcore-runtime.ps1 `
  -DataDir 'D:\Minecraft\MyServer\plugins\STARCORE' `
  -AsZip
```

如果你希望把地形/头像缓存也一起打包：

```powershell
.\scripts\backup-starcore-runtime.ps1 `
  -DataDir 'D:\Minecraft\MyServer\plugins\STARCORE' `
  -IncludeCache `
  -AsZip
```

如果你把地图导出目录改到了默认值以外的位置：

```powershell
.\scripts\backup-starcore-runtime.ps1 `
  -DataDir 'D:\Minecraft\MyServer\plugins\STARCORE' `
  -MapExportDir 'D:\Minecraft\MyServer\web\starcore-map' `
  -AsZip
```

### 备份脚本实际会做什么

- 默认备份 `plugins/STARCORE` 下除 `cache/` 外的全部内容
- 如果检测到默认地图目录 `plugins/map`，会一并备份
- 会额外写出 `backup-manifest.json`
- `-AsZip` 会在输出目录再生成一个 zip 包

默认输出目录：

```text
target/runtime-backups/starcore-runtime-backup-时间戳/
```

## 升级前检查

升级 STARCORE jar 之前，建议固定按这个顺序做：

1. 停服，或者至少确保不会再有玩家写入领地/国家状态。
2. 执行一次运行态备份。
3. 如果当前使用 SQLite，确认以下文件一起存在：
   - `starcore.db`
   - `starcore.db-wal`
   - `starcore.db-shm`
4. 替换 jar。
5. 开服后先看控制台：
   - `STARCORE ... enabled.`
   - `STARCORE database ready: ...`
   - `Registered STARCORE public API.`
6. 进服或控制台执行：
   - `/starcore status`
   - `/starcore modules`
   - `/starcore map web`

## 回滚 / 恢复

回滚时不要只恢复单个 `.db` 文件，建议直接回整份运行态备份。

推荐顺序：

1. 关服。
2. 删除或移走当前的：
   - `plugins/STARCORE`
   - `plugins/map`（如果你也要回滚地图导出）
3. 从备份中恢复：
   - `STARCORE/` -> `plugins/STARCORE`
   - `map/` -> `plugins/map`
4. 如果是 SQLite，确保 `starcore.db`、`starcore.db-wal`、`starcore.db-shm` 一起恢复。
5. 启动服务器。
6. 用 `/starcore status` 和地图页做一次基本检查。

### 推荐恢复命令

把最近一次备份恢复到默认测试服目录：

```powershell
.\scripts\restore-starcore-runtime.ps1
```

把指定备份恢复到生产服目录，并允许替换当前运行态：

```powershell
.\scripts\restore-starcore-runtime.ps1 `
  -BackupPath 'D:\Backups\starcore-runtime-backup-20260605-172643.zip' `
  -DataDir 'D:\Minecraft\MyServer\plugins\STARCORE' `
  -MapExportDir 'D:\Minecraft\MyServer\plugins\map' `
  -ReplaceExisting
```

说明：

- `-ReplaceExisting` 会先把当前 `plugins/STARCORE` 和 `plugins/map` 挪到 `target/runtime-restores/pre-restore-*`
- 不带 `-ReplaceExisting` 时，如果目标目录已存在，脚本会直接拒绝执行
- 如果备份里没有 `map/`，脚本只恢复 `STARCORE/`

## SQLite 与 MySQL

当前 STARCORE 默认使用 SQLite，本地单服直接可用；大服再切 MySQL。

### 从 SQLite 切到 MySQL 前

1. 先做一次完整运行态备份。
2. 预先创建 MySQL 数据库和账号。
3. 修改 `plugins/STARCORE/config.yml`：
   - `database.type: mysql`
   - `database.mysql.host`
   - `database.mysql.port`
   - `database.mysql.database`
   - `database.mysql.username`
   - `database.mysql.password`
4. 首次切换建议先在测试服验证。
5. 生产服如果希望数据库失败就阻止插件继续启用，可以把 `database.fail-fast` 改为 `true`。

### 数据库预检查

在改数据库配置或换库前，先跑：

```powershell
.\scripts\check-starcore-database-settings.ps1
```

如果你要检查指定服务器目录：

```powershell
.\scripts\check-starcore-database-settings.ps1 `
  -DataDir 'D:\Minecraft\MyServer\plugins\STARCORE'
```

它当前会做这些事情：

- 读取 `config.yml` 里的 `database.*`
- 判断当前生效类型是 `sqlite` 还是 `mysql`
- 对 SQLite 检查 `starcore.db` 及 `-wal / -shm` 文件组
- 对 MySQL 检查 host / port / database / username
- 对 MySQL 额外做一次轻量 TCP 可达性检查
- 输出 JSON，适合后续接进自动化

## ProtectorAPI 参考契约检查

如果准备升级 `references/ProtectorAPI`，或者担心外部保护桥接假设已经漂移，先跑同步，再跑契约检查：

```powershell
.\scripts\sync-protectorapi-reference.ps1
```

同步脚本会：

- 确认本地 `references/ProtectorAPI` checkout 存在
- 在需要时补 origin remote
- 执行 `git fetch origin`
- 记录当前 `HEAD`、`origin/main`、ahead/behind、working tree clean 状态
- 输出 JSON 摘要到 `target/protectorapi-reference-syncs/sync-时间戳/`

然后执行：

```powershell
.\scripts\check-protectorapi-reference.ps1
```

它当前会检查：

- `references/ProtectorAPI` checkout 是否存在
- `ProtectorAPI.findModule(Location)` 是否还在
- `ProtectorAPI.getAllAvailableProtectionModules()` 是否还在
- `IProtectionModule.getPluginName()` / `getProtectionRangeInfo(Location)` 是否还在
- `IProtectionRange.getDisplayName()` / `getId()` 是否还在
- `src/main/resources/plugin.yml` 里的 `softdepend: [ProtectorAPI]` 是否还在
- `ProtectorApiBridgeContract` 是否仍绑定同一套窄接口

默认会写出一份 JSON 摘要到：

```text
target/protectorapi-reference-checks/check-时间戳/protectorapi-reference-check-summary.json
```

## 运维脚本自检

如果你修改了这些运维脚本，或者准备发版前想确认高风险分支没有坏掉，可以直接跑：

```powershell
.\scripts\selfcheck-starcore-runtime-tools.ps1
```

它当前会自动验证三件事：

- 先用临时运行态生成一份新的 backup zip
- 再用这份 zip 验证 `restore-starcore-runtime.ps1 -ReplaceExisting`
- 再构造一个 MySQL 配置样本，验证数据库预检查的 warning 分支
- 再执行一次 ProtectorAPI reference sync，确认本地参考 checkout 仍可正常 fetch 和汇总
- 再执行一次 ProtectorAPI 参考契约检查，确认本地参考 checkout 和桥接契约仍对齐

默认产物目录：

```text
target/runtime-tool-selfchecks/selfcheck-时间戳/
```

其中最重要的结果文件是：

```text
runtime-tool-selfcheck-summary.json
```

### 一个重要原则

即使越来越多状态已经进入 SQL，也仍然建议备份整个 `plugins/STARCORE`，不要只备份数据库文件。原因是：

- 配置和语言文件也在这里
- 仍可能存在 namespace 文件回退面
- 地图缓存、头像缓存和一些模块辅助状态也在这里

## 哪些目录可以删

这些目录/产物属于可再生面，必要时可以删：

- `plugins/STARCORE/cache/avatars`
- `plugins/STARCORE/cache/terrain/v5`
- `plugins/map`

删除后的影响：

- 头像缓存会重新拉取
- 地形瓦片会重新渲染
- 地图静态网页会重新导出

不建议随意删：

- `plugins/STARCORE/starcore.db*`
- `plugins/STARCORE/config.yml`
- `plugins/STARCORE/messages_zh_cn.yml`
- 各模块 namespace 目录

## 发布前验证

本仓库当前统一验证链路是：

1. 轻量验证：

```powershell
.\scripts\verify-starcore-release.ps1
```

2. 一站式完整发版验证 + 证据包 + 平台成品包（正式公开发版前推荐）：

```powershell
.\scripts\import-starcore-spark-profile.ps1 -ReportPath <spark-report-file-or-directory> -ReportUrl <spark-report-url> -SourceLabel production-profile
```

```powershell
.\scripts\verify-starcore-release.ps1 -IncludeSmoke -ProtectorApiSmoke -BrowserSmoke -BuildEvidencePack -BuildReleaseChannelAssets -SparkProfileSummaryPath <spark-profile-summary.json> -RequireRealSparkProfile
```

3. 完整验证：

```powershell
.\scripts\verify-starcore-release.ps1 -IncludeSmoke -ProtectorApiSmoke -BrowserSmoke
```

4. 证据包打包：

```powershell
.\scripts\build-starcore-release-evidence-pack.ps1
```

5. 发布渠道成品包：

```powershell
.\scripts\build-starcore-release-channel-assets.ps1
```

6. 如果需要手工拆开跑，顺序保持：
   - `mvn -q test`
   - `mvn -q package`
   - `.\scripts\check-map-hud-contract.ps1 -RequireMirrorRoots`
   - `.\scripts\selfcheck-starcore-runtime-tools.ps1`
   - `.\scripts\smoke-starcore-paper-integration.ps1 -TimeoutSeconds 360 -ProtectorApiSmoke -BrowserSmoke`
   - `.\scripts\check-starcore-performance-budget.ps1 -SmokeSummaryJsonPath <smoke-summary.json>`
   - `.\scripts\import-starcore-spark-profile.ps1 -ReportPath <spark-report-file-or-directory> -ReportUrl <spark-url> -SourceLabel production-profile`
   - `.\scripts\build-latest-verification-summary.ps1 -SmokeSummaryJsonPath <smoke-summary.json> -PerformanceBudgetSummaryPath <performance-budget-summary.json> -HudContractSummaryPath <map-hud-contract-summary.json> -RuntimeToolSelfcheckSummaryPath <runtime-tool-selfcheck-summary.json> -SparkProfileSummaryPath <spark-profile-summary.json>`

说明：

- 深烟测会写 `target/smoke-harness-<timestamp>/smoke-summary.json`
- 完整深烟测的资源区块迁移 marker 应包含 `feedbackSound:<n>+worldSound:<n>+particles:<n>+actionbar:<n>+title:<n>+bossbar:<n>+bossbarHide:<n>`；这些字段分别证明 `foundation.feedback` 共享 Bukkit adapter 已从配置化语义事件触达玩家侧声音、World 侧声音、World 侧粒子、actionbar、title、BossBar 展示和 BossBar 自动清理
- 资源区块迁移阻塞解释现在由 `NationResourceDistrictCommandSupport.explanation(...)` 统一生成；改动迁移状态、余额/领袖/在线判断、网页资源区块 API 或 marker metadata 时，至少跑 `NationResourceDistrictCommandSupportTest`、`MapResourceDistrictEndpointTest`、`ResourceDistrictMapMetadataSupportTest`，确认 `ready`、`awaiting-target`、`waiting-depletion`、`insufficient-balance` 等状态仍输出 `explanation.state/severity/summary/reasons/details`
- 改动网页国家详情或资源区块情报面时，完整 Browser smoke 还应输出 `resourceExplanation=<state>:<count>`，且 count 大于 0；当前通过值示例为 `resourceExplanation=player-offline:5`，证明 `#nation-detail-panel [data-resource-explanation-state]` 已渲染并在筛选/快捷动作后保持存在
- 改动资源 explanation 前端 renderer、severity 样式或状态字段映射时，完整 Browser smoke 还应输出 `resourceExplanationFixture=ready+awaiting-target+waiting-depletion+insufficient-balance+player-offline:5`；该 fixture 会在真实 Browser 中复用生产 `resourceMigrationExplanation(...)` 与 `renderResourceMigrationExplanation(...)`，并验证 success/info/error computed style
- 改动资源 explanation 真实数据来源、资源区块 service 造景、viewer snapshot 或认证视图时，完整 Paper 与 Browser smoke 还应输出 `resourceExplanationRuntime=ready+awaiting-target+waiting-depletion+insufficient-balance+player-offline:5`；该 marker 会用真实 `/api/map/snapshot` 覆盖在线创始人、在线低余额官员和离线访问者视图，证明多状态不是只靠受控 DOM fixture
- 改动国家详情最近操作日志、事件 metadata 或官员/治理日志时，完整 Browser smoke 还应输出 `recentLogFilter=<category>:<count>`，且 count 大于 0；当前通过值示例为 `recentLogFilter=resource:3`，脚本内部会确认分类筛选后所有条目类别一致，并且筛选后数量小于总数
- 改动后端事件查询、按资源/财政/战争/官员/外交/策略/领地/国家深查、最近日志页或 `/api/map/events` 时，完整 Browser smoke 还应输出 `eventQuery=<filter>:<count>`，且 count 大于 0；当前通过值示例为 `eventQuery=resource:9`，脚本会用真实 Browser 登录态请求 `/api/map/events`，证明接口可查询 metadata 5 条摘要之外的完整事件结果
- 改动完整事件日志 UI、深日志筛选、分页或 `/api/map/events` DOM 消费链路时，完整 Browser smoke 还应输出 `eventLedger=<filter>:<count>`，且 count 大于 0；当前通过值示例为 `eventLedger=resource:3`，脚本会真实点击国家详情里的完整事件日志入口、分类、当前资源区块筛选、时间范围和分页按钮，证明 DOM 渲染与后端查询都可用
- 改动完整事件日志 `query/actor/reason` 搜索、搜索框 DOM、后端上下文匹配或导出 URL 时，完整 Browser smoke 还应输出 `eventSearch=<encodedQuery>:<count>`，且 count 大于 0；当前通过值示例为 `eventSearch=%E8%B5%84%E6%BA%90:3`，脚本会输入搜索词并确认 DOM 状态、条目内容和导出 URL 都保留搜索条件
- 改动完整事件日志上下文 chip、`details` 渲染、actor/reason 别名映射或一键追责行为时，完整 Browser smoke 还应输出 `eventContext=<field>-<value>:<count>`、`eventReason=reason-<value>:<count>`、`eventJump=reason-<value>:<count>` 和 `eventFacts=amount+balance:<count>`，且 count 大于 0；当前通过值示例为 `eventContext=actor-SmokeAuditor:1`、`eventReason=reason-browser-context-chip:1`、`eventJump=reason-browser-context-chip:1`、`eventFacts=amount+balance:2`，脚本会在主 smoke 国家写入带 `actor/reason/policy/target/amount/balance` 的审计事件，真实点击 actor 与 reason chip，再点击跨事件跳转，并确认导出 URL 保留当前上下文筛选
- 改动完整事件日志和资源 explanation、官员授权矩阵或运营处理入口的联动时，完整 Browser smoke 还应输出 `eventOps=resource+explanation+auth+group:<count>`，且 count 大于 0；脚本会在资源事件日志条目里验证 `data-event-operation-link`、typed resource explanation、`resource-migration` 授权角色和 `open-operation-group` 处理同类问题按钮，并确认旧 web 资源指挥面仍未恢复
- 改动完整事件日志事件族运营联动、事件 `details` 到处理入口的映射或 `event-ledger-operation-scope` 行为时，完整 Browser smoke 还应输出 `eventOpsFamilies=finance+war+officer+diplomacy+strategy+territory+nation:7`；脚本会在财政、战争、官员、外交、策略、领地、国家事件条目里验证 `data-event-operation-family/action/auth-key/filter/reason/query` 和同类处理按钮，确认它们不是只渲染上下文 chip
- 改动真实命令来源事件 context、战争/官员/国库/外交/策略命令原因参数或事件写入链路时，完整 Paper smoke 还应输出 `eventCommandSources=war+officer+treasury:6`、`eventCommandSourcesExtended=war+officer+treasury+diplomacy+strategy:14`、`warCommandEvents=founderDeclare+diplomatEnd+marshalDeclare:3`、`governanceCommandEvents=officerAppoint+officerRemove+treasuryWithdraw:3`、`strategyCommandEvents=policySet+policyClear+technologyUnlock+technologyRevoke:6` 和 `diplomacyCommandEvents=founderSet+diplomatSet:2`；脚本会在真实 `/starcore diplomacy`、`/starcore policy`、`/starcore technology`、`/starcore war`、`/starcore officer`、`/starcore treasury withdraw` 命令后读取 `EventService.eventsOf(...)`，确认 context 来自命令执行结果而不是 audit fixture
- 改动完整事件日志移动端样式、侧栏滚动、搜索区、上下文 chip、facts pill 或截图证据链时，完整 Browser smoke 还应输出 `eventMobile=390x844:<count>`，且 count 大于 0；当前通过值示例为 `eventMobile=390x844:6`，脚本会切换到 390x844 mobile viewport，确认搜索、当前上下文 chip、跨事件跳转按钮、上下文 chip 和 `amount/balance` facts 可见、无横向溢出、无控件重叠，并写出 `browserMobileScreenshot`
- 改动完整事件日志分类、事件族上下文、财政/战争/官员/外交/策略/领地/国家审计字段或移动端证据链时，完整 Browser smoke 还应输出 `eventFamilies=finance+war+officer+diplomacy+strategy+territory+nation:7` 与 `eventFamilyMobile=finance+war+officer+diplomacy+strategy+territory+nation:7`；脚本会在真实运行服写入 `treasury.withdraw`、`war.declared`、`officer.appointed`、`diplomacy.updated`、`policy.set`、`territory.claimed`、`nation.created` 七类上下文事件，并在桌面 DOM 与 390x844 移动视口中逐类验证 actor/reason chip、只读 facts 和跨事件跳转按钮
- 改动完整事件日志 CSV/JSON 导出、导出按钮或 `/api/map/events?format=...` 时，完整 Browser smoke 还应输出 `eventLedgerExport=csv+json`；脚本会拦截导出按钮的 `window.open`，确认 URL 指向 `/api/map/events` 并携带当前 `filter/range/resourceId/query/actor/reason/format`
- 改动官员配置、国家领土 metadata 或 Web 国家详情授权区时，完整 Browser smoke 还应输出 `officerAuth=marshal+treasurer+diplomat+steward:9` 和 `officerAccess=founder:9`（或本轮明确预期的访问者状态）；当前默认值证明资源迁移、国库、外交、战争、政策、科技 9 个授权项及当前访问者逐项授权结果都已由真实 snapshot metadata 渲染进 DOM
- 完整深烟测的网页圈地 marker 应包含 `webClaimFeedback=confirmSound:<n>+confirmActionbar:<n>+confirmBossbar:<n>` 和 `onlineWebClaim=...+pendingSound:<n>+pendingActionbar:<n>+pendingBossbar:<n>+cancelSound:<n>+cancelActionbar:<n>+cancelBossbar:<n>+cancelTyped:1`；这些字段证明 `/starcore map confirm <编号>`、在线网页 pending 通知和 `/starcore map cancel <编号>` 也复用 `nation.claims.feedback` 配置 profile，且取消 pending 会返回 typed explanation
- 完整深烟测的圈地解释 marker 应包含 `claimExplanation=externalProtection:1`；这些字段证明 `/api/map/claim/preview` 与 `/api/map/claim/request` 在外部保护冲突时仍输出结构化 `explanation.state/severity/summary/reasons/details`，命令、圈地工具和 web 不需要各自硬写失败解释
- 完整深烟测的国家操作 marker 应包含 `nationOperationFeedback=operationSound:<n>+operationActionbar:<n>+operationTitle:<n>+operationBossbar:<n>`；这些字段证明国家创建、城邦创建、改名提案和提案签署复用 `nation.operations.feedback` 配置 profile
- 完整深烟测的策略操作 marker 应包含 `strategyFeedback=strategySound:<n>+strategyActionbar:<n>+strategyTitle:<n>+strategyBossbar:<n>`；这些字段证明政策激活/清除、科技解锁/撤销和战争宣告/停战通过真实 `/starcore policy`、`/starcore technology`、`/starcore war` 命令复用 `nation.strategy.feedback` 配置 profile；当前策略官员授权还要求 marker 包含 `policyOfficer=steward:clear+set`、`technologyOfficer=steward:revoke+unlock` 和 `warOfficer=marshal:declare+diplomat:end`
- 完整深烟测的治理操作 marker 应包含 `governanceFeedback=governanceSound:<n>+governanceActionbar:<n>+governanceTitle:<n>+governanceBossbar:<n>+...+diplomacy:<n>+officer:<n>+treasury:<n>`；这些字段证明外交关系变化、官员任免、国库大额收支通过真实 `/starcore diplomacy`、`/starcore officer`、`/starcore treasury` 命令分别复用 `nation.diplomacy.feedback`、`nation.officers.feedback`、`nation.treasury.feedback` 配置 profile；该 marker 写出时短 BossBar 可能尚未自动隐藏，因此只要求展示计数，不要求 `governanceBossbarHide` 大于 0
- HUD 契约检查会写 `target/map-hud-contract-checks/check-<timestamp>/map-hud-contract-summary.json`
- 性能预算检查会写 `target/performance-budget-checks/check-<timestamp>/performance-budget-summary.json`；预算阈值和回退阈值来自 `scripts/starcore-performance-budgets.json`
- 性能预算当前支持 suite time、method-level runtime、dedicated batch sample 和 smoke artifact size 证据；method-level runtime / batch sample 都来自 Surefire XML 的 `<testcase time>`，用于盯住 claim lookup、map render、SQL flush 关键路径
- 当前 dedicated batch sample 预算为 `claim_lookup_batch`、`map_render_batch`、`sql_flush_batch`；样本测试分别覆盖 web claim preview/submit/confirm 批量路径、terrain PNG/binary 冷热缓存与 disk cache 路径、SQL balance deposit/withdraw/flush/reload 批量路径
- 性能预算检查默认按 `scripts/starcore-performance-budgets.json` 的 `trend.baselineWindowSize` 和 `trend.aggregation` 读取最近多份 `performance-budget-summary.json`，当前为最近 5 份 median；summary 会写 `baseline.mode`、`baseline.aggregation`、`baseline.sampleCount`、`baseline.summaryPaths[]` 和 `baseline.comparisons[]`
- 如果需要复现某次单基线判断，可显式传 `-BaselineSummaryPath`；此时 summary 会写 `baseline.mode=explicit`、`baseline.sampleCount=1`
- 显式 baseline 模式下，顶层 `baselineSummaryPath` 应是完整 JSON 路径，`baselineSummaryPaths` 应保持数组；如果这里退化成单个盘符或字符串字符，说明脚本输出类型回归了
- 轻量验证不会复用旧 smoke 结果，生成的 `LATEST_VERIFICATION_SUMMARY.md` 会明确写出本次未包含 smoke
- 轻量验证仍会执行 claim lookup、map render、SQL flush 及对应 batch sample 的 Surefire 预算；Browser snapshot 预算只有完整 smoke 或显式传入 `-SmokeSummaryJsonPath` 时才会检查
- Spark profiling 入口会写 `target/spark-profile-imports/profile-<timestamp>/spark-profile-summary.json`；导入脚本只规范化外部 spark 报告/链接，不会自动启动服务器 profiling
- 推荐实服采集顺序：在装有 spark 的测试服或生产影子服执行 `/spark profiler start --timeout 60`，高峰/高负载路径跑完后执行 `/spark profiler stop` 和 `/spark profiler open`，再把本地导出的报告或报告目录传给 `-ReportPath`，把 spark 链接传给 `-ReportUrl`
- `-SourceLabel sample-verification` 只代表脚本集成样本，不能当作真实服性能证据；真实验收建议用 `production-profile`、`staging-profile` 或带日期/场景的 label
- `verify-starcore-release.ps1 -RequireRealSparkProfile` 是正式发版门禁：会在 Maven 和 smoke 前快速检查 Spark summary，拒绝缺失 summary、`sample-verification`、`manual`、缺本地 artifact、空 artifact 或缺 http(s) report URL
- 统一入口会在 `mvn package` 后自动执行 HUD 契约检查，当前默认要求三份 map 资产根都存在并符合 intel-only 资源区块契约：不应残留 `#resource-command-panel`、`open-resource-command` 或 `resourceCommand*`，Browser smoke 必须输出 `commandUiRemoved=true`
- 一站式命令在带 `-BuildEvidencePack` 或 `-BuildReleaseChannelAssets` 时会强制要求 `-IncludeSmoke`，并把同一轮 HUD contract summary 与 performance budget summary 传入证据包/发布渠道包；正式发版时再加 `-SparkProfileSummaryPath` 和 `-RequireRealSparkProfile`，同一份真实 spark profile summary 也会进入摘要和发布包，避免把旧 smoke、旧 HUD 门禁、旧性能预算或样本 profiling 工件误打进新包里
- Markdown 摘要脚本：[`scripts/render-starcore-smoke-summary.ps1`](/D:/qwq/项目/mapadd/starcore/scripts/render-starcore-smoke-summary.ps1)
- 最新自动汇总验证面：[`docs/LATEST_VERIFICATION_SUMMARY.md`](/D:/qwq/项目/mapadd/starcore/docs/LATEST_VERIFICATION_SUMMARY.md)
- 发版证据包脚本会把 jar、验证摘要、发布文档、smoke summary、Map HUD contract summary、performance budget summary、可选 Spark profile summary/artifact、runtime selfcheck、浏览器工件和 Paper 日志收拢到 `target/release-evidence-<timestamp>/`
- 如果不是走一站式命令，完整验证跑完后，建议立刻执行一次 `.\scripts\build-starcore-release-evidence-pack.ps1`，把对外分发需要的证据固定成同一包
- 发布渠道成品包脚本会把 Modrinth / Hangar / SpigotMC 可直接粘贴的描述、更新日志模板、截图、当前 jar、Map HUD contract summary、performance budget summary 和可选 Spark profile summary/artifact 收拢到 `target/release-channel-assets-<timestamp>/`，并在 `UPLOAD_GUIDE.md` 与 manifest snapshot 里标记 HUD 契约、性能预算、baseline trend 和 Spark profiling 状态
- 如果不是走一站式命令，准备上架平台时建议在证据包之后再执行一次 `.\scripts\build-starcore-release-channel-assets.ps1`

## 当前推荐的运维入口

日常维护优先看这几份：

- [`docs/LATEST_VERIFICATION_SUMMARY.md`](/D:/qwq/项目/mapadd/starcore/docs/LATEST_VERIFICATION_SUMMARY.md)
- [`docs/RELEASE_CHECKLIST_2026-06-05.md`](/D:/qwq/项目/mapadd/starcore/docs/RELEASE_CHECKLIST_2026-06-05.md)
- [`docs/CONTINUATION_PLAN_2026-06-05.md`](/D:/qwq/项目/mapadd/starcore/docs/CONTINUATION_PLAN_2026-06-05.md)
