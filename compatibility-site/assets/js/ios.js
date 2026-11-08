(() => {
  const GROUPS = { done: 'Done (30+ fps)', below30: 'Below 30 fps', crash: 'Crashes / still testing', not_tested: 'Not tested yet' };
  const el = (t, c, x) => { const e = document.createElement(t); if (c) e.className = c; if (x != null) e.textContent = x; return e; };
  const tone = (v, g) => v == null ? '' : v >= g - 0.5 ? 't-ok' : v >= g * 2 / 3 ? 't-warn' : 't-bad';
  const fmt = v => v == null ? '—' : Number(v).toFixed(1);
  const lb = document.getElementById('lb');
  const openShot = s => {
    const im = document.getElementById('lb-img');
    im.src = '/assets/ios/img/' + s.f; im.alt = s.c;
    document.getElementById('lb-cap').textContent = s.c;
    lb.showModal ? lb.showModal() : lb.setAttribute('open', '');
  };
  document.getElementById('lb-close').addEventListener('click', () => lb.close ? lb.close() : lb.removeAttribute('open'));
  lb.addEventListener('click', e => { if (e.target === lb && lb.close) lb.close(); });

  function card(g) {
    const goal = g.target || 30;
    const c = el('article', 'ios-card');
    c.appendChild(el('h3', '', g.title));
    c.appendChild(el('span', 'sub', g.id));
    const cls = /^Testing now/.test(g.result) ? 'c-test' : 'c-' + g.group;
    c.appendChild(el('span', 'chip ' + cls, g.result));
    const meta = el('div', 'ios-meta');
    if (g.res) meta.appendChild(el('span', 'chip', g.res));
    if (g.site) meta.appendChild(el('span', 'chip', 'shadPS4 list: ' + g.site));
    if (g.screen) meta.appendChild(el('span', 'chip', g.screen === 'gameplay' ? 'Gameplay' : g.screen === 'menus' ? 'Menus' : 'No gameplay'));
    if (meta.children.length) c.appendChild(meta);
    if (g.avg != null) {
      const t = tone(g.avg, goal);
      const f = el('div', 'ios-fps'); f.appendChild(el('b', '', fmt(g.avg))); f.appendChild(el('small', '', 'avg fps' + (g.method === 'approx' ? ' (approx)' : ''))); c.appendChild(f);
      const b = el('div', 'ios-bar ' + t + (g.screen === 'menus' ? ' menus' : ''));
      b.style.setProperty('--v', Math.min(g.avg, 60) / 60 * 100 + '%'); b.style.setProperty('--gp', Math.min(goal, 60) / 60 * 100 + '%');
      b.setAttribute('role', 'img'); b.setAttribute('aria-label', fmt(g.avg) + ' fps average, goal ' + goal); b.appendChild(el('i')); c.appendChild(b);
      const l = el('div', 'ios-lows');
      [['1% low', g.low1], ['0.1% low', g.low01]].forEach(([k, v]) => { const s = el('span', '', k + ' '); s.appendChild(el('b', '', fmt(v))); l.appendChild(s); });
      c.appendChild(l);
    }
    if (g.what) { const p = el('p', 'ios-what', g.what); if (g.note) p.appendChild(el('em', '', g.note)); c.appendChild(p); }
    if (g.shots && g.shots.length) {
      const box = el('div', 'ios-shots' + (g.shots.length > 3 ? ' many' : ''));
      g.shots.forEach(s => {
        const btn = el('button', 'ios-thumb'); btn.type = 'button'; btn.setAttribute('aria-label', 'Open screenshot: ' + s.c);
        const im = el('img'); im.src = '/assets/ios/img/t/' + s.f; im.alt = s.c; im.loading = 'lazy'; im.width = 360; im.height = 165;
        btn.appendChild(im); btn.addEventListener('click', () => openShot(s)); box.appendChild(btn);
      });
      c.appendChild(box);
    }
    if (g.run) c.appendChild(el('span', 'ios-run', g.run));
    return c;
  }

  function render(d) {
    document.getElementById('ios-updated').textContent = 'Updated ' + d.updated.replace('T', ' ').replace('Z', ' UTC') + ' · ' + d.games.length + ' games';
    const actions = document.getElementById('ios-actions');
    const a = el('a', 'button button-primary', 'Get it on the App Store ↗'); a.href = 'https://apps.apple.com/in/app/bachatas4-emulator/id6822708927'; a.target = '_blank'; a.rel = 'noopener noreferrer'; actions.appendChild(a);
    const counts = document.getElementById('ios-counts'), filters = document.getElementById('ios-filters'), root = document.getElementById('ios-groups');
    const sections = {};
    const filterBtns = [];
    const setFilter = k => { filterBtns.forEach(b => b.setAttribute('aria-pressed', String(b.dataset.k === k))); Object.entries(sections).forEach(([id, s]) => { s.hidden = k !== 'all' && k !== id; }); };
    const mk = (k, label) => { const b = el('button', '', label); b.type = 'button'; b.dataset.k = k; b.addEventListener('click', () => setFilter(k)); filters.appendChild(b); filterBtns.push(b); };
    mk('all', 'All (' + d.games.length + ')');
    d.groups.forEach(gid => {
      const list = d.games.filter(g => g.group === gid);
      if (gid === 'not_tested') list.sort((x, y) => x.queue - y.queue);
      if (!list.length) return;
      const li = el('li', 'k-' + gid); li.appendChild(el('b', '', String(list.length))); li.appendChild(el('span', '', GROUPS[gid])); counts.appendChild(li);
      mk(gid, GROUPS[gid] + ' (' + list.length + ')');
      const sec = el('section', 'ios-group k-' + gid); const h = el('h2', '', GROUPS[gid]); h.appendChild(el('small', '', list.length + (list.length === 1 ? ' game' : ' games'))); sec.appendChild(h);
      const grid = el('div', 'ios-grid'); list.forEach(g => grid.appendChild(card(g))); sec.appendChild(grid);
      root.appendChild(sec); sections[gid] = sec;
    });
    setFilter('all');
  }

  fetch('/assets/ios/games.json', { cache: 'no-cache' })
    .then(r => { if (!r.ok) throw new Error('HTTP ' + r.status); return r.json(); })
    .then(render)
    .catch(e => { document.getElementById('ios-updated').textContent = ''; document.getElementById('ios-status').textContent = 'Results did not load (' + e.message + '). Reload to try again.'; });
})();
