(() => {
  'use strict';

  const themeToggle = document.querySelector('#theme-toggle');

  function setTheme(theme) {
    document.documentElement.dataset.theme = theme;
    localStorage.setItem('bachata-compat-theme', theme);
    if (themeToggle) {
      themeToggle.setAttribute('aria-label', `Switch to ${theme === 'dark' ? 'light' : 'dark'} theme`);
    }
  }

  function initializeTheme() {
    if (!themeToggle) return;
    const saved = localStorage.getItem('bachata-compat-theme');
    const preferred = matchMedia('(prefers-color-scheme: light)').matches ? 'light' : 'dark';
    setTheme(saved || preferred);
    themeToggle.addEventListener('click', () => setTheme(document.documentElement.dataset.theme === 'dark' ? 'light' : 'dark'));
  }

  const navToggle = document.querySelector('#nav-toggle');
  const siteNav = document.querySelector('#site-nav');

  function closeMenu() {
    if (!navToggle) return;
    navToggle.setAttribute('aria-expanded', 'false');
    navToggle.setAttribute('aria-label', 'Open menu');
    siteNav.classList.remove('nav-open');
  }

  function initializeNav() {
    if (!navToggle || !siteNav) return;
    navToggle.addEventListener('click', () => {
      const expanded = navToggle.getAttribute('aria-expanded') === 'true';
      if (expanded) {
        closeMenu();
      } else {
        navToggle.setAttribute('aria-expanded', 'true');
        navToggle.setAttribute('aria-label', 'Close menu');
        siteNav.classList.add('nav-open');
      }
    });
    siteNav.addEventListener('click', event => {
      if (event.target.closest('a')) closeMenu();
    });
    document.addEventListener('keydown', event => {
      if (event.key === 'Escape' && siteNav.classList.contains('nav-open')) {
        closeMenu();
        navToggle.focus();
      }
    });
    document.addEventListener('click', event => {
      if (siteNav.classList.contains('nav-open') && !event.target.closest('.site-header')) closeMenu();
    });
  }

  initializeTheme();
  initializeNav();
})();
