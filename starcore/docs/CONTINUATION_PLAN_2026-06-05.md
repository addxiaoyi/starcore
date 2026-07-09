# STARCORE 续跑主看板

日期: 2026-06-14（文件名沿用 2026-06-05，内容已按最新续跑刷新）

## 这份文档怎么用

这份文档现在是 STARCORE 长任务的唯一主看板。

- 看当前真实状态: 先看这里
- 看自动生成的最新验证面: 再看 `LATEST_VERIFICATION_SUMMARY.md`
- 看已完成功能总表: 再看 `COMPLETION_STATUS_2026-06-04.md`
- 看圈地/资源区块设计细节: 再看 `CLAIM_RESOURCE_SYSTEM_PLAN_2026-06-04.md`
- 看模块分层波次和后续功能规划: 再看 `MODULE_PLAN.md`

后续每次续做，优先更新这份文档，不要把“当前状态”分散写到多份文件里。

## 当前状态

### 2026-06-15 外交 + 策略真实命令来源事件 context（当前最新续跑，已完成）

本轮继续把外交和策略类高价值命令从 smoke audit fixture 推向真实命令来源事件 context：`diplomacy set`、`policy set/clear`、`technology unlock/revoke` 现在都会把执行者、目标、操作类型、策略/科技键、关系显示值、排序后的 `pairId` 和可选原因写进真实事件 context。Paper smoke 也改为在真实 `/starcore diplomacy`、`/starcore policy`、`/starcore technology` 命令之后读取 `EventService.eventsOf(...)`，确认事件不是 mock fixture 伪造。当前深烟测证据来自 2026-06-15 06:57 的同一轮运行服 smoke，摘要在 2026-06-15 08:03 重新生成。

- 新增/更新:
  - `src/main/java/dev/starcore/starcore/command/StarCoreCommand.java`
  - `src/main/resources/messages_zh_cn.yml`
  - `src/test/java/dev/starcore/starcore/command/StarCoreCommandShortAliasDispatchTest.java`
  - `scripts/smoke-starcore-paper-integration.ps1`
  - `docs/CONTINUATION_PLAN_2026-06-05.md`
  - `docs/COMPLETION_STATUS_2026-06-04.md`
  - `docs/LATEST_VERIFICATION_SUMMARY.md`
  - `docs/OPERATIONS_RUNBOOK_2026-06-05.md`
  - `docs/RELEASE_CHECKLIST_2026-06-05.md`
  - `README.md`
- 功能:
  - `/starcore diplomacy set <nationA> <nationB> <neutral|friendly|allied|hostile|war|vassal> [原因...]` 现在会把真实命令来源写入 `diplomacy.updated` context：`actor`、`operation=diplomacy-set`、`target`、`targetId`、`relation`、`relationDisplay`、`pairId=<sorted nation ids>`、`reason`。
  - `/starcore policy set <policyKey> [原因...]` 现在会把真实命令来源写入 `policy.set` context：`actor`、`operation=policy-set`、`target`、`targetId`、`policy`、`reason`。
  - `/starcore policy clear [原因...]` 现在会把真实命令来源写入 `policy.cleared` context：`actor`、`operation=policy-clear`、`target`、`targetId`、`policy`、`reason`。
  - `/starcore technology unlock/revoke <nation> <technology> [原因...]` 现在会把真实命令来源写入 `technology.unlocked` / `technology.revoked` context：`actor`、`operation=technology-unlock|technology-revoke`、`target`、`targetId`、`technology`、`reason`。
  - Paper smoke 新增硬 marker：`eventCommandSourcesExtended=war+officer+treasury+diplomacy+strategy:14`、`strategyCommandEvents=policySet+policyClear+technologyUnlock+technologyRevoke:6`、`diplomacyCommandEvents=founderSet+diplomatSet:2`。
- 验证:
  - `mvn -q "-Dtest=StarCoreCommandShortAliasDispatchTest#diplomacyDiplomatCanDirectlySetParticipatingNationWithoutAdminPermission+policyStewardCanSetAndClearOwnNationWithoutAdminPermission+technologyStewardCanUnlockAndRevokeTargetNationWithoutAdminPermission" test`: PASS
  - `mvn -q "-Dtest=StarCoreCommandShortAliasDispatchTest" test`: PASS
  - `mvn package`: PASS，`332` tests / `0` failures / `0` errors / `0` skipped
  - `.\scripts\smoke-starcore-paper-integration.ps1 -ProtectorApiSmoke -BrowserSmoke -TimeoutSeconds 420`: PASS
    - Paper marker 新增 `eventCommandSourcesExtended=war+officer+treasury+diplomacy+strategy:14`
    - Paper marker 新增 `strategyCommandEvents=policySet+policyClear+technologyUnlock+technologyRevoke:6`
    - Paper marker 新增 `diplomacyCommandEvents=founderSet+diplomatSet:2`
    - Browser marker 继续包含 `eventCommandSources=war+officer+treasury:6`
    - Browser marker 继续包含 `eventOpsFamilies=finance+war+officer+diplomacy+strategy+territory+nation:7`
    - Browser marker 继续包含 `eventFamilies=finance+war+officer+diplomacy+strategy+territory+nation:7`
    - Browser marker 继续包含 `eventFamilyMobile=finance+war+officer+diplomacy+strategy+territory+nation:7`
    - Browser marker 继续包含 `commandUiRemoved=true`
  - `.\scripts\check-map-hud-contract.ps1 -RequireMirrorRoots`: PASS，summary `target\map-hud-contract-checks\check-20260615-065742\map-hud-contract-summary.json`。
  - `.\scripts\check-starcore-performance-budget.ps1 -SmokeSummaryJsonPath target\smoke-harness-20260615-065629\smoke-summary.json`: PASS，summary `target\performance-budget-checks\check-20260615-065755\performance-budget-summary.json`，`10/10` pass，baseline trend `ok`。
  - `.\scripts\build-latest-verification-summary.ps1 -SmokeSummaryJsonPath target\smoke-harness-20260615-065629\smoke-summary.json -PerformanceBudgetSummaryPath target\performance-budget-checks\check-20260615-065755\performance-budget-summary.json -HudContractSummaryPath target\map-hud-contract-checks\check-20260615-065742\map-hud-contract-summary.json`: PASS。

最新证据：

- Smoke summary: `target\smoke-harness-20260615-065629\smoke-summary.json`
- Performance budget: `target\performance-budget-checks\check-20260615-065755\performance-budget-summary.json`
- HUD contract: `target\map-hud-contract-checks\check-20260615-065742\map-hud-contract-summary.json`
- Browser DOM: `D:\qwq\项目\mapadd\test-server-paper-1.21.11\zcode-starcore-map-browser-smoke-20260615-065629.dom.html`
- Browser screenshot: `D:\qwq\项目\mapadd\test-server-paper-1.21.11\zcode-starcore-map-browser-smoke-20260615-065629.png`
- Browser mobile screenshot: `D:\qwq\项目\mapadd\test-server-paper-1.21.11\zcode-starcore-map-browser-smoke-20260615-065629.mobile.png`
- Paper log: `D:\qwq\项目\mapadd\test-server-paper-1.21.11\zcode-starcore-deep-integration-smoke-20260615-065629.out.log`
- Latest jar SHA256: `46C5F431B9CFFC6120CABB94391AC4789BAF40B8175E80BEE2254604EBC5BC00`

下一步：

1. 继续补领地、国家生命周期和 resolution 相关真实命令来源 context。
2. 观察下一轮性能预算趋势，确认 `map_render`、`map_render_batch`、`sql_flush`、`sql_flush_cases` 的 warning 是否回落。
3. 采集并导入真实 Spark profile 后，跑带真实门禁的一站式 zip 发版验证。

### 2026-06-15 真实命令来源事件 context（当前最新续跑，已完成）

本轮按上一项待办继续减少“只靠 smoke audit fixture 证明运营联动”的比例，把战争、官员和国库支出三类真实命令写出的事件 context 纳入 Paper smoke 硬 marker。命令层不按 smoke 名称写死：`war declare/end` 与 `officer appoint/remove` 只新增可选原因尾参，事件 context 由命令执行者、目标国家/玩家、操作类型、角色、金额和余额等真实运行态字段组装。

- 新增/更新:
  - `src/main/java/dev/starcore/starcore/command/StarCoreCommand.java`
  - `src/main/resources/messages_zh_cn.yml`
  - `src/test/java/dev/starcore/starcore/command/StarCoreCommandShortAliasDispatchTest.java`
  - `scripts/smoke-starcore-paper-integration.ps1`
  - `docs/CONTINUATION_PLAN_2026-06-05.md`
  - `docs/COMPLETION_STATUS_2026-06-04.md`
  - `README.md`
  - `docs/OPERATIONS_RUNBOOK_2026-06-05.md`
  - `docs/RELEASE_CHECKLIST_2026-06-05.md`
- 功能:
  - `/starcore war declare/end <nationA> <nationB> [原因...]` 现在会把真实命令来源写入 `war.declared` / `war.ended` context：`actor`、`operation`、`target`、`targetId`、排序后的 `warId`、`reason`。
  - `/starcore officer appoint/remove ... [原因...]` 现在会把真实命令来源写入 `officer.appointed` / `officer.removed` context：`actor`、`operation`、`role`、`member/target/targetId`、`reason`。
  - Paper smoke 在真实 `/starcore war`、`/starcore officer`、`/starcore treasury withdraw` 命令后读取 `EventService.eventsOf(...)`，确认 context 不是 audit fixture 伪造。
  - 新增硬 marker：`eventCommandSources=war+officer+treasury:6`、`warCommandEvents=founderDeclare+diplomatEnd+marshalDeclare:3`、`governanceCommandEvents=officerAppoint+officerRemove+treasuryWithdraw:3`。
- 验证:
  - `node --check src\main\resources\web\map\js\map.js`: PASS
  - `node --check scripts\smoke-starcore-map-browser.mjs`: PASS
  - PowerShell parse check for `scripts\smoke-starcore-paper-integration.ps1`: PASS
  - `mvn -q "-Dtest=StarCoreCommandShortAliasDispatchTest" test`: PASS
  - `mvn package`: PASS，`332` tests / `0` failures / `0` errors / `0` skipped
  - `.\scripts\sync-map-preview-shell.ps1`: PASS，已同步 `D:\qwq\项目\mapadd\map`
  - `.\scripts\smoke-starcore-paper-integration.ps1 -ProtectorApiSmoke -BrowserSmoke -TimeoutSeconds 420`: PASS
    - Paper marker 新增 `eventCommandSources=war+officer+treasury:6`
    - Paper marker 新增 `warCommandEvents=founderDeclare+diplomatEnd+marshalDeclare:3`
    - Paper marker 新增 `governanceCommandEvents=officerAppoint+officerRemove+treasuryWithdraw:3`
    - Browser marker 继续包含 `eventOpsFamilies=finance+war+officer+diplomacy+strategy+territory+nation:7`
    - Browser marker 继续包含 `eventFamilies=finance+war+officer+diplomacy+strategy+territory+nation:7`
    - Browser marker 继续包含 `eventFamilyMobile=finance+war+officer+diplomacy+strategy+territory+nation:7`
    - Browser marker 继续包含 `commandUiRemoved=true`
  - `.\scripts\check-map-hud-contract.ps1 -RequireMirrorRoots`: PASS，summary `target\map-hud-contract-checks\check-20260615-000304\map-hud-contract-summary.json`。
  - `.\scripts\check-starcore-performance-budget.ps1 -SmokeSummaryJsonPath target\smoke-harness-20260614-235933\smoke-summary.json`: PASS with warning，summary `target\performance-budget-checks\check-20260615-000330\performance-budget-summary.json`，`10/10` pass，baseline trend `warning`（`map_render`、`map_render_batch`、`sql_flush`、`sql_flush_cases` 历史中位线对比偏高，但均低于 configured budgets）。
  - `.\scripts\build-latest-verification-summary.ps1 -SmokeSummaryJsonPath target\smoke-harness-20260614-235933\smoke-summary.json -PerformanceBudgetSummaryPath target\performance-budget-checks\check-20260615-000330\performance-budget-summary.json -HudContractSummaryPath target\map-hud-contract-checks\check-20260615-000304\map-hud-contract-summary.json`: PASS。

最新证据：

- Smoke summary: `target\smoke-harness-20260614-235933\smoke-summary.json`
- Performance budget: `target\performance-budget-checks\check-20260615-000330\performance-budget-summary.json`
- HUD contract: `target\map-hud-contract-checks\check-20260615-000304\map-hud-contract-summary.json`
- Browser DOM: `D:\qwq\项目\mapadd\test-server-paper-1.21.11\zcode-starcore-map-browser-smoke-20260614-235933.dom.html`
- Browser screenshot: `D:\qwq\项目\mapadd\test-server-paper-1.21.11\zcode-starcore-map-browser-smoke-20260614-235933.png`
- Browser mobile screenshot: `D:\qwq\项目\mapadd\test-server-paper-1.21.11\zcode-starcore-map-browser-smoke-20260614-235933.mobile.png`
- Paper log: `D:\qwq\项目\mapadd\test-server-paper-1.21.11\zcode-starcore-deep-integration-smoke-20260614-235933.out.log`
- Latest jar SHA256: `C010EB68D89BA6DA73EB5B98A52DFDEA34E2AC94A88353FFD0AB0FD17008EE20`
- Map asset hashes after sync:
  - `map.js`: `79777FC4FF9009D2A9CA5BC31E9883D929F4599DBCB6953FBCCC9F9B60346622`
  - `styles.css`: `0825A4E115FEF7620D2B8510999BA6D19E37250A4804F05306E386BAE527FB7E`

下一步：

1. 继续补外交、政策/科技、领地、国家生命周期等真实命令来源 context，进一步减少 audit fixture 占比。
2. 观察下一轮性能预算趋势，尤其 `map_render`、`map_render_batch`、`sql_flush`、`sql_flush_cases` 的 median warning。
3. 采集并导入真实 Spark profile 后，跑带真实门禁的一站式 zip 发版验证。

### 2026-06-14 完整事件日志七类运营联动（历史续跑，已完成）

本轮继续上一项 `eventOpsFamilies` 做完整闭环，把财政、战争、官员三类事件也接入运营联动卡，使完整事件日志的七类真实上下文事件族都能从单条日志跳到可执行或可追责的处理入口。实现仍走集中式事件族定义表：`treasury.*` 映射国库支出追责与授权 key，`war.*` 映射宣战/停战授权边界，`officer.*` 映射官员管理追责；外交、策略、领地、国家四类沿用上一轮定义。资源事件继续保留 typed resource explanation 与 `open-operation-group`，不恢复旧 web 资源指挥面。

- 新增/更新:
  - `src/main/resources/web/map/js/map.js`
  - `scripts/smoke-starcore-map-browser.mjs`
  - `scripts/smoke-starcore-paper-integration.ps1`
  - `docs/CONTINUATION_PLAN_2026-06-05.md`
  - `docs/COMPLETION_STATUS_2026-06-04.md`
  - `docs/MODULE_PLAN.md`
  - `docs/OPERATIONS_RUNBOOK_2026-06-05.md`
  - `docs/RELEASE_CHECKLIST_2026-06-05.md`
- 功能:
  - `eventLedgerOperationDefinitions()` 新增 `finance`、`war`、`officer` 三个事件族，不按 smoke 国家名或固定 fixture 写死。
  - 财政事件 `treasury.withdraw` 会显示国库支出运营入口，并携带 `amount/balance/target/reason/actor` 等上下文。
  - 战争事件 `war.declared` / `war.ended` 会显示战争处理入口，并按 `war-declare` / `war-end` 授权 key 暴露边界。
  - 官员事件 `officer.*` 会显示官员管理运营入口；没有专门 auth key 时回退到国家创始人/治理边界说明。
  - Browser smoke 的 `familySpecs` 已把前三类纳入 `operationOk`，外层 Paper smoke 已锁住 `eventOpsFamilies=finance+war+officer+diplomacy+strategy+territory+nation:7`。
- 验证:
  - `node --check src\main\resources\web\map\js\map.js`: PASS
  - `node --check scripts\smoke-starcore-map-browser.mjs`: PASS
  - PowerShell parse check for `scripts\smoke-starcore-paper-integration.ps1`: PASS
  - `mvn package`: PASS，`331` tests / `0` failures / `0` errors / `0` skipped
  - `.\scripts\sync-map-preview-shell.ps1`: PASS，已同步 `D:\qwq\项目\mapadd\map`
  - `.\scripts\smoke-starcore-paper-integration.ps1 -ProtectorApiSmoke -BrowserSmoke -TimeoutSeconds 420`: PASS
    - Browser marker 新增 `eventOpsFamilies=finance+war+officer+diplomacy+strategy+territory+nation:7`
    - Browser marker 继续包含 `eventOps=resource+explanation+auth+group:3`
    - Browser marker 继续包含 `eventFamilies=finance+war+officer+diplomacy+strategy+territory+nation:7`
    - Browser marker 继续包含 `eventFamilyMobile=finance+war+officer+diplomacy+strategy+territory+nation:7`
    - Browser marker 继续包含 `eventMobile=390x844:6`
    - Browser marker 继续包含 `eventLedgerExport=csv+json`
    - Browser marker 继续包含 `commandUiRemoved=true`
  - `.\scripts\check-map-hud-contract.ps1 -RequireMirrorRoots`: PASS，summary `target\map-hud-contract-checks\check-20260614-231212\map-hud-contract-summary.json`。
  - `.\scripts\check-starcore-performance-budget.ps1 -SmokeSummaryJsonPath target\smoke-harness-20260614-230514\smoke-summary.json`: PASS with warning，summary `target\performance-budget-checks\check-20260614-231347\performance-budget-summary.json`，`10/10` pass，baseline trend `warning`（claim lookup、map render、SQL flush batch 等历史中位线对比偏高，但均低于 configured budgets）。
  - `.\scripts\build-latest-verification-summary.ps1 -SmokeSummaryJsonPath target\smoke-harness-20260614-230514\smoke-summary.json -PerformanceBudgetSummaryPath target\performance-budget-checks\check-20260614-231347\performance-budget-summary.json -HudContractSummaryPath target\map-hud-contract-checks\check-20260614-231212\map-hud-contract-summary.json`: PASS。
  - 视觉检查：desktop 截图无明显侧栏溢出；390x844 mobile 截图中搜索、chip、facts、运营联动卡和按钮无明显重叠或横向溢出。

最新证据：

- Smoke summary: `target\smoke-harness-20260614-230514\smoke-summary.json`
- Performance budget: `target\performance-budget-checks\check-20260614-231347\performance-budget-summary.json`
- HUD contract: `target\map-hud-contract-checks\check-20260614-231212\map-hud-contract-summary.json`
- Browser DOM: `D:\qwq\项目\mapadd\test-server-paper-1.21.11\zcode-starcore-map-browser-smoke-20260614-230514.dom.html`
- Browser screenshot: `D:\qwq\项目\mapadd\test-server-paper-1.21.11\zcode-starcore-map-browser-smoke-20260614-230514.png`
- Browser mobile screenshot: `D:\qwq\项目\mapadd\test-server-paper-1.21.11\zcode-starcore-map-browser-smoke-20260614-230514.mobile.png`
- Paper log: `D:\qwq\项目\mapadd\test-server-paper-1.21.11\zcode-starcore-deep-integration-smoke-20260614-230514.out.log`
- Latest jar SHA256: `7B619AB8AD9158E8B758F9A7042242274EC49D523F6E961BFB37446652ACB01F`
- Map asset hashes after sync:
  - `map.js`: `79777FC4FF9009D2A9CA5BC31E9883D929F4599DBCB6953FBCCC9F9B60346622`
  - `styles.css`: `0825A4E115FEF7620D2B8510999BA6D19E37250A4804F05306E386BAE527FB7E`

下一步：

1. 继续补真实命令来源事件，减少只靠 smoke audit fixture 证明运营联动的比例。
2. 观察下一轮性能预算趋势，尤其 claim lookup、map render、SQL flush batch 的 median warning。
3. 采集并导入真实 Spark profile 后，跑带真实门禁的一站式 zip 发版验证。

### 2026-06-14 完整事件日志非资源运营联动（历史续跑，已完成）

本轮接着上一项 `eventOps` 做闭环，把完整事件日志里的外交、策略、领地、国家四类事件接到运营联动卡。前端新增一张事件族定义表，用事件 `category/type/details` 推导处理入口、上下文字段和授权 key：`diplomacy.updated` 走 `diplomacy-set`，`policy.* / technology.*` 走对应策略授权，`territory.* / claim.*` 和 `nation.* / city.* / resolution.*` 走治理/领地处理入口。资源事件仍保留 typed resource explanation、资源迁移授权和 `open-operation-group` 入口，不恢复旧 web 资源指挥面。

- 新增/更新:
  - `src/main/resources/web/map/js/map.js`
  - `scripts/smoke-starcore-map-browser.mjs`
  - `scripts/smoke-starcore-paper-integration.ps1`
  - `docs/CONTINUATION_PLAN_2026-06-05.md`
  - `docs/COMPLETION_STATUS_2026-06-04.md`
  - `docs/MODULE_PLAN.md`
  - `docs/OPERATIONS_RUNBOOK_2026-06-05.md`
  - `docs/RELEASE_CHECKLIST_2026-06-05.md`
- 功能:
  - `renderEventLedgerOperationLink(...)` 拆成资源专用卡和通用事件族运营卡，通用卡只消费定义表与 `details`，避免按 smoke fixture 写死 UI。
  - 外交、策略、领地、国家事件条目会渲染 `data-event-operation-link="true"`，并输出 `data-event-operation-family/action/auth-key/filter/reason/query`。
  - `event-ledger-operation-scope` 入口可一键把完整日志聚焦到同类 family + reason/query，方便从单条事件追到同类问题。
  - 外交与策略卡会复用官员授权矩阵；领地与国家治理卡显示命令权限/领袖管理员边界。
- 验证:
  - `node --check src\main\resources\web\map\js\map.js`: PASS
  - `node --check scripts\smoke-starcore-map-browser.mjs`: PASS
  - PowerShell parse check for `scripts\smoke-starcore-paper-integration.ps1`: PASS
  - `mvn package`: PASS，`331` tests / `0` failures / `0` errors / `0` skipped
  - `.\scripts\sync-map-preview-shell.ps1`: PASS，已同步 `D:\qwq\项目\mapadd\map`
  - `.\scripts\smoke-starcore-paper-integration.ps1 -ProtectorApiSmoke -BrowserSmoke -TimeoutSeconds 420`: PASS
    - Browser marker 新增 `eventOpsFamilies=diplomacy+strategy+territory+nation:4`
    - Browser marker 继续包含 `eventOps=resource+explanation+auth+group:3`
    - Browser marker 继续包含 `eventFamilies=finance+war+officer+diplomacy+strategy+territory+nation:7`
    - Browser marker 继续包含 `eventFamilyMobile=finance+war+officer+diplomacy+strategy+territory+nation:7`
    - Browser marker 继续包含 `eventMobile=390x844:6`
    - Browser marker 继续包含 `eventLedgerExport=csv+json`
    - Browser marker 继续包含 `commandUiRemoved=true`
  - `.\scripts\check-map-hud-contract.ps1 -RequireMirrorRoots`: PASS，summary `target\map-hud-contract-checks\check-20260614-224229\map-hud-contract-summary.json`。
  - `.\scripts\check-starcore-performance-budget.ps1 -SmokeSummaryJsonPath target\smoke-harness-20260614-224045\smoke-summary.json`: PASS with warning，summary `target\performance-budget-checks\check-20260614-224229\performance-budget-summary.json`，`10/10` pass，baseline trend `warning`（`map_render` suite time `0.368s` vs baseline `0.17s`，仍低于 configured `3.000s` budget）。
  - `.\scripts\build-latest-verification-summary.ps1 -SmokeSummaryJsonPath target\smoke-harness-20260614-224045\smoke-summary.json -PerformanceBudgetSummaryPath target\performance-budget-checks\check-20260614-224229\performance-budget-summary.json -HudContractSummaryPath target\map-hud-contract-checks\check-20260614-224229\map-hud-contract-summary.json`: PASS。

最新证据：

- Smoke summary: `target\smoke-harness-20260614-224045\smoke-summary.json`
- Performance budget: `target\performance-budget-checks\check-20260614-224229\performance-budget-summary.json`
- HUD contract: `target\map-hud-contract-checks\check-20260614-224229\map-hud-contract-summary.json`
- Browser DOM: `D:\qwq\项目\mapadd\test-server-paper-1.21.11\zcode-starcore-map-browser-smoke-20260614-224045.dom.html`
- Browser screenshot: `D:\qwq\项目\mapadd\test-server-paper-1.21.11\zcode-starcore-map-browser-smoke-20260614-224045.png`
- Browser mobile screenshot: `D:\qwq\项目\mapadd\test-server-paper-1.21.11\zcode-starcore-map-browser-smoke-20260614-224045.mobile.png`
- Paper log: `D:\qwq\项目\mapadd\test-server-paper-1.21.11\zcode-starcore-deep-integration-smoke-20260614-224045.out.log`
- Latest jar SHA256: `0E6771ECD2CF1E40BDAD2C9D0D2FA3D99FD0190B0360A35C47EF3DFFA86AD9BD`

下一步：

1. 财政、战争、官员事件运营入口已在当前最新续跑推进，等待完整验证回填。
2. 观察下一轮性能预算趋势，确认本轮 `map_render` baseline warning 是测试波动还是需要拆分/优化。
3. 采集并导入真实 Spark profile 后，跑带真实门禁的一站式 zip 发版验证。

### 2026-06-14 完整事件日志资源运营联动（历史续跑，已完成）

本轮继续 P2“官员 / 事件 / 审计日志”和 P1“资源区块管理闭环”，把完整事件日志里的资源类事件和资源 explanation、官员授权矩阵、运营处理入口连起来。资源事件条目现在会优先按事件 `resourceId` 找当前资源区块 metadata；如果这是一条历史资源事件、原资源 marker 已经因为迁移/刷新消失，则回退到当前国家运营焦点资源，显示 `resourceMigrationExplanation(...)` 的 typed explanation、`resource-migration` 授权角色和“处理同类问题”入口。这个入口复用现有 `open-operation-group`，不会恢复旧 web 资源指挥面，HUD 仍保持 intel-only。

- 新增/更新:
  - `src/main/resources/web/map/js/map.js`
  - `src/main/resources/web/map/css/styles.css`
  - `scripts/smoke-starcore-map-browser.mjs`
  - `scripts/smoke-starcore-paper-integration.ps1`
  - `docs/LATEST_VERIFICATION_SUMMARY.md`
- 功能:
  - 完整事件日志资源条目新增 `data-event-operation-link="true"` 的运营联动卡片，展示资源状态、迁移说明和处理授权。
  - 运营联动卡片复用 `resourceOperationSummary(...)`、`resourceMigrationExplanation(...)` 和 `nationOfficerAuthorizationRows(...)`，不写死具体事件类型。
  - 对历史资源事件支持 fallback：事件资源 ID 不再存在于当前 marker 时，仍能跳到当前国家最相关的运营焦点，形成“处理同类问题”工作流。
  - “处理同类问题”按钮复用 `open-operation-group` 和优先队列筛选，点击后会选中对应资源并高亮对应状态组。
  - 静态预览壳已通过 `scripts/sync-map-preview-shell.ps1` 同步，三份 map 资产继续满足 `commandUiRemoved=true` 契约。
- 验证:
  - `node --check scripts\smoke-starcore-map-browser.mjs`: PASS
  - `node --check src\main\resources\web\map\js\map.js`: PASS
  - PowerShell parse check for `scripts\smoke-starcore-paper-integration.ps1`: PASS
  - `mvn package`: PASS，`331` tests / `0` failures / `0` errors / `0` skipped
  - `.\scripts\smoke-starcore-paper-integration.ps1 -ProtectorApiSmoke -BrowserSmoke -TimeoutSeconds 420`: PASS
    - Browser marker 新增 `eventOps=resource+explanation+auth+group:3`
    - Browser marker 继续包含 `eventFamilies=finance+war+officer+diplomacy+strategy+territory+nation:7`
    - Browser marker 继续包含 `eventFamilyMobile=finance+war+officer+diplomacy+strategy+territory+nation:7`
    - Browser marker 继续包含 `eventMobile=390x844:6`
    - Browser marker 继续包含 `eventLedgerExport=csv+json`
    - Browser marker 继续包含 `resourceExplanationRuntime=ready+awaiting-target+waiting-depletion+insufficient-balance+player-offline:5`
    - Browser marker 继续包含 `commandUiRemoved=true`
  - `.\scripts\check-starcore-performance-budget.ps1 -SmokeSummaryJsonPath target\smoke-harness-20260614-220633\smoke-summary.json`: PASS，summary `target\performance-budget-checks\check-20260614-220744\performance-budget-summary.json`，`10/10` pass，baseline trend `ok`。
  - `.\scripts\check-map-hud-contract.ps1 -RequireMirrorRoots`: PASS，summary `target\map-hud-contract-checks\check-20260614-220744\map-hud-contract-summary.json`。
  - `.\scripts\build-latest-verification-summary.ps1 -SmokeSummaryJsonPath target\smoke-harness-20260614-220633\smoke-summary.json -PerformanceBudgetSummaryPath target\performance-budget-checks\check-20260614-220744\performance-budget-summary.json -HudContractSummaryPath target\map-hud-contract-checks\check-20260614-220744\map-hud-contract-summary.json`: PASS。

最新证据：

- Smoke summary: `target\smoke-harness-20260614-220633\smoke-summary.json`
- Performance budget: `target\performance-budget-checks\check-20260614-220744\performance-budget-summary.json`
- HUD contract: `target\map-hud-contract-checks\check-20260614-220744\map-hud-contract-summary.json`
- Browser DOM: `D:\qwq\项目\mapadd\test-server-paper-1.21.11\zcode-starcore-map-browser-smoke-20260614-220633.dom.html`
- Browser screenshot: `D:\qwq\项目\mapadd\test-server-paper-1.21.11\zcode-starcore-map-browser-smoke-20260614-220633.png`
- Browser mobile screenshot: `D:\qwq\项目\mapadd\test-server-paper-1.21.11\zcode-starcore-map-browser-smoke-20260614-220633.mobile.png`
- Paper log: `D:\qwq\项目\mapadd\test-server-paper-1.21.11\zcode-starcore-deep-integration-smoke-20260614-220633.out.log`
- Latest jar SHA256: `979E185B6A7C86CC9A0D5C72C806632CAC0B6C81736FDF43129F511EE2DBC76E`

下一步：

1. 把财政/战争/官员事件也接入对应处理入口，但先补 Web 侧真实动作或明确只读追责语义。
2. 继续给完整事件日志补更细的真实命令来源事件，而不只是审计 fixture。
3. 采集并导入真实 Spark profile 后，跑带真实门禁的一站式 zip 发版验证。

### 2026-06-14 完整事件日志 7 类事件族移动端真实上下文（历史续跑，已完成）

本轮继续 P2“官员 / 事件 / 审计日志”，把完整事件日志多事件族基线从财政、战争、官员三类扩到财政、战争、官员、外交、策略、领地、国家七类真实运行服事件族。Paper harness 现在会通过真实 `EventService` 写入 `treasury.withdraw`、`war.declared`、`officer.appointed`、`diplomacy.updated`、`policy.set`、`territory.claimed`、`nation.created` 七条带 `actor/reason` 和只读 facts 的上下文审计事件，并在 `STARCORE_SMOKE_PASS` 输出 `eventFamilies=finance+war+officer+diplomacy+strategy+territory+nation:7`。Browser smoke 会在真实认证 Browser 中逐类加载完整事件日志，验证分类、reason 过滤、actor/reason chip、只读 facts 和跨事件跳转；移动端 390x844 基线也会逐类覆盖同七种事件族，输出 `eventFamilyMobile=finance+war+officer+diplomacy+strategy+territory+nation:7`。旧 web 资源指挥面继续缺席，HUD 契约仍是 intel-only。

- 新增/更新:
  - `scripts/smoke-starcore-paper-integration.ps1`
  - `scripts/smoke-starcore-map-browser.mjs`
  - `docs/LATEST_VERIFICATION_SUMMARY.md`
- 功能:
  - Paper smoke 新增外交上下文事件：`diplomacy.updated` / `actor=SmokeDiplomat` / `reason=treaty-reset` / facts `fixed/members`。
  - Paper smoke 新增策略上下文事件：`policy.set` / `actor=SmokeSteward` / `reason=strategy-review` / facts `fixed/percent`。
  - Paper smoke 新增领地上下文事件：`territory.claimed` / `actor=SmokeFounder` / `reason=border-growth` / facts `claims/fixed`。
  - Paper smoke 新增国家上下文事件：`nation.created` / `actor=SmokeFounder` / `reason=foundation-cycle` / facts `members/claims`。
  - Browser smoke 的桌面事件族验证从 3 类提升到 7 类，仍逐类验证分类过滤、reason、actor chip、reason chip、只读 facts 和 jump。
  - Browser mobile baseline 同步从 3 类提升到 7 类，并继续保留移动端无横向溢出、控件不重叠、触控高度和截图可见性断言。
- 验证:
  - `node --check scripts\smoke-starcore-map-browser.mjs`: PASS
  - PowerShell parse check for `scripts\smoke-starcore-paper-integration.ps1`: PASS
  - `.\scripts\smoke-starcore-paper-integration.ps1 -ProtectorApiSmoke -BrowserSmoke -TimeoutSeconds 420`: PASS
    - Paper marker 包含 `eventFamilies=finance+war+officer+diplomacy+strategy+territory+nation:7`
    - Browser marker 包含 `eventFamilies=finance+war+officer+diplomacy+strategy+territory+nation:7`
    - Browser marker 包含 `eventFamilyMobile=finance+war+officer+diplomacy+strategy+territory+nation:7`
    - Browser marker 继续包含 `eventMobile=390x844:6`
    - Browser marker 继续包含 `eventLedgerExport=csv+json`
    - Browser marker 继续包含 `resourceExplanationRuntime=ready+awaiting-target+waiting-depletion+insufficient-balance+player-offline:5`
    - Browser marker 继续包含 `commandUiRemoved=true`
  - `.\scripts\check-starcore-performance-budget.ps1 -SmokeSummaryJsonPath target\smoke-harness-20260614-213637\smoke-summary.json`: PASS，summary `target\performance-budget-checks\check-20260614-213820\performance-budget-summary.json`，`10/10` pass，baseline trend `ok`。
  - `.\scripts\check-map-hud-contract.ps1 -RequireMirrorRoots`: PASS，summary `target\map-hud-contract-checks\check-20260614-213819\map-hud-contract-summary.json`。
  - `.\scripts\build-latest-verification-summary.ps1 -SmokeSummaryJsonPath target\smoke-harness-20260614-213637\smoke-summary.json -PerformanceBudgetSummaryPath target\performance-budget-checks\check-20260614-213820\performance-budget-summary.json -HudContractSummaryPath target\map-hud-contract-checks\check-20260614-213819\map-hud-contract-summary.json`: PASS。

最新证据：

- Smoke summary: `target\smoke-harness-20260614-213637\smoke-summary.json`
- Performance budget: `target\performance-budget-checks\check-20260614-213820\performance-budget-summary.json`
- HUD contract: `target\map-hud-contract-checks\check-20260614-213819\map-hud-contract-summary.json`
- Browser DOM: `D:\qwq\项目\mapadd\test-server-paper-1.21.11\zcode-starcore-map-browser-smoke-20260614-213637.dom.html`
- Browser screenshot: `D:\qwq\项目\mapadd\test-server-paper-1.21.11\zcode-starcore-map-browser-smoke-20260614-213637.png`
- Browser mobile screenshot: `D:\qwq\项目\mapadd\test-server-paper-1.21.11\zcode-starcore-map-browser-smoke-20260614-213637.mobile.png`
- Paper log: `D:\qwq\项目\mapadd\test-server-paper-1.21.11\zcode-starcore-deep-integration-smoke-20260614-213637.out.log`
- Latest jar SHA256: `1BFF30AFF500F711395A72D1F77E8A170F7752A30F25DD6812953D897E4C5F57`

下一步：

1. 资源事件和 resource explanation / 官员授权 / 运营快捷入口联动已在 22:07 段完成；后续继续扩到外交、策略、领地、国家事件的对应处理入口。
2. 采集并导入真实 Spark profile 后，跑带真实门禁的一站式 zip 发版验证。
3. 继续给完整事件日志补更细的外交/策略/领地/国家真实命令来源事件，而不只是审计 fixture。

### 2026-06-14 真实 Spark profile 发版门禁（已完成，等待真实 profile artifact）

本轮继续 P3“运维 / 发布 / 证据包”和 P3“性能 / 缓存 / 空间索引”，没有伪造真实 Spark 报告，而是先把正式发版入口补成可拦截样本证据的门禁。`verify-starcore-release.ps1` 现在新增 `-RequireRealSparkProfile`；只要启用该开关，就会在 Maven/test/smoke 前解析 `-SparkProfileSummaryPath`，拒绝缺失 summary、`sourceLabel=sample-verification`、`sourceLabel=manual`、缺本地 artifact、空 artifact 或缺 http(s) report URL 的 Spark profiling 证据。这样一站式 zip 发版命令不会把旧的 sample profile 打进正式证据包。

- 新增/更新:
  - `scripts/verify-starcore-release.ps1`
  - `README.md`
  - `docs/RELEASE_CHECKLIST_2026-06-05.md`
  - `docs/RELEASE_CHANNEL_PACK_2026-06-05.md`
  - `docs/OPERATIONS_RUNBOOK_2026-06-05.md`
  - `docs/MODULE_PLAN.md`
  - `docs/COMPLETION_STATUS_2026-06-04.md`
- 功能:
  - `verify-starcore-release.ps1 -RequireRealSparkProfile` 会要求 `status=ok`、非 sample/manual `sourceLabel`、绝对 http(s) `reportUrl`、存在且非空的本地 artifact、以及 `start/stop/open` 采集命令。
  - 轻量验证和日常完整 smoke 仍可不启用该门禁；正式打包命令文档已改为 `-SparkProfileSummaryPath <spark-profile-summary.json> -RequireRealSparkProfile`。
  - 最终 verify JSON 新增 `sparkProfileGate` 与 `sparkProfileSource`，便于发版后核对是否真的启用了真实 profile 门禁。
- 验证:
  - PowerShell parse check for `scripts\verify-starcore-release.ps1`: PASS
  - `.\scripts\verify-starcore-release.ps1 -RequireRealSparkProfile -SparkProfileSummaryPath target\spark-profile-imports\profile-script-check\spark-profile-summary.json`: 按预期失败，错误指向 `sourceLabel=sample-verification`，且失败发生在 Maven/test/smoke 前。

当前等待:

- 仓库中尚未发现真实运行服 Spark artifact；只有历史 `sample-verification` 样本。
- 下一步需要在 staging 或 production-shadow 服采集 spark 报告，执行 `.\scripts\import-starcore-spark-profile.ps1 -ReportPath <spark-report-file-or-directory> -ReportUrl <spark-report-url> -SourceLabel production-profile`，再跑 `.\scripts\verify-starcore-release.ps1 -IncludeSmoke -ProtectorApiSmoke -BrowserSmoke -BuildEvidencePack -BuildReleaseChannelAssets -SparkProfileSummaryPath <spark-profile-summary.json> -RequireRealSparkProfile`。

下一步：

1. 采集并导入真实 Spark profile 后，跑带真实门禁的一站式 zip 发版验证。
2. 外交、策略、领地、国家事件族移动端证据已在 21:36 段并入 7 类事件族 smoke。
3. 资源 explanation、官员授权和资源事件日志筛选的“处理同类问题”运营联动已在 22:07 段完成。

### 2026-06-14 完整事件日志多事件族移动端真实上下文（已完成）

本轮继续 P2“官员 / 事件 / 审计日志”，把完整事件日志移动端基线从单条 `officer.audit` 上下文扩展到财政、战争、官员三类真实运行服事件族。Paper harness 现在会通过真实 `EventService` 写入 `treasury.withdraw`、`war.declared`、`officer.appointed` 三条带 `actor/reason/target/amount/balance` 的上下文审计事件，并在 `STARCORE_SMOKE_PASS` 输出 `eventFamilies=finance+war+officer:3`。Browser smoke 会在真实认证 Browser 中依次加载 `finance`、`war`、`officer` 完整事件日志分类，验证 actor/reason chip、只读 facts、跨事件跳转按钮和分类过滤；移动端 390x844 基线也会逐类加载同三种事件族，确认 chip/facts/jump 在窄屏侧栏中可见、无横向溢出、无控件重叠。旧 web 资源指挥面继续缺席，HUD 契约仍是 intel-only。

- 新增/更新:
  - `scripts/smoke-starcore-paper-integration.ps1`
  - `scripts/smoke-starcore-map-browser.mjs`
  - `docs/LATEST_VERIFICATION_SUMMARY.md`
- 功能:
  - Paper smoke 新增财政上下文事件：`treasury.withdraw` / `actor=SmokeTreasurer` / `reason=audit-payout`。
  - Paper smoke 新增战争上下文事件：`war.declared` / `actor=SmokeMarshal` / `reason=border-conflict`。
  - Paper smoke 新增官员上下文事件：`officer.appointed` / `actor=SmokeFounder` / `reason=staff-rotation`。
  - Browser smoke 新增 `eventFamilies=finance+war+officer:3`，证明三类事件族都能在桌面 DOM 中按分类、reason、actor、facts 和 jump 渲染。
  - Browser mobile baseline 新增 `eventFamilyMobile=finance+war+officer:3`，证明同三类事件族在 390x844 移动视口下仍可读、可点、可截图。
  - 原有 `eventContext=actor-SmokeAuditor:1`、`eventReason=reason-browser-context-chip:1`、`eventJump=reason-browser-context-chip:1` 和 `eventFacts=amount+balance:2` 继续保留，避免回退。
- 验证:
  - `node --check scripts\smoke-starcore-map-browser.mjs`: PASS
  - PowerShell parse check for `scripts\smoke-starcore-paper-integration.ps1`: PASS
  - `.\scripts\smoke-starcore-paper-integration.ps1 -ProtectorApiSmoke -BrowserSmoke -TimeoutSeconds 420`: PASS
    - Paper marker 包含 `eventFamilies=finance+war+officer:3`
    - Browser marker 包含 `eventFamilies=finance+war+officer:3`
    - Browser marker 包含 `eventFamilyMobile=finance+war+officer:3`
    - Browser marker 继续包含 `eventMobile=390x844:6`
    - Browser marker 继续包含 `eventLedgerExport=csv+json`
    - Browser marker 继续包含 `resourceExplanationRuntime=ready+awaiting-target+waiting-depletion+insufficient-balance+player-offline:5`
    - Browser marker 继续包含 `commandUiRemoved=true`
  - `.\scripts\check-starcore-performance-budget.ps1 -SmokeSummaryJsonPath target\smoke-harness-20260614-210736\smoke-summary.json`: PASS，summary `target\performance-budget-checks\check-20260614-210846\performance-budget-summary.json`，`10/10` pass，baseline trend `ok`。
  - `.\scripts\check-map-hud-contract.ps1 -RequireMirrorRoots`: PASS，summary `target\map-hud-contract-checks\check-20260614-210847\map-hud-contract-summary.json`。
  - `.\scripts\build-latest-verification-summary.ps1 -SmokeSummaryJsonPath target\smoke-harness-20260614-210736\smoke-summary.json -PerformanceBudgetSummaryPath target\performance-budget-checks\check-20260614-210846\performance-budget-summary.json -HudContractSummaryPath target\map-hud-contract-checks\check-20260614-210847\map-hud-contract-summary.json`: PASS。

最新证据：

- Smoke summary: `target\smoke-harness-20260614-210736\smoke-summary.json`
- Performance budget: `target\performance-budget-checks\check-20260614-210846\performance-budget-summary.json`
- HUD contract: `target\map-hud-contract-checks\check-20260614-210847\map-hud-contract-summary.json`
- Browser DOM: `D:\qwq\项目\mapadd\test-server-paper-1.21.11\zcode-starcore-map-browser-smoke-20260614-210736.dom.html`
- Browser screenshot: `D:\qwq\项目\mapadd\test-server-paper-1.21.11\zcode-starcore-map-browser-smoke-20260614-210736.png`
- Browser mobile screenshot: `D:\qwq\项目\mapadd\test-server-paper-1.21.11\zcode-starcore-map-browser-smoke-20260614-210736.mobile.png`
- Paper log: `D:\qwq\项目\mapadd\test-server-paper-1.21.11\zcode-starcore-deep-integration-smoke-20260614-210736.out.log`
- Latest jar SHA256: `1BFF30AFF500F711395A72D1F77E8A170F7752A30F25DD6812953D897E4C5F57`

下一步：

1. 用真实运行服 spark profile 替换 sample profile 后，跑一站式 zip 发版验证。
2. 外交、策略、领地、国家事件族移动端证据已在 21:36 段并入 7 类事件族 smoke。
3. 资源 explanation、官员授权和资源事件日志筛选的“处理同类问题”运营联动已在 22:07 段完成。

### 2026-06-14 资源 explanation 多状态运行服真实数据造景（已完成）

本轮继续 P1“资源区块管理闭环”，把上一片已经验证过的多状态 Browser fixture 推进到真实运行服数据造景。Paper harness 现在会通过真实 `NativeNationResourceDistrictService` 内部对象和 MapModule snapshot 管线造出多类资源区块状态；Browser smoke 不再只看当前主 URL，而是同时拉取当前访问者、低余额官员和离线访问者的真实 `/api/map/snapshot`，再交给生产 `resourceMigrationExplanation(...)` 与 `renderResourceMigrationExplanation(...)` 渲染，证明 `ready`、`awaiting-target`、`waiting-depletion`、`insufficient-balance`、`player-offline` 五种状态都来自运行服数据，而不是硬编码 DOM fixture。旧 web 资源指挥面继续缺席，HUD 契约仍是 intel-only。

- 新增/更新:
  - `scripts/smoke-starcore-paper-integration.ps1`
  - `scripts/smoke-starcore-map-browser.mjs`
  - `docs/LATEST_VERIFICATION_SUMMARY.md`
- 功能:
  - Paper smoke 通过真实资源区块 service/snapshot 造景，并在 `STARCORE_SMOKE_PASS` 输出 `resourceExplanationRuntime=ready+awaiting-target+waiting-depletion+insufficient-balance+player-offline:5`。
  - Browser smoke 对多认证视图做运行时验证：在线创始人覆盖 `ready`、`awaiting-target`、`waiting-depletion`，在线低余额 marshal 覆盖 `insufficient-balance`，离线 viewer 覆盖 `player-offline`。
  - Browser smoke 初始资源选择优先当前访问者国家的资源，避免全访问视图误选到别国离线资源。
  - 资源详情快捷动作断言改为点击当下记录 `detailSelectOk` / `prioritySelectOk`，避免多真实资源后续点击改变 `selectedResourceId` 造成误判。
  - Browser PASS marker 同时保留 `resourceExplanationFixture=...:5` 和新增 `resourceExplanationRuntime=...:5`，分别锁住生产 renderer fixture 覆盖与真实运行服覆盖。
- 验证:
  - `node --check scripts\smoke-starcore-map-browser.mjs`: PASS
  - PowerShell parse check for `scripts\smoke-starcore-paper-integration.ps1`: PASS
  - `mvn -q -DskipTests package`: PASS
  - `.\scripts\smoke-starcore-paper-integration.ps1 -ProtectorApiSmoke -BrowserSmoke -TimeoutSeconds 420`: PASS
    - Paper marker 包含 `resourceExplanationRuntime=ready+awaiting-target+waiting-depletion+insufficient-balance+player-offline:5`
    - Browser marker 包含 `resourceExplanationRuntime=ready+awaiting-target+waiting-depletion+insufficient-balance+player-offline:5`
    - Browser marker 继续包含 `resourceExplanationFixture=ready+awaiting-target+waiting-depletion+insufficient-balance+player-offline:5`
    - Browser marker 继续包含 `eventMobile=390x844:6`
    - Browser marker 继续包含 `eventLedgerExport=csv+json`
    - Browser marker 继续包含 `commandUiRemoved=true`
  - `.\scripts\check-starcore-performance-budget.ps1 -SmokeSummaryJsonPath target\smoke-harness-20260614-204506\smoke-summary.json`: PASS，summary `target\performance-budget-checks\check-20260614-204635\performance-budget-summary.json`，`10/10` pass，baseline trend `ok`。
  - `.\scripts\check-map-hud-contract.ps1 -RequireMirrorRoots`: PASS，summary `target\map-hud-contract-checks\check-20260614-204635\map-hud-contract-summary.json`。
  - 旧资源指挥 UI token 扫描：无命中。
  - `.\scripts\build-latest-verification-summary.ps1 -SmokeSummaryJsonPath target\smoke-harness-20260614-204506\smoke-summary.json -PerformanceBudgetSummaryPath target\performance-budget-checks\check-20260614-204635\performance-budget-summary.json -HudContractSummaryPath target\map-hud-contract-checks\check-20260614-204635\map-hud-contract-summary.json`: PASS。

最新证据：

- Smoke summary: `target\smoke-harness-20260614-204506\smoke-summary.json`
- Performance budget: `target\performance-budget-checks\check-20260614-204635\performance-budget-summary.json`
- HUD contract: `target\map-hud-contract-checks\check-20260614-204635\map-hud-contract-summary.json`
- Browser DOM: `D:\qwq\项目\mapadd\test-server-paper-1.21.11\zcode-starcore-map-browser-smoke-20260614-204506.dom.html`
- Browser screenshot: `D:\qwq\项目\mapadd\test-server-paper-1.21.11\zcode-starcore-map-browser-smoke-20260614-204506.png`
- Browser mobile screenshot: `D:\qwq\项目\mapadd\test-server-paper-1.21.11\zcode-starcore-map-browser-smoke-20260614-204506.mobile.png`
- Paper log: `D:\qwq\项目\mapadd\test-server-paper-1.21.11\zcode-starcore-deep-integration-smoke-20260614-204506.out.log`
- Latest jar SHA256: `1BFF30AFF500F711395A72D1F77E8A170F7752A30F25DD6812953D897E4C5F57`

下一步：

1. 完整事件日志财政/战争/官员多事件族移动端基线已在 21:07 段完成；外交、策略、领地、国家事件族已在 21:36 段并入 7 类事件族 smoke。
2. 用真实运行服 spark profile 替换 sample profile 后，跑一站式 zip 发版验证。
3. 继续把资源 explanation 和官员权限、事件/日志筛选联动起来，形成更完整的运营闭环。

### 2026-06-14 资源 explanation 多状态 Browser fixture 与 severity 样式（已完成）

本轮继续 P1“资源区块管理闭环”，把资源区块 explanation 从“真实运行服只证明 `player-offline` 一种状态渲染”补成“真实 Browser 里可验证 5 类状态的同源渲染与样式差异”。生产 CSS 现在会按 `data-resource-explanation-severity="success|info|error"` 给 explanation 卡片轻量区分边框、背景和标签色；Browser smoke 则复用 `window.strategicMap.resourceMigrationExplanation(...)` 与 `renderResourceMigrationExplanation(...)`，用受控 metadata fixture 渲染 `ready`、`awaiting-target`、`waiting-depletion`、`insufficient-balance`、`player-offline` 五种状态，验证 `state/severity/codes`、摘要、原因、短缺金额和 computed border style。旧 web 资源指挥面继续缺席，HUD 契约仍是 intel-only。

- 新增/更新:
  - `src/main/resources/web/map/css/styles.css`
  - `scripts/smoke-starcore-map-browser.mjs`
  - `scripts/smoke-starcore-paper-integration.ps1`
  - `docs/LATEST_VERIFICATION_SUMMARY.md`
- 功能:
  - `.resource-explanation[data-resource-explanation-severity="success"]` 使用 success 视觉提示，覆盖 `ready`。
  - `.resource-explanation[data-resource-explanation-severity="info"]` 使用 info 视觉提示，覆盖 `awaiting-target` / `waiting-depletion`。
  - `.resource-explanation[data-resource-explanation-severity="error"]` 使用 error 视觉提示，覆盖 `insufficient-balance` / `player-offline`。
  - Browser smoke 新增多状态 fixture，真实调用生产 renderer，不手写 DOM 结构。
  - PASS marker 新增 `resourceExplanationFixture=ready+awaiting-target+waiting-depletion+insufficient-balance+player-offline:5`。
  - Paper 集成 smoke 外层新增该 marker 的硬断言，避免 Browser 脚本内部软验证漂移。
- 验证:
  - `node --check scripts\smoke-starcore-map-browser.mjs`: PASS
  - PowerShell parse check for `scripts\smoke-starcore-paper-integration.ps1`: PASS
  - `mvn -q -DskipTests package`: PASS
  - `.\scripts\sync-map-preview-shell.ps1`: PASS，`styles.css` hash `469D3C694C5A7B198D9150D0CFD704F00D2CBFE02BD10AAB0998E2B6C77261A4`
  - `.\scripts\sync-map-preview-shell.ps1 -PreviewRoot ..\test-server-paper-1.21.11\plugins\map`: PASS，同 hash
  - `.\scripts\smoke-starcore-paper-integration.ps1 -ProtectorApiSmoke -BrowserSmoke -TimeoutSeconds 420`: PASS
    - Browser marker 包含 `resourceExplanationFixture=ready+awaiting-target+waiting-depletion+insufficient-balance+player-offline:5`
    - Browser marker 继续包含 `resourceExplanation=player-offline:5`
    - Browser marker 继续包含 `eventMobile=390x844:6`
    - Browser marker 继续包含 `commandUiRemoved=true`
  - `.\scripts\check-starcore-performance-budget.ps1 -SmokeSummaryJsonPath target\smoke-harness-20260614-200515\smoke-summary.json`: PASS，summary `target\performance-budget-checks\check-20260614-200651\performance-budget-summary.json`，`10/10` pass，baseline trend `ok`。
  - `.\scripts\check-map-hud-contract.ps1 -RequireMirrorRoots`: PASS，summary `target\map-hud-contract-checks\check-20260614-200651\map-hud-contract-summary.json`。
  - 旧资源指挥 UI token 扫描：无命中。
  - `.\scripts\build-latest-verification-summary.ps1 -SmokeSummaryJsonPath target\smoke-harness-20260614-200515\smoke-summary.json -PerformanceBudgetSummaryPath target\performance-budget-checks\check-20260614-200651\performance-budget-summary.json -HudContractSummaryPath target\map-hud-contract-checks\check-20260614-200651\map-hud-contract-summary.json`: PASS。

最新证据：

- Smoke summary: `target\smoke-harness-20260614-200515\smoke-summary.json`
- Performance budget: `target\performance-budget-checks\check-20260614-200651\performance-budget-summary.json`
- HUD contract: `target\map-hud-contract-checks\check-20260614-200651\map-hud-contract-summary.json`
- Browser DOM: `D:\qwq\项目\mapadd\test-server-paper-1.21.11\zcode-starcore-map-browser-smoke-20260614-200515.dom.html`
- Browser screenshot: `D:\qwq\项目\mapadd\test-server-paper-1.21.11\zcode-starcore-map-browser-smoke-20260614-200515.png`
- Browser mobile screenshot: `D:\qwq\项目\mapadd\test-server-paper-1.21.11\zcode-starcore-map-browser-smoke-20260614-200515.mobile.png`
- Paper log: `D:\qwq\项目\mapadd\test-server-paper-1.21.11\zcode-starcore-deep-integration-smoke-20260614-200515.out.log`
- Latest jar SHA256: `B3D89DFEBCE675911A0EC6B9BF8420403C00AFC9C584A54764187D0F42BE8D6B`

下一步：

1. 资源 explanation 多状态运行服真实数据造景已在 20:45 段完成；后续继续把 explanation 和官员权限、事件/日志筛选联动起来。
2. 完整事件日志财政/战争/官员多事件族移动端基线已在 21:07 段完成；外交、策略、领地、国家事件族已在 21:36 段并入 7 类事件族 smoke。
3. 用真实运行服 spark profile 替换 sample profile 后，跑一站式 zip 发版验证。

### 2026-06-14 完整事件日志移动端窄屏截图基线（已完成）

本轮继续 P2“官员 / 事件 / 审计日志”的完整事件日志验收，把上一刀已经完成的搜索、actor/reason chip、跨事件跳转和 amount/balance facts 推进到真实移动端窄屏基线。Browser smoke 现在会用 390x844 mobile viewport 重新打开国家详情完整事件日志，按 `reason=browser-context-chip` 加载同一条审计事件，验证搜索框、当前上下文 chip、跨事件跳转按钮、可点击上下文 chip 和只读 facts 都在侧栏内可见、无横向溢出、无控件重叠，并把截图证据落盘。截图前会按目标控件的最近滚动父级做几何对齐，避免只验证 DOM 但截图停在侧栏其它区域。

- 新增/更新:
  - `src/main/resources/web/map/css/styles.css`
  - `scripts/smoke-starcore-map-browser.mjs`
  - `scripts/smoke-starcore-paper-integration.ps1`
  - `scripts/build-latest-verification-summary.ps1`
  - `scripts/render-starcore-smoke-summary.ps1`
  - `scripts/starcore-performance-budgets.json`
  - `scripts/build-starcore-release-evidence-pack.ps1`
  - `scripts/build-starcore-release-channel-assets.ps1`
  - `docs/LATEST_VERIFICATION_SUMMARY.md`
- 功能:
  - 移动端完整事件日志搜索区现在会让输入框独占一行，搜索/清空按钮各占半行，当前上下文 chip 与跨事件跳转按钮在窄屏下稳定并排。
  - `event-ledger-context-chip`、当前上下文、跨事件跳转按钮和只读 facts 都补了 `min-width: 0` 等窄屏约束，避免长 actor/reason/fact 文本把侧栏撑出 viewport。
  - Browser smoke 新增 `--mobile-screenshot`，会切到 `390x844`、`deviceScaleFactor=2`、`mobile=true`，并验证无横向溢出、地图仍保留至少 64px 上下文、交互控件高度不低于 30px。
  - Browser smoke PASS marker 新增 `eventMobile=390x844:6`，证明移动端基线 6 个关键点全部通过。
  - Paper 集成 smoke 外层会创建 `*.mobile.png`，断言文件存在，并把 `browserMobileScreenshot` / `browserSmoke.mobileScreenshotFile` 写进 smoke summary。
  - 性能预算的 `browser_snapshot` artifact 现在包含 Browser DOM、桌面截图和移动端截图三项；release evidence pack 与 release channel assets 会复制移动端截图并写入 manifest / upload guide。
- 验证:
  - `node --check scripts/smoke-starcore-map-browser.mjs`: PASS
  - PowerShell parse check for smoke/evidence/channel/summary scripts: PASS
  - `.\scripts\smoke-starcore-paper-integration.ps1 -ProtectorApiSmoke -BrowserSmoke -TimeoutSeconds 420`: PASS
    - Browser marker 包含 `eventMobile=390x844:6`
    - Browser marker 继续包含 `eventJump=reason-browser-context-chip:1`
    - Browser marker 继续包含 `eventFacts=amount+balance:2`
    - Browser marker 继续包含 `commandUiRemoved=true`
  - 人工打开移动端截图确认：搜索、清空、原因上下文、跨事件跳转按钮、事件卡片、actor/reason/policy/target chip 与 `amount/balance` facts 都在移动视口内可见。
  - `.\scripts\check-starcore-performance-budget.ps1 -SmokeSummaryJsonPath target\smoke-harness-20260614-193403\smoke-summary.json`: PASS，summary `target\performance-budget-checks\check-20260614-193628\performance-budget-summary.json`，`10/10` pass，baseline trend `ok`，Browser mobile screenshot `443349` bytes。
  - `.\scripts\check-map-hud-contract.ps1 -RequireMirrorRoots`: PASS，summary `target\map-hud-contract-checks\check-20260614-193600\map-hud-contract-summary.json`。
  - 旧资源指挥 UI token 扫描：无命中。
  - `.\scripts\build-latest-verification-summary.ps1 -SmokeSummaryJsonPath target\smoke-harness-20260614-193403\smoke-summary.json -PerformanceBudgetSummaryPath target\performance-budget-checks\check-20260614-193628\performance-budget-summary.json -HudContractSummaryPath target\map-hud-contract-checks\check-20260614-193600\map-hud-contract-summary.json`: PASS。
  - `.\scripts\build-starcore-release-evidence-pack.ps1 ... -SkipZip`: PASS，输出 `target\release-evidence-20260614-193657`，manifest 包含 `browserMobileScreenshot`。
  - `.\scripts\build-starcore-release-channel-assets.ps1 ... -SkipZip`: PASS，输出 `target\release-channel-assets-20260614-193657`，manifest 与 `UPLOAD_GUIDE.md` 包含 Browser 移动端截图。

最新证据：

- Smoke summary: `target\smoke-harness-20260614-193403\smoke-summary.json`
- Performance budget: `target\performance-budget-checks\check-20260614-193628\performance-budget-summary.json`
- HUD contract: `target\map-hud-contract-checks\check-20260614-193600\map-hud-contract-summary.json`
- Browser DOM: `D:\qwq\项目\mapadd\test-server-paper-1.21.11\zcode-starcore-map-browser-smoke-20260614-193403.dom.html`
- Browser screenshot: `D:\qwq\项目\mapadd\test-server-paper-1.21.11\zcode-starcore-map-browser-smoke-20260614-193403.png`
- Browser mobile screenshot: `D:\qwq\项目\mapadd\test-server-paper-1.21.11\zcode-starcore-map-browser-smoke-20260614-193403.mobile.png`
- Paper log: `D:\qwq\项目\mapadd\test-server-paper-1.21.11\zcode-starcore-deep-integration-smoke-20260614-193403.out.log`
- Release evidence dir: `target\release-evidence-20260614-193657`
- Release channel assets dir: `target\release-channel-assets-20260614-193657`
- Latest jar SHA256: `5D93DA8E01564950A0D587E865D858547B7E503C48E4DDB249636DC4AD9A3EEB`

下一步：

1. 资源 explanation 多状态运行服真实数据造景已在 20:45 段完成；后续继续把 explanation 和官员权限、事件/日志筛选联动起来。
2. 完整事件日志财政/战争/官员多事件族移动端基线已在 21:07 段完成；外交、策略、领地、国家事件族已在 21:36 段并入 7 类事件族 smoke。
3. 用真实运行服 spark profile 替换 sample profile 后，跑一站式 zip 发版验证。

### 2026-06-14 完整事件日志跨事件跳转定位（已完成）

本轮继续 P2“官员 / 事件 / 审计日志”的完整事件日志追责体验，把上一刀的 actor/reason chip 从“当前分类内收窄”补成“可一键横跳完整事件集”。当前上下文筛选 chip 旁会在已按分类或资源收窄时显示紧凑跨事件按钮；点击后保留同一个 `query/actor/reason` 值，但清掉事件分类和资源区块约束，直接回到 `/api/map/events?filter=all&...`，用于从同一操作人或原因横向查政策、科技、战争、外交和财政记录。筛选、时间范围、分页也已继续透传 `actor/reason`，避免用户在追责时点范围或翻页丢失上下文。

- 新增/更新:
  - `src/main/resources/web/map/js/map.js`
  - `src/main/resources/web/map/css/styles.css`
  - `scripts/smoke-starcore-map-browser.mjs`
  - `scripts/smoke-starcore-paper-integration.ps1`
  - `src/test/java/dev/starcore/starcore/module/map/MapEventLogEndpointTest.java`
  - `docs/LATEST_VERIFICATION_SUMMARY.md`
- 功能:
  - 当前上下文筛选区新增 `event-ledger-context-jump` 跨事件按钮，使用同一 `query/actor/reason` 值重新查询 `filter=all`。
  - `load-event-ledger`、分类、时间范围、当前资源、分页按钮现在都会保留 `actor/reason` 状态，不再只保留通用 `query`。
  - Browser smoke 会先点击 reason chip，再点击跨事件跳转，确认 `filter=all` 且 `reason=browser-context-chip`；PASS marker 新增 `eventJump=reason-browser-context-chip:1`。
  - CSV/JSON 导出检查现在站在跨事件状态上，确认导出 URL 保留 `filter=all` 与当前 `reason`。
  - `/api/map/events` 契约测试补入政策、科技、外交和财政样例，锁住同一 `reason=strategy-review` 可跨 `policy.set` / `technology.unlocked` 定位，并确认财政 facts `amount/balance` 仍进入 `details`。
  - 外层 Paper smoke 新增 `eventJump` marker 硬断言，避免 Browser 脚本只做软验证。
- 验证:
  - `node --check src/main/resources/web/map/js/map.js`: PASS
  - `node --check scripts/smoke-starcore-map-browser.mjs`: PASS
  - `mvn -q "-Dtest=MapEventLogEndpointTest,MapWebServerTest,MapModuleViewerSnapshotContractTest" test`: PASS
  - `mvn -q test`: PASS (`331` tests)
  - `mvn -q -DskipTests package`: PASS
  - `.\scripts\sync-map-preview-shell.ps1`: PASS
  - `.\scripts\sync-map-preview-shell.ps1 -PreviewRoot ..\test-server-paper-1.21.11\plugins\map`: PASS
  - `.\scripts\smoke-starcore-paper-integration.ps1 -ProtectorApiSmoke -BrowserSmoke -TimeoutSeconds 420`: PASS
    - Browser marker 包含 `eventJump=reason-browser-context-chip:1`
    - Browser marker 继续包含 `eventContext=actor-SmokeAuditor:1`
    - Browser marker 继续包含 `eventReason=reason-browser-context-chip:1`
    - Browser marker 继续包含 `eventFacts=amount+balance:2`
    - Browser marker 继续包含 `eventLedgerExport=csv+json`
    - Browser marker 继续包含 `commandUiRemoved=true`
  - `.\scripts\check-starcore-performance-budget.ps1 -SmokeSummaryJsonPath target\smoke-harness-20260614-185837\smoke-summary.json`: PASS (`10/10`, baseline trend `ok`)
  - `.\scripts\check-map-hud-contract.ps1 -RequireMirrorRoots`: PASS
  - 旧资源指挥 UI token 扫描：无命中
  - `.\scripts\build-latest-verification-summary.ps1 -SmokeSummaryJsonPath target\smoke-harness-20260614-185837\smoke-summary.json -PerformanceBudgetSummaryPath target\performance-budget-checks\check-20260614-185949\performance-budget-summary.json -HudContractSummaryPath target\map-hud-contract-checks\check-20260614-185949\map-hud-contract-summary.json`: PASS

最新证据：

- Smoke summary: `target\smoke-harness-20260614-185837\smoke-summary.json`
- Performance budget: `target\performance-budget-checks\check-20260614-185949\performance-budget-summary.json`
- HUD contract: `target\map-hud-contract-checks\check-20260614-185949\map-hud-contract-summary.json`
- Browser DOM: `D:\qwq\项目\mapadd\test-server-paper-1.21.11\zcode-starcore-map-browser-smoke-20260614-185837.dom.html`
- Browser screenshot: `D:\qwq\项目\mapadd\test-server-paper-1.21.11\zcode-starcore-map-browser-smoke-20260614-185837.png`
- Paper log: `D:\qwq\项目\mapadd\test-server-paper-1.21.11\zcode-starcore-deep-integration-smoke-20260614-185837.out.log`
- Latest jar SHA256: `A16B8AE11B92C29C94109E4AB7C089CAA98C097017AC22C7D4DF0949524DC2D9`
- Map asset hashes after sync:
  - `map.js`: `A9FA67AF8493711D8DD033987E2373B9803CBA28D78FF57882C361F8E12BCF7C`
  - `styles.css`: `C2A781E76D42A4057F3FD961BB9CFD15A371B78298E6499D432E4EA7E04A3609`

下一步：

1. 资源 explanation 多状态运行服真实数据造景已在 20:45 段完成；后续继续把 explanation 和官员权限、事件/日志筛选联动起来。
2. release evidence/channel assets 已在 19:36 段完成，manifest 与 upload guide 已包含 `browserMobileScreenshot`。
3. 完整事件日志移动端窄屏截图基线已在 19:34 段完成，Browser marker 锁住 `eventMobile=390x844:6`。

### 2026-06-14 Web 国家详情当前访问者官员授权状态（已完成）

本轮把上一刀的“配置授权矩阵”继续升级为“当前访问者是否能操作，以及为什么”。后端在按 viewer 过滤地图 snapshot 时，会为国家领土 metadata 追加 9 个 `viewerCanOfficer*`、`viewerOfficerStatus*`、`viewerOfficerMatchedRole*` 字段，并写出 `viewerOfficerNationScope`；前端国家详情授权矩阵不只展示配置官职，还会给每个动作标出当前访问者是否可操作、是领袖直通、命中哪个官职，或是匿名/外部国家/缺任命/缺配置。整条链路仍保持配置驱动，不把默认角色写死到 Web。

- 新增/更新:
  - `src/main/java/dev/starcore/starcore/module/map/MapModule.java`
  - `src/test/java/dev/starcore/starcore/module/map/MapModuleViewerSnapshotContractTest.java`
  - `src/main/resources/web/map/js/map.js`
  - `src/main/resources/web/map/css/styles.css`
  - `scripts/smoke-starcore-map-browser.mjs`
  - `scripts/smoke-starcore-paper-integration.ps1`
  - `README.md`
  - `docs/LATEST_VERIFICATION_SUMMARY.md`
  - `docs/CONTINUATION_PLAN_2026-06-05.md`
  - `docs/MODULE_PLAN.md`
  - `docs/COMPLETION_STATUS_2026-06-04.md`
  - `docs/RELEASE_CHECKLIST_2026-06-05.md`
  - `docs/OPERATIONS_RUNBOOK_2026-06-05.md`
- 功能:
  - `MapModule` 的 viewer 关系化领土 metadata 现在会区分 `anonymous`、`external-nation`、`founder`、`officer`、`needs-appointment`、`no-role-config`、`unknown-nation`。
  - 国家详情授权项新增 `data-officer-authorization-can`、`data-officer-authorization-status`、可选 `data-officer-authorization-matched-role`，并按授权/阻塞状态上色。
  - Browser smoke 不只检查 `officerAuth=marshal+treasurer+diplomat+steward:9`，还要求当前 smoke founder 看到 `officerAccess=founder:9`。
  - 为压住 DOM 体积预算，空 matched role 不再写入 DOM；最终 browser DOM 体积回到趋势阈值内。
- 验证:
  - `node --check src/main/resources/web/map/js/map.js`: PASS。
  - `node --check scripts/smoke-starcore-map-browser.mjs`: PASS。
  - `mvn -q "-Dtest=MapModuleViewerSnapshotContractTest#territoryRelationAddsViewerOfficerAuthorizationStateForNationDetailPanel" test`: PASS。
  - `mvn -q "-Dtest=MapModuleViewerSnapshotContractTest,ConfigurationServiceResourceFeedbackConfigTest" test`: PASS。
  - `mvn -q test`: PASS，`329` tests，`0` failures，`0` errors。
  - `mvn -q -DskipTests package`: PASS。
  - `.\scripts\sync-map-preview-shell.ps1`: PASS。
  - `.\scripts\sync-map-preview-shell.ps1 -PreviewRoot ..\test-server-paper-1.21.11\plugins\map`: PASS。
  - `.\scripts\smoke-starcore-paper-integration.ps1 -ProtectorApiSmoke -BrowserSmoke -TimeoutSeconds 420`: PASS。
  - Smoke summary: `target\smoke-harness-20260614-171602\smoke-summary.json`。
  - Browser marker: `STARCORE_BROWSER_SMOKE_PASS viewer=SmokePlayer nation=Smokemqdklknq balance=9999091.76 nationDetail=true officerAuth=marshal+treasurer+diplomat+steward:9 officerAccess=founder:9 nationAction=true recentLog=5 recentLogFilter=resource:3 eventQuery=resource:9 eventLedger=resource:3 eventLedgerExport=csv+json resourceAction=player-offline resourceExplanation=player-offline:5 resourceCost=100000.00 commandUiRemoved=true browser=Edg/149.0.4022.62`。
  - `.\scripts\check-starcore-performance-budget.ps1 -SmokeSummaryJsonPath target\smoke-harness-20260614-171602\smoke-summary.json`: PASS，summary `target\performance-budget-checks\check-20260614-171731\performance-budget-summary.json`，`10/10` pass，baseline trend `ok`，Browser DOM `75141` bytes。
  - `.\scripts\check-map-hud-contract.ps1 -RequireMirrorRoots`: PASS，summary `target\map-hud-contract-checks\check-20260614-171746\map-hud-contract-summary.json`，source/static-preview/runtime-map `matches=0`。
  - `.\scripts\build-latest-verification-summary.ps1`: PASS。
  - 最新 jar size: `17744991`。
  - 最新 jar SHA256: `3A91B5C066B44BF635B3FC9E1AD9433DA6C214520A95CA177AD80E00AE568C1A`。

下一刀建议:

1. 给完整事件日志补 actor/reason 搜索和战争/财政/官员上下文定位。
2. 补 Web 国家详情的移动端截图基线，重点看授权矩阵、财政、事件日志在窄屏下的密度和换行。
3. 继续把资源区块 explanation 多状态 fixture 做齐：ready、缺资金、等枯竭、等选点、需上线。

### 2026-06-14 Web 国家详情官员授权矩阵（已完成）

本轮继续“方方面面灵活智能、不硬编码”的官员权限线，把已经配置化的资源迁移、国库支出、外交直设、战争、政策和科技授权角色暴露到 Web 国家详情。领土 polygon metadata 现在会从 `ConfigurationService` 输出 9 个 `officerRole*` 字段；网页国家详情新增“官员授权”矩阵，直接显示当前配置允许哪些角色处理迁移/国库/外交/战争/政策/科技。Browser smoke 已在真实运行服里断言 DOM 存在，并在 PASS marker 输出 `officerAuth=marshal+treasurer+diplomat+steward:9`。

- 新增/更新:
  - `src/main/java/dev/starcore/starcore/module/map/NationMapMetadataSupport.java`
  - `src/main/java/dev/starcore/starcore/module/map/MapModule.java`
  - `src/test/java/dev/starcore/starcore/module/map/MapModuleViewerSnapshotContractTest.java`
  - `src/main/resources/web/map/js/map.js`
  - `src/main/resources/web/map/css/styles.css`
  - `scripts/smoke-starcore-map-browser.mjs`
  - `scripts/smoke-starcore-paper-integration.ps1`
  - `README.md`
  - `docs/LATEST_VERIFICATION_SUMMARY.md`
  - `docs/CONTINUATION_PLAN_2026-06-05.md`
  - `docs/MODULE_PLAN.md`
  - `docs/COMPLETION_STATUS_2026-06-04.md`
  - `docs/RELEASE_CHECKLIST_2026-06-05.md`
  - `docs/OPERATIONS_RUNBOOK_2026-06-05.md`
- 功能:
  - `NationMapMetadataSupport.appendOfficerAuthorizationMetadata(...)` 从配置读取并输出 `officerRoleResourceMigration`、`officerRoleTreasuryWithdraw`、`officerRoleDiplomacySet`、`officerRoleWarDeclare`、`officerRoleWarEnd`、`officerRolePolicySet`、`officerRolePolicyClear`、`officerRoleTechnologyUnlock`、`officerRoleTechnologyRevoke`。
  - 国家详情面新增 `data-officer-authorization="true"` 授权区，每项 action 都有 `data-officer-authorization-key` 和 `data-officer-authorization-roles`，便于 Browser smoke 和后续 UI 自动化直接检查。
  - 前端不写死默认角色；它只展示后端 metadata 给出的角色列表。Java contract test 用非默认组合验证 metadata 真来自配置。
  - Browser smoke 的 DOM 快照改为在国家详情刷新后写出，外层 Paper smoke 也会检查授权区 DOM 和 `officerAuth=marshal+treasurer+diplomat+steward:9` marker。
- 验证:
  - `node --check src/main/resources/web/map/js/map.js`: PASS。
  - `node --check scripts/smoke-starcore-map-browser.mjs`: PASS。
  - `mvn -q "-Dtest=MapModuleViewerSnapshotContractTest#territoryPolygonIncludesOperationalMetadataForNationDetailPanel" test`: PASS。
  - `mvn -q "-Dtest=MapModuleViewerSnapshotContractTest,ConfigurationServiceResourceFeedbackConfigTest" test`: PASS。
  - `.\scripts\sync-map-preview-shell.ps1`: PASS。
  - `.\scripts\sync-map-preview-shell.ps1 -PreviewRoot ..\test-server-paper-1.21.11\plugins\map`: PASS。
  - `mvn -q test`: PASS，`328` tests，`0` failures，`0` errors。
  - `mvn -q -DskipTests package`: PASS。
  - `.\scripts\smoke-starcore-paper-integration.ps1 -ProtectorApiSmoke -BrowserSmoke -TimeoutSeconds 420`: PASS。
  - Smoke summary: `target\smoke-harness-20260614-164915\smoke-summary.json`。
  - Browser marker: `STARCORE_BROWSER_SMOKE_PASS viewer=SmokePlayer nation=Smokemqdjmzyd balance=9999122.26 nationDetail=true officerAuth=marshal+treasurer+diplomat+steward:9 nationAction=true recentLog=5 recentLogFilter=resource:3 eventQuery=resource:9 eventLedger=resource:3 eventLedgerExport=csv+json resourceAction=player-offline resourceExplanation=player-offline:5 resourceCost=100000.00 commandUiRemoved=true browser=Edg/149.0.4022.62`。
  - `.\scripts\check-starcore-performance-budget.ps1 -SmokeSummaryJsonPath target\smoke-harness-20260614-164915\smoke-summary.json`: PASS，summary `target\performance-budget-checks\check-20260614-165044\performance-budget-summary.json`，`10/10` pass。
  - `.\scripts\check-map-hud-contract.ps1 -RequireMirrorRoots`: PASS，summary `target\map-hud-contract-checks\check-20260614-165044\map-hud-contract-summary.json`，source/static-preview/runtime-map `matches=0`。
  - 最新 jar size: `17742295`。
  - 最新 jar SHA256: `3EC866E9F70EC51009D633D937EA3CC5DB521647054C26FDC9754D8753744EE8`。

下一刀建议:

1. 给完整事件日志补 actor/reason 搜索和战争/财政/官员上下文定位。
2. 补 Web 国家详情的移动端截图基线，重点看授权矩阵、财政、事件日志在窄屏下的密度和换行。
3. 继续把资源区块 explanation 多状态 fixture 做齐：ready、缺资金、等枯竭、等选点、需上线。

### 2026-06-14 完整事件日志 CSV/JSON 导出 / Browser 按钮烟测（已完成）

本轮继续 P2“官员 / 事件 / 最近日志”，把刚接入国家详情的完整事件日志补上可下载导出。`/api/map/events` 现在支持 `format=csv/json`，复用同一套国家可见性、分类、类型、资源区块和时间窗口过滤；网页完整事件日志新增“导出CSV / 导出JSON”按钮，导出 URL 读取当前深日志状态，不另写业务判断。Browser smoke 已真实点击导出按钮并拦截 `window.open`，确认 URL 带上 `format`、`filter=resource`、`range=24h` 和当前资源区块参数；PASS marker 新增 `eventLedgerExport=csv+json`。

- 新增/更新:
  - `src/main/java/dev/starcore/starcore/module/map/MapEventLogEndpoint.java`
  - `src/main/java/dev/starcore/starcore/module/map/MapModule.java`
  - `src/test/java/dev/starcore/starcore/module/map/MapEventLogEndpointTest.java`
  - `src/test/java/dev/starcore/starcore/module/map/MapModuleViewerSnapshotContractTest.java`
  - `src/main/resources/web/map/js/map.js`
  - `scripts/smoke-starcore-map-browser.mjs`
  - `docs/LATEST_VERIFICATION_SUMMARY.md`
  - `docs/CONTINUATION_PLAN_2026-06-05.md`
  - `docs/MODULE_PLAN.md`
  - `docs/RELEASE_CHECKLIST_2026-06-05.md`
  - `docs/OPERATIONS_RUNBOOK_2026-06-05.md`
  - `docs/COMPLETION_STATUS_2026-06-04.md`
- 功能:
  - `/api/map/events?format=csv` 返回 `text/csv; charset=utf-8` 和下载文件名，默认沿用 `map.web.finance-export.csv-bom-enabled` 写 UTF-8 BOM。
  - `/api/map/events?format=json` 返回完整筛选结果 JSON，不受分页截断影响，并带 `filter/type/resourceId/range/from/to/total/summary/events[]`。
  - CSV 字段包含国家、筛选条件、资源区块、时间窗口、事件 ID、发生时间、事件类型、分类、消息、事件资源 ID 和原始 context。
  - 下载文件名使用 `starcore-events-<国家名>-<国家ID前8位>-<filter>-<type>-<resource>-<range>.<ext>`，保留中文国家名，并清理不适合文件名的符号。
  - `MapModule.buildEventLogResponse(...)` 现在会透传 `contentType` 与 `filename`，避免路由层把导出退化成普通 JSON 响应。
  - 网页完整事件日志新增 `event-ledger-export` CSV/JSON 两个按钮，导出 URL 复用当前 filter/range/from/to/resourceId。
  - Browser smoke 会拦截导出按钮的 `window.open`，确认导出 URL 指向 `/api/map/events` 且携带当前筛选状态；PASS marker 新增 `eventLedgerExport=csv+json`。
- 验证:
  - `node --check src\main\resources\web\map\js\map.js`: PASS。
  - `node --check scripts\smoke-starcore-map-browser.mjs`: PASS。
  - `mvn -q "-Dtest=MapEventLogEndpointTest,MapModuleViewerSnapshotContractTest" test`: PASS。
  - `mvn -q "-Dtest=MapEventLogEndpointTest,MapWebServerTest,MapModuleViewerSnapshotContractTest" test`: PASS。
  - `.\scripts\sync-map-preview-shell.ps1`: PASS。
  - `.\scripts\sync-map-preview-shell.ps1 -PreviewRoot ..\test-server-paper-1.21.11\plugins\map`: PASS。
  - `.\scripts\check-map-hud-contract.ps1 -RequireMirrorRoots`: PASS，summary `target\map-hud-contract-checks\check-20260614-141014\map-hud-contract-summary.json`，source/static-preview/runtime-map `matches=0`。
  - `mvn -q test`: PASS，`307` tests，`0` failures，`0` errors。
  - `mvn -q -DskipTests package`: PASS。
  - `.\scripts\smoke-starcore-paper-integration.ps1 -ProtectorApiSmoke -BrowserSmoke -TimeoutSeconds 420`: PASS。
  - Smoke summary: `target\smoke-harness-20260614-141248\smoke-summary.json`。
  - Browser marker: `STARCORE_BROWSER_SMOKE_PASS viewer=SmokePlayer nation=Smokemqde1pqh balance=9799090.59 nationDetail=true nationAction=true recentLog=5 recentLogFilter=resource:3 eventQuery=resource:9 eventLedger=resource:3 eventLedgerExport=csv+json resourceAction=player-offline resourceExplanation=player-offline:5 resourceCost=100000.00 commandUiRemoved=true browser=Edg/149.0.4022.62`。
  - Browser DOM: `D:\qwq\项目\mapadd\test-server-paper-1.21.11\zcode-starcore-map-browser-smoke-20260614-141248.dom.html`。
  - Browser screenshot: `D:\qwq\项目\mapadd\test-server-paper-1.21.11\zcode-starcore-map-browser-smoke-20260614-141248.png`。
  - `.\scripts\check-starcore-performance-budget.ps1 -SmokeSummaryJsonPath target\smoke-harness-20260614-141248\smoke-summary.json`: PASS，summary `target\performance-budget-checks\check-20260614-141413\performance-budget-summary.json`，`10/10` pass，baseline trend `ok`。
  - 最新 jar size: `17734031`。
  - 最新 jar SHA256: `53FB34D2F8CC94174597A28390F399946A286F3143DBF664F2BF04857482DBB4`。
  - 说明：ProtectorAPI update check warning 仍是外部更新检查告警，不是 STARCORE 验证失败。

下一刀建议:

1. 继续把官员权限从“任免状态”推进到命令权限/运营动作授权，例如 marshal 可处理部分资源/战争操作但不能改核心国策。
2. 给完整事件日志继续补战争、财政、官员上下文筛选和搜索，让管理层能按“谁 / 哪场战争 / 哪笔财政原因”追责。
3. 用真实运行服执行 Spark profiling，导入真实报告替换 `sample-verification`，再跑一站式 zip 发版验证。

### 2026-06-14 国家事件深日志 Web UI / Browser DOM 烟测（已完成）

本轮继续 P2“官员 / 事件 / 最近日志”，把上一刀已经跑通的 `/api/map/events` 真正接进网页国家详情。国家详情现在不只显示 metadata 里的最近 5 条摘要，还能打开“完整事件日志”，按分类、时间窗口、当前资源区块和分页读取后端事件流；Browser smoke 已真实点击深日志入口、资源分类、当前资源区块筛选、24h 时间范围和分页按钮。PASS marker 新增 `eventLedger=resource:3`，证明深日志 UI 的 DOM、筛选状态和后端查询结果已经在真实运行服里闭环。

- 新增/更新:
  - `src/main/resources/web/map/js/map.js`
  - `src/main/resources/web/map/css/styles.css`
  - `scripts/smoke-starcore-map-browser.mjs`
  - `docs/LATEST_VERIFICATION_SUMMARY.md`
  - `docs/CONTINUATION_PLAN_2026-06-05.md`
  - `docs/MODULE_PLAN.md`
  - `docs/RELEASE_CHECKLIST_2026-06-05.md`
  - `docs/OPERATIONS_RUNBOOK_2026-06-05.md`
  - `docs/COMPLETION_STATUS_2026-06-04.md`
- 功能:
  - 国家详情最近日志块新增完整事件日志入口 `data-action="load-event-ledger"`，深日志区域使用 `#nation-event-ledger`。
  - 深日志支持分类 chips、`all/1h/24h/7d/custom` 时间窗口、自定义起止时间、当前选中资源区块筛选和上一页/下一页分页。
  - `#nation-event-ledger` 写入稳定 DOM dataset：`data-event-ledger-filter`、`data-event-ledger-range`、`data-event-ledger-resource-id`、`data-event-ledger-page`、`data-event-ledger-pages`、`data-event-ledger-count`、`data-event-ledger-total`。
  - 前端状态通过 `eventLedgerApiUrl(...)` 统一拼接 `/api/map/events` 参数，不在 JS 里硬编码业务事件来源。
  - Browser smoke 继续保留 raw authenticated fetch 到 `/api/map/events`，同时新增真实 DOM 流程：点击深日志、点击资源分类、点击当前资源区块、点击 24h、校验渲染条目分类/资源 ID，并在多页时验证 next/prev。
  - 当前 HUD 契约继续保持 intel-only：无 `#resource-command-panel`、无 `open-resource-command`、无 stale `resourceCommand*`，Browser marker 继续输出 `commandUiRemoved=true`。
- 验证:
  - `node --check src\main\resources\web\map\js\map.js`: PASS。
  - `node --check scripts\smoke-starcore-map-browser.mjs`: PASS。
  - `.\scripts\sync-map-preview-shell.ps1`: PASS。
  - `.\scripts\sync-map-preview-shell.ps1 -PreviewRoot ..\test-server-paper-1.21.11\plugins\map`: PASS。
  - source/static-preview/runtime-map 的 `map.js` 与 `styles.css` 已同步一致。
  - `.\scripts\check-map-hud-contract.ps1 -RequireMirrorRoots`: PASS，summary `target\map-hud-contract-checks\check-20260614-134600\map-hud-contract-summary.json`，source/static-preview/runtime-map `matches=0`。
  - `mvn -q "-Dtest=MapEventLogEndpointTest,MapWebServerTest,MapModuleViewerSnapshotContractTest" test`: PASS。
  - `mvn -q test`: PASS，`305` tests，`0` failures，`0` errors。
  - `mvn -q -DskipTests package`: PASS。
  - `.\scripts\smoke-starcore-paper-integration.ps1 -ProtectorApiSmoke -BrowserSmoke -TimeoutSeconds 420`: PASS。
  - Smoke summary: `target\smoke-harness-20260614-134844\smoke-summary.json`。
  - Browser marker: `STARCORE_BROWSER_SMOKE_PASS viewer=SmokePlayer nation=Smokemqdd6q67 balance=9799054.56 nationDetail=true nationAction=true recentLog=5 recentLogFilter=resource:3 eventQuery=resource:9 eventLedger=resource:3 resourceAction=player-offline resourceExplanation=player-offline:5 resourceCost=100000.00 commandUiRemoved=true browser=Edg/149.0.4022.62`。
  - Browser DOM: `D:\qwq\项目\mapadd\test-server-paper-1.21.11\zcode-starcore-map-browser-smoke-20260614-134844.dom.html`。
  - Browser screenshot: `D:\qwq\项目\mapadd\test-server-paper-1.21.11\zcode-starcore-map-browser-smoke-20260614-134844.png`。
  - `.\scripts\check-starcore-performance-budget.ps1 -SmokeSummaryJsonPath target\smoke-harness-20260614-134844\smoke-summary.json`: PASS，summary `target\performance-budget-checks\check-20260614-135005\performance-budget-summary.json`，`10/10` pass，baseline trend `ok`。
  - 最新 jar size: `17730783`。
  - 最新 jar SHA256: `979A1F22437A8003DB20DA087330E803B6962CCF61A487FFED9A2659D3F0B1C8`。
  - 说明：ProtectorAPI update check warning 仍是外部更新检查告警，不是 STARCORE 验证失败。

下一刀建议:

1. 继续把官员权限从“任免状态”推进到命令权限/运营动作授权，例如 marshal 可处理部分资源/战争操作但不能改核心国策。
2. 上面的完整事件日志 CSV/JSON 导出已完成；下一步改为补战争、财政、官员上下文筛选和搜索。
3. 用真实运行服执行 Spark profiling，导入真实报告替换 `sample-verification`，再跑一站式 zip 发版验证。

### 2026-06-14 国家事件日志后端查询接口 / Browser 真实查询（已完成）

本轮继续 P2“官员 / 事件 / 最近日志”，把网页国家详情最近日志从“metadata 里最多 5 条摘要可筛选”继续推进到后端可分页查询的国家事件接口。新增 `/api/map/events`，前端和后续管理面可以按国家、分类、事件类型、资源区块和时间窗口读取完整事件流，不再只能依赖 Browser 当前能看到的 5 条摘要。Browser smoke 已带认证真实请求该接口，PASS marker 新增 `eventQuery=resource:9`，证明资源分类深查拿到了 5 条摘要之外的完整事件结果。

- 新增/更新:
  - `src/main/java/dev/starcore/starcore/module/map/MapEventLogEndpoint.java`
  - `src/test/java/dev/starcore/starcore/module/map/MapEventLogEndpointTest.java`
  - `src/main/java/dev/starcore/starcore/module/map/MapModule.java`
  - `src/main/java/dev/starcore/starcore/module/map/MapWebServer.java`
  - `src/test/java/dev/starcore/starcore/module/map/MapWebServerTest.java`
  - `src/main/resources/web/map/js/map.js`
  - `scripts/smoke-starcore-map-browser.mjs`
  - `src/main/java/dev/starcore/starcore/module/nation/resource/NativeNationResourceDistrictService.java`
  - `docs/LATEST_VERIFICATION_SUMMARY.md`
  - `docs/CONTINUATION_PLAN_2026-06-05.md`
  - `docs/MODULE_PLAN.md`
  - `docs/RELEASE_CHECKLIST_2026-06-05.md`
  - `docs/OPERATIONS_RUNBOOK_2026-06-05.md`
  - `docs/COMPLETION_STATUS_2026-06-04.md`
- 功能:
  - `/api/map/events` 支持 `nation/nationId`、`filter`、`type`、`resourceId/resource/districtId`、`range/from/to`、`page/size`。
  - API 返回 `summary` 和分页 `events[]`，事件项包含 `id/type/category/message/occurredAt/resourceId/context/details`。
  - `filter=resource` 优先按最近日志分类桶匹配，避免被财政流水归一化规则误归到 `resource-income`。
  - Browser smoke 在真实运行服里用登录态请求 `/api/map/events`，并把查询结果写入 `eventQuery=<filter>:<count>`。
  - `NativeNationResourceDistrictService.start()` 现在会 best-effort 预热信标材质 `BlockData`，使用配置里的 `nationResourceBeaconMaterial()`，避免 Paper 冷启动阶段第一次 `Block#setType` 初始化卡住 smoke；该预热不硬编码材质，失败只在 debug 下记录，不改变业务结果。
- 验证:
  - `mvn -q "-Dtest=NativeNationResourceDistrictServiceFlowTest" test`: PASS。
  - `mvn -q "-Dtest=MapEventLogEndpointTest,MapWebServerTest,MapModuleViewerSnapshotContractTest" test`: PASS。
  - `mvn -q test`: PASS，`305` tests，`0` failures，`0` errors。
  - `mvn -q -DskipTests package`: PASS。
  - `node --check src/main/resources/web/map/js/map.js`: PASS。
  - `node --check scripts/smoke-starcore-map-browser.mjs`: PASS。
  - `.\scripts\sync-map-preview-shell.ps1`: PASS。
  - `.\scripts\check-map-hud-contract.ps1 -RequireMirrorRoots`: PASS，summary `target\map-hud-contract-checks\check-20260614-111540\map-hud-contract-summary.json`，contract `intel-only-resource-district`，source/static-preview/runtime-map `matches=0`。
  - `.\scripts\smoke-starcore-paper-integration.ps1 -ProtectorApiSmoke -BrowserSmoke -TimeoutSeconds 420`: PASS。
  - Smoke summary: `target\smoke-harness-20260614-112217\smoke-summary.json`。
  - Browser marker: `STARCORE_BROWSER_SMOKE_PASS viewer=SmokePlayer nation=Smokemqd7yc64 balance=9799088.61 nationDetail=true nationAction=true recentLog=5 recentLogFilter=resource:3 eventQuery=resource:9 resourceAction=player-offline resourceExplanation=player-offline:5 resourceCost=100000.00 commandUiRemoved=true browser=Edg/149.0.4022.62`。
  - `.\scripts\check-starcore-performance-budget.ps1 -SmokeSummaryJsonPath target\smoke-harness-20260614-112217\smoke-summary.json`: PASS，summary `target\performance-budget-checks\check-20260614-112323\performance-budget-summary.json`，`10/10` pass，baseline trend `ok`。
  - 最新 jar size: `17728979`。
  - 最新 jar SHA256: `EF32B6E7933508B22B0CAADDA298C8E9432C7479191E9FD4E0281ED7516A82D7`。
  - 说明：ProtectorAPI update check warning 仍是外部更新检查告警，不是 STARCORE 验证失败。

下一刀建议:

1. 继续把官员权限从“任免状态”推进到命令权限/运营动作授权，例如 marshal 可处理部分资源/战争操作但不能改核心国策。
2. 上面的完整事件日志 Web UI 已完成；下一步改为给深日志补 CSV/JSON 导出，或继续加战争、财政、官员上下文筛选。
3. 用真实运行服执行 Spark profiling，导入真实报告替换 `sample-verification`，再跑一站式 zip 发版验证。

### 2026-06-11 国家最近操作日志分类筛选/Browser 烟测（已完成）

本轮继续 P2“官员 / 事件 / 最近日志”，把网页国家详情里的“最近操作日志”从固定 5 条列表推进到可筛选的运营日志面。后端 `NationMapMetadataSupport` 现在会为每条 `recentEvent*` metadata 写入稳定分类，并用 `selectRecentEvents(...)` 在最多 5 条摘要中优先保留最新事件，同时补齐不同运营分类的代表事件；前端只按 metadata 分类渲染筛选按钮，不在 JS 里重写业务事件来源。Browser smoke 已真实点击最近日志分类筛选，确认筛选后条目全部匹配同一分类，且筛选数量小于总数。

- 新增/更新:
  - `src/main/java/dev/starcore/starcore/module/map/NationMapMetadataSupport.java`
  - `src/main/java/dev/starcore/starcore/module/map/MapModule.java`
  - `src/test/java/dev/starcore/starcore/module/map/NationMapMetadataSupportTest.java`
  - `src/main/resources/web/map/js/map.js`
  - `src/main/resources/web/map/css/styles.css`
  - `scripts/smoke-starcore-map-browser.mjs`
  - `docs/LATEST_VERIFICATION_SUMMARY.md`
  - `docs/CONTINUATION_PLAN_2026-06-05.md`
  - `docs/MODULE_PLAN.md`
  - `docs/RELEASE_CHECKLIST_2026-06-05.md`
  - `docs/OPERATIONS_RUNBOOK_2026-06-05.md`
  - `docs/COMPLETION_STATUS_2026-06-04.md`
- 功能:
  - `recentEventCategory(type)` 把事件类型归入 `resource`、`finance`、`officer`、`diplomacy`、`war`、`strategy`、`territory`、`nation`、`other`。
  - `appendRecentEvents(...)` 为 Browser metadata 输出 `recentEvent{n}Category`，保留原有 type/message/at/resourceId。
  - `selectRecentEvents(...)` 输入 newest-first 事件列表，最多输出 5 条，优先最新事件并补齐不同分类代表，避免资源迁移等高频事件刷掉官员/战争/财政等运营信号。
  - 网页国家详情新增最近日志筛选 chips，筛选按钮由当前真实事件分类动态生成；筛选后 `#nation-recent-log` 带 `data-recent-log-filter/count/total`，事件条目带 `data-event-category`。
  - 最近日志条目现在显示分类徽标和“事件分类”字段，保留资源事件的查看/定位区块按钮。
  - Browser smoke 新增最近日志筛选断言：至少有 `all + 一个分类`，点击分类后所有条目类别一致，且筛选后条数小于总数；PASS marker 新增 `recentLogFilter=<category>:<count>`。
- 验证:
  - `node --check src/main/resources/web/map/js/map.js`: PASS。
  - `node --check scripts/smoke-starcore-map-browser.mjs`: PASS。
  - `mvn -q "-Dtest=NationMapMetadataSupportTest,MapModuleViewerSnapshotContractTest" test`: PASS。
  - `mvn -q test`: PASS，`301` tests，`0` failures，`0` errors。
  - `.\scripts\sync-map-preview-shell.ps1`: PASS。
  - `.\scripts\check-map-hud-contract.ps1 -RequireMirrorRoots`: PASS，summary `target\map-hud-contract-checks\check-20260611-074213\map-hud-contract-summary.json`，contract `intel-only-resource-district`，source/static-preview/runtime-map `matches=0`。
  - `mvn -q -DskipTests package`: PASS。
  - `.\scripts\smoke-starcore-paper-integration.ps1 -ProtectorApiSmoke -BrowserSmoke -TimeoutSeconds 420`: PASS。
  - Smoke summary: `target\smoke-harness-20260611-073915\smoke-summary.json`。
  - Browser marker: `STARCORE_BROWSER_SMOKE_PASS viewer=SmokePlayer nation=Smokemq8ppkbs balance=9798888.59 nationDetail=true nationAction=true recentLog=5 recentLogFilter=resource:3 resourceAction=player-offline resourceExplanation=player-offline:5 resourceCost=100000.00 commandUiRemoved=true browser=Edg/149.0.4022.52`。
  - `.\scripts\check-starcore-performance-budget.ps1 -SmokeSummaryJsonPath target\smoke-harness-20260611-073915\smoke-summary.json`: PASS，summary `target\performance-budget-checks\check-20260611-074212\performance-budget-summary.json`，`10/10` pass，baseline trend `ok`。
  - 最新 jar size: `17712958`。
  - 最新 jar SHA256: `DB48B411BB1DAF6730A38DC5E0F94735328A8EC28628EAE182DB51A84C1E0092`。
  - 说明：ProtectorAPI update check warning 仍是外部更新检查告警，不是 STARCORE 验证失败。

下一刀建议:

1. 继续把官员权限从“任免状态”推进到命令权限/运营动作授权，例如 marshal 可处理部分资源/战争操作但不能改核心国策。
2. 给最近日志增加“按资源区块 / 财政 / 战争 / 官员”的后端查询接口，避免 Browser 只看 metadata 摘要。
3. 用真实运行服执行 Spark profiling，导入真实报告替换 `sample-verification`，再跑一站式 zip 发版验证。

### 2026-06-10 资源区块 explanation Browser DOM 渲染/烟测（已完成）

本轮继续 P1“资源区块管理闭环”，把上一刀已经产出的 `migrationExplanation*` metadata 真正渲染进网页国家详情和资源区块运营面。Browser DOM 现在能直接看到“迁移判断 / 原因 / 下一步 / 资金缺口”等结构化说明；国家详情里的当前资源区块、运营焦点、操作建议、分组列表和优先队列都消费同一批 metadata，不再恢复旧的 web 端 `#resource-command-panel`。当前 HUD 契约仍是 intel-only 资源区块情报面，迁移操作继续留在游戏内 GUI/命令，Browser smoke 同时锁住 explanation DOM 存在和旧 command UI 缺席。

- 新增/更新:
  - `src/main/resources/web/map/js/map.js`
  - `src/main/resources/web/map/css/styles.css`
  - `scripts/smoke-starcore-map-browser.mjs`
  - `docs/LATEST_VERIFICATION_SUMMARY.md`
  - `docs/CONTINUATION_PLAN_2026-06-05.md`
  - `docs/MODULE_PLAN.md`
  - `docs/RELEASE_CHECKLIST_2026-06-05.md`
  - `docs/OPERATIONS_RUNBOOK_2026-06-05.md`
  - `docs/COMPLETION_STATUS_2026-06-04.md`
- 功能:
  - `resourceMigrationExplanation(meta, summary)` 统一读取 `migrationExplanationState`、`migrationExplanationSeverity`、`migrationExplanationSummary`、`migrationExplanationReasonCodes`、`migrationExplanationPrimaryReason`，并在旧字段存在时兼容回退。
  - `renderResourceMigrationExplanation(...)` 输出带 `data-resource-explanation-state` / `data-resource-explanation-severity` / `data-resource-explanation-codes` 的小型 DOM 块。
  - 国家详情当前资源区块、运营焦点、操作建议、操作分组和优先队列都已显示 explanation；compact 场景只收束展示，不另写业务判断。
  - CSS 新增 `.resource-explanation*` 样式，保持卡片内部紧凑、低干扰，不引入旧 `resource-command` 命名。
  - Browser smoke 新增 `#nation-detail-panel [data-resource-explanation-state]` 断言，验证 explanation state/text 与选中资源区块 metadata 匹配，并在筛选/快捷动作点击后继续存在。
  - Browser smoke 继续要求旧 command UI 缺席：无 `#resource-command-panel`、无 `open-resource-command`、无 stale `resourceCommand*`。
- 验证:
  - `node --check src/main/resources/web/map/js/map.js`: PASS。
  - `node --check scripts/smoke-starcore-map-browser.mjs`: PASS。
  - `.\scripts\sync-map-preview-shell.ps1`: PASS。
  - `.\scripts\check-map-hud-contract.ps1 -RequireMirrorRoots`: PASS，summary `target\map-hud-contract-checks\check-20260610-175404\map-hud-contract-summary.json`，contract `intel-only-resource-district`，source/static-preview/runtime-map `matches=0`。
  - `mvn -q "-Dtest=MapResourceDistrictEndpointTest,ResourceDistrictMapMetadataSupportTest,MapModuleViewerSnapshotContractTest,NationResourceDistrictCommandSupportTest" test`: PASS。
  - `mvn -q test`: PASS，`299` tests，`0` failures，`0` errors。
  - `mvn -q -DskipTests package`: PASS。
  - `.\scripts\smoke-starcore-paper-integration.ps1 -ProtectorApiSmoke -BrowserSmoke -TimeoutSeconds 420`: PASS。
  - Smoke summary: `target\smoke-harness-20260610-175052\smoke-summary.json`。
  - Browser marker: `STARCORE_BROWSER_SMOKE_PASS viewer=SmokePlayer nation=Smokemq7w444x balance=9799053.76 nationDetail=true nationAction=true recentLog=5 resourceAction=player-offline resourceExplanation=player-offline:5 resourceCost=100000.00 commandUiRemoved=true browser=Edg/149.0.4022.52`。
  - Paper marker 继续包含 `claimExplanation=externalProtection:1`、`webClaimFeedback`、`onlineWebClaim=...+cancelTyped:1`、`nationOperationFeedback`、`strategyFeedback`、`governanceFeedback` 和资源区块迁移 feedback 七类计数。
  - `.\scripts\check-starcore-performance-budget.ps1 -SmokeSummaryJsonPath target\smoke-harness-20260610-175052\smoke-summary.json`: PASS，summary `target\performance-budget-checks\check-20260610-175422\performance-budget-summary.json`，`10/10` pass，baseline trend `ok`。
  - 最新 jar size: `17710267`。
  - 最新 jar SHA256: `5602E4BEA9561A9E65EAA88BB90CE66625967261B6784994D9699D42112425D2`。
  - 说明：ProtectorAPI update check warning 仍是外部更新检查告警，不是 STARCORE 验证失败。

下一刀建议:

1. 扩展官员权限与事件/日志筛选，让官员任免进入真实运营闭环。
2. 用控制数据补资源区块 explanation 的 ready、缺资金、等待枯竭、等待选点等多状态 Browser 断言和样式快照。
3. 用真实运行服执行 Spark profiling，导入真实报告替换 `sample-verification`，再跑一站式 zip 发版验证。

### 2026-06-10 资源区块迁移阻塞 typed explanation（已完成）

本轮继续 P1“资源区块管理闭环”，把资源区块迁移状态从平铺文案推进到结构化 explanation。`NationResourceDistrictCommandSupport` 现在从同一个 `CommandState` 生成 `ClaimSelectionExplanation`，覆盖 `ready`、`awaiting-target`、`waiting-depletion`、`insufficient-balance`、`leader-only`、`player-offline`、`not-own-nation`、`district-not-found` 等状态；网页 POST、地图 marker metadata 和 `/starcore resource migrate` 失败输出都能读取同源原因，避免 web/GUI/命令各写一套阻塞判断。

- 新增/更新:
  - `src/main/java/dev/starcore/starcore/module/nation/resource/NationResourceDistrictMigrationResult.java`
  - `src/main/java/dev/starcore/starcore/module/nation/resource/NationResourceDistrictCommandSupport.java`
  - `src/main/java/dev/starcore/starcore/module/map/MapResourceDistrictEndpoint.java`
  - `src/main/java/dev/starcore/starcore/module/map/ResourceDistrictMapMetadataSupport.java`
  - `src/main/java/dev/starcore/starcore/command/ResourceCommandHandler.java`
  - `src/test/java/dev/starcore/starcore/module/nation/resource/NationResourceDistrictCommandSupportTest.java`
  - `src/test/java/dev/starcore/starcore/module/map/MapResourceDistrictEndpointTest.java`
  - `src/test/java/dev/starcore/starcore/module/map/ResourceDistrictMapMetadataSupportTest.java`
  - `docs/LATEST_VERIFICATION_SUMMARY.md`
  - `docs/CONTINUATION_PLAN_2026-06-05.md`
  - `docs/MODULE_PLAN.md`
  - `docs/RELEASE_CHECKLIST_2026-06-05.md`
  - `docs/OPERATIONS_RUNBOOK_2026-06-05.md`
  - `docs/COMPLETION_STATUS_2026-06-04.md`
- 功能:
  - `NationResourceDistrictMigrationResult` 新增 `ClaimSelectionExplanation`，保留旧 4 参数构造和 `success/failure` 工厂兼容现有调用。
  - `NationResourceDistrictCommandSupport.explanation(...)` 复用已有 `CommandState` / `CommandPresentation`，输出 `state/severity/summary/reasons/details`。
  - explanation details 带出余额、迁移费用、资金缺口、是否本国资源区块、是否领袖、是否在线、是否可支付、是否可开始迁移。
  - `/api/map/resource-districts/migrate` 响应新增 `explanation` 对象，同时继续保留 `migrationActionState`、`migrationNextStep`、`migrationRestrictionDetail` 等旧字段。
  - 地图资源区块 marker metadata 新增 `migrationExplanationState`、`migrationExplanationSeverity`、`migrationExplanationSummary`、`migrationExplanationReasonCodes` 和 primary reason 字段，后续前端 DOM 深化可直接消费。
  - `/starcore resource migrate <world:x:z>` 失败时会追加最多 3 条 explanation reason；余额不足、非领袖、领袖离线、等待枯竭等都来自同一套状态解释。
- 验证:
  - `mvn -q "-Dtest=NationResourceDistrictCommandSupportTest,NationResourceDistrictMenuSupportTest,MapResourceDistrictEndpointTest,ResourceDistrictMapMetadataSupportTest,MapModuleViewerSnapshotContractTest,ResourceCommandHandlerTest" test`: PASS。
  - `mvn -q test`: PASS。
  - `mvn -q -DskipTests package`: PASS。
  - `.\scripts\check-starcore-performance-budget.ps1 -SmokeSummaryJsonPath target\smoke-harness-20260610-165537\smoke-summary.json`: PASS，summary `target\performance-budget-checks\check-20260610-173115\performance-budget-summary.json`，`10/10` pass，baseline trend `ok`。
  - 最新 jar size: `17709462`。
  - 最新 jar SHA256: `E84CA6940C035DF04B5B3AACBBB4FAC8DFFDDA65A6E8EFA78A68A61FC3741667`。
  - 说明：本刀未重跑 Paper+Browser 深烟测；运行服深烟测证据仍沿用 `target\smoke-harness-20260610-165537\smoke-summary.json`，本刀新增契约由 JUnit 与性能预算覆盖。

下一刀建议:

1. 上面的 Browser DOM 渲染 slice 已完成，后续只继续补多状态样式/断言，不再恢复旧网页指挥面。
2. 扩展官员权限与事件/日志筛选，让官员任免进入真实运营闭环。
3. 用真实运行服执行 Spark profiling，导入真实报告替换 `sample-verification`，再跑一站式 zip 发版验证。

### 2026-06-10 网页圈地回服确认状态 typed explanation（已完成）

本轮继续 P1“圈地体验与价格解释”，把网页圈地 pending 的回服确认链从“异常文本”推进到结构化状态结果。`/starcore map confirm <编号>` 现在会收到 `WebClaimConfirmationResult`，其中包含 `status` 和 `ClaimSelectionExplanation`；找不到 pending、非本人确认、pending 过期、确认时国家/余额/容量/冲突状态变化、提交失败都会返回 typed explanation。新增 `/starcore map cancel <编号>`，玩家可主动取消自己的网页 pending，取消也走 `pending-cancelled` explanation 和独立 `web-cancelled` feedback profile。

- 新增/更新:
  - `src/main/java/dev/starcore/starcore/module/map/model/WebClaimConfirmationResult.java`
  - `src/main/java/dev/starcore/starcore/module/map/MapClaimEndpoint.java`
  - `src/main/java/dev/starcore/starcore/module/map/MapService.java`
  - `src/main/java/dev/starcore/starcore/module/map/MapModule.java`
  - `src/main/java/dev/starcore/starcore/command/StarCoreCommand.java`
  - `src/main/java/dev/starcore/starcore/command/StarCoreCommandAliases.java`
  - `src/main/resources/messages_zh_cn.yml`
  - `src/main/resources/config.yml`
  - `src/test/java/dev/starcore/starcore/module/map/MapClaimEndpointTest.java`
  - `src/test/java/dev/starcore/starcore/command/StarCoreCommandAliasesTest.java`
  - `scripts/smoke-starcore-paper-integration.ps1`
  - `docs/LATEST_VERIFICATION_SUMMARY.md`
  - `docs/CONTINUATION_PLAN_2026-06-05.md`
  - `docs/MODULE_PLAN.md`
  - `docs/RELEASE_CHECKLIST_2026-06-05.md`
  - `docs/OPERATIONS_RUNBOOK_2026-06-05.md`
  - `docs/COMPLETION_STATUS_2026-06-04.md`
- 功能:
  - `WebClaimConfirmationResult` 新增 `status`、`explanation`、`confirmed()`、`cancelled()`，保留旧 6 参数构造兼容现有测试/调用。
  - `MapClaimEndpoint.confirm(...)` 不再把常规失败都抛成异常，而是返回 `pending-id-invalid`、`pending-not-found`、`pending-not-owner`、`pending-expired`、`confirm-failed` 等 typed explanation。
  - `MapModule.confirmWebClaimSync(...)` 在确认时重新 preview；如果余额、容量、重叠、外部保护或国家归属变化导致不可提交，会直接复用 preview 的 `ClaimSelectionExplanation`。
  - 新增 `MapService.cancelWebClaim(...)`、`MapClaimEndpoint.cancel(...)` 和 `/starcore map cancel <编号>`，成功取消返回 `pending-cancelled`。
  - 在线网页 pending 通知现在同时给出确认和取消命令：`/starcore map confirm <编号>`、`/starcore map cancel <编号>` 以及中文 `/starcore 地图 确认/取消 <编号>`。
  - `nation.claims.feedback.web-cancelled` 新增默认 profile，取消 pending 有独立 sound/actionbar/BossBar，不复用失败反馈。
  - 命令端失败会打印 explanation reasons，玩家看到的不再只是“确认失败: 某文本”。
  - Paper smoke 的 `onlineWebClaim` marker 新增真实 `/starcore map cancel <编号>` 探针与 `cancelTyped:1`。
- 验证:
  - `mvn -q "-Dtest=MapClaimEndpointTest,MapPerformanceBatchSamplesTest,StarCoreCommandAliasesTest,ConfigurationServiceResourceFeedbackConfigTest,ConfigDefaultsCoverageTest" test`: PASS。
  - `mvn -q test`: PASS。
  - `mvn -q -DskipTests package`: PASS。
  - `.\scripts\smoke-starcore-paper-integration.ps1 -ProtectorApiSmoke -BrowserSmoke -TimeoutSeconds 420`: PASS on 2026-06-10 16:58 +08:00。
  - Smoke summary: `D:\qwq\项目\mapadd\starcore\target\smoke-harness-20260610-165537\smoke-summary.json`
  - Smoke marker: `STARCORE_SMOKE_PASS ... webClaimFeedback=confirmSound:1+confirmActionbar:1+confirmTitle:0+confirmBossbar:1+confirmBossbarHide:1 onlineWebClaim=SmokeEnvoy:34cdae58+pendingSound:1+pendingActionbar:1+pendingTitle:0+pendingBossbar:1+pendingBossbarHide:0+cancelSound:1+cancelActionbar:1+cancelTitle:0+cancelBossbar:1+cancelBossbarHide:0+cancelTyped:1 ...`。
  - `.\scripts\check-map-hud-contract.ps1 -RequireMirrorRoots`: PASS，summary `target\map-hud-contract-checks\check-20260610-165922\map-hud-contract-summary.json`。
  - `.\scripts\check-starcore-performance-budget.ps1 -SmokeSummaryJsonPath target\smoke-harness-20260610-165537\smoke-summary.json`: PASS，summary `target\performance-budget-checks\check-20260610-170235\performance-budget-summary.json`，`10/10` pass，baseline trend `ok`。
  - 最新 jar SHA256: `4BD34B3455F724AC2D8771BAAC54EAC04CB18CA0BC0874A68599F25C79D91F12`。
  - 说明：本轮曾捕获到一次本机 Surefire 预算抖动导致的临时 `map_render_cases` error；按完整 `mvn -q test` 形态重跑后最终 performance budget 为 `ok`，没有留下 error summary 作为最新证据。

下一刀建议:

1. 把同一解释模型继续扩到资源区块阻塞原因，让 web/GUI/命令都能解释“为什么不能迁移 / 为什么缺资金 / 为什么需领袖上线”。
2. 扩展官员权限与事件/日志筛选，让官员任免进入真实运营闭环。
3. 用真实运行服执行 Spark profiling，导入真实报告替换 `sample-verification`，再跑一站式 zip 发版验证。

### 2026-06-10 外交/官员/国库高价值操作接入共享 feedback profile（已完成）

本轮继续把 `foundation.feedback` 扩到治理类高价值命令。外交关系变化、官员任免、国库大额收支现在都只在命令层触发语义事件，具体 sound/particle/actionbar/title/BossBar 继续由配置 profile 控制；国库可见反馈额外受 `nation.treasury.feedback.minimum-amount` 控制，避免小额流水刷屏。Paper smoke 已真实执行 `/starcore diplomacy`、`/starcore officer`、`/starcore treasury` 命令，并用同一玩家代理记录治理类游戏内反馈计数。

- 新增/更新:
  - `src/main/java/dev/starcore/starcore/core/config/ConfigurationService.java`
  - `src/main/java/dev/starcore/starcore/command/StarCoreCommand.java`
  - `src/main/resources/config.yml`
  - `src/test/java/dev/starcore/starcore/core/config/ConfigurationServiceResourceFeedbackConfigTest.java`
  - `scripts/smoke-starcore-paper-integration.ps1`
  - `docs/LATEST_VERIFICATION_SUMMARY.md`
  - `docs/CONTINUATION_PLAN_2026-06-05.md`
  - `docs/MODULE_PLAN.md`
  - `docs/RELEASE_CHECKLIST_2026-06-05.md`
  - `docs/OPERATIONS_RUNBOOK_2026-06-05.md`
  - `docs/COMPLETION_STATUS_2026-06-04.md`
- 功能:
  - 新增独立可配置 feedback profile root：`nation.diplomacy.feedback`、`nation.officers.feedback`、`nation.treasury.feedback`。
  - `StarCoreCommand` 新增治理类反馈 helper，命令分支只发 `relation-updated`、`relation-proposed`、`diplomacy-failed`、`officer-appointed`、`officer-removed`、`treasury-deposited`、`treasury-withdrawn`、`treasury-rewarded`、`treasury-income-settled`、`treasury-tax-settled` 等语义 key。
  - 国库 feedback 只对玩家 sender 触发，并由 `nation.treasury.feedback.minimum-amount` 控制最低可见金额；控制台/admin 文本和账本写入保持不变。
  - 默认配置已分别给外交、官员、国库补中文注释和默认音效/UI profile，后续服主可单独调弱或关闭某类治理反馈。
  - Paper smoke 新增 `governanceFeedback=governanceSound:<n>+governanceActionbar:<n>+governanceTitle:<n>+governanceBossbar:<n>+...+diplomacy:<n>+officer:<n>+treasury:<n>` marker。
- 验证:
  - `mvn -q "-Dtest=ConfigurationServiceResourceFeedbackConfigTest,ConfigDefaultsCoverageTest" test`: PASS。
  - `mvn -q test`: PASS。
  - `mvn -q -DskipTests package`: PASS。
  - `.\scripts\smoke-starcore-paper-integration.ps1 -ProtectorApiSmoke -BrowserSmoke -TimeoutSeconds 420`: PASS on 2026-06-10 16:33 +08:00。
  - Smoke summary: `D:\qwq\项目\mapadd\starcore\target\smoke-harness-20260610-163327\smoke-summary.json`
  - Smoke marker: `STARCORE_SMOKE_PASS ... claimExplanation=externalProtection:1 webClaimFeedback=confirmSound:1+confirmActionbar:1+confirmTitle:0+confirmBossbar:1+confirmBossbarHide:1 nationOperationFeedback=operationSound:4+operationActionbar:4+operationTitle:1+operationBossbar:4+operationBossbarHide:0 strategyFeedback=strategySound:3+strategyActionbar:3+strategyTitle:3+strategyBossbar:3+strategyBossbarHide:0 governanceFeedback=governanceSound:6+governanceActionbar:6+governanceTitle:3+governanceBossbar:6+governanceBossbarHide:0+diplomacy:1+officer:2+treasury:3 ...`。
  - `.\scripts\check-map-hud-contract.ps1 -RequireMirrorRoots`: PASS，summary `target\map-hud-contract-checks\check-20260610-163453\map-hud-contract-summary.json`。
  - `.\scripts\check-starcore-performance-budget.ps1 -SmokeSummaryJsonPath target\smoke-harness-20260610-163327\smoke-summary.json`: PASS，summary `target\performance-budget-checks\check-20260610-163453\performance-budget-summary.json`，`10/10` pass，baseline trend `ok`。
  - 最新 jar SHA256: `41A93BE5FF8C2CC9EF2202A97F17B6EDAE84A9817673C5EE8A3B5E1D78DF8EBE`。
  - 说明：治理 marker 里的 `governanceBossbarHide=0` 是因为 smoke marker 在短 BossBar 自动隐藏前写出；该类命令级 marker 只要求展示计数大于 0，不把 hide count 当作失败条件。

下一刀建议:

1. 继续把 web confirm 失败、pending 过期和取消状态补成 typed explanation，和 preview/request 的结构保持一致。
2. 扩展官员权限与事件/日志筛选，让官员任免不止是 assignment state，而能进入真实运营闭环。
3. 用真实运行服执行 Spark profiling，导入真实报告替换 `sample-verification`，再跑一站式 zip 发版验证。

### 2026-06-10 圈地结构化解释面统一（已完成）

本轮继续 P1“圈地体验与价格解释”，把命令、圈地工具和网页地图共用的预览结果从单条 message 推进到结构化解释面。`ClaimSelectionPreview` 现在保留原有 `message` / `pricing` / `canSubmit` 兼容字段，同时携带 `ClaimSelectionExplanation` 与 `ClaimSelectionReason`；后端根据真实预览、价格拆解、容量、余额、重叠和外部保护冲突生成原因列表，前端优先展示这些原因，再展示高价区块明细。这样后续扩展失败原因、价格驱动或按钮禁用说明时，不需要在命令和 JS 里各写一套判断。

- 新增/更新:
  - `src/main/java/dev/starcore/starcore/module/nation/model/ClaimSelectionExplanation.java`
  - `src/main/java/dev/starcore/starcore/module/nation/model/ClaimSelectionReason.java`
  - `src/main/java/dev/starcore/starcore/module/nation/model/ClaimSelectionPreview.java`
  - `src/main/java/dev/starcore/starcore/module/nation/NationModule.java`
  - `src/main/java/dev/starcore/starcore/module/map/MapClaimEndpoint.java`
  - `src/main/java/dev/starcore/starcore/module/nation/claimtool/ClaimToolListener.java`
  - `src/main/java/dev/starcore/starcore/command/StarCoreCommand.java`
  - `src/main/resources/messages_zh_cn.yml`
  - `src/main/resources/web/map/js/map.js`
  - `src/main/resources/web/map/css/styles.css`
  - `scripts/smoke-starcore-paper-integration.ps1`
  - `docs/LATEST_VERIFICATION_SUMMARY.md`
  - `docs/CONTINUATION_PLAN_2026-06-05.md`
  - `docs/MODULE_PLAN.md`
  - `docs/RELEASE_CHECKLIST_2026-06-05.md`
  - `docs/OPERATIONS_RUNBOOK_2026-06-05.md`
  - `docs/COMPLETION_STATUS_2026-06-04.md`
- 功能:
  - `NationModule.previewClaimSelection(...)` 现在生成 `no-nation`、`leader-only`、`overlap`、`external-protection-conflict`、`claim-limit`、`insufficient-balance` 等结构化 reason。
  - 预览成功和失败都会从真实 pricing/capacity 数据补充容量、价格摘要、最高价区块，以及可选的距离/生物群系价格驱动。
  - `/api/map/claim/preview` 和 `/api/map/claim/request` 返回 `explanation.state`、`explanation.severity`、`explanation.summary`、`explanation.reasons[]`、`details`，同时继续保留旧 `message`。
  - endpoint 级失败也有 typed explanation：`selection-too-large`、`claim-cooldown`。
  - 网页 `#claim-details` 优先渲染 explanation reasons，再渲染高价区块；`sync-map-preview-shell.ps1` 已同步源码 map 资产到静态预览壳。
  - `/starcore nation claim` 和圈地工具失败时会打印最多 3 条结构化解释原因，命令侧不再只看到一条笼统失败文本。
  - Paper smoke 新增 `claimExplanation=externalProtection:1` marker，锁住外部保护冲突在 web preview/request 中暴露 `explanation.state` 与 reason code。
- 验证:
  - `git diff --check`: PASS。
  - `mvn -q "-Dtest=NationModuleRulesTest,MapClaimEndpointTest,MapModuleViewerSnapshotContractTest,ClaimToolListenerTest" test`: PASS。
  - `mvn -q test`: PASS。
  - `mvn -q -DskipTests package`: PASS。
  - `.\scripts\check-map-hud-contract.ps1 -RequireMirrorRoots`: PASS，summary `target\map-hud-contract-checks\check-20260610-161210\map-hud-contract-summary.json`。
  - `.\scripts\smoke-starcore-paper-integration.ps1 -ProtectorApiSmoke -BrowserSmoke -TimeoutSeconds 420`: PASS on 2026-06-10 16:11 +08:00。
  - Smoke summary: `D:\qwq\项目\mapadd\starcore\target\smoke-harness-20260610-161030\smoke-summary.json`
  - Smoke marker: `STARCORE_SMOKE_PASS ... claimExplanation=externalProtection:1 webClaimFeedback=confirmSound:1+confirmActionbar:1+confirmTitle:0+confirmBossbar:1+confirmBossbarHide:1 onlineWebClaim=SmokeEnvoy:d207d7f2+pendingSound:1+pendingActionbar:1+pendingTitle:0+pendingBossbar:1+pendingBossbarHide:0 nationOperationFeedback=operationSound:4+operationActionbar:4+operationTitle:1+operationBossbar:4+operationBossbarHide:0 strategyFeedback=strategySound:3+strategyActionbar:3+strategyTitle:3+strategyBossbar:3+strategyBossbarHide:0 ...`。
  - `.\scripts\check-starcore-performance-budget.ps1 -SmokeSummaryJsonPath target\smoke-harness-20260610-161030\smoke-summary.json`: PASS，summary `target\performance-budget-checks\check-20260610-161157\performance-budget-summary.json`，`10/10` pass，baseline trend `ok`，history median `5/5` samples。
  - 最新 jar SHA256: `CEC9ED9CD10369F20540A930BE194B02733870AD4F13FA3B903DF38E73657F02`。

下一刀建议:

1. 把外交关系变化、官员任免、财政大额收支等运营高价值事件接入共享 feedback，并补命令级 smoke marker。
2. 继续把 web confirm 失败、pending 过期等回服确认状态补成 typed explanation，和 preview/request 的结构保持一致。
3. 用真实运行服执行 spark profiling，导入真实报告替换 `sample-verification`，再跑完整一站式 zip 发版验证。

### 2026-06-10 策略高价值操作接入共享 feedback profile（已完成）

本轮继续把 `foundation.feedback` 扩到政策、科技和战争这些策略高价值操作。命令层现在只触发 `policy-activated`、`policy-cleared`、`technology-unlocked`、`technology-revoked`、`war-declared`、`war-ended`、`strategy-failed` 等语义事件；具体 sound/particle/actionbar/title/BossBar 都由 `nation.strategy.feedback.*` 配置 profile 控制。Paper smoke 新增命令级策略探针，真实执行 `/starcore policy set civil_industry`、`/starcore technology unlock <国家> logistics` 和 `/starcore war declare <国家> <对手>`，并记录同一玩家代理上的游戏内反馈计数。

- 新增/更新:
  - `src/main/java/dev/starcore/starcore/core/config/ConfigurationService.java`
  - `src/main/java/dev/starcore/starcore/command/StarCoreCommand.java`
  - `src/main/resources/config.yml`
  - `src/test/java/dev/starcore/starcore/core/config/ConfigurationServiceResourceFeedbackConfigTest.java`
  - `scripts/smoke-starcore-paper-integration.ps1`
  - `docs/LATEST_VERIFICATION_SUMMARY.md`
  - `docs/CONTINUATION_PLAN_2026-06-05.md`
  - `docs/MODULE_PLAN.md`
  - `docs/RELEASE_CHECKLIST_2026-06-05.md`
  - `docs/OPERATIONS_RUNBOOK_2026-06-05.md`
  - `docs/COMPLETION_STATUS_2026-06-04.md`
- 功能:
  - `ConfigurationService.nationStrategyFeedbackProfile(...)` 复用通用 `inGameFeedbackProfile("nation.strategy.feedback", eventKey)`。
  - `StarCoreCommand` 新增 `emitStrategyFeedback(...)`，策略命令继续走统一 `BukkitInGameFeedbackService`，业务分支不写 sound/particle/UI 细节。
  - 默认配置新增 `nation.strategy.feedback`，覆盖政策激活/清除、科技解锁/撤销、战争宣告/结束和统一失败提示。
  - Paper smoke 新增 `strategyFeedback=strategySound:<n>+strategyActionbar:<n>+strategyTitle:<n>+strategyBossbar:<n>` marker。
- 验证:
  - `git diff --check`: PASS。
  - `mvn -q "-Dtest=ConfigurationServiceResourceFeedbackConfigTest" test`: PASS。
  - `mvn -q test`: PASS，`290` tests，`0` failures，`0` errors。
  - `mvn -q -DskipTests package`: PASS。
  - `.\scripts\smoke-starcore-paper-integration.ps1 -ProtectorApiSmoke -BrowserSmoke -TimeoutSeconds 420`: PASS on 2026-06-10 15:42 +08:00。
  - Smoke summary: `D:\qwq\项目\mapadd\starcore\target\smoke-harness-20260610-154110\smoke-summary.json`
  - Smoke marker: `STARCORE_SMOKE_PASS ... nationOperationFeedback=operationSound:4+operationActionbar:4+operationTitle:1+operationBossbar:4+operationBossbarHide:0 strategyFeedback=strategySound:3+strategyActionbar:3+strategyTitle:3+strategyBossbar:3+strategyBossbarHide:0 ...`。
  - `.\scripts\check-starcore-performance-budget.ps1 -SmokeSummaryJsonPath target\smoke-harness-20260610-154110\smoke-summary.json`: PASS，summary `target\performance-budget-checks\check-20260610-154344\performance-budget-summary.json`，`10/10` pass，baseline trend `ok`，history median `5/5` samples。
  - `.\scripts\build-latest-verification-summary.ps1 ...`: PASS，`docs\LATEST_VERIFICATION_SUMMARY.md` 已显示最新 jar SHA256 `FB021C50543AD297D4F40A559C73F253591C20803FDFBE7CA12290AE5385BB7D`、策略 feedback marker、国家操作 feedback marker、web claim feedback marker、资源区块七类效果 marker 和 `Performance budget: ok (10/10 pass)`。

下一刀建议:

1. 继续补圈地“为什么贵 / 为什么冲突”的统一解释面，把余额不足、容量限制、外部保护冲突做成命令和 web 都能读懂的结构化解释。
2. 把外交关系变化、官员任免、财政大额收支等运营高价值事件接入共享 feedback，并补命令级 smoke marker。
3. 用真实运行服执行 spark profiling，导入真实报告替换 `sample-verification`，再跑完整一站式 zip 发版验证。

### 2026-06-10 国家生命周期操作接入共享 feedback profile（已完成）

本轮继续把 `foundation.feedback` 扩到低频高价值国家操作。国家创建、城邦创建、入国提案、改名提案和提案签署现在走 `nation.operations.feedback.*` 配置 profile；命令层只触发 `nation-created`、`city-created`、`join-proposed`、`rename-proposed`、`proposal-signed`、`operation-failed` 等语义事件，具体 sound/particle/actionbar/title/BossBar 仍由 `config.yml` 控制。Paper smoke 新增命令级国家操作探针，真实执行 `/starcore nation create`、`/starcore nation city create`、`/starcore nation rename` 和 `/starcore resolution sign`，并记录同一玩家代理上的游戏内反馈计数。

- 新增/更新:
  - `src/main/java/dev/starcore/starcore/core/config/ConfigurationService.java`
  - `src/main/java/dev/starcore/starcore/command/StarCoreCommand.java`
  - `src/main/resources/config.yml`
  - `src/test/java/dev/starcore/starcore/core/config/ConfigurationServiceResourceFeedbackConfigTest.java`
  - `scripts/smoke-starcore-paper-integration.ps1`
  - `docs/LATEST_VERIFICATION_SUMMARY.md`
  - `docs/CONTINUATION_PLAN_2026-06-05.md`
  - `docs/MODULE_PLAN.md`
  - `docs/RELEASE_CHECKLIST_2026-06-05.md`
  - `docs/OPERATIONS_RUNBOOK_2026-06-05.md`
  - `docs/COMPLETION_STATUS_2026-06-04.md`
- 功能:
  - `ConfigurationService.nationOperationFeedbackProfile(...)` 复用通用 `inGameFeedbackProfile("nation.operations.feedback", eventKey)`。
  - `StarCoreCommand` 的 claim feedback helper 改为通用 `emitConfiguredFeedback(...)`，claim 和 nation operation 都走同一套安全 no-op / Bukkit adapter 调用。
  - 默认配置新增 `nation.operations.feedback`，覆盖国家创建、城邦创建、入国提案、改名提案、提案签署和操作失败。
  - Paper smoke 新增 `nationOperationFeedback=operationSound:<n>+operationActionbar:<n>+operationTitle:<n>+operationBossbar:<n>` marker。
- 验证:
  - `git diff --check`: PASS。
  - `mvn -q "-Dtest=ConfigurationServiceResourceFeedbackConfigTest,ConfigDefaultsCoverageTest,StarCoreCommandShortAliasDispatchTest" test`: PASS。
  - `mvn -q test`: PASS，`289` tests，`0` failures，`0` errors。
  - `mvn -q -DskipTests package`: PASS。
  - `.\scripts\smoke-starcore-paper-integration.ps1 -ProtectorApiSmoke -BrowserSmoke -TimeoutSeconds 420`: PASS on 2026-06-10 15:28 +08:00。
  - Smoke summary: `D:\qwq\项目\mapadd\starcore\target\smoke-harness-20260610-152733\smoke-summary.json`
  - Smoke marker: `STARCORE_SMOKE_PASS ... webClaimFeedback=confirmSound:1+confirmActionbar:1+confirmTitle:0+confirmBossbar:1+confirmBossbarHide:1 onlineWebClaim=SmokeEnvoy:a3d327b3+pendingSound:1+pendingActionbar:1+pendingTitle:0+pendingBossbar:1+pendingBossbarHide:0 nationOperationFeedback=operationSound:4+operationActionbar:4+operationTitle:1+operationBossbar:4+operationBossbarHide:0 ...`。
  - `.\scripts\check-starcore-performance-budget.ps1 -SmokeSummaryJsonPath target\smoke-harness-20260610-152733\smoke-summary.json`: PASS，summary `target\performance-budget-checks\check-20260610-152852\performance-budget-summary.json`，`10/10` pass，baseline trend `ok`，history median `5/5` samples。
  - `.\scripts\build-latest-verification-summary.ps1 ...`: PASS，`docs\LATEST_VERIFICATION_SUMMARY.md` 已显示最新 jar SHA256 `D2F12458AF6A36B097795128D06A61C63591F210B210F6ABEF0A6FC1E5B08FF2`、国家操作 feedback marker、web claim feedback marker、资源区块七类效果 marker 和 `Performance budget: ok (10/10 pass)`。

下一刀建议:

1. 把政策激活、科技解锁、战争宣告这些策略高价值事件接入共享 feedback，并补 smoke marker。
2. 继续补圈地“为什么贵 / 为什么冲突”的统一解释面，把余额不足、容量限制、外部保护冲突做成命令和 web 都能读懂的结构化解释。
3. 用真实运行服执行 spark profiling，导入真实报告替换 `sample-verification`，再跑完整一站式 zip 发版验证。

### 2026-06-10 圈地确认与网页 pending 接入共享 feedback profile（已完成）

本轮继续 P1“圈地体验与价格解释 / 游戏内效果”，把刚抽出的 `foundation.feedback` 底座接到圈地低频关键操作。圈地工具、当前区块圈地、网页 pending 通知和 `/starcore map confirm <编号>` 回服确认现在都只发语义事件；具体声音、粒子、actionbar、BossBar 文案和持续时间全部在 `nation.claims.feedback.*` 配置下控制。网页 HTTP endpoint 仍保持纯参数/preview/pending 逻辑，MapModule 只在在线 pending 通知边界触发 `web-pending`，命令层在确认/取消/失败边界触发对应 claim feedback 事件。

- 新增/更新:
  - `src/main/java/dev/starcore/starcore/core/config/ConfigurationService.java`
  - `src/main/java/dev/starcore/starcore/command/StarCoreCommand.java`
  - `src/main/java/dev/starcore/starcore/module/map/MapModule.java`
  - `src/main/resources/config.yml`
  - `src/test/java/dev/starcore/starcore/core/config/ConfigurationServiceResourceFeedbackConfigTest.java`
  - `scripts/smoke-starcore-paper-integration.ps1`
  - `docs/LATEST_VERIFICATION_SUMMARY.md`
  - `docs/CONTINUATION_PLAN_2026-06-05.md`
  - `docs/MODULE_PLAN.md`
  - `docs/RELEASE_CHECKLIST_2026-06-05.md`
  - `docs/OPERATIONS_RUNBOOK_2026-06-05.md`
  - `docs/COMPLETION_STATUS_2026-06-04.md`
- 功能:
  - `ConfigurationService.nationClaimFeedbackProfile(...)` 复用通用 `inGameFeedbackProfile("nation.claims.feedback", eventKey)`。
  - 新增默认事件：`tool-confirmed`、`tool-cancelled`、`tool-failed`、`current-confirmed`、`current-failed`、`web-pending`、`web-confirmed`、`web-failed`。
  - `StarCoreCommand` 在当前区块圈地、圈地工具确认/取消/失败、网页回服确认成功/失败时触发共享 Bukkit feedback。
  - `MapModule.sendPendingClaimNotification(...)` 对在线玩家发出 `web-pending`，但 `MapClaimEndpoint` 仍保持纯 endpoint/pending 逻辑。
  - Smoke harness 的命令玩家代理新增 sound/actionbar/title/BossBar 计数，marker 会显示 `webClaimFeedback=...` 与 `onlineWebClaim=...+pending...`。
- 验证:
  - `git diff --check`: PASS。
  - `mvn -q "-Dtest=ConfigurationServiceResourceFeedbackConfigTest,ConfigDefaultsCoverageTest,StarCoreCommandShortAliasDispatchTest,MapClaimEndpointTest" test`: PASS。
  - `mvn -q test`: PASS，`288` tests，`0` failures，`0` errors。
  - `mvn -q -DskipTests package`: PASS。
  - `.\scripts\smoke-starcore-paper-integration.ps1 -ProtectorApiSmoke -BrowserSmoke -TimeoutSeconds 420`: PASS on 2026-06-10 15:10 +08:00。
  - Smoke summary: `D:\qwq\项目\mapadd\starcore\target\smoke-harness-20260610-150939\smoke-summary.json`
  - Smoke marker: `STARCORE_SMOKE_PASS ... migration=gui+mined:world:240:240+forced:world:240:241+feedbackSound:5+worldSound:7+particles:7+actionbar:3+title:2+bossbar:4+bossbarHide:4 ... webClaim=command:3257ecd1 webClaimFeedback=confirmSound:1+confirmActionbar:1+confirmTitle:0+confirmBossbar:1+confirmBossbarHide:1 onlineWebClaim=SmokeEnvoy:246c937f+pendingSound:1+pendingActionbar:1+pendingTitle:0+pendingBossbar:1+pendingBossbarHide:0 ...`。
  - `.\scripts\check-starcore-performance-budget.ps1 -SmokeSummaryJsonPath target\smoke-harness-20260610-150939\smoke-summary.json`: PASS，summary `target\performance-budget-checks\check-20260610-151049\performance-budget-summary.json`，`10/10` pass，baseline trend `ok`，history median `5/5` samples。
  - `.\scripts\build-latest-verification-summary.ps1 ...`: PASS，`docs\LATEST_VERIFICATION_SUMMARY.md` 已显示最新 jar SHA256 `724B4D082A7A5DCBA8572DB0702E189F0D2796A6D7BAE705FFCEBC49520AD916`、web claim confirm/pending feedback marker、资源区块七类效果 marker 和 `Performance budget: ok (10/10 pass)`。

下一刀建议:

1. 继续把 `foundation.feedback` 接到国家创建/城邦创建/改名/加入决议通过等国家操作，让国家生命周期也有配置化游戏内反馈。
2. 把政策激活、科技解锁、战争宣告这些低频高价值事件接入共享 feedback，并补 smoke marker。
3. 用真实运行服执行 spark profiling，导入真实报告替换 `sample-verification`，再跑完整一站式 zip 发版验证。

### 2026-06-10 通用游戏内 feedback profile 抽取（已完成）

本轮继续 P1“资源区块管理闭环 / 游戏内效果”，把上一刀资源区块私有 feedback adapter 抽成可复用的 `foundation.feedback` 底座。资源区块服务现在只发出语义事件和目标位置：`migration-started`、`migration-target-selected`、`resource-mined` 等；声音、粒子、actionbar、title、BossBar 的解析与 Bukkit 表现层都集中到通用 `InGameFeedbackProfile` / `InGameFeedbackService` / `BukkitInGameFeedbackService`，后续圈地确认、国家操作、政策/科技解锁可以接同一套配置 profile，而不需要各模块硬写一份表现逻辑。

- 新增/更新:
  - `src/main/java/dev/starcore/starcore/foundation/feedback/InGameFeedbackProfile.java`
  - `src/main/java/dev/starcore/starcore/foundation/feedback/InGameFeedbackService.java`
  - `src/main/java/dev/starcore/starcore/foundation/feedback/BukkitInGameFeedbackService.java`
  - `src/main/java/dev/starcore/starcore/core/config/ConfigurationService.java`
  - `src/main/java/dev/starcore/starcore/module/nation/resource/NativeNationResourceDistrictService.java`
  - `src/test/java/dev/starcore/starcore/foundation/feedback/BukkitInGameFeedbackServiceTest.java`
  - `src/test/java/dev/starcore/starcore/core/config/ConfigurationServiceResourceFeedbackConfigTest.java`
  - `src/test/java/dev/starcore/starcore/module/nation/resource/NativeNationResourceDistrictServiceFlowTest.java`
  - `scripts/smoke-starcore-paper-integration.ps1`
  - `docs/LATEST_VERIFICATION_SUMMARY.md`
  - `docs/CONTINUATION_PLAN_2026-06-05.md`
  - `docs/MODULE_PLAN.md`
  - `docs/RELEASE_CHECKLIST_2026-06-05.md`
  - `docs/OPERATIONS_RUNBOOK_2026-06-05.md`
  - `docs/COMPLETION_STATUS_2026-06-04.md`
- 功能:
  - `ConfigurationService.nationResourceFeedbackProfile(...)` 现在委托到通用 `inGameFeedbackProfile(rootPath, eventKey)`，配置路径仍是 `nation.resources.feedback.*`，兼容原有默认值。
  - `NativeNationResourceDistrictService` 移除资源区块专用私有 feedback support，只依赖 `InGameFeedbackService`，默认生产实现为 `BukkitInGameFeedbackService(plugin, configuration::nationResourceFeedbackProfile)`。
  - `BukkitInGameFeedbackService` 统一处理在线玩家声音、World 侧声音、World 侧粒子、actionbar、title/subtitle、BossBar 展示和 scheduler 自动 hide。
  - 普通单测不直接触发 Paper `Sound` / `Particle` registry，避免脱离 Paper runtime 的 registry access 问题；真实声音/粒子继续由 Paper smoke 覆盖。
  - Smoke harness 的 World 侧效果探针改为复用生产服务字段 `feedbackSupport`，不再反射旧的资源区块私有 adapter。
- 验证:
  - `rg -n "BukkitResourceDistrictFeedbackSupport|ResourceDistrictFeedbackSupport|ResourceDistrictFeedbackProfile|ConfigurationService\.ResourceDistrict" src scripts`: PASS，无源码/脚本残留。
  - `git diff --check`: PASS。
  - `mvn -q "-Dtest=BukkitInGameFeedbackServiceTest,ConfigurationServiceResourceFeedbackConfigTest,ConfigDefaultsCoverageTest,NativeNationResourceDistrictServiceFlowTest" test`: PASS。
  - `mvn -q test`: PASS，`287` tests，`0` failures，`0` errors。
  - `mvn -q -DskipTests package`: PASS。
  - `.\scripts\smoke-starcore-paper-integration.ps1 -ProtectorApiSmoke -BrowserSmoke -TimeoutSeconds 420`: PASS on 2026-06-10 14:52 +08:00。
  - Smoke summary: `D:\qwq\项目\mapadd\starcore\target\smoke-harness-20260610-145122\smoke-summary.json`
  - Smoke marker: `STARCORE_SMOKE_PASS ... migration=gui+mined:world:176:176+forced:world:176:177+feedbackSound:5+worldSound:7+particles:7+actionbar:3+title:2+bossbar:4+bossbarHide:4 ... webClaim=command:4479c3cc ... viewer=ok ... mapSummary=5 territory polygon(s), 0 player marker(s), 1 resource district marker(s)`。
  - `.\scripts\check-starcore-performance-budget.ps1 -SmokeSummaryJsonPath target\smoke-harness-20260610-145122\smoke-summary.json`: PASS，summary `target\performance-budget-checks\check-20260610-145255\performance-budget-summary.json`，`10/10` pass，baseline trend `ok`，history median `5/5` samples。
  - `.\scripts\build-latest-verification-summary.ps1 ...`: PASS，`docs\LATEST_VERIFICATION_SUMMARY.md` 已显示最新 jar SHA256 `8141264456D692967A91BD3C903BB0E6A2B2FD92F497EA4C3FE709C41756B34C`、完整七类游戏内效果 marker、`Performance budget: ok (10/10 pass)` 和 sample Spark profile。

下一刀建议:

1. 把 `foundation.feedback` profile 接到圈地确认/取消/失败解释，让 `/starcore map confirm` 和 web pending claim 回服确认也能走配置化游戏内提示。
2. 把国家操作、政策解锁、科技研究完成的高价值事件接入共享 feedback profile，保持每类事件配置化、可禁用、可 smoke。
3. 用真实运行服执行 spark profiling，导入真实报告替换 `sample-verification`，再跑完整一站式 zip 发版验证。

### 2026-06-10 资源区块 BossBar 短时提示配置化与清理 smoke（已完成）

本轮继续 P1“资源区块管理闭环 / 游戏内效果”，把 BossBar 做成同一套资源区块 feedback profile 的可选短时提示。它不改变业务规则：代码仍只触发资源区块语义事件；BossBar 文案、颜色、样式、进度和持续 tick 数全部由 `config.yml` 控制。生产 adapter 在 show 之后用 Bukkit scheduler 自动 hide，smoke 会等 hide 任务跑完再写 `STARCORE_SMOKE_PASS`，防止“显示了但残留”的假完成。

- 新增/更新:
  - `src/main/java/dev/starcore/starcore/core/config/ConfigurationService.java`
  - `src/main/java/dev/starcore/starcore/module/nation/resource/NativeNationResourceDistrictService.java`
  - `src/main/resources/config.yml`
  - `src/test/java/dev/starcore/starcore/core/config/ConfigurationServiceResourceFeedbackConfigTest.java`
  - `scripts/smoke-starcore-paper-integration.ps1`
  - `docs/LATEST_VERIFICATION_SUMMARY.md`
  - `docs/CONTINUATION_PLAN_2026-06-05.md`
  - `docs/RELEASE_CHECKLIST_2026-06-05.md`
  - `docs/OPERATIONS_RUNBOOK_2026-06-05.md`
  - `docs/MODULE_PLAN.md`
- 功能:
  - `ResourceDistrictFeedbackProfile` 新增 `bossBar`、`bossBarColor`、`bossBarOverlay`、`bossBarProgress`、`bossBarDurationTicks`。
  - `BukkitResourceDistrictFeedbackSupport` 使用 Adventure `BossBar`，在线玩家收到短时状态条，离线/World-only 事件不创建 audience UI。
  - hide 逻辑由 Bukkit scheduler 在 `bossbar-duration-ticks` 后执行；插件不可用或调度异常时立即 hide，避免残留。
  - 默认配置在迁移开始、目标锁定、迁移完成、强制迁移和阻塞状态提供克制 BossBar；高频 `resource-mined` / `resource-refreshed` 不默认显示 BossBar，继续保护 TPS。
- Smoke 证据:
  - 玩家代理新增 `showBossBar` / `hideBossBar` 计数。
  - 首轮 smoke 抓到 `shown=4, hidden=0` 的时序问题，随后修正最终 marker 写入，延迟到 hide 任务执行后再断言。
  - 最新 marker 形态为 `migration=...+feedbackSound:5+worldSound:7+particles:7+actionbar:3+title:2+bossbar:4+bossbarHide:4`。
- 验证:
  - `mvn -q "-Dtest=ConfigurationServiceResourceFeedbackConfigTest,ConfigDefaultsCoverageTest,NativeNationResourceDistrictServiceFlowTest" test`: PASS。
  - `mvn -q test`: PASS，`285` tests，`0` failures，`0` errors。
  - `mvn -q -DskipTests package`: PASS。
  - `.\scripts\smoke-starcore-paper-integration.ps1 -ProtectorApiSmoke -BrowserSmoke -TimeoutSeconds 420`: PASS on 2026-06-10 14:30 +08:00。
  - Smoke summary: `D:\qwq\项目\mapadd\starcore\target\smoke-harness-20260610-142932\smoke-summary.json`
  - Smoke marker: `STARCORE_SMOKE_PASS ... migration=gui+mined:world:124:124+forced:world:124:125+feedbackSound:5+worldSound:7+particles:7+actionbar:3+title:2+bossbar:4+bossbarHide:4 ... webClaim=command:89610a50 ... viewer=ok ... mapSummary=5 territory polygon(s), 0 player marker(s), 1 resource district marker(s)`。
  - `.\scripts\check-starcore-performance-budget.ps1 -SmokeSummaryJsonPath target\smoke-harness-20260610-142932\smoke-summary.json`: PASS，summary `target\performance-budget-checks\check-20260610-143043\performance-budget-summary.json`，`10/10` pass，baseline trend `ok`。
  - `.\scripts\build-latest-verification-summary.ps1 ...`: PASS，`docs\LATEST_VERIFICATION_SUMMARY.md` 已显示最新 jar SHA256 `A8080F3C154B4A7C10A3003B7507E57AD89D0653DF72A80DFD8552AE312401F9`、BossBar show/hide smoke 计数和 `Performance budget: ok (10/10 pass)`。

下一刀建议:

1. 用真实运行服执行 spark profiling，导入真实报告替换 `sample-verification`，再跑完整一站式 zip 发版验证。
2. 跑 `.\scripts\verify-starcore-release.ps1 -IncludeSmoke -ProtectorApiSmoke -BrowserSmoke -BuildEvidencePack -BuildReleaseChannelAssets -SparkProfileSummaryPath <real-spark-summary> -RequireRealSparkProfile`，确认真实 zip 包含 HUD contract、full smoke、10/10 performance budget、完整游戏内效果 marker 和真实 spark profile。
3. 继续把这套 feedback profile 抽成可复用于圈地确认、国家操作和政策/科技解锁的通用游戏内反馈组件，避免每个模块各写一套表现逻辑。

### 2026-06-10 资源区块 actionbar/title 可见提示配置化（已完成）

本轮继续 P1“资源区块管理闭环 / 游戏内效果”，把资源区块反馈 profile 从声音/粒子扩展到 actionbar/title/subtitle。业务代码仍只触发 `migration-started`、`migration-target-selected`、`migration-completed` 等语义事件；具体可见文案、title 副标题和 title 动画时长都从 `config.yml` 读取，服主可以按事件覆盖，不需要改 Java。

- 新增/更新:
  - `src/main/java/dev/starcore/starcore/core/config/ConfigurationService.java`
  - `src/main/java/dev/starcore/starcore/module/nation/resource/NativeNationResourceDistrictService.java`
  - `src/main/resources/config.yml`
  - `src/test/java/dev/starcore/starcore/core/config/ConfigurationServiceResourceFeedbackConfigTest.java`
  - `scripts/smoke-starcore-paper-integration.ps1`
  - `docs/LATEST_VERIFICATION_SUMMARY.md`
  - `docs/CONTINUATION_PLAN_2026-06-05.md`
  - `docs/RELEASE_CHECKLIST_2026-06-05.md`
  - `docs/OPERATIONS_RUNBOOK_2026-06-05.md`
  - `docs/MODULE_PLAN.md`
- 功能:
  - `ResourceDistrictFeedbackProfile` 新增 `actionBar`、`title`、`subtitle`、`titleFadeInTicks`、`titleStayTicks`、`titleFadeOutTicks`。
  - `BukkitResourceDistrictFeedbackSupport` 在玩家在线时发送 `Player.sendActionBar(...)` 和 `Player.showTitle(...)`；没有玩家的 World 侧事件仍只跑声音/粒子，避免给不存在的 audience 发 UI。
  - 默认配置为迁移核心交付、目标锁定、迁移完成、强制迁移、阻塞、刷新和采集提供克制的中文 actionbar/title 文案。
  - 高频 `resource-mined` 只用轻 actionbar + 低粒子数量，继续保护 TPS。
- Smoke 证据:
  - 玩家代理新增 `sendActionBar` 和 `showTitle` 计数。
  - 最新 marker 形态为 `migration=...+feedbackSound:5+worldSound:7+particles:7+actionbar:3+title:2`。
  - `actionbar:3` 来自迁移开始两次 + 资源采集一次；`title:2` 来自两次目标锁定，和当前真实事件流一致。
- 验证:
  - `mvn -q "-Dtest=ConfigurationServiceResourceFeedbackConfigTest,ConfigDefaultsCoverageTest,NativeNationResourceDistrictServiceFlowTest" test`: PASS。
  - `mvn -q test`: PASS，`285` tests，`0` failures，`0` errors。
  - `mvn -q -DskipTests package`: PASS。
  - `.\scripts\smoke-starcore-paper-integration.ps1 -ProtectorApiSmoke -BrowserSmoke -TimeoutSeconds 420`: PASS on 2026-06-10 14:18 +08:00。
  - Smoke summary: `D:\qwq\项目\mapadd\starcore\target\smoke-harness-20260610-141751\smoke-summary.json`
  - Smoke marker: `STARCORE_SMOKE_PASS ... migration=gui+mined:world:192:192+forced:world:192:193+feedbackSound:5+worldSound:7+particles:7+actionbar:3+title:2 ... webClaim=command:382e0bef ... viewer=ok ... mapSummary=5 territory polygon(s), 0 player marker(s), 1 resource district marker(s)`。
  - `.\scripts\check-starcore-performance-budget.ps1 -SmokeSummaryJsonPath target\smoke-harness-20260610-141751\smoke-summary.json`: PASS，summary `target\performance-budget-checks\check-20260610-141845\performance-budget-summary.json`，`10/10` pass，baseline trend `ok`。
  - `.\scripts\build-latest-verification-summary.ps1 ...`: PASS，`docs\LATEST_VERIFICATION_SUMMARY.md` 已显示最新 jar SHA256 `D376FBF6F87B67528B58FA4FCF6C31D18FFD3508DCCDA0FC5BF6C89FDBDF156C` 和 actionbar/title smoke 计数。

下一刀建议:

1. 用真实运行服执行 spark profiling，导入真实报告替换 `sample-verification`，再跑完整一站式 zip 发版验证。
2. 跑 `.\scripts\verify-starcore-release.ps1 -IncludeSmoke -ProtectorApiSmoke -BrowserSmoke -BuildEvidencePack -BuildReleaseChannelAssets -SparkProfileSummaryPath <real-spark-summary> -RequireRealSparkProfile`，确认真实 zip 包含 HUD contract、full smoke、10/10 performance budget、可见提示 smoke marker 和真实 spark profile。
3. 继续把这套 feedback profile 抽成可复用于圈地确认、国家操作和政策/科技解锁的通用游戏内反馈组件。

### 2026-06-10 资源区块世界侧声音/粒子 Paper smoke 证据（已完成）

本轮接 P1“资源区块管理闭环 / 游戏内效果”继续加厚验收证据：上一刀已经证明迁移主流程会触达 `Player.playSound(...)`，这次补上 World 侧 `playSound(...)` 和 `spawnParticle(...)` 计数。smoke harness 仍不改生产业务逻辑，只在验收层反射创建正式 `BukkitResourceDistrictFeedbackSupport`，用可计数的 `World` 代理跑同一套配置化语义事件，确认声音和粒子分支都能从 `config.yml` 解析到 Bukkit adapter。

- 新增/更新:
  - `scripts/smoke-starcore-paper-integration.ps1`
  - `docs/LATEST_VERIFICATION_SUMMARY.md`
  - `docs/CONTINUATION_PLAN_2026-06-05.md`
  - `docs/RELEASE_CHECKLIST_2026-06-05.md`
  - `docs/OPERATIONS_RUNBOOK_2026-06-05.md`
  - `docs/MODULE_PLAN.md`
- Smoke 证据:
  - `runMigrationSmoke(...)` 保留真实迁移 GUI / 挖掘完成 / 强制迁移流程中的玩家侧声音计数。
  - 新增 `runFeedbackEffectProbe(...)`，覆盖 `migration-started`、`migration-target-selected`、`migration-completed`、`migration-completed-forced`、`migration-blocked`、`resource-refreshed`、`resource-mined` 七个配置事件。
  - 新 marker 形态为 `migration=...+feedbackSound:5+worldSound:7+particles:7`，其中 `worldSound` / `particles` 来自正式 Bukkit feedback support 的 World 侧调用计数。
  - 具体 Sound、Particle、数量、音量、音高、扩散和 y offset 仍全部由 `nation.resources.feedback.*` 配置控制，业务代码只触发语义事件，不把表现写死到迁移规则里。
- 验证:
  - `mvn -q -DskipTests package`: PASS on 2026-06-10 14:08 +08:00。
  - `.\scripts\smoke-starcore-paper-integration.ps1 -ProtectorApiSmoke -BrowserSmoke -TimeoutSeconds 420`: PASS on 2026-06-10 14:09 +08:00。
  - Smoke summary: `D:\qwq\项目\mapadd\starcore\target\smoke-harness-20260610-140843\smoke-summary.json`
  - Smoke marker: `STARCORE_SMOKE_PASS ... migration=gui+mined:world:156:156+forced:world:156:157+feedbackSound:5+worldSound:7+particles:7 ... webClaim=command:19433097 ... viewer=ok ... mapSummary=5 territory polygon(s), 0 player marker(s), 1 resource district marker(s)`。
  - WebClaim smoke: PASS，`provider=MockProtectorSmoke chunk=164,164 bounds=2624,2624->2655,2655`。
  - Browser smoke: PASS，`nationDetail=true nationAction=true recentLog=5 resourceAction=player-offline commandUiRemoved=true`。
  - `.\scripts\check-starcore-performance-budget.ps1 -SmokeSummaryJsonPath target\smoke-harness-20260610-140843\smoke-summary.json`: PASS，summary `target\performance-budget-checks\check-20260610-141022\performance-budget-summary.json`，`10/10` pass，Browser DOM=`25483` bytes，Browser screenshot=`400568` bytes，baseline trend `ok`。
  - `.\scripts\build-latest-verification-summary.ps1 ... -SmokeSummaryJsonPath target\smoke-harness-20260610-140843\smoke-summary.json ...`: PASS，`docs\LATEST_VERIFICATION_SUMMARY.md` 已显示 `feedbackSound:5+worldSound:7+particles:7` 和 `Performance budget: ok (10/10 pass)`。

下一刀建议:

1. 用真实运行服执行 spark profiling，导入真实报告替换 `sample-verification`，再跑完整一站式 zip 发版验证。
2. 跑 `.\scripts\verify-starcore-release.ps1 -IncludeSmoke -ProtectorApiSmoke -BrowserSmoke -BuildEvidencePack -BuildReleaseChannelAssets -SparkProfileSummaryPath <real-spark-summary> -RequireRealSparkProfile`，确认真实 zip 包含 HUD contract、full smoke、10/10 performance budget 和真实 spark profile。
3. 继续把这套 feedback profile 抽成可复用于圈地确认、国家操作和政策/科技解锁的通用游戏内反馈组件。

### 2026-06-10 Spark profiling 报告入口接入发布证据链（已完成）

本轮继续加深 P3“高性能可验证”，把 spark profiling 从“下一步建议”落成可导入、可摘要、可打包的证据入口。当前验证用 `sample-verification` 样本证明脚本链路；它不是实服性能报告，后续真实发版前需要用运行服的 spark 报告替换同一入口。

- 新增/更新:
  - `scripts/import-starcore-spark-profile.ps1`
  - `scripts/build-latest-verification-summary.ps1`
  - `scripts/build-starcore-release-evidence-pack.ps1`
  - `scripts/build-starcore-release-channel-assets.ps1`
  - `scripts/verify-starcore-release.ps1`
  - `docs/LATEST_VERIFICATION_SUMMARY.md`
  - `docs/CONTINUATION_PLAN_2026-06-05.md`
  - `docs/RELEASE_CHECKLIST_2026-06-05.md`
  - `docs/OPERATIONS_RUNBOOK_2026-06-05.md`
  - `docs/MODULE_PLAN.md`
- Spark profiling 入口:
  - `import-starcore-spark-profile.ps1` 支持 `-ReportPath`、`-ReportUrl`、`-SourceLabel`、`-Notes` 和采集命令字段，输出 `target\spark-profile-imports\profile-*\spark-profile-summary.json`。
  - 本地 artifact 会复制到 import 目录并记录 `kind`、`bytes`、`fileCount`、`sha256`；只提供 URL 时会输出 warning，避免把纯链接误当成本地证据。
  - `build-latest-verification-summary.ps1` 新增可选 `-SparkProfileSummaryPath`，在 `## Spark Profiling` 段展示 URL、artifact、hash、采集命令和备注。
  - release evidence pack 新增 `performance\spark-profile-summary.json` 和 `performance\spark-profile-artifact\*`，manifest 写入 `sparkProfile*` snapshot 字段。
  - release channel assets 新增 `references\spark-profile-summary.json` 和 `references\spark-profile-artifact\*`，`UPLOAD_GUIDE.md` 显示 `Spark profiling` 状态。
  - `verify-starcore-release.ps1` 新增可选 `-SparkProfileSummaryPath`，并把仓库内 PowerShell JSON 脚本改为当前进程调用，避免 Windows 中文路径在子进程输出捕获中出现乱码后继续传递。
- 验证:
  - `.\scripts\import-starcore-spark-profile.ps1 -ProjectRoot . -ReportPath docs\LATEST_VERIFICATION_SUMMARY.md -ReportUrl https://spark.lucko.me/starcore-sample-profile -SourceLabel sample-verification ...`: PASS，summary `target\spark-profile-imports\profile-script-check\spark-profile-summary.json`，artifact SHA256 `997BC41D542DE9380499F36F6A140BC853729C5EC6DDB81249D89D724B4BB6F2`。
  - `.\scripts\build-latest-verification-summary.ps1 ... -SparkProfileSummaryPath target\spark-profile-imports\profile-script-check\spark-profile-summary.json`: PASS，`docs\LATEST_VERIFICATION_SUMMARY.md` 显示 `Spark profile: ok (sample-verification)` 和 `## Spark Profiling`。
  - `.\scripts\build-starcore-release-evidence-pack.ps1 ... -SparkProfileSummaryPath ... -SkipZip`: PASS，output `target\release-evidence-spark-profile-check`，manifest `sparkProfile=ok`、`sparkProfileSource=sample-verification`、`sparkProfileArtifactBytes=6568`。
  - `.\scripts\build-starcore-release-channel-assets.ps1 ... -SparkProfileSummaryPath ... -SkipZip`: PASS，output `target\release-channel-assets-spark-profile-check`，`UPLOAD_GUIDE.md` 显示 `Spark profiling: ok`，manifest 写入 `references.sparkProfileSummary` 和 `references.sparkProfileArtifact`。
  - `.\scripts\verify-starcore-release.ps1 -SparkProfileSummaryPath target\spark-profile-imports\profile-script-check\spark-profile-summary.json`: PASS，轻量模式 `mvn test/package`、HUD contract、runtime selfcheck、performance budget warning（Browser snapshot not included）和 Spark section 均生成；实际落盘 JSON/Markdown 路径保持中文正常。

下一刀建议:

1. 用真实运行服执行 spark profiling，导入真实报告替换 `sample-verification`，再跑完整一站式 zip 发版验证。
2. 跑 `.\scripts\verify-starcore-release.ps1 -IncludeSmoke -ProtectorApiSmoke -BrowserSmoke -BuildEvidencePack -BuildReleaseChannelAssets -SparkProfileSummaryPath <real-spark-summary> -RequireRealSparkProfile`，确认真实 zip 包含 batch budget、median trend、HUD contract、smoke 和 spark profile。
3. 继续把这套 feedback profile 抽成可复用于圈地确认、国家操作和政策/科技解锁的通用游戏内反馈组件。

### 2026-06-10 性能预算增加专门批量样本（已完成）

本轮继续加深 P3“高性能可验证”，把上一刀的 suite/method runtime 预算推进到专门批量样本。Java 测试只做确定性批量路径采样和功能断言，性能阈值仍全部留在 `scripts/starcore-performance-budgets.json`，避免把预算数字硬编码进测试。

- 新增/更新:
  - `src/test/java/dev/starcore/starcore/module/map/MapPerformanceBatchSamplesTest.java`
  - `src/test/java/dev/starcore/starcore/foundation/economy/SqlEconomyPerformanceBatchSamplesTest.java`
  - `scripts/starcore-performance-budgets.json`
  - `scripts/check-starcore-performance-budget.ps1`
  - `docs/LATEST_VERIFICATION_SUMMARY.md`
  - `docs/CONTINUATION_PLAN_2026-06-05.md`
  - `docs/RELEASE_CHECKLIST_2026-06-05.md`
  - `docs/OPERATIONS_RUNBOOK_2026-06-05.md`
  - `docs/MODULE_PLAN.md`
- 批量样本:
  - `claim_lookup_batch`: `MapPerformanceBatchSamplesTest#samplesClaimLookupPreviewSubmitAndConfirmBatch`，64 组 web claim preview -> submit -> confirm，覆盖价格明细 JSON 排序和 pending claim 消费。
  - `map_render_batch`: `MapPerformanceBatchSamplesTest#samplesTerrainRasterColdHotDiskAndBinaryBatch`，32 组内存冷/热 PNG + binary 路径，另加 16 组 disk cache writer/reader 路径，断言 disk reader 不重新 render。
  - `sql_flush_batch`: `SqlEconomyPerformanceBatchSamplesTest#samplesSqlBalanceFlushBatchAgainstSqlite`，24 个账户 deposit/withdraw/flush/reload，覆盖 `InternalEconomyService` + `SqlBalanceStorage` 的 SQLite 批量落盘路径。
  - `check-starcore-performance-budget.ps1` 顺手修正显式 `-BaselineSummaryPath` 模式下顶层 `baselineSummaryPath` / `baselineSummaryPaths` 的数组输出，避免单路径被 PowerShell 字符串索引成首字符。
- 验证:
  - `mvn -q "-Dtest=MapPerformanceBatchSamplesTest,SqlEconomyPerformanceBatchSamplesTest" test`: PASS。
  - `mvn -q test`: PASS，`285` tests，`0` failures，`0` errors。
  - 首次 `.\scripts\check-starcore-performance-budget.ps1 -ProjectRoot . -SmokeSummaryJsonPath target\smoke-harness-20260610-124003\smoke-summary.json`: `10/10` pass，baseline warning 仅因 3 个新增 batch budget 尚无历史样本。
  - Final performance summary: `D:\qwq\项目\mapadd\starcore\target\performance-budget-checks\check-20260610-134023\performance-budget-summary.json`
  - Final budget result: `ok`，`10/10` pass，baseline trend `ok`，mode `history`，aggregation `median`，samples `5/5`，comparisons `11`。
  - Batch budgets: `claim_lookup_batch=0.016s/1.500s`，`map_render_batch=0.191s/2.000s`，`sql_flush_batch=0.289s/4.000s`。
  - `.\scripts\check-starcore-performance-budget.ps1 ... -BaselineSummaryPath target\performance-budget-checks\check-20260610-133754\performance-budget-summary.json`: PASS，`baseline.mode=explicit`、`sampleCount=1`，顶层 `baselineSummaryPath` 为完整路径。
  - `.\scripts\check-starcore-performance-budget.ps1 -ProjectRoot . -AllowMissingSmoke`: PASS as warning，轻量模式 `9/10` pass、Browser snapshot `not_included`、history median baseline `ok`，未复用旧 smoke。
  - `.\scripts\build-latest-verification-summary.ps1 ... -PerformanceBudgetSummaryPath target\performance-budget-checks\check-20260610-134023\performance-budget-summary.json`: PASS，摘要显示 `Performance budget: ok (10/10 pass)`，列出 3 个 batch budget。
  - `.\scripts\build-starcore-release-evidence-pack.ps1 ... -PerformanceBudgetSummaryPath ... -SkipZip`: PASS，output `target\release-evidence-performance-batch-check`，manifest `performanceBudgetPassed=10`、`performanceBudgetBaseline=ok`。
  - `.\scripts\build-starcore-release-channel-assets.ps1 ... -PerformanceBudgetSummaryPath ... -SkipZip`: PASS，output `target\release-channel-assets-performance-batch-check`，manifest `performanceBudgetPassed=10`、`performanceBudgetBaseline=ok`，`UPLOAD_GUIDE.md` 显示 `Performance budget: ok (10/10 pass)` 和 `Performance trend: ok`。

下一刀建议:

1. 用真实运行服执行 spark profiling，导入真实报告替换 `sample-verification`。
2. 跑一次完整一站式 zip 发版验证，把 batch budget + median trend + spark profile 证据打进真实 release evidence / release channel zip。
3. 继续把这套 feedback profile 抽成可复用于圈地确认、国家操作和政策/科技解锁的通用游戏内反馈组件。

### 2026-06-10 性能趋势升级为最近 5 次 median（已完成）

本轮继续加深 P3“高性能可验证”，把 performance trend 从单次 baseline 对比升级为历史窗口 median 对比。`scripts/starcore-performance-budgets.json` 现在声明 `trend.baselineWindowSize=5` 和 `trend.aggregation=median`；`check-starcore-performance-budget.ps1` 默认读取最近 5 份 `performance-budget-summary.json`，按预算项聚合可比较样本的中位数，再和当前结果做 regression 判断。显式传 `-BaselineSummaryPath` 时仍保持单 baseline 兼容，输出 `baseline.mode=explicit`。

- 新增/更新:
  - `scripts/starcore-performance-budgets.json`
  - `scripts/check-starcore-performance-budget.ps1`
  - `scripts/build-latest-verification-summary.ps1`
  - `docs/LATEST_VERIFICATION_SUMMARY.md`
  - `docs/CONTINUATION_PLAN_2026-06-05.md`
  - `docs/RELEASE_CHECKLIST_2026-06-05.md`
  - `docs/OPERATIONS_RUNBOOK_2026-06-05.md`
  - `docs/MODULE_PLAN.md`
- 趋势门禁:
  - performance summary 的 `baseline` 现在包含 `mode`、`aggregation`、`windowSize`、`sampleCount`、`summaryPaths[]`。
  - 默认历史模式输出 `baseline.mode=history`、`baseline.aggregation=median`、`baseline.windowSize=5`。
  - `build-latest-verification-summary.ps1` 的 Performance Budgets 段显示 mode、aggregation、samples/window 和 comparison 数。
  - `-BaselineSummaryPath` 保持兼容，输出 `baseline.mode=explicit`、`sampleCount=1`。
- 验证:
  - `.\scripts\check-starcore-performance-budget.ps1 -ProjectRoot . -SmokeSummaryJsonPath target\smoke-harness-20260610-124003\smoke-summary.json`: PASS on 2026-06-10 13:27 +08:00，`7/7` pass，baseline trend `ok`，mode `history`，aggregation `median`，samples `5/5`，comparisons `8`。
  - Performance summary: `D:\qwq\项目\mapadd\starcore\target\performance-budget-checks\check-20260610-132712\performance-budget-summary.json`
  - Latest baseline sample in window: `D:\qwq\项目\mapadd\starcore\target\performance-budget-checks\check-20260610-132651\performance-budget-summary.json`
  - `.\scripts\check-starcore-performance-budget.ps1 ... -BaselineSummaryPath target\performance-budget-checks\check-20260610-131458\performance-budget-summary.json`: PASS，`baseline.mode=explicit`、`sampleCount=1`。
  - `.\scripts\check-starcore-performance-budget.ps1 -ProjectRoot . -AllowMissingSmoke`: PASS as warning，轻量模式 `6/7` pass、Browser snapshot `not_included`、history median baseline `ok`，未复用旧 smoke。
  - `.\scripts\build-latest-verification-summary.ps1 ... -PerformanceBudgetSummaryPath ...`: PASS，摘要显示 `Baseline trend: ok / mode=history / aggregation=median / samples=5/5`。
  - `.\scripts\build-starcore-release-evidence-pack.ps1 ... -PerformanceBudgetSummaryPath ... -SkipZip`: PASS，output `target\release-evidence-performance-median-check`。
  - `.\scripts\build-starcore-release-channel-assets.ps1 ... -PerformanceBudgetSummaryPath ... -SkipZip`: PASS，output `target\release-channel-assets-performance-median-check`。

下一刀建议:

1. 接入 spark profiling 报告入口，先文档化采集方式，再考虑自动化。
2. 跑一次完整一站式 zip 发版验证，把 median trend 证据打进真实 release evidence / release channel zip。
3. 继续补游戏内效果 smoke 证据，尤其是资源区块迁移/圈地确认的声音、粒子和提示链路。

### 2026-06-10 性能预算增加方法级 runtime 门禁（已完成）

本轮继续加深 P3“高性能可验证”，在 suite time / baseline trend 的基础上增加方法级 Surefire runtime 预算。`check-starcore-performance-budget.ps1` 现在会解析 Surefire XML 的 `<testcase time>`，并支持 `surefire-testcase-time` 预算类型；具体要盯哪些方法仍由 `scripts/starcore-performance-budgets.json` 配置，不在脚本里硬编码。

- 新增/更新:
  - `scripts/starcore-performance-budgets.json`
  - `scripts/check-starcore-performance-budget.ps1`
  - `scripts/build-latest-verification-summary.ps1`
  - `docs/LATEST_VERIFICATION_SUMMARY.md`
  - `docs/CONTINUATION_PLAN_2026-06-05.md`
  - `docs/RELEASE_CHECKLIST_2026-06-05.md`
  - `docs/OPERATIONS_RUNBOOK_2026-06-05.md`
  - `docs/MODULE_PLAN.md`
- 方法级预算:
  - `claim_lookup_cases`: 覆盖 web claim submit/confirm/validate、ClaimConflictSampler、SQL territory claim/unclaim。
  - `map_render_cases`: 覆盖 authenticated viewer snapshot、资源区块迁移响应、资源区块 layer metadata、claim preview、terrain tile hot/disk cache 和 PNG/data endpoint。
  - `sql_flush_cases`: 覆盖 Nation、ResourceDistrict、Resource、Treasury、Balance SQL persistence reload 路径。
  - `build-latest-verification-summary.ps1` 会把 `surefire-testcase-time` 渲染成 testcase 数、总耗时和预算。
- 验证:
  - `.\scripts\check-starcore-performance-budget.ps1 -ProjectRoot . -SmokeSummaryJsonPath target\smoke-harness-20260610-124003\smoke-summary.json`: PASS on 2026-06-10 13:14 +08:00；首次生成 `7/7` pass，因 baseline 尚无新增方法级预算而 trend warning。
  - Final performance summary: `D:\qwq\项目\mapadd\starcore\target\performance-budget-checks\check-20260610-131458\performance-budget-summary.json`
  - Final budget result: `7/7` pass，baseline trend `ok`，baseline comparisons `8`。
  - Method budgets: `claim_lookup_cases=0.042s/1.000s`（6 cases），`map_render_cases=0.083s/1.500s`（7 cases），`sql_flush_cases=0.267s/2.000s`（5 cases）。
  - `.\scripts\build-latest-verification-summary.ps1 ... -PerformanceBudgetSummaryPath ...`: PASS，摘要显示 `Performance budget: ok (7/7 pass)`，并列出 3 个方法级预算。
  - `.\scripts\build-starcore-release-evidence-pack.ps1 ... -PerformanceBudgetSummaryPath ... -SkipZip`: PASS，output `target\release-evidence-performance-testcase-check`，manifest `performanceBudgetPassed=7`、`performanceBudgetBaseline=ok`。
  - `.\scripts\build-starcore-release-channel-assets.ps1 ... -PerformanceBudgetSummaryPath ... -SkipZip`: PASS，output `target\release-channel-assets-performance-testcase-check`，manifest `performanceBudgetPassed=7`、`performanceBudgetBaseline=ok`，`UPLOAD_GUIDE.md` 显示 `Performance budget: ok (7/7 pass)`。
  - `.\scripts\check-starcore-performance-budget.ps1 -ProjectRoot . -AllowMissingSmoke`: PASS as warning on 2026-06-10 13:17 +08:00，轻量模式 `6/7` pass、Browser snapshot `not_included`、baseline trend `ok`，未复用旧 smoke。

下一刀建议:

1. 接入 spark profiling 报告入口，先文档化采集方式，再考虑自动化。
2. 跑一次完整一站式 zip 发版验证，把 batch budget + median trend 证据打进真实 release evidence / release channel zip。
3. 继续补游戏内效果 smoke 证据，尤其是资源区块迁移/圈地确认的声音、粒子和提示链路。

### 2026-06-10 性能预算 baseline/trend 接入发布包（已完成）

本轮继续加深 P3“高性能可验证”，在上一刀单次预算门禁上补 baseline/trend 对比。`check-starcore-performance-budget.ps1` 现在会自动读取上一份 `target/performance-budget-checks/check-*/performance-budget-summary.json` 作为 baseline，也可以显式传 `-BaselineSummaryPath`；每个预算项的回退阈值仍在 `scripts/starcore-performance-budgets.json` 里配置，当前以 `warn` 模式记录趋势异常，避免把不同机器上的微小波动直接做成硬失败。

- 新增/更新:
  - `scripts/starcore-performance-budgets.json`
  - `scripts/check-starcore-performance-budget.ps1`
  - `scripts/build-latest-verification-summary.ps1`
  - `scripts/build-starcore-release-evidence-pack.ps1`
  - `scripts/build-starcore-release-channel-assets.ps1`
  - `docs/LATEST_VERIFICATION_SUMMARY.md`
  - `docs/CONTINUATION_PLAN_2026-06-05.md`
  - `docs/RELEASE_CHECKLIST_2026-06-05.md`
  - `docs/OPERATIONS_RUNBOOK_2026-06-05.md`
  - `docs/MODULE_PLAN.md`
- 趋势门禁:
  - Surefire suite time 预算支持 `maxRegressionPercent`、`minRegressionDeltaSeconds`、`regressionMode`。
  - Browser DOM / screenshot 工件预算支持 `maxRegressionPercent`、`minRegressionDeltaBytes`、`regressionMode`。
  - performance summary 新增 `baseline.status`、`baseline.summaryPath`、`baseline.comparisons[]`、`baseline.warnings[]`、`baseline.errors[]`。
  - `LATEST_VERIFICATION_SUMMARY.md` 的 Performance Budgets 段现在显示 `Baseline trend` 状态和 comparison 数。
  - release evidence/channel manifest 新增 `snapshot.performanceBudgetBaseline`。
  - release channel `UPLOAD_GUIDE.md` 显示 `Performance trend`。
- 验证:
  - `.\scripts\check-starcore-performance-budget.ps1 -ProjectRoot . -SmokeSummaryJsonPath target\smoke-harness-20260610-124003\smoke-summary.json`: PASS on 2026-06-10 13:09 +08:00，`4/4` pass，baseline trend `ok`。
  - Performance summary: `D:\qwq\项目\mapadd\starcore\target\performance-budget-checks\check-20260610-130932\performance-budget-summary.json`
  - Baseline summary: `D:\qwq\项目\mapadd\starcore\target\performance-budget-checks\check-20260610-130544\performance-budget-summary.json`
  - Baseline comparisons: `5`（claim lookup / map render / SQL flush / Browser DOM / Browser screenshot）。
  - `.\scripts\build-latest-verification-summary.ps1 ... -PerformanceBudgetSummaryPath ...`: PASS，摘要显示 `Performance budget: ok` 和 `Baseline trend: ok`。
  - `.\scripts\build-starcore-release-evidence-pack.ps1 ... -PerformanceBudgetSummaryPath ... -SkipZip`: PASS，output `target\release-evidence-performance-trend-check`，manifest `performanceBudgetBaseline=ok`。
  - `.\scripts\build-starcore-release-channel-assets.ps1 ... -PerformanceBudgetSummaryPath ... -SkipZip`: PASS，output `target\release-channel-assets-performance-trend-check`，manifest `performanceBudgetBaseline=ok`，`UPLOAD_GUIDE.md` 显示 `Performance trend: ok`。

下一刀建议:

1. 接入 spark profiling 报告入口，先文档化采集方式，再考虑自动化。
2. 跑一次完整一站式 zip 发版验证，把 batch budget + median trend 证据打进真实 release evidence / release channel zip。
3. 继续补游戏内效果 smoke 证据，尤其是资源区块迁移/圈地确认的声音、粒子和提示链路。

### 2026-06-10 性能预算门禁接入 release verify（已完成）

本轮继续推进 P3“高性能可验证”，把 claim lookup、map render、SQL flush、browser snapshot 的第一版性能预算做成可配置发布门禁。阈值、测试类映射和 Browser 工件大小限制都放在 `scripts/starcore-performance-budgets.json`，脚本只负责读取当前 Surefire XML、smoke summary 和工件大小并输出 `target/performance-budget-checks/check-*/performance-budget-summary.json`，避免把预算和证据路径硬编码在逻辑里。

- 新增/更新:
  - `scripts/starcore-performance-budgets.json`
  - `scripts/check-starcore-performance-budget.ps1`
  - `scripts/verify-starcore-release.ps1`
  - `scripts/build-latest-verification-summary.ps1`
  - `scripts/build-starcore-release-evidence-pack.ps1`
  - `scripts/build-starcore-release-channel-assets.ps1`
  - `docs/LATEST_VERIFICATION_SUMMARY.md`
  - `docs/CONTINUATION_PLAN_2026-06-05.md`
  - `docs/RELEASE_CHECKLIST_2026-06-05.md`
  - `docs/OPERATIONS_RUNBOOK_2026-06-05.md`
  - `docs/MODULE_PLAN.md`
- 性能门禁:
  - `claim_lookup`: 汇总 `MapClaimEndpointTest`、`ClaimConflictSamplerTest`、`SqlTerritoryServiceTest` 的 Surefire suite time，预算 `2.0s`。
  - `map_render`: 汇总 map snapshot / terrain tile 相关 suite time，预算 `3.0s`。
  - `sql_flush`: 汇总 Nation/Resource/Treasury/Balance SQL storage suite time，预算 `5.0s`。
  - `browser_snapshot`: 完整 smoke 时检查 Browser smoke `PASS`，并限制 DOM `12000..250000` bytes、截图 `120000..1500000` bytes。
  - 轻量 verify 不跑 smoke 时不会复用旧 smoke；Browser snapshot 会标记 `not_included`，claim/map/sql 三个预算仍照常检查。
- 发布链路:
  - `verify-starcore-release.ps1` 在 smoke 之后、生成摘要之前执行性能预算检查。
  - `build-latest-verification-summary.ps1` 新增 `## Performance Budgets` 段。
  - release evidence pack 新增 `performance/performance-budget-summary.json`，manifest 写入 `performanceBudget*` snapshot 字段。
  - release channel assets 新增 `references/performance-budget-summary.json`，`UPLOAD_GUIDE.md` 和 manifest snapshot 显示性能预算状态。
- 验证:
  - `.\scripts\verify-starcore-release.ps1`: PASS on 2026-06-10 12:55 +08:00；轻量模式性能预算 `warning`，`3/4` pass，Browser snapshot `not_included`。
  - Lightweight performance summary: `D:\qwq\项目\mapadd\starcore\target\performance-budget-checks\check-20260610-125506\performance-budget-summary.json`
  - `.\scripts\check-starcore-performance-budget.ps1 -ProjectRoot . -SmokeSummaryJsonPath target\smoke-harness-20260610-124003\smoke-summary.json`: PASS on 2026-06-10 12:59 +08:00，`4/4` pass。
  - Full smoke performance summary: `D:\qwq\项目\mapadd\starcore\target\performance-budget-checks\check-20260610-125921\performance-budget-summary.json`
  - Full budget evidence: `claim_lookup=0.074s/2.000s`, `map_render=0.168s/3.000s`, `sql_flush=0.367s/5.000s`, Browser DOM `25463` bytes, Browser screenshot `405707` bytes。
  - `.\scripts\build-starcore-release-evidence-pack.ps1 ... -PerformanceBudgetSummaryPath ... -SkipZip`: PASS, output `target\release-evidence-performance-budget-check`
  - `.\scripts\build-starcore-release-channel-assets.ps1 ... -PerformanceBudgetSummaryPath ... -SkipZip`: PASS, output `target\release-channel-assets-performance-budget-check`

下一刀建议:

1. 接入 spark profiling 报告入口，先文档化采集方式，再考虑自动化。
2. 跑一次完整一站式 zip 发版验证，把 batch budget + median trend 证据打进真实 release evidence / release channel zip。
3. 继续补游戏内效果 smoke 证据，尤其是资源区块迁移/圈地确认的声音、粒子和提示链路。

### 2026-06-10 HUD contract summary 纳入 release channel assets（已完成）

本轮继续收口对外发布材料，把同一轮 Map HUD contract summary 纳入 release channel assets。现在平台成品包不只包含 Modrinth / Hangar / SpigotMC 文案、更新日志、Browser 截图和当前 jar，还会复制 `references/map-hud-contract-summary.json`，并在 `release-channel-assets-manifest.json` 的 `references.mapHudContractSummary` 与 `snapshot.mapHudContract*` 字段里记录 HUD 契约状态、三份资产 match 计数和 Browser smoke 契约检查。

- 新增/更新:
  - `scripts/build-starcore-release-channel-assets.ps1`
  - `scripts/verify-starcore-release.ps1`
  - `docs/CONTINUATION_PLAN_2026-06-05.md`
  - `docs/RELEASE_CHECKLIST_2026-06-05.md`
  - `docs/OPERATIONS_RUNBOOK_2026-06-05.md`
  - `docs/MODULE_PLAN.md`
- 发布渠道包:
  - `build-starcore-release-channel-assets.ps1` 新增 `-HudContractSummaryPath`，未传入时会自动读取最新 `target/map-hud-contract-checks/check-*/map-hud-contract-summary.json`。
  - 成品包新增 `references/map-hud-contract-summary.json`。
  - `UPLOAD_GUIDE.md` 新增 `Map HUD contract` 状态行，并把 HUD 契约摘要列入“上传时建议一起带上的材料”。
  - manifest 新增 `references.mapHudContractSummary`。
  - manifest snapshot 新增 `mapHudContract`、`mapHudContractSourceMatches`、`mapHudContractStaticPreviewMatches`、`mapHudContractRuntimeMatches`、`mapHudBrowserSmokeContract`。
  - `verify-starcore-release.ps1 -BuildReleaseChannelAssets` 会把同一轮 HUD contract summary 传给成品包脚本，避免上架材料引用旧门禁结果。
- 验证:
  - `.\scripts\verify-starcore-release.ps1 -IncludeSmoke -ProtectorApiSmoke -BrowserSmoke -BuildReleaseChannelAssets`: PASS on 2026-06-10 12:40 +08:00
  - Release channel assets: `D:\qwq\项目\mapadd\starcore\target\release-channel-assets-20260610-124048.zip`
  - Release channel manifest: `D:\qwq\项目\mapadd\starcore\target\release-channel-assets-20260610-124048\release-channel-assets-manifest.json`
  - HUD contract in pack: `D:\qwq\项目\mapadd\starcore\target\release-channel-assets-20260610-124048\references\map-hud-contract-summary.json`
  - Manifest snapshot: `mapHudContract=ok`, source/static-preview/runtime matches all `0`, `mapHudBrowserSmokeContract=ok`
  - Smoke summary: `D:\qwq\项目\mapadd\starcore\target\smoke-harness-20260610-124003\smoke-summary.json`
  - Smoke marker: `STARCORE_SMOKE_PASS ... migration=gui+mined:world:210:210+forced:world:210:211+feedbackSound:5 ... webClaim=command:9d9cd76f ... viewer=ok ...`
  - Browser smoke: PASS, `nationDetail=true nationAction=true recentLog=5 resourceAction=player-offline commandUiRemoved=true`
  - Release channel zip SHA256: `83E82C4C7D7E714042999F682FC8361AF3A34B6F6CDB3841C291995D0046E5E0`
  - Jar SHA256: `E3406BFB5484AA7A7A1F15F4C93440194034E7851A5218DA164F4370ABE9A25D`

下一刀建议:

1. 继续做国家领袖运营面趋势和阻塞分组，保持 web 为情报面、游戏内 GUI/命令为操作面。
2. 如需更强效果证据，可继续补 World 侧 `playSound/spawnParticle` 计数，覆盖迁移完成、强制迁移完成和刷新粒子。
3. 给 claim lookup、map render、SQL flush、browser snapshot 设计性能预算门禁。

### 2026-06-10 HUD contract summary 纳入 release evidence pack（已完成）

本轮继续收口发布证据链，把上一刀生成的 Map HUD contract summary 纳入 release evidence pack。现在证据包不只包含 jar、验证摘要、smoke summary、Browser 工件和 Paper 日志，还会复制 `contracts/map-hud-contract-summary.json`，并在 `release-evidence-manifest.json` 的 `contracts.mapHudContractSummary` 与 `snapshot.mapHudContract*` 字段里记录 HUD 契约状态和三份资产的 match 计数。

- 新增/更新:
  - `scripts/build-starcore-release-evidence-pack.ps1`
  - `scripts/verify-starcore-release.ps1`
  - `docs/CONTINUATION_PLAN_2026-06-05.md`
  - `docs/RELEASE_CHECKLIST_2026-06-05.md`
  - `docs/OPERATIONS_RUNBOOK_2026-06-05.md`
  - `docs/MODULE_PLAN.md`
- 发布证据:
  - `build-starcore-release-evidence-pack.ps1` 新增 `-HudContractSummaryPath`，未传入时会自动读取最新 `target/map-hud-contract-checks/check-*/map-hud-contract-summary.json`。
  - 证据包新增 `contracts/` 目录。
  - manifest 新增 `contracts.mapHudContractSummary`。
  - manifest snapshot 新增 `mapHudContract`、`mapHudContractSourceMatches`、`mapHudContractStaticPreviewMatches`、`mapHudContractRuntimeMatches`、`mapHudBrowserSmokeContract`。
  - `verify-starcore-release.ps1 -BuildEvidencePack` 会把同一轮 HUD contract summary 传给证据包脚本，避免打包旧门禁结果。
- 验证:
  - `.\scripts\build-starcore-release-evidence-pack.ps1 ... -HudContractSummaryPath ... -SkipZip`: PASS on 2026-06-10 12:31 +08:00
  - `.\scripts\verify-starcore-release.ps1 -IncludeSmoke -ProtectorApiSmoke -BrowserSmoke -BuildEvidencePack`: PASS on 2026-06-10 12:33 +08:00
  - Evidence pack: `D:\qwq\项目\mapadd\starcore\target\release-evidence-20260610-123339.zip`
  - Evidence manifest: `D:\qwq\项目\mapadd\starcore\target\release-evidence-20260610-123339\release-evidence-manifest.json`
  - HUD contract in pack: `D:\qwq\项目\mapadd\starcore\target\release-evidence-20260610-123339\contracts\map-hud-contract-summary.json`
  - Manifest snapshot: `mapHudContract=ok`, source/static-preview/runtime matches all `0`, `mapHudBrowserSmokeContract=ok`
  - Smoke summary: `D:\qwq\项目\mapadd\starcore\target\smoke-harness-20260610-123247\smoke-summary.json`
  - Smoke marker: `STARCORE_SMOKE_PASS ... migration=gui+mined:world:35:35+forced:world:35:36+feedbackSound:5 ... webClaim=command:a10bafd1 ... viewer=ok ... mapSummary=5 territory polygon(s), 0 player marker(s), 1 resource district marker(s)`
  - Browser smoke: PASS, `nationDetail=true nationAction=true recentLog=5 resourceAction=player-offline commandUiRemoved=true`
  - Evidence zip SHA256: `53660DD3ACEBCE97FB5CCD05CB7EBAE9DB824A639F9F9D59926A7848397CAFD6`
  - Jar SHA256: `E3406BFB5484AA7A7A1F15F4C93440194034E7851A5218DA164F4370ABE9A25D`

下一刀建议:

1. 继续做国家领袖运营面趋势和阻塞分组，保持 web 为情报面、游戏内 GUI/命令为操作面。
2. 如需更强效果证据，可继续补 World 侧 `playSound/spawnParticle` 计数，覆盖迁移完成、强制迁移完成和刷新粒子。
3. 给 claim lookup、map render、SQL flush、browser snapshot 设计性能预算门禁。

### 2026-06-10 HUD 契约扫描纳入 release verify（已完成）

本轮把 HUD resource-command 契约扫描从人工清单升级为发布验证门禁，防止 `src/main/resources/web/map`、静态预览壳 `map/`、测试服 runtime 副本 `test-server-paper-1.21.11/plugins/map/` 再次漂移。当前产品契约仍是资源区块 web HUD 只做情报/筛选/定位，不保留 web 端迁移指挥面；脚本会扫描旧 `resourceCommand|open-resource-command|nationDetailOpenCommand|section-resource-command|resource-command` token，并检查 Browser smoke 已使用 `commandUiRemoved=true`，且不再输出旧 `confirmUi=true`。

- 新增/更新:
  - `scripts/check-map-hud-contract.ps1`
  - `scripts/verify-starcore-release.ps1`
  - `scripts/build-latest-verification-summary.ps1`
  - `docs/LATEST_VERIFICATION_SUMMARY.md`
  - `docs/RELEASE_CHECKLIST_2026-06-05.md`
  - `docs/OPERATIONS_RUNBOOK_2026-06-05.md`
  - `docs/MODULE_PLAN.md`
- 发布门禁:
  - 新增 `check-map-hud-contract.ps1`，可单独输出 `target/map-hud-contract-checks/check-*/map-hud-contract-summary.json`。
  - `verify-starcore-release.ps1` 现在在 `mvn package` 后自动执行 Map HUD contract check。
  - 默认要求三份 map 资产根都存在并通过；如特殊环境没有镜像目录，可显式使用 `-AllowMissingHudMirrorRoots`。
  - `build-latest-verification-summary.ps1` 会把最新 HUD 契约检查写入 `docs/LATEST_VERIFICATION_SUMMARY.md`。
- 验证:
  - `.\scripts\check-map-hud-contract.ps1 -ProjectRoot . -RequireMirrorRoots`: PASS on 2026-06-10 12:20 +08:00
  - `.\scripts\verify-starcore-release.ps1 -IncludeSmoke -ProtectorApiSmoke -BrowserSmoke`: PASS on 2026-06-10 12:22 +08:00
  - HUD contract summary: `D:\qwq\项目\mapadd\starcore\target\map-hud-contract-checks\check-20260610-122140\map-hud-contract-summary.json`
  - 三份 map 资产扫描: source/static-preview/runtime-map 均 `matches=0`
  - Browser smoke contract: `commandUiRemoved=true` 存在，旧 `confirmUi=true` 不存在
  - Smoke summary: `D:\qwq\项目\mapadd\starcore\target\smoke-harness-20260610-122149\smoke-summary.json`
  - Smoke marker: `STARCORE_SMOKE_PASS ... migration=gui+mined:world:141:141+forced:world:141:142+feedbackSound:5 ... webClaim=command:0d78b088 ... viewer=ok ... mapSummary=5 territory polygon(s), 0 player marker(s), 1 resource district marker(s)`
  - Browser smoke: PASS, `nationDetail=true nationAction=true recentLog=5 resourceAction=player-offline commandUiRemoved=true`
  - Jar SHA256: `E3406BFB5484AA7A7A1F15F4C93440194034E7851A5218DA164F4370ABE9A25D`

下一刀建议:

1. 继续做国家领袖运营面趋势和阻塞分组，保持 web 为情报面、游戏内 GUI/命令为操作面。
2. 如需更强效果证据，可继续补 World 侧 `playSound/spawnParticle` 计数，覆盖迁移完成、强制迁移完成和刷新粒子。
3. 给 claim lookup、map render、SQL flush、browser snapshot 设计性能预算门禁。

### 2026-06-10 资源区块 feedback Paper smoke 证据（已完成）

本轮继续收口 P1“资源区块管理闭环 / 游戏内效果”的验证证据，把 Paper smoke 的迁移流程接入声音反馈可观测计数。smoke harness 的代理玩家现在会记录 `Player.playSound(...)` 调用次数，迁移 smoke 完成时要求至少观测到 5 次声音调用，并把结果写入 `STARCORE_SMOKE_PASS` 的 `migration=...+feedbackSound:5` 字段。这样可以证明生产 `BukkitResourceDistrictFeedbackSupport` 在真实 Paper 流程中被调用，而不是只停留在配置解析和单测 mock 事件层。

- 新增/更新:
  - `scripts/smoke-starcore-paper-integration.ps1`
- 后端验证思路:
  - 不把生产逻辑改成测试专用形状。
  - 不依赖客户端画面像素或粒子渲染截图。
  - 通过 smoke harness 的 `Player` 代理记录平台 API 调用，验证语义事件已经到达 Bukkit adapter。
  - 当前覆盖迁移开始、迁移目标选定、资源矿物挖掘和第二次迁移流程中的玩家可听见声音反馈；迁移完成/强制完成仍由真实 World 侧播放声音。
- 验证:
  - `mvn -q test`: PASS on 2026-06-10 12:09 +08:00
  - `mvn -q -DskipTests package`: PASS on 2026-06-10 12:09 +08:00
  - `.\scripts\smoke-starcore-paper-integration.ps1 -TimeoutSeconds 360 -ProtectorApiSmoke -BrowserSmoke`: PASS on 2026-06-10 12:10 +08:00
  - Smoke marker: `STARCORE_SMOKE_PASS ... migration=gui+mined:world:208:208+forced:world:208:209+feedbackSound:5 ... webClaim=command:31eee271 ... viewer=ok ... mapSummary=5 territory polygon(s), 0 player marker(s), 1 resource district marker(s)`
  - WebClaim smoke: PASS, `provider=MockProtectorSmoke chunk=216,216 bounds=3456,3456->3487,3487`
  - Browser smoke: PASS, `nationDetail=true nationAction=true recentLog=5 resourceAction=player-offline commandUiRemoved=true`
  - Jar: `D:\qwq\项目\mapadd\starcore\target\starcore-0.1.0-SNAPSHOT.jar`
  - Jar size: `17682762` bytes
  - Jar SHA256: `E3406BFB5484AA7A7A1F15F4C93440194034E7851A5218DA164F4370ABE9A25D`

下一刀建议:

1. 如需更强效果证据，可继续补 World 侧 `playSound/spawnParticle` 计数，覆盖迁移完成、强制迁移完成和刷新粒子。
2. 继续做国家领袖运营面趋势和阻塞分组，保持 web 为情报面、游戏内 GUI/命令为操作面。

### 2026-06-10 Paper smoke 国库基线修复（已完成）

本轮接上一刀的 smoke 失败继续收口，修复 `scripts/smoke-starcore-paper-integration.ps1` 对国库余额从 0 开始的隐含假设。资源区块 `ensureDistricts(nation)` 会在首次创建区块时按生物群系丰富度、生成矿物数量和配置化国库收入规则结算资源产出，因此 smoke 不能硬编码期望余额为 `2400.50`。现在 smoke 会先读取当前国库基线，再严格校验 deposit/withdraw 的净增量为 `+2400.50`，既保留资源区块真实自动收入，也继续验证 TreasuryService 读写行为。

- 新增/更新:
  - `scripts/smoke-starcore-paper-integration.ps1`
- 修复:
  - `STARCORE_SMOKE_FAIL unexpected treasury balance: 2806.50`
  - 原因: 资源区块首次刷新已经给国库结算了一笔配置化资源收入，旧 smoke 把国库余额误认为必须从 0 开始。
  - 处理: 改为 `treasuryBaseline + 2500.75 - 100.25` 的增量断言，避免绑定某个固定生物群系、矿物生成数量或收入配置。
- 验证:
  - `mvn -q test`: PASS on 2026-06-10 12:01 +08:00
  - `mvn -q -DskipTests package`: PASS on 2026-06-10 12:01 +08:00
  - `.\scripts\smoke-starcore-paper-integration.ps1 -TimeoutSeconds 360 -ProtectorApiSmoke -BrowserSmoke`: PASS on 2026-06-10 12:02 +08:00
  - Smoke marker: `STARCORE_SMOKE_PASS ... districts=1 ... migration=gui+mined:world:252:252+forced:world:252:253 ... webClaim=command:1bd6f5d8 ... viewer=ok ... mapSummary=5 territory polygon(s), 0 player marker(s), 1 resource district marker(s)`
  - WebClaim smoke: PASS, `provider=MockProtectorSmoke chunk=260,260 bounds=4160,4160->4191,4191`
  - Browser smoke: PASS, `nationDetail=true nationAction=true recentLog=5 resourceAction=player-offline commandUiRemoved=true`
  - Jar: `D:\qwq\项目\mapadd\starcore\target\starcore-0.1.0-SNAPSHOT.jar`
  - Jar size: `17682762` bytes
  - Jar SHA256: `E3406BFB5484AA7A7A1F15F4C93440194034E7851A5218DA164F4370ABE9A25D`

下一刀建议:

1. 如需继续加厚效果证据，可补 World 侧 `playSound/spawnParticle` 计数。
2. 继续做国家领袖运营面趋势和阻塞分组，保持 web 为情报面、游戏内 GUI/命令为操作面。
3. 把 HUD resource-command 契约扫描纳入 release verify，防止三份 web 资产再次漂移。

### 2026-06-10 资源区块游戏内反馈配置化（已完成）

本轮按 P1“资源区块管理闭环 / 游戏内效果”继续推进，把资源区块迁移、刷新和采集的声音/粒子反馈做成配置化语义 profile。代码只触发 `migration-started`、`migration-target-selected`、`migration-completed`、`migration-completed-forced`、`migration-blocked`、`resource-refreshed`、`resource-mined` 这些业务事件；具体 Sound、Particle、数量、扩散半径、音量和音高都由 `config.yml` 控制，避免把表现硬编码进迁移规则。

- 新增/更新:
  - `src/main/java/dev/starcore/starcore/core/config/ConfigurationService.java`
  - `src/main/java/dev/starcore/starcore/module/nation/resource/NativeNationResourceDistrictService.java`
  - `src/main/resources/config.yml`
  - `src/test/java/dev/starcore/starcore/core/config/ConfigurationServiceResourceFeedbackConfigTest.java`
  - `src/test/java/dev/starcore/starcore/module/nation/resource/NativeNationResourceDistrictServiceFlowTest.java`
- 后端:
  - 新增 `ConfigurationService.ResourceDistrictFeedbackProfile` 与 `nationResourceFeedbackProfile(...)`。
  - 支持全局开关、默认 profile、单事件覆盖、名称归一化、数值裁剪。
  - `NativeNationResourceDistrictService` 新增 `ResourceDistrictFeedbackSupport` 边界。
  - 正式运行使用 Bukkit `Sound` / `Particle` 解析并自动忽略无效名称。
  - 迁移开始、目标选定、迁移完成、强制迁移、阻塞状态、资源刷新、资源矿物采集都会发出语义反馈事件。
- 配置:
  - 新增 `nation.resources.feedback.*` 中文注释配置。
  - 默认效果偏克制，粒子数量上限由配置读取层裁剪到 80，避免高频资源操作拖慢 TPS。
  - 服主可单独关闭全局反馈，或只覆盖某个事件的声音/粒子。
- 测试:
  - `ConfigurationServiceResourceFeedbackConfigTest` 锁住空配置安全降级、事件覆盖默认值、全局关闭/单事件开启和数值裁剪。
  - `NativeNationResourceDistrictServiceFlowTest` 锁住迁移开始、目标选定、完成和阻塞反馈语义事件。
  - `ConfigDefaultsCoverageTest` 继续锁住新增固定配置路径在默认 `config.yml` 中存在。
- 验证:
  - `mvn -q -DskipTests compile`: PASS
  - `mvn -q "-Dtest=ConfigurationServiceResourceFeedbackConfigTest,ConfigDefaultsCoverageTest,NativeNationResourceDistrictServiceFlowTest" test`: PASS
  - 后续全量 test/package 与 Paper+Browser smoke 见上一节，均 PASS

下一刀建议:

1. 如需更强画面效果证据，可补 World 侧粒子计数或截图 smoke。
2. 继续做国家领袖运营面趋势和阻塞分组，保持 web 为情报面、游戏内 GUI/命令为操作面。

### 2026-06-10 ResourceCommandHandler 抽取

本轮继续瘦 `StarCoreCommand`，把 `/sc rsc` / `/starcore resource` 资源命令族搬到 `ResourceCommandHandler`。资源库存、资源区块列表、资源区块查看、资源区块迁移、管理员资源 grant/consume 和资源命令 Tab 补全现在都集中在 handler 内；`StarCoreCommand` 只保留 root 分发与 `resourceCommands.handle/complete(...)` 薄委托，后续扩 RPG 资源玩法时不要把逻辑塞回主命令类。

- 新增/更新:
  - `src/main/java/dev/starcore/starcore/command/ResourceCommandHandler.java`
  - `src/main/java/dev/starcore/starcore/command/StarCoreCommand.java`
  - `src/test/java/dev/starcore/starcore/command/ResourceCommandHandlerTest.java`
- 后端:
  - `ResourceCommandHandler.handle(...)` 接管资源命令执行入口。
  - `ResourceCommandHandler.complete(...)` 接管资源命令补全入口。
  - handler 复用 `StarCoreCommandAliases.normalizeResourceSubcommand(...)`，资源别名规则仍然只有一个来源。
  - 资源库存 `status`、资源区块 `districts/inspect/migrate`、管理员 `grant/consume` 全部从 `StarCoreCommand` 搬出。
  - 资源 grant/consume 仍写入 `resource.granted` / `resource.consumed` 事件和结构化 ledger context。
  - 资源区块详情仍复用 `NationOperationalSupport`、`NationResourceDistrictOperationalSupport`、`NationResourceDistrictViewSupport`，避免命令、GUI、网页情报面语义漂移。
  - `StarCoreCommand.java` 从 2917 行降到 2536 行。
- 测试:
  - 新增 `ResourceCommandHandlerTest`，覆盖核心服务缺失、资源子命令补全、国家/资源区块/资源类型/数量补全、资源 grant 记账 context、资源区块 inspect 详情输出。
  - `StarCoreCommandShortAliasDispatchTest` 继续锁住 `/sc rsc d/i/m` 短命令真实分发。
  - `StarCoreCommandTabCompletionTest` 继续锁住 `/sc rsc` 和中文资源命令补全。
- 验证:
  - `mvn -q -DskipTests compile`: PASS on 2026-06-10 10:40 +08:00
  - `mvn -q "-Dtest=ResourceCommandHandlerTest,StarCoreCommandShortAliasDispatchTest,StarCoreCommandTabCompletionTest,StarCoreCommandAliasesTest" test`: PASS on 2026-06-10 10:39 +08:00
  - `mvn -q clean test`: PASS on 2026-06-10 10:45 +08:00，干净报告为 69 个测试类 / 277 tests / 0 failures / 0 errors / 0 skipped
  - `mvn -q -DskipTests package`: PASS on 2026-06-10 10:45 +08:00
  - `git diff --check`: PASS on 2026-06-10 10:46 +08:00
  - Jar: `D:\qwq\项目\mapadd\starcore\target\starcore-0.1.0-SNAPSHOT.jar`
  - Jar size: `17675581` bytes
  - Jar SHA256: `6A6EECF83C49D3C642C788D777699FB649EDEC86C43149F4E8130B714E9B5B97`

### 2026-06-10 StarCoreCommandAliases 抽取

本轮开始瘦 `StarCoreCommand`，把根命令候选、中英文短别名和各命令族 subcommand 归一化逻辑集中到 `StarCoreCommandAliases`。`StarCoreCommand` 现在只保留薄委托，后续新增 RPG 服常用中文一字命令、英文短命令或别名时优先改 alias support，不要继续把大段 switch 塞回主命令类。

- 新增/更新:
  - `src/main/java/dev/starcore/starcore/command/StarCoreCommandAliases.java`
  - `src/main/java/dev/starcore/starcore/command/StarCoreCommand.java`
  - `src/test/java/dev/starcore/starcore/command/StarCoreCommandAliasesTest.java`
- 后端:
  - `StarCoreCommandAliases.rootSuggestions()` 接管根命令补全候选，包括英文短入口和中文入口
  - `StarCoreCommandAliases.normalizeRoot(...)` 接管 `/sc` 根命令归一化
  - `normalizeNation/Economy/Resolution/Government/Map/Diplomacy/Treasury/Policy/Resource/Technology/War/OfficerSubcommand(...)` 全部迁入 support
  - `normalizeSimple(...)` 和 `normalizeToken(...)` 迁入 support，避免主命令类继续维护底层 token 规则
  - `StarCoreCommand` 保留同名薄委托，减少调用点改动面，行为保持不变
  - `StarCoreCommand.java` 从 3026 行降到 2917 行
- 测试:
  - `StarCoreCommandAliasesTest` 覆盖根候选不可变、中英文 root/subcommand 归一化、大小写/空白 token 归一化
  - `StarCoreCommandTabCompletionTest` 继续锁住中文短命令和补全行为
  - `StarCoreCommandShortAliasDispatchTest` 继续锁住短命令真实分发
- 验证:
  - `mvn -q -DskipTests compile`: PASS on 2026-06-10 10:02 +08:00
  - `mvn -q "-Dtest=StarCoreCommandAliasesTest,StarCoreCommandTabCompletionTest,StarCoreCommandShortAliasDispatchTest" test`: PASS on 2026-06-10 10:02 +08:00
  - `mvn -q test`: PASS on 2026-06-10 10:02 +08:00
  - `mvn -q -DskipTests package`: PASS on 2026-06-10 10:02 +08:00
  - `git diff --check`: PASS on 2026-06-10 10:02 +08:00
  - Jar: `D:\qwq\项目\mapadd\starcore\target\starcore-0.1.0-SNAPSHOT.jar`
  - Jar size: `17669458` bytes
  - Jar SHA256: `77CE2D169AE1633AACC96F1EB17AF47B5420C37EF51C84C58DA7200D8C8ED6D4`

### 2026-06-10 MapWebServer 抽取

本轮继续瘦 `MapModule`，把网页地图 `HttpServer` 生命周期、executor 创建/关闭和路由注册集中到 `MapWebServer`。`MapModule` 现在只负责按配置组装 `Settings`、传入各个 handler 方法引用并记录启动日志；后续新增/调整网页地图路由时优先改 `MapWebServer.Routes`，不要把 server 创建、context 注册和 executor 清理逻辑继续堆回主模块。

- 新增/更新:
  - `src/main/java/dev/starcore/starcore/module/map/MapWebServer.java`
  - `src/main/java/dev/starcore/starcore/module/map/MapModule.java`
  - `src/test/java/dev/starcore/starcore/module/map/MapWebServerTest.java`
- 后端:
  - `MapWebServer.start(...)` 接管 map web 启用判断、`HttpServer.create(...)`、cached thread pool 创建、路由注册、executor 绑定和 server start
  - `MapWebServer.stop()` 接管 `HttpServer.stop(0)` 与 executor `shutdownNow()`，并清空引用
  - `MapWebServer.executor()` 继续暴露当前 executor，保持地形瓦片磁盘缓存异步写入复用 Web executor 的旧行为
  - `MapModule.disable(...)` 现在只清理模块状态后委托 `webServer.stop()`
  - `MapModule.startWebServer()` 只组装 `Settings` / `Routes` 并保留启动日志与 access-secret 警告
  - `MapModule.java` 从 2285 行降到 2278 行
- 测试:
  - `MapWebServerTest` 覆盖禁用时不创建 server、启动注册全部地图路由、executor 暴露、二次启动忽略、stop 清理和启动失败回滚
  - `MapModuleViewerSnapshotContractTest` 继续锁住地图 snapshot / health / viewer 契约
- 验证:
  - `mvn -q -DskipTests compile`: PASS on 2026-06-10 09:20 +08:00
  - `mvn -q "-Dtest=MapWebServerTest,MapModuleViewerSnapshotContractTest" test`: PASS on 2026-06-10 09:20 +08:00
  - `mvn -q test`: PASS on 2026-06-10 09:20 +08:00
  - `mvn -q -DskipTests package`: PASS on 2026-06-10 09:20 +08:00
  - `git diff --check`: PASS on 2026-06-10 09:20 +08:00
  - Jar: `D:\qwq\项目\mapadd\starcore\target\starcore-0.1.0-SNAPSHOT.jar`
  - Jar size: `17667026` bytes
  - Jar SHA256: `0E6418197A753E9A8D30B03F14973A1F9CEA841EC61C324C7234F6E5F8C316A6`

### 2026-06-10 MapHttpRequestParser 抽取

本轮继续瘦 `MapModule`，把网页地图 query/form/POST body/扁平 JSON 请求参数解析集中到 `MapHttpRequestParser`。`MapModule` 现在不再保存低层 `URLDecoder`、form body 和简易 JSON 字符串解析逻辑；网页圈地、资源区块迁移、头像和财政接口统一复用 parser，后续新增地图 Web API 时不需要再手写一套请求参数解析。

- 新增/更新:
  - `src/main/java/dev/starcore/starcore/module/map/MapHttpRequestParser.java`
  - `src/main/java/dev/starcore/starcore/module/map/MapModule.java`
  - `src/test/java/dev/starcore/starcore/module/map/MapHttpRequestParserTest.java`
- 后端:
  - `MapHttpRequestParser.query(...)` 接管 URI query 解码和无效片段跳过
  - `MapHttpRequestParser.requestParams(...)` 接管 query + POST body 合并
  - form POST body 保留旧行为: body 参数不覆盖 query 参数
  - JSON POST body 保留旧行为: JSON 字段覆盖 query 参数，支持字符串转义和 primitive 值文本化
  - `MapModule` 清理 `requestParams(...)`、`parseQuery(...)`、`parseFormBody(...)`、`parseFlatJsonObject(...)` 和 JSON 小 record
  - `MapModule.java` 从 2435 行降到 2285 行
- 测试:
  - `MapHttpRequestParserTest` 覆盖 query 解码/非法片段跳过、form POST 合并规则、JSON 覆盖规则、转义字符串、primitive 值、空 body 和坏 JSON 兜底
  - `MapModuleViewerSnapshotContractTest` 继续锁住地图 snapshot / health / viewer 契约
- 验证:
  - `mvn -q -DskipTests compile`: PASS on 2026-06-10 08:41 +08:00
  - `mvn -q "-Dtest=MapHttpRequestParserTest,MapModuleViewerSnapshotContractTest" test`: PASS on 2026-06-10 08:41 +08:00
  - `mvn -q test`: PASS on 2026-06-10 08:41 +08:00
  - `mvn -q -DskipTests package`: PASS on 2026-06-10 08:41 +08:00
  - `git diff --check`: PASS on 2026-06-10 08:41 +08:00
  - Jar: `D:\qwq\项目\mapadd\starcore\target\starcore-0.1.0-SNAPSHOT.jar`
  - Jar size: `17662274` bytes
  - Jar SHA256: `CBA4EAEBD144A11189CD7430354C1FD14DBC24E3362697C50FC5E3C44F8756C3`

### 2026-06-10 MapHttpResponses 抽取

本轮继续瘦 `MapModule`，把网页地图 HTTP 响应写出、JSON/text/bytes 输出、附件响应、CORS header 和 OPTIONS preflight 规则集中到 `MapHttpResponses`。`MapModule` 现在只保留同名薄委托，后续地图 Web API、资源指挥、财政导出、头像/地形响应扩展优先改 HTTP support，避免每个路由各写一套缓存头和 CORS。

- 新增/更新:
  - `src/main/java/dev/starcore/starcore/module/map/MapHttpResponses.java`
  - `src/main/java/dev/starcore/starcore/module/map/MapModule.java`
  - `src/test/java/dev/starcore/starcore/module/map/MapHttpResponsesTest.java`
- 后端:
  - `MapHttpResponses.writeJson(...)` 接管 JSON content type、no-store cache 和 CORS
  - `MapHttpResponses.writeText(...)` / `writeBytes(...)` 接管纯文本、二进制响应、短缓存和 no-store 策略
  - `MapHttpResponses.writeClaimResponse(...)` 接管财政 CSV/JSON 导出类响应与 attachment filename 清理
  - `MapHttpResponses.handleCorsPreflight(...)` 接管 OPTIONS 204、允许方法/请求头和关闭 exchange
  - `MapModule` 保留薄委托，减少路由改动面，后续可继续清理委托层
  - `MapModule.java` 从 2477 行降到 2435 行
- 测试:
  - `MapHttpResponsesTest` 覆盖 JSON no-store 与 allow-origin、bytes 短缓存与 wildcard CORS、导出 filename 清理、OPTIONS preflight 和非 OPTIONS 不拦截
  - `MapModuleViewerSnapshotContractTest` 继续锁住地图 snapshot / health / viewer 契约
- 验证:
  - `mvn -q -DskipTests compile`: PASS on 2026-06-10 08:30 +08:00
  - `mvn -q "-Dtest=MapHttpResponsesTest,MapModuleViewerSnapshotContractTest" test`: PASS on 2026-06-10 08:30 +08:00
  - `mvn -q test`: PASS on 2026-06-10 08:30 +08:00
  - `mvn -q -DskipTests package`: PASS on 2026-06-10 08:30 +08:00
  - `git diff --check`: PASS on 2026-06-10 08:30 +08:00
  - Jar: `D:\qwq\项目\mapadd\starcore\target\starcore-0.1.0-SNAPSHOT.jar`
  - Jar size: `17660814` bytes
  - Jar SHA256: `AD86104D771A07BF2031B1D6BE07D269B72F64DE62261C1D750083092A9D2CD1`

### 2026-06-10 MapStaticFileHandler 抽取

本轮继续瘦 `MapModule`，把网页地图静态文件服务从内部 `StaticHandler` 抽到 `MapStaticFileHandler`。`MapModule` 现在只负责注册根路由和解析网页导出目录；静态文件的路径安全、目录/缺失处理、内容类型和缓存头全部集中到 handler，后续地图 Web 前端资源、缓存策略或安全规则扩展不再继续塞进主模块。

- 新增/更新:
  - `src/main/java/dev/starcore/starcore/module/map/MapStaticFileHandler.java`
  - `src/main/java/dev/starcore/starcore/module/map/MapModule.java`
  - `src/test/java/dev/starcore/starcore/module/map/MapStaticFileHandlerTest.java`
- 后端:
  - `MapStaticFileHandler.response(...)` 接管根路径 `index.html`、静态资源读取、扩展名 content type 和 cache-control
  - handler 使用绝对归一化 root 与 target 校验，阻止 `../` 与反斜杠路径穿越
  - 缺失文件、目录请求、缺失首页和越界请求统一返回 `404 Not Found`
  - HTML 使用 `no-store, no-cache, must-revalidate`，JS/CSS/JSON/图片等静态资源默认使用 `public, max-age=60`
  - `MapModule` 清理内部 `StaticHandler` 和 `HttpHandler` 导入
  - `MapModule.java` 从 2531 行降到 2477 行
- 测试:
  - `MapStaticFileHandlerTest` 覆盖首页、JS content type/cache、缺失文件、目录请求、缺失首页、正反斜杠路径穿越和未知扩展名
  - `MapModuleViewerSnapshotContractTest` 继续锁住地图 snapshot / health / viewer 契约
- 验证:
  - `mvn -q -DskipTests compile`: PASS on 2026-06-10 07:32 +08:00
  - `mvn -q "-Dtest=MapStaticFileHandlerTest,MapModuleViewerSnapshotContractTest" test`: PASS on 2026-06-10 07:32 +08:00
  - `mvn -q test`: PASS on 2026-06-10 07:32 +08:00
  - `mvn -q -DskipTests package`: PASS on 2026-06-10 07:32 +08:00
  - `git diff --check`: PASS on 2026-06-10 07:32 +08:00
  - Jar: `D:\qwq\项目\mapadd\starcore\target\starcore-0.1.0-SNAPSHOT.jar`
  - Jar size: `17658853` bytes
  - Jar SHA256: `03AFAFBA6566DAE222590588581D8AA2130E919143BF1BAE5C6FC1FB7434A66D`

### 2026-06-10 MapSseBroadcaster 抽取

本轮继续瘦 `MapModule`，把网页地图 SSE 连接列表、ready/snapshot 事件写入、过期访问关闭、写入失败移除和关闭全部客户端的逻辑抽到 `MapSseBroadcaster`。`MapModule` 现在只负责 SSE HTTP 入口的 CORS、开关、访问鉴权、响应头和初始 snapshot 生成，后续心跳、重连策略、客户端统计和性能优化优先改 broadcaster。

- 新增/更新:
  - `src/main/java/dev/starcore/starcore/module/map/MapSseBroadcaster.java`
  - `src/main/java/dev/starcore/starcore/module/map/MapModule.java`
  - `src/test/java/dev/starcore/starcore/module/map/MapSseBroadcasterTest.java`
- 后端:
  - `MapSseBroadcaster.register(...)` 接管 SSE client 注册、`ready` 事件和初始 `snapshot` 事件写入
  - `MapSseBroadcaster.broadcastSnapshots(...)` 接管过期访问关闭、snapshot 广播、写入失败移除和已投递数量统计
  - `MapSseBroadcaster.closeAll()` 接管模块关闭时的 SSE 连接清理
  - `MapModule.refreshCacheAndBroadcast(...)` 现在只刷新缓存、检查 SSE 开关并委托 broadcaster 广播
  - `MapModule.buildHealthJson(...)` 通过 `sseBroadcaster.clientCount()` 输出当前 SSE 客户端数量
  - `MapModule.java` 从 2583 行降到 2531 行
- 测试:
  - `MapSseBroadcasterTest` 覆盖注册 ready/snapshot、过期客户端关闭、换行清理、写入失败移除和 closeAll
  - `MapModuleViewerSnapshotContractTest` 继续锁住地图 snapshot / health / viewer 契约
- 验证:
  - `mvn -q -DskipTests compile`: PASS on 2026-06-10 07:15 +08:00
  - `mvn -q "-Dtest=MapSseBroadcasterTest,MapModuleViewerSnapshotContractTest" test`: PASS on 2026-06-10 07:15 +08:00
  - `mvn -q test`: PASS on 2026-06-10 07:16 +08:00
  - `mvn -q -DskipTests package`: PASS on 2026-06-10 07:17 +08:00
  - `git diff --check`: PASS on 2026-06-10 07:17 +08:00
  - Jar: `D:\qwq\项目\mapadd\starcore\target\starcore-0.1.0-SNAPSHOT.jar`
  - Jar size: `17657488` bytes
  - Jar SHA256: `FE11A5D6764989718E2BB19403DCB4F351F06E5D7372FB7BB9BC75BB895D2D0F`

### 2026-06-09 MapViewerJsonWriter 抽取

本轮继续瘦 `MapModule`，把访问玩家 viewer JSON 的余额、国家、等级/经验、城邦/资源区块上限、在线位置和角色字段输出抽到 `MapViewerJsonWriter`。`MapModule` 现在只负责从 Bukkit 在线玩家、玩家档案、经济和 NationService 准备 `ViewerDetails`，字段渲染与转义集中在 writer，后续网页地图“访问玩家详细信息”扩展不再继续塞进主模块。

- 新增/更新:
  - `src/main/java/dev/starcore/starcore/module/map/MapViewerJsonWriter.java`
  - `src/main/java/dev/starcore/starcore/module/map/MapModule.java`
  - `src/test/java/dev/starcore/starcore/module/map/MapViewerJsonWriterTest.java`
- 后端:
  - `MapViewerJsonWriter.toJson(...)` 接管 viewer JSON 输出，包括 `playerId/playerName/balance/nationId/nationName/nationKind/founderName/government/role`
  - writer 接管国家等级/经验、claim 上限、城邦上限、资源区块上限、在线状态、世界坐标和 founder 字段输出
  - `MapViewerJsonWriter.ViewerDetails` 作为轻量数据边界，避免 writer 直接依赖 Bukkit、经济或 NationService
  - `MapModule.viewerJson(...)` 现在只负责构造 `ViewerDetails` 并委托 writer
  - `MapModule.java` 从 2633 行降到 2583 行
- 测试:
  - `MapViewerJsonWriterTest` 覆盖 null viewer、余额、国家角色、等级进度、资源区块上限、在线坐标、founder 和字符串转义
  - `MapSnapshotJsonWriterTest` 与 `MapModuleViewerSnapshotContractTest` 继续锁住真实 snapshot 里的访问玩家字段
- 验证:
  - `mvn -q -DskipTests compile`: PASS on 2026-06-09 23:28 +08:00
  - `mvn -q "-Dtest=MapViewerJsonWriterTest,MapSnapshotJsonWriterTest,MapModuleViewerSnapshotContractTest" test`: PASS on 2026-06-09 23:29 +08:00
  - `mvn -q test`: PASS on 2026-06-09 23:30 +08:00
  - `mvn -q -DskipTests package`: PASS on 2026-06-09 23:31 +08:00
  - `git diff --check`: PASS on 2026-06-09 23:31 +08:00
  - Jar: `D:\qwq\项目\mapadd\starcore\target\starcore-0.1.0-SNAPSHOT.jar`
  - Jar size: `17655708` bytes
  - Jar SHA256: `D4111AA4AE9B8BEC367FC67A390A5CFD1686A547C044A95FDB47D79DB64CA2EE`

### 2026-06-09 MapSnapshotJsonWriter 抽取

本轮继续瘦 `MapModule`，把地图 snapshot JSON 的顶层渲染、access、summary、worlds、terrain 插槽、diplomacy matrix、layers / territories / markers / metadata 字段输出抽到 `MapSnapshotJsonWriter`。`MapModule` 现在只保留快照视图过滤、viewer JSON 数据准备、terrain metadata 回调和 HTTP/SSE 外壳，后续网页地图字段扩展优先改 writer 或专用 support，不再继续把字符串拼接堆进 `MapModule`。

- 新增/更新:
  - `src/main/java/dev/starcore/starcore/module/map/MapSnapshotJsonWriter.java`
  - `src/main/java/dev/starcore/starcore/module/map/MapModule.java`
  - `src/test/java/dev/starcore/starcore/module/map/MapSnapshotJsonWriterTest.java`
- 后端:
  - `MapSnapshotJsonWriter.toJson(...)` 接管 snapshot 顶层 JSON、summary、worlds、terrain JSON 插槽、diplomacy relations、layer/territory/marker/metadata 渲染
  - `MapSnapshotJsonWriter.accessJson(...)` 接管 public/full/allied access JSON 渲染
  - `MapModule.toJson(...)` 现在只是一层委托，传入 public worlds、viewer JSON、terrain JSON 和最新外交关系
  - `MapModule` 保留 `viewerJson(...)`，因为 viewer 需要 Bukkit 在线玩家、经济和 NationService 数据准备
  - `MapModule.java` 从 2798 行降到 2633 行
- 测试:
  - `MapSnapshotJsonWriterTest` 覆盖私有访问 snapshot worlds、public worlds、access scope、viewer JSON 插槽、terrain JSON 插槽、summary、diplomacy matrix、layer/territory/marker/metadata 转义
  - `MapModuleViewerSnapshotContractTest` 继续锁住真实地图 snapshot/viewer relation/terrain metadata 契约
- 验证:
  - `mvn -q -DskipTests compile`: PASS on 2026-06-09 22:55 +08:00
  - `mvn -q "-Dtest=MapSnapshotJsonWriterTest,MapModuleViewerSnapshotContractTest" test`: PASS on 2026-06-09 22:56 +08:00
  - `mvn -q test`: PASS on 2026-06-09 22:56 +08:00
  - `mvn -q -DskipTests package`: PASS on 2026-06-09 22:57 +08:00
  - `git diff --check`: PASS on 2026-06-09 22:57 +08:00
  - Jar: `D:\qwq\项目\mapadd\starcore\target\starcore-0.1.0-SNAPSHOT.jar`
  - Jar size: `17651786` bytes
  - Jar SHA256: `CA0595AC11B7666FBEF75DC2B46A505170B16FCE61577021785CBBB6358FD953`

### 2026-06-09 NationMapMetadataSupport 抽取

本轮继续瘦 `MapModule`，把国家领地 polygon metadata 的基础国家字段、最近国家事件字段和财政摘要字段抽到 `NationMapMetadataSupport`。`MapModule` 现在只负责从 nation/event/treasury/resource district 服务拿数据并委托 support 写字段，后续 RPG 地图国家面板、财政面板、事件面板扩展优先改 support。

- 新增/更新:
  - `src/main/java/dev/starcore/starcore/module/map/NationMapMetadataSupport.java`
  - `src/main/java/dev/starcore/starcore/module/map/MapModule.java`
  - `src/test/java/dev/starcore/starcore/module/map/NationMapMetadataSupportTest.java`
- 后端:
  - `NationMapMetadataSupport.baseMetadata(...)` 接管国家身份、政体、等级/经验、成员数、城邦数、资源区块数、claim 数、颜色、关系等 polygon metadata
  - `NationMapMetadataSupport.appendRecentEvents(...)` 接管 `recentEvent*` 字段和已解析资源区块 marker id 写入
  - `NationMapMetadataSupport.appendFinanceSummary(...)` 接管 `financeEvent*`、财政分类合计、净额和 treasury balance 写入
  - `MapModule.polygonFor(...)` 现在只负责关系/颜色、概览获取、财政/事件委托和 polygon 坐标组装
  - `MapModule.java` 从 2909 行降到 2798 行
- 测试:
  - `NationMapMetadataSupportTest` 覆盖基础国家 metadata、最近事件 resource id、财政合计和最近 3 条财政事件输出
  - `MapModuleViewerSnapshotContractTest` 继续锁住真实地图 snapshot/viewer relation 契约
- 验证:
  - `mvn -q -DskipTests compile`: PASS on 2026-06-09 20:32 +08:00
  - `mvn -q "-Dtest=NationMapMetadataSupportTest,MapModuleViewerSnapshotContractTest" test`: PASS on 2026-06-09 20:34 +08:00
  - `mvn -q test`: PASS on 2026-06-09 20:35 +08:00
  - `mvn -q -DskipTests package`: PASS on 2026-06-09 20:35 +08:00
  - `git diff --check`: PASS on 2026-06-09 20:35 +08:00
  - Jar: `D:\qwq\项目\mapadd\starcore\target\starcore-0.1.0-SNAPSHOT.jar`
  - Jar size: `17647715` bytes
  - Jar SHA256: `A9C735F0CDBBC29F06BABBC2AE0E5BEE4D8B05B46A72CDC50A9213C9D7078E61`

### 2026-06-09 ResourceDistrictMapMetadataSupport 抽取

本轮继续瘦 `MapModule`，把资源区块 marker metadata 的基础字段、产能预报字段和 viewer 迁移命令字段抽到 `ResourceDistrictMapMetadataSupport`。后续资源区块地图面板、RPG 玩家信息面板、迁移按钮状态扩展应该优先改 support，不再把字段继续塞进 `MapModule`。

- 新增/更新:
  - `src/main/java/dev/starcore/starcore/module/map/ResourceDistrictMapMetadataSupport.java`
  - `src/main/java/dev/starcore/starcore/module/map/MapModule.java`
  - `src/test/java/dev/starcore/starcore/module/map/ResourceDistrictMapMetadataSupportTest.java`
- 后端:
  - `ResourceDistrictMapMetadataSupport.baseMetadata(...)` 接管国家身份、国家等级、领地上限、资源区块、产能预报、beacon、刷新时间、迁移目标等 marker metadata
  - `ResourceDistrictMapMetadataSupport.appendViewerCommandMetadata(...)` 接管 viewer 迁移花费、余额缺口、角色、在线状态、按钮状态、阶段/下一步/限制等 metadata
  - `MapModule.markerForResourceDistrict(...)` 现在只负责 nation/district 服务查找、关系/颜色计算、坐标计算和 support 委托
  - `MapModule.appendResourceDistrictViewerMetadata(...)` 现在只负责解析 command state/presentation 并委托 support 写字段
- 测试:
  - `ResourceDistrictMapMetadataSupportTest` 覆盖基础资源区块 metadata 和 viewer command metadata 输出
  - `MapModuleViewerSnapshotContractTest` 继续锁住真实地图 layer 与 viewer relation 场景
- 验证:
  - `mvn -q -DskipTests compile`: PASS on 2026-06-09 20:22 +08:00
  - `mvn -q "-Dtest=ResourceDistrictMapMetadataSupportTest,MapModuleViewerSnapshotContractTest" test`: PASS on 2026-06-09 20:22 +08:00
  - `git diff --check`: PASS on 2026-06-09 20:22 +08:00
  - `mvn -q test`: PASS on 2026-06-09 20:25 +08:00
  - `mvn -q -DskipTests package`: PASS on 2026-06-09 20:25 +08:00
  - Jar: `D:\qwq\项目\mapadd\starcore\target\starcore-0.1.0-SNAPSHOT.jar`
  - Jar size: `17645235` bytes
  - Jar SHA256: `8C7E6A6503369C275358275D5CDC4CEF0A314EBC37665D358C65C4801EDADFC0`

### 2026-06-09 MapResourceDistrictEndpoint 抽取

本轮继续网页端点瘦身，把 `/api/map/resource-district/migrate` 的迁移响应构建、状态码映射、迁移结果 JSON 和网页资源指挥字段从 `MapModule` 抽到 `MapResourceDistrictEndpoint`。`MapModule` 现在只保留 HTTP 外壳、访问鉴权、请求体解析、同步调度、服务/玩家查找和刷新广播回调。

- 新增/更新:
  - `src/main/java/dev/starcore/starcore/module/map/MapResourceDistrictEndpoint.java`
  - `src/main/java/dev/starcore/starcore/module/map/MapModule.java`
  - `src/test/java/dev/starcore/starcore/module/map/MapResourceDistrictEndpointTest.java`
- 后端:
  - `MapResourceDistrictEndpoint` 接管 disabled / player-offline / success 等迁移响应构建
  - endpoint 统一维护迁移结果状态码映射
  - endpoint 统一输出迁移结果 JSON，包括国家概览、资源产能预报、迁移花费、viewer 状态、迁移阶段/下一步/限制等网页字段
  - `MapModule` 清理旧 `resourceDistrictMigrationJson(...)` 和 `resourceDistrictMigrationStatus(...)`
  - `MapModule.buildResourceDistrictMigrationResponse(...)` 保留为薄委托，现有反射契约测试继续可用
  - settings 组装对未初始化的 `onlinePlayerDirectory` 做空兜底，禁用服务路径不会提前 NPE
- 测试:
  - `MapResourceDistrictEndpointTest` 覆盖 disabled 不调用 migrator、离线 viewer 返回 409、成功路径输出运营字段并触发刷新回调
  - `MapModuleViewerSnapshotContractTest` 继续锁住真实资源区块迁移响应 JSON 契约
- 验证:
  - `mvn -q -DskipTests compile`: PASS on 2026-06-09 20:13 +08:00
  - `mvn -q "-Dtest=MapResourceDistrictEndpointTest,MapModuleViewerSnapshotContractTest" test`: PASS on 2026-06-09 20:13 +08:00
  - `git diff --check`: PASS on 2026-06-09 20:13 +08:00
  - `mvn -q test`: PASS on 2026-06-09 20:15 +08:00
  - `mvn -q -DskipTests package`: PASS on 2026-06-09 20:15 +08:00
  - 当前 jar: `target/starcore-0.1.0-SNAPSHOT.jar`
  - 当前 jar 大小: `17643074` bytes
  - 当前 jar SHA256: `2AFCE1709DA0992C8DD4F14CE7968FCE042B6853BCD9EF0F97C0AFBF06E7E9E8`

### 2026-06-09 MapClaimEndpoint 抽取

本轮继续网页端点瘦身，把 `/api/map/claim/preview` 与 `/api/map/claim/request` 的圈地选择参数、预览 JSON、pending 请求、冷却和确认前校验从 `MapModule` 抽到 `MapClaimEndpoint`。`MapModule` 现在只保留 HTTP 外壳、访问鉴权、请求体解析、Bukkit 主线程同步、玩家通知和真实 `NationService` 调用。

- 新增/更新:
  - `src/main/java/dev/starcore/starcore/module/map/MapClaimEndpoint.java`
  - `src/main/java/dev/starcore/starcore/module/map/MapModule.java`
  - `src/test/java/dev/starcore/starcore/module/map/MapClaimEndpointTest.java`
  - `src/test/java/dev/starcore/starcore/module/map/MapModuleViewerSnapshotContractTest.java`
- 后端:
  - `MapClaimEndpoint` 接管 `world/minX/maxX/minZ/maxZ` 参数校验和 `ChunkClaimSelection` 生成
  - 选区超过 `map.web.claim-max-chunks` 时直接返回不可提交预览，避免继续调用 nation preview
  - endpoint 统一渲染网页圈地预览 JSON 与 pricing 明细
  - pricing 明细继续按 `price DESC -> distance DESC -> chunkX -> chunkZ` 排序
  - endpoint 维护 pending web claim、cooldown 和过期清理
  - confirm 前的 pending id 校验、owner 校验、过期校验已移入 endpoint
  - `MapModule` 清理旧 `PendingWebClaim`、`pendingWebClaims`、`webClaimCooldowns`、`claimSelectionFromParams(...)`、`claimPreviewJson(...)` 和 pricing JSON helper
  - `MapModule` 的网页圈地入口继续负责 CORS、POST/开关/访问鉴权、请求体解析、同步调度、玩家通知和真实 `NationService` 调用
- 测试:
  - `MapClaimEndpointTest` 覆盖参数校验、过大选区不调用 preview、pending/cooldown、确认 owner 校验与 pending 消耗
  - `MapModuleViewerSnapshotContractTest` 的价格排序护栏改为直接测试 `MapClaimEndpoint.previewJson(...)`
- 验证:
  - `mvn -q -DskipTests compile`: PASS on 2026-06-09 19:56 +08:00
  - `mvn -q "-Dtest=MapClaimEndpointTest,MapModuleViewerSnapshotContractTest" test`: PASS on 2026-06-09 19:56 +08:00
  - `git diff --check`: PASS on 2026-06-09 19:57 +08:00
  - `mvn -q test`: PASS on 2026-06-09 19:59 +08:00
  - `mvn -q -DskipTests package`: PASS on 2026-06-09 19:59 +08:00
  - 当前 jar: `target/starcore-0.1.0-SNAPSHOT.jar`
  - 当前 jar 大小: `17625531` bytes
  - 当前 jar SHA256: `C83B1E52065FD2FAACC686F2CC568260577103F36F0FE782B2CA1B66D7B066DE`

### 2026-06-09 MapAvatarEndpoint 抽取

本轮继续按“新增网页 API 先 endpoint/support，`MapModule` 做薄路由”的规则推进，把 `/api/map/avatar` 的头像代理、缓存读写、上游回源、旧缓存降级和缓存清理从 `MapModule` 抽到 `MapAvatarEndpoint`。这块后续会服务 RPG 地图玩家信息面板，所以先打干净根基。

- 新增/更新:
  - `src/main/java/dev/starcore/starcore/module/map/MapAvatarEndpoint.java`
  - `src/main/java/dev/starcore/starcore/module/map/MapModule.java`
  - `src/test/java/dev/starcore/starcore/module/map/MapAvatarEndpointTest.java`
- 后端:
  - `MapAvatarEndpoint` 接管 `/api/map/avatar?id=<uuid>` 的 `id` 校验和响应构建
  - 支持按配置 upstream 顺序回源，保留 `{uuid}` / `{uuidNoDash}` 占位符格式化
  - cache enabled 时读写 `cache/avatars/<uuid>.png`
  - 新鲜缓存命中输出 24h max-age，新下载头像输出 1h max-age
  - upstream 全部失败时，如果本地已有旧缓存，则以 5min max-age 降级返回
  - 无头像可用时返回 `502 Avatar upstream unavailable`
  - 缓存清理由 endpoint 统一删除超过 TTL 的头像文件
  - `MapModule.handleAvatarProxy(...)` 现在只保留 CORS、GET 检查、query 解析、settings 组装和 `HttpExchange` 写出
  - `MapModule` 清理旧 `downloadAvatar*`、`isAvatarCacheFresh`、`avatarCacheFile` 和未使用的 `writeFile(...)`
- 测试:
  - `MapAvatarEndpointTest` 覆盖缺参/非法 UUID、新鲜缓存、回源重试、禁用缓存不写入、旧缓存降级、502、cleanup 和 upstream URL 格式化
- 验证:
  - `mvn -q -DskipTests compile`: PASS on 2026-06-09 19:42 +08:00
  - `mvn -q "-Dtest=MapAvatarEndpointTest,MapModuleViewerSnapshotContractTest" test`: PASS on 2026-06-09 19:43 +08:00
  - `git diff --check`: PASS on 2026-06-09 19:43 +08:00
  - `mvn -q test`: PASS on 2026-06-09 19:45 +08:00
  - `mvn -q -DskipTests package`: PASS on 2026-06-09 19:45 +08:00
  - 当前 jar: `target/starcore-0.1.0-SNAPSHOT.jar`
  - 当前 jar 大小: `17615077` bytes
  - 当前 jar SHA256: `12639D7D078A74D7FDEB2C18515A9F38FE94A9E096758E15EBB388D6B4B8093C`

### 2026-06-09 TerrainWorldMetadataService 抽取

本轮继续 P4 的反屎山收口，把地图 snapshot 中 `terrain` metadata 的 JSON 构建和世界 `region/*.mca` 边界扫描从 `MapModule` 抽到 `TerrainWorldMetadataService`。现在 `MapModule.appendTerrainMetadata(...)` 只做薄委托，地形瓦片服务线里的 metadata 也有独立测试兜底。

- 新增/更新:
  - `src/main/java/dev/starcore/starcore/module/map/TerrainWorldMetadataService.java`
  - `src/main/java/dev/starcore/starcore/module/map/MapModule.java`
  - `src/test/java/dev/starcore/starcore/module/map/TerrainWorldMetadataServiceTest.java`
- 后端:
  - `TerrainWorldMetadataService` 接管 snapshot `terrain` JSON 构建
  - 服务通过注入的世界解析器跳过不存在世界，避免 metadata 输出空世界
  - 服务扫描世界 `region/*.mca` 计算 `minX/minZ/maxX/maxZ`
  - 无 region 文件夹或无有效 region 文件时按出生点回退 `spawn ± 256`
  - 保留 remember 回调，让地形磁盘缓存仍能记忆世界目录
  - `MapModule` 删除旧 `appendTerrainWorldMetadata(...)` 和 `generatedRegionBounds(...)`
- 测试:
  - `TerrainWorldMetadataServiceTest` 覆盖已生成 region 边界、缺失世界跳过、无 region 回退、tileSize 裁剪和 remember 回调
- 验证:
  - `mvn -q -DskipTests compile`: PASS on 2026-06-09 19:27 +08:00
  - `mvn -q "-Dtest=TerrainWorldMetadataServiceTest,TerrainTileEndpointTest,TerrainTileServiceTest,TerrainTileDiskCacheTest,MapModuleViewerSnapshotContractTest" test`: PASS on 2026-06-09 19:27 +08:00
  - `mvn -q test`: PASS on 2026-06-09 19:31 +08:00
  - `git diff --check`: PASS on 2026-06-09 19:28 +08:00
  - `mvn -q -DskipTests package`: PASS on 2026-06-09 19:31 +08:00
  - 当前 jar: `target/starcore-0.1.0-SNAPSHOT.jar`
  - 当前 jar 大小: `17608950` bytes
  - 当前 jar SHA256: `6C58204FC90BF148A144C7893F1AA02018C960A0F1B7ADFDD61A086DC8FF222D`

### 2026-06-09 TerrainTileEndpoint 抽取

本轮继续 P4，把地形 HTTP 的参数解析和响应构建从 `MapModule` 抽到 `TerrainTileEndpoint`。现在 `MapModule` 的地形 HTTP handler 只保留 CORS、GET 检查、配置启用检查、访问鉴权和 `HttpExchange` 写出，`world/x/z/size` 解析、状态码、content type 和响应体构建都集中到 endpoint。

- 新增/更新:
  - `src/main/java/dev/starcore/starcore/module/map/TerrainTileEndpoint.java`
  - `src/main/java/dev/starcore/starcore/module/map/MapModule.java`
  - `src/test/java/dev/starcore/starcore/module/map/TerrainTileEndpointTest.java`
- 后端:
  - `TerrainTileEndpoint` 接管 `/api/map/terrain`、`/api/map/terrain-data`、`/api/map/terrain-bin` 的参数解析和响应构建
  - `size` 会统一裁剪到 `1..4096`
  - 三种格式分别输出 `image/png`、`application/octet-stream`、`application/json; charset=utf-8`
  - endpoint 统一返回 `400 Missing/Invalid`、`404 World not found`、`503 Terrain renderer is busy`
  - `MapModule` 通过单个 `handleTerrainTileRequest(...)` 处理 CORS、GET、terrain-enabled、访问鉴权和写响应
- 测试:
  - `TerrainTileEndpointTest` 覆盖 PNG / binary / data 三种成功响应、cache max-age、缺参、非法参数、size 裁剪、空世界、busy typed exception
- 验证:
  - `mvn -q -DskipTests compile`: PASS on 2026-06-09 19:13 +08:00
  - `mvn -q "-Dtest=TerrainTileEndpointTest,TerrainTileServiceTest,TerrainTileDiskCacheTest,MapModuleViewerSnapshotContractTest" test`: PASS on 2026-06-09 19:13 +08:00
  - `node --check src/main/resources/web/map/js/map.js`: PASS on 2026-06-09 19:16 +08:00
  - `mvn -q test`: PASS on 2026-06-09 19:16 +08:00
  - `mvn -q -DskipTests package`: PASS on 2026-06-09 19:18 +08:00
  - 当前 jar: `target/starcore-0.1.0-SNAPSHOT.jar`
  - 当前 jar 大小: `17604819` bytes
  - 当前 jar SHA256: `5266D9C6F099D5EB50F3794C5D1D6562FED218C6A78C482F1D59CE43C70D61C9`

### 2026-06-09 TerrainTileService 外壳抽取

本轮继续 P4，把地形瓦片缓存/磁盘/并发/dirty 移除编排从 `MapModule` 抽进 `TerrainTileService`。现在 `MapModule` 只保留 HTTP 参数解析、Bukkit 主线程调度、真实世界 raster 取样和地形 metadata 输出，地形瓦片的缓存与渲染编排不再散在地图模块里。

- 新增/更新:
  - `src/main/java/dev/starcore/starcore/module/map/TerrainTileService.java`
  - `src/main/java/dev/starcore/starcore/module/map/TerrainTileBusyException.java`
  - `src/main/java/dev/starcore/starcore/module/map/TerrainTileDiskCache.java`
  - `src/main/java/dev/starcore/starcore/module/map/MapModule.java`
  - `src/test/java/dev/starcore/starcore/module/map/TerrainTileServiceTest.java`
  - `src/test/java/dev/starcore/starcore/module/map/TerrainTileDiskCacheTest.java`
- 后端:
  - `TerrainTileService` 接管 PNG / terrain-bin / raster 三类缓存编排
  - 服务负责内存缓存、磁盘缓存读取、PNG 磁盘异步写入、raster render Future 合并、并发槽位 acquire/release
  - dirty tile 后由服务统一移除三类缓存和正在渲染的 future
  - revision 和 revision broadcast 仍复用 `TerrainTileInvalidationService`，但由 `TerrainTileService` 对 `MapModule` 暴露更薄的门面
  - `TerrainTileBusyException` 从 `MapModule` 私有内部类提升为包内顶层类
  - `MapModule` 删除旧 `terrainMemoryCache`、`terrainTileRasterRenders`、`terrainRenderLimiter`、`terrainDiskCache` 字段和对应 helper
- 稳定性:
  - `TerrainTileDiskCache` 路径新增 tile pixels 分层，避免服主修改 `map.web.terrain-tile-pixels` 后复用不同像素尺寸的旧 PNG
- 测试:
  - `TerrainTileServiceTest` 覆盖内存缓存复用、磁盘缓存复用、busy typed exception、dirty 后重渲染和 revision bump
  - `TerrainTileDiskCacheTest` 新增不同 tile pixels 缓存路径隔离护栏
- 验证:
  - `mvn -q -DskipTests compile`: PASS on 2026-06-09 18:59 +08:00
  - `mvn -q "-Dtest=TerrainTileServiceTest,TerrainTileDiskCacheTest,TerrainTileMemoryCacheTest,TerrainTileInvalidationServiceTest,MapModuleViewerSnapshotContractTest" test`: PASS on 2026-06-09 19:04 +08:00
  - `node --check src/main/resources/web/map/js/map.js`: PASS on 2026-06-09 19:04 +08:00
  - `mvn -q test`: PASS on 2026-06-09 19:05 +08:00
  - `mvn -q -DskipTests package`: PASS on 2026-06-09 19:05 +08:00
  - 当前 jar: `target/starcore-0.1.0-SNAPSHOT.jar`
  - 当前 jar 大小: `17597165` bytes
  - 当前 jar SHA256: `255752F4B4874E8A17F263E782DED367089D2E8E12D1064849009B097970800B`

### 2026-06-09 地形瓦片像素尺寸配置化

本轮把前后端共同硬编码的 256 像素地形瓦片协议做成可配置链路。默认仍是 256，旧服行为不变；服主可以按服务器性能和地图清晰度在 `64..512` 之间调节单张地形瓦片像素尺寸。

- 新增/更新:
  - `src/main/resources/config.yml`
  - `src/main/java/dev/starcore/starcore/core/config/ConfigurationService.java`
  - `src/main/java/dev/starcore/starcore/module/map/MapModule.java`
  - `src/main/resources/web/map/js/map.js`
  - `src/test/java/dev/starcore/starcore/core/config/ConfigurationServiceTerrainConfigTest.java`
  - `src/test/java/dev/starcore/starcore/module/map/MapModuleViewerSnapshotContractTest.java`
- 配置:
  - 新增 `map.web.terrain-tile-pixels: 256`
  - 配置文件已补中文注释：`128` 更省性能但细节较低，`256` 为推荐默认，`512` 更清晰但渲染和传输成本更高
  - `ConfigurationService.mapTerrainTilePixels()` 会把配置裁剪到 `64..512`
- 后端:
  - `MapModule` 启动时读取 `terrain-tile-pixels`
  - 地形 raster 数组、采样循环、地形 JSON metadata `terrain.tileSize`、terrain-data / terrain-bin / PNG 输出都跟随配置尺寸
  - `detailedTerrainColor` 和 `terrainHeightAt` 不再使用固定 256，而是接收当前 tilePixels
- 前端:
  - `map.js` 新增 `terrainTileSize` 状态
  - Leaflet grid layer 的 `tileSize`、canvas 宽高、terrain URL 的 `worldSize` 计算都跟随 snapshot `terrain.tileSize`
  - snapshot 中 tile size 变化时会重建地形图层，避免 Leaflet 旧图层继续用旧 tileSize
  - 旧的 `tileSize: 256`、canvas 256、`Math.round(256 / scale)` 硬编码已移除，只保留默认 fallback 256
- 测试:
  - `ConfigurationServiceTerrainConfigTest` 覆盖默认 tile pixels 和 `64..512` 裁剪
  - `MapModuleViewerSnapshotContractTest` 覆盖地图 JSON 会输出配置后的 `terrain.tileSize`
- 验证:
  - `mvn -q -DskipTests compile`: PASS on 2026-06-09 18:47 +08:00
  - `mvn -q "-Dtest=ConfigurationServiceTerrainConfigTest,ConfigDefaultsCoverageTest,MapModuleViewerSnapshotContractTest,TerrainTileCodecTest" test`: PASS on 2026-06-09 18:47 +08:00
  - `node --check src/main/resources/web/map/js/map.js`: PASS on 2026-06-09 18:47 +08:00
  - `mvn -q test`: PASS on 2026-06-09 18:48 +08:00
  - `mvn -q -DskipTests package`: PASS on 2026-06-09 18:49 +08:00
  - 当前 jar: `target/starcore-0.1.0-SNAPSHOT.jar`
  - 当前 jar 大小: `17591830` bytes
  - 当前 jar SHA256: `C95895E8032E32CC15ACA6AE6A4D4064100AF1DBF45E69409F181260D6675985`

### 2026-06-09 地形 dirty tracking 配置化

本轮继续沿着“灵活、不硬编码、高性能可调”推进，把地形瓦片脏标记参数从 `MapModule` 常量移到中文注释配置。现在服主可以按服务器规模和地形更新频率调整 dirty tile 粒度与 dirty map 上限，不需要改 Java 代码。

- 新增/更新:
  - `src/main/resources/config.yml`
  - `src/main/java/dev/starcore/starcore/core/config/ConfigurationService.java`
  - `src/test/java/dev/starcore/starcore/core/config/ConfigurationServiceTerrainConfigTest.java`
  - `src/main/java/dev/starcore/starcore/module/map/MapModule.java`
- 配置:
  - 新增 `map.web.terrain-dirty-tile-sizes: [256, 128, 64, 32, 16, 8, 4, 2, 1]`
  - 新增 `map.web.terrain-dirty-max-entries: 8192`
  - 配置文件已补中文注释，说明大尺寸负责远景刷新、小尺寸负责近景精确刷新，以及大服频繁改方块时如何调高 dirty map 上限
- 后端:
  - `ConfigurationService` 新增 `mapTerrainDirtyTileSizes()` 与 `mapTerrainDirtyMaxEntries()`
  - dirty tile sizes 会过滤非法值、去重，并在全部非法时回退默认值
  - dirty max entries 会裁剪到 `256..65536`
  - `MapModule` 启动时按配置创建 `TerrainTileInvalidationService`，不再硬编码 dirty 粒度和上限
- 测试:
  - `ConfigurationServiceTerrainConfigTest` 覆盖默认值、非法值过滤、重复值去重、下限/上限裁剪
  - `ConfigDefaultsCoverageTest` 覆盖新增配置路径已经写入默认 `config.yml`
- 架构:
  - 本轮只配置化 dirty tracking 参数；前后端地形瓦片像素尺寸配置化已在后续 2026-06-09 18:49 +08:00 续跑完成
- 验证:
  - `mvn -q -DskipTests compile`: PASS on 2026-06-09 18:33 +08:00
  - `mvn -q "-Dtest=ConfigurationServiceTerrainConfigTest,ConfigDefaultsCoverageTest,TerrainTileInvalidationServiceTest,MapModuleViewerSnapshotContractTest" test`: PASS on 2026-06-09 18:33 +08:00
  - `node --check src/main/resources/web/map/js/map.js`: PASS on 2026-06-09 18:33 +08:00
  - `mvn -q test`: PASS on 2026-06-09 18:35 +08:00
  - `mvn -q -DskipTests package`: PASS on 2026-06-09 18:36 +08:00
  - 当前 jar: `target/starcore-0.1.0-SNAPSHOT.jar`
  - 当前 jar 大小: `17591408` bytes
  - 当前 jar SHA256: `691653F9D83AAD7DA6AAB030A4043110291565C425FB6B88E455FE37F178623C`

### 2026-06-09 TerrainTileInvalidationService 拆分

本轮继续 P4，把地形瓦片脏块追踪和 terrain revision 广播从 `MapModule` 中拆出。现在方块变化映射到多级 tile key、dirty 时间戳、dirty 裁剪、失效判断、render 完成后的清理、revision 单调递增、revision broadcast 排队/取消都集中到独立服务里，`MapModule` 只保留 Bukkit 事件入口、缓存移除和刷新回调。

- 新增:
  - `src/main/java/dev/starcore/starcore/module/map/TerrainTileInvalidationService.java`
  - `src/test/java/dev/starcore/starcore/module/map/TerrainTileInvalidationServiceTest.java`
- 地图后端:
  - `TerrainTileInvalidationService` 接管 dirty tile map、terrain revision、revision broadcast task id
  - `MapModule` 的 `terrainTileInvalidatedAfter`、`terrainTileDirtyAfter`、`clearTerrainTileDirtyBefore` 改为委托服务
  - 方块事件触发后，服务返回受影响 tile keys，`MapModule` 只负责移除内存缓存和正在渲染的 future
  - 地图 snapshot 的 `terrain.revision` 改为读取服务 revision
  - `MapModule` 清理旧 `terrainTileDirtyAt`、`terrainRevision`、`terrainRevisionBroadcastTaskId`、`alignTerrainTile`、`bumpTerrainRevision`、`pruneTerrainTileDirtyAt` 等职责
- 测试:
  - `TerrainTileInvalidationServiceTest` 覆盖负坐标对齐、邻居 tile、dirty 判断、render 清理、TTL/max entries 裁剪、revision 单调递增、非法配置跳过
- 架构:
  - `docs/ANTI_SPAGHETTI_REFACTOR_PLAN.md` 标记 P4.5 完成
  - P4 地形瓦片主线已经完成；后续可按需组合完整 `TerrainTileService` 外壳，继续降低 `MapModule` HTTP/raster 体积
- 验证:
  - `mvn -q -DskipTests compile`: PASS on 2026-06-09 18:20 +08:00
  - `mvn -q "-Dtest=TerrainTileInvalidationServiceTest" test`: PASS on 2026-06-09 18:20 +08:00
  - `mvn -q "-Dtest=TerrainTileInvalidationServiceTest,TerrainTilePrewarmServiceTest,TerrainTileMemoryCacheTest,TerrainTileRenderLimiterTest,TerrainTileDiskCacheTest,TerrainTileCodecTest,MapModuleViewerSnapshotContractTest" test`: PASS on 2026-06-09 18:21 +08:00
  - `node --check src/main/resources/web/map/js/map.js`: PASS on 2026-06-09 18:21 +08:00
  - `mvn -q test`: PASS on 2026-06-09 18:21 +08:00
  - `mvn -q -DskipTests package`: PASS on 2026-06-09 18:22 +08:00
  - 当前 jar: `target/starcore-0.1.0-SNAPSHOT.jar`
  - 当前 jar 大小: `17590872` bytes
  - 当前 jar SHA256: `935A6BC9F3E752518B1658330A257B54FD0F5E5A6DABB4204E6679E5362E6B1F`

### 2026-06-09 TerrainTilePrewarmService 拆分

本轮继续 P4，把地形瓦片预热队列从 `MapModule` 中拆出。现在预热候选瓦片生成、排序、限量、poll/requeue/clear 都集中到独立服务里，`MapModule` 只保留 Bukkit 调度和实际 `terrainTile(...)` 渲染调用。

- 新增:
  - `src/main/java/dev/starcore/starcore/module/map/TerrainPrewarmTile.java`
  - `src/main/java/dev/starcore/starcore/module/map/TerrainTilePrewarmService.java`
  - `src/test/java/dev/starcore/starcore/module/map/TerrainTilePrewarmServiceTest.java`
- 地图后端:
  - `TerrainPrewarmTile` 从 `MapModule` 私有内部 record 提升为包内模型
  - `TerrainTilePrewarmService` 接管按世界出生点生成预热候选、按 tile size 优先级和距离排序、max tiles 限量、poll/requeue/clear
  - `MapModule` 保留 `startTerrainPrewarm` / Bukkit scheduler / `runTerrainPrewarmStep`，但队列数据结构和候选构建已委托服务
  - `MapModule` 清理旧 `buildTerrainPrewarmTiles` 和 `terrainTileDistanceSquared`
- 测试:
  - `TerrainTilePrewarmServiceTest` 覆盖排序、限量、重建清空、非法输入跳过、busy requeue 进入队尾
- 架构:
  - `docs/ANTI_SPAGHETTI_REFACTOR_PLAN.md` 标记 P4.4 预热队列拆分已完成
  - P4 后续继续拆脏块追踪和 revision 广播
- 验证:
  - `mvn -q -DskipTests compile`: PASS on 2026-06-09 18:05 +08:00
  - `mvn -q "-Dtest=TerrainTilePrewarmServiceTest,TerrainTileMemoryCacheTest,TerrainTileRenderLimiterTest,TerrainTileDiskCacheTest,TerrainTileCodecTest,MapModuleViewerSnapshotContractTest" test`: PASS on 2026-06-09 18:06 +08:00
  - `node --check src/main/resources/web/map/js/map.js`: PASS on 2026-06-09 18:06 +08:00
  - `mvn -q test`: PASS on 2026-06-09 18:08 +08:00
  - `mvn -q -DskipTests package`: PASS on 2026-06-09 18:08 +08:00
  - 当前 jar: `target/starcore-0.1.0-SNAPSHOT.jar`
  - 当前 jar 大小: `17587478` bytes
  - 当前 jar SHA256: `BF2A5136D4156F0C812FA31BA4FBD175F9C54867D187A7E34CA7FF57972D4DAA`

### 2026-06-09 TerrainTileMemoryCache 拆分

本轮继续 P4，把地形瓦片内存缓存和 raster 渲染并发计数从 `MapModule` 中拆出。现在 PNG、terrain-bin、raster 三类内存缓存的 TTL、max entries、脏块/源文件失效回调都集中在独立缓存类里，渲染并发槽位也有独立 limiter。

- 新增:
  - `src/main/java/dev/starcore/starcore/module/map/TerrainTileMemoryCache.java`
  - `src/main/java/dev/starcore/starcore/module/map/TerrainTileRenderLimiter.java`
  - `src/test/java/dev/starcore/starcore/module/map/TerrainTileMemoryCacheTest.java`
  - `src/test/java/dev/starcore/starcore/module/map/TerrainTileRenderLimiterTest.java`
- 地图后端:
  - `MapModule` 的 PNG / terrain-bin / raster 三类内存缓存改为委托 `TerrainTileMemoryCache`
  - `MapModule` 的地形 raster 渲染并发计数改为委托 `TerrainTileRenderLimiter`
  - 脏块失效时统一调用 `terrainMemoryCache.remove(key)` 清理三类缓存
  - `MapModule` 清理旧 `TerrainTileCacheEntry`、`TerrainTileRasterCacheEntry` 和三段重复 prune 逻辑
- 测试:
  - `TerrainTileMemoryCacheTest` 覆盖 TTL、脏块/源文件 invalidation、max entries、remove
  - `TerrainTileRenderLimiterTest` 覆盖 max jobs、release、reset 和 release 低于 0 的兜底
- 架构:
  - `docs/ANTI_SPAGHETTI_REFACTOR_PLAN.md` 标记 P4.3 内存缓存与渲染并发限制拆分已完成
  - P4 后续继续拆预热队列、脏块追踪和 revision 广播
- 验证:
  - `mvn -q -DskipTests compile`: PASS on 2026-06-09 17:54 +08:00
  - `mvn -q "-Dtest=TerrainTileMemoryCacheTest,TerrainTileRenderLimiterTest,TerrainTileDiskCacheTest,TerrainTileCodecTest,MapModuleViewerSnapshotContractTest" test`: PASS on 2026-06-09 17:55 +08:00
  - `node --check src/main/resources/web/map/js/map.js`: PASS on 2026-06-09 17:55 +08:00
  - `mvn -q test`: PASS on 2026-06-09 17:57 +08:00
  - `mvn -q -DskipTests package`: PASS on 2026-06-09 17:58 +08:00
  - 当前 jar: `target/starcore-0.1.0-SNAPSHOT.jar`
  - 当前 jar 大小: `17584453` bytes
  - 当前 jar SHA256: `5808C14F7CDD25310146C8D34A2DC19B0AB1803CE4209AEB05AB1BDFE0BB5406`

### 2026-06-09 TerrainTileDiskCache 拆分

本轮继续 P4，把地形瓦片磁盘缓存和 key 从 `MapModule` 中拆出。现在磁盘缓存的路径安全、TTL、脏块失效、region 源文件失效都集中在独立类里，后续继续抽内存缓存和预热服务时可以复用同一个 `TerrainTileKey`。

- 新增:
  - `src/main/java/dev/starcore/starcore/module/map/TerrainTileKey.java`
  - `src/main/java/dev/starcore/starcore/module/map/TerrainTileDiskCache.java`
  - `src/test/java/dev/starcore/starcore/module/map/TerrainTileDiskCacheTest.java`
- 地图后端:
  - `TerrainTileKey` 从 `MapModule` 私有内部 record 提升为包内模型
  - `TerrainTileDiskCache` 接管地形 PNG 磁盘缓存安全路径、读写、TTL 过期删除、空文件删除、脏块失效和 region 源文件失效
  - `MapModule` 保留是否启用磁盘缓存的配置判断和异步调度，实际文件操作委托 `TerrainTileDiskCache`
  - `MapModule` 清理旧 `terrainTileDiskCacheFile`、`terrainTileSourceModifiedAtMillis`、`terrainWorldRegionDirectory` 和磁盘缓存版本常量
- 测试:
  - `TerrainTileDiskCacheTest` 覆盖路径安全、TTL、脏块删除、空文件删除、region 源文件更新失效、世界目录记忆
- 架构:
  - `docs/ANTI_SPAGHETTI_REFACTOR_PLAN.md` 标记 P4.2 磁盘缓存层拆分已完成
  - P4 后续继续拆内存缓存、预热队列、脏块追踪和 revision 广播
- 验证:
  - `mvn -q -DskipTests compile`: PASS on 2026-06-09 17:43 +08:00
  - `mvn -q "-Dtest=TerrainTileDiskCacheTest,TerrainTileCodecTest,MapModuleViewerSnapshotContractTest" test`: PASS on 2026-06-09 17:44 +08:00
  - `node --check src/main/resources/web/map/js/map.js`: PASS on 2026-06-09 17:45 +08:00
  - `mvn -q test`: PASS on 2026-06-09 17:46 +08:00
  - `mvn -q -DskipTests package`: PASS on 2026-06-09 17:46 +08:00
  - 当前 jar: `target/starcore-0.1.0-SNAPSHOT.jar`
  - 当前 jar 大小: `17581127` bytes
  - 当前 jar SHA256: `35D644E8C460BA44D90E52F34FC1F278F4210B9457DDC22A4E3D0567B850F7D6`

### 2026-06-09 TerrainTileCodec 拆分

本轮按 P4 开始拆地形瓦片系统，先把最容易分叉且需要稳定格式的编码层从 `MapModule` 搬出。这样后续继续抽缓存、磁盘缓存和预热服务时，terrain-data / terrain-bin / PNG 输出格式有独立测试兜底。

- 新增:
  - `src/main/java/dev/starcore/starcore/module/map/TerrainTileRaster.java`
  - `src/main/java/dev/starcore/starcore/module/map/TerrainTileCodec.java`
  - `src/test/java/dev/starcore/starcore/module/map/TerrainTileCodecTest.java`
- 地图后端:
  - `/api/map/terrain` 的 PNG 编码改为委托 `TerrainTileCodec.encodePng`
  - `/api/map/terrain-data` 的 JSON 编码改为委托 `TerrainTileCodec.encodeJson`
  - `/api/map/terrain-bin` 的二进制编码改为委托 `TerrainTileCodec.encodeBinary`
  - `MapModule` 清理旧 `encodeTerrainTilePng`、`terrainTileDataJson`、`terrainTileDataBinary`、调色板和高度编码方法
  - `TerrainTileRaster` 从 `MapModule` 私有内部 record 提升为包内模型，为下一步 `TerrainTileService` 抽取铺底
- 测试:
  - `TerrainTileCodecTest` 锁住 JSON 关键字段、palette/pixels/lights、terrain-bin 头部和 PNG 魔数
- 架构:
  - `docs/ANTI_SPAGHETTI_REFACTOR_PLAN.md` 标记 P4.1 编码层拆分已完成
  - P4 后续继续拆内存缓存、磁盘缓存、预热队列、脏块追踪和 revision 广播
- 验证:
  - `mvn -q -DskipTests compile`: PASS on 2026-06-09 17:31 +08:00
  - `mvn -q "-Dtest=TerrainTileCodecTest,MapModuleViewerSnapshotContractTest" test`: PASS on 2026-06-09 17:32 +08:00
  - `node --check src/main/resources/web/map/js/map.js`: PASS on 2026-06-09 17:33 +08:00
  - `mvn -q test`: PASS on 2026-06-09 17:34 +08:00
  - `mvn -q -DskipTests package`: PASS on 2026-06-09 17:34 +08:00
  - 当前 jar: `target/starcore-0.1.0-SNAPSHOT.jar`
  - 当前 jar 大小: `17578495` bytes
  - 当前 jar SHA256: `18414DF71EEE951F71A8306F32C21F9053F8CB2620CAE7D18840607E7991210A`

### 2026-06-09 MapFinanceEndpoint 拆分

本轮按反屎山计划推进 P3，把网页地图财政事件接口从超大 `MapModule` 拆到独立 endpoint，避免财政筛选、导出和网页账本继续扩大地图模块。

- 新增:
  - `src/main/java/dev/starcore/starcore/module/map/MapFinanceEndpoint.java`
- 地图后端:
  - `/api/map/finance/events` 的财政查询、分页、时间窗口、filter 归一化、CSV/JSON 导出、导出文件名改为由 `MapFinanceEndpoint` 负责
  - `MapModule.buildFinanceEventsResponse(MapViewerAccess, Map)` 保留原签名，内部只做 endpoint 响应适配，现有反射契约测试无需改
  - `MapModule` 清理旧财政渲染方法、分页 helper、导出 helper 和 `FinanceEventTimeWindow`
  - 国家可见性仍留在 `MapModule`，通过函数传给 endpoint，避免 endpoint 直接读地图可见性缓存
- 架构:
  - `MapModule` 继续作为地图 Web 路由和通用 HTTP 响应入口
  - `docs/ANTI_SPAGHETTI_REFACTOR_PLAN.md` 标记 P3 核心拆分已完成
  - 下一步进入 P4: 拆 `TerrainTileService`，把地形瓦片缓存、预热和渲染从 `MapModule` 中抽出
- 验证:
  - `mvn -q -DskipTests compile`: PASS on 2026-06-09 15:45 +08:00
  - `mvn -q "-Dtest=MapModuleViewerSnapshotContractTest,LedgerCategoryServiceTest" test`: PASS on 2026-06-09 15:47 +08:00
  - `mvn -q test`: PASS on 2026-06-09 15:48 +08:00
  - `mvn -q -DskipTests package`: PASS on 2026-06-09 15:49 +08:00
  - 当前 jar: `target/starcore-0.1.0-SNAPSHOT.jar`
  - 当前 jar 大小: `17576985` bytes
  - 当前 jar SHA256: `D856ECBE658A01C1AB89779963E3DFDBBC972B7B8F439BA74A08703192843325`

### 2026-06-09 EventCommandHandler 拆分

本轮按反屎山计划推进 P2，把 `/sc ev` 事件命令从超大 `StarCoreCommand` 拆到独立 handler，避免后续事件审计、导出、账本展示继续把主命令类撑大。

- 新增:
  - `src/main/java/dev/starcore/starcore/command/EventCommandHandler.java`
- 命令:
  - `/sc ev list/audit/export/record/clear` 的执行逻辑改为由 `EventCommandHandler` 负责
  - `/sc ev` 的 Tab 补全改为由 `EventCommandHandler` 负责
  - 事件 CSV/JSON 导出、分页、时间窗口解析、事件类型本地化、事件 context 格式化已从主命令类搬出
  - `StarCoreCommand` 清理事件拆分遗留导入、旧 `normalizeEventSubcommand` 和旧事件时间格式方法
- 架构:
  - `StarCoreCommand` 继续作为薄 root/subcommand 分发入口
  - `docs/ANTI_SPAGHETTI_REFACTOR_PLAN.md` 标记 P2 核心拆分已完成
  - 下一步进入 P3: 拆 `MapFinanceEndpoint`，把网页地图财政 API 从 `MapModule` 中抽出
- 验证:
  - `mvn -q -DskipTests compile`: PASS on 2026-06-09 15:25 +08:00
  - `mvn -q "-Dtest=StarCoreCommandShortAliasDispatchTest,StarCoreCommandTabCompletionTest,LedgerCategoryServiceTest" test`: PASS on 2026-06-09 15:26 +08:00
  - `mvn -q test`: PASS on 2026-06-09 15:26 +08:00
  - `mvn -q -DskipTests package`: PASS on 2026-06-09 15:27 +08:00
  - 当前 jar: `target/starcore-0.1.0-SNAPSHOT.jar`
  - 当前 jar 大小: `17569327` bytes
  - 当前 jar SHA256: `A852F48F45C84A4684A2E1B44075C32F5D5FA92F597E80F6C311890C0AB1F211`

### 2026-06-09 LedgerCategoryService 收口

本轮按反屎山计划推进 P1，把财政/事件流水分类从命令和网页地图里抽到 `LedgerCategoryService`。

- 新增:
  - `src/main/java/dev/starcore/starcore/module/event/LedgerCategoryService.java`
  - `src/test/java/dev/starcore/starcore/module/event/LedgerCategoryServiceTest.java`
- 命令:
  - `StarCoreCommand` 的事件 filter 归一化、分类匹配、显示名 suffix、Tab 补全候选改为委托 `LedgerCategoryService`
  - 删除命令内重复的 `eventFilterEventTypes` / `eventFilterPrefixes` / `ledgerCategoryKey`
- 网页地图:
  - `MapModule` 的财政 filter 归一化和匹配改为委托 `LedgerCategoryService`
  - 删除地图内重复的 `financeFilterEventTypes` / `financeFilterPrefixes` / `financeLedgerCategoryKey`
- 架构:
  - `docs/ANTI_SPAGHETTI_REFACTOR_PLAN.md` 标记 P1 核心抽取已完成
  - 下一步进入 P2: 拆 `EventCommandHandler`
- 验证:
  - `mvn -q "-Dtest=LedgerCategoryServiceTest,StarCoreCommandShortAliasDispatchTest,StarCoreCommandTabCompletionTest,MapModuleViewerSnapshotContractTest" test`: PASS on 2026-06-09 14:59 +08:00
  - `mvn -q test`: PASS on 2026-06-09 15:04 +08:00
  - `mvn -q -DskipTests package`: PASS on 2026-06-09 15:04 +08:00
  - 当前 jar: `target/starcore-0.1.0-SNAPSHOT.jar`
  - 当前 jar 大小: `17563400` bytes
  - 当前 jar SHA256: `07890E6D65B158BF765C5B6FF4539D3879D440500270EBD97650358097E76A10`

### 2026-06-09 反屎山风险收口

本轮先处理最明确的代码质量风险: 分析产物污染源码树，以及后续拆分缺少固定计划。

- 清理:
  - 删除 `src/main/java/dev/starcore/starcore/module/map/graphify-out/`
  - `.gitignore` 新增 `.codegraph/`、`graphify-out/`、`**/graphify-out/`
- 文档:
  - 新增 `docs/ANTI_SPAGHETTI_REFACTOR_PLAN.md`
  - 明确 `MapModule.java` 和 `StarCoreCommand.java` 是当前需要控规模的两个热点
  - 固定后续拆分顺序: `LedgerCategoryService` -> `EventCommandHandler` -> `MapFinanceEndpoint` -> `TerrainTileService`
- 下一步:
  - 优先抽 `LedgerCategoryService`，把命令和网页财政分类的归一化、匹配、补全统一收口。
- 验证:
  - `rg --files src/main/java | rg "graphify-out|\\.json$"`: PASS, no matches on 2026-06-09 14:39 +08:00
  - `mvn -q -DskipTests compile`: PASS on 2026-06-09 14:39 +08:00

### 2026-06-09 自定义财政分类 Tab 补全

本轮把上一轮的 `ledger.categories` 配置化继续补齐到命令体验。现在服主在 `config.yml` 里新增的财政/事件流水分类名，会自动出现在 `/sc ev audit <国家> ...` 与 `/sc ev export <国家> ...` 的 Tab 补全里。

- 配置服务：
  - `ConfigurationService` 新增 `ledgerCategoryKeys()`
  - 默认分类和配置文件新增分类会合并输出
  - `all`、`finance`、`resource-income` 等原有候选顺序保持兼容
- 命令：
  - `StarCoreCommand` 的事件筛选补全改为读取 `ledgerCategoryKeys()`
  - 自定义分类如 `rpg-bonus`、`season-pass` 可直接补全
- 文档：
  - README 明确自定义 `ledger.categories` 会进入补全
  - 管理员财政指南补充分类命名建议
- 验证：
  - `mvn -q "-Dtest=StarCoreCommandTabCompletionTest,ConfigDefaultsCoverageTest" test`: PASS on 2026-06-09 14:25 +08:00
  - `mvn -q test`: PASS on 2026-06-09 14:27 +08:00
  - `mvn -q -DskipTests package`: PASS on 2026-06-09 14:28 +08:00
  - 当前 jar: `target/starcore-0.1.0-SNAPSHOT.jar`
  - 当前 jar 大小: `17561198` bytes
  - 当前 jar SHA256: `802EA7F2D51BFBF3F185B814AB7DE9D379ED4D149462DAFAE84BB1E8AADEB2A2`

下一步建议：

- 继续做真实 Paper smoke，验证配置自定义分类、命令补全、命令导出、网页财政流水和 CSV 中文编码。

### 2026-06-09 财政流水分类配置化

本轮把命令端事件审计/导出与网页地图财政流水的分类规则接到同一份 `ledger.categories` 配置。现在服主可以在 `config.yml` 中把 RPG 任务、副本、活动、资源奖励等自定义事件类型归入 `resource-income`、`reward`、`tax` 等分类，不需要修改 Java 代码。

- 配置：
  - `config.yml` 新增中文注释的 `ledger.categories`
  - 每个分类支持 `event-types` 精确匹配与 `prefixes` 前缀匹配
  - 默认保留 `finance`、`resource-income`、`income`、`reward`、`tax`、`deposit`、`withdraw` 以及 nation/territory/resource 等事件分类
- 后端：
  - `ConfigurationService` 新增 ledger 分类读取，并保留内置默认兜底
  - `/sc ev audit/export` 的财政细分过滤改为读取配置分类规则
  - `/api/map/finance/events` 的网页财政过滤同样读取配置分类规则
- 文档：
  - `docs/ADMIN_FINANCE_LEDGER_GUIDE.md` 新增分类规则配置示例
  - README 的可配置玩法列表新增财政流水分类项
- 验证：
  - `mvn -q -DskipTests compile`: PASS on 2026-06-09 14:19 +08:00
  - `mvn -q "-Dtest=ConfigDefaultsCoverageTest,StarCoreCommandShortAliasDispatchTest,MapModuleViewerSnapshotContractTest" test`: PASS on 2026-06-09 14:19 +08:00
  - `mvn -q test`: PASS on 2026-06-09 14:20 +08:00
  - `mvn -q -DskipTests package`: PASS on 2026-06-09 14:20 +08:00
  - 当前 jar: `target/starcore-0.1.0-SNAPSHOT.jar`
  - 当前 jar 大小: `17560381` bytes
  - 当前 jar SHA256: `EFE219CEE24577B1FD6D1ACF8ECED9EAD50C46FC3079DA7530D129F351AC8559`

下一步建议：

- 继续做 Paper smoke：验证资源刷新入账、自定义 ledger 分类、网页财政导出和中文 CSV。

### 2026-06-09 资源区块预计国库收益预报（已完成）

本轮把资源区块运营预报从“资源/经验产量”继续扩展到“预计国库收益”。网页地图现在不仅能看到实际入账后的资源产出财政汇总，也能在资源区块详情和国家运营预报里提前看到未来 3 轮、6 小时和 12 小时的预计国库收入。

- 共享预测模型：
  - `NationResourceDistrictOperationalOverview` 新增预计国库收益字段
  - `NationResourceDistrictOperationalSupport` 复用 `NationResourceDistrictRules.treasuryIncome(...)` 计算预测收入
  - 预测会尊重 `nation.resources.refresh.treasury-income.enabled/base-income/income-per-generated-block/richness-multiplier`
- 地图数据：
  - 资源区块 JSON 新增 `expectedTreasuryIncomeYield`
  - 新增 `expectedTreasuryIncomeYieldPerHour`
  - 新增 `forecastTreasuryIncomeNext3Cycles`
  - 资源区块 marker metadata 同步输出这些字段
- 网页地图：
  - 单个资源区块详情的“未来3轮”现在显示预计国库收益
  - 国家运营预报新增“预计国库”指标
  - 显示 6 小时 / 12 小时预计国库收入
  - 中文文案 `预计国库`，英文文案 `Treasury Forecast`
- 测试覆盖：
  - `NationResourceDistrictOperationalSupportTest` 锁住默认预测收入、每小时收入和未来 3 轮收入
  - `MapModuleViewerSnapshotContractTest` 锁住 marker metadata 和资源区块 JSON 的预计国库收益字段
  - `NationResourceDistrictViewSupportTest` / `NationResourceDistrictMenuSupportTest` 验证旧展示兼容
- 验证：
  - `node --check src/main/resources/web/map/js/map.js`: PASS on 2026-06-09 12:31 +08:00
  - `mvn -q -DskipTests compile`: PASS on 2026-06-09 12:31 +08:00
  - `mvn -q "-Dtest=NationResourceDistrictOperationalSupportTest,MapModuleViewerSnapshotContractTest" test`: PASS on 2026-06-09 12:30 +08:00
  - `mvn -q "-Dtest=NationResourceDistrictViewSupportTest,NationResourceDistrictMenuSupportTest" test`: PASS on 2026-06-09 12:30 +08:00
  - `mvn -q test`: PASS on 2026-06-09 12:31 +08:00
  - `mvn -q -DskipTests package`: PASS on 2026-06-09 12:31 +08:00
  - 当前 jar: `target/starcore-0.1.0-SNAPSHOT.jar`
  - 当前 jar 大小: `17546006` bytes
  - 当前 jar SHA256: `D7451670C7D7FDC4B8F40A0891B511CCB505DC1149AE2B3453266BE500412D25`

下一步建议：

- 继续补真实 Paper smoke，验证资源刷新、预测展示、国库入账、网页财政汇总全链路。
- 或继续做财政流水筛选：按资源产出、税收、奖励、支出分类查看。

### 2026-06-09 资源产出收入网页财政汇总

本轮把上一轮新增的 `treasury.resource-income` 接到网页地图财政汇总里。现在资源区块自动产出的国库收入不只会进入完整流水，也会在国家详情财政概览中作为“资源产出”独立指标显示，并计入近期净额。

- 后端 API：
  - `/api/map/finance/events` 的 `summary` 新增 `resourceIncome`
  - `summary.net` 现在纳入 `treasury.resource-income`
  - 完整流水仍继续展示 `treasury.resource-income` 明细、金额和 context details
- 地图 polygon metadata：
  - 新增 `financeResourceIncomeTotal`
  - `financeNetTotal` 现在纳入资源产出收入
  - 最近财政流水列表会正常显示 `treasury.resource-income`
- 前端：
  - 国家详情财政概览新增“资源产出”指标
  - 中文文案 `资源产出`
  - 英文文案 `Resource Income`
- 测试覆盖：
  - `MapModuleViewerSnapshotContractTest` 锁住 polygon metadata 的资源产出汇总、净额、最新流水顺序
  - `MapModuleViewerSnapshotContractTest` 锁住财政 API summary 的 `resourceIncome` 和净额
- 验证：
  - `node --check src/main/resources/web/map/js/map.js`: PASS on 2026-06-09 11:47 +08:00
  - `mvn -q -DskipTests compile`: PASS on 2026-06-09 11:47 +08:00
  - `mvn -q "-Dtest=MapModuleViewerSnapshotContractTest" test`: PASS on 2026-06-09 11:48 +08:00
  - `mvn -q test`: PASS on 2026-06-09 11:49 +08:00
  - `mvn -q -DskipTests package`: PASS on 2026-06-09 11:49 +08:00
  - 当前 jar: `target/starcore-0.1.0-SNAPSHOT.jar`
  - 当前 jar 大小: `17544687` bytes
  - 当前 jar SHA256: `93488CE2FABF50C98132718EA075B99BE07F14BE210B2B196F9D4434B42EF2E6`

下一步建议：

- 继续做资源区块收益趋势/未来产出预测，展示到网页地图国家详情面板。
- 或继续补真实 Paper smoke，验证“资源刷新 -> 国库入账 -> 网页财政汇总”全链路。

### 2026-06-09 资源区块自动产出入账

本轮把资源区块刷新从“生成矿物 + 国家经验”继续推进到 RPG 经济闭环：资源区块成功刷新矿物时，现在可以按配置自动给国家国库发放收入，并写入财政流水事件，网页地图的财政流水分页也能读到 `treasury.resource-income`。

- 资源刷新链路：
  - `NativeNationResourceDistrictService.refreshDistrictIfDue(...)` 成功生成矿物后继续给资源区块累计经验
  - 继续调用 `NationService.addExperience(...)` 增加国家经验
  - 新增 `settleResourceIncome(...)` 结算国库资源产出收入
  - 新增 `resource.refresh` 事件记录本轮生成数量、经验和收入
  - 新增 `treasury.resource-income` 事件记录国库入账和余额
- 新增可配置项：
  - `nation.resources.refresh.treasury-income.enabled`
  - `nation.resources.refresh.treasury-income.base-income`
  - `nation.resources.refresh.treasury-income.income-per-generated-block`
  - `nation.resources.refresh.treasury-income.richness-multiplier`
- 默认公式：
  - `国库收入 = (基础收入 + 每块收入 × 本轮生成矿物数) × 生物群系丰富度 × 收入倍率`
  - 默认开启，默认基础收入 `250.00`，每个生成矿物块 `15.00`
- 测试覆盖：
  - `NationResourceDistrictRulesTest.treasuryIncomeScalesWithGeneratedBlocksAndRichness`
  - `NativeNationResourceDistrictServiceFlowTest.resourceIncomeSettlementDepositsToTreasuryAndRecordsLedgerEvent`
  - `ConfigDefaultsCoverageTest` / `MessageDefaultsCoverageTest` 继续锁住新增配置和中文消息默认项
- 验证：
  - `mvn -q -DskipTests compile`: PASS on 2026-06-09 11:32 +08:00
  - `mvn -q "-Dtest=NationResourceDistrictRulesTest,NativeNationResourceDistrictServiceFlowTest" test`: PASS on 2026-06-09 11:33 +08:00
  - `mvn -q test`: PASS on 2026-06-09 11:34 +08:00
  - `mvn -q -DskipTests package`: PASS on 2026-06-09 11:34 +08:00
  - 当前 jar: `target/starcore-0.1.0-SNAPSHOT.jar`
  - 当前 jar 大小: `17544510` bytes
  - 当前 jar SHA256: `28CB71263E0B9584EE1089C4ED13EBF306FE5A87C3EE82D086A406171743D6F6`

下一步建议：

- 把 `treasury.resource-income` 纳入网页财政汇总分类，让资源产出收入在汇总卡片中有独立指标。
- 或继续做资源区块收益趋势/未来产出预测，展示到网页地图国家详情面板。

### 2026-06-09 国家财政流水前端分页

本轮把上一轮新增的 `/api/map/finance/events` 接入网页地图国家详情面板。现在选中国家后，财政概览区块不仅显示最近 3 条摘要，还可以点击按钮读取完整 `treasury.*` 流水并翻页查看。

- 前端接入：
  - `MapConfig.financeEventsUrl: ./api/map/finance/events`
  - `financeLedgerState` 保存当前国家、页码、加载状态、错误和接口数据
  - `loadFinanceLedger(...)` 调用财政 API
  - `renderNationFinanceLedger(...)` 渲染页码、总数和完整流水
  - `renderNationFinanceLedgerEvents(...)` 渲染 actor/reason/balance/details 关键信息
- 交互：
  - 财政概览区新增 `查看全部流水/刷新流水`
  - 支持 `上一页/下一页`
  - 切换国家时重置旧国家分页状态，避免显示错账
  - API 失败时在财政区块内显示错误，不影响地图主渲染
- 样式：
  - `nation-finance-ledger`
  - `nation-finance-ledger-head`
  - 复用现有 `nation-operations-timeline` 和 `nation-timeline-item`
- 验证：
  - `node --check src/main/resources/web/map/js/map.js`: PASS on 2026-06-09 11:06 +08:00
  - `mvn -q -DskipTests compile`: PASS on 2026-06-09 11:06 +08:00
  - `mvn -q "-Dtest=MapModuleViewerSnapshotContractTest" test`: PASS on 2026-06-09 11:06 +08:00
  - `mvn -q test`: PASS on 2026-06-09 11:07 +08:00
  - `mvn -q -DskipTests package`: PASS on 2026-06-09 11:07 +08:00
  - 当前 jar: `target/starcore-0.1.0-SNAPSHOT.jar`
  - 当前 jar 大小: `17541763` bytes
  - 当前 jar SHA256: `374FB6AFB47245BC7AE7BE650DFF478948DAA172850DE5E90001C7D5CDE170F6`

下一步建议：

- 继续做资源区块自动产出入账和国家经验增长，让 RPG 经济循环更完整。
- 或给访问者详情卡片增加快捷按钮：聚焦自己、聚焦所属国家、打开圈地模式。

### 2026-06-09 国家财政 Web API

本轮把国家财政流水从“地图详情摘要”继续推进到可分页查询的 Web JSON API。网页管理端、外部 RPG 面板或后续前端列表都可以通过接口读取完整 `treasury.*` 账本，不再只能看最近 3 条摘要。

- 新增接口：
  - `GET /api/map/finance/events?nation=<国家ID或国家名>&page=1&size=25`
  - `nation` 支持国家 UUID 或国家名
  - `page` 默认 `1`
  - `size` 默认 `25`，最大 `100`
- 访问控制：
  - 公共视图拒绝访问，不暴露经济数据
  - 个人链接/绑定访问可查自己可见范围内的国家
  - full access 可查全部国家
- 返回内容：
  - `ok`
  - `nationId/nationName/nationKind`
  - `treasuryBalance`
  - `page/size/total/totalPages`
  - `summary`: `income/reward/tax/deposit/withdraw/net`
  - `events`: 分页后的财政流水，包含 `id/type/message/occurredAt/amount/actor/reason/balance/context/details`
- 实现位置：
  - `MapModule.startWebServer()` 注册 `/api/map/finance/events`
  - `handleFinanceEventsRequest(...)` 处理 HTTP 入口
  - `buildFinanceEventsResponse(...)` 构建可测试响应
  - `financeEventsJson(...)` 输出分页 JSON
  - `ledgerContextEntries(...)` 将结构化 context 拆成 details 对象
- 测试覆盖：
  - `MapModuleViewerSnapshotContractTest.financeEventsResponseReturnsPagedTreasuryLedgerForWebMap`
  - 锁住分页、总页数、汇总金额、金库余额、事件顺序、context details 和非财政事件过滤
- 验证：
  - `mvn -q -DskipTests compile`: PASS on 2026-06-09 10:55 +08:00
  - `mvn -q "-Dtest=MapModuleViewerSnapshotContractTest" test`: PASS on 2026-06-09 10:54 +08:00
  - `mvn -q test`: PASS on 2026-06-09 10:55 +08:00
  - `node --check src/main/resources/web/map/js/map.js`: PASS on 2026-06-09 10:55 +08:00
  - `mvn -q -DskipTests package`: PASS on 2026-06-09 10:55 +08:00
  - 当前 jar: `target/starcore-0.1.0-SNAPSHOT.jar`
  - 当前 jar 大小: `17540173` bytes
  - 当前 jar SHA256: `3348DEDC4B4A1F2EAECA50234EB3A5CFA2A1415433310B7BC80649065F636B88`

下一步建议：

- 前端接入财政 API，在国家详情面板增加“查看全部流水/翻页”。
- 或继续做资源区块自动产出入账和国家经验增长，让 RPG 经济循环更完整。

### 2026-06-09 地图访问者详情卡片

本轮继续补网页地图的信息密度，把访问者信息从简单文本升级为可扫读的指标卡片。使用个人地图链接打开时，侧边栏现在会清晰显示玩家余额、身份、所属国家、国家类型、在线状态、坐标、等级、领地/城邦/资源区块容量和国家进度。

- 数据来源：
  - 继续复用 `MapModule.appendViewer(...)` 已输出的 viewer JSON
  - 余额来自 `context.economyService().balance(viewerId)`
  - 国家、身份、等级、领地上限来自 `NationService` 与 `NationOperationalSupport`
  - 在线世界与坐标来自 `OnlinePlayerDirectory`
- 网页地图：
  - `map.js` 的 `buildViewerIntelCard(...)` 改为指标网格
  - 访问者卡片显示余额、身份、国家、国家类型、在线状态、坐标
  - 有国家时额外显示等级、领地、城邦、资源区块容量、领袖、政体、经验、升级进度和距下一级
  - 未认证访问仍显示公共说明，不暴露个人经济数据
  - `styles.css` 新增 `viewer-intel-*` 样式，保持侧边栏紧凑、可扫读、移动端不溢出
- 验证：
  - `node --check src/main/resources/web/map/js/map.js`: PASS on 2026-06-09 10:46 +08:00
  - `mvn -q -DskipTests compile`: PASS on 2026-06-09 10:46 +08:00
  - `mvn -q "-Dtest=MapModuleViewerSnapshotContractTest" test`: PASS on 2026-06-09 10:46 +08:00
  - `mvn -q test`: PASS on 2026-06-09 10:46 +08:00
  - `mvn -q -DskipTests package`: PASS on 2026-06-09 10:47 +08:00
  - 当前 jar: `target/starcore-0.1.0-SNAPSHOT.jar`
  - 当前 jar 大小: `17536900` bytes
  - 当前 jar SHA256: `3D99EA6194CFE0ABBA34032172EACB3B429D3DA11AE66A70DD869649F6249187`

下一步建议：

- 继续做国家财政 Web API / JSON endpoint，让网页管理端可分页查询完整 `treasury.*` 账本。
- 或把网页地图访问者卡片补上可操作按钮，例如聚焦自己、聚焦所属国家、打开圈地模式。

### 2026-06-09 地图国家财政汇总

本轮把 RPG 经济循环已经产生的 `treasury.*` 账本接入网页地图国家详情面板。地图网站现在不只显示资源区块运营状态，也能在选中国家时看到国家金库余额、近期收支汇总和最新财政流水，方便服主/管理组在 RPG 服务器里查账。

- 后端 metadata：
  - `MapModule.polygonFor(...)` 为国家领土 polygon 写入财政字段
  - `treasuryBalance`
  - `financeEventCount`
  - `financeIncomeTotal`
  - `financeRewardTotal`
  - `financeTaxTotal`
  - `financeDepositTotal`
  - `financeWithdrawTotal`
  - `financeNetTotal`
  - 最近 3 条财政流水：`financeEvent0/1/2Type`、`Message`、`At`、`Amount`
- 财政来源：
  - 金库余额来自 `TreasuryService`
  - 流水和汇总来自 `EventService.eventsOf(nationId)` 中的 `treasury.*`
  - 收入、奖励、税收、管理员存入计为正向资金
  - 金库支出计为负向资金
- 网页地图：
  - `map.js` 国家详情面板新增“财政概览”
  - 显示金库余额、近期净额、日常收入、任务奖励、玩家税收、金库支出、管理员存入和流水数量
  - 显示最近 3 条财政流水，包含事件类型、金额、相对时间和绝对时间
  - 中英文文案均已加入，中文为默认管理体验服务
- 测试覆盖：
  - `MapModuleViewerSnapshotContractTest` 锁住国家 polygon 的财政 metadata
  - 测试覆盖 `treasury.income/reward/tax/withdraw` 汇总、最新 3 条流水顺序、金库余额和普通最近事件列表共存
- 最新验证：
  - `mvn -q -DskipTests compile`: PASS on 2026-06-09 10:39 +08:00
  - `mvn -q "-Dtest=MapModuleViewerSnapshotContractTest" test`: PASS on 2026-06-09 10:39 +08:00
  - `mvn -q test`: PASS on 2026-06-09 10:39 +08:00
  - `mvn -q -DskipTests package`: PASS on 2026-06-09 10:40 +08:00
  - 当前 jar: `target/starcore-0.1.0-SNAPSHOT.jar`
  - 当前 jar 大小: `17536645` bytes
  - 当前 jar SHA256: `BAD8375D55948708F43B61B2792ACE76E4B3387E7B98B19971F9F8D957F74456`

下一步建议：

- 继续做网页地图访问者详细信息面板：显示访问玩家余额、所属国家、角色、在线位置、可操作按钮。
- 或继续做国家财政 API / Web JSON endpoint，让外部 RPG 面板和网页管理端可以直接查询完整账本分页。

### 2026-06-09 玩家税收自动调度器

本轮把上一轮的手动玩家税收继续推进到可配置自动结算。自动税收仍然默认关闭，必须同时开启 `nation.economy.tax.enabled` 和 `nation.economy.tax.auto-enabled` 才会启动，避免服主还没确认经济参数就自动扣玩家余额。

- 新增自动配置：
  - `nation.economy.tax.auto-enabled: false`
  - `nation.economy.tax.auto-interval: days`
  - `nation.economy.tax.auto-amount: 1`
- 调度位置：
  - `TreasuryModule.enable(...)` 启动税收任务
  - `TreasuryModule.disable(...)` 取消税收任务
  - 与日常收入自动结算并列，互不影响
- 自动行为：
  - 周期触发时动态查找 `NationService`
  - 使用 `context.economyService()` 扣成员个人余额
  - 按同一税额算法结算：固定税 + 余额百分比税 + 保底余额 + 余额不足跳过/扣到保底
  - 每个国家汇总入国家金库
  - 能找到 `EventService` 时写入 `treasury.tax`
  - 自动事件操作人为 `STARCORE`，原因为 `auto`
- 安全边界：
  - 默认不启动
  - 国家或经济服务不可用时跳过本轮并写日志
  - 单个国家失败不会中断其它国家
  - 事件服务不可用时仍可完成入账，避免因账本模块异常阻断经济循环
- 测试覆盖：
  - `TreasuryModuleTest` 直接锁住自动税收会扣成员余额、入国家金库、记录 `treasury.tax`
  - `ConfigDefaultsCoverageTest` 锁住新增自动税收配置都有默认项
  - 命令/补全测试继续覆盖手动税收入口
- 最新验证：
  - `mvn -q -DskipTests compile`: PASS on 2026-06-09 10:00 +08:00
  - `mvn -q "-Dtest=TreasuryModuleTest,ConfigDefaultsCoverageTest" test`: PASS on 2026-06-09 10:03 +08:00
  - `mvn -q "-Dtest=StarCoreCommandShortAliasDispatchTest,StarCoreCommandTabCompletionTest,TreasuryModuleTest,ConfigDefaultsCoverageTest" test`: PASS on 2026-06-09 10:04 +08:00
  - `mvn -q test`: PASS on 2026-06-09 10:06 +08:00
  - `mvn -q -DskipTests package`: PASS on 2026-06-09 10:07 +08:00
  - 当前 jar: `target/starcore-0.1.0-SNAPSHOT.jar`
  - 当前 jar 大小: `17534246` bytes
  - 当前 jar SHA256: `FBA4F07554799CAA106AAD256FFE102D68AED3BDDF4EE4DDA14323954178780B`

下一步建议：

- 继续做 web/admin 端税收、奖励、日常收入汇总视图。
- 或给税收系统补外部 API，方便 RPG 插件主动触发某个国家/全部国家的税收结算。

### 2026-06-09 玩家税制手动结算

本轮开始落地 RPG 服务器经济循环里的“玩家税收 -> 国家金库”链路。第一版只做管理员手动结算，默认关闭自动扣税，避免未经服主确认改变服务器经济节奏。

- 新增命令：
  - `/sc treasury tax <国家|all> [原因...]`
  - `/sc tr tax <国家|all> [原因...]`
  - `/sc 金库 税收 <国家|全部> [原因...]`
- 权限：
  - 需要 `starcore.admin`
- 默认配置：
  - `nation.economy.tax.enabled: false`
  - `nation.economy.tax.fixed-amount: 0.00`
  - `nation.economy.tax.balance-percent: 0.00`
  - `nation.economy.tax.minimum-balance: 0.00`
  - `nation.economy.tax.skip-insufficient-members: true`
- 税额算法：
  - 先计算成员可税余额：`个人余额 - minimum-balance`
  - 每名成员税额：`fixed-amount + 可税余额 * balance-percent / 100`
  - 税额按 2 位小数向下规范化
  - `skip-insufficient-members=true` 时，余额不足以支付完整税额的成员会跳过
  - `skip-insufficient-members=false` 时，最多扣到 `minimum-balance` 以上的可用余额
- 入账与账本：
  - 从成员个人余额扣款
  - 汇总存入国家金库
  - 写入 `treasury.tax`
  - 账本 context 包含 `actor/amount/balance/taxedMembers/skippedMembers/fixed/percent/minimumBalance/reason`
  - 中文审计字段已补齐：纳税成员、跳过成员、固定税、比例税、保底余额
- Tab 补全：
  - `tr` 子命令包含 `tax/税收/收税`
  - `tr tax <TAB>` 支持国家名和 `all/全部/所有`
  - 事件类型补全包含 `treasury.tax`
- 最新验证：
  - `mvn -q -DskipTests compile`: PASS on 2026-06-09 09:46 +08:00
  - `mvn -q "-Dtest=StarCoreCommandShortAliasDispatchTest,StarCoreCommandTabCompletionTest,ConfigDefaultsCoverageTest" test`: PASS on 2026-06-09 09:49 +08:00
  - `mvn -q test`: PASS on 2026-06-09 09:51 +08:00
  - `mvn -q -DskipTests package`: PASS on 2026-06-09 09:52 +08:00
  - 当前 jar: `target/starcore-0.1.0-SNAPSHOT.jar`
  - 当前 jar 大小: `17530985` bytes
  - 当前 jar SHA256: `DE2A5AC4A5FEB6842D5406EF335308A699935A2225319DB2657E71E55E474C52`

下一步建议：

- 税收自动调度器已在 `2026-06-09 玩家税收自动调度器` 续跑中完成。
- 后续可继续做 web/admin 端税收、奖励、日常收入汇总视图，或补外部税收 API。

### 2026-06-09 外部 RPG 插件 API 文档

本轮把 `TreasuryRewardService` 的外部接入方式写成可直接给 RPG/任务/副本插件作者使用的文档，避免后续接入时靠执行命令或猜测服务注册方式。

- 新增文档：
  - `docs/RPG_PLUGIN_API_INTEGRATION.md`
- 文档覆盖：
  - 外部插件 `plugin.yml` 使用 `softdepend: [STARCORE]` 或 `depend: [STARCORE]`
  - STARCORE jar 作为 `provided` 依赖，不打进外部插件 jar
  - 通过 Bukkit `ServicesManager` 获取 `StarCoreApi`
  - 通过 `StarCoreApi.nationService()` 按国家名查国家
  - 通过 `StarCoreApi.treasuryRewardService()` 发放 RPG 奖励
  - 解释 `TreasuryRewardResult` 的 `nationId/amount/balance/eventRecorded`
  - 说明 `eventRecorded=false` 只代表事件服务缺失，不代表入账失败
  - 给出推荐 reason 文案和常见失败情况
- README 接入：
  - Operations 链接区新增 RPG API 文档入口
  - Public API 段落明确 RPG 插件应使用 `api.treasuryRewardService()` 写 `treasury.reward`
- 验证：
  - `rg -n "RPG_PLUGIN_API_INTEGRATION|treasuryRewardService|TreasuryRewardResult|DungeonRewardBridge|softdepend: \[STARCORE\]|eventRecorded=false" README.md docs src/main/java`: PASS on 2026-06-09 09:34 +08:00
  - `rg -n "## Public API|RPG plugin API integration|RPG treasury rewards|当前最新续跑|外部 RPG/任务插件" README.md docs\CONTINUATION_PLAN_2026-06-05.md docs\LATEST_VERIFICATION_SUMMARY.md`: PASS on 2026-06-09 09:34 +08:00

下一步建议：

- 继续做玩家税制，先落定扣费模型：在线成员扣费、离线余额扣费、国家资源产出抽成，还是混合方案。
- 或继续把 web/admin 端奖励和收入流水做成汇总视图，方便 RPG 服主查账。

### 2026-06-09 TreasuryRewardService / RPG 奖励 API

本轮把上一轮的 RPG 国家奖励命令继续往“可被其它 RPG 插件稳定调用”的方向推进：奖励不再只靠执行 `/sc tr rw` 命令接入，而是有独立服务接口和 `StarCoreApi` accessor。

- 新增公开服务：
  - `TreasuryRewardService`
  - `TreasuryRewardResult`
- API 入口：
  - `StarCoreApi.treasuryRewardService()`
  - 外部插件可通过服务注册表/API 查找奖励服务，调用 `reward(NationId, amount, actor, reason)`
- 模块提供：
  - `TreasuryModule implements TreasuryRewardService`
  - `ModuleMetadata` 同时提供 `TreasuryService.class` 和 `TreasuryRewardService.class`
- 命令桥接：
  - `/sc treasury reward ...`
  - `/sc tr rw ...`
  - 中文别名 `奖励/奖/任务奖励`
  - 命令运行时优先调用 `TreasuryRewardService.reward(...)`
  - 服务缺失时保留旧 fallback，避免模块加载顺序异常时命令完全失效
- 行为：
  - 金额必须为正数
  - 金额按 2 位小数向下规范化
  - 奖励写入国家金库
  - 能找到 `EventService` 时记录 `treasury.reward`
  - 找不到事件服务时仍然完成入账，并返回 `eventRecorded=false`
- 测试覆盖：
  - 命令测试锁住 `/sc tr rw` 会优先调用 `TreasuryRewardService`
  - 模块测试锁住 `TreasuryModule.reward(...)` 会入账、规范金额、写 `treasury.reward` context
  - 模块测试锁住事件服务缺失时仍然入账
- 最新验证：
  - `mvn -q -DskipTests compile`: PASS on 2026-06-09 09:08 +08:00
  - `mvn -q "-Dtest=StarCoreCommandShortAliasDispatchTest,StarCoreCommandTabCompletionTest" test`: PASS on 2026-06-09 09:12 +08:00
  - `mvn -q "-Dtest=TreasuryModuleTest" test`: PASS on 2026-06-09 09:20 +08:00
  - `mvn -q test`: PASS on 2026-06-09 09:22 +08:00
  - `mvn -q -DskipTests package`: PASS on 2026-06-09 09:23 +08:00
  - 当前 jar: `target/starcore-0.1.0-SNAPSHOT.jar`
  - 当前 jar 大小: `17526954` bytes
  - 当前 jar SHA256: `724E9A5021E7F0784D6178FA5E4A775502E6468563E0E13E593A5518F6247CED`

下一步建议：

- 外部 RPG/任务插件 API 使用文档已在 `2026-06-09 外部 RPG 插件 API 文档` 续跑中补到 `docs/RPG_PLUGIN_API_INTEGRATION.md`。
- 下一步更适合继续做玩家税制，但需要先确定扣费模型：在线成员扣费、离线余额扣费、国家资源产出抽成，还是混合方案。

### 2026-06-09 RPG 国家奖励入账

本轮把 RPG 任务/副本/活动奖励接入国家金库主链，避免服主或后续 API 只能用普通 `deposit` 混记奖励来源。

- 新增命令：
  - `/sc treasury reward <国家> <amount> <原因...>`
  - `/sc tr rw <国家> 750 Dungeon clear`
  - `/sc 金库 奖励 <国家> 750 副本通关`
- 权限：
  - 需要 `starcore.admin`
- 行为：
  - 金额必须为正数
  - 直接写入国家金库
  - 独立写入事件类型 `treasury.reward`
  - 不混用 `treasury.deposit`
- 账本：
  - 事件消息使用 `command.event.message.treasury-reward`
  - 事件类型中文显示为 `国家奖励`
  - 结构化 context 包含 `actor/amount/balance/reason`
- Tab 补全：
  - `tr` 子命令包含 `rw/reward/奖励`
  - 第四参数提供常用金额补全
- 最新验证：
  - `mvn -q "-Dtest=StarCoreCommandShortAliasDispatchTest,StarCoreCommandTabCompletionTest" test`: PASS on 2026-06-09 08:52 +08:00
  - `mvn -q test`: PASS on 2026-06-09 08:53 +08:00
  - `mvn -q -DskipTests package`: PASS on 2026-06-09 08:53 +08:00
  - 当前 jar: `target/starcore-0.1.0-SNAPSHOT.jar`
  - 当前 jar 大小: `17523986` bytes
  - 当前 jar SHA256: `D21A2706E6780FA75F341F40ED09B2E5706353978AD2B92B9185B64023582601`

下一步建议：

- 公开 API/服务入口已在 `2026-06-09 TreasuryRewardService / RPG 奖励 API` 续跑中完成。
- 后续可继续做外部 RPG API 文档、玩家税制、或 web/admin 奖励流水汇总。

### 2026-06-09 国家日常收入自动结算调度器（已完成）

本轮把国家日常收入从“手动批量结算”推进到“可配置自动结算”。自动任务默认关闭，只有服主显式设置 `nation.economy.daily-income.auto-enabled: true` 时才启动。

- 调度位置：
  - `TreasuryModule.enable(...)` 读取配置并启动 Bukkit 定时任务
  - `TreasuryModule.disable(...)` 取消任务
- 配置：
  - `nation.economy.daily-income.auto-enabled: false`
  - `nation.economy.daily-income.auto-interval: days`
  - `nation.economy.daily-income.auto-amount: 1`
- 行为：
  - 根据 `auto-interval/auto-amount` 换算 ticks
  - 周期触发时动态查找 `NationService` 和 `EventService`
  - 遍历所有国家
  - 按同一公式结算日常收入
  - 每个国家单独写入 `treasury.income`
  - 自动事件操作人为 `STARCORE`
  - 原因显示为 `自动结算`
- 安全边界：
  - 默认关闭，不改变现有服务器经济节奏
  - 如果 Nation/Event 服务不可用，本轮结算跳过并写日志
  - 单个国家结算失败不会中断其它国家
- 最新验证：
  - `mvn -q -DskipTests compile`: PASS on 2026-06-09 08:32 +08:00
  - `mvn -q "-Dtest=StarCoreCommandShortAliasDispatchTest,StarCoreCommandTabCompletionTest" test`: PASS on 2026-06-09 08:34 +08:00
  - `mvn -q test`: PASS on 2026-06-09 08:35 +08:00
  - `mvn -q -DskipTests package`: PASS on 2026-06-09 08:35 +08:00
  - 当前 jar: `target/starcore-0.1.0-SNAPSHOT.jar`
  - 当前 jar 大小: `17523521` bytes
  - 当前 jar SHA256: `14F3A9919B3C57967EDB8A1358B4B686238969636F463CF396497B1F41D44328`

下一步建议：

- `/sc tr reward <国家> <amount> <原因...>` 已完成；后续可做 API 入口，给外部 RPG 任务插件直接调用。
- 或继续做“玩家税收”前的税制设计：明确是否扣在线成员、全体离线余额，还是从资源产出抽成。

### 2026-06-09 国家日常收入批量结算

本轮把上一次的单国家日常收入结算扩展成批量结算，并把自动结算配置先预留到配置服务里。

- 新增批量命令：
  - `/sc treasury income all [原因...]`
  - `/sc tr inc all Batch settlement`
  - `/sc 金库 收入 全部 每日结算`
- 批量行为：
  - 遍历 `NationService.nations()`
  - 每个国家按同一公式单独结算
  - 每个国家单独写入 `treasury.income` 事件
  - 命令输出汇总已结算国家数和总金额
- Tab 补全：
  - `/sc tr inc <TAB>` 现在包含 `all` / `全部` / `所有`
- 自动结算预留配置（已在自动结算调度器续跑中接入）：
  - `nation.economy.daily-income.auto-enabled: false`
  - `nation.economy.daily-income.auto-interval: days`
  - `nation.economy.daily-income.auto-amount: 1`
  - `ConfigurationService` 已提供读取方法
  - 默认仍不启动自动任务，避免未经确认改变服务器经济节奏
- 最新验证：
  - `mvn -q "-Dtest=StarCoreCommandShortAliasDispatchTest,StarCoreCommandTabCompletionTest" test`: PASS on 2026-06-09 08:19 +08:00
  - `mvn -q test`: PASS on 2026-06-09 08:20 +08:00
  - `mvn -q -DskipTests package`: PASS on 2026-06-09 08:20 +08:00
  - `mvn -q -DskipTests compile`: PASS on 2026-06-09 08:32 +08:00
  - `mvn -q "-Dtest=StarCoreCommandShortAliasDispatchTest,StarCoreCommandTabCompletionTest" test`: PASS on 2026-06-09 08:34 +08:00
  - `mvn -q test`: PASS on 2026-06-09 08:35 +08:00
  - `mvn -q -DskipTests package`: PASS on 2026-06-09 08:35 +08:00
  - 当前 jar: `target/starcore-0.1.0-SNAPSHOT.jar`
  - 当前 jar 大小: `17523521` bytes
  - 当前 jar SHA256: `14F3A9919B3C57967EDB8A1358B4B686238969636F463CF396497B1F41D44328`

下一步建议：

- 自动结算调度器已完成；后续优先做 RPG 任务奖励入账或玩家税制。

### 2026-06-09 国家日常收入结算

本轮把 RPG 经济主链往前推进了一步：先做“可配置、可审计、手动触发”的国家日常收入结算，不自动扣玩家钱，也不自动跑周期任务，避免在测试服/正式服未确认税制前影响经济。

- 新增命令：
  - `/sc treasury income <国家> [原因...]`
  - `/sc tr inc <国家> Daily settlement`
  - `/sc 金库 收入 <国家> 每日结算`
- 权限：
  - 需要 `starcore.admin`
- 默认配置新增到 `config.yml`：
  - `nation.economy.daily-income.base-amount: 100.00`
  - `nation.economy.daily-income.per-member: 25.00`
  - `nation.economy.daily-income.per-claim: 5.00`
- 结算公式：
  - `基础金额 + 成员数 * per-member + 领地数 * per-claim`
- 默认示例：
  - 1 名成员、4 个领地区块时收入为 `100 + 1*25 + 4*5 = 145.00`
- 账本接入：
  - 写入事件类型 `treasury.income`
  - 事件消息包含操作人、金额、成员数、领地数、原因
  - 结构化 context 包含 `actor/amount/balance/members/claims/reason`
  - 审计输出已能中文显示成员数和领地数
- Tab 补全：
  - `tr` 子命令包含 `inc/income/收入`
  - 第三个参数继续补国家名
- 最新验证：
  - `mvn -q -DskipTests compile`: PASS on 2026-06-09 08:00 +08:00
  - `mvn -q "-Dtest=StarCoreCommandShortAliasDispatchTest,StarCoreCommandTabCompletionTest" test`: PASS on 2026-06-09 08:01 +08:00
  - `mvn -q test`: PASS on 2026-06-09 08:02 +08:00
  - `mvn -q -DskipTests package`: PASS on 2026-06-09 08:02 +08:00
  - 当前 jar: `target/starcore-0.1.0-SNAPSHOT.jar`
  - 当前 jar 大小: `17518843` bytes
  - 当前 jar SHA256: `BD26AE86A4BA1B62186B93494D10A8CC1C66827D6E3AA84F5477CFC558FDAB6E`

下一步建议：

- 做“税率/玩家税收”前先设计清楚扣费对象：在线玩家、全体成员离线余额、还是只对国家资源产出抽成。
- 更稳的下一步是做 `treasury income all` 批量结算全部国家，并接入纪元/每日周期任务开关；默认仍关闭自动结算。
- 如果要接 RPG 任务系统，建议先新增 `/sc tr reward <国家> <amount> <原因...>` 或 API 入口，统一写 `treasury.reward`。

### 2026-06-09 管理员账本导出（已完成）

本轮接着事件账本分页继续做服主管理工具化，已把国家事件账本导出落成可测试命令。

- 新增 `/sc event export <国家> [finance|treasury|resource|all] [时间窗口] [csv|json]`
- 短命令与中文入口：
  - `/sc ev ex <国家> finance 7d csv`
  - `/sc ev ex <国家> 24h json`
  - `/sc 事 导出 <国家> 财政 7天 csv`
- 导出权限：需要 `starcore.admin`
- 默认筛选：不写筛选时默认导出 `finance`，即 `treasury.*` 和 `resource.*`
- 导出目录：
  - 正式运行：插件数据目录 `exports/events/`
  - 单元测试：`target/starcore-event-exports-test/`
- CSV/JSON 字段：
  - `id`
  - `nationId`
  - `occurredAt`
  - `type`
  - `localizedType`
  - `message`
  - `context`
- 已补稳定性处理：
  - 文件名国家名/筛选名安全化
  - CSV 双引号转义
  - JSON 字符串转义
  - 导出格式限定为 `csv/json`
- Tab 补全已补：
  - `event` 子命令含 `ex/export/导出`
  - 导出筛选含 `finance/treasury/resource/all/财政`
  - 导出格式含 `csv/json`
- 新增回归测试已锁住：
  - CSV 导出只包含财政/资源账本，不包含战争事件
  - CSV 内容正确转义双引号
  - JSON 导出支持 `24h` 时间窗口并过滤旧账
  - 导出相关 Tab 补全
- 最新验证：
  - `mvn -q "-Dtest=StarCoreCommandShortAliasDispatchTest,StarCoreCommandTabCompletionTest" test`: PASS on 2026-06-09 07:28 +08:00
  - `mvn -q test`: PASS on 2026-06-09 07:29 +08:00
  - `mvn -q -DskipTests package`: PASS on 2026-06-09 07:29 +08:00
  - 当前 jar: `target/starcore-0.1.0-SNAPSHOT.jar`
  - 当前 jar 大小: `17517258` bytes
  - 当前 jar SHA256: `B25869820FAA4C2808FD60B807AD6900CAD756C52EED3AC5FEFB7ECBF8836BD9`

下一步建议：

- 做“税率 / 日常国库收入 / RPG 任务奖励入账”主链，把导出的账本真正接到 RPG 服务器经济循环。
- 或者做“国家公告 / 管理员审计页”网页只读面，把最近账本摘要展示给服主，但完整导出仍保留命令侧。

### 2026-06-09 事件账本分页与时间窗口（已完成）

本轮继续完善国家事件 / 财政账本命令，目标是让 RPG 服主能直接追溯“谁在什么时间对国家资金、资源和运营状态做了什么”，并避免事件多以后刷屏。

- `/sc event list <国家> [筛选] [时间窗口] [页码] [每页数量]` 已接入分页和时间窗口。
- `/sc event audit <国家> [finance|treasury|resource|all] [时间窗口] [页码] [每页数量]` 已接入分页和时间窗口。
- 短命令保持可用：
  - `/sc ev a <国家> finance`
  - `/sc ev a <国家> finance 24h`
  - `/sc ev a <国家> finance 2 25`
  - `/sc ev a <国家> finance 24h 2 25`
  - `/sc ev ls <国家> resource 7d 1 10`
- 时间窗口支持：
  - `24h`, `7d`, `30d`
  - `1天`, `7天`
  - `分钟`, `小时`, `天`
- 第四个参数如果是时间窗口或页码，会自动走默认筛选：
  - 审计默认 `finance`
  - 列表默认 `all`
- Tab 补全已补：
  - 第 5 参数提示 `1`, `2`, `3`, `24h`, `7d`, `30d`, `1天`, `7天`
  - 第 6 参数提示 `10`, `25`, `50`
- 新增回归测试已锁住：
  - 审计分页只显示目标页事件
  - `24h` 时间窗口过滤旧事件
  - `/sc ev a <国家> 24h` 不会把 `24h` 误当成筛选类型
  - 筛选后非法查询参数会返回 `command.event.invalid-page`
- 最新验证：
  - `mvn -q "-Dtest=StarCoreCommandShortAliasDispatchTest,StarCoreCommandTabCompletionTest" test`: PASS on 2026-06-09 07:16 +08:00
  - `mvn -q test`: PASS on 2026-06-09 07:18 +08:00
  - `mvn -q -DskipTests package`: PASS on 2026-06-09 07:18 +08:00
  - 当前 jar: `target/starcore-0.1.0-SNAPSHOT.jar`
  - 当前 jar 大小: `17512777` bytes
  - 当前 jar SHA256: `346C3B5E7B03DD06949FE6F1F70F34247083E88097053409AE064E087107AE84`

下一步建议继续做“账本导出 / 管理员审计工具化”（已在 2026-06-09 管理员账本导出续跑中完成基础版）：

- 给 `/sc ev a` 增加 `export` 或独立 `/sc ev export`，把指定国家指定窗口的财政 / 资源账本导出为 CSV 或 JSON。
- 在网页地图的国家详情里只展示最近少量事件，详细审计仍留给命令或导出文件，避免网页变成后台管理面。
- 后续如果要做制裁、税率、战争赔款、任务奖励系统，统一通过同一套 `EventService + structured context` 写账，避免每个模块散写审计日志。

### 2026-06-08 文档校准（历史记录，已被 2026-06-10 intel-only 契约取代）

这次续做先把文档口径对齐到当前文件扫描结果，避免继续在“网页指挥面已恢复 / 已移除”两套结论之间来回摇摆。

- 这段是 2026-06-08 当时的漂移诊断；当前真实口径请看本文件顶部的 2026-06-10 记录。
- 当时存在一个必须先处理的前端资产漂移：
  - 打包源 `src/main/resources/web/map` 当时仍包含 `#section-resource-command`、`#resource-command-panel`、`resourceCommand*` 文案/状态和 `nationDetailOpenCommand`
  - 静态预览壳 `D:\qwq\项目\mapadd\map` 当时没有 `resourceCommand` / `open-resource-command` / `section-resource-command` 匹配
  - 插件 runtime 副本 `D:\qwq\项目\mapadd\test-server-paper-1.21.11\plugins\map` 当时也包含 `#section-resource-command`、`#resource-command-panel`、`resourceCommand*` 和 `open-resource-command`
- 因此，当时不能只引用 `docs/LATEST_VERIFICATION_SUMMARY.md` 里的“source / preview / runtime 都无匹配”结论；它和当时打包源文件扫描不一致。
- 当时下一轮第一件事不是继续堆产品文案，而是确定网页资源区块面板的产品口径：
  - 如果保留 web 端资源区块指挥面，就把预览壳、runtime 副本、Browser smoke 和 HUD 专项文档全部同步回 `#resource-command-panel` 契约
  - 如果继续走 BlueMap-like 情报面，就从打包源 `index.html` / `styles.css` / `map.js` 移除残留 DOM、样式、文案、状态和事件钩子，再同步预览壳/runtime 副本
- 当时从预览/runtime 结果看，产品方向更接近“情报 / 选择 / 定位 / 队列筛选 / 运营概览”，而不是 web 端迁移确认控制台；当前已经选择并验证 intel-only 情报面。
- 资源区块真实迁移操作已有游戏内命令、信标 GUI、共享后端主链和 Paper 深烟测覆盖；网页地图是否保留迁移入口，需要先做一次明确产品决定。
- 当时已发现一个验证契约漂移点：`scripts/smoke-starcore-map-browser.mjs` 仍保留旧的 `#resource-command-panel` / `resourceCommand*` 断言和 `confirmUi=true` PASS 文案。
- 后续若要跑完整 Browser smoke，必须先让脚本和三份前端资产对齐：
  - 保留指挥面时，断言三份前端资产都存在并可用
  - 移除指挥面时，断言三份前端资产都无残留，并改成国家运营面、优先队列、筛选、选中态、地图高亮、最近日志查看/定位动作断言
  - 不论选择哪条路，都要将 `confirmUi=true` 改成能真实描述当前契约的字段，避免旧 PASS 文案误导发布判断
- 最新可继续引用的验证摘要：
  - `node --check` 已通过三份 `map.js` 副本
  - `mvn -q test` 为 `154` tests / `0 failures` / `0 errors` / `0 skipped`
  - `mvn -q package` 通过
  - 最新完整 smoke 工件为 `target/smoke-harness-20260608-094703/smoke-summary.json`
  - 最新 HUD 静态视觉证据为 `zcode-map-preview-desktop-clean15.png` 和 `zcode-map-preview-mobile-open-clean15.png`

#### 复核命令（下次续做先跑）

从 `D:\qwq\项目\mapadd` 执行：

```powershell
$pattern = 'resourceCommand|open-resource-command|nationDetailOpenCommand|section-resource-command'
rg -n $pattern 'starcore\src\main\resources\web\map' 'map' 'test-server-paper-1.21.11\plugins\map'
```

当时这条命令的合理预期是：

- `starcore\src\main\resources\web\map` 有命中
- `test-server-paper-1.21.11\plugins\map` 有命中
- `map` 静态预览壳无命中

当前 2026-06-10 的合理预期已改为三份资产都无旧 command UI 命中，并由 `check-map-hud-contract.ps1 -RequireMirrorRoots` 验证。

#### HUD 契约决策分支

以下是历史决策分支。当前已选择 intel-only 资源区块情报面；未来若重新引入 web 端指挥面，才按“保留”分支同步修改三份资产、Browser smoke 和 contract gate。

保留 web 端资源区块指挥面时，下一步按这个顺序走：

1. 确认 `src/main/resources/web/map/index.html`、`css/styles.css`、`js/map.js` 的 resource-command DOM / 样式 / JS 钩子完整。
2. 执行 `starcore\scripts\sync-map-preview-shell.ps1`，把静态预览壳同步回与打包源一致。
3. 确认插件 runtime 副本也与打包源一致。
4. 更新 `scripts/smoke-starcore-map-browser.mjs`，保留 `#resource-command-panel` 断言，但把 `confirmUi=true` 改成更明确的 PASS 字段。
5. 刷新 `docs/LATEST_VERIFICATION_SUMMARY.md` 和 HUD 截图证据。

移除 web 端资源区块指挥面时，下一步按这个顺序走：

1. 从打包源 `index.html` 移除 `#section-resource-command` / `#resource-command-panel`。
2. 从 `styles.css` 移除 `resource-command-*` 样式。
3. 从 `map.js` 移除 `resourceCommand*` 状态、文案、提交/取消、`open-resource-command` 动作和相关事件钩子。
4. 同步 `map` 静态预览壳和 `test-server-paper-1.21.11\plugins\map` runtime 副本。
5. 更新 Browser smoke，改断言国家运营面、资源区块筛选、选中态、marker 高亮、最近日志查看/定位动作。
6. 刷新 `docs/LATEST_VERIFICATION_SUMMARY.md` 和 HUD 截图证据。

### 本轮已经重新确认

- 已执行 `C:\Users\l\.codex\scripts\auto-pull-codex-stack.ps1`
- `mvn -q test` 通过
- `mvn -q package` 通过
- `mvn -q "-Dtest=StarCoreCommandShortAliasDispatchTest,StarCoreCommandTabCompletionTest" test` 通过（2026-06-09 07:16 +08:00）
- `mvn -q test` 通过（2026-06-09 07:18 +08:00）
- `mvn -q -DskipTests package` 通过（2026-06-09 07:18 +08:00）
- `mvn -q "-Dtest=StarCoreCommandShortAliasDispatchTest,StarCoreCommandTabCompletionTest" test` 通过（2026-06-09 07:28 +08:00）
- `mvn -q test` 通过（2026-06-09 07:29 +08:00）
- `mvn -q -DskipTests package` 通过（2026-06-09 07:29 +08:00）
- `mvn -q -DskipTests compile` 通过（2026-06-09 08:00 +08:00）
- `mvn -q "-Dtest=StarCoreCommandShortAliasDispatchTest,StarCoreCommandTabCompletionTest" test` 通过（2026-06-09 08:01 +08:00）
- `mvn -q test` 通过（2026-06-09 08:02 +08:00）
- `mvn -q -DskipTests package` 通过（2026-06-09 08:02 +08:00）
- `mvn -q "-Dtest=StarCoreCommandShortAliasDispatchTest,StarCoreCommandTabCompletionTest" test` 通过（2026-06-09 08:19 +08:00）
- `mvn -q test` 通过（2026-06-09 08:20 +08:00）
- `mvn -q -DskipTests package` 通过（2026-06-09 08:20 +08:00）
- `mvn -q clean test package` 通过
- 当前测试总数: `154`
- 当前测试结果: `0 failures / 0 errors / 0 skipped`
- 当前打包产物: `target/starcore-0.1.0-SNAPSHOT.jar`
- 当前 jar 大小: `17520975` bytes
- 当前 jar SHA256: `41EA2072C43D51F9B629F6CC9BC0E7770CFA5D836FDE760BEC2BDA174E55C0CD`
- 当前 runtime tool selfcheck: `ok`
- 当前 ProtectorAPI 参考契约检查: `ok`
- 发布验证总控脚本现已补齐：`scripts/verify-starcore-release.ps1`
- ProtectorAPI 参考契约检查脚本现已补齐：`scripts/check-protectorapi-reference.ps1`
- 最新验证摘要汇总脚本现已修复：`scripts/build-latest-verification-summary.ps1`
- 发布渠道文案包现已补齐：`docs/RELEASE_CHANNEL_PACK_2026-06-05.md`
- 已克隆 `references/ProtectorAPI` 作为外部保护桥 API 漂移参考
- 本地 `references/ProtectorAPI` 已重新 `fetch origin` 并确认 `HEAD == origin/main`
- 最新轻量验证摘要已刷新：`docs/LATEST_VERIFICATION_SUMMARY.md`
- 最新轻量验证摘要当前对应时间：`2026-06-07 23:18:25 +08:00`
- 最新 ProtectorAPI 参考检查已刷新到 `20260606-174430`
- 最新完整 smoke 已刷新到 `20260607-230034`
- 最新 runtime tool selfcheck 已刷新到 `20260606-111058`
- 最新发布证据包已刷新到 `20260606-045450`
- 最新发布渠道成品包已刷新到 `20260606-045452`
- 本轮已继续把国家资源区块迁移事件接入国家最近操作日志：
  - 资源区块迁移请求
  - 迁移目标已选定
  - 正常迁移完成
  - 强制迁移完成
- 国家领地元数据现已补齐 `recentEventCount / recentEvent{n}Type / recentEvent{n}Message / recentEvent{n}At`
- 国家领地元数据现已补齐 `recentEvent{n}ResourceId`，用于把最近日志条目直接联动到资源区块动作
- 网页国家详情面现已显示“最近操作日志”块，并复用后端权威国家事件流
- 浏览器烟测现已正式锁住“最近操作日志”块：
  - 标签存在
  - 至少 1 条最近事件存在
  - 最近事件文本已真实渲染
  - PASS 输出会直接带出 `recentLog=<count>`
- 本轮已修正最近操作日志资源动作的 ID 语义漂移：
  - 事件上下文里保存的资源区块 UUID 现会统一映射成网页地图资源标记 ID `resource:<districtId>`
  - 最近操作日志里的 `查看资源区块 / 定位资源区块` 已重新接回真实资源区块
  - `打开指挥面` 当前不能作为稳定发布目标，因为打包源、预览壳、runtime 副本对该面板是否存在并不一致
  - 最新完整 smoke 的旧 `recentLogCommandOk=false` 问题可作为历史修复记录保留；下一轮 Browser smoke 应先按最终产品契约决定是否继续断言该动作

### 本轮已修复 / 已补强

- 新增 `SqlResolutionStateStorageTest`
- 新增 `ProtectorApiBridgeContractTest`
- 新增 `ProtectorApiExternalProtectionServiceTest`
- 新增 `NationModuleRulesTest`
- 新增 `MapModuleViewerSnapshotContractTest`
- 新增 `NationModuleResourceProgressionTest`
- 新增 `NativeNationResourceDistrictServiceFlowTest`
- 新增 `ExternalProtectionServicesTest`
- 新增 `preview-starcore-map.ps1`
- 新增 `selfcheck-starcore-runtime-tools.ps1`
- 本轮文档校准当时发现 HUD 资产未完全收口：
  - `src/main/resources/web/map/index.html` 当时仍挂载 `#resource-command-panel`
  - `src/main/resources/web/map/js/map.js` 当时仍保留 `resourceCommand*` 状态、文案和更新钩子
  - `src/main/resources/web/map/css/styles.css` 当时仍保留 `resource-command-*` 样式
  - 插件 runtime 副本 `D:\qwq\项目\mapadd\test-server-paper-1.21.11\plugins\map` 当时也保留同一套 resource-command 面板和 JS 逻辑
  - 静态预览壳 `D:\qwq\项目\mapadd\map` 当时没有这些 resource-command 匹配
  - 该漂移已在后续 intel-only HUD 契约收口中处理，当前由 `check-map-hud-contract.ps1 -RequireMirrorRoots` 断言三份资产旧 command UI 匹配数为 0
- `resolution` 模块 SQL 持久化现已补齐：
  - legacy `.properties` 导入测试
  - SQL round-trip 覆盖式重载测试
  - `JoinNation / RenameNation / ChangeGovernment / ChangeDiplomacyRelation` 编码回放覆盖
- `ProtectorAPI` 兼容桥现已补齐：
  - 新增 `ProtectorApiBridgeContract`，把 STARCORE 真正依赖的反射入口集中收口
  - 反射契约绑定单测
  - 反射桥摘要输出单测
  - 受保护区块冲突识别单测
  - 关闭集成后的短路行为单测
  - 测试侧仅使用最小 stub 镜像 `LinsMinecraftStudio/ProtectorAPI` 的反射入口，不把 ProtectorAPI 当成 compile-time 依赖
  - 新增 `ExternalProtectionServices / ProtectorApiBridgeProvider / CompositeExternalProtectionService`
  - `StarCorePlugin` 不再直接写死单一桥接实例化入口，为后续兼容更多外部保护来源预留稳定扩展口
  - 兼容桥提供者现已升级为 `ServiceLoader + META-INF/services` 发现模式，默认内建 `ProtectorApiBridgeProvider`
  - `ExternalProtectionServicesTest` 会锁住 provider 去重、排序和 fallback 逻辑，避免后续扩桥时退回硬编码
  - 新增 `GuardedExternalProtectionService`，把 provider 初始化失败、summary 读取失败、运行时查询异常隔离成单桥故障，不再拖垮整条外部保护链
  - 新增 `scripts/check-protectorapi-reference.ps1`，会自动检查：
    - `references/ProtectorAPI` 本地 checkout 是否存在
    - `ProtectorAPI.findModule(Location)` / `getAllAvailableProtectionModules()` 是否仍存在
    - `IProtectionModule.getPluginName()` / `getProtectionRangeInfo(Location)` 是否仍存在
    - `IProtectionRange.getDisplayName()` / `getId()` 是否仍存在
    - `plugin.yml softdepend: [ProtectorAPI]` 是否漂移
    - `ProtectorApiBridgeContract` 是否仍绑定同一套窄接口
    - `META-INF/services/dev.starcore.starcore.foundation.protection.ExternalProtectionBridgeProvider` 是否仍注册 `ProtectorApiBridgeProvider`
- `NationModule` 服规回归护栏现已补齐：
  - `createNation(...)` 历史建国次数上限测试
  - `createCityState(...)` 个人城邦上限测试
  - `createCityState(...)` 国家城邦上限测试
  - `addMember(...)` 成员上限测试
  - `previewClaimSelection(...)` 领袖专属圈地测试
  - `previewClaimSelection(...)` 等级领地上限测试
- 资源区块领袖命令面已继续补齐：
  - `/sc rsc i <world:x:z>` / `/starcore resource inspect <world:x:z>`
  - `/sc rsc m <world:x:z>` / `/starcore resource migrate <world:x:z>`
  - `inspect` / `migrate` 支持短别名 `i / m`
  - 已补中文别名 `查看 / 检查 / 详情 / 迁移`
  - 玩家省略坐标时会默认回落到当前所在区块
  - tab 补全现已覆盖资源区块坐标建议、中文子命令和短别名
- 地图访问者快照与资源区块成长护栏现已补齐：
  - `MapModuleViewerSnapshotContractTest`
  - `NationModuleResourceProgressionTest`
  - `NativeNationResourceDistrictServiceFlowTest`
  - 自动锁住访问者地图 JSON 里的 `balance / nation / government / role / online / nationExperience / claimCount / resourceDistrictCount`
  - 自动锁住资源区块地图层元数据里的 `migrationLabel / beaconPosition / nextRefreshAt / forceMigrationAt / pendingTarget`
  - 自动锁住 `claimSelection(...)` 成功后会触发资源区块补齐
  - 自动锁住 `addExperience(...)` 只有在国家等级实际提升时才触发资源区块扩容检查
  - 自动锁住 `ensureDistricts(...)` 会按国家已拥有领地和等级上限分配资源区块
  - 自动锁住 `WAITING_DEPLETION` 超时后会完成强制迁移，并把区块状态重置回 `none`
  - 自动锁住 `WAITING_DEPLETION` 在资源未清空且未到强制迁移超时前，会继续保持等待状态，不能提前迁移
  - 自动锁住 `beginMigration(...)` 会把资源区块迁移状态切到 `awaiting_target`，并通过 `MigrationCoreSupport` 发放迁移核心
  - 自动锁住 `handleMigrationTarget(...)` 会在合法本国区块落点后切到 `waiting_depletion`，并通过 `MigrationCoreSupport` 消耗迁移核心
  - 自动锁住资源区块迁移事件会进入国家最近操作日志
- 网页地图资源区块情报面已补强：
  - tooltip 现在会显示迁移状态中文标签、目标区块、下次刷新时间、强制迁移时间和信标坐标
  - 资源区块侧边栏列表现在会显示多行运行态摘要，不再只剩生物群系和资源数
  - 前端双语状态文本已统一走 `migrationState` 语义映射，避免英文界面夹杂后端中文文案
  - 资源区块 tooltip / 列表 / 指挥面板 现在会显示理论单轮资源、理论单轮经验和理论刷新周期
  - 资源区块 tooltip / 列表 / 指挥面板 / 国家详情优先区块面 现在还会继续显示：
    - 小时折算资源收益
    - 小时折算经验收益
    - 未来 3 轮资源预估
    - 未来 3 轮经验预估
    - 未来 3 轮预计时长
  - 已新增资源区块命令面板，可在网页地图侧边栏选中资源区块后直接发起迁移
  - 后端已新增 `POST /api/map/resource-district/migrate`
  - 网页迁移现在复用游戏内 `beginMigration(...)` 主链，而不是另写一套旁路逻辑
  - 网页迁移响应现在会直接返回资源区块最新迁移状态、目标、刷新时间、强制迁移时间、信标坐标和访问者余额
  - 本轮已继续把网页资源区块迁移资格判断收口到后端权威字段：
    - `migrationCost`
    - `migrationCostRequired`
    - `viewerOwnsDistrictNation`
    - `viewerIsNationLeader`
    - `viewerOnline`
    - `viewerCanAffordMigration`
    - `canStartMigration`
    - `migrationActionState`
  - 前端资源区块指挥面板现已优先消费这组后端字段，不再只靠前端本地猜测领袖 / 本国 / 在线 / 余额条件
  - 资源区块指挥面板现在会直接显示迁移花费、访问者余额、是否足额，以及更明确的不可迁移原因
  - 资源区块指挥面板本轮已继续升级成“国家运营状态面”：
    - 当前阶段
    - 下一步操作
    - 当前限制
    - 迁移资金缺口
  - 新增后端权威字段 `migrationBalanceShortfall`，网页地图不再只会提示“余额不足”，而是能直接显示还差多少
  - 资源区块详情长文本现在允许换行显示，阶段说明、下一步和限制原因不再被单行截断
  - 前端现在会先就地应用这份权威迁移结果，再异步拉全量快照对齐，避免只靠下一轮快照才看到状态变化
  - 地图迁移开关已接入 `map.web.resource-district-management-enabled`
  - 当前网页迁移要求国家领袖在线，因为迁移核心仍通过游戏内背包发放
  - 浏览器烟测现在也会断言资源区块指挥面板里的迁移花费、访问者余额、按钮禁用态和 `migrationActionState`
  - `NationResourceDistrictOperationalOverview` / `MapModule` 本轮已继续补齐后端权威预报字段：
    - `expectedResourceYieldPerHour`
    - `expectedExperienceYieldPerHour`
    - `forecastResourceYieldNext3Cycles`
    - `forecastExperienceYieldNext3Cycles`
    - `forecastWindowMinutesNext3Cycles`
  - 本轮已继续把资源区块 marker / tooltip / 选中指挥面板升级成更接近“国家运营面”的状态：
    - 国家类型
    - 政体
    - 国家等级
    - 国家经验
    - 成员数
    - 领地容量
    - 资源区块容量
    - 当前访问者身份
    - 当前访问者在线状态
  - 资源区块选中态现在不只是“能不能点迁移”，还会直接展示该资源区块所属国家的运营容量和领袖管理语义
  - 浏览器烟测本轮已继续锁住资源区块面板里的国家政体、身份、领地容量和资源区块容量，避免只验证迁移按钮而遗漏运营信息
  - 本轮已继续把国家运营信息统一往“地图快照 / 命令输出 / 浏览器烟测”三层落稳：
    - 新增 `NationOperationalSupport / NationOperationalOverview / NationLevelProgress`
    - `MapModuleViewerSnapshotContractTest` 现已锁住：
      - `founderName`
      - `nationExperienceProgress`
      - `nationNextLevelExperience`
      - `nationExperienceRemaining`
      - `nationMaxLevelReached`
      - `cityStateCount`
      - `cityStateLimit`
    - `/sc n i <国家>` 现在会显示：
      - 国家领袖
      - 等级进度
      - 城邦容量
      - 资源区块容量
    - `/sc rsc i <world:x:z>` 现在会额外显示：
      - 国家领袖
      - 国家政体
      - 国家成员数
      - 国家等级与总经验
      - 国家等级进度
      - 国家领地容量
      - 国家城邦容量
      - 国家资源区块容量
    - `messages_zh_cn.yml` 已补齐上述命令面的中文消息键
    - `StarCoreCommandShortAliasDispatchTest` 已补命令层护栏，锁住 `n i` 与 `rsc i` 的新国家运营输出
    - 浏览器烟测脚本现已继续接入：
      - `viewer founder`
      - `viewer city-state capacity`
      - `viewer progress`
      - `viewer next-level remaining`
      的参数链、DOM 断言与 smoke harness 结果传递
    - 国家运营预报计算本轮已从“单轮收益直接乘时间窗”收口到“按每小时产能折算”：
      - 避免不同资源区块刷新周期差异时出现 6h / 12h 预估偏差
      - 国家运营概览与资源区块未来 3 轮预报现在统一基于同一套小时折算语义
    - 本轮已真实重跑：
      - `mvn -q test`
      - `mvn -q package`
      - `scripts/smoke-starcore-paper-integration.ps1 -BrowserSmoke -ProtectorApiSmoke`
    - 本轮已修正 `MapModuleViewerSnapshotContractTest` 对最近事件顺序的时序脆弱性：
      - 测试改为显式事件时间戳
      - 不再依赖 `Instant.now()` 的瞬时顺序碰运气
    - 浏览器烟测本轮已继续锁住：
      - 当前阶段
      - 下一步操作
      - 当前限制
      - 在线状态
      - 迁移资金缺口
      这些资源区块运营字段的 DOM 呈现
    - `MapModuleViewerSnapshotContractTest` 本轮已新增 `migrationBalanceShortfall` 护栏，并补一条低余额路径，锁住 `insufficient-balance` 状态与缺口计算
    - 最新组合烟测 `20260606-132907` 已通过，说明资源区块网页运营状态面和浏览器新断言也已随真实运行态跑通
    - 浏览器烟测本轮已继续真实点击网页资源区块两步确认流，锁住：
      - 第一次点击进入确认态
      - 取消按钮出现
      - 当前阶段 / 下一步 / 状态文本切换到确认语义
      - 取消后按钮和状态能正确复位
    - 本轮已继续补齐资源区块网页命令面的专用样式，并把 `migrationBalanceShortfall` 同步抬到 `#resource-command-meta`，与浏览器烟测的元信息断言保持一致
    - 最新组合烟测 `20260606-151340` 已再次通过，说明资源区块网页命令面在最新源码下仍满足：
      - 迁移花费 / 访问者余额 / 资金缺口元信息可见
      - 当前阶段 / 下一步 / 当前限制详情可见
      - 两步确认 / 取消复位 UI 流仍可真实跑通
    - 本轮已继续把网页圈地面板补成更接近运营视角的定价面：
      - 显示当前容量 / 预览后容量 / 重叠区块
      - 显示基础单价 / 预览均价
      - 显示高价区块明细
      - 明细里直接显示距离、资源丰富度、距离系数、生物群系系数
    - 后端 `claimPreviewJson(...)` 本轮已继续把圈地价格明细按 `price DESC` 输出，网页地图不再只按选区遍历顺序展示区块，而是优先展示最贵、最值得关注的区块
    - `MapModuleViewerSnapshotContractTest` 本轮已新增网页圈地预览 JSON 护栏，锁住：
      - `pricing.baseChunkPrice`
      - `pricing.detailLimit`
      - 高价区块优先排序
    - 本轮已继续把网页地图国家列表补成“选中国家运营面”：
      - 国家列表下方新增国家运营详情面板
      - 可显示国家领袖、政体、国家类型、外交关系、等级、总经验、升级进度、距下一级、成员数、领地容量、城邦容量、资源区块容量
      - 若当前选中的资源区块属于该国家，则国家详情面会直接联动显示该资源区块的单轮收益、刷新周期、迁移状态、资金缺口与当前目标
    - `polygonFor(...)` 本轮已继续补厚领地多边形元数据，网页国家详情面不再只靠列表最小字段，而是直接复用后端权威的国家运营字段
    - `MapModuleViewerSnapshotContractTest` 本轮已新增国家领地元数据护栏，锁住：
      - `nation / nationKind / government`
      - `claimCount / claimLimit`
      - `founderName / memberCount`
      - `nationLevel / nationExperience / nationExperienceProgress`
      - `cityStateLimit / resourceDistrictLimit`
    - 本轮已继续把国家详情面从“只读”推进到“可操作”：
      - 新增国家级资源区块总数
      - 新增国家级待处理迁移数
      - 新增国家级理论总单轮收益
      - 新增快捷按钮：聚焦国家 / 选中资源区块 / 定位资源区块
    - 这一步让国家详情面已经不只是说明板，而是可以作为网页国家运营面的入口继续往下走
    - 本轮已继续把国家详情面升级为“资源区块优先队列”：
      - 按 `migrationActionState`、理论单轮收益、资金缺口排序本国资源区块
      - 直接突出“等待选点 / 等待枯竭 / 可立即迁移 / 余额不足 / 领袖需上线”等优先级
      - 每个条目可直接 `选中资源区块 / 定位资源区块`
      - 整个条目本身也支持单击切换当前资源区块
    - 本轮已继续把优先区块队列补成可筛选运营面：
      - 新增 `全部 / 可迁移 / 等待选点 / 缺资金 / 等枯竭` 快速筛选
      - 筛选状态会同步高亮资源区块列表中的对应条目
      - 筛选状态现在也会同步刷新地图上的资源区块 marker 高亮与选中态，不再依赖整张快照重渲染
      - 筛选切换后会重新计算国家详情面当前最值得处理的资源区块
    - 本轮已继续把国家运营详情面补成更像领袖运营面：
      - 新增“运营概览”块
      - 直接显示可立即处理 / 流程等待 / 受阻区块数量
      - 直接显示筛选覆盖率、小时折算资源收益、小时折算经验收益
      - 直接显示按当前资源区块产出估算的升级预计时间
      - 直接显示总资金缺口与最高单区块缺口
      - 直接给出当前态势判断，帮助领袖先看“缺资金 / 等选点 / 等枯竭 / 需上线”哪一类问题
    - 本轮已继续把国家运营详情面补成更像“领袖操作台”：
      - 新增按阻塞原因分组的“运营快捷入口”
      - 可直接打开“可迁移 / 等选点 / 缺资金 / 需上线 / 等枯竭”等资源区块组
      - 点击后会直接切换优先筛选、选中该组首要区块并飞到地图位置
      - 这样领袖不用先自己切筛选再手动找区块
    - 本轮已继续把国家运营详情面补成“带节奏预报的运营台”：
      - 新增国家级运营预报块
      - 直接显示下次刷新 / 下次强制迁移的相对时间
      - 直接显示 1 小时内刷新波次与 6 小时内强迁波次
      - 直接显示 6 小时 / 12 小时资源与经验预估
      - 直接显示按当前资源区块单轮经验估算的升级轮次
      - 直接显示下一次关键时间的绝对时间，方便领袖排班处理
    - 本轮已继续把国家运营详情面补成“可排程的运营时间线”：
      - 新增国家级运营时间线块
      - 直接列出近期资源刷新 / 强制迁移 / 等待选点事件
      - 每条时间线事件都会显示区块、相对时间、绝对时间、当前限制和下一步
      - 每条时间线事件都可直接 `查看此区块 / 定位此区块`
    - 本轮已继续把运营时间线补成“可直接处理”的操作面：
      - 每条时间线事件现在都可直接 `处理这一类`
      - 点击后会切换到对应运营筛选组，并自动选中该事件对应区块
      - 这样领袖可以从“看到事件”直接跳到“处理同类问题”的视角
    - 本轮已继续把国家运营详情面补成“建议优先级面”：
      - 新增“处理建议”块
      - 会从当前运营阻塞组里提取 1-3 条首要建议
      - 每条建议会显示影响区块数、首要区块、下一步和当前限制
      - 每条建议都可直接跳到对应问题组
    - 本轮已继续把“处理建议”块补成更像操作卡片：
      - 每条建议现在还会直接显示建议补足金额
      - 每条建议现在还会直接显示建议目标区块
      - 每条建议现在还会直接显示负责人
      - 这样领袖不用再自己切回资源区块详情去拼“谁来处理 / 差多少钱 / 目标在哪”
    - 本轮已继续把“处理建议”块补成按状态给动作的运营建议卡：
      - `可立即迁移` 建议现在会直接提示打开可迁移区块，并突出单轮收益
      - `等待放置目标` 建议现在会直接提示去选迁移目标，并突出当前目标区块
      - `资金不足` 建议现在会直接提示查看资金缺口，并突出缺口金额
      - `领袖需上线 / 需国家领袖` 建议现在会直接提示对应负责人或在线限制
      - `等待旧区枯竭` 建议现在会直接提示查看枯竭进度，并突出强制迁移窗口
      - 推荐卡片现已补上专用说明文案，不再只有一枚统一的“优先处理”按钮
    - 本轮已继续把国家运营详情面补成“当前操作焦点”块：
      - 新增 `当前操作焦点 / Operations Focus` 块
      - 会锁定当前筛选组下最高优先级的资源区块，而不是让领袖自己在建议卡、时间线和优先区块列表之间来回找
      - 会直接显示当前分组、当前阶段、下一步、当前限制、资金缺口
      - 会根据状态直接显示关键窗口，例如刷新时间或强制迁移窗口
      - 会额外显示当前目标或单轮收益，并提供 `选中资源区块 / 定位资源区块 / 打开对应问题组` 三个直达动作
    - 本轮已继续把“当前操作焦点”块补成组内导航面：
      - 新增 `上一条 / 下一条` 导航按钮
      - 会显示当前焦点序号，例如 `1 / 3`
      - 焦点块现在优先跟随“当前筛选组内已选中的资源区块”，不再永远锁死第一条
      - 切换筛选组时，如果当前选中区块不在新组里，会自动回落到新组第一条
      - `#nation-operations-focus` 现已额外输出 `data-focus-index / data-focus-count / data-focus-resource-id`，方便 smoke 做稳定断言
    - 历史记录：本轮当时曾把“国家详情面 -> 资源区块指挥面”打通成同一条领袖操作链：
      - `index.html` 当时正式挂载过 `#resource-command-panel`
      - 国家详情头部动作、当前操作焦点、处理建议、运营时间线、优先区块队列当时新增过 `打开指挥面`
      - 最近操作日志当时也重新补回过 `打开指挥面`
      - 点击后当时会统一选中对应资源区块，并把侧边栏滚到资源区块指挥面
      - 这条 web command UI 路线后续已废弃；当前 2026-06-10 契约是 intel-only 情报面，Browser smoke 锁 `commandUiRemoved=true` 与 `resourceExplanation=<state>:<count>`
    - 浏览器烟测当时已继续锁住：
      - `#nation-detail-priority-list` 实际渲染
      - 优先区块条目存在
      - 国家详情里的快捷按钮与优先区块条目按钮都能切换选中资源区块
      - 优先区块筛选条存在
      - 国家运营概览块实际渲染
      - `#nation-operations-focus` 焦点块实际渲染
      - 焦点块至少存在 2 个可点击动作按钮
      - 焦点块 `上一条 / 下一条` 会真实切换到当前筛选组内前后资源区块
      - `打开指挥面` 当时会真实打开 `#resource-command-panel`，并把目标资源区块带进指挥面；当前该断言已被旧 command UI 缺席断言取代
      - 国家运营预报块实际渲染
      - 国家运营时间线块实际渲染
      - 运营快捷入口按钮点击后会真实切换筛选、联动选中区块
      - 时间线里的“处理这一类”按钮也会真实切换到对应筛选组并联动选中区块
      - “处理建议”块会真实渲染，不会只存在于源码
      - “处理建议”块至少存在 1 个状态专属按钮文案，不会全部退回通用词
      - 更新后的建议卡片字段已经并入最新完整 smoke
      - 资源区块指挥面当时列为浏览器 smoke 必测面；当前必测面改为资源区块情报 explanation DOM
      - 运营概览指标标签存在且随国家详情一起稳定输出
      - 运营快捷入口按钮存在
      - `ready` 与 `all` 筛选切换后 DOM 能稳定重渲染
      - 最近操作日志里的 `查看此区块 / 定位此区块 / 打开指挥面` 三连动作当时都可真实点击；当前最近日志只保留查看/定位/筛选类情报动作
    - 本轮已修正一次源码回归：
      - 资源区块指挥面 DOM 本体当时虽已恢复，但国家详情动作出口一度漏掉 `open-resource-command`
      - 已补回国家详情头部动作、当前操作焦点、运营时间线、最近操作日志、优先区块队列的按钮面
    - 历史组合烟测 `20260608-090147` 当时已通过，说明国家详情优先区块筛选条、资源列表强调联动、地图资源点高亮联动、运营概览块、当前操作焦点块、运营快捷入口、状态专属建议卡、最近操作日志动作链和当时的资源区块指挥面全链路在当时打包产物下稳定
  - 已新增本地静态预览脚本 `scripts/preview-starcore-map.ps1`
  - 已新增静态预览壳同步脚本 `scripts/sync-map-preview-shell.ps1`
  - `preview-starcore-map.ps1` 本轮已修复为可正常启动 Python 静态服务、等待端口真正监听后再报告成功的版本
  - 已重新验证 `http://127.0.0.1:43123/?demo=1` 可返回 `200`，页面内容包含 `STARCORE` 和 `leaflet`
  - 已通过 `?demo=1` 做真实桌面 / 移动端浏览器预览，确认资源区块列表、侧边栏展开与移动端布局可正常工作
  - `sync-map-preview-shell.ps1` 现已作为 `D:\qwq\项目\mapadd\map` 静态预览壳的标准同步入口，避免源码和预览壳再次漂移
- 资源区块信标 GUI 情报面已更接近网页地图详情：
  - 状态面已显示 pending target
  - 状态面已显示 next refresh time
  - 状态面已显示 force migration time
  - 迁移状态 pane 已同步显示当前迁移状态、目标区块、下次刷新和强制迁移时间
  - 状态面和迁移状态 pane 现已显示理论单轮资源、理论单轮经验和理论刷新周期
  - 状态面 / 迁移状态 pane / 迁移确认面本轮已继续补上：
    - 理论每小时资源
    - 理论每小时经验
    - 未来 3 轮资源预估
    - 未来 3 轮经验预估
    - 未来 3 轮预计时长
  - 迁移状态 pane 本轮已继续接入共享操作态提示：
    - 当前阶段
    - 下一步
    - 当前限制
  - 迁移确认面现在会拆成“当前资源区块状态 / 迁移后运营预估”两个 pane
  - 已新增 `NationResourceDistrictMenuSupport`，把状态面、迁移状态面、迁移确认面的共享文案组装集中收口
  - 已新增 `NationResourceDistrictMenuSupportTest`，锁住状态面 / 迁移状态 pane / 迁移确认面里的每小时产能、未来 3 轮预报、下一步和限制文案
  - 深烟测现已直接断言资源区块状态面 / 迁移状态 pane / 迁移确认面里的关键中文文本和理论收益字段，避免 GUI 打开正常但内容悄悄回退
  - 本轮已修复深烟测里“第二次迁移确认界面误拿旧菜单实例”的问题：现在优先使用本次 `openInventory` 捕获的 Inventory，再按 `districtId + confirmation + 区块坐标 lore` 做回退匹配
- 资源区块迁移显示契约现已统一收口：
  - 新增 `NationResourceDistrictViewSupport`
  - `inspect / migrate` 命令、网页地图 `migrationLabel`、资源区块信标迁移状态现已共用同一套迁移状态文案
  - `resource.district.migration.waiting-depletion` 已改为带目标区块参数的真实运行态文案
  - 资源区块信标悬浮显示现已接入共享展示支持，和命令 / GUI / 网页地图继续朝同一套资源区块运行态文案收口
  - 资源区块信标悬浮显示本轮也已继续补上每小时产能与未来 3 轮预报
  - 已新增 `NationResourceDistrictViewSupportTest` 锁住迁移状态、信标坐标、ISO 时间、每小时产能和未来 3 轮悬浮展示文本
- 资源区块理论收益概览现已统一接入：
  - 新增 `NationResourceDistrictOperationalSupport` / `NationResourceDistrictOperationalOverview`
  - `inspect` / `districts` 命令、网页地图 metadata、资源区块信标 GUI 现已共用同一套理论单轮资源、理论单轮经验、理论刷新周期预测
  - `inspect` / `districts` 命令本轮已继续补上理论每小时资源、理论每小时经验、未来 3 轮产出和未来 3 轮预计时长
  - 资源区块信标悬浮显示现在也会展示理论单轮资源、理论单轮经验、理论刷新周期、每小时产能和未来 3 轮预报
  - 已新增 `NationResourceDistrictOperationalSupportTest`
  - `MapModuleViewerSnapshotContractTest` 现已锁住 `expectedResourceYield / expectedExperienceYield / refreshCooldownMinutes`
- `NationModule` 与资源区块服务的耦合已进一步收口：
  - `resourceDistrictService` 字段改为依赖 `NationResourceDistrictService` 接口
  - 模块启停仍使用原生实现
  - 测试替身和后续扩展桥接不再需要硬绑 `NativeNationResourceDistrictService`
  - 资源区块迁移核心物品的创建 / 解析 / 发放 / 消耗已通过 `MigrationCoreSupport` 小适配层收口，为后续替换 Bukkit 物品交互或补更多外部桥接预留稳定扩展口
- 运维脚本自检现已补齐：
  - 统一自检脚本 `scripts/selfcheck-starcore-runtime-tools.ps1`
  - 自动生成 backup zip
  - 自动验证 `restore-starcore-runtime.ps1 -ReplaceExisting`
  - 自动验证 `check-starcore-database-settings.ps1` 的 MySQL warning 分支
  - 自动验证 `check-protectorapi-reference.ps1` 的参考契约分支
  - 自动验证 `build-latest-verification-summary.ps1` 能在 surefire XML 被日志污染时继续产出摘要
  - 最新轻量验证自检摘要: `target/runtime-tool-selfchecks/selfcheck-20260606-075458/runtime-tool-selfcheck-summary.json`
  - 最新完整 smoke 关联自检摘要: `target/runtime-tool-selfchecks/selfcheck-20260606-075458/runtime-tool-selfcheck-summary.json`
- 本地网页预览本轮再次确认：
  - `scripts/preview-starcore-map.ps1 -Port 43124`
  - `http://127.0.0.1:43124/?demo=1`
- 深度烟测现在已真实覆盖：
  - `ResolutionService.proposeJoin(...)`
  - `ResolutionService.sign(...)`
  - `ResolutionState.ENACTED` 写入
  - 成员加入国家后的运行态状态变化
  - `starcore_resolution_state` SQLite 工件落库
  - 关键 SQLite 表计数导出到 harness result
  - `nation / event / officer / policy / resource / resolution` 表行数大于 0 的脚本内硬断言
  - 资源区块迁移确认 GUI 已真实通过信标打开、状态面跳转、确认界面打开和确认按钮点击断言
  - 资源区块迁移主链已真实覆盖 `gui confirm -> awaiting_target -> waiting_depletion -> mined migration`
  - 资源区块迁移主链现已继续覆盖 `第二次 gui confirm -> waiting_depletion -> force migration timeout`
- 在线玩家目录抽象已接入：
  - `OnlinePlayerDirectory`
  - `BukkitOnlinePlayerDirectory`
- 地图模块和命令补全不再散落依赖 `Bukkit.getPlayer / getOnlinePlayers`
- 在线玩家网页圈地语义已保持并验证：
  - 在线玩家可从网页创建 pending
  - 玩家会收到待确认提示
  - marker 中会写入 `onlineWebClaim=...`
- 运维/发布面已开始收口：
  - 新增运行态备份脚本 `scripts/backup-starcore-runtime.ps1`
  - 新增运行态恢复脚本 `scripts/restore-starcore-runtime.ps1`
  - 新增数据库预检查脚本 `scripts/check-starcore-database-settings.ps1`
  - 新增发布验证总控脚本 `scripts/verify-starcore-release.ps1`
  - 新增发版证据打包脚本 `scripts/build-starcore-release-evidence-pack.ps1`
  - 新增发布渠道成品包脚本 `scripts/build-starcore-release-channel-assets.ps1`
  - 发布渠道成品包脚本已修复为 Windows PowerShell 5.1 可直接执行的版本
  - 脚本文件现已改成 UTF-8 BOM，避免中文标题和 here-string 在系统 PowerShell 下被错读
  - `smoke-starcore-paper-integration.ps1` 默认超时已上调到 `360` 秒，避免单独执行时被 Paper 冷启动误杀
  - 浏览器烟测现在改为读取 `#intel-grid` 等稳定 DOM 区域，不再误判默认折叠侧栏里的访问者信息
  - 两个发布打包脚本已修复 `Compress-Archive` 的 PowerShell 5.1 路径展开问题
  - 最新成品包产物：
    - `target/release-channel-assets-20260606-045452`
    - `target/release-channel-assets-20260606-045452.zip`
    - `target/release-channel-assets-20260606-045452/release-channel-assets-manifest.json`
  - 最新发版证据包产物：
    - `target/release-evidence-20260606-045450`
    - `target/release-evidence-20260606-045450.zip`
    - `target/release-evidence-20260606-045450/release-evidence-manifest.json`
  - 新增运维手册 `docs/OPERATIONS_RUNBOOK_2026-06-05.md`
  - 新增发布检查清单 `docs/RELEASE_CHECKLIST_2026-06-05.md`
  - `README.md` 已接入这些运维入口
- 发布契约自动护栏已补齐：
  - 新增 `MessageDefaultsCoverageTest`
  - 新增 `PluginDescriptorContractTest`
  - 自动检查 `messages_zh_cn.yml` 是否覆盖代码里固定消息键
  - 自动检查 `plugin.yml` 的主命令、短别名和 `softdepend: [ProtectorAPI]` 是否漂移
  - 已同步修正 `config.yml` 与 `README.md` 中过时的 SQL 默认存储说明
- 现实时间同步稳定性已补强：
  - `TimeSyncService` 现在会在 `start()` 时先立即校准一次世界时间，不再等待首个调度周期
  - 如果启用了 `freeze-daylight-cycle`，STARCORE 会记住被接管世界原本的 `doDaylightCycle`
  - 在插件停用、`/starcore reload` 重载或关闭现实时间同步时，会恢复这些世界原本的昼夜规则
  - 新增 `TimeSyncServiceTest` 覆盖 `doDaylightCycle` 捕获/恢复逻辑

## 已验证基线

下面这些不是本轮新做的，但它们已经有真实验证结果，当前可以继续作为稳定基线沿用：

- 游戏圈地工具与网页地图圈地共用：
  - `NationService.previewClaimSelection(...)`
  - `NationService.claimSelection(...)`
- 浏览器地图访问者面板已验证显示：
  - 余额
  - 国家
  - 政体
  - 身份
  - 在线状态
  - 国家经验
  - 领地上限
  - 资源区块上限
- 已完成并验证 SQL 化的模块/状态：
  - territory
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

## 当前真实烟测证据

- Paper 烟测通过
- Browser 烟测通过
- 真实 `ProtectorAPI` 插件加载通过：
  - `ProtectorAPI-Plugin-2.2.1.jar`
  - 服务端日志包含: `Successfully loaded ProtectorAPI!`
- 最新完整 `ProtectorAPI + BrowserSmoke` 组合烟测标记：
  - `STARCORE_SMOKE_PASS nation=Smokemq8ppkbs claims=5 price=489.07 districts=1 claimTool=GOLDEN_SHOVEL treasury=5053.30 diplomacy=war policy=civil_industry resources=food:150,ore:64 resolution=join_nation:enacted technology=logistics war=true officer=marshal event=resource migration=gui+mined:world:31:32+forced:world:31:31+feedbackSound:5+worldSound:7+particles:7+actionbar:3+title:2+bossbar:4+bossbarHide:4 protector=runtime:MockProtectorSmoke@39:39 claimExplanation=externalProtection:1 webClaim=command:940ccbdf webClaimFeedback=confirmSound:1+confirmActionbar:1+confirmTitle:0+confirmBossbar:1+confirmBossbarHide:1 onlineWebClaim=SmokeEnvoy:4a335fe9+pendingSound:1+pendingActionbar:1+pendingTitle:0+pendingBossbar:1+pendingBossbarHide:0+cancelSound:1+cancelActionbar:1+cancelTitle:0+cancelBossbar:1+cancelBossbarHide:0+cancelTyped:1 nationOperationFeedback=operationSound:4+operationActionbar:4+operationTitle:1+operationBossbar:4+operationBossbarHide:0 strategyFeedback=strategySound:3+strategyActionbar:3+strategyTitle:3+strategyBossbar:3+strategyBossbarHide:0 governanceFeedback=governanceSound:6+governanceActionbar:6+governanceTitle:3+governanceBossbar:6+governanceBossbarHide:0+diplomacy:1+officer:2+treasury:3 viewer=ok beacon=504,79,504:BEACON mapSummary=5 territory polygon(s), 0 player marker(s), 1 resource district marker(s)`
- 最新 WebClaim HTTP 烟测标记：
  - `WebClaimSmoke PASS provider=MockProtectorSmoke chunk=39,39 bounds=624,624->655,655`
- 最新 Browser 烟测标记：
  - `STARCORE_BROWSER_SMOKE_PASS viewer=SmokePlayer nation=Smokemq8ppkbs balance=9798888.59 nationDetail=true nationAction=true recentLog=5 recentLogFilter=resource:3 resourceAction=player-offline resourceExplanation=player-offline:5 resourceCost=100000.00 commandUiRemoved=true browser=Edg/149.0.4022.52`
  - 当前 Browser smoke 不再使用旧 `confirmUi=true` 作为 PASS 字段；资源区块 HUD 契约是 intel-only 情报面，旧 `#resource-command-panel` 不应存在
- 最新组合 Browser 证据文件：
  - `D:\qwq\项目\mapadd\test-server-paper-1.21.11\zcode-starcore-map-browser-smoke-20260611-073915.dom.html`
  - `D:\qwq\项目\mapadd\test-server-paper-1.21.11\zcode-starcore-map-browser-smoke-20260611-073915.png`
- 最新组合 Paper 日志：
  - `D:\qwq\项目\mapadd\test-server-paper-1.21.11\zcode-starcore-deep-integration-smoke-20260611-073915.out.log`
- 最新 runtime tool selfcheck 证据：
  - `target/runtime-tool-selfchecks/selfcheck-20260610-140012/runtime-tool-selfcheck-summary.json`
  - `restore -ReplaceExisting = PASS`
  - `mysql precheck branch = warning`
- 最新 ProtectorAPI 参考契约证据：
  - `target/protectorapi-reference-checks/check-20260610-140019/protectorapi-reference-check-summary.json`
  - `reference head = 88ced9783aaffffe333b57610162ac8ef9759760`
  - `status = ok`
- 最新轻量验证摘要说明：
- `docs/LATEST_VERIFICATION_SUMMARY.md` 现在对应 2026-06-11 07:48:13 +08:00 的当前工作区验证
  - 该摘要已包含完整 smoke、浏览器工件、最新 runtime selfcheck 和最新 ProtectorAPI 参考契约检查
- 查看完整 smoke 以 `20260611-073915` 工件为准
- 最新成功网页圈地闭环证据：
  - `/api/map/claim/request` 已真实返回 `requestSubmitted:true`
  - `/starcore map confirm <编号>` 已真实完成确认
  - 最新 PASS 标记包含 `webClaim=command:940ccbdf`
  - 最新 PASS 标记包含 `onlineWebClaim=SmokeEnvoy:4a335fe9`
  - 最新 PASS 标记包含 `resolution=join_nation:enacted`
  - 最新地图摘要已刷新为 `mapSummary=5 territory polygon(s), 0 player marker(s), 1 resource district marker(s)`
  - 最新浏览器访问者面板仍通过：`balance / nation / government / role / online / nationExperience / claimCount / resourceDistrictCount`
  - 最新 Browser smoke 输出 `recentLogFilter=resource:3`、`resourceExplanation=player-offline:5` 与 `commandUiRemoved=true`，旧 `confirmUi=true` 已不再作为 PASS 字段

### 最新组合烟测 SQLite 工件计数

- `starcore_diplomacy_state=10`
- `starcore_event_state=157`
- `starcore_metadata=3`
- `starcore_nation_resource_district_state=56`
- `starcore_nation_state=76`
- `starcore_officer_state=5`
- `starcore_player_balances=7`
- `starcore_policy_state=21`
- `starcore_resolution_state=27`
- `starcore_resource_state=7`
- `starcore_technology_state=7`
- `starcore_territory_claims=5`
- `starcore_treasury_state=7`
- `starcore_war_state=7`

## 本轮新增完成面

### 运维 / 发布

- 已新增脚本：
  - `scripts/backup-starcore-runtime.ps1`
  - `scripts/restore-starcore-runtime.ps1`
  - `scripts/check-starcore-database-settings.ps1`
  - `scripts/check-protectorapi-reference.ps1`
- 当前脚本能力：
  - 备份 `plugins/STARCORE`
  - 默认排除 `cache/`
  - 可选 `-IncludeCache`
  - 可选 `-AsZip`
  - 默认尝试同时备份 `plugins/map`
  - 若地图导出目录不是默认值，可手动传 `-MapExportDir`
  - 自动写出 `backup-manifest.json`
  - 恢复脚本支持从目录或 zip 还原
  - 恢复脚本支持 `-ReplaceExisting` 并先把旧运行态挪到 safeguard 目录
  - 数据库预检查脚本可检查 SQLite 文件组或 MySQL TCP 可达性
- 已新增文档：
  - `docs/OPERATIONS_RUNBOOK_2026-06-05.md`
  - `docs/RELEASE_CHECKLIST_2026-06-05.md`
- 已明确当前运行面边界：
  - 主数据目录：`plugins/STARCORE`
  - 默认地图导出目录：`plugins/map`
  - 地图缓存位于 `plugins/STARCORE/cache/terrain/...`
  - 头像缓存位于 `plugins/STARCORE/cache/avatars`

### resolution

- 已有文件：
  - `src/main/java/dev/starcore/starcore/module/resolution/ResolutionStateStorage.java`
  - `src/main/java/dev/starcore/starcore/module/resolution/ResolutionStateCodec.java`
  - `src/main/java/dev/starcore/starcore/module/resolution/PersistenceResolutionStateStorage.java`
  - `src/main/java/dev/starcore/starcore/module/resolution/SqlResolutionStateStorage.java`
  - `src/main/java/dev/starcore/starcore/module/resolution/DatabaseAwareResolutionStateStorage.java`
- 已有测试：
  - `src/test/java/dev/starcore/starcore/module/resolution/SqlResolutionStateStorageTest.java`
- 已有真实组合烟测 SQLite 证据：
  - `starcore_resolution_state=14`
- 已有真实深烟测运行态证据：
  - `resolution=join_nation:enacted`

### 在线玩家网页圈地

- 已有统一在线玩家抽象：
  - `src/main/java/dev/starcore/starcore/foundation/player/OnlinePlayerDirectory.java`
  - `src/main/java/dev/starcore/starcore/foundation/player/BukkitOnlinePlayerDirectory.java`
- 已有真实组合烟测运行态证据：
  - `onlineWebClaim=SmokeEnvoy:feed48fb`
- 已验证语义：
  - 在线玩家网页创建 pending
  - 在线玩家收到回服确认提示
  - 访问者快照里在线态可被 synthetic viewer 覆盖验证

## 烟测脚本状态

- `scripts/smoke-starcore-paper-integration.ps1` 已继续扩展
- 已真实触发：
  - `OfficerService.appoint(...)`
  - `EventService.record(...)`
  - `ResolutionService.proposeJoin(...)`
  - `ResolutionService.sign(...)`
  - `NationService.previewClaimSelection(...)` 外部保护冲突拦截
  - `NationService.claimSelection(...)` 外部保护冲突拦截
  - `NationService.claimCurrentChunk(...)` 外部保护冲突拦截
- 已补完的更深闭环：
  - 直接对 `/api/map/claim/preview` 的外部保护冲突 JSON 断言
  - 直接对 `/api/map/claim/request` 的外部保护冲突 JSON 断言
  - 直接对 `/starcore status` 的外部保护桥摘要断言
  - 直接对“允许提交”路径的 `/api/map/claim/request` 创建 pending 断言
  - 直接对 `/starcore map confirm <编号>` 成功确认断言
  - 直接对在线玩家网页圈地即时提示和 `onlineWebClaim=...` 标记断言
  - 直接对决议 `ENACTED` 状态与成员写入断言
  - 直接对 `claimCount / map summary / authenticated viewer snapshot` 刷新断言
  - 直接对 harness 导出的关键 SQLite 表计数做外层 PowerShell 硬断言
  - 直接导出 `target/smoke-harness-<timestamp>/smoke-summary.json` 供后续自动汇总使用
- 当前脚本分层：
  - 服内 harness 负责共享服务链和 `/starcore status` 断言
  - 服内 harness 也负责导出 SQLite 关键表计数
  - 服外 PowerShell 负责基于 personal map 链接断言真实 HTTP 入口，并消费 harness 结果做硬断言
  - 这样可以避开 `MapModule.callSync(...)` 与主线程同步 HTTP 请求互等导致的超时

## 最新进展：2026-06-08 22:11

本轮继续把财政 / 资源 / 经济账本从“事件已记录”推进到“服主能直接查询”：

- 新增 `/sc event audit <国家> [finance|treasury|resource|all]`，短命令可用 `/sc ev a <国家> finance`。
- 新增中文短别名：`审`, `审计`, `账`, `账本`。
- `/sc event list <国家> [类型前缀]` 现在也可以按事件类型过滤，例如 `treasury.`、`resource.`、`war.`。
- 账本默认 `finance` 会合并展示 `treasury.*` 和 `resource.*`，适合查国家金库和资源库存流水。
- 事件输出改成本地时间 `yyyy-MM-dd HH:mm:ss`，事件类型显示中文名并保留原始类型，例如 `金库存入 / treasury.deposit`。
- Tab 补全已覆盖审计入口、筛选分类、常见事件类型。
- `messages_zh_cn.yml` 已补齐审计用法、筛选名、事件类型中文默认文案。
- 新增/扩展回归测试：
  - `StarCoreCommandShortAliasDispatchTest` 锁住 `ev a` 只显示财政 / 资源流水，不混入战争事件。
  - `StarCoreCommandTabCompletionTest` 锁住审计命令、中文别名、筛选分类和事件类型补全。
- 最新验证：
  - `mvn -q "-Dtest=StarCoreCommandShortAliasDispatchTest,StarCoreCommandTabCompletionTest" test`: PASS
  - `mvn -q test`: PASS
  - `mvn -q -DskipTests package`: PASS
  - Paper + Browser + ProtectorAPI smoke: PASS，summary 为 `target/smoke-harness-20260608-220738/smoke-summary.json`
  - 最新 jar: `target/starcore-0.1.0-SNAPSHOT.jar`
  - jar size: `17506294`
  - jar SHA256: `8132ABEFCCE29174DB8EBBC92E89C3AB91DC55087E2A995E9AF0EF09F686E8FD`

### 2026-06-09 07:03 账本详情增强

本轮继续把账本从“能查流水”推进到“能看清原因与结构化字段”：

- `/sc treasury deposit <国家> <金额> [原因...]` 与 `/sc treasury withdraw <国家> <金额> [原因...]` 现在会把原因写入国家事件。
- `/sc resource grant <国家> <类型> <数量> [原因...]` 与 `/sc resource consume <国家> <类型> <数量> [原因...]` 现在会把原因写入国家事件。
- 财政/资源事件会写入结构化 context：操作人、数量、资源类型、余额、原因。
- `/sc ev a <国家> finance` 审计输出会在事件行下方显示中文详情行，方便服主追溯。
- `messages_zh_cn.yml` 已补齐原因、详情、结构化字段和财政/资源事件消息。
- 新增/扩展回归测试：
  - `StarCoreCommandShortAliasDispatchTest` 锁住 `ev a` 的 context 中文详情展示。
  - `StarCoreCommandShortAliasDispatchTest` 锁住金库存入原因会写入结构化 context。
- 最新验证：
  - `mvn -q "-Dtest=StarCoreCommandShortAliasDispatchTest,StarCoreCommandTabCompletionTest" test`: PASS
  - `mvn -q test`: PASS
  - `mvn -q -DskipTests package`: PASS
  - Paper + Browser + ProtectorAPI smoke: PASS，summary 为 `target/smoke-harness-20260608-233546/smoke-summary.json`
  - smoke 备注：summary 为 PASS，但测试服后续 shutdown/长时间等待阶段出现 Paper watchdog/chunk-light 噪声；该段未显示 STARCORE 栈，作为测试环境性能跟进项。
  - 最新 jar: `target/starcore-0.1.0-SNAPSHOT.jar`
  - jar size: `17507933`
  - jar SHA256: `35BBE39D3B049D3B0EEB68F1508A177BF2BFB4BD98967811E78B1210B36C37B7`

### 同日 20:45 基线

本轮先把命令可用性继续往 RPG 服日常使用方向补稳：

- `/sc` 根命令新增更多英文短入口和中文一字入口，例如 `国`, `图`, `钱`, `资/矿`, `技`, `战`, `时`, `策`, `官`, `事`, `纪`。
- 国家、地图、资源、经济、科技、战争、外交、金库、官员、事件等子命令同步支持一字中文别名。
- Tab 补全现已覆盖国家名、在线玩家名、资源区块坐标、资源类型、科技项、政策项、官职、常用金币数量和常用资源数量。
- 补全匹配改为大小写不敏感，玩家输入 `T` 或 `test` 都能补出 `TestNation` 这类名称。
- `StarCoreCommandTabCompletionTest` 已扩展参数补全和中文一字别名护栏。

随后继续补强了资源区块信标 GUI 管理闭环：

- 迁移确认页状态 pane 现在显示待迁移目标与强制迁移时间。
- 迁移操作 pane 现在显示“本次扣费”，区分首次迁移会扣费与已领取迁移核心后不会重复扣费。
- 点击确认并重新打开状态页后，反馈 lore 现在显示结果、当前迁移状态、当前目标和强制迁移时间。
- `NativeNationResourceDistrictService` 改为复用 `NationResourceDistrictMenuSupport.resultFeedbackLore(...)` 生成确认后反馈。
- `NationResourceDistrictMenuSupportTest` 已扩展确认页/确认后反馈护栏。
- 最新验证：
  - `mvn -q -Dtest=StarCoreCommandTabCompletionTest test`: PASS
  - `mvn -q -Dtest=NationResourceDistrictMenuSupportTest test`: PASS
  - `mvn -q test`: PASS
  - `mvn -q -DskipTests package`: PASS
  - Paper + Browser + ProtectorAPI smoke: PASS，summary 为 `target/smoke-harness-20260608-204240/smoke-summary.json`
  - 最新 jar: `target/starcore-0.1.0-SNAPSHOT.jar`
  - jar size: `17501512`
  - jar SHA256: `D8FE26942900D65172FB595EA0C6DEBAA32D7E0CD6EE2C0DEF50A08FA92E6A1C`

## 下一步直接执行顺序

## 2026-06-09 12:46 +08:00 本轮进展：网页财政流水分类筛选

本轮把网页地图里的国家财政流水从“只能看完整账本”推进到“可按类型筛选”：

- `/api/map/finance/events` 支持 `filter` 参数，统一识别 `all/resource/income/reward/tax/deposit/withdraw`，并兼容部分中文别名。
- 网页地图国家财政面板新增筛选按钮：全部、资源产出、日常收入、任务奖励、玩家税收、管理员存入、金库支出。
- 财政流水分页会保持当前筛选条件，刷新按钮也会按当前分类重新加载。
- API 返回体新增 `filter` 字段，前端会用它校准当前选中的筛选项。
- `MapModuleViewerSnapshotContractTest` 已补 resource 分类契约，验证资源流水只返回 `treasury.resource-income`，不会混入 deposit / withdraw / income。

最新验证：

- `node --check src/main/resources/web/map/js/map.js`: PASS
- `mvn -q -DskipTests compile`: PASS
- `mvn -q "-Dtest=MapModuleViewerSnapshotContractTest" test`: PASS
- `mvn -q test`: PASS
- `mvn -q -DskipTests package`: PASS
- 最新 jar: `target/starcore-0.1.0-SNAPSHOT.jar`
- jar size: `17548011`
- jar SHA256: `20FFE535D7CFA22AEF305E40AD536A2799153D687A19B681AB005586F7F44330`

下一刀建议继续沿着“玩家和管理员不用查后台也能看懂国家经济”推进：

1. 给财政流水补时间范围过滤，例如最近 1h / 24h / 7d / 自定义起止时间。
2. 给财政流水补导出入口，优先 CSV/JSON，方便 RPG 服管理员查账。
3. 把资源区块产出、玩家税收、RPG 奖励统一接到更明确的账本分类配置。
4. 再做一次 Paper + Browser smoke，确认网页按钮和 API filter 在真实运行服里可点可用。

---

## 2026-06-09 12:56 +08:00 本轮进展：财政流水时间范围筛选

本轮把上一刀的“分类账本”继续推进成“按时间查账”：

- `/api/map/finance/events` 新增 `range/from/to` 入参。
- 快捷范围支持 `all`、`1h`、`24h`、`7d`，自定义范围使用 ISO 时间字符串。
- API 返回体新增 `range`、`from`、`to`，网页端可以回显和保持当前时间窗口。
- 网页地图国家财政面板新增时间按钮：全部时间、最近1小时、最近24小时、最近7天、自定义。
- 自定义时间支持 `datetime-local` 输入，前端会转换为 ISO 时间再请求 API。
- 分页、刷新、分类切换都会保留当前时间窗口。
- `MapModuleViewerSnapshotContractTest` 已补自定义时间窗口护栏，验证只返回窗口内的 deposit + resource-income，并排除 withdraw / tax。

最新验证：

- `node --check src/main/resources/web/map/js/map.js`: PASS
- `mvn -q -DskipTests compile`: PASS
- `mvn -q "-Dtest=MapModuleViewerSnapshotContractTest" test`: PASS
- `mvn -q test`: PASS
- `mvn -q -DskipTests package`: PASS
- 最新 jar: `target/starcore-0.1.0-SNAPSHOT.jar`
- jar size: `17551694`
- jar SHA256: `8FA0B1741017FEFBA7783DE09EF8F017D41245BF43BA08DA307807BA1271BE61`

下一刀建议：

1. 给财政流水补 CSV/JSON 导出入口，导出沿用当前 `filter/range/from/to`。
2. 给网页端做一次 Browser smoke，真实点击分类和时间范围按钮。
3. 给管理员命令补同样的账本筛选，避免只有网页能查。
4. 后续再考虑独立 SQL ledger 表，支撑大服长期高频查账。

---

## 2026-06-09 13:05 +08:00 本轮进展：财政流水 CSV/JSON 导出

本轮把网页财政账本从“能筛选查看”继续推进到“能直接导出查账”：

- `/api/map/finance/events` 复用现有权限、国家可见性、类型筛选和时间范围筛选。
- 新增 `format=csv` 导出，返回 `text/csv; charset=utf-8` 和下载文件名。
- 新增 `format=json` 导出，返回完整筛选结果 JSON，不受分页截断影响。
- CSV 字段包含国家、筛选条件、时间窗口、事件 ID、发生时间、类型、消息、金额、操作人、原因、余额、原始 context。
- 网页国家财政面板新增“导出CSV / 导出JSON”按钮，沿用当前分类和时间范围。
- `MapModuleViewerSnapshotContractTest` 已补导出护栏，验证 content type、文件名、筛选结果和资源产出导出内容。

最新验证：

- `node --check src/main/resources/web/map/js/map.js`: PASS
- `mvn -q -DskipTests compile`: PASS
- `mvn -q "-Dtest=MapModuleViewerSnapshotContractTest" test`: PASS
- `mvn -q test`: PASS
- `mvn -q -DskipTests package`: PASS
- 最新 jar: `target/starcore-0.1.0-SNAPSHOT.jar`
- jar size: `17554042`
- jar SHA256: `82EF81E0A20E6A0B9D29FFD939CAAAE8051AC5034B8B4521FFB823AAE9AABDF1`

下一刀建议：

1. 跑 Paper + Browser smoke，真实点击财政分类、时间范围、CSV/JSON 导出按钮。
2. 给管理员命令补同样的账本筛选与导出提示，避免网页成为唯一查账入口。
3. 给导出文件名增加更稳定的国家 ID 片段，避免中文国家名全变成 `_`。
4. 继续把资源区块产出、玩家税收、RPG 奖励接入可配置账本分类。

---

## 2026-06-09 13:17 +08:00 本轮进展：财政导出文件名稳定化

本轮修复了财政流水导出文件名对中文国家名不友好的问题：

- `financeExportFilename(...)` 不再把中文国家名整体退化为 `_`。
- 新增 `safeFinanceFilenameSegment(...)`，保留 Unicode 字母/数字，并清理不适合文件名的符号。
- 导出文件名追加国家 ID 前 8 位，减少中文重名或清理后重名导致的覆盖风险。
- 示例文件名从 `starcore-finance-_-resource-custom.csv` 变为 `starcore-finance-星河商会-00000000-resource-custom.csv`。
- `MapModuleViewerSnapshotContractTest` 已更新 CSV/JSON 文件名护栏。

最新验证：

- `mvn -q -DskipTests compile`: PASS
- `mvn -q "-Dtest=MapModuleViewerSnapshotContractTest" test`: PASS
- `mvn -q test`: PASS
- `mvn -q -DskipTests package`: PASS
- 最新 jar: `target/starcore-0.1.0-SNAPSHOT.jar`
- jar size: `17554475`
- jar SHA256: `B0B6564A61E289C34C126BCC70F3115E254F6E75E786D87668139B949CC4E2D6`

下一刀建议：

1. 跑 Paper + Browser smoke，真实点击分类、时间范围、CSV/JSON 导出。
2. 给命令端财政账本补类型/时间筛选，让管理员不打开网页也能查。
3. 给 CSV 导出增加 BOM 可配置项，兼容部分 Windows 表格软件。
4. 继续把资源区块产出、税收、RPG 奖励做成可配置账本分类。

---

## 2026-06-09 13:26 +08:00 本轮进展：财政 CSV 导出 BOM 配置

本轮继续补强中文 RPG 服常见的表格兼容问题：

- `config.yml` 新增中文注释配置 `map.web.finance-export.csv-bom-enabled`，默认 `true`。
- `ConfigurationService` 新增 `mapWebFinanceExportCsvBomEnabled()`。
- 网页财政 CSV 导出默认写入 UTF-8 BOM，方便 Windows Excel 等表格软件识别中文。
- 若服主使用的工具不需要 BOM，可在配置中关闭。
- `MapModuleViewerSnapshotContractTest` 已锁住默认 CSV 以 BOM + header 开头。
- `ConfigDefaultsCoverageTest` 已验证新增配置路径存在默认值。

最新验证：

- `mvn -q -DskipTests compile`: PASS
- `mvn -q "-Dtest=ConfigDefaultsCoverageTest,MapModuleViewerSnapshotContractTest" test`: PASS
- `mvn -q test`: PASS
- `mvn -q -DskipTests package`: PASS
- 最新 jar: `target/starcore-0.1.0-SNAPSHOT.jar`
- jar size: `17554760`
- jar SHA256: `34ABC9423386BAA438F34274A3E4825E7112950BE186C5E54E687DAF51420BE4`

下一刀建议：

1. 跑 Paper + Browser smoke，验证网页导出按钮真实下载 CSV/JSON。
2. 给命令端财政账本补类型/时间筛选，让控制台也能查。
3. 把命令端事件导出和网页财政导出的格式/文件名规则统一。
4. 继续做资源区块产出、税收、RPG 奖励的可配置账本分类。

---

## 2026-06-09 13:36 +08:00 本轮进展：命令端财政账本细分筛选

本轮把网页端已有的财政细分思路补到命令端，方便管理员在控制台或游戏内直接查账：

- `/sc ev audit <nation>` 和 `/sc ev export <nation>` 支持细分财政过滤：
  - `resource-income` / `资源产出`
  - `income` / `日常收入`
  - `reward` / `任务奖励`
  - `tax` / `税收`
  - `deposit` / `存入`
  - `withdraw` / `支出`
- 原有 `finance` 仍保留为粗粒度财政与资源账本。
- Tab 补全新增上述英文与中文候选。
- 中文用法提示已更新，管理员可以直接看到可用过滤词。
- `StarCoreCommandShortAliasDispatchTest` 已补审计与导出细分过滤护栏。
- `StarCoreCommandTabCompletionTest` 已补补全护栏。

最新验证：

- `mvn -q -DskipTests compile`: PASS
- `mvn -q "-Dtest=StarCoreCommandShortAliasDispatchTest,StarCoreCommandTabCompletionTest" test`: PASS
- `mvn -q test`: PASS
- `mvn -q -DskipTests package`: PASS
- 最新 jar: `target/starcore-0.1.0-SNAPSHOT.jar`
- jar size: `17555972`
- jar SHA256: `AD38DC75893D500963256201FD671EEDCF3A75A3A9D486E632EF827603025361`

下一刀建议：

1. 统一命令端事件导出和网页财政导出的 CSV 字段、BOM、文件名规则。
2. 跑 Paper + Browser smoke，验证网页和命令端查账链路都能实跑。
3. 把资源区块产出、税收、RPG 奖励做成可配置账本分类。
4. 增加管理员文档，列出常用查账命令示例。

---

## 2026-06-09 13:45 +08:00 本轮进展：命令端事件导出 CSV 规则统一

本轮把命令端 `/sc ev export` 的 CSV 导出向网页财政导出靠齐，减少管理员两套表格格式的维护成本：

- 命令端 CSV 默认使用同一个 BOM 配置：`map.web.finance-export.csv-bom-enabled`。
- 命令端 CSV 字段改为：
  - `nation_id,nation_name,filter,range,from,to,event_id,occurred_at,type,localized_type,message,amount,actor,reason,balance,context`
- 命令端 CSV 会从事件 context 中提取 `amount/actor/reason/balance`。
- 命令端导出文件名改为统一前缀风格：
  - `starcore-event-<国家名>-<国家ID前8位>-<filter>-<range>-<timestamp>.csv`
- 保留 timestamp，避免控制台多次导出覆盖旧文件。
- `StarCoreCommandShortAliasDispatchTest` 已更新 CSV BOM、字段、文件名和 context 字段护栏。

最新验证：

- `mvn -q -DskipTests compile`: PASS
- `mvn -q "-Dtest=StarCoreCommandShortAliasDispatchTest,StarCoreCommandTabCompletionTest" test`: PASS
- `mvn -q test`: PASS
- `mvn -q -DskipTests package`: PASS
- 最新 jar: `target/starcore-0.1.0-SNAPSHOT.jar`
- jar size: `17557500`
- jar SHA256: `03BF865B4CEEC09278F401D079FD8AAE38FD943B4D4C4153EB1825DB4D7EE6F6`

下一刀建议：

1. 跑 Paper + Browser smoke，验证网页导出和命令端导出在真实运行服都可用。
2. 把命令端 JSON 导出也补上 `range/from/to/total` 元数据，和网页 JSON 更一致。
3. 增加管理员文档，列出常用财政查账和导出命令示例。
4. 继续做资源区块产出、税收、RPG 奖励的可配置账本分类。

---

## 2026-06-09 13:52 +08:00 本轮进展：命令端 JSON 导出元数据补齐

本轮把命令端 `/sc ev export ... json` 的导出信息补齐，和网页 JSON 导出的元数据更接近：

- 命令端 JSON 导出新增 `nationId`。
- 命令端 JSON 导出新增 `range`，例如 `24h`、`7d`、`all`。
- 命令端 JSON 导出新增 `from` / `to`，其中相对时间窗口会写入实际 `from`，`to` 留空表示导出当前时刻。
- 命令端 JSON 导出新增 `total`，表示过滤后的导出事件数量。
- `24h` 导出文件名和 JSON `range` 保持为 `24h`，不再被显示为 `1d`。
- `StarCoreCommandShortAliasDispatchTest` 已补 JSON 元数据和文件名前缀护栏。

最新验证：

- `mvn -q -DskipTests compile`: PASS
- `mvn -q "-Dtest=StarCoreCommandShortAliasDispatchTest,StarCoreCommandTabCompletionTest" test`: PASS
- `mvn -q test`: PASS
- `mvn -q -DskipTests package`: PASS
- 最新 jar: `target/starcore-0.1.0-SNAPSHOT.jar`
- jar size: `17557608`
- jar SHA256: `D8DBC147C6EA4CAE48AF827546C9EDE441344D97BC59387A3D67F3AD981DB462`

下一刀建议：

1. 跑 Paper + Browser smoke，验证网页导出和命令端导出在真实运行服都可用。
2. 增加管理员文档，列出常用财政查账和导出命令示例。
3. 把资源区块产出、税收、RPG 奖励做成可配置账本分类。
4. 继续做国家领袖运营面，补资金缺口和资源产出趋势。

---

## 2026-06-09 14:00 +08:00 本轮进展：管理员财政查账指南

本轮把已经完成的命令端/网页端查账能力整理成管理员可直接使用的文档：

- 新增 `docs/ADMIN_FINANCE_LEDGER_GUIDE.md`。
- 文档覆盖：
  - `/sc ev a` / `/starcore event audit` 查账命令。
  - `/sc ev ex` / `/starcore event export` 导出命令。
  - 网页地图财政概览、分类、时间范围、CSV/JSON 导出入口。
  - `finance/resource-income/income/reward/tax/deposit/withdraw` 等筛选词和中文别名。
  - `24h/7d/30d/60m/1天/7天` 等时间窗口。
  - 命令端和网页端导出目录、文件名格式、CSV 字段。
  - `map.web.finance-export.csv-bom-enabled` 的用途和排查建议。
  - RPG 插件应使用 `TreasuryRewardService` 写 `treasury.reward`。
- README 的 Operations 入口已新增管理员财政查账指南链接。
- `docs/MODULE_PLAN.md` 的“财政 / 资源 / 经济账本”规划行已同步当前底座。

最新验证：

- `rg -n "ADMIN_FINANCE_LEDGER_GUIDE|resource-income|csv-bom-enabled|starcore-event-|财政 / 资源 / 经济账本" README.md docs/ADMIN_FINANCE_LEDGER_GUIDE.md docs/MODULE_PLAN.md`: PASS
- `mvn -q -DskipTests compile`: PASS

下一刀建议：

1. 跑 Paper + Browser smoke，验证网页导出和命令端导出在真实运行服都可用。
2. 把资源区块产出、税收、RPG 奖励做成可配置账本分类。
3. 继续做国家领袖运营面，补资金缺口和资源产出趋势。
4. 把管理员指南中的关键命令加入 README 主命令段落。

---

## 2026-06-09 14:05 +08:00 本轮进展：README 财政查账命令入口

本轮把管理员指南里的高频财政查账命令补进 README 主命令段落，降低服主第一次使用的查找成本：

- README 新增 `Finance ledger quick commands` 小节。
- 小节包含可直接复制的命令示例：
  - `/sc ev a <nation> finance 24h`
  - `/sc ev a <nation> resource-income 7d`
  - `/sc ev a <nation> tax 7d`
  - `/sc ev a <nation> withdraw 2 25`
  - `/sc ev ex <nation> finance 7d csv`
  - `/sc ev ex <nation> resource-income 24h json`
- README 同步列出常用筛选词和中文别名。
- README 从该小节链接到 `docs/ADMIN_FINANCE_LEDGER_GUIDE.md`。

最新验证：

- `rg -n "Finance ledger quick commands|resource-income 7d|ADMIN_FINANCE_LEDGER_GUIDE|财政|资源产出" README.md docs/ADMIN_FINANCE_LEDGER_GUIDE.md`: PASS
- `mvn -q -DskipTests compile`: PASS

下一刀建议：

1. 跑 Paper + Browser smoke，验证网页导出和命令端导出在真实运行服都可用。
2. 把资源区块产出、税收、RPG 奖励做成可配置账本分类。
3. 继续做国家领袖运营面，补资金缺口和资源产出趋势。
4. 把 README 的 smoke / verification 段落更新到最新 jar 与当前财政导出能力。

---

---

## 2026-06-14 14:58 +08:00 本轮进展：资源区块迁移官员授权配置化

本轮继续推进 P2“官员 / 事件 / 审计日志”里的官员职责落地，把资源区块迁移从“只能国家领袖操作”升级为“国家领袖或配置授权官职操作”：

- `config.yml` 新增 `nation.officers.resource-district-migration-roles`，默认 `marshal`。
- `ConfigurationService.nationResourceMigrationOfficerRoles()` 会规范化并去重配置的官职，配置为空时回退默认 `marshal`。
- `NativeNationResourceDistrictService` 的信标打开、迁移确认、领取迁移核心、设置迁移目标都改用同一套服务层授权：
  - 国家领袖可操作。
  - `starcore.admin` 仍可操作。
  - 被任命为配置白名单内官职的玩家可操作，默认即 `marshal`。
  - `treasurer / diplomat / steward` 不会因为只是官员就自动获得迁移权限。
- `NationResourceDistrictCommandSupport` 新增结构化授权字段：
  - `viewerCanManageMigration`
  - `viewerAuthorizedOfficerRole`
- 地图 resource district metadata 和 `/api/map/resource-district/migrate` 响应已同步输出上述字段，网页情报解释和服务层权限不再割裂。
- 资源区块信标 / 迁移 / GUI 的中文文案已从“只有国家领导者”调整为“国家领袖或资源迁移授权官员”。

新增/更新测试护栏：

- `NationResourceDistrictCommandSupportTest`
  - 锁住非领袖但已授权 `marshal` 可进入 `ready`。
  - 锁住未授权官职仍是 `leader-only` 阻塞。
- `NativeNationResourceDistrictServiceFlowTest`
  - 锁住默认配置下 `marshal` 能领取迁移核心并设置迁移目标。
  - 锁住默认配置下 `treasurer` 不能领取迁移核心。
- `MapModuleViewerSnapshotContractTest`
  - 锁住非领袖 `marshal` 的 resource marker metadata：`viewerCanManageMigration=true`、`viewerAuthorizedOfficerRole=marshal`。
- `ResourceDistrictMapMetadataSupportTest`
  - 锁住 metadata 新字段。
- `ConfigurationServiceResourceFeedbackConfigTest`
  - 锁住资源迁移授权官职配置可自定义、规范化、去重。

最新验证：

- `mvn -q "-Dtest=NationResourceDistrictCommandSupportTest,NativeNationResourceDistrictServiceFlowTest,ResourceDistrictMapMetadataSupportTest,MapModuleViewerSnapshotContractTest,MapResourceDistrictEndpointTest,ConfigurationServiceResourceFeedbackConfigTest,ConfigDefaultsCoverageTest" test`: PASS
- `mvn -q test`: PASS (`313` tests, `0` failures, `0` errors, `0` skipped)
- `mvn -q -DskipTests package`: PASS
- `codegraph sync .`: PASS
- 最新 jar: `target/starcore-0.1.0-SNAPSHOT.jar`
- jar size: `17737246`
- jar SHA256: `D4540D2D027BF03D22C443F09760747B01B5F2781399477E199A82EA1EF84F9B`

未跑项：

- 本轮没有改前端静态资源，未执行 `sync-map-preview-shell.ps1`。
- 本轮未重跑 Paper + Browser smoke；`docs/LATEST_VERIFICATION_SUMMARY.md` 中的 smoke / performance / HUD contract 仍保留上一轮完整 smoke 证据。

下一刀建议：

1. 跑一次 Paper + Browser smoke，把默认 `marshal` 官员迁移授权接入真实服 marker 证据。
2. 继续把官员权限矩阵扩到国库、外交、战争、政策/科技操作，但保持每个职责配置化，不写死到命令层。
3. Web 情报面可以继续展示“当前可操作者/授权官职”提示，让国家成员不用查文档也知道该找谁。
4. 后续若恢复 web 资源指挥面，必须继续保持当前 intel-only HUD 契约，先更新契约与 Browser smoke 再开放 UI。

---

## 2026-06-14 15:10 +08:00 本轮进展：marshal 资源区块迁移授权真实服 smoke 证据

本轮把上一刀留下的“还缺真实服证据”补完，并把证据变成 smoke 门禁：

- `scripts/smoke-starcore-paper-integration.ps1` 的嵌入式 Java harness 现在会：
  - 创建 `SmokeMarshal`。
  - 通过 join resolution 把 `SmokeMarshal` 加入 smoke 国家，确保他是非领袖成员。
  - 将 `SmokeMarshal` 任命为 `marshal`。
  - 用 `SmokeMarshal` 的玩家代理执行资源区块信标 GUI 迁移链路，而不是继续用 founder。
  - marker 输出 `officerMigration=marshal:member+gui+target+forced`。
- PowerShell smoke 外层新增 marker 断言：
  - Paper smoke 如果 PASS 但缺少 `officerMigration=marshal:member+gui+target+forced`，脚本会直接失败。
- `docs/RELEASE_CHECKLIST_2026-06-05.md` 已把该 marker 写入发布检查项。
- `docs/LATEST_VERIFICATION_SUMMARY.md` 已刷新到本轮 smoke / HUD / performance 证据。

最新验证：

- `mvn -q test`: PASS (`313` tests, `0` failures, `0` errors, `0` skipped)
- `mvn -q -DskipTests package`: PASS
- `.\scripts\smoke-starcore-paper-integration.ps1 -ProtectorApiSmoke -BrowserSmoke -TimeoutSeconds 420`: PASS
  - Paper marker 包含 `officerMigration=marshal:member+gui+target+forced`
  - Browser marker 继续包含 `commandUiRemoved=true`
- `.\scripts\check-starcore-performance-budget.ps1 -SmokeSummaryJsonPath target\smoke-harness-20260614-150846\smoke-summary.json`: PASS (`10/10`)
- `.\scripts\check-map-hud-contract.ps1 -RequireMirrorRoots`: PASS
- `codegraph sync .`: PASS（already up to date）
- 最新 jar: `target/starcore-0.1.0-SNAPSHOT.jar`
- jar size: `17737246`
- jar SHA256: `CB85F341CA0B28B4531CBEE3E18030051C7A07ECCC0029DDCA08D983223D5627`
- 最新 smoke summary: `target/smoke-harness-20260614-150846/smoke-summary.json`
- 最新 performance summary: `target/performance-budget-checks/check-20260614-150955/performance-budget-summary.json`
- 最新 HUD contract summary: `target/map-hud-contract-checks/check-20260614-151011/map-hud-contract-summary.json`

下一刀建议：

1. 继续把官员职责配置化扩到国库、外交、战争、政策/科技操作，沿用“配置白名单 + 服务层统一授权 + metadata 暴露”的模式。
2. Web 情报面的授权矩阵与当前访问者状态已在 17:18 段完成；下一步改为补 actor/reason 搜索和战争/财政/官员上下文定位。
3. 继续做国家领袖运营面，把资源区块收益趋势、余额缺口、资源上限和最近事件串起来。
4. 导入真实 Spark profile 后，做一轮完整 `verify-starcore-release.ps1 -IncludeSmoke -ProtectorApiSmoke -BrowserSmoke -BuildEvidencePack -BuildReleaseChannelAssets -SparkProfileSummaryPath <spark-profile-summary.json> -RequireRealSparkProfile`，把本轮证据打进发布材料。

---

## 2026-06-14 15:33 +08:00 本轮进展：国库支出官员授权配置化

本轮继续推进 P2“官员 / 事件 / 审计日志”的职责矩阵，把国库支出从纯管理员入口扩展为“管理员、国家领袖或配置授权官职”可操作：

- `config.yml` 新增 `nation.officers.treasury-withdraw-roles`，默认 `treasurer`。
- `ConfigurationService.nationTreasuryWithdrawOfficerRoles()` 复用统一的官职列表规范化逻辑：
  - trim / lower-case / `_` 转 `-`
  - 去重
  - 配置为空时回退默认 `treasurer`
- `/starcore treasury withdraw <nation> <amount> [reason...]` 现在允许：
  - `starcore.admin`
  - 目标国家 founder
  - 被任命为配置白名单官职的玩家，默认 `treasurer`
- `deposit / reward / income / tax` 仍保持管理员或系统结算入口，不把“凭空入账/批量结算”下放给普通官员。
- 新增中文提示 `command.treasury.withdraw-no-permission`，区分“国库支出授权不足”和管理员专用命令的 `no-admin`。
- Paper smoke 的治理探针新增非管理员 `SmokeTreasurer`：
  - 任命为 `treasurer`
  - 使用无 `starcore.admin` 的玩家代理执行本国 `/starcore treasury withdraw`
  - marker 输出 `treasuryOfficer=treasurer:withdraw`
  - 外层 smoke 如果缺少该 marker 会直接失败
- README、发布清单、最新验证摘要已同步该授权边界和 smoke 证据。

新增/更新测试护栏：

- `ConfigurationServiceResourceFeedbackConfigTest`
  - 锁住 `nation.officers.treasury-withdraw-roles` 默认 `treasurer`。
  - 锁住自定义官职列表会规范化并去重。
- `StarCoreCommandShortAliasDispatchTest`
  - 锁住非管理员 `treasurer` 可对本国执行 `/sc tr w`。
  - 锁住普通成员即使有基础命令权限也不能执行国库支出。
  - 测试 harness 现在能区分 `starcore.command` 与 `starcore.admin`，避免用“全权限 true”误证明官员授权。

最新验证：

- `mvn -q "-Dtest=StarCoreCommandShortAliasDispatchTest,ConfigurationServiceResourceFeedbackConfigTest" test`: PASS
- `mvn -q test`: PASS (`316` tests, `0` failures, `0` errors, `0` skipped)
- `mvn -q -DskipTests package`: PASS
- `.\scripts\smoke-starcore-paper-integration.ps1 -ProtectorApiSmoke -BrowserSmoke -TimeoutSeconds 420`: PASS
  - Paper marker 包含 `treasuryOfficer=treasurer:withdraw`
  - Paper marker 继续包含 `officerMigration=marshal:member+gui+target+forced`
  - Browser marker 继续包含 `commandUiRemoved=true`
- `.\scripts\check-starcore-performance-budget.ps1 -SmokeSummaryJsonPath target\smoke-harness-20260614-153139\smoke-summary.json`: PASS (`10/10`)
- `.\scripts\check-map-hud-contract.ps1 -RequireMirrorRoots`: PASS
- 最新 jar: `target/starcore-0.1.0-SNAPSHOT.jar`
- jar size: `17738333`
- jar SHA256: `D349B66B414C8D6CF2BEF053D7F71C43EB7DD6A3218C2F7FDD1F9418CD9C9A6D`
- 最新 smoke summary: `target/smoke-harness-20260614-153139/smoke-summary.json`
- 最新 performance summary: `target/performance-budget-checks/check-20260614-153245/performance-budget-summary.json`
- 最新 HUD contract summary: `target/map-hud-contract-checks/check-20260614-153245/map-hud-contract-summary.json`

下一刀建议：

1. 继续把外交 `set/propose`、战争 `declare/end`、政策/科技 `set/unlock/revoke` 拆成配置化官职白名单，保持“高风险系统命令不误下放”的边界。
2. 把官员授权结果暴露到 Web 情报面，例如国家详情显示“国库支出授权官职: treasurer”。
3. 把国库支出事件的 context 继续做成更可读的网页追责条目，显示 actor/amount/reason/balance。
4. 做一轮完整 release evidence pack，把 `treasuryOfficer=treasurer:withdraw` 证据打进发布材料。

---

按这个顺序继续，最省时间：

1. 资源区块信标 GUI 管理闭环
   - 本轮已完成确认页与确认后反馈增强。
   - 下一次只需要按需继续补“确认后即时刷新/声音/粒子/按钮材质”等体验细节。
   - 继续保持 GUI、命令、悬浮显示、网页情报面共用 `NationResourceDistrictMenuSupport / ViewSupport / OperationalSupport` 的文案语义。
2. 国家领袖运营面继续做深
   - 网页地图国家详情继续补资源区块收益趋势、阻塞原因分组、余额/资金缺口解释。
   - 保持 web 端为情报态，真实迁移仍走游戏内信标 GUI / 命令链。
   - 把圈地价格明细、国家等级、区块上限、资源区块上限统一成玩家一眼能读懂的运营面。
3. 财政 / 资源 / 经济账本继续做深
   - 本轮已完成基于事件系统的 `/sc ev a` 审计入口，也已补上原因字段与结构化 context 详情。
   - 下一步适合补分页、导出、时间范围过滤，以及可选独立 SQL ledger 表。
   - 目标是让管理员能查到“谁因为哪个操作花了多少钱 / 获得了多少资源”，并能按时间、类型、国家快速过滤。
4. 外部保护桥继续保持可选扩展
   - `ProtectorAPI` 软依赖桥已稳定，后续新增保护来源时继续走 ServiceLoader + 单桥故障隔离。
   - 不碰本插件原生圈地主链，不引入 WorldEdit / WorldGuard / Lands 硬依赖。

## 继续做时的硬规则

- 不要并行跑 `mvn package` 和 Paper 烟测
- 正确顺序始终是：
  1. `mvn -q test`
  2. `mvn -q package`
  3. Paper 烟测
  4. Browser 烟测
- 当前主看板只有这一份，先更新这里，再动其他总结文档

## 当前最值得做的事

当前最划算的下一刀，是把“已经能用”的国家运营面继续做成“玩家不用看文档也能管理”的 RPG 服务器产品面：

- 资源区块信标 GUI / 迁移交互闭环
- `inspect / migrate` 命令、信标 GUI、网页地图情报面、信标悬浮显示四端状态统一
- 国家领袖视角的网页地图管理面
- 资源区块与地图详情的联动
- 财政 / 资源 / 经济账本与最近操作审计
- 在稳定底座上继续按需扩外部保护桥

功能规划的主表已经移到 `docs/MODULE_PLAN.md`：

- P0：命令 / 工具 / 地图圈地稳定主链与 HUD 契约统一
- P1：国家领袖运营面、资源区块管理闭环、圈地体验与价格解释
- P2：政策/科技 ModifierEngine、外交/决议/战争联动、财政/资源账本、官员/事件审计
- P3：外部保护桥/API、发布运维性能

这一刀补完后，STARCORE 会更接近：

- 一个统一 SQL 运行面
- 一个统一命令/工具/网页圈地验证面
- 一个可选外部保护桥接面
- 一个更省维护成本的发布/验证流程
- 一个更完整的 RPG 国家运营管理面

## 2026-06-14 15:54 +08:00 本轮进展：外交直设官员授权配置化

本轮继续推进 P2“官员 / 事件 / 审计日志”的职责矩阵，把外交关系直设从普通 `starcore.command` 玩家可触达的状态变更收紧为“管理员、参与国家领袖或配置授权官职”可操作：

- `config.yml` 新增 `nation.officers.diplomacy-set-roles`，默认 `diplomat`。
- `ConfigurationService.nationDiplomacySetOfficerRoles()` 沿用共享官职白名单规范化逻辑，支持 trim、小写化、下划线转短横线和去重。
- `/starcore diplomacy set <nationA> <nationB> <relation>` 现在要求：
  - `starcore.admin`
  - 任一参与国家 founder
  - 任一参与国家的配置授权官职，默认 `diplomat`
- `/starcore diplomacy propose` 仍保持原有提案玩法；本轮只收紧 direct-set 这种直接改状态的入口。
- 新增中文提示 `command.diplomacy.set-no-permission`，避免和未知国家、未知关系混在一起。
- Paper smoke 现在真实任命非管理员 `SmokeDiplomat` 为 `diplomat`，执行外交直设，并把 marker 写为：
  - `diplomacyOfficer=diplomat:set`

测试与验证：

- `mvn -q "-Dtest=StarCoreCommandShortAliasDispatchTest,ConfigurationServiceResourceFeedbackConfigTest" test`: PASS
- `mvn -q test`: PASS (`319` tests)
- `mvn -q -DskipTests package`: PASS
- `.\scripts\smoke-starcore-paper-integration.ps1 -ProtectorApiSmoke -BrowserSmoke -TimeoutSeconds 420`: PASS
  - Paper marker 包含 `diplomacyOfficer=diplomat:set`
  - Paper marker 继续包含 `treasuryOfficer=treasurer:withdraw`
  - Paper marker 继续包含 `officerMigration=marshal:member+gui+target+forced`
  - Browser marker 继续包含 `commandUiRemoved=true`
- `.\scripts\check-starcore-performance-budget.ps1 -SmokeSummaryJsonPath target\smoke-harness-20260614-155248\smoke-summary.json`: PASS (`10/10`)
- `.\scripts\check-map-hud-contract.ps1 -RequireMirrorRoots`: PASS
- `.\scripts\build-latest-verification-summary.ps1`: PASS

最新证据：

- Smoke summary: `target/smoke-harness-20260614-155248/smoke-summary.json`
- Performance budget: `target/performance-budget-checks/check-20260614-155359/performance-budget-summary.json`
- HUD contract: `target/map-hud-contract-checks/check-20260614-155359/map-hud-contract-summary.json`
- Latest jar SHA256: `3CEF91AC845E003678C3F83BA9773CF04DC2A341C4B231A644519F1D79282036`

下一步：

1. 战争 `declare/end` 授权已在下一段完成；政策/科技 `set/clear/unlock/revoke` 授权已在 16:28 段完成。
2. 把官员授权结果暴露到 Web 情报面，例如国家详情显示“国库支出授权官职: treasurer / 外交直设授权官职: diplomat / 战争授权官职: marshal,diplomat / 策略授权官职: steward”。
3. 做一轮完整 release evidence pack，把 `policyOfficer=steward:clear+set`、`technologyOfficer=steward:revoke+unlock`、`warOfficer=marshal:declare+diplomat:end`、`diplomacyOfficer=diplomat:set`、`treasuryOfficer=treasurer:withdraw` 和 `officerMigration=marshal:member+gui+target+forced` 证据打进发布材料。

## 2026-06-14 16:09 +08:00 本轮进展：战争宣告/停战官员授权配置化

本轮继续推进 P2“官员 / 事件 / 审计日志”的职责矩阵，把战争状态变更拆成两个配置化授权入口，避免把军事/外交职责硬编码到命令层：

- `config.yml` 新增 `nation.officers.war-declare-roles`，默认 `marshal`。
- `config.yml` 新增 `nation.officers.war-end-roles`，默认 `marshal` 与 `diplomat`。
- `ConfigurationService` 新增：
  - `nationWarDeclareOfficerRoles()`
  - `nationWarEndOfficerRoles()`
- `/starcore war declare <nationA> <nationB>` 现在要求：
  - `starcore.admin`
  - 任一参与国家 founder
  - 任一参与国家的宣战授权官职，默认 `marshal`
- `/starcore war end <nationA> <nationB>` 现在要求：
  - `starcore.admin`
  - 任一参与国家 founder
  - 任一参与国家的停战授权官职，默认 `marshal` 或 `diplomat`
- 新增中文提示：
  - `command.war.declare-no-permission`
  - `command.war.end-no-permission`
- Paper smoke 现在真实任命非管理员 `SmokePeaceEnvoy` 为 `diplomat` 执行停战，再任命非管理员 `SmokeWarMarshal` 为 `marshal` 再次宣战，并把 marker 写为：
  - `warOfficer=marshal:declare+diplomat:end`

测试与验证：

- `mvn -q "-Dtest=StarCoreCommandShortAliasDispatchTest,ConfigurationServiceResourceFeedbackConfigTest" test`: PASS
- `mvn -q test`: PASS (`323` tests)
- `mvn -q -DskipTests package`: PASS
- `.\scripts\smoke-starcore-paper-integration.ps1 -ProtectorApiSmoke -BrowserSmoke -TimeoutSeconds 420`: PASS
  - Paper marker 包含 `warOfficer=marshal:declare+diplomat:end`
  - Paper marker 继续包含 `diplomacyOfficer=diplomat:set`
  - Paper marker 继续包含 `treasuryOfficer=treasurer:withdraw`
  - Paper marker 继续包含 `officerMigration=marshal:member+gui+target+forced`
  - Browser marker 继续包含 `commandUiRemoved=true`
- `.\scripts\check-starcore-performance-budget.ps1 -SmokeSummaryJsonPath target\smoke-harness-20260614-160744\smoke-summary.json`: PASS (`10/10`)
- `.\scripts\check-map-hud-contract.ps1 -RequireMirrorRoots`: PASS
- `.\scripts\build-latest-verification-summary.ps1`: PASS

最新证据：

- Smoke summary: `target/smoke-harness-20260614-160744/smoke-summary.json`
- Performance budget: `target/performance-budget-checks/check-20260614-160854/performance-budget-summary.json`
- HUD contract: `target/map-hud-contract-checks/check-20260614-160854/map-hud-contract-summary.json`
- Latest jar SHA256: `A456099E009C043F08EA074C11CDE927085DAD5AAA491B574CFAFD125F2B1EAD`

下一步：

1. 政策/科技 `set/clear/unlock/revoke` 配置化官员授权已在 16:28 段完成。
2. 把官员授权结果暴露到 Web 情报面，例如国家详情显示“迁移/国库/外交/战争/政策/科技授权官职”。
3. 做一轮完整 release evidence pack，把 `policyOfficer=steward:clear+set`、`technologyOfficer=steward:revoke+unlock`、`warOfficer=marshal:declare+diplomat:end`、`diplomacyOfficer=diplomat:set`、`treasuryOfficer=treasurer:withdraw` 和 `officerMigration=marshal:member+gui+target+forced` 证据打进发布材料。

## 2026-06-14 16:28 +08:00 本轮进展：政策/科技官员授权配置化

本轮继续推进 P2“官员 / 事件 / 审计日志”的职责矩阵，把政策与科技这类高风险策略命令从“领袖/管理员硬门槛”升级为“管理员、目标国家领袖或配置授权官职”可操作，同时保持不同动作可分别配置：

- `config.yml` 新增：
  - `nation.officers.policy-set-roles`，默认 `steward`
  - `nation.officers.policy-clear-roles`，默认 `steward`
  - `nation.officers.technology-unlock-roles`，默认 `steward`
  - `nation.officers.technology-revoke-roles`，默认 `steward`
- `ConfigurationService` 新增对应读取方法，并复用现有官职白名单规范化逻辑：
  - trim / lower-case / `_` 转 `-`
  - 去重
  - 配置为空时回退默认 `steward`
- `/starcore policy set <policy>` 与 `/starcore policy clear` 现在允许：
  - `starcore.admin`
  - 玩家所在国家 founder
  - 玩家所在国家的配置授权官职，默认 `steward`
- `/starcore technology unlock <nation> <technology>` 与 `/starcore technology revoke <nation> <technology>` 现在允许：
  - `starcore.admin`
  - 目标国家 founder
  - 目标国家的配置授权官职，默认 `steward`
- 新增中文提示：
  - `command.policy.set-no-permission`
  - `command.policy.clear-no-permission`
  - `command.technology.unlock-no-permission`
  - `command.technology.revoke-no-permission`
- 科技树 lore 从“管理员解锁”改为更准确的“解锁命令”，避免 UI 文案和授权模型冲突。
- Paper smoke 现在真实任命非管理员 `SmokeSteward` 为 `steward`，并验证：
  - policy clear
  - policy set `open_diplomacy`
  - technology revoke `logistics`
  - technology unlock `logistics`
  - marker 写为 `policyOfficer=steward:clear+set` 与 `technologyOfficer=steward:revoke+unlock`

测试与验证：

- `mvn -q "-Dtest=StarCoreCommandShortAliasDispatchTest,ConfigurationServiceResourceFeedbackConfigTest" test`: PASS
- `mvn -q test`: PASS (`328` tests)
- `mvn -q -DskipTests package`: PASS
- `.\scripts\smoke-starcore-paper-integration.ps1 -ProtectorApiSmoke -BrowserSmoke -TimeoutSeconds 420`: PASS
  - Paper marker 包含 `policyOfficer=steward:clear+set`
  - Paper marker 包含 `technologyOfficer=steward:revoke+unlock`
  - Paper marker 继续包含 `warOfficer=marshal:declare+diplomat:end`
  - Paper marker 继续包含 `diplomacyOfficer=diplomat:set`
  - Paper marker 继续包含 `treasuryOfficer=treasurer:withdraw`
  - Paper marker 继续包含 `officerMigration=marshal:member+gui+target+forced`
  - Browser marker 继续包含 `commandUiRemoved=true`
- `.\scripts\check-starcore-performance-budget.ps1 -SmokeSummaryJsonPath target\smoke-harness-20260614-162450\smoke-summary.json`: PASS (`10/10`)
- `.\scripts\check-map-hud-contract.ps1 -RequireMirrorRoots`: PASS
- `.\scripts\build-latest-verification-summary.ps1`: PASS

最新证据：

- Smoke summary: `target/smoke-harness-20260614-162450/smoke-summary.json`
- Performance budget: `target/performance-budget-checks/check-20260614-162549/performance-budget-summary.json`
- HUD contract: `target/map-hud-contract-checks/check-20260614-162549/map-hud-contract-summary.json`
- Latest jar SHA256: `A3002E82061C219E6754FACB4EBB785275421C6DFB6CACC815EE4FB3DC560EC2`

下一步：

1. 把官员授权结果暴露到 Web 情报面，例如国家详情显示“迁移/国库/外交/战争/政策/科技授权官职”。
2. 做一轮完整 release evidence pack，把 `policyOfficer=steward:clear+set`、`technologyOfficer=steward:revoke+unlock`、`warOfficer=marshal:declare+diplomat:end`、`diplomacyOfficer=diplomat:set`、`treasuryOfficer=treasurer:withdraw` 和 `officerMigration=marshal:member+gui+target+forced` 证据打进发布材料。
3. 继续加深完整事件日志的上下文追责，让政策/科技/战争/财政变更能按 actor、类型、国家和时间窗口快速查到。

## 2026-06-14 17:42 +08:00 本轮进展：完整事件日志 actor/reason/context 搜索

本轮继续推进 P2“官员 / 事件 / 审计日志”的追责链路，把完整事件日志从分类/资源区块/时间窗口筛选推进到可按文本和结构化上下文定位，避免前端或接口只靠固定事件类型硬写：

- `/api/map/events` 新增 `query` / `q` / `search` 通用搜索参数，会匹配事件类型、消息、分类、context、details key/value。
- `/api/map/events` 新增 `actor` 搜索参数，会在 `actor/operator/player/viewer/member/target/targetName` 等上下文字段中匹配。
- `/api/map/events` 新增 `reason` 搜索参数，会在 `reason/cause/operation/action/policy/technology/relation/warId` 等上下文字段中匹配。
- JSON 响应、JSON 导出、CSV header 和导出文件名都同步记录 `query/actor/reason`，导出仍沿用当前国家、分类、资源区块和时间窗口筛选。
- 国家详情完整事件日志 UI 新增紧凑搜索控件；分类、资源区块、时间范围、分页、CSV/JSON 导出都会保留当前搜索词。
- Browser smoke 会真实打开国家详情完整事件日志，输入搜索词后验证 DOM、条目文本和导出 URL，并写出 `eventSearch=%E8%B5%84%E6%BA%90:3`。
- Web 资源区块仍保持 intel-only 契约；本轮没有恢复旧 `resourceCommand*` 或 `open-resource-command` 指挥面。

测试与验证：

- `node --check src/main/resources/web/map/js/map.js`: PASS
- `node --check scripts/smoke-starcore-map-browser.mjs`: PASS
- `mvn -q "-Dtest=MapEventLogEndpointTest" test`: PASS
- `mvn -q "-Dtest=MapEventLogEndpointTest,MapWebServerTest,MapModuleViewerSnapshotContractTest" test`: PASS
- `mvn -q test`: PASS (`330` tests)
- `mvn -q -DskipTests package`: PASS
- `.\scripts\smoke-starcore-paper-integration.ps1 -ProtectorApiSmoke -BrowserSmoke -TimeoutSeconds 420`: PASS
  - Browser marker 包含 `eventSearch=%E8%B5%84%E6%BA%90:3`
  - Browser marker 继续包含 `eventQuery=resource:9`
  - Browser marker 继续包含 `eventLedger=resource:3`
  - Browser marker 继续包含 `eventLedgerExport=csv+json`
  - Browser marker 继续包含 `officerAuth=marshal+treasurer+diplomat+steward:9`
  - Browser marker 继续包含 `officerAccess=founder:9`
  - Browser marker 继续包含 `commandUiRemoved=true`
- `.\scripts\check-starcore-performance-budget.ps1 -SmokeSummaryJsonPath target\smoke-harness-20260614-173946\smoke-summary.json`: PASS (`10/10`, baseline trend `ok`)
- `.\scripts\check-map-hud-contract.ps1 -RequireMirrorRoots`: PASS
- `.\scripts\build-latest-verification-summary.ps1`: PASS

最新证据：

- Smoke summary: `target\smoke-harness-20260614-173946\smoke-summary.json`
- Performance budget: `target\performance-budget-checks\check-20260614-174052\performance-budget-summary.json`
- HUD contract: `target\map-hud-contract-checks\check-20260614-174052\map-hud-contract-summary.json`
- Browser DOM: `D:\qwq\项目\mapadd\test-server-paper-1.21.11\zcode-starcore-map-browser-smoke-20260614-173946.dom.html`
- Browser screenshot: `D:\qwq\项目\mapadd\test-server-paper-1.21.11\zcode-starcore-map-browser-smoke-20260614-173946.png`
- Paper log: `D:\qwq\项目\mapadd\test-server-paper-1.21.11\zcode-starcore-deep-integration-smoke-20260614-173946.out.log`
- Latest jar SHA256: `3B4ACEF76C812086A2B81D1DD4340C8B77E2625EC16242BE17DE619C053BE9B3`

下一步：

1. 继续把完整事件日志条目做成更强的上下文追责视图，例如将战争、财政、官员操作的关键 context 字段渲染成可点击筛选条件。
2. 给 `/api/map/events` 增加更多稳定契约样例，覆盖政策、科技、外交、战争、财政的 actor/reason 组合。
3. 做一次完整 release evidence/channel assets 打包，把 `eventSearch=%E8%B5%84%E6%BA%90:3` 和最新 `officerAccess=founder:9` 证据同步进发布材料。

## 2026-06-14 18:13 +08:00 本轮进展：完整事件日志上下文 chip 一键追责

本轮继续 P2“官员 / 事件 / 审计日志”的追责体验，把上一轮 `query/actor/reason` 搜索从“手动输入”推进成“条目上下文可点击筛选”：

- 国家详情完整事件日志条目现在会从事件 `details` 中提取可追责字段，渲染为紧凑 context chip。
- `actor/operator/player/viewer/member/target/targetName` 会映射到 `/api/map/events?actor=...`。
- `reason/cause/operation/action/policy/technology/relation/warId` 会映射到 `/api/map/events?reason=...`。
- 其他高价值上下文字段仍可走通用 `query`，避免按事件类型硬编码。
- 点击 chip 会清掉其它上下文搜索，只保留当前追责维度，减少多条件误叠加导致空结果。
- 搜索框旁新增当前上下文筛选 chip，用户能看见当前是 `query`、`actor` 还是 `reason` 状态。
- CSV/JSON 导出 URL 现在会保留 `query/actor/reason` 当前上下文筛选。
- Browser smoke 在主 smoke 国家里写入 `officer.audit` 审计事件，真实点击 `SmokeAuditor` actor chip，并锁住 `eventContext=actor-SmokeAuditor:1`。

测试与验证：

- `node --check src/main/resources/web/map/js/map.js`: PASS
- `node --check scripts/smoke-starcore-map-browser.mjs`: PASS
- `mvn -q "-Dtest=MapEventLogEndpointTest,MapWebServerTest,MapModuleViewerSnapshotContractTest" test`: PASS
- `mvn -q test`: PASS (`330` tests)
- `mvn -q -DskipTests package`: PASS
- `.\scripts\sync-map-preview-shell.ps1`: PASS
- `.\scripts\sync-map-preview-shell.ps1 -PreviewRoot ..\test-server-paper-1.21.11\plugins\map`: PASS
- `.\scripts\smoke-starcore-paper-integration.ps1 -ProtectorApiSmoke -BrowserSmoke -TimeoutSeconds 420`: PASS
  - Browser marker 包含 `eventContext=actor-SmokeAuditor:1`
  - Browser marker 继续包含 `eventSearch=%E8%B5%84%E6%BA%90:3`
  - Browser marker 继续包含 `eventLedgerExport=csv+json`
  - Browser marker 继续包含 `officerAccess=founder:9`
  - Browser marker 继续包含 `commandUiRemoved=true`
- `.\scripts\check-starcore-performance-budget.ps1 -SmokeSummaryJsonPath target\smoke-harness-20260614-181137\smoke-summary.json`: PASS (`10/10`, baseline trend `ok`)
- `.\scripts\check-map-hud-contract.ps1 -RequireMirrorRoots`: PASS
- 旧资源指挥 UI token 扫描：无命中
- `.\scripts\build-latest-verification-summary.ps1`: PASS

最新证据：

- Smoke summary: `target\smoke-harness-20260614-181137\smoke-summary.json`
- Performance budget: `target\performance-budget-checks\check-20260614-181248\performance-budget-summary.json`
- HUD contract: `target\map-hud-contract-checks\check-20260614-181248\map-hud-contract-summary.json`
- Browser DOM: `D:\qwq\项目\mapadd\test-server-paper-1.21.11\zcode-starcore-map-browser-smoke-20260614-181137.dom.html`
- Browser screenshot: `D:\qwq\项目\mapadd\test-server-paper-1.21.11\zcode-starcore-map-browser-smoke-20260614-181137.png`
- Paper log: `D:\qwq\项目\mapadd\test-server-paper-1.21.11\zcode-starcore-deep-integration-smoke-20260614-181137.out.log`
- Latest jar SHA256: `28AC8DEFEF0AFE5A9D35AB7CF41136D5F3D5F7D73DC0A77F50C36C374FF26EC8`
- Map asset hashes after sync:
  - `map.js`: `F2001248816A18FD84FC055AFBF1E8EDEF487E88A4C3BFD9C3F715E245A4F79C`
  - `styles.css`: `74ACCDC35E83140624B466C2C03A8FFB6FA633EB15C85A0B71451E40FD3315B8`

下一步：

1. 给完整事件日志补更细的上下文展示分组，例如把金额/余额保持只读，把 actor/reason/policy/warId 保持可点。
2. 给 Browser smoke 增加 reason chip 路径，和当前 actor chip 形成双维度追责证明。
3. 做 release evidence/channel assets 打包，把 `eventContext=actor-SmokeAuditor:1` 证据同步进发布材料。

## 2026-06-14 18:38 +08:00 本轮进展：完整事件日志 reason chip 与上下文字段分组

本轮继续上一段“上下文 chip 一键追责”，完成了当时的前两项下一步：

- `map.js` 新增集中式 `EVENT_LEDGER_CONTEXT_FIELDS` 字段表，把事件 `details` 按 `actor`、`reason`、通用 `query` 和只读 `facts` 分组。
- 可追责字段继续渲染成 `data-event-ledger-context-chip="true"` 按钮；`actor/operator/player/viewer/member` 走 `actor`，`reason/cause/operation/action/policy/technology/relation/warId` 走 `reason`。
- `amount/balance/members/claims/taxedMembers/skippedMembers/fixed/percent/minimumBalance` 现在渲染为 `data-event-ledger-context-readonly="true"` 的只读 facts pill，不再被误当成搜索按钮。
- Browser smoke 种子审计事件补入 `amount=12.34;balance=56.78`，真实点击 actor chip 后再复位并点击 reason chip。
- Browser smoke marker 现在同时锁住：
  - `eventContext=actor-SmokeAuditor:1`
  - `eventReason=reason-browser-context-chip:1`
  - `eventFacts=amount+balance:2`
- CSV/JSON 导出检查现在站在 reason chip 筛选状态上，确认导出 URL 带 `reason=browser-context-chip`。
- 外层 Paper smoke 新增 `eventReason` 与 `eventFacts` marker 硬断言，避免 Browser 脚本只做软验证。

测试与验证：

- `node --check src/main/resources/web/map/js/map.js`: PASS
- `node --check scripts/smoke-starcore-map-browser.mjs`: PASS
- `mvn -q "-Dtest=MapEventLogEndpointTest,MapWebServerTest,MapModuleViewerSnapshotContractTest" test`: PASS
- `mvn -q test`: PASS (`330` tests)
- `mvn -q -DskipTests package`: PASS
- `.\scripts\sync-map-preview-shell.ps1`: PASS
- `.\scripts\sync-map-preview-shell.ps1 -PreviewRoot ..\test-server-paper-1.21.11\plugins\map`: PASS
- `.\scripts\smoke-starcore-paper-integration.ps1 -ProtectorApiSmoke -BrowserSmoke -TimeoutSeconds 420`: PASS
  - Browser marker 包含 `eventContext=actor-SmokeAuditor:1`
  - Browser marker 包含 `eventReason=reason-browser-context-chip:1`
  - Browser marker 包含 `eventFacts=amount+balance:2`
  - Browser marker 继续包含 `eventSearch=%E8%B5%84%E6%BA%90:3`
  - Browser marker 继续包含 `eventLedgerExport=csv+json`
  - Browser marker 继续包含 `officerAccess=founder:9`
  - Browser marker 继续包含 `commandUiRemoved=true`
- `.\scripts\check-starcore-performance-budget.ps1 -SmokeSummaryJsonPath target\smoke-harness-20260614-183643\smoke-summary.json`: PASS (`10/10`, baseline trend `ok`)
- `.\scripts\check-map-hud-contract.ps1 -RequireMirrorRoots`: PASS
- 旧资源指挥 UI token 扫描：无命中
- `.\scripts\build-latest-verification-summary.ps1 -SmokeSummaryJsonPath target\smoke-harness-20260614-183643\smoke-summary.json -PerformanceBudgetSummaryPath target\performance-budget-checks\check-20260614-183824\performance-budget-summary.json -HudContractSummaryPath target\map-hud-contract-checks\check-20260614-183824\map-hud-contract-summary.json`: PASS

最新证据：

- Smoke summary: `target\smoke-harness-20260614-183643\smoke-summary.json`
- Performance budget: `target\performance-budget-checks\check-20260614-183824\performance-budget-summary.json`
- HUD contract: `target\map-hud-contract-checks\check-20260614-183824\map-hud-contract-summary.json`
- Browser DOM: `D:\qwq\项目\mapadd\test-server-paper-1.21.11\zcode-starcore-map-browser-smoke-20260614-183643.dom.html`
- Browser screenshot: `D:\qwq\项目\mapadd\test-server-paper-1.21.11\zcode-starcore-map-browser-smoke-20260614-183643.png`
- Paper log: `D:\qwq\项目\mapadd\test-server-paper-1.21.11\zcode-starcore-deep-integration-smoke-20260614-183643.out.log`
- Latest jar SHA256: `895774CCD470AE91E09F8606B2FA303F4626E05ABFDB94F9D25DB6AA98A37B37`
- Map asset hashes after sync:
  - `map.js`: `FC6522B749F9441A50F0EBC80538B0333FB66A1CD866768ECF47973A16CF25CE`
  - `styles.css`: `6AA805B55F0174244886EAD0082141A88D1A87BF28EB5E8F59FFFE2C36AB85D6`

下一步：

1. 跨事件跳转定位和更多 `/api/map/events` 契约样例已在 2026-06-14 18:59 段完成，并锁住 `eventJump=reason-browser-context-chip:1`。
2. 做 release evidence/channel assets 打包，把 `eventJump=reason-browser-context-chip:1`、`eventReason=reason-browser-context-chip:1` 和 `eventFacts=amount+balance:2` 证据同步进发布材料。
3. 给完整事件日志继续补移动端窄屏截图基线，重点看搜索、上下文 chip、facts pill 和跨事件按钮的密度。
