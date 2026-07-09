# STARCORE 当前完成状态

检查时间：`2026-06-05`

工作目录：`D:\qwq\项目\mapadd\starcore`

最新自动验证总览见 [LATEST_VERIFICATION_SUMMARY.md](/D:/qwq/项目/mapadd/starcore/docs/LATEST_VERIFICATION_SUMMARY.md)。

## 当前已完成面

- `StarCorePlugin` 已完成核心生命周期、模块注册、配置加载、语言加载、服务暴露与公开 API 注册。
- 默认模块已覆盖：
  - nation
  - government
  - resolution
  - treasury
  - diplomacy
  - policy
  - map
  - war
  - technology
  - resource
  - officer
  - event
- 配置文件 `config.yml` 已提供中文注释，并覆盖纪元、现实时间同步、国家/城邦限制、圈地价格、圈地工具、资源区块、数据库、网页地图、外部保护桥接等主要配置面。
- 命令系统已完成：
  - 短别名优先
  - 常用中文入口
  - Tab 补全
  - 结构化帮助
  - 国家/经济/纪元/时间/地图/资源/战争等模块入口
- 原生圈地系统已独立完成，不依赖：
  - WorldEdit
  - WorldGuard
  - Lands
  - Towny
- 游戏内圈地工具、命令圈地、网页拖拽圈地已统一走：
  - `NationService.previewClaimSelection(...)`
  - `NationService.claimSelection(...)`
- 网页圈地面板现在会直接显示：
  - 当前领地容量与预览后容量
  - 重叠区块数
  - 基础单价与预览均价
  - 高价区块明细
  - 每个区块的距离、资源丰富度、距离系数、生物群系系数
- 圈地预览 JSON 现在会优先返回高价区块明细，网页地图不再只是按遍历顺序展示区块。
- 圈地预览 JSON 现在还会返回结构化 `explanation`：
  - `state`
  - `severity`
  - `summary`
  - `reasons[]`
  - `details`
- 命令、圈地工具和网页地图现在共用 `ClaimSelectionExplanation` / `ClaimSelectionReason`，余额不足、容量限制、重叠、外部保护冲突和主要价格驱动不再由各入口各写一套失败说明。
- 网页国家列表现在支持选中国家后查看国家运营详情，并可与当前选中的资源区块联动显示该国家的收益、刷新、迁移与容量信息。
- 网页国家详情面现在已进一步升级成资源区块优先队列：
  - 会按迁移状态、理论收益和资金缺口排序本国资源区块
  - 会直接突出等待选点、等待枯竭、可迁移、余额不足、领袖需上线等运营优先级
  - 支持直接从国家详情面选中或定位对应资源区块
- 网页国家详情面的优先区块队列现在还支持快速筛选：
  - `全部 / 可迁移 / 等待选点 / 缺资金 / 等枯竭`
  - 并会同步强调资源区块列表中的匹配条目
- 网页国家详情面现在还包含国家领袖运营概览：
  - 可立即处理 / 流程等待 / 受阻区块数量
  - 筛选覆盖率
  - 小时折算资源收益 / 小时折算经验收益
  - 升级预计时间
  - 总资金缺口 / 最高单区块缺口
  - 当前态势判断
- 网页国家详情面现在还包含国家运营预报：
  - 下次刷新 / 下次强制迁移的相对时间
  - 1 小时内刷新波次
  - 6 小时内强制迁移波次
  - 6 小时 / 12 小时资源与经验预估
  - 按当前资源区块经验产出估算的升级轮次
  - 关键时间绝对时间提示
- 网页国家详情面现在还包含国家运营时间线：
  - 近期资源刷新 / 强制迁移 / 等待选点事件
  - 每条事件会显示区块、相对时间、绝对时间、当前限制和下一步
  - 每条事件支持直接查看或定位对应资源区块
  - 每条事件还支持直接“处理这一类”，切到对应运营筛选组并联动选中区块
- 网页国家详情面现在还包含“最近操作日志”块：
  - 直接显示国家近期迁移相关事件
  - 每条事件会显示事件消息、事件分类、事件类型、相对时间和绝对时间
  - 数据直接复用领地元数据里的 `recentEventCount / recentEvent{n}*`
  - 最近日志 metadata 现在包含 `recentEvent{n}Category`，并会优先保留最新事件，同时补齐不同运营分类代表，避免高频资源事件刷掉官员/战争/财政等信号
  - Browser DOM 现在可按资源、财政、官员、外交、战争、策略、领地、国家、其他分类筛选最近日志
  - 后端 `/api/map/events` 现在可按国家、分类、事件类型、资源区块和时间窗口分页查询完整事件流，网页深日志页和外部管理面不再只能读取 metadata 里的 5 条摘要
  - 国家详情完整事件日志 UI 现在已接入 `/api/map/events`，支持分类、时间范围、当前资源区块筛选和分页，并由 Browser smoke 真实点击验证
  - 完整事件日志现在可导出 CSV/JSON，导出沿用当前分类、时间范围和资源区块筛选，Browser smoke 已锁住 `eventLedgerExport=csv+json`
  - 完整事件日志已支持 `query/actor/reason` 搜索、actor/reason 上下文 chip、当前上下文跨事件跳转、金额/余额只读 facts 分组和 390x844 移动端窄屏截图基线
  - Browser smoke 已锁住 `eventContext=actor-SmokeAuditor:1`、`eventReason=reason-browser-context-chip:1`、`eventJump=reason-browser-context-chip:1`、`eventMobile=390x844:6` 与 `eventFacts=amount+balance:2`
  - Browser/Paper smoke 已进一步锁住财政、战争、官员、外交、策略、领地、国家七类真实上下文事件族，marker 为 `eventFamilies=finance+war+officer+diplomacy+strategy+territory+nation:7` 与 `eventFamilyMobile=finance+war+officer+diplomacy+strategy+territory+nation:7`
  - 资源类事件现在已联动资源 explanation、资源迁移授权和运营处理组入口，Browser smoke 已锁住 `eventOps=resource+explanation+auth+group:3`
  - 财政、战争、官员、外交、策略、领地、国家事件现在已联动运营卡、授权/命令边界和同类追踪入口，Browser smoke 已锁住 `eventOpsFamilies=finance+war+officer+diplomacy+strategy+territory+nation:7`
  - 战争、官员、国库支出三类真实命令来源事件 context 已进入 Paper smoke 硬门禁，marker 为 `eventCommandSources=war+officer+treasury:6`、`warCommandEvents=founderDeclare+diplomatEnd+marshalDeclare:3`、`governanceCommandEvents=officerAppoint+officerRemove+treasuryWithdraw:3`
- 网页国家详情面现在还包含“官员授权”矩阵：
  - 会直接展示资源迁移、国库支出、外交调整、宣战、停战、政策和科技操作对应的配置授权官职
  - 数据来自国家领土 metadata 的 9 个 `officerRole*` 字段，由 `ConfigurationService` 当前配置生成
  - Browser smoke 已锁住 `officerAuth=marshal+treasurer+diplomat+steward:9`，证明真实运行服 DOM 能看到完整授权矩阵
  - 当前访问者的逐项授权状态也已渲染进矩阵，可显示领袖直通、命中官职、缺少任命、外部国家、匿名或缺配置等状态
  - Browser smoke 已锁住 `officerAccess=founder:9`，证明真实运行服 DOM 能看到当前访问者 9 项授权状态
- 网页国家详情面现在还包含“处理建议”块：
  - 会从当前运营阻塞组中提取 1-3 条首要建议
  - 每条建议会显示影响区块数、首要区块、下一步和当前限制
  - 每条建议支持直接跳到对应问题组
  - 每条建议现在还会显示建议补足金额、建议目标区块和负责人
  - 每条建议现在还会按当前阻塞状态给出更具体的主动作：
    - `打开可迁移区块`
    - `去选迁移目标`
    - `查看资金缺口`
    - `查看需上线区块`
    - `查看领袖专属区块`
    - `查看枯竭进度`
  - 每条建议现在还会额外显示一句状态专属说明，帮助领袖先判断这一组该怎么推进
- 网页国家详情面现在还包含“当前操作焦点”块：
  - 会锁定当前筛选组下最高优先级的资源区块
  - 会直接显示当前分组、当前阶段、下一步、当前限制、资金缺口
  - 会根据当前状态直接显示关键时间窗口，例如刷新时间或强制迁移窗口
  - 会额外显示当前目标区块或单轮收益
  - 会直接提供 `选中资源区块 / 定位资源区块 / 打开对应问题组` 快捷动作
  - 现在还支持在当前筛选组内用 `上一条 / 下一条` 顺序切换焦点资源区块
  - 焦点块会显示当前序号，并优先跟随当前筛选组里的已选中资源区块
  - 切换筛选组时，如果当前选中区块不在新组里，会自动回落到该组第一条
