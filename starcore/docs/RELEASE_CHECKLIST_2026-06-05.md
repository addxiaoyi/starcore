# STARCORE 发布检查清单

日期: 2026-06-05

这份清单只做一件事：把 STARCORE 每次发版前该确认的内容压成同一条流水线。

## 1. 代码与配置

- [ ] `README.md` 已同步当前功能、命令、依赖边界和验证方式
- [ ] `config.yml` 默认值完整，中文注释没有遗漏
- [ ] `messages_zh_cn.yml` 与命令/提示文本保持一致
- [ ] `plugin.yml` 的版本、命令、`softdepend: [ProtectorAPI]` 正确
- [ ] 如本次改动涉及游戏内效果，确认 `foundation.feedback` 通用组件仍只读取配置 profile，业务服务只发语义事件，没有把 sound/particle/actionbar/title/BossBar 表现硬编码回模块内部
- [ ] `references/ProtectorAPI` 当前 checkout 已执行同步脚本并确认可正常 fetch / 汇总
- [ ] `ProtectorApiBridgeContract` 与 `references/ProtectorAPI` 当前 checkout 仍对齐

## 2. 构建

- [ ] 优先执行统一入口：

```powershell
.\scripts\verify-starcore-release.ps1
```

- [ ] 如需一站式完整发版验证 + 证据包 + 平台成品包，先导入真实 Spark profile，再执行带门禁的正式命令：

```powershell
.\scripts\import-starcore-spark-profile.ps1 -ReportPath <spark-report-file-or-directory> -ReportUrl <spark-report-url> -SourceLabel production-profile
```

```powershell
.\scripts\verify-starcore-release.ps1 -IncludeSmoke -ProtectorApiSmoke -BrowserSmoke -BuildEvidencePack -BuildReleaseChannelAssets -SparkProfileSummaryPath <spark-profile-summary.json> -RequireRealSparkProfile
```

- [ ] 如需只做完整发版验证，执行：

```powershell
.\scripts\verify-starcore-release.ps1 -IncludeSmoke -ProtectorApiSmoke -BrowserSmoke
```

- [ ] 如手工执行，先跑 `mvn -q test`
- [ ] 确认测试为 `0 failures / 0 errors`
- [ ] 再跑 `mvn -q package`
- [ ] 确认 jar 产物存在：`target/starcore-0.1.0-SNAPSHOT.jar`
- [ ] 统一入口会自动执行 HUD 契约门禁；如需单独检查，执行：

```powershell
.\scripts\check-map-hud-contract.ps1 -RequireMirrorRoots
```

- [ ] 确认 `target/map-hud-contract-checks/check-*/map-hud-contract-summary.json` 为 `status=ok`
- [ ] 统一入口会自动执行性能预算门禁；如需单独检查，执行：

```powershell
.\scripts\check-starcore-performance-budget.ps1 -SmokeSummaryJsonPath <smoke-summary.json>
```

- [ ] 确认 `target/performance-budget-checks/check-*/performance-budget-summary.json` 没有 `status=error`
- [ ] 完整 smoke 发版时确认性能预算所有 configured checks 都 pass 且 `baseline.status` 不是 `error`；轻量验证时 Browser snapshot 可以是 `not_included`，但不得复用旧 smoke summary
- [ ] 默认趋势检查应显示 `baseline.mode=history`、`baseline.aggregation=median`、`baseline.sampleCount` 大于 `0`；如使用 `-BaselineSummaryPath`，确认是有意切换到 `baseline.mode=explicit`
- [ ] 检查 performance budget summary 已包含 suite time、method-level runtime 和 dedicated batch sample 三层预算，例如 `claim_lookup_cases`、`map_render_cases`、`sql_flush_cases`、`claim_lookup_batch`、`map_render_batch`、`sql_flush_batch`
- [ ] 如使用显式 baseline，确认顶层 `baselineSummaryPath` 是完整 JSON 路径，`baselineSummaryPaths` 是数组，且 `baseline.summaryPath` 指向同一份 baseline summary
- [ ] 如本次做真实服性能验收，执行 `import-starcore-spark-profile.ps1` 导入 spark 报告，并在后续摘要/打包命令里显式传 `-SparkProfileSummaryPath <spark-profile-summary.json>`
- [ ] 正式打包发版时必须加 `-RequireRealSparkProfile`；该门禁会拒绝缺失 summary、`sourceLabel=sample-verification`、`sourceLabel=manual`、缺本地 artifact 或缺 http(s) report URL 的 Spark 证据
- [ ] 如果摘要里的 Spark profile `source=sample-verification`，只能说明脚本入口通过，不能当作真实服 profiling 证据

