(() => {
  const LABEL = { playable:'Playable', ingame:'Ingame', menus:'Menus', boots:'Boots', nothing:'Nothing', unknown:'Unknown' };
  const safeStatus = value => Object.hasOwn(LABEL, String(value || '').toLowerCase()) ? String(value).toLowerCase() : 'unknown';
  const text = (value, fallback = 'Not recorded') => {
    const normalized = value === null || value === undefined ? '' : String(value).trim();
    return normalized || fallback;
  };
  const fps = report => {
    const value = Number(report?.performance?.averageFps);
    return Number.isFinite(value) ? `${value >= 10 ? value.toFixed(1) : value.toFixed(2)} FPS` : 'Not recorded';
  };
  const driver = report => text(report?.driver?.display || [report?.driver?.name || report?.driver?.type || report?.driver?.kind, report?.driver?.version, report?.driver?.build].filter(Boolean).join(' '));
  function createGameCard(game, report = (game.reports || [])[0] || {}) {
    const status = safeStatus(report.status || game.bestStatus || game.latestStatus);
    const link = document.createElement('a');
    link.className = 'game-card';
    link.href = `/games/${encodeURIComponent(game.cusaId)}/`;
    link.setAttribute('aria-label', `${game.title} ${game.cusaId} — open compatibility details`);
    const imageWrap = document.createElement('div');
    imageWrap.className = 'game-image-wrap';
    const image = document.createElement('img');
    image.className = 'game-image';
    image.loading = 'lazy';
    image.alt = `${game.title} compatibility screenshot`;
    image.src = report.thumbnail || game.thumbnail || '/assets/placeholder.svg';
    image.onerror = () => { image.onerror = null; image.src = '/assets/placeholder.svg'; };
    const badge = document.createElement('span');
    badge.className = `status-badge ${status}`;
    badge.textContent = LABEL[status];
    const count = document.createElement('span');
    count.className = 'report-count';
    count.textContent = `${game.reportCount || (game.reports || []).length || 0} ${(game.reportCount || (game.reports || []).length) === 1 ? 'report' : 'reports'}`;
    imageWrap.append(image, badge, count);

    const body = document.createElement('div');
    body.className = 'game-card-body';
    const titleRow = document.createElement('div');
    titleRow.className = 'title-row';
    const title = document.createElement('h3'); title.textContent = game.title;
    const serial = document.createElement('span'); serial.className = 'serial'; serial.textContent = game.cusaId;
    titleRow.append(title, serial);
    const note = document.createElement('p'); note.className = 'game-note'; note.textContent = text(report.summary, 'Open the game page for full compatibility history.');
    const specs = document.createElement('dl'); specs.className = 'quick-specs';
    const add = (label, value) => {
      const wrap = document.createElement('div');
      const dt = document.createElement('dt'); dt.textContent = label;
      const dd = document.createElement('dd'); dd.textContent = text(value);
      wrap.append(dt, dd); specs.append(wrap);
    };
    add('Release', report.releaseTag || game.latestRelease);
    add('Device', report.device?.label);
    add('Driver', driver(report));
    add('Average', fps(report));
    body.append(titleRow, note, specs);
    link.append(imageWrap, body);
    return link;
  }
  window.BachataCards = { createGameCard, safeStatus, LABEL };
})();
