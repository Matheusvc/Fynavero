# Lançamento da Fynavero

Guia para colocar a plataforma no ar para pessoas de verdade. Tudo o que é código já está pronto no repositório; os passos abaixo são as contas e configurações que só você (dono do projeto) pode criar.

## O que já vem pronto

| Item | Como funciona |
|---|---|
| Banco PostgreSQL | Perfil `postgres` (ligado automaticamente no Docker). Aceita `DATABASE_URL` no formato `postgres://usuario:senha@host/banco` que Render e Railway entregam. Testado num PostgreSQL de verdade (`PostgresProductionTest`). |
| Sessões salvas no banco | Em produção, reiniciar ou publicar uma versão nova não desconecta ninguém. Redefinir a senha encerra as outras sessões da pessoa. |
| Esqueci minha senha | Link de uso único, válido por 1 hora. |
| Confirmação de e-mail | Enviada no cadastro (vale 2 dias). Sem confirmar, a pessoa usa o app normalmente, mas não recebe e-mails de aviso. Contas antigas, de exemplo e do Google já contam como confirmadas. |
| Limites contra robôs | 5 contas novas por hora por rede; 5 e-mails de link por hora por endereço e 20 por rede; bloqueio de 15 min após 5 senhas erradas. |
| HTTPS e IP real | O Docker já liga `COOKIE_SECURE=true` e `FORWARD_HEADERS_STRATEGY=framework`. |
| Health check | `GET /actuator/health` (usado pela hospedagem). |
| Backup diário | `.github/workflows/backup.yml`: `pg_dump` criptografado com AES-256, guardado 30 dias. |

## Passo 1 — E-mail (Brevo ou Resend)

Os dois têm plano gratuito suficiente para começar (confira os limites atuais no site de cada um).

1. Crie a conta no serviço escolhido.
2. Cadastre e **verifique o seu domínio** (ex.: `seudominio.com.br`), adicionando no DNS os registros SPF, DKIM e DMARC que o serviço mostrar. Sem isso, os e-mails caem no spam.
3. Gere as credenciais SMTP e anote:

| | Brevo | Resend |
|---|---|---|
| `SPRING_MAIL_HOST` | `smtp-relay.brevo.com` | `smtp.resend.com` |
| `SPRING_MAIL_PORT` | `587` | `587` |
| `SPRING_MAIL_USERNAME` | login SMTP mostrado no painel | `resend` |
| `SPRING_MAIL_PASSWORD` | chave SMTP | chave de API (`re_…`) |
| `MAIL_FROM` | `Fynavero <nao-responda@seudominio.com.br>` | idem |

O remetente de `MAIL_FROM` precisa ser do domínio verificado.

## Passo 2 — Hospedagem

### Opção A: Render (recomendada para começar)

O `render.yaml` vem com os planos **grátis**, para testar. Limites do grátis: o site dorme após ~15 min sem visitas (a primeira visita depois demora ~1 min e os e-mails agendados podem não sair) e o banco expira em ~30 dias. Antes de abrir para pessoas de verdade, mude no painel da Render o site para **Starter** e o banco para **Basic**.

1. Crie a conta em render.com e conecte o GitHub.
2. **New › Blueprint**, escolha o repositório `Fynavero`. O arquivo `render.yaml` cria o app e o PostgreSQL.
3. Preencha as variáveis pedidas: `APP_BASE_URL` (ex.: `https://fynavero.onrender.com`, ou o seu domínio) e as do e-mail (passo 1).
4. Aguarde o primeiro deploy (o build Docker leva alguns minutos). Abra a URL e crie a sua conta.

### Opção B: Railway

1. Crie a conta em railway.com e conecte o GitHub.
2. **New Project › Deploy from GitHub repo › Fynavero** (o `railway.json` usa o Dockerfile).
3. No mesmo projeto, **New › Database › PostgreSQL**.
4. No serviço do app, em **Variables**: `DATABASE_URL = ${{Postgres.DATABASE_URL}}`, `APP_BASE_URL`, e as variáveis de e-mail.
5. Em **Settings › Networking**, gere o domínio público (ou ligue o seu).

### Domínio próprio (opcional)

Aponte um `CNAME` (ex.: `app.seudominio.com.br`) para o endereço da hospedagem, adicione o domínio no painel e atualize `APP_BASE_URL`. O certificado HTTPS é emitido automaticamente.

### Login com Google (opcional)

No Google Cloud Console, crie credenciais OAuth (aplicativo web) com URI de redirecionamento `{APP_BASE_URL}/login/oauth2/code/google` e defina `SPRING_SECURITY_OAUTH2_CLIENT_REGISTRATION_GOOGLE_CLIENT_ID` e `..._CLIENT_SECRET`.

## Passo 3 — Backup e restauração

1. Pegue a **URL externa** do banco no painel da hospedagem (com `?sslmode=require` no fim, se não vier).
2. No GitHub: **Settings › Secrets and variables › Actions › New repository secret**:
   - `DATABASE_BACKUP_URL` = URL externa do banco
   - `BACKUP_PASSPHRASE` = uma frase longa só sua. **Guarde num gerenciador de senhas**: sem ela, o backup não abre.
3. Em **Actions › Backup diário do banco › Run workflow**, rode uma vez para testar. Depois ele roda sozinho todo dia às 3h.

Para restaurar (teste primeiro num banco novo):

```sh
TARGET_DATABASE_URL='postgres://usuario:senha@host:5432/banco_teste' ./scripts/restore-postgres.sh fynavero-AAAA-MM-DD.dump.gpg
```

Atenção: num repositório **público**, qualquer pessoa logada no GitHub consegue baixar os artefatos. Os backups estão criptografados, mas o mais seguro é deixar o repositório **privado** (Settings › General › Danger Zone › Change visibility). Os planos pagos de PostgreSQL da Render e da Railway também têm backups automáticos próprios; vale ligar os dois.

## Checklist antes de divulgar

- [ ] Criar uma conta pela URL pública e receber o e-mail de confirmação (fora do spam).
- [ ] Testar "Esqueci minha senha" de ponta a ponta.
- [ ] Lançar uma conta com vencimento amanhã e conferir o lembrete às 8h do dia seguinte.
- [ ] Rodar o backup manualmente uma vez e restaurar num banco de teste.
- [ ] Revisar os textos de Termos e Privacidade (`/termos` e `/privacidade`) com quem cuida da parte jurídica.
- [ ] Conferir que `DEMO_DATA` está `false` em produção (é o padrão do perfil `postgres`).

## Limites conhecidos

- O limite de pedidos e o bloqueio de senha ficam na memória de uma instância. Para rodar várias instâncias ao mesmo tempo, mova-os para Redis.
- As tarefas de e-mail (8h, 20h e dia 1) rodam em cada instância; com mais de uma, ligue o agendamento só em uma delas ou adote um lock distribuído (ex.: ShedLock).
