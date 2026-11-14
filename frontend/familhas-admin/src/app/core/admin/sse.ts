export interface SseMessage {
  readonly event: string;
  readonly data: string;
  readonly id: string | null;
}

/**
 * Lê mensagens Server-Sent Events completas do buffer (separadas por linha em branco) e devolve o resto
 * incompleto. Comentários (": ping") são ignorados. Aceita \n e \r\n.
 */
export function parseSse(buffer: string): { messages: SseMessage[]; rest: string } {
  const normalized = buffer.replace(/\r\n/g, '\n');
  const blocks = normalized.split('\n\n');
  const rest = blocks.pop() ?? '';
  const messages: SseMessage[] = [];
  for (const block of blocks) {
    let event = 'message';
    let id: string | null = null;
    const data: string[] = [];
    for (const line of block.split('\n')) {
      if (line === '' || line.startsWith(':')) continue;
      const colon = line.indexOf(':');
      const field = colon === -1 ? line : line.slice(0, colon);
      const value = colon === -1 ? '' : line.slice(colon + 1).replace(/^ /, '');
      if (field === 'event') event = value;
      else if (field === 'data') data.push(value);
      else if (field === 'id') id = value;
    }
    if (data.length > 0 || event !== 'message') {
      messages.push({ event, data: data.join('\n'), id });
    }
  }
  return { messages, rest };
}
