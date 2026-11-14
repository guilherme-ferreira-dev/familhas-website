import { parseSse } from './sse';

describe('parseSse', () => {
  it('lê eventos completos, ignora comentários e guarda o resto', () => {
    const { messages, rest } = parseSse(
      'event:ready\ndata:{}\n\n: ping\n\nevent:payment\nid:p1\ndata:{"paymentId":"p1"}\n\nevent:pay',
    );
    expect(messages).toEqual([
      { event: 'ready', data: '{}', id: null },
      { event: 'payment', data: '{"paymentId":"p1"}', id: 'p1' },
    ]);
    expect(rest).toBe('event:pay');
  });

  it('aceita CRLF e espaço depois dos dois-pontos', () => {
    expect(parseSse('event: payment\r\ndata: {"a":1}\r\n\r\n').messages).toEqual([
      { event: 'payment', data: '{"a":1}', id: null },
    ]);
  });
});
