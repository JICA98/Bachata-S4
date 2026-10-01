(() => {
  const list = document.querySelector('#home-game-grid');
  if (!list) return;
  const set = (id, value) => { const el = document.querySelector(id); if (el) el.textContent = value ?? '—'; };
  fetch('/data/site-index.json', { cache:'no-store' })
    .then(response => { if (!response.ok) throw new Error(`HTTP ${response.status}`); return response.json(); })
    .then(index => {
      const stats = index.stats || {};
      set('#stat-games', stats.games); set('#stat-reports', stats.reports); set('#stat-devices', stats.devices); set('#stat-playable', stats.playable); set('#stat-ingame', stats.ingame);
      const all = index.games || [];
      const working = all.filter(window.BachataCards.isWorking);
      // Showcase what runs: famous playable and ingame titles first, everything else only if nothing runs yet.
      const games = [...(working.length ? working : all)].sort(window.BachataCards.featuredCompare).slice(0, 8);
      list.replaceChildren(...games.map((game, i) => window.BachataCards.createGameCard(game, null, i)));
      document.querySelector('#home-data-meta').textContent = working.length
        ? `${working.length} of ${all.length} tested games reach gameplay`
        : games.length ? 'Recently tested games' : 'No compatibility reports yet';
    })
    .catch(error => {
      console.error(error);
      list.innerHTML = '<div class="empty-state"><h3>Compatibility data could not be loaded</h3><p>Try again after the next site deployment.</p></div>';
      document.querySelector('#home-data-meta').textContent = 'Data unavailable';
    });
})();