## 3. 深烟测

- [ ] 完整验证模式下，执行：

```powershell
.\scripts\smoke-starcore-paper-integration.ps1 -TimeoutSeconds 360 -ProtectorApiSmoke -BrowserSmoke
```

- [ ] Paper 日志里出现 `STARCORE_SMOKE_PASS`
- [ ] Browser 日志里出现 `STARCORE_BROWSER_SMOKE_PASS`
- [ ] Web claim 结果通过
- [ ] Browser smoke marker 包含 `recentLogFilter=<category>:<count>`，且 count 大于 0；这证明国家详情最近操作日志分类筛选在真实 Browser DOM 中可点击、可缩小结果集
- [ ] Browser smoke marker 包含 `eventQuery=<filter>:<count>`，且 count 大于 0；这证明 `/api/map/events` 在真实 Browser 认证态里可按国家、分类、类型、资源区块或时间窗口查询完整事件流
- [ ] Browser smoke marker 包含 `eventLedger=<filter>:<count>`，且 count 大于 0；这证明国家详情完整事件日志 UI 已真实点击、加载、筛选并渲染后端事件流
- [ ] Browser smoke marker 包含 `eventSearch=<encodedQuery>:<count>`，且 count 大于 0；这证明国家详情完整事件日志搜索框、DOM 状态、后端结果和导出 URL 已联动
- [ ] Browser smoke marker 包含 `eventContext=<field>-<value>:<count>`，且 count 大于 0；这证明国家详情完整事件日志上下文 chip 已真实点击并驱动 `actor/reason/query` 参数
- [ ] Browser smoke marker 包含 `eventReason=reason-<value>:<count>`，且 count 大于 0；这证明 reason chip 已真实点击并驱动 `/api/map/events?reason=...`
- [ ] Browser smoke marker 包含 `eventJump=reason-<value>:<count>`，且 count 大于 0；这证明当前上下文筛选可一键横跳完整事件集，并保留同一 `reason` 参数
- [ ] Browser smoke marker 包含 `eventOps=resource+explanation+auth+group:<count>`，且 count 大于 0；这证明资源类事件日志已联动 typed resource explanation、资源迁移授权和运营处理组入口
- [ ] Browser smoke marker 包含 `eventOpsFamilies=finance+war+officer+diplomacy+strategy+territory+nation:7`；这证明财政、战争、官员、外交、策略、领地、国家事件日志已渲染运营联动卡，并可用 family + reason/query 追踪同类问题
- [ ] Paper smoke marker 包含 `eventCommandSources=war+officer+treasury:6`；这证明战争、官员、国库支出三类上下文来自真实命令写入的事件，而不是只靠 smoke audit fixture
- [ ] Paper smoke marker 包含 `eventCommandSourcesExtended=war+officer+treasury+diplomacy+strategy:14`；这证明战争、官员、国库、外交、策略五类真实命令来源事件都进入了结构化 context
- [ ] Paper smoke marker 包含 `strategyCommandEvents=policySet+policyClear+technologyUnlock+technologyRevoke:6`；这证明政策激活/清除和科技解锁/撤销都写进了真实事件上下文
- [ ] Paper smoke marker 包含 `diplomacyCommandEvents=founderSet+diplomatSet:2`；这证明创始人和外交官的真实外交直设命令也写进了结构化 context
- [ ] Paper smoke marker 包含 `warCommandEvents=founderDeclare+diplomatEnd+marshalDeclare:3`；这证明创始人宣战、外交官停战、元帅宣战三条真实战争命令都写入结构化 context
- [ ] Paper smoke marker 包含 `governanceCommandEvents=officerAppoint+officerRemove+treasuryWithdraw:3`；这证明官员任命、官员移除、财务官国库支出三条真实治理命令都写入结构化 context
- [ ] Browser smoke marker 包含 `eventMobile=390x844:<count>`，且 count 大于 0；这证明完整事件日志搜索、上下文 chip、facts pill 和跨事件按钮在移动端窄屏下可见、无横向溢出、无控件重叠
- [ ] Browser smoke marker 包含 `eventFamilies=finance+war+officer+diplomacy+strategy+territory+nation:7`；这证明完整事件日志已在真实 Browser DOM 中覆盖财政、战争、官员、外交、策略、领地、国家七类上下文事件族
- [ ] Browser smoke marker 包含 `eventFamilyMobile=finance+war+officer+diplomacy+strategy+territory+nation:7`；这证明七类上下文事件族已进入 390x844 移动端窄屏基线
- [ ] Browser smoke marker 包含 `eventFacts=amount+balance:<count>`，且 count 大于 0；这证明金额/余额等上下文字段以只读 facts 分组渲染，没有误变成追责按钮
- [ ] Browser smoke marker 包含 `eventLedgerExport=csv+json`；这证明完整事件日志 CSV/JSON 导出按钮已真实点击，且导出 URL 沿用当前筛选状态，包括 `filter/range/resourceId/query/actor/reason`
- [ ] Browser smoke marker 包含 `officerAuth=marshal+treasurer+diplomat+steward:9`；这证明 Web 国家详情已用真实 snapshot metadata 渲染资源迁移、国库、外交、战争、政策、科技授权官职矩阵
- [ ] Browser smoke marker 包含 `officerAccess=founder:9` 或本轮明确预期的访问者状态；这证明 Web 国家详情已渲染当前访问者逐项授权结果，而不是只显示静态角色清单
- [ ] Browser smoke marker 包含 `resourceExplanation=<state>:<count>`，且 count 大于 0；这证明国家详情里的资源区块 explanation DOM 已按 metadata 渲染
- [ ] Browser smoke marker 包含 `resourceExplanationFixture=ready+awaiting-target+waiting-depletion+insufficient-balance+player-offline:5`；这证明资源 explanation 多状态 fixture 仍复用生产 renderer，并覆盖 success/info/error severity 样式
- [ ] Browser smoke marker 和 Paper smoke marker 均包含 `resourceExplanationRuntime=ready+awaiting-target+waiting-depletion+insufficient-balance+player-offline:5`；这证明多状态 explanation 已来自真实运行服 `/api/map/snapshot` 和 Paper resource district 造景，不只是受控 DOM fixture
- [ ] 资源区块迁移 marker 包含 `feedbackSound:<n>+worldSound:<n>+particles:<n>+actionbar:<n>+title:<n>+bossbar:<n>+bossbarHide:<n>`，且七个计数均大于 0，`bossbarHide` 不小于 `bossbar`
- [ ] 资源区块迁移 marker 包含 `officerMigration=marshal:member+gui+target+forced`，证明默认配置下非领袖 `marshal` 成员能完成 GUI 发核心、设置目标和强制迁移链路
- [ ] 如动过 `foundation.feedback`，确认上面的七类计数来自同一轮 Paper smoke，证明共享 `BukkitInGameFeedbackService` 仍能触达玩家侧 UI、World 侧效果和 BossBar 清理
- [ ] 如动过资源区块迁移/运营状态，确认 `NationResourceDistrictCommandSupportTest`、`MapResourceDistrictEndpointTest` 和 `ResourceDistrictMapMetadataSupportTest` 通过，且资源区块 API/metadata 仍暴露 `explanation.state/severity/summary/reasons/details`
- [ ] 圈地体验 marker 包含 `webClaimFeedback=confirmSound:<n>+confirmActionbar:<n>+confirmBossbar:<n>` 和 `onlineWebClaim=...+pendingSound:<n>+pendingActionbar:<n>+pendingBossbar:<n>+cancelSound:<n>+cancelActionbar:<n>+cancelBossbar:<n>+cancelTyped:1`，且确认/待确认/取消的 sound、actionbar、BossBar 展示计数均大于 0
- [ ] 圈地解释 marker 包含 `claimExplanation=externalProtection:1`；如本次改动涉及 claim preview/request，确认 web JSON 仍暴露 `explanation.state/severity/summary/reasons/details`，且旧 `message` 字段保持兼容
- [ ] 国家操作 marker 包含 `nationOperationFeedback=operationSound:<n>+operationActionbar:<n>+operationTitle:<n>+operationBossbar:<n>`，且 sound、actionbar、BossBar 计数均大于 0
- [ ] 策略操作 marker 包含 `strategyFeedback=strategySound:<n>+strategyActionbar:<n>+strategyTitle:<n>+strategyBossbar:<n>`，且政策激活/清除、科技解锁/撤销、战争宣告/停战三类命令级探针对应的 sound、actionbar、title、BossBar 计数均大于 0
- [ ] 策略操作 marker 包含 `policyOfficer=steward:clear+set`，证明默认配置下非管理员 `steward` 能清除并激活本国政策，且不是依赖 `starcore.admin` 误放行
- [ ] 策略操作 marker 包含 `technologyOfficer=steward:revoke+unlock`，证明默认配置下非管理员 `steward` 能撤销并解锁本国科技，且不是依赖 `starcore.admin` 误放行
- [ ] 策略操作 marker 包含 `warOfficer=marshal:declare+diplomat:end`，证明默认配置下非管理员 `marshal` 能宣战、非管理员 `diplomat` 能停战，且不是依赖 `starcore.admin` 误放行
- [ ] 治理操作 marker 包含 `governanceFeedback=governanceSound:<n>+governanceActionbar:<n>+governanceTitle:<n>+governanceBossbar:<n>+...+diplomacy:<n>+officer:<n>+treasury:<n>`，且外交、官员、国库三类真实命令探针对应的 sound、actionbar、BossBar 展示计数均大于 0；短 BossBar 可能在 marker 写出后才自动隐藏，因此治理 marker 不要求 `governanceBossbarHide` 大于 0
- [ ] 治理操作 marker 包含 `treasuryOfficer=treasurer:withdraw`，证明默认配置下非管理员 `treasurer` 能对本国执行国库支出，且不是依赖 `starcore.admin` 误放行
- [ ] 治理操作 marker 包含 `diplomacyOfficer=diplomat:set`，证明默认配置下非管理员 `diplomat` 能直接设置本国参与的外交关系，且不是依赖 `starcore.admin` 误放行
- [ ] `ProtectorAPI` 兼容桥摘要正确
- [ ] `ProtectorAPI` 参考契约检查通过
- [ ] Browser smoke 的断言与当前 HUD 契约一致：
  - `check-map-hud-contract.ps1` 已扫描打包源、静态预览壳、插件 runtime 副本里的 `resourceCommand|open-resource-command|nationDetailOpenCommand|section-resource-command|resource-command`
  - 当前 intel-only 契约下，三份资产都不应再残留 `#resource-command-panel`、`resourceCommand*` 或 `open-resource-command`
  - `scripts/smoke-starcore-map-browser.mjs` 必须输出 `commandUiRemoved=true` 与 `resourceExplanation=<state>:<count>`，且不应继续使用旧 `confirmUi=true`
  - 如果未来恢复 web 端资源区块指挥面，必须同步更新三份资产、Browser smoke 和 `check-map-hud-contract.ps1` 的契约规则

