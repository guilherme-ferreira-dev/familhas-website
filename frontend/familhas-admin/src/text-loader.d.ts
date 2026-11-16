// Arquivos lidos como texto nos testes (import ... with { loader: 'text' }).
declare module '*.webmanifest' {
  const content: string;
  export default content;
}
