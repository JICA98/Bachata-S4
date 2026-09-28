(() => {
  const root = document.documentElement;
  let saved = null;
  try { saved = localStorage.getItem('bachata-theme'); } catch (_) {}
  if (saved === 'light' || saved === 'dark') root.dataset.theme = saved;

  const PLAY_URL = 'https://play.google.com/store/apps/details?id=com.bachatas4.android';
  const playIcon = '<svg viewBox="0 0 24 24" aria-hidden="true"><path fill="#34a853" d="M3.6 1.8 13.8 12 3.6 22.2c-.4-.2-.6-.7-.6-1.2V3c0-.5.2-1 .6-1.2z"/><path fill="#fbbc04" d="m17.3 8.5-3.5 3.5 3.5 3.5 4-2.3c.9-.5.9-1.9 0-2.4z"/><path fill="#4285f4" d="M3.6 22.2 13.8 12l3.5 3.5L5 22.4c-.5.3-1 .2-1.4-.2z"/><path fill="#ea4335" d="M3.6 1.8c.4-.4.9-.5 1.4-.2l12.3 6.9-3.5 3.5z"/></svg>';
  const playButton = extra => `<a class="button button-play ${extra}" href="${PLAY_URL}" target="_blank" rel="noopener noreferrer" aria-label="Get Bachata S4 on Google Play">${playIcon}<span><small>Get it on</small><strong>Google Play</strong></span></a>`;
  window.BachataPlayButton = playButton;

  const current = document.body.dataset.page || '';
  const nav = [
    ['home', '/', 'Home'],
    ['compatibility', '/compatibility.html', 'Compatibility'],
    ['updates', '/updates.html', 'Updates'],
    ['methodology', '/methodology.html', 'How Testing Works'],
    ['about', '/about.html', 'About'],
    ['contact', '/contact.html', 'Contact']
  ];

  const header = document.querySelector('[data-site-header]');
  if (header) {
    header.innerHTML = `
      <a class="skip-link" href="#main">Skip to content</a>
      <header class="site-header">
        <div class="container header-inner">
          <a class="brand" href="/" aria-label="Bachata S4 home">
            <img class="brand-logo" src="/assets/bachata-s4-logo.png" alt="Bachata S4 logo" width="42" height="42">
            <span><strong>Bachata S4</strong><small>PS4 emulation on Android</small></span>
          </a>
          <nav class="site-nav" id="site-nav" aria-label="Primary">
            ${nav.map(([id, href, label]) => `<a class="nav-link" href="${href}" ${current === id ? 'aria-current="page"' : ''}>${label}</a>`).join('')}
            <a class="nav-link" href="https://github.com/JICA98/Bachata-S4" target="_blank" rel="noreferrer">GitHub ↗</a>
            ${playButton('nav-play')}
          </nav>
          <div class="header-actions">
            ${playButton('')}
            <button class="icon-button" id="theme-toggle" type="button" aria-label="Switch theme">◐</button>
            <button class="icon-button nav-toggle" id="nav-toggle" type="button" aria-expanded="false" aria-controls="site-nav" aria-label="Open menu">☰</button>
          </div>
        </div>
      </header>`;
  }

  const footer = document.querySelector('[data-site-footer]');
  if (footer) {
    footer.innerHTML = `
      <footer class="site-footer">
        <div class="container footer-inner">
          <div class="brand"><img class="brand-logo" src="/assets/bachata-s4-logo.png" alt="Bachata S4 logo" width="38" height="38"><span><strong>Bachata S4</strong><small>Evidence-based compatibility</small></span></div>
          <div>
            <nav class="footer-nav" aria-label="Footer">
              <a href="/compatibility.html">Compatibility</a><a href="/guide.html">Guide</a><a href="/faq.html">FAQ</a><a href="/privacy.html">Privacy</a><a href="/terms.html">Terms</a><a href="${PLAY_URL}" target="_blank" rel="noopener noreferrer">Google Play ↗</a><a href="https://www.profitableratecpmnetwork.com/h7iu4ucf5?key=629d0734aa9635e65f0197352ff2748f" target="_blank" rel="noopener noreferrer">Partner Offers ↗</a>
            </nav>
            <p class="footer-copy">Bachata S4 is an independent open-source project and is not affiliated with Sony Interactive Entertainment. Use only software and content you are legally entitled to use. Google Play and the Google Play logo are trademarks of Google LLC.</p>
          </div>
        </div>
      </footer>`;
  }

  document.querySelector('#theme-toggle')?.addEventListener('click', () => {
    const next = root.dataset.theme === 'light' ? 'dark' : 'light';
    root.dataset.theme = next;
    try { localStorage.setItem('bachata-theme', next); } catch (_) {}
  });
  const menu = document.querySelector('#site-nav');
  document.querySelector('#nav-toggle')?.addEventListener('click', (event) => {
    const open = menu?.classList.toggle('open');
    event.currentTarget.setAttribute('aria-expanded', open ? 'true' : 'false');
  });

  // Nova ambient backdrop: drifting blurred accent glows plus rising, twinkling particles,
  // the same recipe as the app's ConsoleBackdrop/ParticleField.
  const backdrop = document.createElement('div');
  backdrop.className = 'nova-backdrop';
  backdrop.setAttribute('aria-hidden', 'true');
  const canvas = document.createElement('canvas');
  backdrop.append(canvas);
  document.body.prepend(backdrop);
  const ctx = canvas.getContext('2d');
  const reduceMotion = window.matchMedia('(prefers-reduced-motion: reduce)').matches;
  const accent = [232, 163, 61];
  const secondary = [120, 96, 220];
  const particles = Array.from({ length: 36 }, () => ({
    x: Math.random(), y: Math.random(), r: 0.8 + Math.random() * 1.9,
    speed: 0.04 + Math.random() * 0.06, tw: 0.4 + Math.random() * 1.6,
    phase: Math.random() * 6.28, drift: 0.2 + Math.random() * 0.7,
  }));
  const smooth = (a, b, x) => { const t = Math.min(1, Math.max(0, (x - a) / (b - a))); return t * t * (3 - 2 * t); };
  let w = 0, h = 0, dpr = 1;
  const resize = () => {
    dpr = Math.min(window.devicePixelRatio || 1, 2);
    w = window.innerWidth; h = window.innerHeight;
    canvas.width = Math.round(w * dpr); canvas.height = Math.round(h * dpr);
  };
  resize();
  window.addEventListener('resize', resize);
  const start = performance.now();
  const draw = now => {
    const t = (now - start) / 1000;
    ctx.setTransform(dpr, 0, 0, dpr, 0, 0);
    if (root.dataset.theme === 'light') ctx.clearRect(0, 0, w, h);
    else { ctx.fillStyle = '#04060b'; ctx.fillRect(0, 0, w, h); }
    const phase = (t / 24) * Math.PI * 2;
    [[accent, .24], [accent, .14], [secondary, .12]].forEach(([c, a], i) => {
      const cx = w * (0.5 + 0.2 * Math.sin(phase + i * 2.1));
      const cy = h * (0.5 + 0.2 * Math.cos(phase + i * 1.4));
      const radius = Math.max(w, h) * (0.38 + 0.12 * i);
      const g = ctx.createRadialGradient(cx, cy, 0, cx, cy, radius);
      g.addColorStop(0, `rgba(${c[0]},${c[1]},${c[2]},${a})`);
      g.addColorStop(1, 'rgba(0,0,0,0)');
      ctx.fillStyle = g;
      ctx.fillRect(0, 0, w, h);
    });
    particles.forEach(p => {
      const yy = (((p.y - t * p.speed) % 1) + 1) % 1;
      const xx = (((p.x + Math.sin(t * p.drift + p.phase) * 0.008) % 1) + 1) % 1;
      const twinkle = 0.42 * (0.4 + 0.6 * Math.max(0, Math.sin(t * p.tw + p.phase)));
      const alpha = twinkle * smooth(1, 0.75, yy) * smooth(0, 0.12, yy) * 0.85;
      ctx.beginPath();
      ctx.fillStyle = `rgba(${accent[0]},${accent[1]},${accent[2]},${alpha})`;
      ctx.arc(w * xx, h * yy, p.r, 0, Math.PI * 2);
      ctx.fill();
    });
    if (!reduceMotion) requestAnimationFrame(draw);
  };
  requestAnimationFrame(draw);

  // Full-screen screenshot viewer for report strips.
  document.addEventListener('click', event => {
    const shot = event.target.closest?.('.shot');
    if (!shot) return;
    event.preventDefault();
    const viewer = document.createElement('div');
    viewer.className = 'shot-viewer';
    viewer.setAttribute('role', 'dialog');
    viewer.setAttribute('aria-label', 'Screenshot');
    const figure = document.createElement('figure');
    const img = document.createElement('img');
    img.src = shot.getAttribute('href');
    img.alt = shot.dataset.caption || 'Compatibility screenshot';
    const caption = document.createElement('p');
    caption.textContent = shot.dataset.caption || '';
    figure.append(img, caption);
    viewer.append(figure);
    const close = () => { viewer.remove(); document.removeEventListener('keydown', onKey); };
    const onKey = e => { if (e.key === 'Escape') close(); };
    viewer.addEventListener('click', close);
    document.addEventListener('keydown', onKey);
    document.body.append(viewer);
  });
})();