## 4. 烟测工件

- [ ] 最新 `smoke-summary.json` 已生成
- [ ] Paper 日志文件路径可追溯
- [ ] Browser DOM 工件可追溯
- [ ] Browser 截图工件可追溯
- [ ] SQLite 关键表计数已写入 summary

## 5. 自动汇总文档

- [ ] 如手工模式，执行：

```powershell
.\scripts\build-latest-verification-summary.ps1
```

- [ ] 检查 [`docs/LATEST_VERIFICATION_SUMMARY.md`](/D:/qwq/项目/mapadd/starcore/docs/LATEST_VERIFICATION_SUMMARY.md) 已更新
- [ ] 检查最新 Map HUD contract summary 已写入摘要
- [ ] 检查最新 performance budget summary 已写入摘要，并能看到 claim lookup、map render、SQL flush、三个 batch budget、browser snapshot、baseline trend mode 与 median sample 数
- [ ] 检查最新 smoke marker 已写入摘要，并能看到资源区块 `feedbackSound`、`worldSound`、`particles`、`actionbar`、`title`、`bossbar`、`bossbarHide` 七类游戏内效果计数
- [ ] 检查最新测试/摘要已覆盖资源区块迁移 explanation，至少能看到 `NationResourceDistrictCommandSupportTest`、`MapResourceDistrictEndpointTest`、`ResourceDistrictMapMetadataSupportTest` 通过，并能看到 Browser marker `resourceExplanation=<state>:<count>`、`resourceExplanationFixture=ready+awaiting-target+waiting-depletion+insufficient-balance+player-offline:5` 与 `resourceExplanationRuntime=ready+awaiting-target+waiting-depletion+insufficient-balance+player-offline:5`
- [ ] 检查最新 smoke marker 已写入网页圈地 `webClaimFeedback`、在线 pending feedback 和在线 cancel feedback 计数，并能看到 `cancelTyped:1`，证明 `/starcore map cancel <编号>` 返回 typed explanation
- [ ] 检查最新 smoke marker 已写入 `claimExplanation=externalProtection:1`，证明外部保护冲突的结构化解释仍在 preview/request 链路里
- [ ] 检查最新 smoke marker 已写入 `nationOperationFeedback` 国家操作反馈计数
- [ ] 检查最新 smoke marker 已写入 `strategyFeedback` 策略操作反馈计数
- [ ] 检查最新 smoke marker 已写入 `policyOfficer=steward:clear+set`，证明政策 set/clear 授权官员链路仍在真实服通过
- [ ] 检查最新 smoke marker 已写入 `technologyOfficer=steward:revoke+unlock`，证明科技 unlock/revoke 授权官员链路仍在真实服通过
- [ ] 检查最新 smoke marker 已写入 `warOfficer=marshal:declare+diplomat:end`，证明战争宣告/停战授权官员链路仍在真实服通过
- [ ] 检查最新 smoke marker 已写入 `governanceFeedback` 治理操作反馈计数，并能看到 diplomacy / officer / treasury 三类探针
- [ ] 检查最新 smoke marker 已写入 `treasuryOfficer=treasurer:withdraw`，证明国库支出授权官员链路仍在真实服通过
- [ ] 检查最新 smoke marker 已写入 `diplomacyOfficer=diplomat:set`，证明外交直设授权官员链路仍在真实服通过
- [ ] 检查最新 Browser smoke marker 已写入 `recentLogFilter=<category>:<count>`，证明最近操作日志分类筛选仍可用
- [ ] 检查最新 Browser smoke marker 已写入 `eventQuery=<filter>:<count>`，证明 `/api/map/events` 的真实查询链路仍可用
- [ ] 检查最新 Browser smoke marker 已写入 `eventLedger=<filter>:<count>`，证明国家详情完整事件日志 DOM、筛选和分页链路仍可用
- [ ] 检查最新 Browser smoke marker 已写入 `eventSearch=<encodedQuery>:<count>`，证明完整事件日志 `query/actor/reason` 搜索链路没有回归
- [ ] 检查最新 Browser smoke marker 已写入 `eventContext=<field>-<value>:<count>`，证明完整事件日志上下文 chip 一键追责链路没有回归
- [ ] 检查最新 Browser smoke marker 已写入 `eventReason=reason-<value>:<count>`，证明完整事件日志 reason chip 链路没有回归
- [ ] 检查最新 Browser smoke marker 已写入 `eventJump=reason-<value>:<count>`，证明完整事件日志跨事件跳转链路没有回归
- [ ] 检查最新 Browser smoke marker 已写入 `eventOps=resource+explanation+auth+group:<count>`，证明资源事件到资源 explanation、授权矩阵和运营处理入口的联动没有回归
- [ ] 检查最新 Paper smoke marker 已写入 `eventCommandSources=war+officer+treasury:6`、`eventCommandSourcesExtended=war+officer+treasury+diplomacy+strategy:14`、`strategyCommandEvents=policySet+policyClear+technologyUnlock+technologyRevoke:6`、`diplomacyCommandEvents=founderSet+diplomatSet:2`、`warCommandEvents=founderDeclare+diplomatEnd+marshalDeclare:3` 和 `governanceCommandEvents=officerAppoint+officerRemove+treasuryWithdraw:3`，证明真实命令来源事件 context 没有回归
- [ ] 检查最新 Browser smoke marker 已写入 `eventMobile=390x844:<count>`，证明完整事件日志移动端窄屏截图基线没有回归
- [ ] 检查最新 Browser smoke marker 已写入 `eventFamilies=finance+war+officer+diplomacy+strategy+territory+nation:7`，证明七类上下文事件族桌面 DOM 验证没有回归
- [ ] 检查最新 Browser smoke marker 已写入 `eventFamilyMobile=finance+war+officer+diplomacy+strategy+territory+nation:7`，证明七类上下文事件族移动端窄屏基线没有回归
- [ ] 检查最新 Browser smoke marker 已写入 `eventFacts=amount+balance:<count>`，证明完整事件日志只读上下文字段分组没有回归
- [ ] 检查最新 Browser smoke marker 已写入 `eventLedgerExport=csv+json`，证明完整事件日志 CSV/JSON 导出按钮仍可用，且导出 URL 保留 `query/actor/reason` 搜索条件
- [ ] 检查最新 smoke summary 已写入 `browserMobileScreenshot` 与 `browserSmoke.mobileScreenshotFile`，且文件存在；release evidence/channel assets manifest 也应包含 `browserMobileScreenshot`
- [ ] 检查最新 Browser smoke marker 已写入 `officerAuth=marshal+treasurer+diplomat+steward:9`，证明国家详情官员授权矩阵仍由真实 metadata 渲染
- [ ] 检查最新 Browser smoke marker 已写入 `officerAccess=founder:9` 或本轮明确预期的访问者状态，证明国家详情当前访问者授权状态仍由真实 metadata 渲染
- [ ] 如果传入了 spark profile summary，检查摘要已出现 `## Spark Profiling`，并能看到 report URL、artifact bytes、SHA256、采集命令和备注
- [ ] 如果使用 `verify-starcore-release.ps1 -RequireRealSparkProfile`，检查最终 JSON 里 `sparkProfileGate=real-required`，且 `sparkProfileSource` 不是 `sample-verification` 或 `manual`
- [ ] 检查最新 runtime tool selfcheck 已写入摘要
- [ ] 检查最新 ProtectorAPI reference check 已写入摘要
- [ ] 若本次未跑烟测，检查摘要已明确标记“not included in this verification run”
- [ ] 若已使用一站式命令，确认证据包和发布渠道成品包都来自同一轮 smoke 工件、HUD contract summary、performance budget summary 与 spark profile summary（如本轮提供）
- [ ] 生成一份发版证据包：

