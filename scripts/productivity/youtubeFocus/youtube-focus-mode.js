// ==UserScript==
// @name         YouTube Focus Mode
// @namespace    siva-focus
// @version      2.0
// @description  Hide distracting recommendations everywhere. Show only search results and exempt pages. Toggle with floating button.
// @match        https://*.youtube.com/*
// @match        https://youtube.com/*
// @run-at       document-start
// @grant        none
// ==/UserScript==

(function () {
  'use strict';

  const STORAGE_KEY = '__siva_focus_enabled__';
  const STYLE_ID = '__siva_focus_style__';
  const EXEMPT_PREFIXES = ['/feed/playlists', '/playlist', '/feed/library', '/feed/history', '/feed/subscriptions', '/feed/channels', '/@'];

  const isExempt = () => EXEMPT_PREFIXES.some(p => location.pathname.startsWith(p));
  const isSearch = () => location.pathname === '/results';
  const isWatch = () => location.pathname === '/watch';
  const isHome = () => location.pathname === '/' || location.pathname === '/feed/trending';

  let enabled = localStorage.getItem(STORAGE_KEY) !== 'false'; // default ON

  // CSS rules that hide distracting content
  const FOCUS_CSS = `
    /* Homepage: hide the entire feed */
    ytd-browse[page-subtype="home"] ytd-rich-grid-renderer,
    ytd-browse[page-subtype="home"] #contents,
    ytd-browse[page-subtype="trending"] #contents,
    /* Homepage chips/filter bar */
    ytd-browse[page-subtype="home"] ytd-feed-filter-chip-bar-renderer,
    ytd-browse[page-subtype="home"] #chips-wrapper,
    /* Shorts shelves everywhere */
    ytd-reel-shelf-renderer,
    ytd-rich-shelf-renderer[is-shorts],
    /* Watch page: sidebar recommendations */
    ytd-watch-flexy #secondary,
    ytd-watch-flexy #related,
    ytd-watch-next-secondary-results-renderer,
    /* Watch page: autoplay */
    ytd-compact-autoplay-renderer,
    /* Watch page: end screen cards & overlays */
    .ytp-endscreen-content,
    .ytp-ce-element,
    .videowall-endscreen,
    .ytp-suggestion-set,
    /* Watch page: info cards */
    .ytp-cards-teaser,
    /* "Up next" chip on player */
    .ytp-autonav-endscreen-upnext-container {
      display: none !important;
    }

    /* Show a gentle message on homepage */
    ytd-browse[page-subtype="home"] #header::after {
      content: "🎯 Focus Mode — Use the search bar to find what you need.";
      display: block;
      text-align: center;
      padding: 60px 20px;
      font-size: 18px;
      color: #888;
    }
  `;

  function injectStyle() {
    if (document.getElementById(STYLE_ID)) return;
    const style = document.createElement('style');
    style.id = STYLE_ID;
    style.textContent = FOCUS_CSS;
    (document.head || document.documentElement).appendChild(style);
  }

  function removeStyle() {
    const el = document.getElementById(STYLE_ID);
    if (el) el.remove();
  }

  function applyState() {
    if (enabled && !isExempt() && !isSearch()) {
      injectStyle();
    } else {
      removeStyle();
    }
    updateButton();
  }

  // --- Toggle Button ---
  let btn = null;

  function createButton() {
    if (btn) return;
    btn = document.createElement('button');
    btn.id = '__siva_focus_btn__';
    Object.assign(btn.style, {
      position: 'fixed', bottom: '20px', right: '20px', zIndex: '99999',
      padding: '8px 14px', border: 'none', borderRadius: '20px',
      cursor: 'pointer', fontSize: '13px', fontWeight: '600',
      boxShadow: '0 2px 8px rgba(0,0,0,0.3)', transition: 'all 0.2s',
      fontFamily: 'Roboto, Arial, sans-serif'
    });
    btn.addEventListener('click', () => {
      enabled = !enabled;
      localStorage.setItem(STORAGE_KEY, enabled);
      applyState();
    });
    document.body.appendChild(btn);
    updateButton();
  }

  function updateButton() {
    if (!btn) return;
    if (enabled) {
      btn.textContent = '🎯 Focus: ON';
      btn.style.background = '#065fd4';
      btn.style.color = '#fff';
    } else {
      btn.textContent = '😴 Focus: OFF';
      btn.style.background = '#ccc';
      btn.style.color = '#333';
    }
  }

  // --- Init & SPA handling ---
  // Inject CSS immediately at document-start to prevent flash of content
  if (enabled && !isExempt() && !isSearch()) {
    const earlyStyle = document.createElement('style');
    earlyStyle.id = STYLE_ID;
    earlyStyle.textContent = FOCUS_CSS;
    document.documentElement.appendChild(earlyStyle);
  }

  function onReady() {
    createButton();
    applyState();
  }

  if (document.body) onReady();
  else document.addEventListener('DOMContentLoaded', onReady);

  // YouTube SPA navigation
  document.addEventListener('yt-navigate-finish', () => applyState());
  document.addEventListener('yt-page-data-updated', () => applyState());

  // Fallback: re-check periodically for late-loading elements
  setInterval(() => {
    if (!btn && document.body) createButton();
  }, 2000);
})();