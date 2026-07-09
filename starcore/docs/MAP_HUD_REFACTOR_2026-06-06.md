# MAP HUD Refactor 2026-06-06

## Scope

This note captures the current frontend direction for the STARCORE web map HUD so follow-up iterations do not regress into a cluttered overlay layout.

Source of truth:

- `D:\qwq\项目\mapadd\starcore\src\main\resources\web\map\index.html`
- `D:\qwq\项目\mapadd\starcore\src\main\resources\web\map\css\styles.css`
- `D:\qwq\项目\mapadd\starcore\src\main\resources\web\map\js\map.js`

Runtime copies that must be synced after source edits:

- `D:\qwq\项目\mapadd\map\`
- `D:\qwq\项目\mapadd\test-server-paper-1.21.11\plugins\map\`
- Standard sync entrypoint for the static preview shell:
  - `D:\qwq\项目\mapadd\starcore\scripts\sync-map-preview-shell.ps1`

Snapshot truth surfaces for verification:

- `D:\qwq\项目\mapadd\test-server-paper-1.21.11\plugins\map\snapshot.json` is the real plugin-exported snapshot during local server runs.
- `D:\qwq\项目\mapadd\map\snapshot.json` is only the static preview shell copy. It must be manually synced if we want localhost preview data to match the plugin export.
- `D:\qwq\项目\mapadd\map\snapshot.js` is a legacy static fallback payload for preview-only use. If it is stale, the page can still show old sample data even when `snapshot.json` was already corrected.
- If the preview shows players, claims, or districts that do not match the plugin export, verify these two files before blaming the HUD code.

## UX direction

- Default state is fullscreen map first, with almost no visual obstruction.
- Sidebar is off-canvas by default and expands only when the operator asks for intel.
- Current visual target has shifted closer to BlueMap:
  - neutral utility styling over sci-fi HUD styling
  - minimal persistent controls on the map surface
  - expanded sidebar should read like a tool drawer, not a dashboard
- The left drawer should now feel closer to a hard-edge operations rail:
  - continuous section bands instead of stacked cards
  - hierarchy driven by dividers, density, and typography rather than glow
  - no decorative quick-jump chrome on the map surface
- The bottom-right status HUD is no longer part of the active layout target.
- Motion should stay lightweight and GSAP-driven. Avoid ornamental loops that block interaction.

## Current implementation

### Sidebar

- There is no dedicated sidebar header anymore.
- Expanded state should open directly into the section stack, like a compact tool drawer.
- The same top-left icon toggle must both open and close the drawer, so it needs to stay above the sidebar layer.
- Body sections should read like one continuous utility drawer separated by lines, not stacked dashboard cards.

### Top bar

- Sidebar toggle is icon only. Do not add a visible text label back into the HUD button.
- The top-left identity slot is plugin-driven:
  - show only recognized viewer avatar + player name
  - hide the slot entirely when `snapshot.viewer` is absent or unauthenticated
- Do not restore decorative brand text like `SC / STARCORE MAP / LIVE MAP` in the floating HUD.
- Language switching no longer belongs in the top bar by default. It now lives in the sidebar footer.

### Map surface

- Keep the default collapsed state very sparse:
  - left-top sidebar toggle
  - right-side zoom rail
  - right-bottom coordinates
- Do not put legend or claim controls back onto the main map surface unless the user explicitly asks for more persistent overlays.

### Status treatment

- The bottom-right status toast has been removed entirely to keep the map surface nearly unobstructed.
- Connection/feed state still exists in runtime logic, but there is no persistent status chip in the simplified drawer layout.
- `setStatus(...)` is still the state source for:
  - loading
  - polling
  - connected
  - static snapshot
  - reconnecting
  - offline / expired

### Expanded sidebar

- The expanded sidebar has been reduced further from dashboard styling toward a tool drawer:
  - no separate header band
  - sections begin immediately near the top edge
  - compact summary/intel rows instead of heavy metric cards
  - footer reduced to language toggle only
- On mobile, the drawer must not cover the entire viewport:
  - always leave a visible map edge/gutter
  - keep the drawer reading like a slide-out tool panel, not a fullscreen modal
- On compact layouts, interaction should quickly return the operator to the map:
  - tapping empty map space should dismiss the drawer
  - choosing a nation / player / resource focus action should collapse the drawer after the camera move
  - touch targets and safe-area padding should be sized for phones, not desktop hover assumptions
  - even when expanded, the drawer should keep a visible map gutter on phones
- Claim tools now live in a dedicated sidebar section instead of a floating map panel.
- Player selection should now behave like a first-class focus state:
  - clicking a player marker or a player row should keep map and list selection visually in sync
  - focused player markers should carry a stronger hard-edge selected state, not just an open tooltip
- Territory selection should now be available directly on the map surface:
  - clicking a territory polygon should select the nation without forcing bulky persistent chrome back onto the map
- Resource districts are now a map intel surface only:
  - keep district visibility, migration state, refresh timing, and target/beacon metadata
  - keep click-to-focus behavior from both map markers and the sidebar list
  - do not expose a web-side migration / confirmation panel; actual resource operations belong to the in-game GUI
- Nation operations now also include a ranked resource-district queue:
  - show the most urgent district first based on migration state, expected yield, and shortfall
  - keep select/focus actions embedded in the queue so the nation panel works as an intel surface, not a command console
  - keep quick filters for `all / ready / awaiting target / insufficient balance / waiting depletion`
  - keep the selected filter consistent with resource-list emphasis so the operator can scan both surfaces together
  - keep map resource markers visually synced with queue filter changes and selected district state instead of waiting for a full snapshot rerender
- Nation operations now also include an operations summary block for leaders:
  - actionable / waiting / blocked district counts
  - filter coverage
  - hourly-equivalent resource and experience throughput
  - level ETA based on current district throughput
  - total and peak migration shortfall
  - a short outlook line that explains what the nation should solve first

### 2026-06-08 cleanup target and current state

- The chosen cleanup target is now the intel-only resource-district contract:
  - no stale `resourceCommand*` state or submit/cancel methods
  - no POST path for resource-district migration from the web HUD
  - no dead `open-resource-command` action buttons in nation timelines or recent events
- Current file scan shows the assets are aligned:
  - source tree `starcore/src/main/resources/web/map` has no `resourceCommand` / `open-resource-command` / `nationDetailOpenCommand` / `section-resource-command` / `resource-command` matches
  - static preview shell `map/` has no matching web-side command surface
  - plugin runtime copy `test-server-paper-1.21.11/plugins/map/` has no matching web-side command surface
- If the web HUD ever regains a resource-district command panel, update all three asset trees and the Browser smoke contract in the same change.
- Resource district labels that remain in the sidebar are now explicitly intel-oriented:
  - stage
  - next step
  - restriction
  - shortfall
- Browser smoke now follows this direction:
  - old `#resource-command-panel` and `confirmUi=true` assertions are no longer the release gate
  - the smoke script asserts the command panel, command buttons, stale methods, and stale state are absent
  - the new PASS flag is `commandUiRemoved=true`