- 网页国家详情面现在已和资源区块情报 explanation 打通：
  - 当前 HUD 契约是 intel-only 资源区块情报面，不再挂载 `#resource-command-panel`
  - 国家详情当前区块、焦点块、建议卡、操作分组和优先队列都会直接渲染迁移 explanation
  - Browser DOM 会带 `data-resource-explanation-state` / `data-resource-explanation-severity` / `data-resource-explanation-codes`，并继续从同一套 metadata 读取迁移状态、限制、下一步和资金缺口
  - 多状态 Browser fixture 已用生产 renderer 覆盖 `ready`、`awaiting-target`、`waiting-depletion`、`insufficient-balance`、`player-offline` 和 success/info/error severity 样式
  - 运行服真实数据造景已用创始人、低余额官员和离线访问者三组 snapshot 覆盖同五种 explanation 状态，Browser/Paper marker 均锁住 `resourceExplanationRuntime=ready+awaiting-target+waiting-depletion+insufficient-balance+player-offline:5`
- 网页国家详情面现在还包含按阻塞原因分组的运营快捷入口：
  - 可直接打开“可迁移 / 等选点 / 缺资金 / 需上线 / 等枯竭”等资源区块组
  - 会自动切换优先筛选、选中首要区块并飞到目标位置
- 网页地图已完成：
  - 认证访问
  - 访问者信息面板
  - 拖拽圈地预览
  - 圈地价格运营面板
  - 选中国家运营详情面板
  - pending 请求
  - 回服确认
  - 资源区块情报 explanation 面
  - 资源区块迁移 POST 接口
  - `terrain=off` 低负载烟测模式
  - 资源区块图层
  - 浏览器烟测
  - `?demo=1` 本地静态演示模式
  - 本地预览脚本 `scripts/preview-starcore-map.ps1`
  - 静态预览壳同步脚本 `scripts/sync-map-preview-shell.ps1`
- 领地保护已内建到 STARCORE 自身监听器，不依赖外部保护插件。
- `ProtectorAPI` 已接为可选软依赖，使用 runtime reflection 兼容桥，不把它变成 compile-time 依赖。
- `ProtectorAPI` 冲突检测已统一挂到共享圈地预览/确认链上，游戏内与网页规则一致。
- 外部保护桥提供者现已升级为 `ServiceLoader + META-INF/services` 发现模式，后续补新的保护桥时不需要重新改核心装配逻辑。
- `ProtectorApiBridgeContract` 已把 STARCORE 真正依赖的 ProtectorAPI 反射入口集中收口，后续上游 API 漂移只需要修一处契约文件。
- `scripts/check-protectorapi-reference.ps1` 已补齐，发版前可自动核对本地 `references/ProtectorAPI` checkout、`plugin.yml softdepend` 与 `ProtectorApiBridgeContract` 是否仍对齐。
- `PluginDescriptorContractTest` 与 `check-protectorapi-reference.ps1` 现在还会锁住 `META-INF/services` 的 provider 注册，避免后续软依赖桥接升级时漏掉 ServiceLoader 描述文件。
- `NationModule` 对资源区块能力现已通过 `NationResourceDistrictService` 接口持有，后续替换实现、补桥接或扩模块时不再硬绑原生实现类。
- 在线玩家目录抽象已完成：
  - `OnlinePlayerDirectory`
  - `BukkitOnlinePlayerDirectory`
- SQL 默认持久化已覆盖：
  - territory claims
  - player balances
  - nation state
  - nation resource district state
  - treasury
  - diplomacy
  - policy
  - resource
  - technology
  - war
  - officer
  - event
  - resolution
- 资源区块系统已完成：
  - 生物群系加权生成
  - 经验/等级成长
  - 信标显示
  - GUI 迁移确认
  - 下界之星迁移核心
  - 延迟迁移
  - 强制迁移
  - 配置化游戏内反馈：声音、粒子、actionbar、title、BossBar
  - 游戏内反馈已抽成 `foundation.feedback` 通用底座，资源区块只发语义事件，Bukkit 表现层和 profile 读取可复用于圈地、国家操作、政策/科技解锁
  - 迁移阻塞原因已接入 typed explanation，覆盖可迁移、等待选点、等待枯竭、余额不足、需领袖、需在线、非本国、区块缺失等状态
  - 网页迁移 POST、地图 marker metadata 和 `/starcore resource migrate` 失败输出已共用 `NationResourceDistrictCommandSupport.explanation(...)`
  - Paper smoke 已锁住 `feedbackSound`、`worldSound`、`particles`、`actionbar`、`title`、`bossbar`、`bossbarHide` 七类效果计数
- 圈地体验已接入共享游戏内反馈：
  - `nation.claims.feedback` 已覆盖当前区块圈地、圈地工具确认/取消/失败、网页 pending 通知和网页回服确认成功/失败
  - Paper smoke 已锁住 `webClaimFeedback` 与在线 pending feedback 计数
- 圈地解释面已结构化：
  - `/api/map/claim/preview` 与 `/api/map/claim/request` 已输出 `explanation.state/severity/summary/reasons/details`
  - `/starcore nation claim` 与圈地工具失败提示会展示结构化原因摘要
  - Paper smoke 已锁住 `claimExplanation=externalProtection:1`
- 网页圈地回服确认状态已结构化：
  - `WebClaimConfirmationResult` 已携带 `status` 与 `ClaimSelectionExplanation`
  - `/starcore map confirm <编号>` 已覆盖 pending 找不到、非本人、过期、确认时状态变化和提交失败的 typed explanation
  - 新增 `/starcore map cancel <编号>`，玩家可取消自己的网页 pending，取消状态为 `pending-cancelled`
  - 在线 pending 通知会同时给出确认和取消命令，并包含中文 `/starcore 地图 确认/取消 <编号>`
  - `nation.claims.feedback.web-cancelled` 已提供独立默认 profile
  - Paper smoke 已锁住 `onlineWebClaim=...+cancelSound:1+cancelActionbar:1+cancelBossbar:1+cancelTyped:1`
- 国家生命周期操作已接入共享游戏内反馈：
  - `nation.operations.feedback` 已覆盖国家创建、城邦创建、入国提案、改名提案、提案签署和操作失败
  - Paper smoke 已锁住 `nationOperationFeedback` 计数
- 策略高价值操作已接入共享游戏内反馈：
  - `nation.strategy.feedback` 已覆盖政策激活/清除、科技解锁/撤销、战争宣告/结束和统一失败提示
  - Paper smoke 已锁住 `strategyFeedback` 计数
- 治理高价值操作已接入共享游戏内反馈：
  - `nation.diplomacy.feedback` 已覆盖外交关系设置、外交提议和失败提示
  - `nation.officers.feedback` 已覆盖官员任命和移除
  - `nation.treasury.feedback` 已覆盖国库存入、支出、奖励、日常收入结算和税收结算，并由 `nation.treasury.feedback.minimum-amount` 控制最低可见金额
  - 命令层只发语义事件，sound/particle/actionbar/title/BossBar 继续由共享 `foundation.feedback` 配置 profile 决定
  - Paper smoke 已锁住 `governanceFeedback=governanceSound:8+governanceActionbar:8+governanceTitle:4+governanceBossbar:8+diplomacy:2+officer:2+treasury:4`
- 外交、策略真实命令来源事件 context 已进入 Paper smoke 硬门禁，marker 为 `eventCommandSourcesExtended=war+officer+treasury+diplomacy+strategy:14`、`strategyCommandEvents=policySet+policyClear+technologyUnlock+technologyRevoke:6`、`diplomacyCommandEvents=founderSet+diplomatSet:2`
- 地图访问者快照契约已补测试护栏，自动锁住：
  - 余额
  - 国家
  - 政体
  - 身份
  - 在线状态
  - 国家经验
  - 领地上限
  - 资源区块上限
- 国家成长触发资源区块补齐已补测试护栏，自动锁住：
  - `claimSelection(...)` 成功后触发 `ensureDistricts(...)`
  - `addExperience(...)` 只有在等级实际提升时才触发 `ensureDistricts(...)`
- 资源区块服务层流程已补测试护栏，自动锁住：
  - `ensureDistricts(...)` 会按国家已拥有领地区块和等级上限真实分配资源区块
  - `beginMigration(...)` 会把资源区块迁移状态切到 `awaiting_target`，并发放迁移核心
  - `handleMigrationTarget(...)` 会在合法本国区块落点后切到 `waiting_depletion`，并消耗迁移核心
  - `WAITING_DEPLETION` 进入强制迁移超时后会自动完成迁移
  - `WAITING_DEPLETION` 在资源尚未清空且未到强制迁移时间时不会提前完成迁移
  - 迁移请求 / 选点 / 正常完成 / 强制完成都会自动写入国家事件流
- 国家最近操作日志元数据现已补测试护栏，自动锁住：
  - `recentEventCount`
  - `recentEvent0Type / recentEvent0Message / recentEvent0At`
  - `recentEvent1Type / recentEvent1Message / recentEvent1At`
- 资源区块迁移核心物品创建/发放/消耗已通过 `MigrationCoreSupport` 适配层收口，后续替换物品交互或继续扩桥时不需要改动资源区块主流程。
- 现实时间同步 `TimeSyncService` 已实现，支持时区、目标世界、同步间隔与昼夜冻结控制。
- 现实时间同步现在会在启用时立即校准世界时间，并在停用、重载或关闭同步时恢复被 STARCORE 接管过世界原本的 `doDaylightCycle` 规则。

## 当前验证状态

### 2026-06-15 08:03 +08:00 最新续跑验证补充

