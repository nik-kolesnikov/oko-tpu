// Вкладка «Интеграция»: код вставки виджета, разрешённые сайты, ключи, настройки виджета, серверный API.
//
// Про ключи (frontend/CLAUDE.md, контракт listKeys):
// - полный ключ, и публичный, и секретный, приходит только в ответе на выпуск или перевыпуск — показываем его сразу;
// - потом виден только префикс; потерянный ключ перевыпускают и меняют код на сайтах;
// - ключи живут только в состоянии вкладки: не в localStorage, не в sessionStorage.
// При создании продукта ключи не выпускаются — пустое состояние с кнопкой «Выпустить публичный ключ».
import { useMemo, useState } from 'react';
import {
  Alert, Badge, Button, Card, Code, Group, Modal, NumberInput, SegmentedControl, Select, SimpleGrid, Stack, Switch,
  Table, Tabs, Text, Textarea, TextInput, Title,
} from '@mantine/core';
import { IconAlertTriangle, IconKey } from '@tabler/icons-react';
import {
  useCreateKey, useKeys, useOrigins, useReissueKey, useReplaceOrigins, useRevokeKey, useServices,
  useUpdateWidgetSettings, useWidgetSettings,
} from '../../api/hooks';
import type { ApiKey, ApiKeyIssued, Product, WidgetSettingsInput } from '../../api/types';
import { CopyBlock } from '../../components/CopyBlock';
import { EmptyState, ErrorState, LoadingState } from '../../components/States';
import { notifyError, notifyOk } from '../../components/notify';
import { formatDate, ORIGIN_RE } from '../../lib/format';
import { embedSnippet } from '../../lib/embed';

// Панель, виджет и API живут на одном домене (в разработке — :5173, в проде — домен ОКО).
const WIDGET_ORIGIN = window.location.origin;
const KEY_PLACEHOLDER = 'ВАШ_ПУБЛИЧНЫЙ_КЛЮЧ';


export function IntegrationTab({ product }: { product: Product }) {
  const id = product.id;
  // Публичный ключ, выпущенный только что на этой вкладке: подставляется в код вставки, пока вкладка открыта.
  const [publicKey, setPublicKey] = useState<ApiKeyIssued | null>(null);
  // Только что выпущенный ключ любого типа — в окно «скопируйте сейчас».
  const [issued, setIssued] = useState<ApiKeyIssued | null>(null);
  const onIssued = (k: ApiKeyIssued) => {
    if (k.type === 'public') setPublicKey(k);
    setIssued(k);
  };

  if (product.status === 'archived') {
    return <Alert color="gray" title="Продукт в архиве">Виджет и приём оценок отключены, ключи отозваны.</Alert>;
  }

  return (
    <Stack gap="lg">
      {product.status === 'paused' && (
        <Alert color="yellow" icon={<IconAlertTriangle size={18} />}>Сбор оценок приостановлен: виджет не показывается, API отвечает отказом. Включить можно в «Настройках».</Alert>
      )}
      <EmbedCard productId={id} publicKey={publicKey} onIssued={onIssued} />
      <OriginsCard productId={id} />
      <KeysCard productId={id} onIssued={onIssued} />
      <IssuedKeyModal issued={issued} onClose={() => setIssued(null)} />
      <WidgetSettingsCard productId={id} />
      <ServerApiCard />
    </Stack>
  );
}