(() => {
  const root = document.querySelector('#release-carousel');
  if (!root) return;
  const RELEASES_API = 'https://api.github.com/repos/JICA98/Bachata-S4/releases?per_page=10';
  const el = (tag, className, text) => {
    const node = document.createElement(tag);
    if (className) node.className = className;
    if (text !== undefined) node.textContent = text;
    return node;
  };

  // Inline markdown: **bold**, `code` and [text](https://…) links. Built from text nodes, never innerHTML.
  function inline(parent, text) {
    const pattern = /\*\*([^*]+)\*\*|`([^`]+)`|\[([^\]]+)\]\((https:\/\/[^)\s]+)\)/g;
    let last = 0;
    for (const m of text.matchAll(pattern)) {
      parent.append(text.slice(last, m.index));
      if (m[1] !== undefined) parent.append(el('strong', '', m[1]));
      else if (m[2] !== undefined) parent.append(el('code', '', m[2]));
      else {
        const a = el('a', '', m[3]);
        a.href = m[4]; a.target = '_blank'; a.rel = 'noopener noreferrer';
        parent.append(a);
      }
      last = m.index + m[0].length;
    }
    parent.append(text.slice(last));
    return parent;
  }

  /** Minimal markdown for release notes: headings, bullet lists and paragraphs. */
  function renderMarkdown(source, release) {
    const out = document.createDocumentFragment();
    let listEl = null;
    let para = [];
    const flush = () => {
      if (para.length) out.append(inline(el('p'), para.join(' ')));
      para = [];
    };
    const lines = String(source || '').replace(/\r/g, '').split('\n');
    lines.forEach((raw, i) => {
      const line = raw.trim();
      const heading = line.match(/^(#{1,6})\s+(.*)$/);
      const bullet = line.match(/^[-*+]\s+(.*)$/);
      if (heading) {
        flush(); listEl = null;
        // The first heading often repeats the release name; skip it.
        if (i === 0 && heading[1].length === 1 && release.name && heading[2].includes(release.version)) return;
        out.append(inline(el(heading[1].length <= 2 ? 'h4' : 'h5'), heading[2]));
      } else if (bullet) {
        flush();
        if (!listEl) { listEl = el('ul'); out.append(listEl); }
        listEl.append(inline(el('li'), bullet[1]));
      } else if (!line) {
        flush(); listEl = null;
      } else {
        listEl = null;
        para.push(line);
      }
    });
    flush();
    return out;
  }

  const normalize = r => ({
    tag: r.tag_name || r.tag,
    version: String(r.tag_name || r.tag || '').replace(/^v/i, ''),
    name: r.name || r.tag_name || r.tag,
    publishedAt: r.published_at || r.publishedAt,
    url: r.html_url || r.url,
    body: r.body || '',
    apk: r.apk || (r.assets || []).find(a => /\.apk$/i.test(a.name || ''))?.browser_download_url || '',
    prerelease: Boolean(r.prerelease),
    draft: Boolean(r.draft),
  });

  function render(releases) {
    let current = 0;
    const slides = el('div', 'release-slides');
    const dots = el('div', 'release-dots');
    const counter = el('span', 'release-counter');
    const prev = el('button', 'icon-button release-nav', '←');
    const next = el('button', 'icon-button release-nav', '→');
    prev.type = next.type = 'button';
    prev.setAttribute('aria-label', 'Previous release');
    next.setAttribute('aria-label', 'Next release');

    releases.forEach((release, i) => {
      const slide = el('article', 'release-slide');
      slide.setAttribute('role', 'group');
      slide.setAttribute('aria-roledescription', 'slide');
      slide.setAttribute('aria-label', `${i + 1} of ${releases.length}: ${release.name}`);
      const head = el('header', 'release-head');
      const meta = el('div', 'release-meta');
      meta.append(el('span', `release-tag${i === 0 ? ' latest' : ''}`, i === 0 ? `Latest · ${release.tag}` : release.tag));
      if (release.prerelease) meta.append(el('span', 'release-tag', 'Pre-release'));
      if (release.publishedAt) {
        const time = el('time', 'muted small', new Date(release.publishedAt).toLocaleDateString(undefined, { year:'numeric', month:'short', day:'numeric' }));
        time.dateTime = release.publishedAt;
        meta.append(time);
      }
      head.append(meta, el('h3', '', release.name));
      const notes = el('div', 'release-notes');
      notes.append(renderMarkdown(release.body, release));
      if (!notes.childNodes.length) notes.append(el('p', 'muted', 'No changelog was published for this release.'));
      const actions = el('div', 'release-actions');
      if (release.apk) {
        const apk = el('a', 'pill-button button-primary', 'Download APK');
        apk.href = release.apk; apk.rel = 'noopener noreferrer';
        actions.append(apk);
      }
      if (release.url) {
        const gh = el('a', 'pill-button', 'Release on GitHub ↗');
        gh.href = release.url; gh.target = '_blank'; gh.rel = 'noopener noreferrer';
        actions.append(gh);
      }
      slide.append(head, notes, actions);
      slide.hidden = i !== 0;
      slides.append(slide);

      const dot = el('button', 'release-dot');
      dot.type = 'button';
      dot.setAttribute('aria-label', `Show ${release.name}`);
      dot.addEventListener('click', () => show(i));
      dots.append(dot);
    });

    function show(i) {
      current = (i + releases.length) % releases.length;
      [...slides.children].forEach((slide, n) => {
        slide.hidden = n !== current;
        if (n === current) slide.querySelector('.release-notes').scrollTop = 0;
      });
      [...dots.children].forEach((dot, n) => dot.setAttribute('aria-current', n === current ? 'true' : 'false'));
      counter.textContent = `${current + 1} / ${releases.length}`;
      prev.disabled = releases.length < 2;
      next.disabled = releases.length < 2;
    }
    prev.addEventListener('click', () => show(current - 1));
    next.addEventListener('click', () => show(current + 1));
    root.addEventListener('keydown', event => {
      if (event.target.closest('a')) return;
      if (event.key === 'ArrowLeft') show(current - 1);
      if (event.key === 'ArrowRight') show(current + 1);
    });

    const controls = el('div', 'release-controls');
    controls.append(prev, dots, counter, next);
    root.replaceChildren(slides, controls);
    show(0);
  }

  const load = url => fetch(url, { headers: { Accept: 'application/vnd.github+json' } })
    .then(r => { if (!r.ok) throw new Error(`HTTP ${r.status}`); return r.json(); })
    .then(data => (Array.isArray(data) ? data : data.releases || []).map(normalize).filter(r => r.tag && !r.draft));

  // Live from GitHub so a new release shows immediately; the build-time copy covers API rate limits.
  load(RELEASES_API)
    .catch(() => load('/data/releases.json'))
    .then(releases => {
      if (!releases.length) throw new Error('No releases');
      render(releases.sort((a, b) => new Date(b.publishedAt || 0) - new Date(a.publishedAt || 0)));
    })
    .catch(error => {
      console.error(error);
      root.closest('section')?.setAttribute('hidden', '');
    });
})();
