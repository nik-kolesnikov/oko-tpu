import { ActionIcon, Code, CopyButton, Group, Tooltip } from '@mantine/core';
import { IconCheck, IconCopy } from '@tabler/icons-react';

export function CopyBlock({ value, block = false, label = 'Копировать' }: { value: string; block?: boolean; label?: string }) {
  return (
    <Group gap="xs" wrap="nowrap" align={block ? 'flex-start' : 'center'}>
      <Code block={block} className={block ? 'code-block' : undefined} style={{ flex: 1, minWidth: 0 }}>{value}</Code>
      <CopyButton value={value} timeout={1500}>
        {({ copied, copy }) => (
          <Tooltip label={copied ? 'Скопировано' : label} withArrow>
            <ActionIcon variant="light" color={copied ? 'teal' : 'gray'} onClick={copy} aria-label={label}>
              {copied ? <IconCheck size={16} /> : <IconCopy size={16} />}
            </ActionIcon>
          </Tooltip>
        )}
      </CopyButton>
    </Group>
  );
}
