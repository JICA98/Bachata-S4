(() => {
  const LABEL = { playable:'Playable', ingame:'Ingame', menus:'Menus', boots:'Boots', nothing:'Nothing', unknown:'Unknown' };
  const safeStatus = value => Object.hasOwn(LABEL, String(value || '').toLowerCase()) ? String(value).toLowerCase() : 'unknown';
  const el = (tag, className, textContent) => {
    const node = document.createElement(tag);
    if (className) node.className = className;
    if (textContent !== undefined) node.textContent = textContent;
    return node;
  };
  const plural = (n, word) => `${n} ${word}${n === 1 ? '' : 's'}`;

  function scoreRing(score, status, caption) {
    const ring = el('div', `score-ring ${status}`);
    const value = Number.isFinite(score) ? Math.max(0, Math.min(100, Math.round(score))) : null;
    ring.style.setProperty('--score', value ?? 0);
    ring.setAttribute('role', 'img');
    ring.setAttribute('aria-label', value === null ? 'No compatibility score yet' : `Compatibility score ${value} of 100`);
    ring.innerHTML = '<svg viewBox="0 0 36 36" aria-hidden="true"><circle class="track" cx="18" cy="18" r="15.9"/><circle class="arc" cx="18" cy="18" r="15.9" pathLength="100"/></svg>';
    const label = el('span');
    label.append(el('strong', '', value === null ? '—' : String(value)));
    if (caption && value !== null) label.append(el('small', '', caption));
    ring.append(label);
    return ring;
  }

  function statusPill(status) {
    return el('span', `status-pill ${status}`, LABEL[status]);
  }

  /** Same layout as the in-app catalog card. `report` narrows the card to a filtered report when given. */
  function createGameCard(game, report = null, index = 0) {
    const compat = game.compatibility || null;
    const latest = (game.reports || [])[0] || {};
    const shown = report || latest;
    const status = safeStatus(report ? report.status : compat?.status || game.latestStatus || shown.status);
    const link = el('a', `game-card ${status}`);
    link.href = `/games/${encodeURIComponent(game.cusaId)}/`;
    link.style.setProperty('--i', Math.min(index, 8));
    link.setAttribute('aria-label', `${game.title} ${game.cusaId} — open compatibility details`);

    const image = shown.thumbnail || game.thumbnail || '/assets/placeholder.svg';
    const backdrop = el('img', 'backdrop');
    backdrop.alt = '';
    backdrop.loading = 'lazy';
    backdrop.src = image;
    backdrop.onerror = () => backdrop.remove();

    const cover = el('img', 'cover');
    cover.alt = `${game.title} screenshot`;
    cover.loading = 'lazy';
    cover.src = image;
    cover.onerror = () => { cover.onerror = null; cover.src = '/assets/placeholder.svg'; };

    const body = el('div', 'body');
    body.append(el('h3', '', game.title));
    const reports = game.reportCount || (game.reports || []).length || 0;
    const sub = [game.cusaId, plural(reports, 'report')];
    if (game.socCount) sub.push(plural(game.socCount, 'chipset'));
    body.append(el('div', 'sub', sub.join(' · ')));
    const pills = el('div', 'pill-row');
    pills.append(statusPill(status));
    if (!report && compat && !compat.current && compat.release) pills.append(el('span', 'muted small', `from ${compat.release}`));
    if (report) pills.append(el('span', 'muted small', [report.releaseTag, report.device?.label].filter(Boolean).join(' · ')));
    body.append(pills);

    const ringStatus = safeStatus(compat?.status || status);
    link.append(backdrop, cover, body, scoreRing(compat?.score ?? NaN, ringStatus, compat?.release));
    return link;
  }
  window.BachataCards = { createGameCard, safeStatus, LABEL, scoreRing, statusPill };
})();
