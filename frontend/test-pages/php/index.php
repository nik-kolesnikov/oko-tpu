<?php
// Тестовая страница на PHP-шаблоне (как у большинства сайтов ТПУ).
// Запуск: php -S localhost:5501 -t test-pages/php
$appVersion = '2.4.0';
// Домен ОКО и ключ задаёт scripts/test-pages.mjs (переменные окружения), по умолчанию — мок.
$okoOrigin = getenv('OKO_ORIGIN') ?: 'http://localhost:5173';
$okoKey = getenv('OKO_TEST_KEY') ?: 'pk_demo_lk_7f3a9c2e5b1d4f60';
$okoService = getenv('OKO_TEST_SERVICE');
$pageTitle = 'Расписание (PHP)';
$lessons = ['Математика', 'Физика', 'Программирование на Java'];
?>
<!doctype html>
<html lang="ru">
<head>
  <meta charset="utf-8">
  <title><?= htmlspecialchars($pageTitle) ?></title>
  <style>body { font: 16px Arial; max-width: 720px; margin: 40px auto; padding: 0 16px; }</style>
</head>
<body>
  <h1><?= htmlspecialchars($pageTitle) ?></h1>
  <ul>
    <?php foreach ($lessons as $l): ?>
      <li><?= htmlspecialchars($l) ?></li>
    <?php endforeach; ?>
  </ul>
  <p>Страница отрендерена на сервере: <?= date('H:i:s') ?></p>

  <?php /* Код вставки ОКО — в общем шаблоне, перед </body> */ ?>
  <!-- ОКО ТПУ: виджет оценки (код со вкладки «Интеграция») -->
  <script>
    (function (d, w) {
      var s = d.createElement('script');
      s.src = '<?= htmlspecialchars($okoOrigin) ?>/widget/v1/loader.js';
      s.async = true;
      s.setAttribute('data-key', '<?= htmlspecialchars($okoKey) ?>');
<?php if ($okoService !== false && $okoService !== ''): ?>
      s.setAttribute('data-service-id', '<?= htmlspecialchars($okoService) ?>');
<?php endif; ?>
      s.setAttribute('data-version', '<?= htmlspecialchars($appVersion) ?>');
      w.addEventListener('load', function () { d.body.appendChild(s); });
    })(document, window);
  </script>
</body>
</html>