2026-06-15 外交 + 策略真实命令来源事件 context 切片完成后，已用最新 smoke 结果刷新验证摘要；当前深烟测证据来自 2026-06-15 06:57 的同一轮运行服 smoke，摘要已在 2026-06-15 08:03 +08:00 重新生成。

- `node --check src\main\resources\web\map\js\map.js`: PASS
- `node --check scripts\smoke-starcore-map-browser.mjs`: PASS
- PowerShell parse check for `scripts\smoke-starcore-paper-integration.ps1`: PASS
- `mvn -q "-Dtest=StarCoreCommandShortAliasDispatchTest" test`: PASS
- `mvn package`: PASS，`332` tests / `0` failures / `0` errors / `0` skipped
- `.\scripts\smoke-starcore-paper-integration.ps1 -ProtectorApiSmoke -BrowserSmoke -TimeoutSeconds 420`: PASS
- Smoke summary: `target\smoke-harness-20260615-065629\smoke-summary.json`
- Paper marker 已包含 `eventCommandSourcesExtended=war+officer+treasury+diplomacy+strategy:14`，证明战争、官员、国库、外交、策略五类真实命令来源事件都写入结构化 context，而不是只停留在 audit fixture
- Paper marker 已包含 `strategyCommandEvents=policySet+policyClear+technologyUnlock+technologyRevoke:6`，证明政策激活/清除和科技解锁/撤销都写进了真实事件上下文
- Paper marker 已包含 `diplomacyCommandEvents=founderSet+diplomatSet:2`，证明创始人和外交官的真实外交直设命令也写进了结构化 context
- Browser marker 继续包含 `eventCommandSources=war+officer+treasury:6`、`eventOpsFamilies=finance+war+officer+diplomacy+strategy+territory+nation:7`、`eventFamilies=finance+war+officer+diplomacy+strategy+territory+nation:7`、`eventFamilyMobile=finance+war+officer+diplomacy+strategy+territory+nation:7`、`eventLedgerExport=csv+json` 和 `commandUiRemoved=true`
- `.\scripts\check-map-hud-contract.ps1 -RequireMirrorRoots`: PASS，summary `target\map-hud-contract-checks\check-20260615-065742\map-hud-contract-summary.json`
- `.\scripts\check-starcore-performance-budget.ps1 -SmokeSummaryJsonPath target\smoke-harness-20260615-065629\smoke-summary.json`: PASS，summary `target\performance-budget-checks\check-20260615-065755\performance-budget-summary.json`，`10/10` pass；baseline trend 为 `ok`
- `.\scripts\build-latest-verification-summary.ps1 -SmokeSummaryJsonPath target\smoke-harness-20260615-065629\smoke-summary.json -PerformanceBudgetSummaryPath target\performance-budget-checks\check-20260615-065755\performance-budget-summary.json -HudContractSummaryPath target\map-hud-contract-checks\check-20260615-065742\map-hud-contract-summary.json`: PASS
- 最新 jar: `target\starcore-0.1.0-SNAPSHOT.jar`
- 最新 jar size: `17754705`
- 最新 jar SHA256: `46C5F431B9CFFC6120CABB94391AC4789BAF40B8175E80BEE2254604EBC5BC00`

### 2026-06-15 00:04 +08:00 最新续跑验证补充

2026-06-15 真实命令来源事件 context 切片完成后，已完成同轮 `mvn package`、Paper+Browser 深烟测、HUD 契约检查、性能预算验证和最新摘要刷新；当前深烟测证据来自 2026-06-14 23:59 的同一轮运行服 smoke：

- `node --check src\main\resources\web\map\js\map.js`: PASS
- `node --check scripts\smoke-starcore-map-browser.mjs`: PASS
- PowerShell parse check for `scripts\smoke-starcore-paper-integration.ps1`: PASS
- `mvn -q "-Dtest=StarCoreCommandShortAliasDispatchTest" test`: PASS
- `mvn package`: PASS，`332` tests / `0` failures / `0` errors / `0` skipped
- `.\scripts\smoke-starcore-paper-integration.ps1 -ProtectorApiSmoke -BrowserSmoke -TimeoutSeconds 420`: PASS
- Smoke summary: `target\smoke-harness-20260614-235933\smoke-summary.json`
- Paper marker 已包含 `eventCommandSources=war+officer+treasury:6`，证明战争、官员、国库支出三类 context 来自真实 `/starcore war`、`/starcore officer`、`/starcore treasury withdraw` 命令写出的事件，而不是只靠 audit fixture
- Paper marker 已包含 `warCommandEvents=founderDeclare+diplomatEnd+marshalDeclare:3`，证明创始人宣战、外交官停战、元帅宣战三条真实战争命令均写入结构化 context
- Paper marker 已包含 `governanceCommandEvents=officerAppoint+officerRemove+treasuryWithdraw:3`，证明官员任命、官员移除、财务官国库支出三条真实治理命令均写入结构化 context
- Browser marker 继续包含 `eventOpsFamilies=finance+war+officer+diplomacy+strategy+territory+nation:7`、`eventFamilies=finance+war+officer+diplomacy+strategy+territory+nation:7`、`eventFamilyMobile=finance+war+officer+diplomacy+strategy+territory+nation:7`、`eventLedgerExport=csv+json` 和 `commandUiRemoved=true`
- `.\scripts\check-map-hud-contract.ps1 -RequireMirrorRoots`: PASS，summary `target\map-hud-contract-checks\check-20260615-000304\map-hud-contract-summary.json`
- `.\scripts\check-starcore-performance-budget.ps1 -SmokeSummaryJsonPath target\smoke-harness-20260614-235933\smoke-summary.json`: PASS with warning，summary `target\performance-budget-checks\check-20260615-000330\performance-budget-summary.json`，`10/10` pass；baseline trend 为 `warning`，`map_render`、`map_render_batch`、`sql_flush`、`sql_flush_cases` 历史中位线对比偏高，但均低于 configured budgets
- 最新 jar: `target\starcore-0.1.0-SNAPSHOT.jar`
- 最新 jar size: `17753890`
- 最新 jar SHA256: `C010EB68D89BA6DA73EB5B98A52DFDEA34E2AC94A88353FFD0AB0FD17008EE20`

### 2026-06-14 22:42 最新续跑验证补充

2026-06-14 完整事件日志七类运营联动完成后，已完成同轮 `mvn package`、Paper+Browser 深烟测、HUD 契约检查、性能预算验证和最新摘要刷新；当前深烟测证据来自 2026-06-14 23:05 的同一轮运行服 smoke：

- `node --check src\main\resources\web\map\js\map.js`: PASS
- `node --check scripts\smoke-starcore-map-browser.mjs`: PASS
- PowerShell parse check for `scripts\smoke-starcore-paper-integration.ps1`: PASS
- `mvn package`: PASS，`331` tests / `0` failures / `0` errors / `0` skipped
- `.\scripts\smoke-starcore-paper-integration.ps1 -ProtectorApiSmoke -BrowserSmoke -TimeoutSeconds 420`: PASS
- Smoke summary: `target\smoke-harness-20260614-230514\smoke-summary.json`
- Browser marker 已包含 `eventOpsFamilies=finance+war+officer+diplomacy+strategy+territory+nation:7`，证明财政、战争、官员、外交、策略、领地、国家事件日志都已渲染运营联动卡和同类追踪入口
- Browser marker 继续包含 `eventOps=resource+explanation+auth+group:3`，证明资源事件日志联动没有回归
- Browser marker 继续包含 `eventFamilies=finance+war+officer+diplomacy+strategy+territory+nation:7` 与 `eventFamilyMobile=finance+war+officer+diplomacy+strategy+territory+nation:7`
- Browser marker 继续包含 `eventMobile=390x844:6`、`eventLedgerExport=csv+json`、`resourceExplanationRuntime=ready+awaiting-target+waiting-depletion+insufficient-balance+player-offline:5` 和 `commandUiRemoved=true`
- `.\scripts\check-map-hud-contract.ps1 -RequireMirrorRoots`: PASS，summary `target\map-hud-contract-checks\check-20260614-231212\map-hud-contract-summary.json`
- `.\scripts\check-starcore-performance-budget.ps1 -SmokeSummaryJsonPath target\smoke-harness-20260614-230514\smoke-summary.json`: PASS with warning，summary `target\performance-budget-checks\check-20260614-231347\performance-budget-summary.json`，`10/10` pass；baseline trend 为 `warning`，claim lookup、map render、SQL flush batch 等历史中位线对比偏高，但均低于 configured budgets
- 最新 jar: `target\starcore-0.1.0-SNAPSHOT.jar`
- 最新 jar size: `17752925`
- 最新 jar SHA256: `7B619AB8AD9158E8B758F9A7042242274EC49D523F6E961BFB37446652ACB01F`

### 2026-06-14 22:07 最新续跑验证补充

2026-06-14 完整事件日志资源运营联动完成后，已完成同轮 `mvn package`、Paper+Browser 深烟测、HUD 契约检查、性能预算验证和最新摘要刷新；当前深烟测证据来自 2026-06-14 22:06 的同一轮运行服 smoke：

