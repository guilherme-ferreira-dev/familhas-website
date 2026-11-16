# LGPD no CRM interno da Voe Familhas

Documentação do tratamento de dados pessoais. Decisões técnicas em [ADR 0018](adr/0018-lgpd-operacao-e-entrega.md). **A conformidade também depende de políticas e decisões da agência fora do código**: os itens marcados como _pendência organizacional_ precisam de resposta do responsável legal.

- Controladora: EEBPG Agência de Viagens LTDA (Voe Familhas).
- Encarregado (DPO): _pendência organizacional_ — nomear e publicar o contato.
- Titulares: clientes e passageiros (inclusive crianças e adolescentes que viajam com a família), membros da equipe.

## Fluxos, finalidade e base legal

A base legal abaixo é a **pertinente sugerida**; a definição final é da agência. Consentimento não é usado como base universal.

| Fluxo | Dados | Finalidade | Base legal sugerida (art. 7º) |
|---|---|---|---|
| Voos Clientes | nome, CPF, telefone, voo, localizador, anexos (bilhetes) | acompanhar a viagem contratada e avisar o cliente | execução de contrato (V) |
| Emitir Voucher | nome, CPF, telefone, observações, passageiros, documento de origem | comprovar o serviço contratado junto ao fornecedor | execução de contrato (V); obrigação legal/regulatória (II) para a guarda |
| Documentos | arquivos enviados pela equipe | guarda de documentos da viagem | execução de contrato (V) |
| Pesquisar Voos | só destinos e datas | cotação | sem dado pessoal |
| Posts e roteiros | conteúdo de marketing, sem dado de cliente | divulgação | sem dado pessoal (não usar foto de cliente sem autorização) |
| Equipe | nome, e-mail, papel, login, TOTP | controle de acesso | legítimo interesse (IX) e execução do contrato de trabalho |
| Auditoria | quem fez o quê e quando, CPF mascarado | segurança e prestação de contas | legítimo interesse (IX); exercício regular de direitos (VI) |
| Backup | cópia das tabelas acima | continuidade | mesma base do dado de origem |

Dados de crianças e adolescentes (art. 14): aparecem como passageiros e em documentos. Tratados no melhor interesse do menor, só para a viagem contratada, com os mesmos controles e sem uso em marketing.

## Controles no código

| Princípio | Como |
|---|---|
| Minimização | Campos opcionais (CPF, telefone); pesquisa de voos e marketing sem dado pessoal; e-mail do backup só com link |
| Acesso mínimo | Papéis do Keycloak e permissões `crm:<recurso>:<ação>` checadas na API; marketing não vê passageiros; privacidade só admin (ADR 0013) |
| Mascaramento | CPF sempre `***.982.247-**` nas respostas; inteiro só no A4 do voucher (auditado como DOWNLOAD) e no relatório ao titular |
| Logs | Sem corpo de requisição e sem dado pessoal; CPF de pedidos de titular vai no corpo, nunca na URL |
| Arquivos | Storage privado, nome aleatório, links assinados de 5 min, MIME e assinatura checados (ADR 0014) |
| Correção | Edição de voos e vouchers pela equipe, com auditoria |
| Acesso pelo titular | Relatório por CPF (Privacidade → Pedido de titular), com identidade confirmada |
| Eliminação | Anonimização com prévia; respeita viagem em andamento e prazo legal do voucher; nunca automática |
| Retenção | Tabelas técnicas por idade (ADR 0012); backup guardado 7 dias; voucher `PRIVACY_VOUCHER_RETENTION` |
| Incidentes | Registro em Privacidade → Incidentes; plano em [incident-response.md](incident-response.md) |
| Criptografia | TLS no Keycloak e na hospedagem; backup guardado com AES-256-GCM |

## Como atender um pedido de titular

1. Receba o pedido por canal conhecido (e-mail ou WhatsApp da agência) e registre o protocolo.
2. Confirme a identidade: documento com foto conferido com o cadastro, ou contato pelo telefone já registrado. Sem isso, não prossiga.
3. **Acesso:** Privacidade → informe CPF e protocolo → Gerar relatório. Envie o arquivo ao titular por canal seguro.
4. **Correção:** edite o voo ou voucher.
5. **Eliminação:** Prévia da anonimização → confira o que fica (viagem em andamento, voucher no prazo) → Anonimizar. Explique ao titular o que foi mantido e por quê. Documentos/anexos ligados ao titular: apague em Documentos ou no voo.
6. Prazo de resposta: _pendência organizacional_ (a ANPD regulamenta; adote prazo curto, ex.: 15 dias).

## Fornecedores e transferências

| Fornecedor | Uso | Dado pessoal enviado | Situação |
|---|---|---|---|
| Oracle (banco próprio) | armazenamento | todos | hospedagem própria; definir provedor de nuvem e região em produção |
| Keycloak (próprio) | login da equipe | e-mail e nome da equipe | hospedagem própria |
| SMTP (Gmail ou outro) | convites e link de backup | e-mail da equipe | _pendência_: escolher provedor e revisar termos |
| SerpApi | preços de voos | nenhum (só destinos e datas) | não configurado |
| Meta (Instagram) | publicação de posts | nenhum de cliente | não configurado; transferência internacional (EUA) |
| Provedor de IA | extração e geração de texto/imagem | **não configurado**. Antes de ligar: pseudonimizar PDFs (sem CPF/passaporte), contrato sem uso para treinamento, avaliar retenção e transferência internacional | não configurado |
| Stripe (Loja) | pagamentos da Loja | dados do comprador; cartão fica no Stripe | fora do CRM |

## Pendências organizacionais

- [ ] Nomear o encarregado e publicar o canal de atendimento ao titular.
- [ ] Confirmar com o contador/jurídico o prazo de guarda de vouchers e comprovantes (`PRIVACY_VOUCHER_RETENTION`, hoje 5 anos) e de documentos de viagem.
- [ ] Validar a base legal de cada fluxo da tabela acima.
- [ ] Política de privacidade e aviso aos clientes (inclusive sobre uso de documentos de menores).
- [ ] Termo de confidencialidade e treinamento da equipe (CPF não vai para grupos de WhatsApp; voucher pelo WhatsApp já sai sem CPF).
- [ ] Contratos com os fornecedores da tabela, em especial IA e SMTP, antes de configurar.
- [ ] Relatório de impacto (RIPD) se o volume de dados ou o uso de IA crescer.
