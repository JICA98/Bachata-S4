(() => {
  'use strict';

  const INDEX_URL = 'data/site-index.json';
  const STATUS_LABEL = { playable: 'Playable', ingame: 'Ingame', menus: 'Menus', boots: 'Boots', nothing: 'Nothing', unknown: 'Unknown' };

  const els = {
    statGames: document.querySelector('#stat-games'),
    statReports: document.querySelector('#stat-reports'),
    statDevices: document.querySelector('#stat-devices'),
    statPlayable: document.querySelector('#stat-playable'),
    statIngame: document.querySelector('#stat-ingame'),
    statsMeta: document.querySelector('#stats-meta'),
    list: document.querySelector('#latest-list')
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

  function renderStats(index) {
    const stats = index.stats || {};
    els.statGames.textContent = stats.games ?? '—';
    els.statReports.textContent = stats.reports ?? '—';
    els.statDevices.textContent = stats.devices ?? '—';
    els.statPlayable.textContent = stats.playable ?? '—';
    els.statIngame.textContent = stats.ingame ?? '—';
    if (els.statsMeta && index.generatedAt) {
      els.statsMeta.textContent = `Data generated ${formatDate(index.generatedAt)} from the compatibility repository`;
    }
  }

  function cardRow(game) {
    const report = (game.reports || [])[0] || {};
    const status = normalizeStatus(report.status || game.bestStatus || game.latestStatus);
    const row = document.createElement('article');
    row.className = 'latest-row';
    const badge = document.createElement('span');
    badge.className = `status-badge ${status}`;
    badge.textContent = STATUS_LABEL[status];
    const title = document.createElement('div');
    title.className = 'latest-title';
    const name = document.createElement('a');
    name.href = `compatibility.html?game=${encodeURIComponent(game.cusaId)}`;
    name.textContent = game.title;
    const meta = document.createElement('p');
    meta.textContent = [game.cusaId, game.region, game.publisher].filter(Boolean).join(' · ');
    title.append(name, meta);
    const specs = document.createElement('dl');
    specs.className = 'latest-specs';
    const specItem = (label, value) => {
      const div = document.createElement('div');
      const dt = document.createElement('dt'); dt.textContent = label;
      const dd = document.createElement('dd'); dd.textContent = value;
      div.append(dt, dd);
      specs.append(div);
    };
    specItem('Release', text(report.releaseTag, game.latestRelease || 'Unknown release'));
    specItem('Device', text(report.device?.label));
    specItem('Driver', text(report.driver?.display || report.driver?.name));
    specItem('Tested', formatDate(report.testedAt || game.latestTestedAt));
    const fps = formatFps(report);
    if (fps) specItem('Average', fps);
    const summary = document.createElement('p');
    summary.className = 'latest-summary';
    summary.textContent = text(report.summary, '');
    const actions = document.createElement('div');
    actions.className = 'latest-actions';
    const full = document.createElement('a');
    full.className = 'button button-quiet';
    full.href = `compatibility.html?game=${encodeURIComponent(game.cusaId)}`;
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
    row.append(badge, title, specs, summary, actions);
    return row;
  }

  function renderLatest(index) {
    const games = (index.games || []).slice();
    games.sort((a, b) => parseDate(b.latestTestedAt) - parseDate(a.latestTestedAt));
    const rows = games.slice(0, 6).map(cardRow);
    els.list.replaceChildren(...rows);
    els.list.setAttribute('aria-busy', 'false');
  }

  async function loadHome() {
    try {
      const response = await fetch(INDEX_URL, { cache: 'no-store' });
      if (!response.ok) throw new Error(`${INDEX_URL}: HTTP ${response.status}`);
      const index = await response.json();
      if (!index || !Array.isArray(index.games)) throw new Error('site-index games must be an array');
      renderStats(index);
      renderLatest(index);
    } catch (error) {
      console.error('Homepage data failed to load:', error);
      els.list.innerHTML = '<div class="dialog-loading"><strong>Compatibility data could not be loaded.</strong><p>The site index may not have been generated yet. Check back after the next scheduled rebuild.</p></div>';
      els.list.setAttribute('aria-busy', 'false');
      if (els.statsMeta) els.statsMeta.textContent = 'Data unavailable';
      [els.statGames, els.statReports, els.statDevices, els.statPlayable, els.statIngame].forEach(el => { if (el) el.textContent = '—'; });
    }
  }

  loadHome();
})();