- `node --check scripts\smoke-starcore-map-browser.mjs`: PASS
- `node --check src\main\resources\web\map\js\map.js`: PASS
- PowerShell parse check for `scripts\smoke-starcore-paper-integration.ps1`: PASS
- `mvn package`: PASS，`331` tests / `0` failures / `0` errors / `0` skipped
- `.\scripts\smoke-starcore-paper-integration.ps1 -ProtectorApiSmoke -BrowserSmoke -TimeoutSeconds 420`: PASS
- Smoke summary: `target\smoke-harness-20260614-220633\smoke-summary.json`
- Browser marker 已包含 `eventOps=resource+explanation+auth+group:3`，证明资源事件日志已联动 typed resource explanation、资源迁移授权矩阵和运营处理组入口
- Browser marker 继续包含 `eventFamilies=finance+war+officer+diplomacy+strategy+territory+nation:7`，证明七类事件族桌面 DOM 基线没有回归
- Browser marker 继续包含 `eventFamilyMobile=finance+war+officer+diplomacy+strategy+territory+nation:7`，证明 390x844 移动端七类事件族基线没有回归
- Browser marker 继续包含 `eventMobile=390x844:6`、`eventLedgerExport=csv+json`、`resourceExplanationRuntime=ready+awaiting-target+waiting-depletion+insufficient-balance+player-offline:5` 和 `commandUiRemoved=true`
- `.\scripts\check-map-hud-contract.ps1 -RequireMirrorRoots`: PASS，summary `target\map-hud-contract-checks\check-20260614-220744\map-hud-contract-summary.json`
- `.\scripts\check-starcore-performance-budget.ps1 -SmokeSummaryJsonPath target\smoke-harness-20260614-220633\smoke-summary.json`: PASS，summary `target\performance-budget-checks\check-20260614-220744\performance-budget-summary.json`，`10/10` pass，baseline trend `ok`
- 最新 jar: `target\starcore-0.1.0-SNAPSHOT.jar`
- 最新 jar size: `17750857`
- 最新 jar SHA256: `979E185B6A7C86CC9A0D5C72C806632CAC0B6C81736FDF43129F511EE2DBC76E`

### 2026-06-14 21:36 历史续跑验证补充

2026-06-14 完整事件日志 7 类事件族移动端真实上下文完成后，已完成同轮 Paper+Browser 深烟测、HUD 契约检查、性能预算验证和最新摘要刷新；当前深烟测证据来自 2026-06-14 21:36 的同一轮运行服 smoke：

- `node --check scripts\smoke-starcore-map-browser.mjs`: PASS
- PowerShell parse check for `scripts\smoke-starcore-paper-integration.ps1`: PASS
- `.\scripts\smoke-starcore-paper-integration.ps1 -ProtectorApiSmoke -BrowserSmoke -TimeoutSeconds 420`: PASS
- Smoke summary: `target\smoke-harness-20260614-213637\smoke-summary.json`
- Paper marker 已包含 `eventFamilies=finance+war+officer+diplomacy+strategy+territory+nation:7`，证明运行服里通过真实 `EventService` 写入财政、战争、官员、外交、策略、领地、国家七类上下文事件族
- Browser marker 已包含 `eventFamilies=finance+war+officer+diplomacy+strategy+territory+nation:7`，证明真实 Browser DOM 已按分类/reason/actor/facts/jump 渲染七类事件族
- Browser marker 已包含 `eventFamilyMobile=finance+war+officer+diplomacy+strategy+territory+nation:7`，证明 390x844 移动端窄屏基线已逐类覆盖七类事件族
- Browser marker 继续包含 `eventMobile=390x844:6`、`eventLedgerExport=csv+json`、`resourceExplanationRuntime=ready+awaiting-target+waiting-depletion+insufficient-balance+player-offline:5` 和 `commandUiRemoved=true`
- `.\scripts\check-map-hud-contract.ps1 -RequireMirrorRoots`: PASS，summary `target\map-hud-contract-checks\check-20260614-213819\map-hud-contract-summary.json`
- `.\scripts\check-starcore-performance-budget.ps1 -SmokeSummaryJsonPath target\smoke-harness-20260614-213637\smoke-summary.json`: PASS，summary `target\performance-budget-checks\check-20260614-213820\performance-budget-summary.json`，`10/10` pass，baseline trend `ok`
- 最新 jar: `target\starcore-0.1.0-SNAPSHOT.jar`
- 最新 jar size: `17749789`
- 最新 jar SHA256: `1BFF30AFF500F711395A72D1F77E8A170F7752A30F25DD6812953D897E4C5F57`

### 2026-06-14 真实 Spark profile 发版门禁补充

本轮在统一发版入口补上真实 Spark profile 门禁，防止正式 zip 发版时把 `sample-verification` 或默认 `manual` 导入误当成运行服性能报告。

- `scripts/verify-starcore-release.ps1` 新增 `-RequireRealSparkProfile`
- 门禁会在 Maven/test/smoke 前快速检查 `-SparkProfileSummaryPath`
- 正式门禁要求：
  - `status=ok`
  - `sourceLabel` 不能是 `sample-verification`、`sample`、`script-check` 或 `manual`
  - `reportUrl` 必须是绝对 `http(s)` URL
  - 本地 artifact 必须存在，且 `bytes/fileCount` 大于 0
  - 采集命令 `start/stop/open` 必须记录
- 已验证：
  - PowerShell parse check for `scripts\verify-starcore-release.ps1`: PASS
  - `.\scripts\verify-starcore-release.ps1 -RequireRealSparkProfile -SparkProfileSummaryPath target\spark-profile-imports\profile-script-check\spark-profile-summary.json`: 按预期失败，并在跑 Maven/smoke 前拒绝 `sourceLabel=sample-verification`
- 文档已同步：
  - `README.md`
  - `docs\RELEASE_CHECKLIST_2026-06-05.md`
  - `docs\RELEASE_CHANNEL_PACK_2026-06-05.md`
  - `docs\OPERATIONS_RUNBOOK_2026-06-05.md`
  - `docs\MODULE_PLAN.md`

真实运行服 Spark artifact 尚未出现在仓库中；下一步需要在 staging 或 production-shadow 服采集 spark 报告，导入后运行带 `-RequireRealSparkProfile` 的一站式 zip 发版验证。

### 2026-06-14 21:07 历史续跑验证补充

2026-06-14 完整事件日志多事件族移动端真实上下文完成后，已完成同轮 Paper+Browser 深烟测、HUD 契约检查、性能预算验证和最新摘要刷新；当前深烟测证据来自 2026-06-14 21:07 的同一轮运行服 smoke：

- `node --check scripts\smoke-starcore-map-browser.mjs`: PASS
- PowerShell parse check for `scripts\smoke-starcore-paper-integration.ps1`: PASS
- `.\scripts\smoke-starcore-paper-integration.ps1 -ProtectorApiSmoke -BrowserSmoke -TimeoutSeconds 420`: PASS
- Smoke summary: `target\smoke-harness-20260614-210736\smoke-summary.json`
- Paper marker 已包含 `eventFamilies=finance+war+officer:3`，证明运行服里通过真实 `EventService` 写入财政、战争、官员三类上下文事件族
- Browser marker 已包含 `eventFamilies=finance+war+officer:3`，证明真实 Browser DOM 已按分类/reason/actor/facts/jump 渲染三类事件族
- Browser marker 已包含 `eventFamilyMobile=finance+war+officer:3`，证明 390x844 移动端窄屏基线已逐类覆盖财政、战争、官员事件族
- Browser marker 继续包含 `eventMobile=390x844:6`、`eventLedgerExport=csv+json`、`resourceExplanationRuntime=ready+awaiting-target+waiting-depletion+insufficient-balance+player-offline:5` 和 `commandUiRemoved=true`
- Smoke marker 继续包含 `claimExplanation=externalProtection:1`、`webClaimFeedback`、`onlineWebClaim=...+cancelSound:1+cancelActionbar:1+cancelBossbar:1+cancelTyped:1`、`nationOperationFeedback`、`strategyFeedback` 和 `governanceFeedback=governanceSound:8+governanceActionbar:8+governanceTitle:4+governanceBossbar:8+governanceBossbarHide:0+diplomacy:2+officer:2+treasury:4`
- `.\scripts\check-map-hud-contract.ps1 -RequireMirrorRoots`: PASS，summary `target\map-hud-contract-checks\check-20260614-210847\map-hud-contract-summary.json`
- `.\scripts\check-starcore-performance-budget.ps1 -SmokeSummaryJsonPath target\smoke-harness-20260614-210736\smoke-summary.json`: PASS，summary `target\performance-budget-checks\check-20260614-210846\performance-budget-summary.json`，`10/10` pass，baseline trend `ok`
- 最新 jar: `target\starcore-0.1.0-SNAPSHOT.jar`
- 最新 jar size: `17749789`
- 最新 jar SHA256: `1BFF30AFF500F711395A72D1F77E8A170F7752A30F25DD6812953D897E4C5F57`

### 2026-06-14 20:45 历史续跑验证补充

2026-06-14 资源 explanation 多状态运行服真实数据造景完成后，已完成同轮构建、Paper+Browser 深烟测、HUD 契约检查、性能预算验证和最新摘要刷新；当前深烟测证据来自 2026-06-14 20:45 的同一轮运行服 smoke：

