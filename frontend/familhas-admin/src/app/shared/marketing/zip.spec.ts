import { crc32, zip } from './zip';

describe('zip', () => {
  it('CRC-32 padrão', () => {
    expect(crc32(new TextEncoder().encode('123456789'))).toBe(0xcbf43926);
  });

  it('gera um arquivo com assinatura, entradas e diretório central', async () => {
    const blob = zip([
      { name: 'lamina-1.jpg', data: new Uint8Array([1, 2, 3]) },
      { name: 'legendas.txt', data: new TextEncoder().encode('Olá') },
    ]);
    const bytes = new Uint8Array(await blob.arrayBuffer());
    const view = new DataView(bytes.buffer);
    expect(view.getUint32(0, true)).toBe(0x04034b50);
    const end = bytes.length - 22;
    expect(view.getUint32(end, true)).toBe(0x06054b50);
    expect(view.getUint16(end + 10, true)).toBe(2);
    expect(new TextDecoder().decode(bytes.slice(30, 30 + 12))).toBe('lamina-1.jpg');
  });
});
