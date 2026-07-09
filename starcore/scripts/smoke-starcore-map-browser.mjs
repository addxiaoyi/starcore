#!/usr/bin/env node

import { spawn, spawnSync } from 'node:child_process';
import { mkdir, writeFile } from 'node:fs/promises';
import { dirname } from 'node:path';
import { createServer } from 'node:net';

function parseArgs(argv) {
  const result = {};
  for (let index = 0; index < argv.length; index += 1) {
    const raw = argv[index];
    if (!raw.startsWith('--')) {
      continue;
    }
    const key = raw.slice(2);
    const next = argv[index + 1];
    if (!next || next.startsWith('--')) {
      result[key] = 'true';
      continue;
    }
    result[key] = next;
    index += 1;
  }
  return result;
}

function requireArg(args, key) {
  const value = args[key];
  if (!value) {
    throw new Error(`Missing required --${key}`);
  }
  return value;
}

async function reservePort() {
  return new Promise((resolve, reject) => {
    const server = createServer();
    server.listen(0, '127.0.0.1', () => {
      const address = server.address();
      const port = address.port;
      server.close(() => resolve(port));
    });
    server.on('error', reject);
  });
}

function delay(ms) {
  return new Promise((resolve) => setTimeout(resolve, ms));
}

async function waitForJson(url, timeoutMs) {
  const started = Date.now();
  let lastError = null;
  while (Date.now() - started < timeoutMs) {
    try {
      const response = await fetch(url);
      if (response.ok) {
        return await response.json();
      }
      lastError = new Error(`HTTP ${response.status}`);
    } catch (error) {
      lastError = error;
    }
    await delay(250);
  }
  throw new Error(`Timed out waiting for ${url}: ${lastError?.message || 'no response'}`);
}

class CdpClient {
  constructor(socket) {
    this.socket = socket;
    this.nextId = 1;
    this.pending = new Map();
    this.events = [];
    socket.addEventListener('message', (event) => this.handleMessage(event.data));
    socket.addEventListener('close', () => {
      for (const { reject } of this.pending.values()) {
        reject(new Error('CDP socket closed'));
      }
      this.pending.clear();
    });
  }

  static async connect(url) {
    return new Promise((resolve, reject) => {
      const socket = new WebSocket(url);
      socket.addEventListener('open', () => resolve(new CdpClient(socket)), { once: true });
      socket.addEventListener('error', () => reject(new Error(`Unable to connect CDP socket: ${url}`)), { once: true });
    });
  }

  handleMessage(raw) {
    const message = JSON.parse(raw);
    if (message.id && this.pending.has(message.id)) {
      const { resolve, reject } = this.pending.get(message.id);
      this.pending.delete(message.id);
      if (message.error) {
        reject(new Error(`${message.error.message || 'CDP error'} ${message.error.data || ''}`.trim()));
      } else {
        resolve(message.result || {});
      }
      return;
    }
    if (message.method) {
      this.events.push(message);
      if (this.events.length > 200) {
        this.events.shift();
      }
    }
  }

  send(method, params = {}) {
    const id = this.nextId;
    this.nextId += 1;
    const payload = JSON.stringify({ id, method, params });
    return new Promise((resolve, reject) => {
      this.pending.set(id, { resolve, reject });
      this.socket.send(payload);
    });
  }

  close() {
    this.socket.close();
  }
}

function killProcessTree(pid) {
  if (!pid) {
    return;
  }
  spawnSync('taskkill.exe', ['/PID', String(pid), '/T', '/F'], { stdio: 'ignore' });
}

