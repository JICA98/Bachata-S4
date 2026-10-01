(() => {
  const grid = document.querySelector('#game-grid');
  if (!grid) return;
  const els = {
    search: document.querySelector('#search'), chips: document.querySelector('#status-chips'), release: document.querySelector('#release-filter'),
    device: document.querySelector('#device-filter'), driver: document.querySelector('#driver-filter'), sort: document.querySelector('#sort-filter'),
    reset: document.querySelector('#reset-filters'), result: document.querySelector('#result-count'), empty: document.querySelector('#empty-state'),
    loadMore: document.querySelector('#load-more')
  };
  // 'working' = ingame or better, the default so visitors first see what runs.
  const STATUSES = ['working', 'all', 'playable', 'ingame', 'menus', 'boots', 'nothing'];
  const DEFAULT_STATUS = 'working';
  const requested = new URLSearchParams(location.search).get('status');
  let index = null;
  let visible = 48;
  let status = STATUSES.includes(requested) ? requested : DEFAULT_STATUS;
  const unique = values => [...new Set(values.filter(Boolean))].sort((a,b) => String(a).localeCompare(String(b)));
  const fill = (select, values) => values.forEach(value => select.append(new Option(value, value)));
  const driverText = report => report?.driver?.display || [report?.driver?.name || report?.driver?.type || report?.driver?.kind, report?.driver?.version, report?.driver?.build].filter(Boolean).join(' ');
  // Status follows the same score the app shows; games without a score fall back to their latest report.
  const gameStatus = window.BachataCards.gameStatus;
  const statusMatches = (game, value) => value === 'all' || (value === 'working' ? window.BachataCards.isWorking(game) : gameStatus(game) === value);
  const chipLabel = value => value === 'all' ? 'All' : value === 'working' ? 'Ingame & up' : window.BachataCards.LABEL[value];
  const reportFilters = () => ({ release:els.release.value, device:els.device.value, driver:els.driver.value });
  const reportFiltered = () => Object.values(reportFilters()).some(value => value !== 'all');
  const reportMatches = (report, f) =>
    (f.release === 'all' || report.releaseTag === f.release) &&
    (f.device === 'all' || report.device?.label === f.device) &&
    (f.driver === 'all' || driverText(report) === f.driver);

  function renderChips() {
    const games = index?.games || [];
    els.chips.replaceChildren(...STATUSES.map(value => {
      const count = games.filter(game => statusMatches(game, value)).length;
      const chip = document.createElement('button');
      chip.type = 'button';
      chip.className = `status-chip ${value === 'all' ? '' : value}`.trim();
      chip.setAttribute('aria-pressed', status === value ? 'true' : 'false');
      if (value !== 'all') { const dot = document.createElement('span'); dot.className = `status-dot ${value}`; chip.append(dot); }
      chip.append(`${chipLabel(value)} · ${count}`);
      chip.addEventListener('click', () => { status = value; visible = 48; renderChips(); render(); });
      return chip;
    }));
  }

  function render() {
    if (!index) return;
    const q = els.search.value.trim().toLowerCase();
    const f = reportFilters();
    let rows = (index.games || []).map(game => ({ game, report: reportFiltered() ? (game.reports || []).find(r => reportMatches(r, f)) || null : null }))
      .filter(({ game, report }) => {
        if (reportFiltered() && !report) return false;
        if (!statusMatches(game, status)) return false;
        const haystack = [game.title, game.cusaId, game.region, game.publisher, ...(game.reports || []).flatMap(r => [r.summary, r.releaseTag, r.device?.label, r.device?.gpu, driverText(r)])].filter(Boolean).join(' ').toLowerCase();
        return !q || haystack.includes(q);
      });
    if (els.sort.value === 'featured') rows.sort((a,b) => window.BachataCards.featuredCompare(a.game, b.game));
    else if (els.sort.value === 'title') rows.sort((a,b) => a.game.title.localeCompare(b.game.title));
    else if (els.sort.value === 'status') rows.sort((a,b) => (b.game.compatibility?.score ?? -1) - (a.game.compatibility?.score ?? -1) || a.game.title.localeCompare(b.game.title));
    else if (els.sort.value === 'fps') rows.sort((a,b) => (Number((b.report || b.game.reports?.[0])?.performance?.averageFps) || -1) - (Number((a.report || a.game.reports?.[0])?.performance?.averageFps) || -1));
    else rows.sort((a,b) => new Date(b.report?.testedAt || b.game.latestTestedAt || 0) - new Date(a.report?.testedAt || a.game.latestTestedAt || 0));
    const shown = rows.slice(0, visible);
    grid.replaceChildren(...shown.map(({ game, report }, i) => window.BachataCards.createGameCard(game, report, i)));
    grid.hidden = rows.length === 0;
    els.empty.hidden = rows.length !== 0;
    els.result.textContent = `${rows.length} ${rows.length === 1 ? 'game' : 'games'}`;
    els.loadMore.hidden = shown.length >= rows.length;
    els.loadMore.textContent = shown.length < rows.length ? `Load more · ${rows.length - shown.length} remaining` : 'Load more';
  }

  fetch('/data/site-index.json', { cache:'no-store' })
    .then(r => { if (!r.ok) throw new Error(`HTTP ${r.status}`); return r.json(); })
    .then(data => {
      index = data;
      const reports = (data.games || []).flatMap(game => game.reports || []);
      fill(els.release, unique(reports.map(r => r.releaseTag)));
      fill(els.device, unique(reports.map(r => r.device?.label)));
      fill(els.driver, unique(reports.map(driverText)));
      renderChips();
      render();
    })
    .catch(error => { console.error(error); grid.innerHTML = '<div class="empty-state"><h3>Compatibility data could not be loaded</h3><p>The generated site index is unavailable.</p></div>'; });
  [els.search, els.release, els.device, els.driver, els.sort].forEach(el => el.addEventListener(el === els.search ? 'input' : 'change', () => { visible = 48; render(); }));
  els.reset.addEventListener('click', () => { els.search.value = ''; [els.release, els.device, els.driver].forEach(el => el.value = 'all'); els.sort.value = 'featured'; status = DEFAULT_STATUS; visible = 48; renderChips(); render(); });
  els.loadMore.addEventListener('click', () => { visible += 48; render(); });
})();
