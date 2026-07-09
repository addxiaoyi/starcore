# STARCORE Latest Verification Summary

Generated on 2026-06-15 08:03:35 +08:00 from current workspace state.

## Build and test

- `mvn -q test`: `332` tests, `0` failures, `0` errors, `0` skipped
- `mvn -q package`: PASS
- Jar: `D:\qwq\项目\mapadd\starcore\target\starcore-0.1.0-SNAPSHOT.jar`
- Jar size: `17754705` bytes
- Jar SHA256: `46C5F431B9CFFC6120CABB94391AC4789BAF40B8175E80BEE2254604EBC5BC00`
- Runtime tool selfcheck: `ok`
- Performance budget: `ok` (10/10 pass, 0 not included)

## Map HUD Contract

- Map HUD contract summary JSON: `D:\qwq\项目\mapadd\starcore\target\map-hud-contract-checks\check-20260615-065742\map-hud-contract-summary.json`
- Contract: `intel-only-resource-district`
- Status: `ok`
- Resource-command scan pattern: `resourceCommand|open-resource-command|nationDetailOpenCommand|section-resource-command|resource-command`
- `source`: `ok`, matches=`0`, path=`D:\qwq\项目\mapadd\starcore\src\main\resources\web\map`
- `static-preview`: `ok`, matches=`0`, path=`D:\qwq\项目\mapadd\map`
- `runtime-map`: `ok`, matches=`0`, path=`D:\qwq\项目\mapadd\test-server-paper-1.21.11\plugins\map`
- Browser smoke contract: `ok`, path=`D:\qwq\项目\mapadd\starcore\scripts\smoke-starcore-map-browser.mjs`

## Latest smoke

- Smoke summary JSON: `D:\qwq\项目\mapadd\starcore\target\smoke-harness-20260615-065629\smoke-summary.json`
- Health: `200`
- Marker: `PASS`
- Marker line: `[06:57:11 INFO]: [STARCORESmokeHarness] STARCORE_SMOKE_PASS nation=Smokemqedwh0k claims=5 price=347.13 districts=1 claimTool=GOLDEN_SHOVEL treasury=3368.50 diplomacy=war policy=civil_industry resources=food:150,ore:64 resolution=join_nation:enacted technology=logistics war=true officer=marshal officerMigration=marshal:member+gui+target+forced event=resource eventFamilies=finance+war+officer+diplomacy+strategy+territory+nation:7 migration=gui+mined:world:211:211+forced:world:211:212+feedbackSound:5+worldSound:7+particles:7+actionbar:3+title:2+bossbar:4+bossbarHide:4 resourceExplanationRuntime=ready+awaiting-target+waiting-depletion+insufficient-balance+player-offline:5 protector=runtime:MockProtectorSmoke@219:219 claimExplanation=externalProtection:1 webClaim=command:218001a1 webClaimFeedback=confirmSound:1+confirmActionbar:1+confirmTitle:0+confirmBossbar:1+confirmBossbarHide:1 onlineWebClaim=SmokeEnvoy:d1182331+pendingSound:1+pendingActionbar:1+pendingTitle:0+pendingBossbar:1+pendingBossbarHide:0+cancelSound:1+cancelActionbar:1+cancelTitle:0+cancelBossbar:1+cancelBossbarHide:0+cancelTyped:1 nationOperationFeedback=operationSound:4+operationActionbar:4+operationTitle:1+operationBossbar:4+operationBossbarHide:0 eventCommandSources=war+officer+treasury:6 eventCommandSourcesExtended=war+officer+treasury+diplomacy+strategy:14 strategyFeedback=strategySound:9+strategyActionbar:9+strategyTitle:6+strategyBossbar:9+strategyBossbarHide:0+policyOfficer=steward:clear+set+technologyOfficer=steward:revoke+unlock+strategyCommandEvents=policySet+policyClear+technologyUnlock+technologyRevoke:6+warOfficer=marshal:declare+diplomat:end+warCommandEvents=founderDeclare+diplomatEnd+marshalDeclare:3 governanceFeedback=governanceSound:8+governanceActionbar:8+governanceTitle:4+governanceBossbar:8+governanceBossbarHide:0+diplomacy:2+officer:2+treasury:4+treasuryOfficer=treasurer:withdraw+diplomacyOfficer=diplomat:set+diplomacyCommandEvents=founderSet+diplomatSet:2+governanceCommandEvents=officerAppoint+officerRemove+treasuryWithdraw:3 viewer=ok beacon=3400,63,3400:BEACON mapSummary=5 territory polygon(s), 0 player marker(s), 1 resource district marker(s)`
- Web claim smoke: `PASS` (provider=MockProtectorSmoke chunk=219,219 bounds=3504,3504->3535,3535)
- Browser smoke: `PASS` (STARCORE_BROWSER_SMOKE_PASS viewer=SmokePlayer nation=Smokemqedwh0k balance=9999066.05 nationDetail=true officerAuth=marshal+treasurer+diplomat+steward:9 officerAccess=founder:9 nationAction=true recentLog=5 recentLogFilter=resource:1 eventQuery=resource:9 eventLedger=resource:3 eventOps=resource+explanation+auth+group:3 eventOpsFamilies=finance+war+officer+diplomacy+strategy+territory+nation:7 eventSearch=%E8%B5%84%E6%BA%90:3 eventContext=actor-SmokeAuditor:1 eventReason=reason-browser-context-chip:1 eventJump=reason-browser-context-chip:1 eventMobile=390x844:6 eventFamilies=finance+war+officer+diplomacy+strategy+territory+nation:7 eventFamilyMobile=finance+war+officer+diplomacy+strategy+territory+nation:7 eventFacts=amount+balance:2 eventLedgerExport=csv+json resourceAction=ready resourceExplanation=ready:12 resourceExplanationFixture=ready+awaiting-target+waiting-depletion+insufficient-balance+player-offline:5 resourceExplanationRuntime=ready+awaiting-target+waiting-depletion+insufficient-balance+player-offline:5 resourceCost=100000.00 commandUiRemoved=true browser=Edg/149.0.4022.62)
- Browser DOM: `D:\qwq\项目\mapadd\test-server-paper-1.21.11\zcode-starcore-map-browser-smoke-20260615-065629.dom.html`
- Browser screenshot: `D:\qwq\项目\mapadd\test-server-paper-1.21.11\zcode-starcore-map-browser-smoke-20260615-065629.png`
- Browser mobile screenshot: `D:\qwq\项目\mapadd\test-server-paper-1.21.11\zcode-starcore-map-browser-smoke-20260615-065629.mobile.png`
- Paper log: `D:\qwq\项目\mapadd\test-server-paper-1.21.11\zcode-starcore-deep-integration-smoke-20260615-065629.out.log`