- Packaging note:
  - if a full Paper BrowserSmoke shows `打开指挥面` after source scans are clean, first check whether `test-server-paper-1.21.11\plugins\map` was left with stale exported assets from an earlier jar run
  - `mvn -q package` must be rerun after HUD source edits so `target\starcore-0.1.0-SNAPSHOT.jar` embeds clean web assets
  - after a failed smoke that exported old assets, re-copy source `index.html`, `css/styles.css`, and `js/map.js` into the Paper `plugins\map` runtime directory before local static preview verification
- Dynamic buttons rendered after snapshot updates now inherit the same restrained GSAP hover/press motion:
  - world filter chips
  - nation detail action buttons
  - timeline / recommendation / priority queue action buttons
- Static preview shells now degrade more cleanly after API 404 fallback:
  - use the synced `snapshot.json` / `snapshot.js` as the truth source
  - stop repeated stream reconnect noise when the preview host has no `/api/map/*`
- The empty / low-data map state should still feel intentional:
  - keep a restrained grid / paper texture on the map floor
  - keep sidebar surfaces layered like a hard-edge operations sheet, not a glowing modal slab
  - prefer subtle tonal separation over thicker borders, pills, or decorative overlays

## Guardrails for future edits

- Keep the map dominant. Do not move back to a permanently open bulky sidebar.
- Preserve existing DOM ids used by runtime logic unless the JS hook points are updated in the same change.
- Do not reintroduce a decorative sidebar header, search bar, status chip, or quick-jump pills unless the user explicitly asks for more operator chrome.
- Do not reintroduce a floating status HUD unless the user explicitly asks for it.
- Avoid reintroducing glow-heavy, decorative HUD treatments if the target remains BlueMap-like utility UI.
- If status visuals change again, keep them lightweight and inside the sidebar/header rhythm instead of adding a separate modal-like overlay.
- Do not keep a web-side resource-district action panel, migration confirmation button, or browser-side command flow unless the product decision explicitly says the web HUD should own that operation surface.
- If a test or summary still mentions `confirmUi=true`, check whether it is stale browser-smoke wording before treating it as proof that the web command panel exists.
- Always verify both desktop and mobile after HUD changes.

## Verification checklist

- Sync source changes into both runtime copies.
- If the task requires "real data" validation, also sync or directly inspect the plugin-exported `snapshot.json` instead of trusting the static preview shell copy.
- If a static localhost preview still shows stale intel after `snapshot.json` was synced, inspect `snapshot.js` and the `resolveSnapshot()` fallback order before assuming the UI is caching bad DOM.
- Run `node --check` on all three `map.js` copies.
- Run `mvn -q test` in `D:\qwq\项目\mapadd\starcore`.
- Search source, preview, and runtime copies for `resourceCommand|open-resource-command|nationDetailOpenCommand|section-resource-command` and confirm the result matches the chosen contract in all three trees.

```powershell
$pattern = 'resourceCommand|open-resource-command|nationDetailOpenCommand|section-resource-command'
rg -n $pattern 'starcore\src\main\resources\web\map' 'map' 'test-server-paper-1.21.11\plugins\map'
```

- For static preview shells, use `?sidebar=open` to inspect the expanded drawer without depending on runtime toggle wiring.
- Reload the local preview and verify:
  - default fullscreen map
  - sidebar opens and closes cleanly
  - expanded sidebar has no dedicated top header block
  - top-left icon toggle remains clickable while the drawer is open
  - no bottom-right status HUD is rendered
  - toggle remains icon-only
  - recognized viewer avatar + name appear only when plugin data is present
  - territory polygons can select a nation directly on the map
  - player list rows and player markers share the same selected state
  - resource section matches the chosen contract: either no web-side action panel/migration buttons, or a fully working command panel in all three trees
  - nation operations priority filters can switch state without requiring a full snapshot rerender
  - resource list emphasis and map marker highlighting stay synchronized with the selected district/filter
  - recent-log resource actions match the chosen contract: inspect/focus only for intel mode, or command action present and working for command mode
  - preview `snapshot.json` matches the intended truth source for that check
