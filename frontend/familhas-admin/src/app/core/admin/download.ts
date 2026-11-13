import { HttpResponse } from '@angular/common/http';

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