## Runtime Tool Selfcheck

- Runtime tool selfcheck summary JSON: `D:\qwq\项目\mapadd\starcore\target\runtime-tool-selfchecks\selfcheck-20260610-140012\runtime-tool-selfcheck-summary.json`
- Restore `-ReplaceExisting`: `PASS`
- Restore safeguard root: `D:\qwq\项目\mapadd\starcore\target\runtime-tool-selfchecks\selfcheck-20260610-140012\runtime-restores\pre-restore-20260610-140015`
- Generated backup zip: `D:\qwq\项目\mapadd\starcore\target\runtime-tool-selfchecks\selfcheck-20260610-140012\runtime-backups\starcore-runtime-backup-20260610-140013.zip`
- MySQL precheck branch: `warning` / tcpReachable=`False`
- MySQL stale SQLite files: `starcore.db`, `starcore.db-wal`, `starcore.db-shm`
- ProtectorAPI reference sync: `ok` / head=`88ced9783aaffffe333b57610162ac8ef9759760` / fetched=`True` / fastForwarded=`False`
- ProtectorAPI reference sync summary JSON: `D:\qwq\项目\mapadd\starcore\target\protectorapi-reference-syncs\sync-20260610-140018\protectorapi-reference-sync-summary.json`
- ProtectorAPI reference check: `ok` / head=`88ced9783aaffffe333b57610162ac8ef9759760`
- ProtectorAPI reference check summary JSON: `D:\qwq\项目\mapadd\starcore\target\protectorapi-reference-checks\check-20260610-140019\protectorapi-reference-check-summary.json`

## Performance Budgets

