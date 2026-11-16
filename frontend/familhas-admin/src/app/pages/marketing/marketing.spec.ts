import { slug } from '../../shared/marketing/canvas';
import { footerText } from '../../shared/marketing/insta-slide';
import { cruiseCaption } from './cruise-post.page';
import { captionsFile, fullCaption, postProblems, SlideDraft, toRequest } from './instagram-editor';

const slide = (over: Partial<SlideDraft> = {}): SlideDraft => ({
  imageFileId: 'img-1',
  cleanFileId: 'clean-1',
  previewUrl: null,
  hook: '',
  title: 'Praia do Gunga',
  bullets: 'Piscinas naturais\nMar verde\n\nBuggy',
  caption: 'Uma das praias mais lindas de Alagoas.',
  ...over,
});

describe('fila do Instagram', () => {
  it('formatos do JSON: Story 1 imagem, Feed 1 a 20, Reel com vídeo', () => {
    expect(postProblems('STORY', [slide(), slide({ imageFileId: 'b' })], false, '')).toEqual([
      'Story leva exatamente 1 imagem.',
    ]);
    expect(postProblems('FEED', [], false, '')).toHaveLength(1);
    expect(
      postProblems(
        'FEED',
        Array.from({ length: 20 }, (_, i) => slide({ imageFileId: `i${i}` })),
        false,
        '',
      ),
    ).toEqual([]);
    expect(
      postProblems(
        'FEED',
        Array.from({ length: 21 }, (_, i) => slide({ imageFileId: `i${i}` })),
        false,
        '',
      ),
    ).toHaveLength(1);
    expect(postProblems('REEL', [], false, '')).toEqual([
      'Reel leva um vídeo MP4 e, se quiser, uma capa.',
    ]);
    expect(postProblems('REEL', [slide()], true, '')).toEqual([]);
    expect(postProblems('FEED', [slide({ bullets: 'a\nb\nc\nd\ne\nf' })], false, '')).toEqual([
      'Lâmina 1: no máximo 5 tópicos.',
    ]);
    const tags = Array.from({ length: 31 }, (_, i) => `#t${i}`).join(' ');
    expect(postProblems('FEED', [slide()], false, tags)).toEqual(['No máximo 30 hashtags.']);
  });

  it('legenda com hashtags e arquivo de legendas do ZIP com a de cada lâmina', () => {
    expect(fullCaption(' Vem pra Maceió ', ' #voefamilhas ')).toBe(
      'Vem pra Maceió\n\n#voefamilhas',
    );
    expect(fullCaption('Só texto', '  ')).toBe('Só texto');
    const file = captionsFile('Maceió', 'Legenda', '#voefamilhas', [
      slide(),
      slide({ caption: '' }),
    ]);
    expect(file).toContain('## Lâmina 1\nUma das praias');
    expect(file).not.toContain('## Lâmina 2');
  });

  it('corpo da API sem tópicos vazios', () => {
    const body = toRequest({
      format: 'FEED',
      title: ' Post ',
      idea: '',
      caption: 'x',
      hashtags: '',
      slides: [slide()],
      videoFileId: null,
      version: 3,
    });
    expect(body.title).toBe('Post');
    expect(body.idea).toBeNull();
    expect(body.slides?.[0].bullets).toEqual(['Piscinas naturais', 'Mar verde', 'Buggy']);
    expect(body.version).toBe(3);
  });

  it('rodapé fixo do JSON e nomes de arquivo seguros', () => {
    expect(footerText('(61) 99990-9464')).toBe(
      'Planeje sua viagem com a Voe Familhas • WhatsApp (61) 99990-9464',
    );
    expect(slug('Maceió 29 de Jan a 04 de Fev')).toBe('maceio-29-de-jan-a-04-de-fev');
    expect(slug('../../etc')).toBe('etc');
    expect(slug('!!!')).toBe('post');
  });

  it('legenda do cruzeiro', () => {
    const text = cruiseCaption({
      ship: 'MSC Grandiosa',
      itinerary: 'Santos · Búzios · Salvador',
      dates: '10 a 17 de Dez',
      cabin: 'Externa',
      price: 3990,
      installments: 'em até 10x sem juros',
      inclusions: ['Pensão completa'],
      whatsapp: '(61) 99990-9464',
    });
    expect(text).toContain('🚢 MSC Grandiosa');
    expect(text).toMatch(/3\.990,00 por pessoa/);
    expect(text).not.toMatch(/\n\n\n/);
  });
});
