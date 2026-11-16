# Roteiro de demonstração do CRM

Uns 20 minutos, no ambiente local com os usuários de teste ([infra/README.md](../infra/README.md#realm-do-crm-interno-voe-familhas-crm)). Antes: `docker compose up -d --wait`, API (`./mvnw spring-boot:run` em `backend/familhas-api`) e CRM (`npm start`, `http://localhost:4300`).

1. **Acesso por papel.** Entre como `semacesso.crm`: barrado. Entre como `marketing.crm`: o menu só tem Marketing; abrir `/vouchers` pela URL dá "sem permissão".
2. **Visão geral** (`gestor.crm`): voos dos próximos dias, botão de olho, integrações "não configuradas" (IA, SerpApi, Meta) — nada finge funcionar.
3. **Voos Clientes** (`atendente.crm`): cadastre um voo com dois passageiros e CPF; veja o CPF mascarado; busque "conceicao joao" (sem acento, fora de ordem); anexe o bilhete em PDF e abra no visualizador.
4. **Emitir Voucher:** voucher de hotel, imprimir A4 com o cabeçalho da EEBPG, enviar pelo WhatsApp (sem CPF).
5. **Pesquisar Voos:** monte um grupo de destinos; a tela explica que a SerpApi não está configurada.
6. **Marketing** (`marketing.crm`): post de cotação com valor por pessoa, PNG, envio para a fila do Instagram; carrossel com ZIP; roteiro com avisos de horário no texto e A4.
7. **Equipe** (`admin.crm`, com TOTP): convide um e-mail, abra o convite no MailHog (`http://localhost:8025`), defina a senha; troque o papel e desative.
8. **Backup:** baixe o backup; apague um voo; restaure o arquivo (prévia → restaurar) e veja o voo voltar com o mesmo id.
9. **Privacidade:** informe o CPF de um passageiro com protocolo e identidade confirmada; gere o relatório; faça a prévia da anonimização (voo futuro fica; passado é anonimizado); registre um incidente de teste.
10. **Auditoria:** veja as ações acima, com o CPF mascarado.
11. **PWA:** no build de produção (`npm run build` servido por https ou localhost), instale o CRM pelo navegador.

Desfaça o que criou (voos, convite, incidente) se a base for usada depois.
