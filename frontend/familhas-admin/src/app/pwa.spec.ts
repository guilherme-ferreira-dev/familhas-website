import ngsw from '../../ngsw-config.json';
import manifestText from '../../public/manifest.webmanifest' with { loader: 'text' };

interface NgswConfig {
  assetGroups: { resources: { files?: string[]; urls?: string[] } }[];
  dataGroups?: unknown;
}

describe('PWA', () => {
  it('o service worker só guarda arquivos do app, nunca respostas da API nem o config.json', () => {
    const config = ngsw as NgswConfig;
    expect(config.dataGroups).toBeUndefined();
    const patterns = config.assetGroups.flatMap((g) => [
      ...(g.resources.files ?? []),
      ...(g.resources.urls ?? []),
    ]);
    expect(
      patterns.some((p) => p.includes('json') || p.includes('/api') || p.startsWith('http')),
    ).toBe(false);
  });

  it('manifest instalável com ícones 192 e 512', () => {
    const manifest = JSON.parse(manifestText) as { display: string; icons: { sizes: string }[] };
    expect(manifest.display).toBe('standalone');
    expect(manifest.icons.map((i) => i.sizes)).toEqual(
      expect.arrayContaining(['192x192', '512x512']),
    );
  });
});