// ── Код вставки ──
function EmbedCard({ productId, publicKey, onIssued }: { productId: number; publicKey: ApiKeyIssued | null; onIssued: (k: ApiKeyIssued) => void }) {
  const services = useServices(productId);
  const keys = useKeys(productId, false);
  const create = useCreateKey(productId);
  const hasActivePublic = keys.data?.items.some((k) => k.type === 'public' && !k.revokedAt) ?? false;
  const [serviceId, setServiceId] = useState('');
  const [version, setVersion] = useState('');
  const key = publicKey?.key ?? KEY_PLACEHOLDER;

  const src = `${WIDGET_ORIGIN}/widget/v1/loader.js`;
  const attrs: [string, string][] = [['data-key', key]];
  if (serviceId) attrs.push(['data-service-id', serviceId]);
  if (version) attrs.push(['data-version', version]);
  const tag = embedSnippet(src, attrs);
  const snippets = {
    html: tag,
    php: `<?php /* общий шаблон сайта (например, footer.php), перед </body> */ ?>\n${tag}`,
    react: `<!-- public/index.html (или index.html в Vite), перед </body>.\n     Не в компоненте: так виджет не пересоздаётся при рендерах. -->\n${tag}`,
  };

  return (
    <Card>
      <Title order={4}>Код виджета</Title>
      <Text size="sm" c="dimmed" mb="sm">Вставьте этот код на все страницы сайта перед &lt;/body&gt;. Виджет подключается после загрузки страницы и не тормозит её; если сервер ОКО недоступен, он просто не появится.</Text>
      {publicKey ? (
        <Alert color="teal" icon={<IconKey size={18} />} mb="sm" title="Публичный ключ выпущен">
          <Text size="sm" mb={6}>Ключ уже подставлен в код ниже. Скопируйте код сейчас: после перезагрузки страницы полный ключ больше не покажется.</Text>
          <CopyBlock value={publicKey.key} label="Копировать ключ" />
        </Alert>
      ) : keys.isPending ? <LoadingState /> : keys.error ? <ErrorState error={keys.error} onRetry={() => keys.refetch()} /> : !hasActivePublic ? (
        <EmptyState title="Публичного ключа ещё нет" action={
          <Button leftSection={<IconKey size={16} />} loading={create.isPending}
            onClick={() => create.mutate('public', { onSuccess: onIssued, onError: (e) => notifyError(e) })}>
            Выпустить публичный ключ
          </Button>
        }>
          Ключ нужен, чтобы виджет отправлял оценки этого продукта. Код вставки появится сразу после выпуска.
        </EmptyState>
      ) : (
        <Alert color="gray" mb="sm" title="Полный ключ показывается только при выпуске">
          <Text size="sm">Подставьте в код публичный ключ, который вы сохранили при выпуске. Если он потерян, восстановить его нельзя: нажмите «Перевыпустить» в блоке «Ключи» ниже. Старый ключ сразу перестанет работать, поэтому код вставки нужно будет заменить на всех сайтах продукта.</Text>
        </Alert>
      )}
      {(publicKey || hasActivePublic) && (<>
        <Group grow mb="sm" align="flex-end">
          <Select label="Привязать к сервису" data={[{ value: '', label: 'Весь продукт' }, ...(services.data?.items ?? []).map((s) => ({ value: String(s.id), label: s.name }))]}
            value={serviceId} onChange={(v) => setServiceId(v ?? '')} allowDeselect={false} />
          <TextInput label="Версия продукта (необязательно)" placeholder="2.4.1" value={version} onChange={(e) => setVersion(e.currentTarget.value.trim().slice(0, 50))} />
        </Group>
        <Tabs defaultValue="html">
          <Tabs.List>
            <Tabs.Tab value="html">HTML</Tabs.Tab>
            <Tabs.Tab value="php">PHP</Tabs.Tab>
            <Tabs.Tab value="react">React</Tabs.Tab>
          </Tabs.List>
          {(['html', 'php', 'react'] as const).map((k) => (
            <Tabs.Panel key={k} value={k} pt="sm"><CopyBlock value={snippets[k]} block label="Копировать код" /></Tabs.Panel>
          ))}
        </Tabs>
        <Text size="xs" c="dimmed" mt="sm">Виджет показывается только на сайтах из списка «Разрешённые сайты».</Text>
      </>)}
    </Card>
  );
}

// ── Разрешённые сайты ──
function OriginsCard({ productId }: { productId: number }) {
  const origins = useOrigins(productId);
  const save = useReplaceOrigins(productId);
  const [text, setText] = useState<string | null>(null);
  const value = text ?? (origins.data?.origins ?? []).join('\n');
  const lines = useMemo(() => value.split('\n').map((s) => s.trim().replace(/\/+$/, '')).filter(Boolean), [value]);
  const bad = lines.filter((l) => !ORIGIN_RE.test(l));

  return (
    <Card>
      <Title order={4}>Разрешённые сайты</Title>
      <Text size="sm" c="dimmed" mb="sm">По одному на строку: схема и домен, без пути. Например, https://lk.tpu.ru. На других сайтах браузер не даст встроить виджет.</Text>
      {origins.isPending ? <LoadingState /> : origins.error ? <ErrorState error={origins.error} onRetry={() => origins.refetch()} /> : (
        <Stack gap="sm">
          <Textarea autosize minRows={3} value={value} onChange={(e) => setText(e.currentTarget.value)}
            error={bad.length ? `Неверный формат: ${bad.join(', ')}` : undefined} aria-label="Разрешённые сайты" />
          {!lines.length && <Text size="sm" c="yellow.8">Список пуст — виджет не покажется ни на одном сайте.</Text>}
          <Group justify="flex-end">
            {text !== null && <Button variant="default" onClick={() => setText(null)}>Отменить</Button>}
            <Button disabled={!!bad.length || text === null} loading={save.isPending}
              onClick={() => save.mutate(lines, { onSuccess: () => { setText(null); notifyOk('Список сайтов сохранён'); }, onError: (e) => notifyError(e) })}>
              Сохранить
            </Button>
          </Group>
        </Stack>
      )}
    </Card>
  );
}

