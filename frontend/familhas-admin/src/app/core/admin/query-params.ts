import { HttpParams } from '@angular/common/http';

export type QueryValue =
  string | number | boolean | null | undefined | readonly (string | number)[];

/** Monta query string sem campos vazios; listas viram parâmetros repetidos (status=A&status=B). */
export function queryParams(values: Readonly<Record<string, QueryValue>>): HttpParams {
  let params = new HttpParams();
  for (const [key, value] of Object.entries(values)) {
    if (value === null || value === undefined || value === '') continue;
    if (Array.isArray(value)) {
      for (const item of value) params = params.append(key, String(item));
    } else {
      params = params.set(key, String(value));
    }
  }
  return params;
}
