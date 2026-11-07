/*
 * ОКО ТПУ — форма оценки внутри iframe (виджет v1).
 * Живёт на домене ОКО, поэтому стили и скрипты сайта до неё не дотягиваются.
 *
 * Порядок работы:
 *  1. говорим родителю (loader.js) «hello» и ждём от него init: raterId, путь страницы, версию;
 *  2. берём настройки GET /api/v1/widget/config (таймаут 2 с);
 *  3. рисуем карточку и говорим «ready» — loader делает iframe видимым;
 *  4. отправляем оценку POST /api/v1/widget/ratings (таймаут 2 с).
 * Любая ошибка — «hide», loader убирает iframe. Текст из API выводим только через textContent.
 */
(function () {
  'use strict';

  var TIMEOUT = 2000;
  var API = '/api/v1/widget';
  var CSS = '/widget/v1/frame.css';
  // Разметку страницы iframe генерирует backend (WidgetService.frame):
  //   <div id="oko-root" data-key="…" data-service-id="…"></div> + <script src="/widget/v1/frame.js">
  // CSP этой страницы: script-src 'self', style-src 'self', connect-src 'self'.
  var root = document.getElementById('oko-root');
  if (!root) return;
  var key = root.getAttribute('data-key');
  var serviceAttr = root.getAttribute('data-service-id');
  var serviceId = serviceAttr ? Number(serviceAttr) : null;
  var init = null;
  var config = null;
  var finished = false;

  function post(msg) {
    // В сообщениях нет ничего секретного, поэтому targetOrigin '*': origin сайта-хоста нам заранее неизвестен.
    if (window.parent !== window) window.parent.postMessage(msg, '*');
  }
  function hide() {
    finished = true;
    post({ oko: 'hide' });
  }
  function close() {
    finished = true;
    post({ oko: 'close' });
  }
  function resize() {
    post({ oko: 'resize', height: Math.ceil(document.body.getBoundingClientRect().height) });
  }

  function request(path, options) {
    var ctrl = new AbortController();
    var timer = setTimeout(function () { ctrl.abort(); }, TIMEOUT);
    options = options || {};
    options.signal = ctrl.signal;
    options.credentials = 'omit';
    options.headers = Object.assign({ 'X-Api-Key': key }, options.headers || {});
    return fetch(API + path, options).finally(function () { clearTimeout(timer); });
  }

  function el(tag, attrs, text) {
    var n = document.createElement(tag);
    for (var a in attrs || {}) n.setAttribute(a, attrs[a]);
    if (text != null) n.textContent = text;
    return n;
  }

  // ── экраны ──
  function card() {
    root.textContent = '';
    var c = el('section', { class: 'oko-card', role: 'dialog', 'aria-labelledby': 'oko-q' });
    var x = el('button', { type: 'button', class: 'oko-close', 'aria-label': 'Закрыть' }, '×');
    x.addEventListener('click', close);
    c.appendChild(x);
    root.appendChild(c);
    return c;
  }

  function renderAsk() {
    var c = card();
    c.appendChild(el('p', { id: 'oko-q', class: 'oko-q' }, config.question || 'Как вам сервис?'));
    var stars = el('div', { class: 'oko-stars', role: 'group', 'aria-label': 'Оценка от 1 до 5' });
    var buttons = [];
    for (var i = 1; i <= 5; i++) {
      (function (score) {
        var b = el('button', { type: 'button', class: 'oko-star', 'aria-label': 'Оценка ' + score + ' из 5' }, '★');
        b.addEventListener('mouseenter', function () { light(score); });
        b.addEventListener('focus', function () { light(score); });
        b.addEventListener('click', function () { choose(score); });
        buttons.push(b);
        stars.appendChild(b);
      })(i);
    }
    stars.addEventListener('mouseleave', function () { light(0); });
    function light(n) {
      buttons.forEach(function (b, idx) { b.classList.toggle('is-on', idx < n); });
    }
    c.appendChild(stars);
    var later = el('button', { type: 'button', class: 'oko-later' }, 'Не сейчас');
    later.addEventListener('click', close);
    c.appendChild(later);
    resize();
  }

  function choose(score) {
    if (score <= (config.commentThreshold || 3)) renderComment(score);
    else submit(score, null);
  }

  function renderComment(score) {
    var c = card();
    c.appendChild(el('p', { id: 'oko-q', class: 'oko-q' }, 'Ваша оценка: ' + score + ' из 5'));
    var label = el('label', { class: 'oko-label', for: 'oko-text' }, 'Что не понравилось? Что можно улучшить?');
    var area = el('textarea', { id: 'oko-text', class: 'oko-text', maxlength: '500', rows: '3' });
    var count = el('div', { class: 'oko-count', 'aria-live': 'polite' }, '0 / 500');
    area.addEventListener('input', function () { count.textContent = area.value.length + ' / 500'; });
    var actions = el('div', { class: 'oko-actions' });
    var skip = el('button', { type: 'button', class: 'oko-btn' }, 'Пропустить');
    var send = el('button', { type: 'button', class: 'oko-btn oko-btn-primary' }, 'Отправить');
    skip.addEventListener('click', function () { submit(score, null, [skip, send]); });
    send.addEventListener('click', function () { submit(score, area.value.trim() || null, [skip, send]); });
    actions.appendChild(skip);
    actions.appendChild(send);
    c.appendChild(label);
    c.appendChild(area);
    c.appendChild(count);
    c.appendChild(actions);
    resize();
    area.focus();
  }

  function renderThanks() {
    var c = card();
    c.appendChild(el('p', { class: 'oko-thanks', role: 'status' }, 'Спасибо! Ваша оценка учтена.'));
    resize();
    setTimeout(close, 2500);
  }

  function submit(score, comment, lock) {
    (lock || []).forEach(function (b) { b.disabled = true; });
    request('/ratings', {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({
        score: score,
        comment: comment,
        raterId: init.raterId,
        serviceId: serviceId,
        pagePath: init.pagePath,
        version: init.version,
      }),
    })
      .then(function (r) {
        // 409 — уже оценивал за 14 дней: для пользователя это тоже «спасибо».
        if (r.status === 201 || r.status === 409) renderThanks();
        else hide();
      })
      .catch(hide);
  }

  // ── клавиатура: Esc закрывает, Tab не выходит за карточку ──
  document.addEventListener('keydown', function (e) {
    if (e.key === 'Escape') return close();
    if (e.key !== 'Tab') return;
    var f = root.querySelectorAll('button:not([disabled]), textarea');
    if (!f.length) return;
    var first = f[0];
    var last = f[f.length - 1];
    if (e.shiftKey && document.activeElement === first) { e.preventDefault(); last.focus(); }
    else if (!e.shiftKey && document.activeElement === last) { e.preventDefault(); first.focus(); }
  });

  // Стили подключаем сами: backend отдаёт голую страницу. Ждём загрузки, чтобы карточка не мигнула без стилей.
  function loadCss() {
    return new Promise(function (resolve) {
      var link = document.createElement('link');
      link.rel = 'stylesheet';
      link.href = CSS;
      link.onload = resolve;
      link.onerror = resolve;
      setTimeout(resolve, TIMEOUT);
      document.head.appendChild(link);
    });
  }

  // ── запуск ──
  function start() {
    Promise.all([request('/config').then(function (r) { return r.ok ? r.json() : null; }), loadCss()])
      .then(function (res) {
        var c = res[0];
        if (finished) return;
        if (!c || !c.enabled) return hide();
        config = c;
        renderAsk();
        post({ oko: 'ready', position: c.position, days: c.askIntervalDays });
      })
      .catch(hide);
  }

  window.addEventListener('message', function (e) {
    if (e.source !== window.parent || init) return;
    var m = e.data;
    if (!m || m.oko !== 'init' || typeof m.raterId !== 'string') return;
    init = { raterId: m.raterId, pagePath: m.pagePath || null, version: m.version || null };
    start();
  });

  if (!key || window.parent === window) return; // открыли страницу не во фрейме — показывать нечего
  setTimeout(function () { if (!init) hide(); }, TIMEOUT);
  post({ oko: 'hello' });
})();