- `node --check scripts\smoke-starcore-map-browser.mjs`: PASS
- PowerShell parse check for `scripts\smoke-starcore-paper-integration.ps1`: PASS
- `mvn -q -DskipTests package`: PASS
- `.\scripts\smoke-starcore-paper-integration.ps1 -ProtectorApiSmoke -BrowserSmoke -TimeoutSeconds 420`: PASS
- Smoke summary: `target\smoke-harness-20260614-204506\smoke-summary.json`
- Paper marker 已包含 `resourceExplanationRuntime=ready+awaiting-target+waiting-depletion+insufficient-balance+player-offline:5`，证明运行服造景在 Paper harness 层通过真实 resource district snapshot 产生
- Browser marker 已包含 `resourceExplanationRuntime=ready+awaiting-target+waiting-depletion+insufficient-balance+player-offline:5`，证明真实 `/api/map/snapshot` 已覆盖 ready、等待选点、等待枯竭、缺资金和需上线五种状态
- Browser marker 继续包含 `resourceExplanationFixture=ready+awaiting-target+waiting-depletion+insufficient-balance+player-offline:5`，证明生产 renderer fixture 与 severity 样式没有回退
- Browser marker 继续包含 `eventMobile=390x844:6`、`eventLedgerExport=csv+json` 和 `commandUiRemoved=true`
- Smoke marker 继续包含 `claimExplanation=externalProtection:1`、`webClaimFeedback`、`onlineWebClaim=...+cancelSound:1+cancelActionbar:1+cancelBossbar:1+cancelTyped:1`、`nationOperationFeedback`、`strategyFeedback` 和 `governanceFeedback=governanceSound:8+governanceActionbar:8+governanceTitle:4+governanceBossbar:8+governanceBossbarHide:0+diplomacy:2+officer:2+treasury:4`
- `.\scripts\check-map-hud-contract.ps1 -RequireMirrorRoots`: PASS，summary `target\map-hud-contract-checks\check-20260614-204635\map-hud-contract-summary.json`
- `.\scripts\check-starcore-performance-budget.ps1 -SmokeSummaryJsonPath target\smoke-harness-20260614-204506\smoke-summary.json`: PASS，summary `target\performance-budget-checks\check-20260614-204635\performance-budget-summary.json`，`10/10` pass，baseline trend `ok`
- 最新 jar: `target\starcore-0.1.0-SNAPSHOT.jar`
- 最新 jar size: `17749789`
- 最新 jar SHA256: `1BFF30AFF500F711395A72D1F77E8A170F7752A30F25DD6812953D897E4C5F57`

### 2026-06-14 14:12 历史续跑验证补充

2026-06-14 完整事件日志 CSV/JSON 导出和 Browser 按钮烟测后，已完成同轮构建、完整测试、HUD 契约检查、Paper+Browser 深烟测和性能预算验证；当前深烟测证据来自 2026-06-14 14:12 的同一轮运行服 smoke：

- `node --check src/main/resources/web/map/js/map.js`: PASS
- `node --check scripts/smoke-starcore-map-browser.mjs`: PASS
- `.\scripts\sync-map-preview-shell.ps1`: PASS
- `.\scripts\sync-map-preview-shell.ps1 -PreviewRoot ..\test-server-paper-1.21.11\plugins\map`: PASS
- `mvn -q "-Dtest=NativeNationResourceDistrictServiceFlowTest" test`: PASS
- `mvn -q "-Dtest=MapEventLogEndpointTest,MapWebServerTest,MapModuleViewerSnapshotContractTest" test`: PASS
- `mvn -q test`: PASS，`307` tests，`0` failures，`0` errors
- `mvn -q -DskipTests package`: PASS
- `.\scripts\smoke-starcore-paper-integration.ps1 -ProtectorApiSmoke -BrowserSmoke -TimeoutSeconds 420`: PASS
- Smoke summary: `target\smoke-harness-20260614-141248\smoke-summary.json`
- Browser marker 已包含 `recentLogFilter=resource:3`，证明最近操作日志分类筛选已在 Browser DOM 中真实点击并缩小结果集
- Browser marker 已包含 `eventQuery=resource:9`，证明 `/api/map/events` 已在真实 Browser 认证态里按资源分类查到完整事件结果
- Browser marker 已包含 `eventLedger=resource:3`，证明完整事件日志 UI 已在国家详情里真实点击、筛选和渲染
- Browser marker 已包含 `eventLedgerExport=csv+json`，证明完整事件日志 CSV/JSON 导出按钮已真实点击并沿用当前筛选状态
- Browser marker 继续包含 `resourceExplanation=player-offline:5` 和 `commandUiRemoved=true`，证明资源 explanation 已渲染进 Browser DOM，且旧 web command UI 仍缺席
- Browser marker 现在还包含 `resourceExplanationFixture=ready+awaiting-target+waiting-depletion+insufficient-balance+player-offline:5`，证明资源 explanation 多状态受控 fixture 已用生产 renderer 覆盖 ready、等待选点、等待枯竭、缺资金和需上线，并验证 success/info/error severity 样式
- Smoke marker 已包含 `claimExplanation=externalProtection:1`、`webClaimFeedback`、`onlineWebClaim=...+cancelSound:1+cancelActionbar:1+cancelBossbar:1+cancelTyped:1`、`nationOperationFeedback`、`strategyFeedback` 和 `governanceFeedback=governanceSound:6+governanceActionbar:6+governanceTitle:3+governanceBossbar:6+governanceBossbarHide:0+diplomacy:1+officer:2+treasury:3`
- `.\scripts\check-map-hud-contract.ps1 -RequireMirrorRoots`: PASS，summary `target\map-hud-contract-checks\check-20260614-141014\map-hud-contract-summary.json`
- `.\scripts\check-starcore-performance-budget.ps1 -SmokeSummaryJsonPath target\smoke-harness-20260614-141248\smoke-summary.json`: PASS，summary `target\performance-budget-checks\check-20260614-141413\performance-budget-summary.json`，`10/10` pass，baseline trend `ok`
- 最新 jar: `target\starcore-0.1.0-SNAPSHOT.jar`
- 最新 jar size: `17734031`
- 最新 jar SHA256: `53FB34D2F8CC94174597A28390F399946A286F3143DBF664F2BF04857482DBB4`

### 统一验证入口

- 轻量验证入口：
  - [verify-starcore-release.ps1](/D:/qwq/项目/mapadd/starcore/scripts/verify-starcore-release.ps1)
- 完整验证入口：
  - `.\scripts\verify-starcore-release.ps1 -IncludeSmoke -ProtectorApiSmoke -BrowserSmoke`

### 最新轻量验证

```text
.\scripts\verify-starcore-release.ps1
PASS
- mvn -q test
- mvn -q package
- runtime tool selfcheck
- LATEST_VERIFICATION_SUMMARY.md updated
```

### 最新完整验证

```text
.\scripts\verify-starcore-release.ps1 -IncludeSmoke -ProtectorApiSmoke -BrowserSmoke
PASS
```

当前真实结果：

- `mvn -q test`
  - `307` tests
  - `0 failures`
  - `0 errors`
  - `0 skipped`
- `mvn -q package`
  - PASS
- 最新 jar：
  - `target/starcore-0.1.0-SNAPSHOT.jar`
- 最新 jar 大小：
  - `17734031` bytes
- 最新 jar SHA256：
  - `53FB34D2F8CC94174597A28390F399946A286F3143DBF664F2BF04857482DBB4`

### 最新 runtime selfcheck 证据

- 目录：
  - `target/runtime-tool-selfchecks/selfcheck-20260610-140012`
- 关键结果：
  - `status = ok`
  - `restore -ReplaceExisting = PASS`
  - `mysql precheck branch = warning`
  - `protectorApiReferenceCheck = ok`

### 最新 ProtectorAPI 参考契约证据

- 目录：
  - `target/protectorapi-reference-checks/check-20260610-140019`
- 关键结果：
  - `status = ok`
  - `reference head = 88ced9783aaffffe333b57610162ac8ef9759760`
  - `remote = https://github.com/LinsMinecraftStudio/ProtectorAPI.git`

说明：

- 上面这一组是当前最新 runtime tool selfcheck 结果，已被最新完整验证摘要引用
- 轻量验证现在会诚实写明 “Smoke summary: not included in this verification run.”
- 当前资源区块 HUD 契约是 intel-only 情报面：
  - 三份 map 资产都不得残留 `#resource-command-panel`、`open-resource-command` 或 `resourceCommand*`
  - Browser smoke 已锁住 `resourceExplanation=player-offline:5`，证明国家详情里的 explanation DOM 渲染和 metadata 匹配
  - Browser smoke 已锁住 `resourceExplanationFixture=ready+awaiting-target+waiting-depletion+insufficient-balance+player-offline:5`，证明多状态 explanation fixture 与 severity 样式在真实 Browser 中可验证
  - Browser smoke 已锁住 `recentLogFilter=resource:3`，证明最近操作日志分类筛选在真实 Browser DOM 中可用
  - Browser smoke 已锁住 `eventQuery=resource:9`，证明 `/api/map/events` 后端事件查询接口在真实 Browser 认证态里可用
  - Browser smoke 已锁住 `eventLedger=resource:3`，证明国家详情完整事件日志 DOM、筛选和分页链路可用
  - Browser smoke 已锁住 `eventLedgerExport=csv+json`，证明完整事件日志 CSV/JSON 导出按钮可用
  - 最新完整 smoke 已再次锁住 `commandUiRemoved=true`，旧 `confirmUi=true` 不再作为 PASS 字段
