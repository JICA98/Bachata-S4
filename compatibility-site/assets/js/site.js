(() => {
  const root = document.documentElement;
  const saved = localStorage.getItem('bachata-theme');
  if (saved === 'light' || saved === 'dark') root.dataset.theme = saved;

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
            <span class="brand-mark" aria-hidden="true">B4</span>
            <span><strong>Bachata S4</strong><small>PS4 emulation on Android</small></span>
          </a>
          <nav class="site-nav" id="site-nav" aria-label="Primary">
            ${nav.map(([id, href, label]) => `<a class="nav-link" href="${href}" ${current === id ? 'aria-current="page"' : ''}>${label}</a>`).join('')}
            <a class="nav-link" href="https://github.com/JICA98/Bachata-S4" target="_blank" rel="noreferrer">GitHub ↗</a>
          </nav>
          <div class="header-actions">
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
          <div class="brand"><span class="brand-mark" aria-hidden="true">B4</span><span><strong>Bachata S4</strong><small>Evidence-based compatibility</small></span></div>
          <div>
            <nav class="footer-nav" aria-label="Footer">
              <a href="/compatibility.html">Compatibility</a><a href="/guide.html">Guide</a><a href="/faq.html">FAQ</a><a href="/privacy.html">Privacy</a><a href="/terms.html">Terms</a>
            </nav>
            <p class="footer-copy">Bachata S4 is an independent open-source project and is not affiliated with Sony Interactive Entertainment. Use only software and content you are legally entitled to use.</p>
          </div>
        </div>
      </footer>`;
  }

  document.querySelector('#theme-toggle')?.addEventListener('click', () => {
    const next = root.dataset.theme === 'light' ? 'dark' : 'light';
    root.dataset.theme = next;
    localStorage.setItem('bachata-theme', next);
  });
  const menu = document.querySelector('#site-nav');
  document.querySelector('#nav-toggle')?.addEventListener('click', (event) => {
    const open = menu?.classList.toggle('open');
    event.currentTarget.setAttribute('aria-expanded', open ? 'true' : 'false');
  });
})();