```powershell
.\scripts\build-starcore-release-evidence-pack.ps1
```

- [ ] 检查证据包内已包含 jar、`LATEST_VERIFICATION_SUMMARY.md`、`smoke-summary.json`、Map HUD contract summary、performance budget summary、Spark profile summary（如本轮提供）、Browser 截图和 Paper 日志
- [ ] 生成一份发布渠道成品包：

```powershell
.\scripts\build-starcore-release-channel-assets.ps1
```

- [ ] 检查成品包内已包含 Modrinth / Hangar / SpigotMC 的可直接粘贴文案、更新日志模板、截图、发布 jar、`references/map-hud-contract-summary.json`、`references/performance-budget-summary.json` 和可选 `references/spark-profile-summary.json`
- [ ] 检查成品包 `UPLOAD_GUIDE.md` 已显示 `Map HUD contract`、`Performance budget`、`Performance trend` 与 `Spark profiling`，且 `release-channel-assets-manifest.json` 的 `snapshot.mapHudContract=ok`、`snapshot.performanceBudget`、`snapshot.performanceBudgetBaseline` 都不是 `error`
- [ ] 如需对外粘贴 smoke 摘要，可执行：

```powershell
.\scripts\render-starcore-smoke-summary.ps1 -SummaryJsonPath <smoke-summary.json>
```