- 浏览器烟测当前还会真实点击国家运营快捷入口，验证分组按钮不只是渲染出来，而是真的会切换筛选并联动选中资源区块
- 浏览器烟测当前还会锁住国家运营时间线的真实渲染
- 浏览器烟测当前还会真实点击时间线里的“处理这一类”按钮，验证其会切换到对应筛选组并选中目标区块
- 浏览器烟测当前还会锁住“处理建议”块的真实渲染
- 浏览器烟测当前还会锁住“处理建议”块里至少存在 1 个状态专属按钮文案，避免整块退回统一通用按钮
- 浏览器烟测当前还会锁住 `#nation-operations-focus` 焦点块的真实渲染，以及其中至少 2 个可点击动作按钮
- 浏览器烟测当前还会真实验证焦点块 `上一条 / 下一条` 导航，锁住焦点序号、按钮禁用态和 `selectedResourceId` 的前后切换
- 浏览器烟测当前还会真实验证资源 explanation 在筛选/快捷动作点击后继续存在，锁住事件上下文到资源标记 ID 的映射链路
- 更新后的建议卡片字段已经进入最新完整 smoke 验证链

### 最新完整 smoke 证据

- 目录：
  - `target/smoke-harness-20260614-141248`
- Paper 日志：
  - `D:\qwq\项目\mapadd\test-server-paper-1.21.11\zcode-starcore-deep-integration-smoke-20260614-141248.out.log`
- Browser DOM：
  - `D:\qwq\项目\mapadd\test-server-paper-1.21.11\zcode-starcore-map-browser-smoke-20260614-141248.dom.html`
- Browser 截图：
  - `D:\qwq\项目\mapadd\test-server-paper-1.21.11\zcode-starcore-map-browser-smoke-20260614-141248.png`
- smoke summary：
  - `target/smoke-harness-20260614-141248/smoke-summary.json`

关键 PASS 标记：

```text
STARCORE_SMOKE_PASS nation=Smokemqde1pqh claims=5 price=327.49 districts=1 claimTool=GOLDEN_SHOVEL treasury=3368.50 diplomacy=war policy=civil_industry resources=food:150,ore:64 resolution=join_nation:enacted technology=logistics war=true officer=marshal event=resource migration=gui+mined:world:149:149+forced:world:149:150+feedbackSound:5+worldSound:7+particles:7+actionbar:3+title:2+bossbar:4+bossbarHide:4 protector=runtime:MockProtectorSmoke@157:157 claimExplanation=externalProtection:1 webClaim=command:62840098 webClaimFeedback=confirmSound:1+confirmActionbar:1+confirmTitle:0+confirmBossbar:1+confirmBossbarHide:1 onlineWebClaim=SmokeEnvoy:13b60364+pendingSound:1+pendingActionbar:1+pendingTitle:0+pendingBossbar:1+pendingBossbarHide:0+cancelSound:1+cancelActionbar:1+cancelTitle:0+cancelBossbar:1+cancelBossbarHide:0+cancelTyped:1 nationOperationFeedback=operationSound:4+operationActionbar:4+operationTitle:1+operationBossbar:4+operationBossbarHide:0 strategyFeedback=strategySound:3+strategyActionbar:3+strategyTitle:3+strategyBossbar:3+strategyBossbarHide:0 governanceFeedback=governanceSound:6+governanceActionbar:6+governanceTitle:3+governanceBossbar:6+governanceBossbarHide:0+diplomacy:1+officer:2+treasury:3 viewer=ok beacon=2408,63,2392:BEACON mapSummary=5 territory polygon(s), 0 player marker(s), 1 resource district marker(s)
```

```text
WebClaimSmoke PASS provider=MockProtectorSmoke chunk=157,157 bounds=2512,2512->2543,2543
```

```text
STARCORE_BROWSER_SMOKE_PASS viewer=SmokePlayer nation=Smokemqde1pqh balance=9799090.59 nationDetail=true nationAction=true recentLog=5 recentLogFilter=resource:3 eventQuery=resource:9 eventLedger=resource:3 eventLedgerExport=csv+json resourceAction=player-offline resourceExplanation=player-offline:5 resourceCost=100000.00 commandUiRemoved=true browser=Edg/149.0.4022.62
```

### 最新组合烟测 SQLite 计数

```text
starcore_diplomacy_state=10
starcore_event_state=157
starcore_metadata=3
starcore_nation_resource_district_state=40
starcore_nation_state=76
starcore_officer_state=5
starcore_player_balances=7
starcore_policy_state=21
starcore_resolution_state=27
starcore_resource_state=7
starcore_technology_state=7
starcore_territory_claims=5
starcore_treasury_state=7
starcore_war_state=7
```

### 2026-06-14 15:10 +08:00 最新补充验证

上一段 `20260614-141248` 是资源区块迁移官员授权配置化之前的完整 smoke 证据；本轮已用新 jar 重新跑完真实服验证，并把 marshal 授权证据写入 marker：

- smoke summary：
  - `target/smoke-harness-20260614-150846/smoke-summary.json`
- Paper 日志：
  - `D:\qwq\项目\mapadd\test-server-paper-1.21.11\zcode-starcore-deep-integration-smoke-20260614-150846.out.log`
- Browser DOM：
  - `D:\qwq\项目\mapadd\test-server-paper-1.21.11\zcode-starcore-map-browser-smoke-20260614-150846.dom.html`
- Browser 截图：
  - `D:\qwq\项目\mapadd\test-server-paper-1.21.11\zcode-starcore-map-browser-smoke-20260614-150846.png`

关键 PASS 标记新增：

```text
officer=marshal officerMigration=marshal:member+gui+target+forced
```

完整验证状态：

- `mvn -q test`: PASS (`313` tests, `0` failures, `0` errors, `0` skipped)
- `mvn -q -DskipTests package`: PASS
- Paper + ProtectorAPI + Browser smoke: PASS
- HUD contract: PASS (`target/map-hud-contract-checks/check-20260614-151011/map-hud-contract-summary.json`)
- Performance budget: PASS (`target/performance-budget-checks/check-20260614-150955/performance-budget-summary.json`, `10/10`)
- 最新 jar SHA256: `CB85F341CA0B28B4531CBEE3E18030051C7A07ECCC0029DDCA08D983223D5627`

### 2026-06-14 15:33 +08:00 最新补充验证

本轮在上一轮 marshal 迁移授权之后，继续把国库支出接入官员职责，并用真实服 smoke 证明默认 `treasurer` 不是靠管理员权限误放行：

- smoke summary：
  - `target/smoke-harness-20260614-153139/smoke-summary.json`
- Paper 日志：
  - `D:\qwq\项目\mapadd\test-server-paper-1.21.11\zcode-starcore-deep-integration-smoke-20260614-153139.out.log`
- Browser DOM：
  - `D:\qwq\项目\mapadd\test-server-paper-1.21.11\zcode-starcore-map-browser-smoke-20260614-153139.dom.html`
- Browser 截图：
  - `D:\qwq\项目\mapadd\test-server-paper-1.21.11\zcode-starcore-map-browser-smoke-20260614-153139.png`

关键 PASS 标记新增：

```text
governanceFeedback=...+treasury:4+treasuryOfficer=treasurer:withdraw
```

完整验证状态：

- `mvn -q test`: PASS (`316` tests, `0` failures, `0` errors, `0` skipped)
- `mvn -q -DskipTests package`: PASS
- Paper + ProtectorAPI + Browser smoke: PASS
- HUD contract: PASS (`target/map-hud-contract-checks/check-20260614-153245/map-hud-contract-summary.json`)
- Performance budget: PASS (`target/performance-budget-checks/check-20260614-153245/performance-budget-summary.json`, `10/10`)
- 最新 jar SHA256: `D349B66B414C8D6CF2BEF053D7F71C43EB7DD6A3218C2F7FDD1F9418CD9C9A6D`

### 2026-06-14 15:54 +08:00 最新补充验证

本轮继续把外交直设接入官员职责，并修掉 `/starcore diplomacy set` 原先只要有 `starcore.command` 就能改任意两国关系的风险。现在直设关系允许管理员、任一参与国家 founder 或任一参与国家配置授权官职操作；默认授权官职为 `diplomat`。

- smoke summary：
  - `target/smoke-harness-20260614-155248/smoke-summary.json`
- Paper 日志：
  - `D:\qwq\项目\mapadd\test-server-paper-1.21.11\zcode-starcore-deep-integration-smoke-20260614-155248.out.log`
- Browser DOM：
  - `D:\qwq\项目\mapadd\test-server-paper-1.21.11\zcode-starcore-map-browser-smoke-20260614-155248.dom.html`
- Browser 截图：
  - `D:\qwq\项目\mapadd\test-server-paper-1.21.11\zcode-starcore-map-browser-smoke-20260614-155248.png`

关键 PASS 标记新增：