- Performance budget summary JSON: `D:\qwq\项目\mapadd\starcore\target\performance-budget-checks\check-20260615-065755\performance-budget-summary.json`
- Status: `ok` / pass=`10` / fail=`0` / not included=`0`
- Baseline trend: `ok` / mode=`history` / aggregation=`median` / samples=`5/5` / comparisons=`12` / latest=`D:\qwq\项目\mapadd\starcore\target\performance-budget-checks\check-20260615-000330\performance-budget-summary.json`
- `claim_lookup`: `pass`, suites=`3`, tests=`10`, time=`0.063s` / budget=`2.000s`
- `claim_lookup_cases`: `pass`, testcases=`6`, time=`0.036s` / budget=`1.000s`
- `claim_lookup_batch`: `pass`, testcases=`1`, time=`0.010s` / budget=`1.500s`
- `map_render`: `pass`, suites=`5`, tests=`29`, time=`0.136s` / budget=`3.000s`
- `map_render_cases`: `pass`, testcases=`7`, time=`0.066s` / budget=`1.500s`
- `map_render_batch`: `pass`, testcases=`1`, time=`0.119s` / budget=`2.000s`
- `sql_flush`: `pass`, suites=`5`, tests=`10`, time=`0.365s` / budget=`5.000s`
- `sql_flush_cases`: `pass`, testcases=`5`, time=`0.253s` / budget=`2.000s`
- `sql_flush_batch`: `pass`, testcases=`1`, time=`0.229s` / budget=`4.000s`
- `browser_snapshot`: `pass`, browser DOM=102759 bytes, browser screenshot=414958 bytes, browser mobile screenshot=436810 bytes

## Harness snapshot

- Message: `nation=Smokemqedwh0k claims=5 price=347.13 districts=1 claimTool=GOLDEN_SHOVEL treasury=3368.50 diplomacy=war policy=civil_industry resources=food:150,ore:64 resolution=join_nation:enacted technology=logistics war=true officer=marshal officerMigration=marshal:member+gui+target+forced event=resource eventFamilies=finance+war+officer+diplomacy+strategy+territory+nation:7 migration=gui+mined:world:211:211+forced:world:211:212+feedbackSound:5+worldSound:7+particles:7+actionbar:3+title:2+bossbar:4+bossbarHide:4 resourceExplanationRuntime=ready+awaiting-target+waiting-depletion+insufficient-balance+player-offline:5 protector=runtime:MockProtectorSmoke@219:219 claimExplanation=externalProtection:1 webClaim=command:218001a1 webClaimFeedback=confirmSound:1+confirmActionbar:1+confirmTitle:0+confirmBossbar:1+confirmBossbarHide:1 onlineWebClaim=SmokeEnvoy:d1182331+pendingSound:1+pendingActionbar:1+pendingTitle:0+pendingBossbar:1+pendingBossbarHide:0+cancelSound:1+cancelActionbar:1+cancelTitle:0+cancelBossbar:1+cancelBossbarHide:0+cancelTyped:1 nationOperationFeedback=operationSound:4+operationActionbar:4+operationTitle:1+operationBossbar:4+operationBossbarHide:0 eventCommandSources=war+officer+treasury:6 eventCommandSourcesExtended=war+officer+treasury+diplomacy+strategy:14 strategyFeedback=strategySound:9+strategyActionbar:9+strategyTitle:6+strategyBossbar:9+strategyBossbarHide:0+policyOfficer=steward:clear+set+technologyOfficer=steward:revoke+unlock+strategyCommandEvents=policySet+policyClear+technologyUnlock+technologyRevoke:6+warOfficer=marshal:declare+diplomat:end+warCommandEvents=founderDeclare+diplomatEnd+marshalDeclare:3 governanceFeedback=governanceSound:8+governanceActionbar:8+governanceTitle:4+governanceBossbar:8+governanceBossbarHide:0+diplomacy:2+officer:2+treasury:4+treasuryOfficer=treasurer:withdraw+diplomacyOfficer=diplomat:set+diplomacyCommandEvents=founderSet+diplomatSet:2+governanceCommandEvents=officerAppoint+officerRemove+treasuryWithdraw:3 viewer=ok beacon=3400,63,3400:BEACON mapSummary=5 territory polygon(s), 0 player marker(s), 1 resource district marker(s)`
- Viewer: `SmokePlayer` / nation `Smokemqedwh0k` / balance `9999066.05`
- Viewer government: `君主制` / role `国家领袖` / online `在线`
- Viewer claims: `5/20` / resources `4/1` / experience `252`
- SQLite counts: `starcore_diplomacy_state=10`, `starcore_event_state=271`, `starcore_metadata=3`, `starcore_nation_resource_district_state=40`, `starcore_nation_state=87`, `starcore_officer_state=25`, `starcore_player_balances=10`, `starcore_policy_state=24`, `starcore_resolution_state=27`, `starcore_resource_state=7`, `starcore_technology_state=7`, `starcore_territory_claims=5`, `starcore_treasury_state=7`, `starcore_war_state=7`
