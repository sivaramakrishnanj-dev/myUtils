// ==UserScript==
// @name         LinkedIn Focus Mode
// @namespace    siva-focus
// @version      2.0
// @description  Feed-only: dark terminal theme, text-only posts, hide non-followed/promoted, reveal media/comments on demand.
// @match        https://www.linkedin.com/*
// @run-at       document-start
// @grant        none
// ==/UserScript==

(function () {
  'use strict';

  const STORAGE_KEY = '__siva_li_focus__';
  const STYLE_ID    = '__siva_li_style__';

  const isFeed = () => location.pathname.startsWith('/feed');
  let enabled = localStorage.getItem(STORAGE_KEY) !== 'false';

  // ── Terminal CSS ──────────────────────────────────────────────────────────
  // Override LinkedIn's CSS custom properties at :root so all components
  // that use var(--white) / var(--color-background-*) get the dark theme.
  const TERMINAL_CSS = `
    :root {
      --white: #080d08 !important;
      --black: #00cc44 !important;
      --color-background-container: #080d08 !important;
      --color-background-container-tint: #0a140a !important;
      --color-background-canvas: #050d05 !important;
      --color-background-canvas-tint: #080d08 !important;
      --color-background-scrim: rgba(0,0,0,0.85) !important;
      --color-text-primary: #00cc44 !important;
      --color-text-secondary: #00aa33 !important;
      --color-text-low-emphasis: #007722 !important;
      --color-border-faint: #0f2a0f !important;
      --color-border-low-emphasis: #1a3a1a !important;
    }

    html, body {
      background: #080d08 !important;
      color: #00cc44 !important;
    }

    /* Catch any element still using a hardcoded white/light background */
    * {
      background-color: transparent !important;
    }

    /* Restore dark backgrounds for structural containers */
    body,
    #main, #workspace,
    .scaffold-layout,
    .scaffold-layout__main,
    .scaffold-layout__content,
    .scaffold-layout__aside,
    .global-nav,
    header {
      background-color: #080d08 !important;
    }

    /* Post cards — direct children of mainFeed */
    [data-testid="mainFeed"] > div {
      background-color: #0a140a !important;
      border: 1px solid #0f2a0f !important;
      border-radius: 4px !important;
      margin-bottom: 8px !important;
    }

    /* All text green */
    * { color: #00cc44 !important; }
    a, a:visited { color: #00ff55 !important; }

    /* Nav */
    .global-nav { border-bottom: 1px solid #0f2a0f !important; }

    /* Inputs */
    input, textarea, [contenteditable] {
      background-color: #0a140a !important;
      border-color: #0f2a0f !important;
    }

    /* Images hidden by default — script adds reveal buttons */
    [data-testid="mainFeed"] img {
      opacity: 0 !important;
      max-height: 0 !important;
      overflow: hidden !important;
      pointer-events: none !important;
    }

    /* Scrollbar */
    ::-webkit-scrollbar { width: 5px; background: #080d08; }
    ::-webkit-scrollbar-thumb { background: #0f2a0f; border-radius: 3px; }

    /* Sidebar noise */
    .scaffold-layout__aside .artdeco-card ~ .artdeco-card,
    [data-ad-banner], .ad-banner-container {
      display: none !important;
    }

    /* Focus reveal buttons */
    .siva-reveal-btn {
      display: inline-block;
      background: #080d08 !important;
      color: #00cc44 !important;
      border: 1px solid #0f2a0f !important;
      padding: 3px 10px;
      margin: 6px 0;
      border-radius: 2px;
      cursor: pointer;
      font-size: 11px;
      font-family: monospace;
      letter-spacing: 0.5px;
    }
    .siva-reveal-btn:hover {
      background: #0f2a0f !important;
      border-color: #00cc44 !important;
    }

    /* Hidden post placeholder */
    .siva-hidden-post {
      padding: 6px 14px;
      margin: 4px 0;
      border: 1px dashed #0f2a0f !important;
      background: #080d08 !important;
      font-size: 11px;
      font-family: monospace;
      color: #1a5c1a !important;
    }
  `;

  function injectStyle() {
    if (document.getElementById(STYLE_ID)) return;
    const s = document.createElement('style');
    s.id = STYLE_ID;
    s.textContent = TERMINAL_CSS;
    (document.head || document.documentElement).appendChild(s);
  }

  function removeStyle() {
    const el = document.getElementById(STYLE_ID);
    if (el) el.remove();
  }

  // ── Get the direct-child post container of mainFeed ───────────────────────
  // Each post is a direct child div of [data-testid="mainFeed"]
  function getPostContainer(el) {
    const feed = document.querySelector('[data-testid="mainFeed"]');
    if (!feed) return null;
    let node = el;
    while (node && node.parentElement !== feed) {
      node = node.parentElement;
    }
    return (node && node.parentElement === feed) ? node : null;
  }

  // ── Detect non-followed / promoted posts ─────────────────────────────────
  function isNonFollowed(postDiv) {
    // "X likes/commented on/reacted to/shared this" — a connection engaged with it, keep it
    const text = postDiv.textContent;
    if (text.includes('commented on this') ||
        text.includes('likes this') ||
        text.includes('reacted to this') ||
        text.includes('shared this') ||
        text.includes('celebrated this') ||
        text.includes('supports this') ||
        text.includes('finds this insightful') ||
        text.includes('finds this funny')) return false;

    // Contains the text "Promoted" in a leaf element
    const leaves = postDiv.querySelectorAll('p, span');
    for (const el of leaves) {
      if (el.childElementCount === 0 && el.textContent.trim() === 'Promoted') return true;
    }

    // Has a "Follow <name>" button → original author is not followed
    if (postDiv.querySelector('button[aria-label^="Follow "]')) return true;

    return false;
  }

  // ── Process a single post container ──────────────────────────────────────
  function processPost(postDiv) {
    if (postDiv.dataset.sivaProcessed) return;
    postDiv.dataset.sivaProcessed = '1';

    if (isNonFollowed(postDiv)) {
      const ph = document.createElement('div');
      ph.className = 'siva-hidden-post';
      ph.textContent = '[ promoted / recommended post — hidden ]';
      postDiv.style.display = 'none';
      postDiv.parentNode?.insertBefore(ph, postDiv);
      return;
    }

    // ── Hide media ────────────────────────────────────────────────────────
    // Video: [data-vjs-player="true"] and its wrapper
    postDiv.querySelectorAll('[data-vjs-player="true"]').forEach(el => {
      const wrapper = el.closest('[data-color-scheme]') || el.parentElement;
      hideWithButton(wrapper || el, '[ + show video ]', () => {
        // Restore images inside
        wrapper.querySelectorAll('img').forEach(img => img.style.cssText = '');
      });
    });

    // Images (non-video): img tags not already inside a video wrapper
    postDiv.querySelectorAll('img').forEach(img => {
      if (img.closest('[data-vjs-player]') || img.dataset.sivaHidden) return;
      // Only hide post images, not avatars (avatars are small, < 100px)
      if (img.width && img.width < 80) return;
      hideWithButton(img, '[ + show image ]', () => {
        img.style.cssText = '';
      });
    });

    // Articles / link previews
    postDiv.querySelectorAll('[data-testid*="article"], .feed-shared-article').forEach(el => {
      hideWithButton(el, '[ + show article preview ]');
    });

    // ── Hide comments ─────────────────────────────────────────────────────
    postDiv.querySelectorAll('[data-testid*="commentList"]').forEach(el => {
      hideWithButton(el, '[ + show comments ]');
    });

    // ── Hide reaction/like counts ─────────────────────────────────────────
    postDiv.querySelectorAll('[aria-label*="reaction"], [aria-label*="like"], [aria-label*="comment"]')
      .forEach(el => {
        if (el.tagName === 'BUTTON') return; // keep action buttons
        el.style.display = 'none';
      });
  }

  function hideWithButton(el, label, onReveal) {
    if (!el || el.dataset.sivaHidden) return;
    el.dataset.sivaHidden = '1';
    el.style.display = 'none';
    const btn = document.createElement('button');
    btn.className = 'siva-reveal-btn';
    btn.textContent = label;
    btn.onclick = () => {
      el.style.display = '';
      if (onReveal) onReveal();
      btn.remove();
    };
    el.parentNode?.insertBefore(btn, el);
  }

  // ── Process all posts ─────────────────────────────────────────────────────
  function processFeed() {
    if (!isFeed() || !enabled) return;
    const feed = document.querySelector('[data-testid="mainFeed"]');
    if (!feed) return;
    // Direct children of mainFeed are the post containers
    feed.querySelectorAll(':scope > div').forEach(processPost);
  }

  // ── MutationObserver for infinite scroll ─────────────────────────────────
  let observer = null;

  function startObserver() {
    if (observer) return;
    observer = new MutationObserver(processFeed);
    observer.observe(document.body, { childList: true, subtree: true });
  }

  function stopObserver() {
    if (observer) { observer.disconnect(); observer = null; }
  }

  // ── Apply / remove focus state ────────────────────────────────────────────
  function applyState() {
    if (enabled && isFeed()) {
      injectStyle();
      processFeed();
      startObserver();
    } else {
      removeStyle();
      stopObserver();
    }
    updateButton();
  }

  // ── Toggle button ─────────────────────────────────────────────────────────
  let btn = null;

  function createButton() {
    if (btn || !document.body) return;
    btn = document.createElement('button');
    btn.id = '__siva_li_btn__';
    Object.assign(btn.style, {
      position: 'fixed', bottom: '20px', right: '20px', zIndex: '99999',
      padding: '8px 14px', border: '1px solid #00cc44', borderRadius: '4px',
      cursor: 'pointer', fontSize: '12px', fontWeight: '600',
      fontFamily: 'monospace', boxShadow: '0 2px 8px rgba(0,0,0,0.5)',
      transition: 'all 0.2s'
    });
    btn.addEventListener('click', () => {
      enabled = !enabled;
      localStorage.setItem(STORAGE_KEY, enabled);
      if (!enabled) { location.reload(); return; }
      applyState();
    });
    document.body.appendChild(btn);
    updateButton();
  }

  function updateButton() {
    if (!btn) return;
    if (enabled && isFeed()) {
      btn.textContent = '🟢 Focus: ON';
      btn.style.background = '#0a140a';
      btn.style.color = '#00cc44';
    } else {
      btn.textContent = '⚪ Focus: OFF';
      btn.style.background = '#ccc';
      btn.style.color = '#333';
      btn.style.borderColor = '#ccc';
    }
  }

  // ── Early CSS injection to prevent flash ─────────────────────────────────
  if (enabled && isFeed()) {
    const early = document.createElement('style');
    early.id = STYLE_ID;
    early.textContent = TERMINAL_CSS;
    document.documentElement.appendChild(early);
  }

  function onReady() {
    createButton();
    applyState();
  }

  if (document.body) onReady();
  else document.addEventListener('DOMContentLoaded', onReady);

  // ── LinkedIn SPA navigation ───────────────────────────────────────────────
  const _push = history.pushState.bind(history);
  history.pushState = function (...args) {
    _push(...args);
    setTimeout(applyState, 400);
  };
  window.addEventListener('popstate', () => setTimeout(applyState, 400));

  setInterval(() => { if (!btn && document.body) createButton(); }, 2000);
})();