```text
governanceFeedback=...+diplomacy:2+officer:2+treasury:4+treasuryOfficer=treasurer:withdraw+diplomacyOfficer=diplomat:set
```

完整验证状态：

- `mvn -q test`: PASS (`319` tests, `0` failures, `0` errors, `0` skipped)
- `mvn -q -DskipTests package`: PASS
- Paper + ProtectorAPI + Browser smoke: PASS
- HUD contract: PASS (`target/map-hud-contract-checks/check-20260614-155359/map-hud-contract-summary.json`)
- Performance budget: PASS (`target/performance-budget-checks/check-20260614-155359/performance-budget-summary.json`, `10/10`)
- 最新 jar SHA256: `3CEF91AC845E003678C3F83BA9773CF04DC2A341C4B231A644519F1D79282036`

### 2026-06-14 16:09 +08:00 最新补充验证

本轮继续把战争宣告/停战接入官员职责，把 `/starcore war declare` 与 `/starcore war end` 从纯管理员入口扩展为“管理员、参与国家领袖或配置授权官职”可操作。宣战和停战拆成两个独立配置项，避免把军事与外交职责写死到命令层。

- smoke summary：
  - `target/smoke-harness-20260614-160744/smoke-summary.json`
- Paper 日志：
  - `D:\qwq\项目\mapadd\test-server-paper-1.21.11\zcode-starcore-deep-integration-smoke-20260614-160744.out.log`
- Browser DOM：
  - `D:\qwq\项目\mapadd\test-server-paper-1.21.11\zcode-starcore-map-browser-smoke-20260614-160744.dom.html`
- Browser 截图：
  - `D:\qwq\项目\mapadd\test-server-paper-1.21.11\zcode-starcore-map-browser-smoke-20260614-160744.png`

关键 PASS 标记新增：

```text
strategyFeedback=...+warOfficer=marshal:declare+diplomat:end
```

完整验证状态：

- `mvn -q test`: PASS (`323` tests, `0` failures, `0` errors, `0` skipped)
- `mvn -q -DskipTests package`: PASS
- Paper + ProtectorAPI + Browser smoke: PASS
- HUD contract: PASS (`target/map-hud-contract-checks/check-20260614-160854/map-hud-contract-summary.json`)
- Performance budget: PASS (`target/performance-budget-checks/check-20260614-160854/performance-budget-summary.json`, `10/10`)
- 最新 jar SHA256: `A456099E009C043F08EA074C11CDE927085DAD5AAA491B574CFAFD125F2B1EAD`

### 2026-06-14 16:28 +08:00 最新补充验证

本轮继续把政策/科技高风险策略命令接入配置化官员授权，并用真实 Paper + Browser smoke 锁住非管理员 `steward` 路径：

```text
strategyFeedback=...+policyOfficer=steward:clear+set+technologyOfficer=steward:revoke+unlock+warOfficer=marshal:declare+diplomat:end
```

完整验证状态：

- `mvn -q "-Dtest=StarCoreCommandShortAliasDispatchTest,ConfigurationServiceResourceFeedbackConfigTest" test`: PASS
- `mvn -q test`: PASS (`328` tests)
- `mvn -q -DskipTests package`: PASS
- `.\scripts\smoke-starcore-paper-integration.ps1 -ProtectorApiSmoke -BrowserSmoke -TimeoutSeconds 420`: PASS
- `.\scripts\check-starcore-performance-budget.ps1 -SmokeSummaryJsonPath target\smoke-harness-20260614-162450\smoke-summary.json`: PASS (`10/10`)
- `.\scripts\check-map-hud-contract.ps1 -RequireMirrorRoots`: PASS
- `.\scripts\build-latest-verification-summary.ps1`: PASS

最新证据：

- Smoke summary: `target/smoke-harness-20260614-162450/smoke-summary.json`
- Performance budget: `target/performance-budget-checks/check-20260614-162549/performance-budget-summary.json`
- HUD contract: `target/map-hud-contract-checks/check-20260614-162549/map-hud-contract-summary.json`
- Jar SHA256: `A3002E82061C219E6754FACB4EBB785275421C6DFB6CACC815EE4FB3DC560EC2`

### 2026-06-14 16:51 +08:00 最新补充验证

本轮把配置化官员权限进一步暴露到 Web 国家详情，新增“官员授权”矩阵。后端领土 metadata 输出 9 个 `officerRole*` 字段，前端只展示 metadata 中的当前配置值；Browser smoke 在真实运行服锁住：

```text
STARCORE_BROWSER_SMOKE_PASS ... officerAuth=marshal+treasurer+diplomat+steward:9 ... commandUiRemoved=true
```

完整验证状态：

- `node --check src/main/resources/web/map/js/map.js`: PASS
- `node --check scripts/smoke-starcore-map-browser.mjs`: PASS
- `mvn -q "-Dtest=MapModuleViewerSnapshotContractTest#territoryPolygonIncludesOperationalMetadataForNationDetailPanel" test`: PASS
- `mvn -q "-Dtest=MapModuleViewerSnapshotContractTest,ConfigurationServiceResourceFeedbackConfigTest" test`: PASS
- `mvn -q test`: PASS (`328` tests)
- `mvn -q -DskipTests package`: PASS
- `.\scripts\smoke-starcore-paper-integration.ps1 -ProtectorApiSmoke -BrowserSmoke -TimeoutSeconds 420`: PASS
- `.\scripts\check-starcore-performance-budget.ps1 -SmokeSummaryJsonPath target\smoke-harness-20260614-164915\smoke-summary.json`: PASS (`10/10`)
- `.\scripts\check-map-hud-contract.ps1 -RequireMirrorRoots`: PASS
- `.\scripts\build-latest-verification-summary.ps1`: PASS

最新证据：

- Smoke summary: `target/smoke-harness-20260614-164915/smoke-summary.json`
- Performance budget: `target/performance-budget-checks/check-20260614-165044/performance-budget-summary.json`
- HUD contract: `target/map-hud-contract-checks/check-20260614-165044/map-hud-contract-summary.json`
- Jar SHA256: `3EC866E9F70EC51009D633D937EA3CC5DB521647054C26FDC9754D8753744EE8`

### 2026-06-14 17:18 +08:00 最新补充验证

本轮把 Web 国家详情官员授权矩阵继续升级为“当前访问者授权状态”。后端 viewer snapshot 为 9 个高价值国家动作输出 `viewerCanOfficer*`、`viewerOfficerStatus*`、`viewerOfficerMatchedRole*`，前端显示当前访问者能否操作以及原因；Browser smoke 在真实运行服锁住：

```text
STARCORE_BROWSER_SMOKE_PASS ... officerAuth=marshal+treasurer+diplomat+steward:9 officerAccess=founder:9 ... commandUiRemoved=true
```

完整验证状态：

- `node --check src/main/resources/web/map/js/map.js`: PASS
- `node --check scripts/smoke-starcore-map-browser.mjs`: PASS
- `mvn -q "-Dtest=MapModuleViewerSnapshotContractTest#territoryRelationAddsViewerOfficerAuthorizationStateForNationDetailPanel" test`: PASS
- `mvn -q "-Dtest=MapModuleViewerSnapshotContractTest,ConfigurationServiceResourceFeedbackConfigTest" test`: PASS
- `mvn -q test`: PASS (`329` tests)
- `mvn -q -DskipTests package`: PASS
- `.\scripts\smoke-starcore-paper-integration.ps1 -ProtectorApiSmoke -BrowserSmoke -TimeoutSeconds 420`: PASS
- `.\scripts\check-starcore-performance-budget.ps1 -SmokeSummaryJsonPath target\smoke-harness-20260614-171602\smoke-summary.json`: PASS (`10/10`, baseline trend `ok`)
- `.\scripts\check-map-hud-contract.ps1 -RequireMirrorRoots`: PASS
- `.\scripts\build-latest-verification-summary.ps1`: PASS

最新证据：

- Smoke summary: `target/smoke-harness-20260614-181137/smoke-summary.json`
- Performance budget: `target/performance-budget-checks/check-20260614-181248/performance-budget-summary.json`
- HUD contract: `target/map-hud-contract-checks/check-20260614-181248/map-hud-contract-summary.json`
- Jar SHA256: `28AC8DEFEF0AFE5A9D35AB7CF41136D5F3D5F7D73DC0A77F50C36C374FF26EC8`

## 这轮补强内容

- 资源区块迁移官员权限已配置化：
  - `nation.officers.resource-district-migration-roles` 默认授权 `marshal`
  - 服务层信标打开、迁移确认、领取迁移核心、设置迁移目标共用同一套授权判断
  - 地图 resource district metadata / 迁移响应输出 `viewerCanManageMigration` 与 `viewerAuthorizedOfficerRole`
  - 单元测试已锁住 `marshal` 可迁移、`treasurer` 不误得迁移权
  - 真实服 smoke 已锁住非领袖 `marshal` 成员完成资源区块 GUI 迁移链路：`officerMigration=marshal:member+gui+target+forced`
