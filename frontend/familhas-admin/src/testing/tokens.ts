/** Access token falso (só o payload importa: o Admin não valida assinatura, quem valida é a API). */
export function fakeToken(roles: string[]): string {
  const encode = (value: object) =>
    btoa(JSON.stringify(value)).replace(/\+/g, '-').replace(/\//g, '_').replace(/=+$/, '');
  return `${encode({ alg: 'none' })}.${encode({ sub: 'kc-1', realm_access: { roles } })}.sig`;
}
