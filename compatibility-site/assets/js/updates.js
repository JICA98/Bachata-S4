(() => {
  'use strict';

  const INDEX_URL = 'data/site-index.json';
  const STATUS_LABEL = { playable: 'Playable', ingame: 'Ingame', menus: 'Menus', boots: 'Boots', nothing: 'Nothing', unknown: 'Unknown' };
  const PAGE_SIZE = 12;

  const els = {
    list: document.querySelector('#update-list'),
    meta: document.querySelector('#feed-meta'),
    loadMore: document.querySelector('#load-more'),
    empty: document.querySelector('#empty-state')
  };

  const state = {
    entries: [],
    visible: 0
  };

  function text(value, fallback = 'Not recorded') {
    const normalized = value === null || value === undefined ? '' : String(value).trim();
    return normalized || fallback;
  }

  function normalizeStatus(value) {
    const normalized = String(value || 'unknown').toLowerCase();
    return Object.hasOwn(STATUS_LABEL, normalized) ? normalized : 'unknown';
  }

  function parseDate(value) {
    const parsed = new Date(value || 0);
    return Number.isNaN(parsed.getTime()) ? new Date(0) : parsed;
  }

  function formatDate(value) {
    const parsed = parseDate(value);
    return parsed.getTime() ? new Intl.DateTimeFormat(undefined, { year: 'numeric', month: 'short', day: 'numeric' }).format(parsed) : 'Unknown date';
  }

  function formatFps(report) {
    const value = Number(report?.performance?.averageFps);
    return Number.isFinite(value) && value >= 0 ? `${value.toFixed(value >= 10 ? 1 : 2)} FPS` : '';
  }

  function collectEntries(index) {
    const entries = [];
    (index.games || []).forEach(game => {
      (game.reports || []).forEach(report => {
        entries.push({ game, report });
      });
    });
    entries.sort((a, b) => parseDate(b.report.testedAt) - parseDate(a.report.testedAt));
    return entries;
  }

  function createEntry({ game, report }) {
    const status = normalizeStatus(report.status);
    const article = document.createElement('article');
    article.className = 'update-item';
    const head = document.createElement('div');
    head.className = 'update-head';
    const badge = document.createElement('span');
    badge.className = `status-badge ${status}`;
    badge.textContent = STATUS_LABEL[status];
    const title = document.createElement('div');
    title.className = 'update-title';
    const name = document.createElement('a');
    name.href = `compatibility.html?game=${encodeURIComponent(game.cusaId)}&report=${encodeURIComponent(report.reportId)}`;
    name.textContent = game.title;
    const sub = document.createElement('p');
    sub.textContent = `${game.cusaId} · ${text(report.releaseTag, 'Unknown release')} · ${formatDate(report.testedAt)}`;
    title.append(name, sub);
    head.append(badge, title);
    const body = document.createElement('div');
    body.className = 'update-body';
    const specs = document.createElement('dl');
    specs.className = 'latest-specs';
    const specItem = (label, value) => {
      if (!value) return;
      const div = document.createElement('div');
      const dt = document.createElement('dt'); dt.textContent = label;
      const dd = document.createElement('dd'); dd.textContent = value;
      div.append(dt, dd);
      specs.append(div);
    };
    specItem('Bachata release', report.releaseTag);
    specItem('Device', report.device?.label);
    specItem('SoC', report.device?.soc);
    specItem('GPU', report.device?.gpu);
    specItem('Android', report.device?.androidVersion);
    specItem('Driver', report.driver?.display || report.driver?.name);
    const fps = formatFps(report);
    specItem('Average FPS', fps);
    body.append(specs);
    const summary = text(report.summary, '');
    if (summary) {
      const paragraph = document.createElement('p');
      paragraph.className = 'latest-summary';
      paragraph.textContent = summary;
      body.append(paragraph);
    }
    const actions = document.createElement('div');
    actions.className = 'latest-actions';
    const full = document.createElement('a');
    full.className = 'button button-quiet';
    full.href = `compatibility.html?game=${encodeURIComponent(game.cusaId)}&report=${encodeURIComponent(report.reportId)}`;
    full.textContent = 'View full report';
    actions.append(full);
    if (game.issueUrl) {
      const issue = document.createElement('a');
      issue.className = 'button button-quiet';
      issue.href = game.issueUrl;
      issue.target = '_blank';
      issue.rel = 'noreferrer';
      issue.textContent = 'Open discussion ↗';
      actions.append(issue);
    }
    body.append(actions);
    article.append(head, body);
    return article;
  }

  function render() {
    const visible = state.entries.slice(0, state.visible);
    els.list.replaceChildren(...visible.map(createEntry));
    els.list.setAttribute('aria-busy', 'false');
    els.loadMore.hidden = visible.length >= state.entries.length;
    els.loadMore.textContent = `Load more updates · ${state.entries.length - visible.length} remaining`;
    els.meta.textContent = `${state.entries.length} ${state.entries.length === 1 ? 'report' : 'reports'} · generated from real compatibility data`;
  }

  function showEmpty(message) {
    els.list.hidden = true;
    els.loadMore.hidden = true;
    els.meta.textContent = 'No compatibility updates found';
    els.empty.hidden = false;
    els.empty.querySelector('p').textContent = message;
  }

  async function loadUpdates() {
    try {
      const response = await fetch(INDEX_URL, { cache: 'no-store' });
      if (!response.ok) throw new Error(`${INDEX_URL}: HTTP ${response.status}`);
      const index = await response.json();
      if (!index || !Array.isArray(index.games)) throw new Error('site-index games must be an array');
      state.entries = collectEntries(index);
      state.visible = PAGE_SIZE;
      if (state.entries.length === 0) {
        showEmpty('Confirm the Pages workflow generated data/site-index.json with report summaries.');
        return;
      }
      render();
    } catch (error) {
      console.error('Updates feed failed to load:', error);
      showEmpty('Confirm the Pages workflow generated data/site-index.json. Refresh the page or try again later.');
    }
  }

  els.loadMore.addEventListener('click', () => { state.visible += PAGE_SIZE; render(); });
  loadUpdates();
})();