- 国库支出官员权限已配置化：
  - `nation.officers.treasury-withdraw-roles` 默认授权 `treasurer`
  - `/starcore treasury withdraw` 允许管理员、目标国家 founder 或配置授权官职操作
  - `deposit / reward / income / tax` 仍保持管理员或系统结算入口
  - 单元测试已锁住非管理员 `treasurer` 可支出本国国库、普通成员不可支出
  - 真实服 smoke 已锁住非管理员 `treasurer` 完成国库支出：`treasuryOfficer=treasurer:withdraw`
- 外交直设官员权限已配置化：
  - `nation.officers.diplomacy-set-roles` 默认授权 `diplomat`
  - `/starcore diplomacy set` 允许管理员、任一参与国家 founder 或任一参与国家配置授权官职操作
  - 普通成员仍可走外交提案流程，不能直接改任意两国关系
  - 单元测试已锁住非管理员 `diplomat` 可直设本国参与关系、普通成员不可直设
  - 真实服 smoke 已锁住非管理员 `diplomat` 完成外交直设：`diplomacyOfficer=diplomat:set`
- 战争宣告/停战官员权限已配置化：
  - `nation.officers.war-declare-roles` 默认授权 `marshal`
  - `nation.officers.war-end-roles` 默认授权 `marshal` 与 `diplomat`
  - `/starcore war declare` 和 `/starcore war end` 允许管理员、任一参与国家 founder 或任一参与国家配置授权官职操作
  - 单元测试已锁住非管理员 `marshal` 可宣战、非管理员 `diplomat` 可停战、普通成员不可调整战争状态
  - 真实服 smoke 已锁住非管理员战争官员链路：`warOfficer=marshal:declare+diplomat:end`
- 政策官员权限已配置化：
  - `nation.officers.policy-set-roles` 默认授权 `steward`
  - `nation.officers.policy-clear-roles` 默认授权 `steward`
  - `/starcore policy set` 和 `/starcore policy clear` 允许管理员、本国 founder 或本国配置授权官职操作
  - 单元测试已锁住非管理员 `steward` 可管理本国政策、普通成员不可管理政策
  - 真实服 smoke 已锁住非管理员政策官员链路：`policyOfficer=steward:clear+set`
- 科技官员权限已配置化：
  - `nation.officers.technology-unlock-roles` 默认授权 `steward`
  - `nation.officers.technology-revoke-roles` 默认授权 `steward`
  - `/starcore technology unlock` 和 `/starcore technology revoke` 允许管理员、目标国家 founder 或目标国家配置授权官职操作
  - 单元测试已锁住非管理员 `steward` 可管理本国科技、普通成员不可管理科技
  - 真实服 smoke 已锁住非管理员科技官员链路：`technologyOfficer=steward:revoke+unlock`
- Web 国家详情官员授权矩阵已完成：
  - `NationMapMetadataSupport` 从 `ConfigurationService` 输出资源迁移、国库、外交、战争、政策、科技对应的 `officerRole*` metadata
  - `MapModule` 继续追加当前访问者逐项授权 metadata，区分 `founder/officer/needs-appointment/external-nation/anonymous/no-role-config`
  - `map.js` 渲染 `data-officer-authorization="true"` 授权区，Browser smoke 和外层 Paper smoke 会检查 DOM 与 `officerAuth=marshal+treasurer+diplomat+steward:9`、`officerAccess=founder:9`
  - `MapModuleViewerSnapshotContractTest` 使用非默认角色组合锁住 metadata 来自配置，而不是写死默认值
- 完整事件日志 actor/reason/context 搜索已完成：
  - `/api/map/events` 支持 `query` / `q` / `search` 通用全文搜索，匹配事件类型、消息、分类、context、details key/value
  - `/api/map/events` 支持 `actor` 搜索，覆盖 `actor/operator/player/viewer/member/target/targetName` 等上下文别名
  - `/api/map/events` 支持 `reason` 搜索，覆盖 `reason/cause/operation/action/policy/technology/relation/warId` 等上下文别名
  - 国家详情完整事件日志 UI 新增搜索框，分类、资源区块、时间范围、分页和 CSV/JSON 导出都会保留搜索条件
  - CSV/JSON 导出 metadata 与 CSV header 已包含 `query/actor/reason`
  - Browser smoke 已锁住 `eventSearch=%E8%B5%84%E6%BA%90:3`，证明真实 Browser DOM、后端查询和导出 URL 联动可用
- 完整事件日志上下文 chip 一键追责已完成：
  - 事件条目会把 `details` 中的可追责字段渲染成 `data-event-ledger-context-chip="true"` 按钮
  - actor 别名 chip 会驱动 `/api/map/events?actor=...`
  - reason / policy / technology / relation / warId 等别名 chip 会驱动 `/api/map/events?reason=...`
  - 当前上下文筛选态会显示跨事件跳转按钮，保留同一 `query/actor/reason` 值但清掉分类与资源区块约束，重新查询完整事件集
  - amount / balance / members / claims / tax 等事实字段会渲染为只读 `data-event-ledger-context-readonly="true"` pill，避免金额和余额被误点成追责筛选
  - 搜索框旁会显示当前上下文筛选状态，清空动作会同时清掉 `query/actor/reason`
  - Browser smoke 已在主 smoke 国家里写入 `officer.audit` 审计事件，并锁住 `eventContext=actor-SmokeAuditor:1`
  - Browser smoke 现在还会真实点击 `reason=browser-context-chip` 并锁住 `eventReason=reason-browser-context-chip:1`
  - Browser smoke 现在还会真实点击跨事件跳转并锁住 `eventJump=reason-browser-context-chip:1`
  - Browser smoke 现在还会锁住 `eventFacts=amount+balance:2`，证明只读上下文字段分组已渲染
  - Browser smoke 现在还会切到 390x844 mobile viewport，验证搜索、当前上下文 chip、跨事件跳转按钮、上下文 chip 和 amount/balance facts 在窄屏下可见、无横向溢出和控件重叠，并锁住 `eventMobile=390x844:6`
  - Smoke summary、release evidence pack、release channel assets 现在都会带 `browserMobileScreenshot`，最新证据为 `zcode-starcore-map-browser-smoke-20260614-193403.mobile.png`
  - `MapEventLogEndpointTest` 已补政策、科技、外交、战争、财政 actor/reason/facts 样例，锁住 `/api/map/events` 多事件族契约
- 新增地图访问者 JSON 契约测试：
  - `src/test/java/dev/starcore/starcore/module/map/MapModuleViewerSnapshotContractTest.java`
- 新增国家成长 / 资源区块流程护栏测试：
  - `src/test/java/dev/starcore/starcore/module/nation/NationModuleResourceProgressionTest.java`
- 新增资源区块服务层流程护栏测试：
  - `src/test/java/dev/starcore/starcore/module/nation/resource/NativeNationResourceDistrictServiceFlowTest.java`
- 新增 ProtectorAPI 反射契约测试：
  - `src/test/java/dev/starcore/starcore/foundation/protection/ProtectorApiBridgeContractTest.java`
- 新增统一发布验证入口：
  - `scripts/verify-starcore-release.ps1`
- 新增 ProtectorAPI 参考契约检查脚本：
  - `scripts/check-protectorapi-reference.ps1`
- 新增保护桥故障隔离层：
  - `src/main/java/dev/starcore/starcore/foundation/protection/GuardedExternalProtectionService.java`
- 新增 ProtectorAPI 反射契约集中层：
  - `src/main/java/dev/starcore/starcore/foundation/protection/ProtectorApiBridgeContract.java`
- `ExternalProtectionServices` 现在会把 provider 初始化失败收口为单桥降级，不再把整条外部保护链一起拖垮
- `build-latest-verification-summary.ps1` 已支持：
  - 轻量验证时不强依赖 smoke
  - 明确标记“本次未包含 smoke”
- 完整验证入口已真实打通：
  - `IncludeSmoke`
  - `ProtectorApiSmoke`
  - `BrowserSmoke`
  - `BuildEvidencePack`
  - `BuildReleaseChannelAssets`
- 修复了 deep smoke 脚本在 PowerShell 5.1 下的中文编码问题：
  - `scripts/smoke-starcore-paper-integration.ps1`
  - 现已改成 PowerShell 5.1 可稳定读取的 UTF-8 BOM

## 当前仍待继续的产品面

- 产品面还需要继续收口：
  - 资源区块信标 GUI 的国家领袖闭环
  - 网页地图上的国家领袖管理视角
  - 资源区块详情、迁移状态、余额与地图操作的联动
- 还没有真正执行发布上传动作；当前完成的是：
  - 功能面
  - 验证面
  - 运维面
  - 发布材料与发版成品包基础面
- PlaceholderAPI、Vault、LuckPerms、Towny、WorldDynamics 仍保持非运行时依赖，这是当前设计边界，不是遗漏。

## 当前推荐入口

- 验证总览：
  - [LATEST_VERIFICATION_SUMMARY.md](/D:/qwq/项目/mapadd/starcore/docs/LATEST_VERIFICATION_SUMMARY.md)
- 运维手册：
  - [OPERATIONS_RUNBOOK_2026-06-05.md](/D:/qwq/项目/mapadd/starcore/docs/OPERATIONS_RUNBOOK_2026-06-05.md)
- 发布清单：
  - [RELEASE_CHECKLIST_2026-06-05.md](/D:/qwq/项目/mapadd/starcore/docs/RELEASE_CHECKLIST_2026-06-05.md)
