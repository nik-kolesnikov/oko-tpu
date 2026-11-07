import { describe, expect, it } from 'vitest';
import { embedSnippet } from './embed';

describe('код вставки', () => {
  const code = embedSnippet('https://oko.tpu.ru/widget/v1/loader.js', [['data-key', 'pk_abc'], ['data-version', "1.0'</script>"]]);

  it('подключает loader только после load', () => {
    expect(code).toContain("w.addEventListener('load'");
    expect(code).toContain("s.src = 'https://oko.tpu.ru/widget/v1/loader.js';");
    expect(code).toContain("s.setAttribute('data-key', 'pk_abc');");
  });

  it('значение не может закрыть тег script или строку', () => {
    expect(code).not.toContain("'</script>");
    expect(code).toContain("1.0\\'\\x3c/script>");
  });
});
