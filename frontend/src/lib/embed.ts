// Генерация кода вставки виджета для вкладки «Интеграция».
/**
 * Код вставки. Скрипт добавляется на страницу только после события load:
 * даже если сервер ОКО отвечает 10 секунд, загрузка сайта не ждёт нас (обычный async-скрипт задержал бы событие load).
 */
export function embedSnippet(src: string, attrs: [string, string][]): string {
  // Экранируем для JS-строки в одинарных кавычках; '<' → \x3c, чтобы значение не могло закрыть тег </script>.
  const q = (v: string) => `'${v.replace(/\\/g, '\\\\').replace(/'/g, "\\'").replace(/</g, '\\x3c')}'`;
  return [
    '<!-- ОКО ТПУ: виджет оценки -->',
    '<script>',
    '  (function (d, w) {',
    "    var s = d.createElement('script');",
    `    s.src = ${q(src)};`,
    '    s.async = true;',
    ...attrs.map(([k, v]) => `    s.setAttribute(${q(k)}, ${q(v)});`),
    "    w.addEventListener('load', function () { d.body.appendChild(s); });",
    '  })(document, window);',
    '</script>',
  ].join('\n');
}