## 6. 运行态备份

- [ ] 发版前对目标服务器先执行一次运行态备份
- [ ] 执行一次统一运维自检，确认 restore `-ReplaceExisting` 和 MySQL 预检查 warning 分支仍可用
- [ ] 如果是 SQLite，确认 `starcore.db`、`starcore.db-wal`、`starcore.db-shm` 一起备份
- [ ] 如果地图站要一起回滚，确认 `plugins/map` 也已备份
- [ ] 发版前跑一次数据库预检查，确认当前库配置和文件组/端口状态正常
- [ ] 发版前跑一次 ProtectorAPI reference sync，确认本地 reference checkout 仍可更新和汇总
- [ ] 发版前跑一次 ProtectorAPI 参考契约检查，确认本地 reference checkout 和桥接契约没有漂移

推荐命令：

```powershell
.\scripts\backup-starcore-runtime.ps1 -DataDir 'D:\Minecraft\MyServer\plugins\STARCORE' -AsZip
```

```powershell
.\scripts\selfcheck-starcore-runtime-tools.ps1
```

```powershell
.\scripts\check-starcore-database-settings.ps1 -DataDir 'D:\Minecraft\MyServer\plugins\STARCORE'
```

```powershell
.\scripts\sync-protectorapi-reference.ps1
```

```powershell
.\scripts\check-protectorapi-reference.ps1
```

## 7. 发版包内容

- [ ] 发布 jar
- [ ] 当前默认配置说明
- [ ] ProtectorAPI 为可选软依赖的说明
- [ ] ProtectorAPI reference sync 脚本说明
- [ ] ProtectorAPI 参考契约检查脚本说明
- [ ] 外部保护桥单桥故障隔离说明
- [ ] 主要命令与短别名说明
- [ ] 当前验证基线摘要
- [ ] Browser 移动端截图证据和 `browserMobileScreenshot` manifest 字段

## 8. 发布后回归

- [ ] 新 jar 已在目标服启动成功
- [ ] `/starcore status` 正常
- [ ] `/starcore modules` 正常
- [ ] 圈地工具选择与确认正常
- [ ] 网页地图能打开并显示访问者余额/国家信息
- [ ] 如安装 `ProtectorAPI`，冲突区块仍能被正确拦截
