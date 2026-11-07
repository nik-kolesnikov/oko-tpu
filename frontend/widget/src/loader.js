/*
 * ОКО ТПУ — loader.js (виджет v1).
 * Вставляется на чужой сайт одной строкой:
 *   <script async src="https://oko.tpu.ru/widget/v1/loader.js" data-key="pk_..."></script>
 *
 * Правила (frontend/CLAUDE.md, раздел «Виджет»):
 * - ничего не делаем до события load и не блокируем рендер страницы;
 * - в глобальную область ничего не кладём;
 * - из DOM хоста трогаем только свой контейнер в конце body;
 * - при любой ошибке молча убираем контейнер: ни alert, ни console.error.
 */
(function () {
  'use strict';

  var script = document.currentScript;
  if (!script) return;
  var key = script.getAttribute('data-key');
  if (!key) return;

  var ROOT_ID = 'oko-widget-root';
  var FRAME_LOAD_TIMEOUT = 2000; // iframe должен загрузиться и сказать «hello»
  var READY_TIMEOUT = 2500; // после hello iframe получает настройки (у него свой таймаут 2 с)
  var DAY = 86400000;

  var base;
  try {
    base = new URL(script.src).origin;
  } catch (e) {
    return;
  }
  var serviceId = script.getAttribute('data-service-id');
  var version = script.getAttribute('data-version');
  var storeKey = 'oko:' + key.slice(0, 24);

  // В localStorage сайта хоста — только идентификатор браузера и дата последнего показа.
  function readState() {
    try {
      return JSON.parse(window.localStorage.getItem(storeKey)) || {};
    } catch (e) {
      return {};
    }
  }
  function writeState(s) {
    try {
      window.localStorage.setItem(storeKey, JSON.stringify(s));
    } catch (e) {
      /* хранилище недоступно (приватный режим) — просто работаем без него */
    }
  }

  function uuid() {
    if (window.crypto && typeof window.crypto.randomUUID === 'function') return window.crypto.randomUUID();
    // randomUUID есть только в защищённом контексте (https/localhost) — запасной вариант по RFC 4122.
    var b = new Uint8Array(16);
    window.crypto.getRandomValues(b);
    b[6] = (b[6] & 0x0f) | 0x40;
    b[8] = (b[8] & 0x3f) | 0x80;
    var h = '';
    for (var i = 0; i < 16; i++) h += (b[i] + 0x100).toString(16).slice(1);
    return h.slice(0, 8) + '-' + h.slice(8, 12) + '-' + h.slice(12, 16) + '-' + h.slice(16, 20) + '-' + h.slice(20);
  }

  // Стили ставим инлайном с !important, чтобы правила сайта вроде `iframe { width: 100% }` нас не ломали.
  function css(el, props) {
    for (var p in props) el.style.setProperty(p, props[p], 'important');
  }

  function start() {
    if (document.getElementById(ROOT_ID) || !document.body) return;

    var state = readState();
    if (state.last && state.days && Date.now() - state.last < state.days * DAY) return; // недавно спрашивали
    if (!state.id) {
      state.id = uuid();
      writeState(state);
    }

    var root = document.createElement('div');
    root.id = ROOT_ID;
    var frame = document.createElement('iframe');
    frame.title = 'Оценка сервиса';
    frame.setAttribute('loading', 'eager');
    frame.src = base + '/api/v1/widget/frame?key=' + encodeURIComponent(key) + (serviceId ? '&serviceId=' + encodeURIComponent(serviceId) : '');
    css(root, { position: 'fixed', bottom: '0', right: '0', 'z-index': '2147483000', width: '0', height: '0', overflow: 'visible' });
    css(frame, {
      position: 'fixed', bottom: '12px', right: '12px', width: 'min(372px, calc(100vw - 24px))', height: '0',
      border: '0', margin: '0', padding: '0', background: 'transparent', 'color-scheme': 'normal',
      visibility: 'hidden', 'max-width': 'none', 'max-height': 'none', display: 'block',
    });
    root.appendChild(frame);

    var helloTimer = setTimeout(remove, FRAME_LOAD_TIMEOUT);
    var readyTimer = null;

    function remove() {
      clearTimeout(helloTimer);
      clearTimeout(readyTimer);
      window.removeEventListener('message', onMessage);
      if (root.parentNode) root.parentNode.removeChild(root);
    }

    function onMessage(e) {
      if (e.source !== frame.contentWindow || e.origin !== base) return;
      var m = e.data;
      if (!m || typeof m !== 'object' || typeof m.oko !== 'string') return;

      if (m.oko === 'hello') {
        clearTimeout(helloTimer);
        readyTimer = setTimeout(remove, READY_TIMEOUT);
        frame.contentWindow.postMessage(
          { oko: 'init', raterId: state.id, pagePath: location.pathname.slice(0, 500), version: version || null },
          base
        );
      } else if (m.oko === 'ready') {
        clearTimeout(readyTimer);
        var days = Number(m.days) > 0 ? Number(m.days) : 14;
        state.last = Date.now();
        state.days = days;
        writeState(state);
        if (m.position === 'bottom-left') css(frame, { left: '12px', right: 'auto' });
        css(frame, { visibility: 'visible' });
      } else if (m.oko === 'resize') {
        var h = Math.min(Math.max(Number(m.height) || 0, 0), 600);
        css(frame, { height: h + 'px' });
      } else if (m.oko === 'close' || m.oko === 'hide') {
        remove();
      }
    }

    window.addEventListener('message', onMessage);
    document.body.appendChild(root);
  }

  function safeStart() {
    try {
      start();
    } catch (e) {
      /* молчим: ошибка виджета не должна стать ошибкой сайта */
      var r = document.getElementById(ROOT_ID);
      if (r && r.parentNode) r.parentNode.removeChild(r);
    }
  }

  if (document.readyState === 'complete') setTimeout(safeStart, 0);
  else window.addEventListener('load', function () { setTimeout(safeStart, 0); });
})();