// ── Ключи ──
function KeysCard({ productId, onIssued }: { productId: number; onIssued: (k: ApiKeyIssued) => void }) {
  const [showRevoked, setShowRevoked] = useState(false);
  const keys = useKeys(productId, showRevoked);
  const create = useCreateKey(productId);
  const reissue = useReissueKey(productId);
  const revoke = useRevokeKey(productId);

  const doReissue = (k: ApiKey) => {
    if (!window.confirm(`Перевыпустить ${k.type === 'public' ? 'публичный' : 'секретный'} ключ ${k.keyPrefix}…? Старый ключ сразу перестанет работать.`)) return;
    reissue.mutate(k.id, { onSuccess: onIssued, onError: (e) => notifyError(e) });
  };
  const doRevoke = (k: ApiKey) => {
    if (!window.confirm(`Отозвать ключ ${k.keyPrefix}…? Оценки с ним сразу перестанут приниматься.`)) return;
    revoke.mutate(k.id, { onSuccess: () => notifyOk('Ключ отозван'), onError: (e) => notifyError(e) });
  };

  return (
    <Card>
      <Group justify="space-between" mb="sm">
        <div>
          <Title order={4}>Ключи</Title>
          <Text size="sm" c="dimmed">Публичный — для виджета. Секретный — для отправки оценок с вашего сервера, в браузер его не кладите.</Text>
        </div>
        <Group gap="xs">
          <Button variant="light" loading={create.isPending && create.variables === 'public'} onClick={() => create.mutate('public', { onSuccess: onIssued, onError: (e) => notifyError(e) })}>Выпустить публичный</Button>
          <Button variant="light" color="orange" loading={create.isPending && create.variables === 'secret'} onClick={() => create.mutate('secret', { onSuccess: onIssued, onError: (e) => notifyError(e) })}>Выпустить секретный</Button>
        </Group>
      </Group>
      {keys.isPending ? <LoadingState /> : keys.error ? <ErrorState error={keys.error} onRetry={() => keys.refetch()} /> : !keys.data.items.length ? (
        <EmptyState title="Ключей нет">Выпустите публичный ключ, чтобы подключить виджет.</EmptyState>
      ) : (
        <Table.ScrollContainer minWidth={620}>
          <Table>
            <Table.Thead><Table.Tr><Table.Th>Тип</Table.Th><Table.Th>Ключ</Table.Th><Table.Th>Выпущен</Table.Th><Table.Th>Статус</Table.Th><Table.Th /></Table.Tr></Table.Thead>
            <Table.Tbody>
              {keys.data.items.map((k) => (
                <Table.Tr key={k.id}>
                  <Table.Td><Badge variant="light" color={k.type === 'public' ? 'teal' : 'orange'}>{k.type === 'public' ? 'публичный' : 'секретный'}</Badge></Table.Td>
                  <Table.Td><Code>{k.keyPrefix}…</Code></Table.Td>
                  <Table.Td><Text size="sm">{formatDate(k.createdAt)}</Text><Text size="xs" c="dimmed">{k.createdBy.fullName}</Text></Table.Td>
                  <Table.Td>{k.revokedAt ? <Text size="sm" c="dimmed">отозван {formatDate(k.revokedAt)}</Text> : <Text size="sm" c="teal.7">действует</Text>}</Table.Td>
                  <Table.Td>
                    {!k.revokedAt && (
                      <Group gap="xs" justify="flex-end" wrap="nowrap">
                        <Button size="xs" variant="default" onClick={() => doReissue(k)}>Перевыпустить</Button>
                        <Button size="xs" variant="subtle" color="red" onClick={() => doRevoke(k)}>Отозвать</Button>
                      </Group>
                    )}
                  </Table.Td>
                </Table.Tr>
              ))}
            </Table.Tbody>
          </Table>
        </Table.ScrollContainer>
      )}
      <Switch mt="sm" size="xs" label="Показать отозванные" checked={showRevoked} onChange={(e) => setShowRevoked(e.currentTarget.checked)} />
    </Card>
  );
}

