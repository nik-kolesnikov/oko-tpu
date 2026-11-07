// Демо-данные для мок-сервера. Все люди и тексты выдуманы.
// Генератор детерминированный: при каждом запуске данные одинаковые.

export function rng(seed) {
  let s = seed >>> 0;
  return () => {
    s = (s * 1664525 + 1013904223) >>> 0;
    return s / 0x100000000;
  };
}

export const DEMO_PUBLIC_KEY = 'pk_demo_lk_7f3a9c2e5b1d4f60';
export const DEMO_SECRET_KEY = 'sk_demo_lk_0b8e6d4c2a1f9e73';

const COMMENTS_BAD = [
  'Долго грузится расписание',
  'После обновления не открывается зачётка',
  'Неудобно искать нужный курс',
  'Кнопка «Отправить» не нажимается на телефоне',
  'Постоянно выкидывает из аккаунта',
  'Не видно оценок за прошлый семестр',
  'Шрифт слишком мелкий',
];
const COMMENTS_MID = ['В целом нормально, но медленно', 'Иногда пропадает меню', null, null];

export function buildSeed(now = new Date()) {
  const users = [
    { id: 1, login: 'admin_ceu', fullName: 'Администратор ЦЭУ', email: null, isAdmin: true },
    { id: 2, login: 'ivanov_ii', fullName: 'Иванов Иван Иванович', email: 'ivanov_ii@example.org', isAdmin: false },
    { id: 3, login: 'petrova_aa', fullName: 'Петрова Анна Андреевна', email: null, isAdmin: false },
  ];

  const iso = (d) => new Date(d).toISOString();
  const daysAgo = (n) => new Date(now.getTime() - n * 86400000);

  const products = [
    { id: 1, name: 'Личный кабинет студента', description: 'Расписание, зачётка, заявления', url: 'https://lk.example.tpu.ru', status: 'active', ownerIds: [2], createdAt: iso(daysAgo(120)), updatedAt: iso(daysAgo(3)) },
    { id: 2, name: 'Электронные курсы', description: 'Курсы и тесты для студентов', url: 'https://courses.example.tpu.ru', status: 'active', ownerIds: [3], createdAt: iso(daysAgo(100)), updatedAt: iso(daysAgo(10)) },
    { id: 3, name: 'Облако ТПУ', description: null, url: 'https://cloud.example.tpu.ru', status: 'paused', ownerIds: [2, 3], createdAt: iso(daysAgo(40)), updatedAt: iso(daysAgo(5)) },
  ];

  const services = [
    { id: 1, productId: 1, name: 'Расписание', url: 'https://lk.example.tpu.ru/schedule', monitoringRef: 'lk-schedule', status: 'active', createdAt: iso(daysAgo(120)) },
    { id: 2, productId: 1, name: 'Зачётная книжка', url: 'https://lk.example.tpu.ru/grades', monitoringRef: 'lk-grades', status: 'active', createdAt: iso(daysAgo(120)) },
    { id: 3, productId: 1, name: 'Заявления', url: null, monitoringRef: 'lk-apps', status: 'active', createdAt: iso(daysAgo(60)) },
    { id: 4, productId: 2, name: 'Курсы', url: null, monitoringRef: 'courses-main', status: 'active', createdAt: iso(daysAgo(100)) },
    { id: 5, productId: 2, name: 'Тесты', url: null, monitoringRef: null, status: 'active', createdAt: iso(daysAgo(100)) },
    { id: 6, productId: 3, name: 'Хранилище', url: null, monitoringRef: 'cloud-storage', status: 'active', createdAt: iso(daysAgo(40)) },
  ];

  // Заглушка мониторинга: что «отдал бы» API мониторинга.
  const health = {
    'lk-schedule': { status: 'up', responseMs: 182 },
    'lk-grades': { status: 'up', responseMs: 240 },
    'lk-apps': { status: 'down', responseMs: null },
    'courses-main': { status: 'up', responseMs: 95 },
    'cloud-storage': { status: 'up', responseMs: 410 },
  };

  const keys = [
    { id: 1, productId: 1, type: 'public', key: DEMO_PUBLIC_KEY, createdAt: iso(daysAgo(120)), createdBy: 2, revokedAt: null },
    { id: 2, productId: 1, type: 'secret', key: DEMO_SECRET_KEY, createdAt: iso(daysAgo(120)), createdBy: 2, revokedAt: null },
    { id: 3, productId: 2, type: 'public', key: 'pk_demo_cr_4c1e8a2f6d3b9e07', createdAt: iso(daysAgo(100)), createdBy: 3, revokedAt: null },
  ];

  const origins = {
    1: ['https://lk.example.tpu.ru', 'http://localhost:5500', 'http://localhost:5501', 'http://localhost:5502', 'http://127.0.0.1:5500'],
    2: ['https://courses.example.tpu.ru'],
    3: [],
  };

  const defaultSettings = () => ({
    question: 'Как вам сервис?',
    askIntervalDays: 14,
    commentThreshold: 3,
    position: 'bottom-right',
    widgetEnabled: true,
    updatedAt: iso(daysAgo(30)),
  });
  const widgetSettings = { 1: { ...defaultSettings(), question: 'Как вам личный кабинет?' }, 2: defaultSettings(), 3: defaultSettings() };

  const releases = [
    { id: 1, productId: 1, serviceId: null, version: '2.3.0', title: 'Новое меню', description: 'Перенесли навигацию влево', releasedAt: iso(daysAgo(45)), createdBy: 2, createdAt: iso(daysAgo(45)) },
    { id: 2, productId: 1, serviceId: 1, version: '2.4.0', title: 'Новое расписание', description: 'Переписали страницу расписания', releasedAt: iso(daysAgo(12)), createdBy: 2, createdAt: iso(daysAgo(12)) },
    { id: 3, productId: 2, serviceId: null, version: '1.12.0', title: 'Тесты на телефоне', description: null, releasedAt: iso(daysAgo(20)), createdBy: 3, createdAt: iso(daysAgo(20)) },
  ];

  // Оценки: продукт 1 — 90 дней, после релиза 2.4.0 средняя падает; продукт 2 — стабильно хорошо; продукт 3 — мало.
  const ratings = [];
  const r = rng(42);
  let id = 1;
  const add = (productId, serviceIds, days, perDay, scoreFn, versionFn) => {
    for (let d = days; d >= 0; d--) {
      const n = Math.floor(r() * perDay * 2);
      for (let i = 0; i < n; i++) {
        const score = scoreFn(d);
        const serviceId = serviceIds.length && r() < 0.8 ? serviceIds[Math.floor(r() * serviceIds.length)] : null;
        let comment = null;
        if (score <= 2) comment = COMMENTS_BAD[Math.floor(r() * COMMENTS_BAD.length)];
        else if (score === 3) comment = COMMENTS_MID[Math.floor(r() * COMMENTS_MID.length)];
        const at = new Date(daysAgo(d).getTime() - Math.floor(r() * 20 * 3600000));
        ratings.push({
          id: id++, productId, serviceId, score, comment,
          raterId: `seed-${productId}-${d}-${i}`,
          raterRole: r() < 0.85 ? 'student' : 'staff',
          pagePath: serviceId === 1 ? '/schedule' : serviceId === 2 ? '/grades' : '/',
          version: versionFn(d), source: r() < 0.9 ? 'widget' : 'api',
          createdAt: at.toISOString(),
        });
      }
    }
  };
  const pick = (weights) => {
    const x = r() * weights.reduce((a, b) => a + b, 0);
    let acc = 0;
    for (let i = 0; i < 5; i++) { acc += weights[i]; if (x < acc) return i + 1; }
    return 5;
  };
  add(1, [1, 2, 3], 90, 4,
    (d) => (d < 12 ? pick([4, 4, 3, 4, 5]) : pick([1, 1, 2, 6, 9])),
    (d) => (d < 12 ? '2.4.0' : d < 45 ? '2.3.0' : '2.2.1'));
  add(2, [4, 5], 60, 2.5, () => pick([1, 1, 2, 5, 9]), (d) => (d < 20 ? '1.12.0' : '1.11.3'));
  add(3, [6], 14, 0.4, () => pick([1, 2, 3, 3, 2]), () => null);

  return { users, products, services, health, keys, origins, widgetSettings, releases, ratings };
}
