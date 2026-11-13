import { HttpResponse } from '@angular/common/http';

/**
 * Nome padrão no formato da API (pagamentos-AAAAMMDD-HHMM.ext, horário de São Paulo). Em outra origem o navegador
 * só lê o Content-Disposition se o CORS expuser o header; sem isso, este nome é o usado.
 */
export function exportFileName(prefix: string, extension: string, now = new Date()): string {
  const parts = Object.fromEntries(
    new Intl.DateTimeFormat('en-GB', {
      timeZone: 'America/Sao_Paulo',
      year: 'numeric',
      month: '2-digit',
      day: '2-digit',
      hour: '2-digit',
      minute: '2-digit',
      hourCycle: 'h23',
    })
      .formatToParts(now)
      .map((p) => [p.type, p.value]),
  );
  return `${prefix}-${parts['year']}${parts['month']}${parts['day']}-${parts['hour']}${parts['minute']}.${extension}`;
}

/** Nome do arquivo do Content-Disposition (`attachment; filename="x.csv"`), ou o padrão. */
export function fileNameFrom(contentDisposition: string | null, fallback: string): string {
  const match = contentDisposition?.match(/filename\*?=(?:UTF-8'')?"?([^";]+)"?/i);
  const name = match?.[1] ? decodeURIComponent(match[1]) : fallback;
  // Nunca deixa a API escolher um caminho: só o nome.
  return name.replace(/[/\\]/g, '_');
}

/** Salva o arquivo recebido da API (o token foi no header, então não dá para usar um link direto). */
export function saveDownload(response: HttpResponse<Blob>, fallbackName: string): void {
  if (!response.body) return;
  const url = URL.createObjectURL(response.body);
  const anchor = document.createElement('a');
  anchor.href = url;
  anchor.download = fileNameFrom(response.headers.get('Content-Disposition'), fallbackName);
  anchor.rel = 'noopener';
  document.body.append(anchor);
  anchor.click();
  anchor.remove();
  setTimeout(() => URL.revokeObjectURL(url), 1000);
}