// Только что выпущенный ключ: показываем один раз, потом состояние очищается.
function IssuedKeyModal({ issued, onClose }: { issued: ApiKeyIssued | null; onClose: () => void }) {
  return (
    <Modal opened={!!issued} onClose={onClose} title={issued?.type === 'secret' ? 'Новый секретный ключ' : 'Новый публичный ключ'} closeOnClickOutside={false}>
      {issued && (
        <Stack>
          <Alert color={issued.type === 'secret' ? 'orange' : 'teal'} icon={<IconAlertTriangle size={18} />}>
            {issued.type === 'secret'
              ? 'Скопируйте ключ сейчас и сохраните в настройках вашего сервера (.env). Мы показываем его один раз и больше не сможем показать.'
              : 'Скопируйте ключ сейчас. Код вставки на этой вкладке уже обновлён, но после перезагрузки страницы полный ключ больше не покажется. Потеряете — придётся перевыпустить ключ и заменить код на сайтах.'}
          </Alert>
          <CopyBlock value={issued.key} label="Копировать ключ" />
          <Group justify="flex-end"><Button onClick={onClose}>Я сохранил ключ</Button></Group>
        </Stack>
      )}
    </Modal>
  );
}

// ── Настройки виджета ──
function WidgetSettingsCard({ productId }: { productId: number }) {
  const settings = useWidgetSettings(productId);
  const save = useUpdateWidgetSettings(productId);
  const [draft, setDraft] = useState<WidgetSettingsInput | null>(null);
  const v: WidgetSettingsInput | null = draft ?? (settings.data
    ? { question: settings.data.question, askIntervalDays: settings.data.askIntervalDays, commentThreshold: settings.data.commentThreshold, position: settings.data.position, widgetEnabled: settings.data.widgetEnabled }
    : null);
  const set = <K extends keyof WidgetSettingsInput>(k: K, val: WidgetSettingsInput[K]) => v && setDraft({ ...v, [k]: val });

  return (
    <Card>
      <Title order={4} mb="sm">Настройки виджета</Title>
      {settings.isPending ? <LoadingState /> : settings.error ? <ErrorState error={settings.error} onRetry={() => settings.refetch()} /> : v && (
        <Stack>
          <Switch label="Показывать виджет" checked={v.widgetEnabled} onChange={(e) => set('widgetEnabled', e.currentTarget.checked)} />
          <TextInput label="Вопрос" maxLength={200} value={v.question} onChange={(e) => set('question', e.currentTarget.value)} description="Например: «Как вам личный кабинет?»" />
          <SimpleGrid cols={{ base: 1, sm: 3 }}>
            <NumberInput label="Спрашивать не чаще, дней" min={1} max={90} value={v.askIntervalDays} onChange={(n) => set('askIntervalDays', Number(n) || 1)} />
            <NumberInput label="Спрашивать текст при оценке ≤" min={1} max={5} value={v.commentThreshold} onChange={(n) => set('commentThreshold', Number(n) || 1)} />
            <div>
              <Text size="sm" fw={500} mb={4}>Положение</Text>
              <SegmentedControl fullWidth data={[{ value: 'bottom-right', label: 'Справа' }, { value: 'bottom-left', label: 'Слева' }]}
                value={v.position} onChange={(p) => set('position', p as WidgetSettingsInput['position'])} />
            </div>
          </SimpleGrid>
          <Group justify="flex-end">
            {draft && <Button variant="default" onClick={() => setDraft(null)}>Отменить</Button>}
            <Button disabled={!draft || !v.question.trim()} loading={save.isPending}
              onClick={() => save.mutate({ ...v, question: v.question.trim() }, { onSuccess: () => { setDraft(null); notifyOk('Настройки сохранены. Виджет подхватит их при следующей загрузке страницы.'); }, onError: (e) => notifyError(e) })}>
              Сохранить
            </Button>
          </Group>
        </Stack>
      )}
    </Card>
  );
}

// ── Серверная интеграция ──
function ServerApiCard() {
  const origin = window.location.origin;
  const curl = `curl -X POST ${origin}/api/v1/ratings \\
  -H "X-Api-Key: $OKO_SECRET_KEY" \\
  -H "Content-Type: application/json" \\
  -d '{"score": 5, "raterId": "id-пользователя-в-вашем-продукте", "raterRole": "student", "version": "1.12.0"}'`;
  return (
    <Card>
      <Title order={4}>Отправка оценок с сервера</Title>
      <Text size="sm" c="dimmed" mb="sm">Для мобильных приложений, ботов и бэкендов. Нужен секретный ключ. Ответ 201 — оценка принята, 409 — этот пользователь уже оценивал продукт за последние 14 дней.</Text>
      <CopyBlock value={curl} block label="Копировать пример" />
    </Card>
  );
}
