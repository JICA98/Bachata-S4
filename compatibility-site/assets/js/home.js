(() => {
  const list = document.querySelector('#home-game-grid');
  if (!list) return;
  const set = (id, value) => { const el = document.querySelector(id); if (el) el.textContent = value ?? '—'; };
  fetch('/data/site-index.json', { cache:'no-store' })
    .then(response => { if (!response.ok) throw new Error(`HTTP ${response.status}`); return response.json(); })
    .then(index => {
      const stats = index.stats || {};
      set('#stat-games', stats.games); set('#stat-reports', stats.reports); set('#stat-devices', stats.devices); set('#stat-playable', stats.playable); set('#stat-ingame', stats.ingame);
      const games = [...(index.games || [])].sort((a,b) => new Date(b.latestTestedAt || 0) - new Date(a.latestTestedAt || 0)).slice(0,6);
      list.replaceChildren(...games.map(game => window.BachataCards.createGameCard(game)));
      document.querySelector('#home-data-meta').textContent = games.length ? `Showing ${games.length} most recently tested games` : 'No compatibility reports yet';
    })
    .catch(error => {
      console.error(error);
      list.innerHTML = '<div class="empty-state"><h3>Compatibility data could not be loaded</h3><p>Try again after the next site deployment.</p></div>';
      document.querySelector('#home-data-meta').textContent = 'Data unavailable';
    });
})();
