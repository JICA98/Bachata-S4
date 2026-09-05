(() => {
  const grid = document.querySelector('#game-grid');
  if (!grid) return;
  const els = {
    search: document.querySelector('#search'), status: document.querySelector('#status-filter'), release: document.querySelector('#release-filter'),
    device: document.querySelector('#device-filter'), driver: document.querySelector('#driver-filter'), sort: document.querySelector('#sort-filter'),
    reset: document.querySelector('#reset-filters'), result: document.querySelector('#result-count'), empty: document.querySelector('#empty-state'),
    loadMore: document.querySelector('#load-more')
  };
  let index = null;
  let visible = 48;
  const unique = values => [...new Set(values.filter(Boolean))].sort((a,b) => String(a).localeCompare(String(b)));
  const fill = (select, values) => values.forEach(value => select.append(new Option(value, value)));
  const driverText = report => report?.driver?.display || [report?.driver?.name || report?.driver?.type || report?.driver?.kind, report?.driver?.version, report?.driver?.build].filter(Boolean).join(' ');
  const reportMatches = (report, filters) => {
    if (filters.status !== 'all' && report.status !== filters.status) return false;
    if (filters.release !== 'all' && report.releaseTag !== filters.release) return false;
    if (filters.device !== 'all' && report.device?.label !== filters.device) return false;
    if (filters.driver !== 'all' && driverText(report) !== filters.driver) return false;
    return true;
  };
  function filters() { return { status:els.status.value, release:els.release.value, device:els.device.value, driver:els.driver.value }; }
  function selectReport(game) {
    const f = filters();
    return (game.reports || []).find(report => reportMatches(report, f)) || null;
  }
  function render() {
    if (!index) return;
    const q = els.search.value.trim().toLowerCase();
    let rows = (index.games || []).map(game => ({ game, report:selectReport(game) })).filter(({game,report}) => {
      if (!report && Object.values(filters()).some(value => value !== 'all')) return false;
      const haystack = [game.title,game.cusaId,game.region,game.publisher,...(game.reports || []).flatMap(r => [r.summary,r.releaseTag,r.device?.label,r.device?.gpu,driverText(r)])].filter(Boolean).join(' ').toLowerCase();
      return !q || haystack.includes(q);
    });
    const statusRank = { playable:0, ingame:1, menus:2, boots:3, nothing:4, unknown:5 };
    if (els.sort.value === 'title') rows.sort((a,b) => a.game.title.localeCompare(b.game.title));
    else if (els.sort.value === 'status') rows.sort((a,b) => (statusRank[a.report?.status || a.game.bestStatus] ?? 9) - (statusRank[b.report?.status || b.game.bestStatus] ?? 9));
    else if (els.sort.value === 'fps') rows.sort((a,b) => (Number(b.report?.performance?.averageFps) || -1) - (Number(a.report?.performance?.averageFps) || -1));
    else rows.sort((a,b) => new Date(b.report?.testedAt || b.game.latestTestedAt || 0) - new Date(a.report?.testedAt || a.game.latestTestedAt || 0));
    const shown = rows.slice(0,visible);
    grid.replaceChildren(...shown.map(({game,report}) => window.BachataCards.createGameCard(game, report || (game.reports || [])[0])));
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
      render();
    })
    .catch(error => { console.error(error); grid.innerHTML = '<div class="empty-state"><h3>Compatibility data could not be loaded</h3><p>The generated site index is unavailable.</p></div>'; });
  [els.search,els.status,els.release,els.device,els.driver,els.sort].forEach(el => el.addEventListener(el === els.search ? 'input' : 'change', () => { visible = 48; render(); }));
  els.reset.addEventListener('click', () => { els.search.value=''; [els.status,els.release,els.device,els.driver].forEach(el => el.value='all'); els.sort.value='recent'; visible=48; render(); });
  els.loadMore.addEventListener('click', () => { visible += 48; render(); });
})();