async function main() {
  const args = parseArgs(process.argv.slice(2));
  const url = requireArg(args, 'url');
  const viewerName = requireArg(args, 'viewer-name');
  const viewerNation = requireArg(args, 'viewer-nation');
  const viewerBalance = requireArg(args, 'viewer-balance');
  const viewerGovernment = String(args['viewer-government'] || '').trim();
  const viewerRole = String(args['viewer-role'] || '').trim();
  const viewerFounder = String(args['viewer-founder'] || '').trim();
  const viewerClaims = String(args['viewer-claims'] || '').trim();
  const viewerCityStates = String(args['viewer-city-states'] || '').trim();
  const viewerResources = String(args['viewer-resources'] || '').trim();
  const viewerProgress = String(args['viewer-progress'] || '').trim();
  const viewerNextLevel = String(args['viewer-next-level'] || '').trim();
  const viewerOnline = String(args['viewer-online'] || '').trim();
  const resourceRuntimeLowUrl = String(args['resource-runtime-low-url'] || '').trim();
  const resourceRuntimeOfflineUrl = String(args['resource-runtime-offline-url'] || '').trim();
  const browser = requireArg(args, 'browser');
  const domFile = requireArg(args, 'dom');
  const screenshotFile = requireArg(args, 'screenshot');
  const mobileScreenshotFile = String(args['mobile-screenshot'] || '').trim();
  const profile = requireArg(args, 'profile');
  const timeoutMs = Number(args['timeout-ms'] || 45000);
  const port = await reservePort();
  await mkdir(profile, { recursive: true });
  await mkdir(dirname(domFile), { recursive: true });
  await mkdir(dirname(screenshotFile), { recursive: true });
  if (mobileScreenshotFile) {
    await mkdir(dirname(mobileScreenshotFile), { recursive: true });
  }

  const browserArgs = [
    '--headless=new',
    `--remote-debugging-port=${port}`,
    '--disable-gpu',
    '--disable-extensions',
    '--disable-background-networking',
    '--disable-component-update',
    '--disable-default-apps',
    '--disable-sync',
    '--disable-features=PaintHolding',
    '--no-first-run',
    '--no-default-browser-check',
    '--lang=zh-CN',
    `--user-data-dir=${profile}`,
    '--window-size=1440,1000',
    'about:blank',
  ];

  const proc = spawn(browser, browserArgs, {
    stdio: ['ignore', 'pipe', 'pipe'],
    windowsHide: true,
  });
  let stderr = '';
  proc.stderr.on('data', (chunk) => {
    stderr += chunk.toString();
  });

  let client = null;
  try {
    const version = await waitForJson(`http://127.0.0.1:${port}/json/version`, Math.min(timeoutMs, 15000));
    const targets = await waitForJson(`http://127.0.0.1:${port}/json/list`, Math.min(timeoutMs, 15000));
    const page = targets.find((target) => target.type === 'page' && target.webSocketDebuggerUrl);
    if (!page) {
      throw new Error('No browser page target was available.');
    }
    client = await CdpClient.connect(page.webSocketDebuggerUrl);
    await client.send('Page.enable');
    await client.send('Runtime.enable');
    await client.send('Emulation.setDeviceMetricsOverride', {
      width: 1440,
      height: 1000,
      deviceScaleFactor: 1,
      mobile: false,
    });
    await client.send('Page.addScriptToEvaluateOnNewDocument', {
      source: `
        window.__starcoreSmokeErrors = [];
        window.addEventListener('error', event => window.__starcoreSmokeErrors.push(String(event.message || event.error || 'error')));
        window.addEventListener('unhandledrejection', event => window.__starcoreSmokeErrors.push(String(event.reason || 'unhandled rejection')));
      `,
    });
    await client.send('Page.navigate', { url });

    const started = Date.now();
    let state = null;
    while (Date.now() - started < timeoutMs) {
      const evaluation = await client.send('Runtime.evaluate', {
        expression: `(() => {
          const normalizeText = (value) => String(value || '').replace(/\\s+/g, ' ').trim();
          const formatMoney = (value) => {
            const numeric = Number(value);
            if (!Number.isFinite(numeric)) {
              return String(value || '0.00');
            }
            return numeric.toFixed(2);
          };
          const bodyText = normalizeText(document.body ? document.body.textContent : '');
          const intelText = normalizeText(document.querySelector('#intel-grid')?.textContent || '');
          let html = document.documentElement ? document.documentElement.outerHTML : '';
          const balance = document.querySelector('#claim-balance')?.textContent?.trim() || '';
          if (window.strategicMap && typeof window.strategicMap.setSidebarCollapsed === 'function') {
            window.strategicMap.setSidebarCollapsed(false, { animate: false });
          }
          const nationItems = Array.from(document.querySelectorAll('#nation-list li[data-nation]'));
          const nationMatch = window.strategicMap && Array.isArray(window.strategicMap.nationData)
            ? window.strategicMap.nationData.find(entry => String(entry.name || '') === ${JSON.stringify(viewerNation)})
                || window.strategicMap.nationData[0]
                || null
            : null;
          if (window.strategicMap && typeof window.strategicMap.setSelectedNation === 'function' && nationMatch && window.strategicMap.selectedNationId !== nationMatch.id) {
            window.strategicMap.setSelectedNation(nationMatch.id, { preserveView: true });
          }
          const resourceItems = Array.from(document.querySelectorAll('#resource-list li[data-resource]'));
          const ownResourceMatch = window.strategicMap && Array.isArray(window.strategicMap.resourceMarkers) && nationMatch
            ? window.strategicMap.resourceMarkers.find(resource => String((((resource || {}).data || {}).metadata || {}).nationId || '') === String(nationMatch.id || ''))
              || null
            : null;
          const currentSelectedResource = window.strategicMap && typeof window.strategicMap.selectedResource === 'function'
            ? window.strategicMap.selectedResource()
            : null;
          const currentSelectedNationId = String(((((currentSelectedResource || {}).data || {}).metadata || {}).nationId) || '');
          if (window.strategicMap
            && typeof window.strategicMap.setSelectedResource === 'function'
            && resourceItems.length > 0
            && (!window.strategicMap.selectedResourceId || (nationMatch && currentSelectedNationId !== String(nationMatch.id || '')))) {
            window.strategicMap.setSelectedResource(ownResourceMatch?.id || resourceItems[0].dataset.resource || '');
          }
          const nationDetailPanel = document.querySelector('#nation-detail-panel');
          const nationDetailText = normalizeText(nationDetailPanel?.textContent || '');
          const nationDetailActions = Array.from(document.querySelectorAll('#nation-detail-panel [data-action]'))
            .map(node => normalizeText(node.textContent || ''))
            .filter(Boolean);
          const operationsSummaryText = normalizeText(document.querySelector('.nation-detail-block-wide')?.textContent || '');
          const operationsFocusNode = document.querySelector('#nation-operations-focus');
          const operationsFocusText = normalizeText(operationsFocusNode?.textContent || '');
          const operationsFocusActions = Array.from(document.querySelectorAll('#nation-operations-focus [data-action]'))
            .map(node => normalizeText(node.textContent || ''))
            .filter(Boolean);
          const operationsMetricLabels = Array.from(document.querySelectorAll('.nation-ops-label'))
            .map(node => normalizeText(node.textContent || ''))
            .filter(Boolean);
          const operationGroupButtons = Array.from(document.querySelectorAll('[data-action="open-operation-group"]'))
            .map(node => normalizeText(node.textContent || ''))
            .filter(Boolean);
          const recommendationButtons = Array.from(document.querySelectorAll('#nation-detail-panel [data-action="open-operation-group"]'))
            .map(node => normalizeText(node.textContent || ''))
            .filter(Boolean);
          const recommendationSpecificButtons = recommendationButtons.filter(text => ![
            '优先处理',
            'Handle First',
            '打开此组',
            'Open Group',
            '处理这一类',
            'Handle This Type',
          ].includes(text));
          const operationsForecastText = normalizeText(Array.from(document.querySelectorAll('.nation-detail-block-wide'))
            .map(node => node.textContent || '')
            .join(' '));
          const timelineItems = Array.from(document.querySelectorAll('#nation-operations-timeline .nation-timeline-item'));
          const timelineTexts = timelineItems
            .map(node => normalizeText(node.textContent || ''))
            .filter(Boolean);
          const timelineHandleButtons = Array.from(document.querySelectorAll('#nation-operations-timeline [data-action="open-operation-group"]'))
            .map(node => normalizeText(node.textContent || ''))
            .filter(Boolean);
          const nationDetailLabels = Array.from(document.querySelectorAll('#nation-detail-panel .intel-label'))
            .map(node => normalizeText(node.textContent || ''))
            .filter(Boolean);
          const recentLogLabel = window.strategicMap && typeof window.strategicMap.t === 'function'
            ? normalizeText(window.strategicMap.t('nationDetailRecentLog'))
            : '';
          const recentLogBlock = Array.from(document.querySelectorAll('#nation-detail-panel .nation-detail-block'))
            .find(node => normalizeText(node.querySelector('.intel-label')?.textContent || '') === recentLogLabel)
            || null;
          const recentEventItems = Array.from(recentLogBlock?.querySelectorAll('.nation-timeline-item[data-event-type]') || []);
          const recentEventTexts = recentEventItems
            .map(node => normalizeText(node.textContent || ''))
            .filter(Boolean);
          const priorityItems = Array.from(document.querySelectorAll('#nation-detail-priority-list .nation-priority-item'));
          const priorityItemTexts = priorityItems
            .map(node => normalizeText(node.textContent || ''))
            .filter(Boolean);
          const firstPriorityResourceId = String(priorityItems[0]?.dataset.resourceId || '');
          const priorityFilters = Array.from(document.querySelectorAll('#nation-detail-priority-filters [data-priority-filter]'));
          const officerAuthorizationBlock = document.querySelector('#nation-detail-panel [data-officer-authorization="true"]');
          const officerAuthorizationNodes = Array.from(document.querySelectorAll('#nation-detail-panel [data-officer-authorization-key]'));
          const officerAuthorizationText = normalizeText(officerAuthorizationBlock?.textContent || '');
          const officerAuthorizationRoles = Array.from(new Set(officerAuthorizationNodes
            .flatMap(node => String(node.dataset.officerAuthorizationRoles || '').split(','))
            .map(role => role.trim().toLowerCase())
            .filter(Boolean)));
          const officerAuthorizationStatuses = officerAuthorizationNodes
            .map(node => String(node.dataset.officerAuthorizationStatus || '').trim().toLowerCase())
            .filter(Boolean);
          const officerAuthorizationCanCount = officerAuthorizationNodes
            .filter(node => String(node.dataset.officerAuthorizationCan || '').trim().toLowerCase() === 'true')
            .length;
          const officerAuthorizationAccessReady = officerAuthorizationCanCount >= 9
            && officerAuthorizationStatuses.length >= 9
            && officerAuthorizationStatuses.every(status => status === 'founder');
          const officerAuthorizationReady = Boolean(officerAuthorizationBlock)
            && officerAuthorizationNodes.length >= 9
            && ['marshal', 'treasurer', 'diplomat', 'steward'].every(role => officerAuthorizationRoles.includes(role))
            && officerAuthorizationAccessReady;
          const nationPanelReady = Boolean(nationMatch)
            && Boolean(nationDetailPanel)
            && nationDetailPanel.hidden === false
            && nationDetailText.includes(${JSON.stringify(viewerNation)})
            && (!${JSON.stringify(viewerFounder)} || nationDetailText.includes(${JSON.stringify(viewerFounder)}))
            && (!${JSON.stringify(viewerGovernment)} || nationDetailText.includes(${JSON.stringify(viewerGovernment)}))
            && (!${JSON.stringify(viewerClaims)} || nationDetailText.includes(${JSON.stringify(viewerClaims)}))
            && (!${JSON.stringify(viewerCityStates)} || nationDetailText.includes(${JSON.stringify(viewerCityStates)}))
            && (!${JSON.stringify(viewerResources)} || nationDetailText.includes(${JSON.stringify(viewerResources)}))
            && (!${JSON.stringify(viewerProgress)} || nationDetailText.includes(${JSON.stringify(viewerProgress)}))
            && (!${JSON.stringify(viewerNextLevel)} || nationDetailText.includes(${JSON.stringify(viewerNextLevel)}))
            && operationsSummaryText.length > 0
            && operationsFocusText.length > 0
            && operationsForecastText.length > 0
            && timelineItems.length >= 1
            && timelineHandleButtons.length >= 1
            && (!recentLogLabel || nationDetailLabels.includes(recentLogLabel))
            && recentEventItems.length >= 1
            && recentEventTexts.length >= 1
            && operationsMetricLabels.length >= 6
            && operationGroupButtons.length >= 1
            && recommendationButtons.length >= 1
            && recommendationSpecificButtons.length >= 1
            && operationsFocusActions.length >= 2
            && priorityFilters.length >= 4
            && priorityItems.length >= 1
            && Boolean(firstPriorityResourceId)
            && officerAuthorizationReady
            && nationDetailActions.length >= 1;
          const selectedResource = window.strategicMap && typeof window.strategicMap.selectedResource === 'function'
            ? window.strategicMap.selectedResource()
            : null;
          const resourceMeta = (((selectedResource || {}).data || {}).metadata || {});
          const resourceCost = formatMoney(resourceMeta.migrationCost || '0.00');
          const resourceViewerBalance = formatMoney(resourceMeta.viewerBalance || ${JSON.stringify(viewerBalance)});
          const resourceGovernment = ${JSON.stringify(viewerGovernment)};
          const resourceRole = ${JSON.stringify(viewerRole)};
          const resourceFounder = ${JSON.stringify(viewerFounder)};
          const resourceClaims = ${JSON.stringify(viewerClaims)};
          const resourceCityStates = ${JSON.stringify(viewerCityStates)};
          const resourceResources = ${JSON.stringify(viewerResources)};
          const resourceProgress = ${JSON.stringify(viewerProgress)};
          const resourceNextLevel = ${JSON.stringify(viewerNextLevel)};
          const resourceOnline = ${JSON.stringify(viewerOnline)};
          const resourceActionState = String(resourceMeta.migrationActionState || '');
          const resourceShortfall = formatMoney(resourceMeta.migrationBalanceShortfall || '0.00');
          const resourceExplanationNodes = Array.from(document.querySelectorAll('#nation-detail-panel [data-resource-explanation-state]'));
          const resourceExplanationStates = resourceExplanationNodes.map(node => String(node.dataset.resourceExplanationState || '').trim()).filter(Boolean);
          const resourceExplanationText = normalizeText(resourceExplanationNodes.map(node => node.textContent || '').join(' '));
          const expectedExplanationState = String(resourceMeta.migrationExplanationState || resourceMeta.migrationActionState || '').trim();
          const expectedExplanationReason = normalizeText(resourceMeta.migrationExplanationPrimaryReason || resourceMeta.migrationRestrictionDetail || resourceMeta.migrationNextStep || '');
          const resourceExplanationReady = resourceExplanationNodes.length >= 1
            && (!expectedExplanationState || resourceExplanationStates.includes(expectedExplanationState))
            && (!expectedExplanationReason || resourceExplanationText.includes(expectedExplanationReason) || resourceExplanationText.includes(resourceShortfall));
          const resourceMetaNodePresent = Boolean(document.querySelector('#resource-command-meta'));
          const resourceDetailsNodePresent = Boolean(document.querySelector('#resource-command-details'));
          const resourceStatusNodePresent = Boolean(document.querySelector('#resource-command-status'));
          const resourceCommandPanelPresent = Boolean(document.querySelector('#resource-command-panel'));
          const resourceCommandActionButtons = Array.from(document.querySelectorAll('[data-action="open-resource-command"], #resource-command-panel .resource-command-actions button'))
            .map(node => normalizeText(node.textContent || ''))
            .filter(Boolean);
          const resourcePanelSurfacePresent = resourceMetaNodePresent || resourceDetailsNodePresent || resourceStatusNodePresent;
          const resourceListText = normalizeText(document.querySelector('#resource-list')?.textContent || '');
          const resourceDetailText = normalizeText(document.querySelector('#nation-detail-panel')?.textContent || '');
          const resourceReadOnlyReady = !selectedResource
            ? resourceItems.length >= 1
            : resourceItems.length >= 1
              && resourceActionState.length > 0
              && resourceDetailText.includes(resourceShortfall)
              && resourceExplanationReady;
          const commandUiRemoved = !resourceCommandPanelPresent
            && !resourcePanelSurfacePresent
            && resourceCommandActionButtons.length === 0;
          html = document.documentElement ? document.documentElement.outerHTML : '';
          const readyText = bodyText + ' ' + intelText;
          const ready = readyText.includes(${JSON.stringify(viewerName)})
            && readyText.includes(${JSON.stringify(viewerNation)})
            && readyText.includes(${JSON.stringify(viewerBalance)})
            && (!${JSON.stringify(viewerFounder)} || readyText.includes(${JSON.stringify(viewerFounder)}))
            && (!${JSON.stringify(viewerCityStates)} || readyText.includes(${JSON.stringify(viewerCityStates)}))
            && (!${JSON.stringify(viewerProgress)} || readyText.includes(${JSON.stringify(viewerProgress)}))
            && (!${JSON.stringify(viewerNextLevel)} || readyText.includes(${JSON.stringify(viewerNextLevel)}))
            && balance === ${JSON.stringify(viewerBalance)}
            && nationPanelReady
            && resourceReadOnlyReady
            && commandUiRemoved;
          return {
            ready,
            text: bodyText,
            intelText,
            html,
            balance,
            nationItemCount: nationItems.length,
            nationName: String((nationMatch || {}).name || ''),
            nationDetailText,
            nationDetailActions,
            operationsSummaryText,
            operationsFocusText,
            operationsFocusActions,
            operationsForecastText,
            nationDetailLabels,
            timelineCount: timelineItems.length,
            timelineTexts,
            timelineHandleButtons,
            recentLogLabel,
            recentEventCount: recentEventItems.length,
            recentEventTexts,
            operationsMetricLabels,
            operationGroupButtons,
            recommendationButtons,
            recommendationSpecificButtons,
            priorityFilterCount: priorityFilters.length,
            priorityItemCount: priorityItems.length,
            priorityItemTexts,
            firstPriorityResourceId,
            officerAuthorizationReady,
            officerAuthorizationCount: officerAuthorizationNodes.length,
            officerAuthorizationRoles,
            officerAuthorizationStatuses,
            officerAuthorizationCanCount,
            officerAuthorizationAccessReady,
            officerAuthorizationText,
            nationPanelReady,
            resourceItemCount: resourceItems.length,
            resourceLabel: String((selectedResource || {}).label || ''),
            resourceCost,
            resourceViewerBalance,
            resourceShortfall,
            resourceExplanationCount: resourceExplanationNodes.length,
            resourceExplanationStates,
            resourceExplanationText,
            resourceExplanationReady,
            resourceExplanationState: expectedExplanationState,
            resourceExplanationReason: expectedExplanationReason,
            resourceListText,
            resourceDetailText,
            resourceActionState,
            resourceGovernment,
            resourceRole,
            resourceFounder,
            resourceClaims,
            resourceCityStates,
            resourceResources,
            resourceProgress,
            resourceNextLevel,
            resourceOnline,
            resourceReadOnlyReady,
            commandUiRemoved,
            resourceMetaNodePresent,
            resourceDetailsNodePresent,
            resourceStatusNodePresent,
            resourceCommandPanelPresent,
            resourceCommandActionButtons,
            url: location.href,
            errors: window.__starcoreSmokeErrors || [],
          };
        })()`,
        returnByValue: true,
      });
      if (evaluation.exceptionDetails) {
        throw new Error(`Browser smoke evaluation exception: ${evaluation.exceptionDetails.text || 'unknown'} at line ${evaluation.exceptionDetails.lineNumber ?? '<unknown>'}, column ${evaluation.exceptionDetails.columnNumber ?? '<unknown>'}`);
      }
      state = evaluation.result?.value || null;
      if (state?.ready) {
        break;
      }
      await delay(500);
    }
    if (!state) {
      throw new Error('Browser smoke could not evaluate page state.');
    }
    await writeFile(domFile, state.html || '', 'utf8');
    if (!state.ready) {
      const textSnippet = String(state.text || '').replace(/\s+/g, ' ').slice(0, 800);
      const intelSnippet = String(state.intelText || '').replace(/\s+/g, ' ').slice(0, 400);
      const nationSnippet = [
        `nationItems=${state.nationItemCount || 0}`,
        `nationName=${state.nationName || '<none>'}`,
        `nationPanelReady=${String(state.nationPanelReady)}`,
        `nationLabels=${(state.nationDetailLabels || []).join('|') || '<none>'}`,
        `operationsLabels=${(state.operationsMetricLabels || []).join('|') || '<none>'}`,
        `operationGroups=${(state.operationGroupButtons || []).join('|') || '<none>'}`,
        `recommendations=${(state.recommendationButtons || []).join('|') || '<none>'}`,
        `recommendationSpecific=${(state.recommendationSpecificButtons || []).join('|') || '<none>'}`,
        `operationsSummary=${String(state.operationsSummaryText || '').slice(0, 220)}`,
        `operationsFocus=${String(state.operationsFocusText || '').slice(0, 220)}`,
        `operationsFocusActions=${(state.operationsFocusActions || []).join('|') || '<none>'}`,
        `operationsForecast=${String(state.operationsForecastText || '').slice(0, 220)}`,
        `timelineCount=${state.timelineCount || 0}`,
        `timeline=${String((state.timelineTexts || []).join(' | ')).slice(0, 260)}`,
        `timelineHandles=${(state.timelineHandleButtons || []).join('|') || '<none>'}`,
        `recentLogLabel=${state.recentLogLabel || '<none>'}`,
        `recentEventCount=${state.recentEventCount || 0}`,
        `recentEvents=${String((state.recentEventTexts || []).join(' | ')).slice(0, 260)}`,
        `nationActions=${(state.nationDetailActions || []).join('|') || '<none>'}`,
        `priorityFilterCount=${state.priorityFilterCount || 0}`,
        `priorityCount=${state.priorityItemCount || 0}`,
        `priorityFirst=${state.firstPriorityResourceId || '<none>'}`,
        `priorityDetail=${String((state.priorityItemTexts || []).join(' | ')).slice(0, 300)}`,
        `officerAuthReady=${String(state.officerAuthorizationReady)}`,
        `officerAuthCount=${state.officerAuthorizationCount || 0}`,
        `officerAuthRoles=${(state.officerAuthorizationRoles || []).join('|') || '<none>'}`,
        `officerAccessReady=${String(state.officerAuthorizationAccessReady)}`,
        `officerAccessCan=${state.officerAuthorizationCanCount || 0}`,
        `officerAccessStatuses=${(state.officerAuthorizationStatuses || []).join('|') || '<none>'}`,
        `officerAuth=${String(state.officerAuthorizationText || '').slice(0, 260)}`,
        `nationDetail=${String(state.nationDetailText || '').slice(0, 300)}`
      ].join('; ');
      const resourceSnippet = [
        `resourceItems=${state.resourceItemCount || 0}`,
        `resourceLabel=${state.resourceLabel || '<none>'}`,
        `resourceCost=${state.resourceCost || '<empty>'}`,
        `resourceViewerBalance=${state.resourceViewerBalance || '<empty>'}`,
        `resourceShortfall=${state.resourceShortfall || '<empty>'}`,
        `resourceActionState=${state.resourceActionState || '<empty>'}`,
        `resourceExplanationCount=${state.resourceExplanationCount || 0}`,
        `resourceExplanationStates=${(state.resourceExplanationStates || []).join('|') || '<none>'}`,
        `resourceExplanationReady=${String(state.resourceExplanationReady)}`,
        `resourceExplanationReason=${state.resourceExplanationReason || '<empty>'}`,
        `resourceCommandPanelPresent=${String(state.resourceCommandPanelPresent)}`,
        `resourceCommandActions=${(state.resourceCommandActionButtons || []).join('|') || '<none>'}`,
        `resourceGovernment=${state.resourceGovernment || '<empty>'}`,
        `resourceRole=${state.resourceRole || '<empty>'}`,
        `resourceFounder=${state.resourceFounder || '<empty>'}`,
        `resourceClaims=${state.resourceClaims || '<empty>'}`,
        `resourceCityStates=${state.resourceCityStates || '<empty>'}`,
        `resourceResources=${state.resourceResources || '<empty>'}`,
        `resourceProgress=${state.resourceProgress || '<empty>'}`,
        `resourceNextLevel=${state.resourceNextLevel || '<empty>'}`,
        `resourceOnline=${state.resourceOnline || '<empty>'}`,
        `resourceReadOnlyReady=${String(state.resourceReadOnlyReady)}`,
        `commandUiRemoved=${String(state.commandUiRemoved)}`,
        `resourceMetaNodePresent=${String(state.resourceMetaNodePresent)}`,
        `resourceStatusNodePresent=${String(state.resourceStatusNodePresent)}`,
        `resourceDetailsNodePresent=${String(state.resourceDetailsNodePresent)}`,
        `resourceExplanation=${String(state.resourceExplanationText || '').slice(0, 260)}`,
        `resourceList=${String(state.resourceListText || '').slice(0, 300)}`,
        `resourceDetail=${String(state.resourceDetailText || '').slice(0, 300)}`
      ].join('; ');
      throw new Error(`Map page did not render expected viewer/nation/resource state. balance=${state.balance || '<empty>'}; errors=${JSON.stringify(state.errors || [])}; intel=${intelSnippet}; nation=${nationSnippet}; resource=${resourceSnippet}; text=${textSnippet}`);
    }
    const nationActionEvaluation = await client.send('Runtime.evaluate', {
      expression: `(() => {
        const map = window.strategicMap;
        if (!map || typeof map.updateNationDetailPanel !== 'function') {
          return { ok: false, reason: 'missing-nation-detail-surface' };
        }
        const focusNation = document.querySelector('#nation-detail-panel [data-action="focus-nation"]');
        if (!focusNation) {
          return { ok: false, reason: 'missing-focus-nation-action' };
        }
        map.selectedResourceId = '';
        map.updateNationDetailPanel();
        const selectResource = document.querySelector('#nation-detail-panel [data-action="select-resource"]');
        if (!selectResource) {
          return { ok: false, reason: 'missing-select-resource-action' };
        }
        const targetResourceId = String(selectResource.dataset.resourceId || '');
        selectResource.click();
        const detailSelectOk = Boolean(targetResourceId) && String(map.selectedResourceId || '') === targetResourceId;
        const priorityButton = document.querySelector('#nation-detail-priority-list .nation-priority-item .nation-detail-action[data-action="select-resource"]');
        if (!priorityButton) {
          return {
            ok: false,
            reason: 'missing-priority-select-action',
            targetResourceId,
            selectedResourceId: String(map.selectedResourceId || ''),
          };
        }
        const priorityTargetResourceId = String(priorityButton.dataset.resourceId || '');
        priorityButton.click();
        const prioritySelectOk = Boolean(priorityTargetResourceId) && String(map.selectedResourceId || '') === priorityTargetResourceId;
        const readyFilter = document.querySelector('#nation-detail-priority-filters [data-priority-filter="ready"]');
        const allFilter = document.querySelector('#nation-detail-priority-filters [data-priority-filter="all"]');
        if (!readyFilter || !allFilter) {
          return {
            ok: false,
            reason: 'missing-priority-filters',
            targetResourceId,
            priorityTargetResourceId,
            selectedResourceId: String(map.selectedResourceId || ''),
          };
        }
        readyFilter.click();
        const afterReadyFilter = Array.from(document.querySelectorAll('#nation-detail-priority-list .nation-priority-item'));
        const readyFilterEmpty = document.querySelector('#nation-detail-priority-list .nation-priority-empty');
        const activeReadyFilter = document.querySelector('#nation-detail-priority-filters [data-priority-filter="ready"]');
        const readyHighlightedMarkers = Array.from(document.querySelectorAll('.resource-marker.is-priority-match'));
        const readyFilterOk = map.nationPriorityFilter === 'ready'
          && (afterReadyFilter.length > 0 || Boolean(readyFilterEmpty))
          && Boolean(activeReadyFilter)
          && activeReadyFilter.classList.contains('is-active');
        const fallbackFilter = document.querySelector('#nation-detail-priority-filters [data-priority-filter="waiting-depletion"]')
          || document.querySelector('#nation-detail-priority-filters [data-priority-filter="awaiting-target"]')
          || document.querySelector('#nation-detail-priority-filters [data-priority-filter="insufficient-balance"]');
        const offlineFilter = document.querySelector('#nation-detail-priority-filters [data-priority-filter="player-offline"]');
        let markerHighlightOk = readyHighlightedMarkers.length > 0;
        let fallbackFilterValue = '';
        let fallbackHighlightCount = 0;
        if (!markerHighlightOk && (offlineFilter || fallbackFilter)) {
          const effectiveFallbackFilter = offlineFilter || fallbackFilter;
          fallbackFilterValue = String(effectiveFallbackFilter.dataset.priorityFilter || '');
          effectiveFallbackFilter.click();
          fallbackHighlightCount = Array.from(document.querySelectorAll('.resource-marker.is-priority-match')).length;
          markerHighlightOk = fallbackHighlightCount > 0;
        }
        allFilter.click();
        const afterAllFilter = Array.from(document.querySelectorAll('#nation-detail-priority-list .nation-priority-item'));
        const activeAllFilter = document.querySelector('#nation-detail-priority-filters [data-priority-filter="all"]');
        const allFilterOk = map.nationPriorityFilter === 'all'
          && afterAllFilter.length >= 1
          && Boolean(activeAllFilter)
          && activeAllFilter.classList.contains('is-active');
        const operationGroupButton = document.querySelector('#nation-detail-panel [data-action="open-operation-group"]');
        const operationGroupFilter = String(operationGroupButton?.dataset.priorityFilter || '');
        const operationGroupResourceId = String(operationGroupButton?.dataset.resourceId || '');
        let operationGroupOk = false;
        let operationGroupActive = false;
        if (operationGroupButton && operationGroupFilter) {
          operationGroupButton.click();
          const refreshedGroupButton = document.querySelector(\`#nation-detail-panel [data-action="open-operation-group"][data-priority-filter="\${operationGroupFilter}"]\`);
          const refreshedGroupResourceId = String(refreshedGroupButton?.dataset.resourceId || operationGroupResourceId);
          const activeGroup = document.querySelector(\`.nation-operation-group[data-operation-group="\${operationGroupFilter}"]\`);
          const activeOperationFilter = document.querySelector(\`#nation-detail-priority-filters [data-priority-filter="\${operationGroupFilter}"]\`);
          operationGroupActive = Boolean(activeGroup) && activeGroup.classList.contains('is-active');
          operationGroupOk = map.nationPriorityFilter === operationGroupFilter
            && String(map.selectedResourceId || '') === refreshedGroupResourceId
            && Boolean(activeOperationFilter)
            && activeOperationFilter.classList.contains('is-active');
        }
        const commandUiRemoved = !document.querySelector('#resource-command-panel')
          && document.querySelectorAll('#nation-detail-panel [data-action="open-resource-command"]').length === 0;
        const timelineHandleButton = document.querySelector('#nation-operations-timeline [data-action="open-operation-group"]');
        const timelineHandleFilter = String(timelineHandleButton?.dataset.priorityFilter || '');
        const timelineHandleResourceId = String(timelineHandleButton?.dataset.resourceId || '');
        let timelineHandleOk = false;
        if (timelineHandleButton && timelineHandleFilter) {
          timelineHandleButton.click();
          const activeTimelineFilter = document.querySelector(\`#nation-detail-priority-filters [data-priority-filter="\${timelineHandleFilter}"]\`);
          timelineHandleOk = map.nationPriorityFilter === timelineHandleFilter
            && String(map.selectedResourceId || '') === timelineHandleResourceId
            && Boolean(activeTimelineFilter)
            && activeTimelineFilter.classList.contains('is-active');
        }
        const recentLogInspectButton = document.querySelector('#nation-detail-panel .nation-timeline-item[data-event-type] [data-action="select-resource"]');
        const recentLogFocusButton = document.querySelector('#nation-detail-panel .nation-timeline-item[data-event-type] [data-action="focus-resource"]');
        let recentLogInspectOk = false;
        let recentLogFocusOk = false;
        let recentLogResourceId = '';
        if (recentLogInspectButton && recentLogFocusButton) {
          recentLogResourceId = String(recentLogInspectButton.dataset.resourceId || '');
          recentLogInspectButton.click();
          recentLogInspectOk = Boolean(recentLogResourceId) && String(map.selectedResourceId || '') === recentLogResourceId;
          recentLogFocusButton.click();
          recentLogFocusOk = Boolean(recentLogResourceId) && String(map.selectedResourceId || '') === recentLogResourceId;
        }
        const recentLogFilterButtons = Array.from(document.querySelectorAll('#nation-recent-log-filters [data-recent-log-filter]'));
        const recentLogAllFilter = recentLogFilterButtons.find(button => String(button.dataset.recentLogFilter || '') === 'all');
        const recentLogCategoryFilter = recentLogFilterButtons.find(button => String(button.dataset.recentLogFilter || '') && String(button.dataset.recentLogFilter || '') !== 'all');
        let recentLogFilterOk = recentLogFilterButtons.length >= 2 && Boolean(recentLogAllFilter) && Boolean(recentLogCategoryFilter);
        let recentLogFilterValue = '';
        let recentLogFilterCount = 0;
        let recentLogFilterTotal = 0;
        if (recentLogFilterOk) {
          recentLogFilterValue = String(recentLogCategoryFilter.dataset.recentLogFilter || '');
          recentLogCategoryFilter.click();
          const recentLogList = document.querySelector('#nation-recent-log');
          const filteredItems = Array.from(document.querySelectorAll('#nation-recent-log .nation-timeline-item[data-event-category]'));
          recentLogFilterCount = filteredItems.length;
          recentLogFilterTotal = Number(recentLogList?.dataset.recentLogTotal || 0);
          recentLogFilterOk = String(map.nationRecentLogFilter || '') === recentLogFilterValue
            && String(recentLogList?.dataset.recentLogFilter || '') === recentLogFilterValue
            && recentLogFilterTotal > filteredItems.length
            && filteredItems.length >= 1
            && filteredItems.every(item => String(item.dataset.eventCategory || '') === recentLogFilterValue);
          const refreshedAllFilter = document.querySelector('#nation-recent-log-filters [data-recent-log-filter="all"]');
          if (refreshedAllFilter) {
            refreshedAllFilter.click();
            recentLogFilterOk = recentLogFilterOk
              && String(map.nationRecentLogFilter || '') === 'all'
              && String(document.querySelector('#nation-recent-log')?.dataset.recentLogFilter || '') === 'all';
          } else {
            recentLogFilterOk = false;
          }
        }
        allFilter.click();
        const explanationNodes = Array.from(document.querySelectorAll('#nation-detail-panel [data-resource-explanation-state]'));
        const explanationStates = explanationNodes.map(node => String(node.dataset.resourceExplanationState || '').trim()).filter(Boolean);
        const explanationText = explanationNodes.map(node => String(node.textContent || '').replace(/\s+/g, ' ').trim()).filter(Boolean).join(' | ');
        const explanationOk = explanationNodes.length >= 1 && explanationStates.length >= 1 && explanationText.length > 0;
        return {
          ok: Boolean(targetResourceId)
            && Boolean(priorityTargetResourceId)
            && detailSelectOk
            && prioritySelectOk
            && readyFilterOk
            && markerHighlightOk
            && allFilterOk
            && operationGroupOk
            && commandUiRemoved
            && timelineHandleOk
            && recentLogInspectOk
            && recentLogFocusOk
            && recentLogFilterOk
            && explanationOk,
          reason: Boolean(targetResourceId)
            && Boolean(priorityTargetResourceId)
            && detailSelectOk
            && prioritySelectOk
            && readyFilterOk
            && markerHighlightOk
            && allFilterOk
            && operationGroupOk
            && commandUiRemoved
            && timelineHandleOk
            && recentLogInspectOk
            && recentLogFocusOk
            && recentLogFilterOk
            && explanationOk
            ? ''
            : 'select-resource-action-did-not-update-selection',
          targetResourceId,
          priorityTargetResourceId,
          selectedResourceId: String(map.selectedResourceId || ''),
          detailSelectOk,
          prioritySelectOk,
          readyFilterOk,
          markerHighlightOk,
          readyHighlightedMarkers: readyHighlightedMarkers.length,
          fallbackFilterValue,
          fallbackHighlightCount,
          allFilterOk,
          operationGroupFilter,
          operationGroupResourceId,
          operationGroupOk,
          operationGroupActive,
          commandUiRemoved,
          timelineHandleFilter,
          timelineHandleResourceId,
          timelineHandleOk,
          recentLogResourceId,
          recentLogInspectOk,
          recentLogFocusOk,
          recentLogFilterOk,
          recentLogFilterValue,
          recentLogFilterCount,
          recentLogFilterTotal,
          recentLogFilterButtons: recentLogFilterButtons.length,
          explanationOk,
          explanationCount: explanationNodes.length,
          explanationStates,
          explanationText,
          readyFilterCount: afterReadyFilter.length,
          readyFilterEmpty: Boolean(readyFilterEmpty),
          allFilterCount: afterAllFilter.length,
        };
      })()`,
      returnByValue: true,
    });
    const nationActionResult = nationActionEvaluation.result?.value || null;
    if (!nationActionResult?.ok) {
      throw new Error(`Nation detail quick action flow failed. reason=${nationActionResult?.reason || 'unknown'}; target=${nationActionResult?.targetResourceId || '<empty>'}; priorityTarget=${nationActionResult?.priorityTargetResourceId || '<empty>'}; selected=${nationActionResult?.selectedResourceId || '<empty>'}; detailSelectOk=${String(nationActionResult?.detailSelectOk)}; prioritySelectOk=${String(nationActionResult?.prioritySelectOk)}; readyFilterOk=${String(nationActionResult?.readyFilterOk)}; markerHighlightOk=${String(nationActionResult?.markerHighlightOk)}; readyHighlightedMarkers=${nationActionResult?.readyHighlightedMarkers ?? '<empty>'}; fallbackFilterValue=${nationActionResult?.fallbackFilterValue || '<empty>'}; fallbackHighlightCount=${nationActionResult?.fallbackHighlightCount ?? '<empty>'}; allFilterOk=${String(nationActionResult?.allFilterOk)}; operationGroupFilter=${nationActionResult?.operationGroupFilter || '<empty>'}; operationGroupResourceId=${nationActionResult?.operationGroupResourceId || '<empty>'}; operationGroupOk=${String(nationActionResult?.operationGroupOk)}; operationGroupActive=${String(nationActionResult?.operationGroupActive)}; commandUiRemoved=${String(nationActionResult?.commandUiRemoved)}; timelineHandleFilter=${nationActionResult?.timelineHandleFilter || '<empty>'}; timelineHandleResourceId=${nationActionResult?.timelineHandleResourceId || '<empty>'}; timelineHandleOk=${String(nationActionResult?.timelineHandleOk)}; recentLogResourceId=${nationActionResult?.recentLogResourceId || '<empty>'}; recentLogInspectOk=${String(nationActionResult?.recentLogInspectOk)}; recentLogFocusOk=${String(nationActionResult?.recentLogFocusOk)}; recentLogFilterOk=${String(nationActionResult?.recentLogFilterOk)}; recentLogFilterValue=${nationActionResult?.recentLogFilterValue || '<empty>'}; recentLogFilterCount=${nationActionResult?.recentLogFilterCount ?? '<empty>'}; recentLogFilterTotal=${nationActionResult?.recentLogFilterTotal ?? '<empty>'}; recentLogFilterButtons=${nationActionResult?.recentLogFilterButtons ?? '<empty>'}; explanationOk=${String(nationActionResult?.explanationOk)}; explanationCount=${nationActionResult?.explanationCount ?? '<empty>'}; explanationStates=${(nationActionResult?.explanationStates || []).join('|') || '<none>'}; explanationText=${String(nationActionResult?.explanationText || '').slice(0, 260)}; readyFilterCount=${nationActionResult?.readyFilterCount ?? '<empty>'}; readyFilterEmpty=${String(nationActionResult?.readyFilterEmpty)}; allFilterCount=${nationActionResult?.allFilterCount ?? '<empty>'}`);
    }
    const eventQueryEvaluation = await client.send('Runtime.evaluate', {
      expression: `(() => {
        const map = window.strategicMap;
        if (!map || typeof map.buildApiUrl !== 'function') {
          return { ok: false, reason: 'missing-event-api-surface' };
        }
        const nationId = String(map.selectedNationId || '');
        const resourceId = ${JSON.stringify(nationActionResult?.recentLogResourceId || '')};
        if (!nationId) {
          return { ok: false, reason: 'missing-selected-nation' };
        }
        if (!resourceId) {
          return { ok: false, reason: 'missing-resource-filter' };
        }
        const params = new URLSearchParams();
        params.set('nation', nationId);
        params.set('filter', 'resource');
        params.set('resourceId', resourceId);
        params.set('page', '1');
        params.set('size', '20');
        return fetch(map.buildApiUrl((window.MapConfig?.eventLogUrl || './api/map/events') + '?' + params.toString()), { cache: 'no-store' })
          .then(async (resp) => {
            const text = await resp.text();
            let data = null;
            try {
              data = JSON.parse(text);
            } catch {
              data = null;
            }
            const events = Array.isArray(data?.events) ? data.events : [];
            const ok = Boolean(resp.ok)
              && Boolean(data)
              && data.ok !== false
              && String(data.filter || '') === 'resource'
              && String(data.resourceId || '') === resourceId
              && Number(data.total || 0) >= 1
              && events.length >= 1
              && events.every(event => String(event.category || '') === 'resource')
              && events.every(event => String(event.resourceId || '') === resourceId);
            return {
              ok,
              reason: ok ? '' : (resp.ok ? 'event-query-mismatch' : 'event-query-http'),
              status: resp.status,
              nationId,
              queryNationId: String(data?.nationId || ''),
              filter: String(data?.filter || ''),
              resourceId,
              total: Number(data?.total || 0),
              count: events.length,
              firstType: String(events[0]?.type || ''),
              firstMessage: String(events[0]?.message || ''),
              raw: String(text || '').slice(0, 260),
            };
          })
          .catch(error => ({
            ok: false,
            reason: error?.message || 'event-query-fetch-failed',
            status: 0,
            nationId,
            queryNationId: '',
            filter: '',
            resourceId,
            total: 0,
            count: 0,
            firstType: '',
            firstMessage: '',
            raw: '',
          }));
      })()`,
      awaitPromise: true,
      returnByValue: true,
    });
    const eventQueryResult = eventQueryEvaluation.result?.value || null;
    if (!eventQueryResult?.ok) {
      throw new Error(`Nation event query failed. reason=${eventQueryResult?.reason || 'unknown'}; status=${eventQueryResult?.status ?? '<empty>'}; nationId=${eventQueryResult?.nationId || '<empty>'}; queryNationId=${eventQueryResult?.queryNationId || '<empty>'}; filter=${eventQueryResult?.filter || '<empty>'}; resourceId=${eventQueryResult?.resourceId || '<empty>'}; total=${eventQueryResult?.total ?? '<empty>'}; count=${eventQueryResult?.count ?? '<empty>'}; firstType=${eventQueryResult?.firstType || '<empty>'}; firstMessage=${String(eventQueryResult?.firstMessage || '').slice(0, 160)}; raw=${String(eventQueryResult?.raw || '').slice(0, 260)}`);
    }
    const eventLedgerEvaluation = await client.send('Runtime.evaluate', {
      expression: `(async () => {
        const delay = (ms) => new Promise(resolve => setTimeout(resolve, ms));
        const normalizeText = (value) => String(value || '').replace(/\\s+/g, ' ').trim();
        const normalizeResourceId = (value) => {
          const raw = String(value || '').trim();
          if (!raw) {
            return '';
          }
          return raw.startsWith('resource:') ? raw : 'resource:' + raw;
        };
        const readLedgerState = (expected = {}) => {
          const ledger = document.querySelector('#nation-event-ledger');
          const items = Array.from(document.querySelectorAll('#nation-event-ledger [data-event-ledger-item="true"]'));
          const filter = String(ledger?.dataset.eventLedgerFilter || '');
          const range = String(ledger?.dataset.eventLedgerRange || '');
          const resourceId = String(ledger?.dataset.eventLedgerResourceId || '');
          const query = String(ledger?.dataset.eventLedgerQuery || '');
          const actor = String(ledger?.dataset.eventLedgerActor || '');
          const reasonFilter = String(ledger?.dataset.eventLedgerReason || '');
          const count = Number(ledger?.dataset.eventLedgerCount || '0');
          const total = Number(ledger?.dataset.eventLedgerTotal || '0');
          const page = Number(ledger?.dataset.eventLedgerPage || '0');
          const pages = Number(ledger?.dataset.eventLedgerPages || '0');
          const itemCategories = items.map(item => String(item.dataset.eventCategory || ''));
          const itemResources = items.map(item => String(item.dataset.resourceId || ''));
          const itemActors = items.map(item => String(item.dataset.eventActor || ''));
          const itemReasons = items.map(item => String(item.dataset.eventReason || ''));
          const itemTexts = items.map(item => normalizeText(item.textContent || '')).filter(Boolean);
          const contextChips = Array.from(document.querySelectorAll('#nation-event-ledger [data-event-ledger-context-chip="true"]'))
            .map(chip => ({
              field: String(chip.dataset.searchField || ''),
              key: String(chip.dataset.searchKey || ''),
              value: String(chip.dataset.searchValue || ''),
              group: String(chip.dataset.eventLedgerContextGroup || ''),
              text: normalizeText(chip.textContent || ''),
            }));
          const readonlyFacts = Array.from(document.querySelectorAll('#nation-event-ledger [data-event-ledger-context-readonly="true"]'))
            .map(fact => ({
              key: String(fact.dataset.contextKey || ''),
              value: String(fact.dataset.contextValue || ''),
              group: String(fact.dataset.eventLedgerContextGroup || ''),
              text: normalizeText(fact.textContent || ''),
            }));
          const operationLinks = Array.from(document.querySelectorAll('#nation-event-ledger [data-event-operation-link="true"]'))
            .map(link => ({
              action: String(link.dataset.eventOperationAction || ''),
              eventResourceId: String(link.dataset.eventOperationEventResourceId || ''),
              resourceId: String(link.dataset.eventOperationResourceId || ''),
              match: String(link.dataset.eventOperationMatch || ''),
              state: String(link.dataset.eventOperationState || ''),
              roles: String(link.dataset.eventOperationRoles || ''),
              canOperate: String(link.dataset.eventOperationCan || ''),
              status: String(link.dataset.eventOperationStatus || ''),
              explanationStates: Array.from(link.querySelectorAll('[data-resource-explanation-state]'))
                .map(node => String(node.dataset.resourceExplanationState || '').trim())
                .filter(Boolean),
              groupButtons: Array.from(link.querySelectorAll('[data-action="open-operation-group"]'))
                .map(button => ({
                  filter: String(button.dataset.priorityFilter || ''),
                  resourceId: String(button.dataset.resourceId || ''),
                  action: String(button.dataset.eventOperationAction || ''),
                  text: normalizeText(button.textContent || ''),
                })),
              text: normalizeText(link.textContent || ''),
            }));
          const filterOk = !expected.filter || filter === expected.filter;
          const rangeOk = !expected.range || range === expected.range;
          const resourceOk = !expected.resourceId || resourceId === expected.resourceId;
          const queryOk = !expected.query || query === expected.query;
          const actorOk = !expected.actor || actor === expected.actor;
          const reasonOk = !expected.reason || reasonFilter === expected.reason;
          const categoryOk = !expected.category || (items.length >= 1 && itemCategories.every(category => category === expected.category));
          const itemResourceOk = !expected.itemResourceId || (items.length >= 1 && itemResources.every(id => id === expected.itemResourceId));
          const itemQueryOk = !expected.query || (items.length >= 1 && itemTexts.every(text => text.toLowerCase().includes(String(expected.query).toLowerCase())));
          const itemActorOk = !expected.actor || (items.length >= 1 && itemTexts.every(text => text.toLowerCase().includes(String(expected.actor).toLowerCase())));
          const itemReasonOk = !expected.reason || (items.length >= 1 && itemTexts.every(text => text.toLowerCase().includes(String(expected.reason).toLowerCase())));
          const contextChipOk = !expected.contextChips || contextChips.length >= 1;
          const readonlyFactsOk = !expected.readonlyKeys || expected.readonlyKeys.every(expectedKey => readonlyFacts.some(fact =>
            String(fact.key || '').toLowerCase() === String(expectedKey || '').toLowerCase()
              && String(fact.value || '').trim()
          ));
          const operationLinkOk = !expected.operationLink || operationLinks.some(link =>
            link.action === 'resource-migration'
              && (!expected.itemResourceId
                || normalizeResourceId(link.eventResourceId) === normalizeResourceId(expected.itemResourceId)
                || normalizeResourceId(link.resourceId) === normalizeResourceId(expected.itemResourceId))
              && link.state
              && link.explanationStates.length >= 1
              && link.roles.split(',').map(role => role.trim().toLowerCase()).includes('marshal')
              && link.groupButtons.some(button => button.action === 'resource-migration' && normalizeResourceId(button.resourceId) === normalizeResourceId(link.resourceId))
          );
          const countOk = count === items.length && total >= items.length;
          const pageOk = page >= 1 && pages >= 1 && page <= pages;
          const loading = Boolean(window.strategicMap?.eventLedgerState?.loading);
          return {
            ok: Boolean(ledger)
              && !loading
              && items.length >= 1
              && countOk
              && pageOk
              && filterOk
              && rangeOk
              && resourceOk
              && queryOk
              && actorOk
              && reasonOk
              && categoryOk
              && itemResourceOk
              && itemQueryOk
              && itemActorOk
              && itemReasonOk
              && contextChipOk
              && readonlyFactsOk
              && operationLinkOk,
            reason: !ledger ? 'missing-event-ledger-dom'
              : loading ? 'event-ledger-loading'
              : items.length < 1 ? 'event-ledger-empty'
              : !countOk ? 'event-ledger-count-mismatch'
              : !pageOk ? 'event-ledger-page-mismatch'
              : !filterOk ? 'event-ledger-filter-mismatch'
              : !rangeOk ? 'event-ledger-range-mismatch'
              : !resourceOk ? 'event-ledger-resource-mismatch'
              : !queryOk ? 'event-ledger-query-mismatch'
              : !actorOk ? 'event-ledger-actor-mismatch'
              : !reasonOk ? 'event-ledger-reason-mismatch'
              : !categoryOk ? 'event-ledger-category-mismatch'
              : !itemResourceOk ? 'event-ledger-item-resource-mismatch'
              : !itemQueryOk ? 'event-ledger-item-query-mismatch'
              : !itemActorOk ? 'event-ledger-item-actor-mismatch'
              : !itemReasonOk ? 'event-ledger-item-reason-mismatch'
              : !contextChipOk ? 'event-ledger-context-chip-missing'
              : !readonlyFactsOk ? 'event-ledger-readonly-facts-missing'
              : !operationLinkOk ? 'event-ledger-operation-link-missing'
              : '',
            filter,
            range,
            resourceId,
            query,
            actor,
            reasonFilter,
            count,
            total,
            page,
            pages,
            itemCategories,
            itemResources,
            itemActors,
            itemReasons,
            contextChips,
            readonlyFacts,
            operationLinks,
            firstText: itemTexts[0] || '',
            text: itemTexts.join(' | ').slice(0, 260),
          };
        };
        const waitForLedger = async (expected = {}, timeoutMs = 15000) => {
          const started = Date.now();
          let last = null;
          while (Date.now() - started < timeoutMs) {
            last = readLedgerState(expected);
            if (last.ok) {
              return last;
            }
            await delay(100);
          }
          return last || { ok: false, reason: 'event-ledger-timeout' };
        };
        const map = window.strategicMap;
        if (!map || typeof map.loadEventLedger !== 'function' || typeof map.updateNationDetailPanel !== 'function' || typeof map.eventLedgerExportUrl !== 'function') {
          return { ok: false, reason: 'missing-event-ledger-surface' };
        }
        const nationId = String(map.selectedNationId || '');
        const targetResourceId = normalizeResourceId(${JSON.stringify(nationActionResult?.recentLogResourceId || '')} || map.selectedResourceId || '');
        if (!nationId) {
          return { ok: false, reason: 'missing-selected-nation' };
        }
        if (!targetResourceId) {
          return { ok: false, reason: 'missing-selected-resource' };
        }
        if (typeof map.setSelectedResource === 'function' && String(map.selectedResourceId || '') !== targetResourceId) {
          map.setSelectedResource(targetResourceId);
        }
        map.eventLedgerState = {
          ...(map.eventLedgerState || {}),
          nationId,
          page: 1,
          size: 3,
          filter: 'all',
          range: 'all',
          from: '',
          to: '',
          resourceId: '',
          query: '',
          actor: '',
          reason: '',
          loading: false,
          error: '',
          data: null,
        };
        map.updateNationDetailPanel();
        const openButton = document.querySelector('#nation-detail-panel [data-action="load-event-ledger"]');
        if (!openButton) {
          return { ok: false, reason: 'missing-event-ledger-open-button', nationId, targetResourceId };
        }
        openButton.click();
        const opened = await waitForLedger({ filter: 'all', range: 'all' });
        if (!opened.ok) {
          return { ...opened, ok: false, phase: 'open', nationId, targetResourceId };
        }

        const resourceFilterButton = Array.from(document.querySelectorAll('#nation-detail-panel [data-action="event-ledger-filter"]'))
          .find(button => String(button.dataset.filter || '') === 'resource');
        if (!resourceFilterButton) {
          return { ok: false, reason: 'missing-event-ledger-resource-filter', nationId, targetResourceId };
        }
        resourceFilterButton.click();
        const resourceFiltered = await waitForLedger({ filter: 'resource', range: 'all', category: 'resource' });
        if (!resourceFiltered.ok) {
          return { ...resourceFiltered, ok: false, phase: 'resource-filter', nationId, targetResourceId };
        }

        const resourceButton = Array.from(document.querySelectorAll('#nation-detail-panel [data-action="event-ledger-resource"]'))
          .find(button => normalizeResourceId(button.dataset.resourceId || '') === targetResourceId);
        if (!resourceButton) {
          return {
            ok: false,
            reason: 'missing-event-ledger-current-resource-button',
            nationId,
            targetResourceId,
            availableResources: Array.from(document.querySelectorAll('#nation-detail-panel [data-action="event-ledger-resource"]')).map(button => String(button.dataset.resourceId || '')),
          };
        }
        resourceButton.click();
        const currentResource = await waitForLedger({ filter: 'resource', range: 'all', resourceId: targetResourceId, category: 'resource', itemResourceId: targetResourceId, operationLink: true });
        if (!currentResource.ok) {
          return { ...currentResource, ok: false, phase: 'current-resource', nationId, targetResourceId };
        }
        const operationLink = currentResource.operationLinks.find(link =>
          link.action === 'resource-migration'
            && (normalizeResourceId(link.eventResourceId) === targetResourceId || normalizeResourceId(link.resourceId) === targetResourceId)
        ) || null;
        const operationLinkNode = Array.from(document.querySelectorAll('#nation-event-ledger [data-event-operation-link="true"]'))
          .find(link => normalizeResourceId(link.dataset.eventOperationEventResourceId || link.dataset.eventOperationResourceId || '') === targetResourceId
            || normalizeResourceId(link.dataset.eventOperationResourceId || '') === targetResourceId);
        const operationLinkButton = operationLinkNode
          ? operationLinkNode.querySelector('[data-action="open-operation-group"][data-event-operation-action="resource-migration"]')
          : null;
        let operationLinkGroupOk = false;
        let operationLinkFilter = '';
        let operationLinkTargetResourceId = '';
        if (operationLinkButton) {
          operationLinkFilter = String(operationLinkButton.dataset.priorityFilter || '');
          operationLinkTargetResourceId = normalizeResourceId(operationLinkButton.dataset.resourceId || '');
          operationLinkButton.click();
          const activeOperationFilter = document.querySelector(\`#nation-detail-priority-filters [data-priority-filter="\${operationLinkFilter}"]\`);
          operationLinkGroupOk = Boolean(operationLinkFilter)
            && Boolean(operationLinkTargetResourceId)
            && String(map.nationPriorityFilter || '') === operationLinkFilter
            && normalizeResourceId(map.selectedResourceId || '') === operationLinkTargetResourceId
            && Boolean(activeOperationFilter)
            && activeOperationFilter.classList.contains('is-active');
        }
        if (!operationLink || !operationLinkButton || !operationLinkGroupOk) {
          return {
            ok: false,
            reason: 'event-ledger-operation-link-action-failed',
            phase: 'event-ledger-operation-link',
            nationId,
            targetResourceId,
            operationLink,
            operationLinkButtonPresent: Boolean(operationLinkButton),
            operationLinkFilter,
            operationLinkTargetResourceId,
            operationLinkGroupOk,
            selectedResourceId: String(map.selectedResourceId || ''),
            priorityFilter: String(map.nationPriorityFilter || ''),
          };
        }

        const rangeButton = Array.from(document.querySelectorAll('#nation-detail-panel [data-action="event-ledger-range"]'))
          .find(button => String(button.dataset.range || '') === '24h');
        if (!rangeButton) {
          return { ok: false, reason: 'missing-event-ledger-24h-range', nationId, targetResourceId };
        }
        rangeButton.click();
        const ranged = await waitForLedger({ filter: 'resource', range: '24h', resourceId: targetResourceId, category: 'resource', itemResourceId: targetResourceId, operationLink: true });
        if (!ranged.ok) {
          return { ...ranged, ok: false, phase: '24h-range', nationId, targetResourceId };
        }
        const searchQuery = ranged.firstText.includes('资源') ? '资源' : 'resource';
        const searchInput = document.querySelector('#nation-detail-panel [data-event-ledger-search="query"]');
        const searchButton = document.querySelector('#nation-detail-panel [data-action="event-ledger-search"]');
        if (!searchInput || !searchButton) {
          return { ok: false, reason: 'missing-event-ledger-search-control', nationId, targetResourceId, searchQuery };
        }
        searchInput.value = searchQuery;
        searchButton.click();
        const searched = await waitForLedger({ filter: 'resource', range: '24h', resourceId: targetResourceId, query: searchQuery, category: 'resource', itemResourceId: targetResourceId });
        if (!searched.ok) {
          return { ...searched, ok: false, phase: 'event-ledger-search', nationId, targetResourceId, searchQuery };
        }
        map.eventLedgerState = {
          ...(map.eventLedgerState || {}),
          size: 20,
        };
        const contextFilterCandidates = ['officer', 'war', 'diplomacy', 'strategy', 'finance', 'all'];
        let contextSeed = null;
        let contextSeedExpected = null;
        const contextAttempts = [];
        for (const candidateFilter of contextFilterCandidates) {
          await map.loadEventLedger(nationId, 1, candidateFilter, '24h', '', '', '', '', '', '');
          const expected = { filter: candidateFilter, range: '24h' };
          if (candidateFilter !== 'all') {
            expected.category = candidateFilter;
          }
          const candidate = await waitForLedger(expected);
          contextAttempts.push({
            filter: candidateFilter,
            ok: Boolean(candidate?.ok),
            reason: String(candidate?.reason || ''),
            count: Number(candidate?.count || 0),
            chips: Array.isArray(candidate?.contextChips) ? candidate.contextChips.length : 0,
            firstText: String(candidate?.firstText || '').slice(0, 120),
          });
          if (candidate?.ok && Array.isArray(candidate.contextChips) && candidate.contextChips.length > 0) {
            contextSeed = candidate;
            contextSeedExpected = expected;
            break;
          }
        }
        if (!contextSeed || !contextSeedExpected) {
          return { ok: false, reason: 'event-ledger-context-chip-missing', phase: 'event-ledger-context-seed', nationId, targetResourceId, searchQuery, contextAttempts };
        }
        const requiredReadonlyKeys = ['amount', 'balance'];
        const seedWithReadonlyFacts = readLedgerState({ ...contextSeedExpected, readonlyKeys: requiredReadonlyKeys });
        if (!seedWithReadonlyFacts.ok) {
          return { ...seedWithReadonlyFacts, ok: false, phase: 'event-ledger-readonly-facts', nationId, targetResourceId, searchQuery, requiredReadonlyKeys };
        }
        const contextChipCandidates = () => Array.from(document.querySelectorAll('#nation-event-ledger [data-action="event-ledger-context-search"]'))
          .filter(chip => ['actor', 'reason', 'query'].includes(String(chip.dataset.searchField || '')) && String(chip.dataset.searchValue || '').trim());
        const findActorChip = () => contextChipCandidates().find(chip => {
            const key = String(chip.dataset.searchKey || '').toLowerCase();
            return String(chip.dataset.searchField || '') === 'actor' && ['actor', 'operator', 'player'].includes(key);
          });
        const findReasonChip = () => contextChipCandidates().find(chip => {
            const key = String(chip.dataset.searchKey || '').toLowerCase();
            return String(chip.dataset.searchField || '') === 'reason' && ['reason', 'cause'].includes(key);
          });
        const clickContextChip = async (chip, phase) => {
          if (!chip) {
            return {
              ok: false,
              reason: 'missing-event-ledger-context-chip',
              phase,
              nationId,
              targetResourceId,
              searchQuery,
              availableContextChips: contextChipCandidates().map(candidate => ({
                field: String(candidate.dataset.searchField || ''),
                key: String(candidate.dataset.searchKey || ''),
                value: String(candidate.dataset.searchValue || ''),
              })),
            };
          }
          const field = String(chip.dataset.searchField || 'query');
          const key = String(chip.dataset.searchKey || '');
          const value = String(chip.dataset.searchValue || '');
          const expected = {
            filter: contextSeedExpected.filter,
            range: '24h',
            contextChips: true,
            readonlyKeys: requiredReadonlyKeys,
          };
          if (contextSeedExpected.category) {
            expected.category = contextSeedExpected.category;
          }
          expected[field] = value;
          chip.click();
          const filtered = await waitForLedger(expected);
          if (!filtered.ok) {
            return { ...filtered, ok: false, phase, nationId, targetResourceId, searchQuery, contextField: field, contextKey: key, contextValue: value };
          }
          return {
            ok: true,
            expected,
            filtered,
            field,
            key,
            value,
            count: filtered.count,
            readonlyFacts: filtered.readonlyFacts,
          };
        };
        const actorContext = await clickContextChip(findActorChip(), 'event-ledger-actor-context-chip');
        if (!actorContext.ok) {
          return {
            ...actorContext,
            ok: false,
          };
        }
        const resetContextExpected = {
          filter: contextSeedExpected.filter,
          range: '24h',
          contextChips: true,
          readonlyKeys: requiredReadonlyKeys,
        };
        if (contextSeedExpected.category) {
          resetContextExpected.category = contextSeedExpected.category;
        }
        await map.loadEventLedger(nationId, 1, contextSeedExpected.filter, '24h', '', '', '', '', '', '');
        const resetContext = await waitForLedger(resetContextExpected);
        if (!resetContext.ok) {
          return { ...resetContext, ok: false, phase: 'event-ledger-reset-context-seed', nationId, targetResourceId, searchQuery };
        }
        const reasonContext = await clickContextChip(findReasonChip(), 'event-ledger-reason-context-chip');
        if (!reasonContext.ok) {
          return {
            ...reasonContext,
            ok: false,
          };
        }
        const findContextJump = (field, value) => Array.from(document.querySelectorAll('#nation-detail-panel [data-action="event-ledger-context-jump"]'))
          .find(button => String(button.dataset.searchField || '') === String(field || '')
            && String(button.dataset.searchValue || '') === String(value || ''));
        const jumpButton = findContextJump(reasonContext.field, reasonContext.value);
        if (!jumpButton) {
          return {
            ok: false,
            reason: 'missing-event-ledger-context-jump',
            phase: 'event-ledger-context-jump',
            nationId,
            targetResourceId,
            searchQuery,
            jumpField: reasonContext.field,
            jumpValue: reasonContext.value,
            availableJumps: Array.from(document.querySelectorAll('#nation-detail-panel [data-action="event-ledger-context-jump"]')).map(button => ({
              field: String(button.dataset.searchField || ''),
              value: String(button.dataset.searchValue || ''),
            })),
          };
        }
        const jumpExpected = {
          filter: 'all',
          range: '24h',
          contextChips: true,
          readonlyKeys: requiredReadonlyKeys,
        };
        jumpExpected[reasonContext.field] = reasonContext.value;
        jumpButton.click();
        const jumpedContext = await waitForLedger(jumpExpected);
        if (!jumpedContext.ok) {
          return {
            ...jumpedContext,
            ok: false,
            phase: 'event-ledger-context-jump',
            nationId,
            targetResourceId,
            searchQuery,
            jumpField: reasonContext.field,
            jumpValue: reasonContext.value,
          };
        }
        const contextField = actorContext.field;
        const contextKey = actorContext.key;
        const contextValue = actorContext.value;
        const contextFiltered = actorContext.filtered;
        const contextExpected = jumpExpected;
        const exportContext = reasonContext;

        const captureExportClick = async (format) => {
          const originalOpen = window.open;
          const opened = [];
          window.open = (url, target, features) => {
            opened.push({ url: String(url || ''), target: String(target || ''), features: String(features || '') });
            return null;
          };
          try {
            const button = Array.from(document.querySelectorAll('#nation-detail-panel [data-action="event-ledger-export"]'))
              .find(candidate => String(candidate.dataset.format || '') === format);
            if (!button) {
              return { ok: false, reason: 'missing-event-ledger-export-button', format, url: '' };
            }
            button.click();
            await delay(50);
            const entry = opened[0] || null;
            if (!entry || !entry.url) {
              return { ok: false, reason: 'event-ledger-export-not-opened', format, url: '' };
            }
            const url = new URL(entry.url, window.location.href);
            const params = url.searchParams;
            const ok = url.pathname.endsWith('/api/map/events')
              && params.get('nation') === nationId
              && params.get('format') === format
              && params.get('filter') === contextExpected.filter
              && params.get('range') === contextExpected.range
              && (params.get('resourceId') || '') === (contextExpected.resourceId || '')
              && (params.get('query') || '') === (exportContext.field === 'query' ? exportContext.value : '')
              && (params.get('actor') || '') === (exportContext.field === 'actor' ? exportContext.value : '')
              && (params.get('reason') || '') === (exportContext.field === 'reason' ? exportContext.value : '');
            return {
              ok,
              reason: ok ? '' : 'event-ledger-export-url-mismatch',
              format,
              url: url.href,
              nation: params.get('nation') || '',
              filter: params.get('filter') || '',
              range: params.get('range') || '',
              resourceId: params.get('resourceId') || '',
              query: params.get('query') || '',
              actor: params.get('actor') || '',
              reasonFilter: params.get('reason') || '',
            };
          } finally {
            window.open = originalOpen;
          }
        };
        const csvExport = await captureExportClick('csv');
        if (!csvExport.ok) {
          return { ...csvExport, ok: false, phase: 'event-ledger-export-csv', nationId, targetResourceId };
        }
        const jsonExport = await captureExportClick('json');
        if (!jsonExport.ok) {
          return { ...jsonExport, ok: false, phase: 'event-ledger-export-json', nationId, targetResourceId };
        }

        const resourceSearchExpected = { filter: 'resource', range: '24h', resourceId: targetResourceId, query: searchQuery, category: 'resource', itemResourceId: targetResourceId, operationLink: true };
        map.eventLedgerState = {
          ...(map.eventLedgerState || {}),
          size: 3,
        };
        await map.loadEventLedger(nationId, 1, 'resource', '24h', '', '', targetResourceId, searchQuery, '', '');
        const restoredResourceSearch = await waitForLedger(resourceSearchExpected);
        if (!restoredResourceSearch.ok) {
          return { ...restoredResourceSearch, ok: false, phase: 'event-ledger-restore-resource-search', nationId, targetResourceId, searchQuery, contextField, contextKey, contextValue };
        }
        const pageOne = readLedgerState(resourceSearchExpected);
        const nextButton = document.querySelector('#nation-detail-panel [data-action="event-ledger-next"]');
        const prevButton = document.querySelector('#nation-detail-panel [data-action="event-ledger-prev"]');
        let paginationMode = 'single';
        let nextPage = null;
        let previousPage = null;
        let paginationOk = Boolean(nextButton) && Boolean(prevButton);
        if (paginationOk && pageOne.pages > 1) {
          paginationMode = 'paged';
          paginationOk = !nextButton.disabled && prevButton.disabled;
          if (paginationOk) {
            nextButton.click();
            nextPage = await waitForLedger(resourceSearchExpected);
            paginationOk = nextPage.ok && nextPage.page === 2;
          }
          const refreshedPrevButton = document.querySelector('#nation-detail-panel [data-action="event-ledger-prev"]');
          if (paginationOk && refreshedPrevButton) {
            refreshedPrevButton.click();
            previousPage = await waitForLedger(resourceSearchExpected);
            paginationOk = previousPage.ok && previousPage.page === 1;
          } else if (paginationOk) {
            paginationOk = false;
          }
        } else if (paginationOk) {
          paginationOk = nextButton.disabled && prevButton.disabled;
        }

        const finalState = readLedgerState(resourceSearchExpected);
        const commandUiRemoved = !document.querySelector('#resource-command-panel')
          && document.querySelectorAll('#nation-detail-panel [data-action="open-resource-command"]').length === 0;
        const exportOk = csvExport.ok && jsonExport.ok;
        const finalOperationLink = (finalState.operationLinks || []).find(link =>
          link.action === 'resource-migration'
            && (normalizeResourceId(link.eventResourceId) === targetResourceId || normalizeResourceId(link.resourceId) === targetResourceId)
        ) || operationLink;
        const ok = finalState.ok && paginationOk && commandUiRemoved && exportOk && operationLinkGroupOk && Boolean(finalOperationLink);
        return {
          ok,
          reason: ok ? '' : (!paginationOk ? 'event-ledger-pagination-mismatch' : !commandUiRemoved ? 'resource-command-ui-regressed' : !exportOk ? 'event-ledger-export-mismatch' : !operationLinkGroupOk ? 'event-ledger-operation-link-action-failed' : !finalOperationLink ? 'event-ledger-operation-link-missing' : finalState.reason || 'event-ledger-ui-mismatch'),
          nationId,
          targetResourceId,
          openCount: opened.count,
          openTotal: opened.total,
          filter: finalState.filter,
          range: finalState.range,
          resourceId: finalState.resourceId,
          query: finalState.query,
          actor: finalState.actor,
          reasonFilter: finalState.reasonFilter,
          searchQuery,
          searchCount: searched.count,
          contextField,
          contextKey,
          contextValue,
          contextCount: contextFiltered.count,
          reasonField: reasonContext.field,
          reasonKey: reasonContext.key,
          reasonValue: reasonContext.value,
          reasonCount: reasonContext.count,
          jumpField: reasonContext.field,
          jumpValue: reasonContext.value,
          jumpCount: jumpedContext.count,
          readonlyFactKeys: requiredReadonlyKeys,
          readonlyFactCount: Array.isArray(jumpedContext.readonlyFacts) ? jumpedContext.readonlyFacts.length : 0,
          operationLinkCount: finalState.operationLinks.length,
          operationLinkState: String(finalOperationLink?.state || ''),
          operationLinkRoles: String(finalOperationLink?.roles || ''),
          operationLinkStatus: String(finalOperationLink?.status || ''),
          operationLinkCan: String(finalOperationLink?.canOperate || ''),
          operationLinkMatch: String(finalOperationLink?.match || ''),
          operationLinkTargetResourceId,
          operationLinkFilter,
          operationLinkGroupOk,
          operationLinkExplanationStates: finalOperationLink?.explanationStates || [],
          count: finalState.count,
          total: finalState.total,
          page: finalState.page,
          pages: finalState.pages,
          paginationMode,
          nextPage: nextPage ? nextPage.page : 0,
          previousPage: previousPage ? previousPage.page : 0,
          paginationOk,
          commandUiRemoved,
          exportOk,
          csvExportUrl: csvExport.url,
          jsonExportUrl: jsonExport.url,
          firstText: finalState.firstText,
          text: finalState.text,
        };
      })()`,
      awaitPromise: true,
      returnByValue: true,
    });
    const eventLedgerResult = eventLedgerEvaluation.result?.value || null;
    if (!eventLedgerResult?.ok) {
      throw new Error(`Nation event ledger UI failed. reason=${eventLedgerResult?.reason || 'unknown'}; phase=${eventLedgerResult?.phase || '<empty>'}; nationId=${eventLedgerResult?.nationId || '<empty>'}; targetResourceId=${eventLedgerResult?.targetResourceId || '<empty>'}; filter=${eventLedgerResult?.filter || '<empty>'}; range=${eventLedgerResult?.range || '<empty>'}; resourceId=${eventLedgerResult?.resourceId || '<empty>'}; query=${eventLedgerResult?.query || '<empty>'}; actor=${eventLedgerResult?.actor || '<empty>'}; reasonFilter=${eventLedgerResult?.reasonFilter || '<empty>'}; searchQuery=${eventLedgerResult?.searchQuery || '<empty>'}; contextField=${eventLedgerResult?.contextField || '<empty>'}; contextKey=${eventLedgerResult?.contextKey || '<empty>'}; contextValue=${eventLedgerResult?.contextValue || '<empty>'}; reasonField=${eventLedgerResult?.reasonField || '<empty>'}; reasonKey=${eventLedgerResult?.reasonKey || '<empty>'}; reasonValue=${eventLedgerResult?.reasonValue || '<empty>'}; jumpField=${eventLedgerResult?.jumpField || '<empty>'}; jumpValue=${eventLedgerResult?.jumpValue || '<empty>'}; jumpCount=${eventLedgerResult?.jumpCount ?? '<empty>'}; readonlyFactCount=${eventLedgerResult?.readonlyFactCount ?? '<empty>'}; operationLinkCount=${eventLedgerResult?.operationLinkCount ?? '<empty>'}; operationLinkState=${eventLedgerResult?.operationLinkState || '<empty>'}; operationLinkRoles=${eventLedgerResult?.operationLinkRoles || '<empty>'}; operationLinkStatus=${eventLedgerResult?.operationLinkStatus || '<empty>'}; operationLinkCan=${eventLedgerResult?.operationLinkCan || '<empty>'}; operationLinkMatch=${eventLedgerResult?.operationLinkMatch || '<empty>'}; operationLinkTargetResourceId=${eventLedgerResult?.operationLinkTargetResourceId || '<empty>'}; operationLinkFilter=${eventLedgerResult?.operationLinkFilter || '<empty>'}; operationLinkGroupOk=${String(eventLedgerResult?.operationLinkGroupOk)}; operationLinkExplanationStates=${(eventLedgerResult?.operationLinkExplanationStates || []).join('|') || '<none>'}; count=${eventLedgerResult?.count ?? '<empty>'}; total=${eventLedgerResult?.total ?? '<empty>'}; page=${eventLedgerResult?.page ?? '<empty>'}; pages=${eventLedgerResult?.pages ?? '<empty>'}; paginationMode=${eventLedgerResult?.paginationMode || '<empty>'}; paginationOk=${String(eventLedgerResult?.paginationOk)}; exportOk=${String(eventLedgerResult?.exportOk)}; csvExportUrl=${String(eventLedgerResult?.csvExportUrl || '').slice(0, 220)}; jsonExportUrl=${String(eventLedgerResult?.jsonExportUrl || '').slice(0, 220)}; commandUiRemoved=${String(eventLedgerResult?.commandUiRemoved)}; first=${String(eventLedgerResult?.firstText || '').slice(0, 160)}; text=${String(eventLedgerResult?.text || '').slice(0, 260)}`);
    }
    const eventFamilyContextEvaluation = await client.send('Runtime.evaluate', {
      expression: `(async () => {
        const delay = (ms) => new Promise(resolve => setTimeout(resolve, ms));
        const normalizeText = (value) => String(value || '').replace(/\\s+/g, ' ').trim();
        const familySpecs = [
          { filter: 'finance', type: 'treasury.withdraw', reason: 'audit-payout', actor: 'SmokeTreasurer', facts: ['amount', 'balance'], operation: 'treasury-withdraw', authKey: 'treasury-withdraw' },
          { filter: 'war', type: 'war.declared', reason: 'border-conflict', actor: 'SmokeMarshal', facts: ['amount', 'balance'], operation: 'war-declare', authKey: 'war-declare' },
          { filter: 'officer', type: 'officer.appointed', reason: 'staff-rotation', actor: 'SmokeFounder', facts: ['amount', 'balance'], operation: 'officer-management' },
          { filter: 'diplomacy', type: 'diplomacy.updated', reason: 'treaty-reset', actor: 'SmokeDiplomat', facts: ['fixed', 'members'], operation: 'diplomacy-set', authKey: 'diplomacy-set' },
          { filter: 'strategy', type: 'policy.set', reason: 'strategy-review', actor: 'SmokeSteward', facts: ['fixed', 'percent'], operation: 'policy-set', authKey: 'policy-set' },
          { filter: 'territory', type: 'territory.claimed', reason: 'border-growth', actor: 'SmokeFounder', facts: ['claims', 'fixed'], operation: 'territory-review' },
          { filter: 'nation', type: 'nation.created', reason: 'foundation-cycle', actor: 'SmokeFounder', facts: ['members', 'claims'], operation: 'nation-governance' },
        ];
        const map = window.strategicMap;
        const nationId = ${JSON.stringify(eventLedgerResult?.nationId || '')};
        if (!map || typeof map.loadEventLedger !== 'function' || !nationId) {
          return { ok: false, reason: 'missing-event-family-ledger-surface', families: [], count: 0 };
        }
        const ledgerSnapshot = () => {
          const ledger = document.querySelector('#nation-event-ledger');
          const data = map.eventLedgerState?.data || {};
          const events = Array.isArray(data.events) ? data.events : [];
          const chips = Array.from(document.querySelectorAll('#nation-event-ledger [data-event-ledger-context-chip="true"]'))
            .map(chip => ({
              field: String(chip.dataset.searchField || ''),
              key: String(chip.dataset.searchKey || ''),
              value: String(chip.dataset.searchValue || ''),
              text: normalizeText(chip.textContent || ''),
            }));
          const facts = Array.from(document.querySelectorAll('#nation-event-ledger [data-event-ledger-context-readonly="true"]'))
            .map(fact => ({
              key: String(fact.dataset.contextKey || '').toLowerCase(),
              value: String(fact.dataset.contextValue || ''),
              text: normalizeText(fact.textContent || ''),
            }));
          const jumps = Array.from(document.querySelectorAll('#nation-detail-panel [data-action="event-ledger-context-jump"]'))
            .map(button => ({
              field: String(button.dataset.searchField || ''),
              value: String(button.dataset.searchValue || ''),
              text: normalizeText(button.textContent || ''),
            }));
          const operationLinks = Array.from(document.querySelectorAll('#nation-event-ledger [data-event-operation-link="true"]'))
            .map(link => ({
              family: String(link.dataset.eventOperationFamily || ''),
              action: String(link.dataset.eventOperationAction || ''),
              authKey: String(link.dataset.eventOperationAuthKey || ''),
              filter: String(link.dataset.eventOperationFilter || ''),
              reason: String(link.dataset.eventOperationReason || ''),
              query: String(link.dataset.eventOperationQuery || ''),
              roles: String(link.dataset.eventOperationRoles || ''),
              status: String(link.dataset.eventOperationStatus || ''),
              buttons: Array.from(link.querySelectorAll('[data-action="event-ledger-operation-scope"]'))
                .map(button => ({
                  action: String(button.dataset.eventOperationAction || ''),
                  filter: String(button.dataset.operationFilter || ''),
                  reason: String(button.dataset.operationReason || ''),
                  query: String(button.dataset.operationQuery || ''),
                  text: normalizeText(button.textContent || ''),
                })),
              text: normalizeText(link.textContent || ''),
            }));
          return {
            ledger,
            filter: String(ledger?.dataset.eventLedgerFilter || ''),
            range: String(ledger?.dataset.eventLedgerRange || ''),
            reason: String(ledger?.dataset.eventLedgerReason || ''),
            count: Number(ledger?.dataset.eventLedgerCount || '0'),
            total: Number(ledger?.dataset.eventLedgerTotal || '0'),
            loading: Boolean(map.eventLedgerState?.loading),
            events,
            chips,
            facts,
            jumps,
            operationLinks,
          };
        };
        const waitForFamily = async (spec) => {
          const started = Date.now();
          let last = null;
          while (Date.now() - started < 15000) {
            last = ledgerSnapshot();
            const matching = last.events.find(event =>
              String(event.type || '') === spec.type
                && String(event.category || '') === spec.filter
                && String(event.details?.reason || '') === spec.reason
                && String(event.details?.actor || '') === spec.actor
            );
            const factsOk = spec.facts.every(key => last.facts.some(fact =>
              fact.key === key && String(fact.value || '').trim()
            ));
            const actorChipOk = last.chips.some(chip =>
              chip.field === 'actor' && chip.value === spec.actor
            );
            const reasonChipOk = last.chips.some(chip =>
              chip.field === 'reason' && chip.value === spec.reason
            );
            const jumpOk = last.jumps.some(jump =>
              jump.field === 'reason' && jump.value === spec.reason
            );
            const operationOk = !spec.operation || last.operationLinks.some(link =>
              link.family === spec.filter
                && link.action === spec.operation
                && (!spec.authKey || link.authKey === spec.authKey)
                && link.filter === spec.filter
                && link.reason === spec.reason
                && link.buttons.some(button =>
                  button.action === spec.operation
                    && button.filter === spec.filter
                    && button.reason === spec.reason
                )
            );
            const scopedOk = Boolean(last.ledger)
              && !last.loading
              && last.filter === spec.filter
              && last.range === '24h'
              && last.reason === spec.reason
              && last.count >= 1
              && last.total >= last.count
              && last.events.length >= 1
              && last.events.every(event => String(event.category || '') === spec.filter);
            if (scopedOk && matching && factsOk && actorChipOk && reasonChipOk && jumpOk && operationOk) {
              return {
                ok: true,
                family: spec.filter,
                type: spec.type,
                actor: spec.actor,
                reason: spec.reason,
                operation: spec.operation || '',
                authKey: spec.authKey || '',
                count: last.count,
                facts: spec.facts,
                chipCount: last.chips.length,
                jumpCount: last.jumps.length,
                operationCount: last.operationLinks.length,
                firstText: normalizeText(document.querySelector('#nation-event-ledger [data-event-ledger-item="true"]')?.textContent || '').slice(0, 180),
              };
            }
            await delay(100);
          }
          return {
            ok: false,
            family: spec.filter,
            type: spec.type,
            actor: spec.actor,
            reason: spec.reason,
            reasonSeen: String(last?.reason || ''),
            filterSeen: String(last?.filter || ''),
            count: Number(last?.count || 0),
            eventTypes: (last?.events || []).map(event => String(event.type || '')).slice(0, 8),
            eventCategories: (last?.events || []).map(event => String(event.category || '')).slice(0, 8),
            chips: (last?.chips || []).map(chip => chip.field + ':' + chip.key + ':' + chip.value).slice(0, 8),
            facts: (last?.facts || []).map(fact => fact.key + ':' + fact.value).slice(0, 8),
            jumps: (last?.jumps || []).map(jump => jump.field + ':' + jump.value).slice(0, 8),
            operationLinks: (last?.operationLinks || []).map(link => link.family + ':' + link.action + ':' + link.authKey + ':' + link.reason).slice(0, 8),
          };
        };
        const results = [];
        for (const spec of familySpecs) {
          map.eventLedgerState = {
            ...(map.eventLedgerState || {}),
            size: 20,
          };
          await map.loadEventLedger(nationId, 1, spec.filter, '24h', '', '', '', '', '', spec.reason);
          results.push(await waitForFamily(spec));
        }
        const ok = results.length === familySpecs.length && results.every(result => result.ok);
        return {
          ok,
          reason: ok ? '' : 'event-family-context-mismatch',
          families: results.filter(result => result.ok).map(result => result.family),
          count: results.filter(result => result.ok).length,
          results,
        };
      })()`,
      awaitPromise: true,
      returnByValue: true,
    });
    const eventFamilyContextResult = eventFamilyContextEvaluation.result?.value || null;
    if (!eventFamilyContextResult?.ok) {
      throw new Error(`Multi-family event ledger contexts failed. reason=${eventFamilyContextResult?.reason || 'unknown'}; families=${(eventFamilyContextResult?.families || []).join('|') || '<none>'}; results=${JSON.stringify(eventFamilyContextResult?.results || []).slice(0, 900)}`);
    }
    const focusNavigationEvaluation = await client.send('Runtime.evaluate', {
      expression: `(() => {
        const map = window.strategicMap;
        if (!map || typeof map.updateNationDetailPanel !== 'function' || typeof map.bindResourceListEvents !== 'function') {
          return { ok: false, reason: 'missing-focus-surface' };
        }
        const nationId = String(map.selectedNationId || '');
        const originalResourceMarkers = Array.isArray(map.resourceMarkers) ? map.resourceMarkers.slice() : [];
        const originalSelectedResourceId = String(map.selectedResourceId || '');
        const originalFilter = String(map.nationPriorityFilter || 'all');
        const refresh = () => {
          map.updateNationDetailPanel();
          map.updateResourceList();
          map.bindResourceListEvents();
          map.updateResourceMarkerVisualStates();
        };
        try {
          const nationResources = originalResourceMarkers.filter(resource => String((((resource || {}).data || {}).metadata || {}).nationId || '') === nationId);
          const seed = nationResources[0] || null;
          if (!nationId || !seed) {
            return {
              ok: false,
              reason: 'missing-focus-seed',
              nationId,
              nationResourceCount: nationResources.length,
            };
          }
          const baseData = JSON.parse(JSON.stringify(seed.data || {}));
          const syntheticData = JSON.parse(JSON.stringify(seed.data || {}));
          const baseId = String(seed.id || baseData.id || '');
          const syntheticId = baseId + '-smoke-focus-next';
          baseData.id = baseId;
          syntheticData.id = syntheticId;
          syntheticData.label = String(syntheticData.label || seed.label || 'Resource') + ' Smoke Next';
          syntheticData.x = Number(syntheticData.x || 0) + 48;
          syntheticData.z = Number(syntheticData.z || 0) + 48;
          baseData.metadata = {
            ...(baseData.metadata || {}),
            nationId,
            migrationActionState: 'ready',
            migrationState: 'none',
            migrationBalanceShortfall: '0.00',
            expectedResourceYield: '200',
            expectedExperienceYield: '80',
          };
          syntheticData.metadata = {
            ...(syntheticData.metadata || {}),
            nationId,
            migrationActionState: 'ready',
            migrationState: 'none',
            migrationBalanceShortfall: '0.00',
            expectedResourceYield: '100',
            expectedExperienceYield: '40',
          };
          const otherResources = originalResourceMarkers.filter(resource => String((((resource || {}).data || {}).metadata || {}).nationId || '') !== nationId);
          map.resourceMarkers = [
            ...otherResources,
            { id: baseId, label: String(seed.label || baseData.label || baseId), marker: null, data: baseData },
            { id: syntheticId, label: String(syntheticData.label || syntheticId), marker: null, data: syntheticData },
          ];
          map.selectedResourceId = baseId;
          map.nationPriorityFilter = 'all';
          refresh();

          const focusNode = document.querySelector('#nation-operations-focus');
          const nextButton = focusNode?.querySelector('[data-action="focus-next-resource"]');
          const prevButton = focusNode?.querySelector('[data-action="focus-prev-resource"]');
          if (!focusNode || !nextButton || !prevButton) {
            return {
              ok: false,
              reason: 'missing-focus-navigation-controls',
              hasFocusNode: Boolean(focusNode),
              hasNextButton: Boolean(nextButton),
              hasPrevButton: Boolean(prevButton),
            };
          }

          const initialResourceId = String(focusNode.dataset.focusResourceId || '');
          const initialIndex = Number(focusNode.dataset.focusIndex || '-1');
          const initialCount = Number(focusNode.dataset.focusCount || '0');
          const initialPrevDisabled = Boolean(prevButton.disabled);
          const initialNextDisabled = Boolean(nextButton.disabled);

          nextButton.click();

          const afterNextNode = document.querySelector('#nation-operations-focus');
          const afterNextPrevButton = afterNextNode?.querySelector('[data-action="focus-prev-resource"]');
          const afterNextNextButton = afterNextNode?.querySelector('[data-action="focus-next-resource"]');
          const afterNextResourceId = String(afterNextNode?.dataset.focusResourceId || '');
          const afterNextIndex = Number(afterNextNode?.dataset.focusIndex || '-1');
          const afterNextSelectedResourceId = String(map.selectedResourceId || '');
          const afterNextPrevDisabled = Boolean(afterNextPrevButton?.disabled);
          const afterNextNextDisabled = Boolean(afterNextNextButton?.disabled);

          afterNextPrevButton?.click();

          const afterPrevNode = document.querySelector('#nation-operations-focus');
          const afterPrevPrevButton = afterPrevNode?.querySelector('[data-action="focus-prev-resource"]');
          const afterPrevResourceId = String(afterPrevNode?.dataset.focusResourceId || '');
          const afterPrevIndex = Number(afterPrevNode?.dataset.focusIndex || '-1');
          const afterPrevSelectedResourceId = String(map.selectedResourceId || '');
          const afterPrevPrevDisabled = Boolean(afterPrevPrevButton?.disabled);

          const ok = initialCount === 2
            && initialIndex === 0
            && initialResourceId === baseId
            && initialPrevDisabled
            && !initialNextDisabled
            && afterNextIndex === 1
            && afterNextResourceId === syntheticId
            && afterNextSelectedResourceId === syntheticId
            && !afterNextPrevDisabled
            && afterNextNextDisabled
            && afterPrevIndex === 0
            && afterPrevResourceId === baseId
            && afterPrevSelectedResourceId === baseId
            && afterPrevPrevDisabled;

          return {
            ok,
            reason: ok ? '' : 'focus-navigation-state-mismatch',
            initialResourceId,
            initialIndex,
            initialCount,
            initialPrevDisabled,
            initialNextDisabled,
            afterNextResourceId,
            afterNextIndex,
            afterNextSelectedResourceId,
            afterNextPrevDisabled,
            afterNextNextDisabled,
            afterPrevResourceId,
            afterPrevIndex,
            afterPrevSelectedResourceId,
            afterPrevPrevDisabled,
          };
        } finally {
          map.resourceMarkers = originalResourceMarkers;
          map.selectedResourceId = originalSelectedResourceId;
          map.nationPriorityFilter = originalFilter;
          refresh();
        }
      })()`,
      returnByValue: true,
    });
    const focusNavigationResult = focusNavigationEvaluation.result?.value || null;
    if (!focusNavigationResult?.ok) {
      throw new Error(`Nation operations focus navigation failed. reason=${focusNavigationResult?.reason || 'unknown'}; initialResource=${focusNavigationResult?.initialResourceId || '<empty>'}; initialIndex=${focusNavigationResult?.initialIndex ?? '<empty>'}; initialCount=${focusNavigationResult?.initialCount ?? '<empty>'}; initialPrevDisabled=${String(focusNavigationResult?.initialPrevDisabled)}; initialNextDisabled=${String(focusNavigationResult?.initialNextDisabled)}; afterNextResource=${focusNavigationResult?.afterNextResourceId || '<empty>'}; afterNextIndex=${focusNavigationResult?.afterNextIndex ?? '<empty>'}; afterNextSelected=${focusNavigationResult?.afterNextSelectedResourceId || '<empty>'}; afterNextPrevDisabled=${String(focusNavigationResult?.afterNextPrevDisabled)}; afterNextNextDisabled=${String(focusNavigationResult?.afterNextNextDisabled)}; afterPrevResource=${focusNavigationResult?.afterPrevResourceId || '<empty>'}; afterPrevIndex=${focusNavigationResult?.afterPrevIndex ?? '<empty>'}; afterPrevSelected=${focusNavigationResult?.afterPrevSelectedResourceId || '<empty>'}; afterPrevPrevDisabled=${String(focusNavigationResult?.afterPrevPrevDisabled)}`);
    }
    const resourceExplanationFixtureEvaluation = await client.send('Runtime.evaluate', {
      expression: `(() => {
        const map = window.strategicMap;
        if (!map
          || typeof map.resourceMigrationExplanation !== 'function'
          || typeof map.renderResourceMigrationExplanation !== 'function') {
          return { ok: false, reason: 'missing-resource-explanation-renderer' };
        }
        const normalizeText = (value) => String(value || '').replace(/\\s+/g, ' ').trim();
        const fixtures = [
          {
            state: 'ready',
            severity: 'success',
            summary: 'fixture ready summary',
            reason: 'fixture ready reason',
            nextStep: 'fixture ready next',
            shortfall: '0.00',
          },
          {
            state: 'awaiting-target',
            severity: 'info',
            summary: 'fixture awaiting target summary',
            reason: 'fixture awaiting target reason',
            nextStep: 'fixture awaiting target next',
            shortfall: '0.00',
          },
          {
            state: 'waiting-depletion',
            severity: 'info',
            summary: 'fixture waiting depletion summary',
            reason: 'fixture waiting depletion reason',
            nextStep: 'fixture waiting depletion next',
            shortfall: '0.00',
          },
          {
            state: 'insufficient-balance',
            severity: 'error',
            summary: 'fixture insufficient balance summary',
            reason: 'fixture insufficient balance reason',
            nextStep: 'fixture insufficient balance next',
            shortfall: '1234.56',
          },
          {
            state: 'player-offline',
            severity: 'error',
            summary: 'fixture player offline summary',
            reason: 'fixture player offline reason',
            nextStep: 'fixture player offline next',
            shortfall: '0.00',
          },
        ];
        const host = document.createElement('section');
        host.setAttribute('data-resource-explanation-fixture', 'true');
        host.style.position = 'fixed';
        host.style.left = '-10000px';
        host.style.top = '0';
        host.style.width = '320px';
        host.style.pointerEvents = 'none';
        host.innerHTML = fixtures.map(fixture => {
          const explanation = map.resourceMigrationExplanation({
            migrationActionState: fixture.state,
            migrationExplanationState: fixture.state,
            migrationExplanationSeverity: fixture.severity,
            migrationExplanationSummary: fixture.summary,
            migrationExplanationPrimaryReason: fixture.reason,
            migrationExplanationReasonCodes: fixture.state + ',fixture',
            migrationNextStep: fixture.nextStep,
            migrationBalanceShortfall: fixture.shortfall,
          });
          return '<article data-fixture-state="' + fixture.state + '">' + map.renderResourceMigrationExplanation(explanation) + '</article>';
        }).join('');
        document.body.appendChild(host);
        try {
          const nodes = Array.from(host.querySelectorAll('[data-resource-explanation-state]'));
          const states = nodes.map(node => String(node.dataset.resourceExplanationState || ''));
          const severities = nodes.map(node => String(node.dataset.resourceExplanationSeverity || ''));
          const codes = nodes.map(node => String(node.dataset.resourceExplanationCodes || ''));
          const text = normalizeText(host.textContent || '');
          const styleByState = Object.fromEntries(nodes.map(node => {
            const style = window.getComputedStyle(node);
            return [String(node.dataset.resourceExplanationState || ''), {
              borderColor: style.borderColor,
              backgroundColor: style.backgroundColor,
            }];
          }));
          const expectedStates = fixtures.map(fixture => fixture.state);
          const stateSet = new Set(states);
          const severityByState = Object.fromEntries(nodes.map(node => [
            String(node.dataset.resourceExplanationState || ''),
            String(node.dataset.resourceExplanationSeverity || ''),
          ]));
          const ok = nodes.length === fixtures.length
            && expectedStates.every(state => stateSet.has(state))
            && severityByState.ready === 'success'
            && severityByState['awaiting-target'] === 'info'
            && severityByState['waiting-depletion'] === 'info'
            && severityByState['insufficient-balance'] === 'error'
            && severityByState['player-offline'] === 'error'
            && codes.every(value => value.includes('fixture'))
            && fixtures.every(fixture => text.includes(fixture.summary) && text.includes(fixture.reason))
            && text.includes('1234.56')
            && styleByState.ready?.borderColor
            && styleByState['insufficient-balance']?.borderColor
            && styleByState.ready.borderColor !== styleByState['insufficient-balance'].borderColor;
          return {
            ok,
            reason: ok ? '' : 'resource-explanation-fixture-mismatch',
            states,
            severities,
            codes,
            count: nodes.length,
            text,
            readyBorderColor: styleByState.ready?.borderColor || '',
            errorBorderColor: styleByState['insufficient-balance']?.borderColor || '',
          };
        } finally {
          host.remove();
        }
      })()`,
      returnByValue: true,
    });
    const resourceExplanationFixtureResult = resourceExplanationFixtureEvaluation.result?.value || null;
    if (!resourceExplanationFixtureResult?.ok) {
      throw new Error(`Resource explanation fixture failed. reason=${resourceExplanationFixtureResult?.reason || 'unknown'}; states=${(resourceExplanationFixtureResult?.states || []).join('|') || '<none>'}; severities=${(resourceExplanationFixtureResult?.severities || []).join('|') || '<none>'}; codes=${(resourceExplanationFixtureResult?.codes || []).join('|') || '<none>'}; count=${resourceExplanationFixtureResult?.count ?? '<empty>'}; readyBorder=${resourceExplanationFixtureResult?.readyBorderColor || '<empty>'}; errorBorder=${resourceExplanationFixtureResult?.errorBorderColor || '<empty>'}; text=${String(resourceExplanationFixtureResult?.text || '').slice(0, 260)}`);
    }
    const resourceExplanationRuntimeEvaluation = await client.send('Runtime.evaluate', {
      expression: `(async () => {
        const map = window.strategicMap;
        if (!map
          || typeof map.resourceMigrationExplanation !== 'function'
          || typeof map.renderResourceMigrationExplanation !== 'function') {
          return { ok: false, reason: 'missing-resource-explanation-renderer' };
        }
        const expectedStates = ['ready', 'awaiting-target', 'waiting-depletion', 'insufficient-balance', 'player-offline'];
        const metadataOf = (marker) => (((marker || {}).data || {}).metadata || (marker || {}).metadata || {});
        const stateOf = (metadata) => String(metadata.migrationExplanationState || metadata.migrationActionState || '').trim();
        const severityOf = (metadata) => String(metadata.migrationExplanationSeverity || '').trim();
        const resourceMarkersFromSnapshot = (snapshot) => {
          const layer = Array.isArray(snapshot?.layers)
            ? snapshot.layers.find(entry => entry && entry.type === 'RESOURCE_DISTRICTS')
            : null;
          return Array.isArray(layer?.markers) ? layer.markers : [];
        };
        const snapshotUrlFor = (mapUrl) => {
          const parsed = new URL(mapUrl, window.location.href);
          parsed.pathname = '/api/map/snapshot';
          return parsed.toString();
        };
        const fetchSnapshot = async (label, mapUrl) => {
          if (!mapUrl) {
            return { label, ok: false, reason: 'missing-url', markers: [] };
          }
          const response = await fetch(snapshotUrlFor(mapUrl), { cache: 'no-store' });
          if (!response.ok) {
            return { label, ok: false, reason: 'http-' + response.status, markers: [] };
          }
          const snapshot = await response.json();
          return { label, ok: true, reason: '', markers: resourceMarkersFromSnapshot(snapshot) };
        };
        const sources = [
          {
            label: 'ready-viewer',
            ok: true,
            reason: '',
            markers: Array.isArray(map.resourceMarkers) ? map.resourceMarkers : [],
          },
          await fetchSnapshot('low-balance-viewer', ${JSON.stringify(resourceRuntimeLowUrl)}),
          await fetchSnapshot('offline-viewer', ${JSON.stringify(resourceRuntimeOfflineUrl)}),
        ];
        const found = {};
        const seen = [];
        for (const source of sources) {
          for (const marker of source.markers || []) {
            const metadata = metadataOf(marker);
            const state = stateOf(metadata);
            if (!state) {
              continue;
            }
            const detail = {
              source: source.label,
              state,
              severity: severityOf(metadata),
              markerId: String(marker.id || ''),
              migrationState: String(metadata.migrationState || ''),
              viewerOnline: String(metadata.viewerOnline || ''),
              viewerCanAffordMigration: String(metadata.viewerCanAffordMigration || ''),
              shortfall: String(metadata.migrationBalanceShortfall || '0.00'),
              summary: String(metadata.migrationExplanationSummary || ''),
              reason: String(metadata.migrationExplanationPrimaryReason || ''),
              metadata,
            };
            seen.push(detail);
            if (expectedStates.includes(state) && !found[state]) {
              found[state] = detail;
            }
          }
        }
        const host = document.createElement('section');
        host.setAttribute('data-resource-explanation-runtime', 'true');
        host.style.position = 'fixed';
        host.style.left = '-10000px';
        host.style.top = '0';
        host.style.width = '320px';
        host.style.pointerEvents = 'none';
        host.innerHTML = expectedStates.map(state => {
          const detail = found[state];
          if (!detail) {
            return '<article data-runtime-state-missing="' + state + '"></article>';
          }
          const explanation = map.resourceMigrationExplanation(detail.metadata);
          return '<article data-runtime-state="' + state + '">' + map.renderResourceMigrationExplanation(explanation) + '</article>';
        }).join('');
        document.body.appendChild(host);
        try {
          const nodes = Array.from(host.querySelectorAll('[data-resource-explanation-state]'));
          const renderedStates = nodes.map(node => String(node.dataset.resourceExplanationState || '').trim());
          const renderedText = String(host.textContent || '').replace(/\\s+/g, ' ').trim();
          const stateSet = new Set(renderedStates);
          const sourceOk = sources.every(source => source.ok);
          const semanticOk = found.ready?.source === 'ready-viewer'
            && found.ready?.severity === 'success'
            && found.ready?.viewerOnline === 'true'
            && found.ready?.viewerCanAffordMigration === 'true'
            && found['awaiting-target']?.source === 'ready-viewer'
            && found['awaiting-target']?.migrationState === 'awaiting_target'
            && found['waiting-depletion']?.source === 'ready-viewer'
            && found['waiting-depletion']?.migrationState === 'waiting_depletion'
            && found['insufficient-balance']?.source === 'low-balance-viewer'
            && found['insufficient-balance']?.severity === 'error'
            && found['insufficient-balance']?.viewerOnline === 'true'
            && found['insufficient-balance']?.viewerCanAffordMigration === 'false'
            && Number(found['insufficient-balance']?.shortfall || '0') > 0
            && found['player-offline']?.source === 'offline-viewer'
            && found['player-offline']?.severity === 'error'
            && found['player-offline']?.viewerOnline === 'false';
          const ok = sourceOk
            && expectedStates.every(state => Boolean(found[state]))
            && expectedStates.every(state => stateSet.has(state))
            && nodes.length === expectedStates.length
            && semanticOk
            && renderedText.length > 0;
          return {
            ok,
            reason: ok ? '' : (!sourceOk ? 'runtime-snapshot-fetch-failed' : !semanticOk ? 'runtime-state-semantic-mismatch' : 'runtime-state-render-mismatch'),
            states: expectedStates.filter(state => Boolean(found[state])),
            renderedStates,
            sourceStatus: sources.map(source => source.label + ':' + (source.ok ? 'ok' : source.reason)),
            seenStates: seen.map(detail => detail.source + ':' + detail.state + ':' + detail.viewerOnline + ':' + detail.viewerCanAffordMigration + ':' + detail.shortfall),
            count: nodes.length,
            text: renderedText,
          };
        } finally {
          host.remove();
        }
      })()`,
      awaitPromise: true,
      returnByValue: true,
    });
    const resourceExplanationRuntimeResult = resourceExplanationRuntimeEvaluation.result?.value || null;
    if (!resourceExplanationRuntimeResult?.ok) {
      throw new Error(`Resource explanation runtime states failed. reason=${resourceExplanationRuntimeResult?.reason || 'unknown'}; states=${(resourceExplanationRuntimeResult?.states || []).join('|') || '<none>'}; rendered=${(resourceExplanationRuntimeResult?.renderedStates || []).join('|') || '<none>'}; sources=${(resourceExplanationRuntimeResult?.sourceStatus || []).join('|') || '<none>'}; seen=${(resourceExplanationRuntimeResult?.seenStates || []).join('|') || '<none>'}; count=${resourceExplanationRuntimeResult?.count ?? '<empty>'}; text=${String(resourceExplanationRuntimeResult?.text || '').slice(0, 260)}`);
    }
    const commandUiRemovalEvaluation = await client.send('Runtime.evaluate', {
      expression: `(() => {
        const map = window.strategicMap;
        const panelCount = document.querySelectorAll('#resource-command-panel, #resource-command-meta, #resource-command-status, #resource-command-details, #resource-command-submit, #resource-command-cancel').length;
        const actionCount = document.querySelectorAll('[data-action="open-resource-command"]').length;
        const staleMethods = ['updateResourceCommandPanel', 'openResourceCommand', 'submitResourceCommand', 'cancelResourceCommand', 'resourceActionContext']
          .filter(name => map && typeof map[name] === 'function');
        const staleState = ['resourceCommandMessage', 'resourceCommandConfirmDistrictId', 'resourceCommandSubmitting']
          .filter(name => map && Object.prototype.hasOwnProperty.call(map, name));
        return {
          ok: panelCount === 0 && actionCount === 0 && staleMethods.length === 0 && staleState.length === 0,
          panelCount,
          actionCount,
          staleMethods,
          staleState,
        };
      })()`,
      returnByValue: true,
    });
    const commandUiRemoval = commandUiRemovalEvaluation.result?.value || null;
    if (!commandUiRemoval?.ok) {
      throw new Error(`Resource district command UI was not fully removed. panelCount=${commandUiRemoval?.panelCount ?? '<unknown>'}; actionCount=${commandUiRemoval?.actionCount ?? '<unknown>'}; staleMethods=${(commandUiRemoval?.staleMethods || []).join('|') || '<none>'}; staleState=${(commandUiRemoval?.staleState || []).join('|') || '<none>'}`);
    }
    const screenshot = await client.send('Page.captureScreenshot', {
      format: 'png',
      captureBeyondViewport: false,
    });
    await writeFile(screenshotFile, Buffer.from(screenshot.data, 'base64'));
    let mobileBaselineResult = {
      ok: !mobileScreenshotFile,
      width: 0,
      height: 0,
      requiredCount: 0,
      factCount: 0,
      jumpCount: 0,
      mapVisibleWidth: 0,
      reason: mobileScreenshotFile ? 'not-run' : 'skipped',
    };
    if (mobileScreenshotFile) {
      await client.send('Emulation.setDeviceMetricsOverride', {
        width: 390,
        height: 844,
        deviceScaleFactor: 2,
        mobile: true,
      });
      await delay(150);
      const mobileBaselineEvaluation = await client.send('Runtime.evaluate', {
        expression: `(async () => {
          const delay = (ms) => new Promise(resolve => setTimeout(resolve, ms));
          const normalizeText = (value) => String(value || '').replace(/\\s+/g, ' ').trim();
          const rectOf = (node) => {
            const rect = node ? node.getBoundingClientRect() : null;
            return rect ? {
              left: rect.left,
              top: rect.top,
              right: rect.right,
              bottom: rect.bottom,
              width: rect.width,
              height: rect.height,
            } : null;
          };
          const nextFrame = () => new Promise(resolve => requestAnimationFrame(() => resolve()));
          const isVisible = (node) => {
            const rect = rectOf(node);
            const style = node ? window.getComputedStyle(node) : null;
            return Boolean(rect)
              && rect.width > 0
              && rect.height > 0
              && style
              && style.visibility !== 'hidden'
              && style.display !== 'none';
          };
          const overlaps = (first, second) => first.left < second.right - 2
            && first.right > second.left + 2
            && first.top < second.bottom - 2
            && first.bottom > second.top + 2;
          const map = window.strategicMap;
          const nationId = ${JSON.stringify(eventLedgerResult?.nationId || '')};
          const reasonValue = ${JSON.stringify(eventLedgerResult?.reasonValue || '')};
          const familySpecs = ${JSON.stringify((eventFamilyContextResult?.results || [])
            .filter((result) => result && result.ok)
            .map((result) => ({
              filter: result.family,
              type: result.type,
              actor: result.actor,
              reason: result.reason,
              facts: result.facts || ['amount', 'balance'],
            })))};
          if (!map || typeof map.loadEventLedger !== 'function' || !nationId || !reasonValue) {
            return { ok: false, reason: 'missing-mobile-event-ledger-state', width: window.innerWidth, height: window.innerHeight };
          }
          if (typeof map.setSidebarCollapsed === 'function') {
            map.setSidebarCollapsed(false, { animate: false });
          }
          await map.loadEventLedger(nationId, 1, 'officer', '24h', '', '', '', '', '', reasonValue);
          const started = Date.now();
          let ledger = null;
          while (Date.now() - started < 15000) {
            ledger = document.querySelector('#nation-event-ledger');
            if (ledger
              && String(ledger.dataset.eventLedgerFilter || '') === 'officer'
              && String(ledger.dataset.eventLedgerReason || '') === reasonValue
              && Number(ledger.dataset.eventLedgerCount || '0') > 0
              && !map.eventLedgerState?.loading) {
              break;
            }
            await delay(100);
          }
          const readFamilyMobileState = () => {
            const ledgerNode = document.querySelector('#nation-event-ledger');
            const data = map.eventLedgerState?.data || {};
            const events = Array.isArray(data.events) ? data.events : [];
            const chips = Array.from(document.querySelectorAll('#nation-event-ledger [data-event-ledger-context-chip="true"]'))
              .filter(isVisible)
              .map(chip => ({
                field: String(chip.dataset.searchField || ''),
                value: String(chip.dataset.searchValue || ''),
              }));
            const facts = Array.from(document.querySelectorAll('#nation-event-ledger [data-event-ledger-context-readonly="true"]'))
              .filter(isVisible)
              .map(fact => ({
                key: String(fact.dataset.contextKey || '').toLowerCase(),
                value: String(fact.dataset.contextValue || ''),
              }));
            const jumps = Array.from(document.querySelectorAll('#nation-detail-panel [data-action="event-ledger-context-jump"]')).filter(isVisible)
              .map(button => ({
                field: String(button.dataset.searchField || ''),
                value: String(button.dataset.searchValue || ''),
              }));
            return {
              ledger: ledgerNode,
              filter: String(ledgerNode?.dataset.eventLedgerFilter || ''),
              range: String(ledgerNode?.dataset.eventLedgerRange || ''),
              reason: String(ledgerNode?.dataset.eventLedgerReason || ''),
              count: Number(ledgerNode?.dataset.eventLedgerCount || '0'),
              events,
              chips,
              facts,
              jumps,
              loading: Boolean(map.eventLedgerState?.loading),
            };
          };
          const familyMobileResults = [];
          for (const spec of familySpecs) {
            await map.loadEventLedger(nationId, 1, spec.filter, '24h', '', '', '', '', '', spec.reason);
            const familyStarted = Date.now();
            let familyState = null;
            while (Date.now() - familyStarted < 15000) {
              familyState = readFamilyMobileState();
              const matching = familyState.events.find(event =>
                String(event.type || '') === spec.type
                  && String(event.category || '') === spec.filter
                  && String(event.details?.reason || '') === spec.reason
                  && String(event.details?.actor || '') === spec.actor
              );
              const factsOk = (spec.facts || []).every(key => familyState.facts.some(fact =>
                fact.key === String(key || '').toLowerCase() && String(fact.value || '').trim()
              ));
              const actorChipOk = familyState.chips.some(chip => chip.field === 'actor' && chip.value === spec.actor);
              const reasonChipOk = familyState.chips.some(chip => chip.field === 'reason' && chip.value === spec.reason);
              const jumpOk = familyState.jumps.some(jump => jump.field === 'reason' && jump.value === spec.reason);
              const ok = Boolean(familyState.ledger)
                && !familyState.loading
                && familyState.filter === spec.filter
                && familyState.range === '24h'
                && familyState.reason === spec.reason
                && familyState.count >= 1
                && Boolean(matching)
                && factsOk
                && actorChipOk
                && reasonChipOk
                && jumpOk;
              if (ok) {
                familyMobileResults.push({
                  ok: true,
                  family: spec.filter,
                  count: familyState.count,
                  chipCount: familyState.chips.length,
                  factCount: familyState.facts.length,
                  jumpCount: familyState.jumps.length,
                });
                break;
              }
              await delay(100);
            }
            if (familyMobileResults.length === 0 || familyMobileResults[familyMobileResults.length - 1].family !== spec.filter) {
              familyMobileResults.push({
                ok: false,
                family: spec.filter,
                filterSeen: String(familyState?.filter || ''),
                reasonSeen: String(familyState?.reason || ''),
                count: Number(familyState?.count || 0),
                eventTypes: (familyState?.events || []).map(event => String(event.type || '')).slice(0, 5),
                chips: (familyState?.chips || []).map(chip => chip.field + ':' + chip.value).slice(0, 5),
                facts: (familyState?.facts || []).map(fact => fact.key + ':' + fact.value).slice(0, 5),
                jumps: (familyState?.jumps || []).map(jump => jump.field + ':' + jump.value).slice(0, 5),
              });
            }
          }
          const familyMobileOk = familySpecs.length >= 7
            && familyMobileResults.length === familySpecs.length
            && familyMobileResults.every(result => result.ok);
          ledger = document.querySelector('#nation-event-ledger');
          const sidebar = document.querySelector('#sidebar');
          const sidebarContent = document.querySelector('#sidebar-content');
          const ledgerBlock = ledger ? (ledger.closest('.nation-detail-block') || ledger) : null;
          const searchInput = document.querySelector('#nation-detail-panel [data-event-ledger-search="query"]');
          const activeContext = document.querySelector('#nation-detail-panel .event-ledger-context-active');
          const jumpButtons = Array.from(document.querySelectorAll('#nation-detail-panel [data-action="event-ledger-context-jump"]')).filter(isVisible);
          const facts = Array.from(document.querySelectorAll('#nation-event-ledger [data-event-ledger-context-readonly="true"]')).filter(isVisible);
          const contextChips = Array.from(document.querySelectorAll('#nation-event-ledger [data-event-ledger-context-chip="true"]')).filter(isVisible);
          const scrollHostFor = (node) => {
            let current = node ? node.parentElement : null;
            while (current && current !== document.body && current !== document.documentElement) {
              const style = window.getComputedStyle(current);
              const canScroll = /(auto|scroll|overlay)/.test(style.overflowY || '')
                && current.scrollHeight > current.clientHeight + 1;
              if (canScroll) {
                return current;
              }
              current = current.parentElement;
            }
            return sidebarContent || document.scrollingElement || document.documentElement;
          };
          const alignNodeForScreenshot = async (node) => {
            if (!node) {
              return null;
            }
            const host = scrollHostFor(node);
            for (let index = 0; index < 6; index += 1) {
              const nodeRect = rectOf(node);
              const hostRect = host === document.scrollingElement || host === document.documentElement
                ? { top: 0, bottom: window.innerHeight }
                : rectOf(host);
              if (!nodeRect || !hostRect) {
                break;
              }
              const targetTop = Math.max(0, hostRect.top) + 12;
              const delta = nodeRect.top - targetTop;
              if (Math.abs(delta) <= 2) {
                break;
              }
              host.scrollTop = Math.max(0, host.scrollTop + delta);
              await nextFrame();
              await delay(40);
            }
            return {
              hostTag: String(host?.id || host?.className || host?.tagName || ''),
              hostScrollTop: Number(host?.scrollTop || 0),
              targetRect: rectOf(node),
            };
          };
          const screenshotAnchor = searchInput || activeContext || ledgerBlock || ledger;
          const screenshotAnchorState = await alignNodeForScreenshot(screenshotAnchor);
          await delay(140);
          const interactiveControls = Array.from(new Set([
            searchInput,
            ...Array.from(document.querySelectorAll('#nation-detail-panel .nation-event-ledger-search button')),
            activeContext,
            ...jumpButtons,
          ].filter(node => node && isVisible(node))));
          const controls = Array.from(new Set([
            ...interactiveControls,
            ...facts,
          ].filter(node => node && isVisible(node))));
          const sidebarRect = rectOf(sidebar);
          const ledgerRect = rectOf(ledger);
          const viewportWidth = window.innerWidth;
          const viewportHeight = window.innerHeight;
          const mapVisibleWidth = sidebarRect ? Math.max(0, viewportWidth - sidebarRect.right) : 0;
          const horizontalOverflow = Math.max(
            document.documentElement ? document.documentElement.scrollWidth - viewportWidth : 0,
            document.body ? document.body.scrollWidth - viewportWidth : 0
          );
          const outsideControls = controls
            .map(node => ({ text: normalizeText(node.textContent || node.value || ''), rect: rectOf(node) }))
            .filter(entry => entry.rect && sidebarRect && (entry.rect.left < sidebarRect.left - 1 || entry.rect.right > sidebarRect.right + 1));
          const controlRects = controls
            .map(node => ({ text: normalizeText(node.textContent || node.value || ''), rect: rectOf(node) }))
            .filter(entry => entry.rect);
          const overlappingControls = [];
          for (let outer = 0; outer < controlRects.length; outer += 1) {
            for (let inner = outer + 1; inner < controlRects.length; inner += 1) {
              if (overlaps(controlRects[outer].rect, controlRects[inner].rect)) {
                overlappingControls.push(String(controlRects[outer].text || outer) + '|' + String(controlRects[inner].text || inner));
              }
            }
          }
          const interactiveRects = interactiveControls
            .map(node => ({ text: normalizeText(node.textContent || node.value || ''), rect: rectOf(node) }))
            .filter(entry => entry.rect);
          const minControlHeight = interactiveRects.reduce((min, entry) => Math.min(min, entry.rect.height), Number.POSITIVE_INFINITY);
          const isInsideViewport = (node) => {
            const rect = rectOf(node);
            return Boolean(rect)
              && rect.top >= 0
              && rect.bottom <= viewportHeight
              && rect.left >= 0
              && rect.right <= viewportWidth;
          };
          const screenshotVisibleCount = [
            searchInput,
            activeContext,
            jumpButtons[0],
            facts[0],
            facts[1],
            contextChips[0],
          ].filter(node => node && isInsideViewport(node)).length;
          const requiredCount = [
            Boolean(ledger),
            Boolean(searchInput && isVisible(searchInput)),
            Boolean(activeContext && isVisible(activeContext)),
            jumpButtons.length >= 1,
            facts.length >= 2,
            contextChips.length >= 1,
          ].filter(Boolean).length;
          const ok = viewportWidth <= 420
            && viewportHeight >= 760
            && Boolean(sidebarRect)
            && Boolean(ledgerRect)
            && requiredCount >= 6
            && familyMobileOk
            && mapVisibleWidth >= 64
            && horizontalOverflow <= 2
            && outsideControls.length === 0
            && overlappingControls.length === 0
            && Number.isFinite(minControlHeight)
            && minControlHeight >= 30
            && screenshotVisibleCount >= 5;
          return {
            ok,
            reason: ok ? '' : (!sidebarRect ? 'mobile-sidebar-missing'
              : !ledgerRect ? 'mobile-event-ledger-missing'
              : requiredCount < 6 ? 'mobile-event-ledger-controls-missing'
              : !familyMobileOk ? 'mobile-event-family-baseline-mismatch'
              : mapVisibleWidth < 64 ? 'mobile-map-context-hidden'
              : horizontalOverflow > 2 ? 'mobile-horizontal-overflow'
              : outsideControls.length > 0 ? 'mobile-control-outside-sidebar'
              : overlappingControls.length > 0 ? 'mobile-control-overlap'
              : !Number.isFinite(minControlHeight) || minControlHeight < 30 ? 'mobile-touch-target-too-small'
              : screenshotVisibleCount < 5 ? 'mobile-screenshot-target-not-visible'
              : 'mobile-event-ledger-baseline-mismatch'),
            width: viewportWidth,
            height: viewportHeight,
            sidebarWidth: sidebarRect ? sidebarRect.width : 0,
            mapVisibleWidth,
            horizontalOverflow,
            requiredCount,
            factCount: facts.length,
            jumpCount: jumpButtons.length,
            contextChipCount: contextChips.length,
            familyCount: familyMobileResults.filter(result => result.ok).length,
            familyResults: familyMobileResults,
            minControlHeight: Number.isFinite(minControlHeight) ? minControlHeight : 0,
            screenshotVisibleCount,
            ledgerTop: ledgerRect ? ledgerRect.top : 0,
            screenshotAnchor: screenshotAnchorState,
            outsideControls: outsideControls.slice(0, 5),
            overlappingControls: overlappingControls.slice(0, 5),
          };
        })()`,
        awaitPromise: true,
        returnByValue: true,
      });
      mobileBaselineResult = mobileBaselineEvaluation.result?.value || null;
      if (!mobileBaselineResult?.ok) {
        throw new Error(`Mobile event ledger baseline failed. reason=${mobileBaselineResult?.reason || 'unknown'}; viewport=${mobileBaselineResult?.width || '<empty>'}x${mobileBaselineResult?.height || '<empty>'}; requiredCount=${mobileBaselineResult?.requiredCount ?? '<empty>'}; familyCount=${mobileBaselineResult?.familyCount ?? '<empty>'}; familyResults=${JSON.stringify(mobileBaselineResult?.familyResults || []).slice(0, 900)}; facts=${mobileBaselineResult?.factCount ?? '<empty>'}; jumps=${mobileBaselineResult?.jumpCount ?? '<empty>'}; mapVisible=${mobileBaselineResult?.mapVisibleWidth ?? '<empty>'}; overflow=${mobileBaselineResult?.horizontalOverflow ?? '<empty>'}; minControlHeight=${mobileBaselineResult?.minControlHeight ?? '<empty>'}; screenshotVisible=${mobileBaselineResult?.screenshotVisibleCount ?? '<empty>'}; ledgerTop=${mobileBaselineResult?.ledgerTop ?? '<empty>'}; anchor=${JSON.stringify(mobileBaselineResult?.screenshotAnchor || null)}; outside=${JSON.stringify(mobileBaselineResult?.outsideControls || [])}; overlap=${(mobileBaselineResult?.overlappingControls || []).join('|') || '<none>'}`);
      }
      const mobileScreenshot = await client.send('Page.captureScreenshot', {
        format: 'png',
        captureBeyondViewport: false,
      });
      await writeFile(mobileScreenshotFile, Buffer.from(mobileScreenshot.data, 'base64'));
    }
    const officerAccessStatuses = Array.from(new Set(state.officerAuthorizationStatuses || []));
    const officerAccess = officerAccessStatuses.length === 1 ? officerAccessStatuses[0] : officerAccessStatuses.join('+');
    console.log(`STARCORE_BROWSER_SMOKE_PASS viewer=${viewerName} nation=${viewerNation} balance=${viewerBalance} nationDetail=true officerAuth=${(state.officerAuthorizationRoles || []).join('+') || 'missing'}:${state.officerAuthorizationCount || 0} officerAccess=${officerAccess || 'missing'}:${state.officerAuthorizationCanCount || 0} nationAction=true recentLog=${state.recentEventCount || 0} recentLogFilter=${nationActionResult?.recentLogFilterValue || 'unknown'}:${nationActionResult?.recentLogFilterCount || 0} eventQuery=${eventQueryResult?.filter || 'unknown'}:${eventQueryResult?.count || 0} eventLedger=${eventLedgerResult?.filter || 'unknown'}:${eventLedgerResult?.count || 0} eventOps=${eventLedgerResult?.operationLinkGroupOk ? 'resource+explanation+auth+group' : 'missing'}:${eventLedgerResult?.operationLinkGroupOk ? (eventLedgerResult?.operationLinkCount || 0) : 0} eventOpsFamilies=${(eventFamilyContextResult?.results || []).filter(result => result.operation).map(result => result.family).join('+') || 'missing'}:${(eventFamilyContextResult?.results || []).filter(result => result.operation).length || 0} eventSearch=${encodeURIComponent(eventLedgerResult?.searchQuery || '')}:${eventLedgerResult?.searchCount || 0} eventContext=${eventLedgerResult?.contextField || 'unknown'}-${encodeURIComponent(eventLedgerResult?.contextValue || '')}:${eventLedgerResult?.contextCount || 0} eventReason=${eventLedgerResult?.reasonField || 'unknown'}-${encodeURIComponent(eventLedgerResult?.reasonValue || '')}:${eventLedgerResult?.reasonCount || 0} eventJump=${eventLedgerResult?.jumpField || 'unknown'}-${encodeURIComponent(eventLedgerResult?.jumpValue || '')}:${eventLedgerResult?.jumpCount || 0} eventMobile=${mobileBaselineResult?.width || 0}x${mobileBaselineResult?.height || 0}:${mobileBaselineResult?.requiredCount || 0} eventFamilies=${(eventFamilyContextResult?.families || []).join('+') || 'missing'}:${eventFamilyContextResult?.count || 0} eventFamilyMobile=${(mobileBaselineResult?.familyResults || []).filter(result => result.ok).map(result => result.family).join('+') || 'missing'}:${mobileBaselineResult?.familyCount || 0} eventFacts=${Array.isArray(eventLedgerResult?.readonlyFactKeys) ? eventLedgerResult.readonlyFactKeys.join('+') : 'missing'}:${eventLedgerResult?.readonlyFactCount || 0} eventLedgerExport=${eventLedgerResult?.exportOk ? 'csv+json' : 'missing'} resourceAction=${state.resourceActionState || 'unknown'} resourceExplanation=${state.resourceExplanationState || state.resourceActionState || 'unknown'}:${state.resourceExplanationCount || 0} resourceExplanationFixture=${(resourceExplanationFixtureResult?.states || []).join('+') || 'missing'}:${resourceExplanationFixtureResult?.count || 0} resourceExplanationRuntime=${(resourceExplanationRuntimeResult?.states || []).join('+') || 'missing'}:${resourceExplanationRuntimeResult?.count || 0} resourceCost=${state.resourceCost || '0.00'} commandUiRemoved=true browser=${version.Browser || 'unknown'}`);
    console.log(`dom=${domFile}`);
    console.log(`screenshot=${screenshotFile}`);
    if (mobileScreenshotFile) {
      console.log(`mobileScreenshot=${mobileScreenshotFile}`);
    }
  } finally {
    try {
      client?.close();
    } catch {
      // Ignore shutdown noise.
    }
    killProcessTree(proc.pid);
  }

  if (stderr.toLowerCase().includes('error') && !stderr.includes('task_manager')) {
    console.error(stderr.trim());
  }
}

main().catch((error) => {
  console.error(error.stack || error.message);
  process.exit(1);
});
