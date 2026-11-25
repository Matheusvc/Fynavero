# Fynavero

**Controle seus gastos e veja seu dinheiro render.**

Plataforma web de gestão financeira pessoal, multiusuário, em português do Brasil. Cada pessoa cria a própria conta, lança gastos, rendas e investimentos (ou importa a planilha que já usa) e acompanha para onde vai o dinheiro, com limites, objetivos e lembretes.

[![CI](https://github.com/Matheusvc/Fynavero/actions/workflows/ci.yml/badge.svg)](https://github.com/Matheusvc/Fynavero/actions/workflows/ci.yml)

🌐 **No ar:** https://fynavero.onrender.com · 📐 [Arquitetura](docs/ARQUITETURA.md) · 🔒 [Segurança](docs/SEGURANCA.md) · 🚀 [Guia de lançamento](docs/LANCAMENTO.md)

---

## Sumário

- [Funcionalidades](#funcionalidades)
- [Tecnologias](#tecnologias)
- [Começando](#começando)
- [Contas de exemplo](#contas-de-exemplo)
- [Produção](#produção)
- [Configuração](#configuração)
- [Testes](#testes)
- [Estrutura do código](#estrutura-do-código)
- [Segurança e privacidade](#segurança-e-privacidade)
- [Próximos passos](#próximos-passos)

## Funcionalidades

**Dia a dia**
- Lançamento rápido de despesa, receita ou investimento, com data, forma de pagamento, descrição e etiquetas (`#viagem`).
- Compras parceladas no crédito, contas que se repetem todo mês e empréstimos (recebido entra no saldo; as parcelas viram despesas futuras).
- Editar, duplicar, marcar como pago e excluir — sempre com **Desfazer**.
- Busca no mês ou no ano todo, filtros por categoria, tipo, forma de pagamento e etiqueta.
- Importação de planilhas `.xlsx`, `.xls` e `.csv` em português, com revisão antes de confirmar e desfazer o lote inteiro.

**Acompanhamento**
- **Início:** saldo para passar o mês, receitas × despesas × investido, "Para onde foi seu dinheiro", próximos vencimentos e dicas.
- **Parcelas e Dívidas:** progresso de cada parcelamento e o total comprometido nos próximos 12 meses.
- **Investimentos:** acumulado por categoria, histórico editável e gráfico de 12 meses.
- **Objetivos:** meta, prazo, barra de progresso e quanto guardar por mês para chegar lá.
- **Limites por categoria:** alerta em 80% e 100% do limite do mês.
- **Relatórios:** visão anual por categoria, comparação entre meses e exportação em Excel e PDF.
- **Ocultar valores** com um toque (o olho no saldo), como nos apps de banco.
- **Cores à sua escolha:** paleta de 12 cores para as linhas dos gráficos (Receitas, Despesas, Investido), as categorias e as etiquetas.

**Avisos**
- Sino com contas vencidas, de hoje e de amanhã (com "Marcar como paga") e limites perto do fim.
- E-mails: conta que vence amanhã (com botão "Marcar como paga"), alerta de limite e resumo do mês no dia 1 — cada um pode ser desligado.

**Conta**
- Cadastro com confirmação de e-mail, login com senha, por link no e-mail ou com Google, e "Esqueci minha senha".
- Primeiro acesso guiado: objetivos, rendas, contas fixas e contribuição (dízimo ou doação automática).
- LGPD: exportar todos os dados (JSON) e excluir a conta definitivamente.

## Tecnologias

| Camada | Tecnologia |
|---|---|
| Back-end | Java 21, Spring Boot 3.5 (Web, Security, Data JPA, Validation, OAuth2 Client, Mail, Session JDBC, Actuator), Flyway |
| Banco | PostgreSQL em produção; H2 em arquivo para desenvolvimento (sem instalar nada) |
| Front-end | React 19, TypeScript, Vite, Tailwind CSS 4, Radix UI, TanStack Query, Recharts |
| Planilhas | SheetJS (`.xlsx`/`.xls`) e Papa Parse (`.csv`), lidas no navegador |
| Exportação | Apache POI (Excel) e OpenPDF (PDF) |
| E-mail | API do Brevo ou qualquer servidor SMTP |
| Infra | Docker, Render (ou Railway), GitHub Actions (testes e backup diário) |

## Começando

Pré-requisitos: **Java 21**, **Maven 3.9+** e **Node 20+**.

```bash
# 1. Back-end em http://localhost:8080 — na primeira execução cria o banco e as contas de exemplo
cd backend
mvn spring-boot:run

# 2. Front-end em http://localhost:5180 (em outro terminal)
cd frontend
npm install
npm run dev
```

Abra **http://localhost:5180**. O Vite encaminha `/api` para o back-end, então login e sessão funcionam na mesma origem.

Sem servidor de e-mail configurado, os e-mails (confirmação, senha, avisos) aparecem no **log do back-end**, com os links prontos para abrir.

## Contas de exemplo

Criadas automaticamente em desenvolvimento (`DEMO_DATA=true`, padrão com H2), com 6 meses de histórico:

| E-mail | Senha | Perfil |
|---|---|---|
| `ana@fynavero.dev` | `Fynavero@2026` | Organizar os gastos: dízimo de 10%, notebook em 10×, objetivos de investimento |
| `joao@fynavero.dev` | `Fynavero@2026` | Sair das dívidas: salário de R$ 3.575, geladeira em 12× e empréstimo em 24× |

Para recomeçar do zero, apague a pasta `backend/data`. Em produção as contas de exemplo ficam desligadas.

## Produção

**Um arquivo só (`.jar` com o front embutido):**

```bash
./build.sh            # ou .\build.ps1 no Windows
java -jar backend/target/fynavero.jar   # http://localhost:8080
```

**Docker:**

```bash
docker build -t fynavero .
docker run -p 8080:8080 -e DATABASE_URL=postgres://usuario:senha@host:5432/fynavero -e APP_BASE_URL=https://seu-dominio fynavero
```

A imagem já sobe no modo produção: PostgreSQL, sessões salvas no banco (reiniciar não desconecta ninguém), cookie só em HTTPS e IP real atrás do proxy.

**Render:** o [`render.yaml`](render.yaml) cria o app e o PostgreSQL de uma vez (*New › Blueprint*). O passo a passo completo — e-mail, domínio próprio e backup diário criptografado — está em [docs/LANCAMENTO.md](docs/LANCAMENTO.md).

## Configuração

Todas as variáveis são opcionais; [`.env.example`](.env.example) traz cada uma com exemplo.

| Variável | Padrão | Para quê |
|---|---|---|
| `DATABASE_URL` | H2 em `backend/data` | Banco. Aceita `jdbc:postgresql://…` ou `postgres://usuario:senha@host/banco` (formato da Render/Railway) |
| `SPRING_PROFILES_ACTIVE` | — | `postgres` liga o modo produção (o Docker já define) |
| `APP_BASE_URL` | `http://localhost:5180` | Endereço público do site, usado nos links dos e-mails |
| `BREVO_API_KEY` | — | Envio de e-mail pela API do Brevo (recomendado) |
| `SPRING_MAIL_HOST`, `_PORT`, `_USERNAME`, `_PASSWORD` | — | Envio por SMTP, alternativa à API |
| `MAIL_FROM` | `Fynavero <nao-responda@…>` | Remetente dos e-mails |
| `COOKIE_SECURE`, `FORWARD_HEADERS_STRATEGY` | `false`, `none` | Use `true` e `framework` atrás de HTTPS/proxy (o Docker já define) |
| `DEMO_DATA` | `true` no H2, `false` no PostgreSQL | Cria as contas de exemplo |
| `LOG_MAIL_CONTENT` | `true` no H2, `false` no PostgreSQL | Sem servidor de e-mail, mostra o conteúdo (com os links) no log — nunca ligue em produção |
| `SPRING_SECURITY_OAUTH2_CLIENT_REGISTRATION_GOOGLE_CLIENT_ID` / `_CLIENT_SECRET` | — | Liga "Continuar com Google" (retorno: `{APP_BASE_URL}/login/oauth2/code/google`) |

## Testes

```bash
cd backend && mvn test              # cálculo, aceite de ponta a ponta, ataques de segurança e um teste num PostgreSQL de verdade (embutido)
cd frontend && npm test             # leitor de planilhas
cd frontend && npm run typecheck    # tipos do TypeScript
cd frontend && npm run check:tokens # nenhuma cor, fonte ou sombra fixa fora da identidade visual
```

O GitHub Actions roda tudo isso a cada push e pull request.

Os testes de aceite cobrem, entre outros: isolamento entre contas, R$ 4.900 em 10× = 10 parcelas de R$ 490, contribuição de 10% sobre R$ 3.575 = R$ 357,50, investimento reduzindo o saldo sem mudar as despesas, excluir e desfazer, importar e desfazer o lote, limites de categoria, objetivos, "Marcar como paga" pelo e-mail, redefinição de senha, confirmação de e-mail, limite de cadastros e exclusão da conta.

## Estrutura do código

Organizado **por área do negócio** nas duas pontas. Regras de cálculo e decisões de arquitetura em [docs/ARQUITETURA.md](docs/ARQUITETURA.md).

```
backend/src/main/java/br/com/fynavero/
  account/       perfil, login, links por e-mail, primeiro acesso e LGPD
  category/      categorias e catálogo de sugestões
  ledger/        lançamentos, recorrências, parcelas, rendas e o FinanceCalculator (único módulo de cálculo)
  importing/     importação de planilhas
  overview/      Início, Parcelas, Investimentos e Relatórios
  planning/      limites por categoria e objetivos
  notification/  sino, e-mails agendados e "Marcar como paga"
  security/      sessão, CSRF, limite de tentativas e Google
  shared/        erros, formatação pt-BR, e-mail, configuração e contas de exemplo
backend/src/main/resources/db/migration     versões do banco (Flyway)

frontend/src/
  app/           rotas e casca (menu, cabeçalho, sino, seletor de mês)
  features/      uma pasta por tela: auth, onboarding, home, transactions, installments,
                 investments, goals, reports, import, settings, notifications, legal, design-system
  components/    componentes reutilizáveis (ui, charts)
  lib/           API, TanStack Query, formatação, tipos e "ocultar valores"
  styles/        tokens.css — a identidade visual Fynavero (único lugar com valores visuais)
```

Cada pacote do back-end segue o mesmo molde: entidades (com o repositório no mesmo arquivo), poucos serviços, **um controller** e **um arquivo de DTOs**.

## Segurança e privacidade

Cada item da lista de pentest web (SQL Injection, XSS, CSRF, IDOR, força bruta, sessão, CORS, path traversal, upload, clickjacking, condição de corrida…) tem proteção e um teste que tenta o ataque — tabela completa em [docs/SEGURANCA.md](docs/SEGURANCA.md).

- Toda consulta ao banco é filtrada pela pessoa logada; recursos de outra conta respondem 404.
- Sessão em cookie `httpOnly` + `SameSite=Lax`, token CSRF em toda alteração, senhas com BCrypt e Content-Security-Policy restritiva.
- Limites contra abuso: bloqueio de 15 minutos após 5 senhas erradas (por conta) ou 30 (por rede), até 5 contas novas por hora por rede e limite de e-mails de link.
- Trocar ou redefinir a senha encerra as sessões nos outros aparelhos.
- Cabeçalhos de proteção: CSP, HSTS, X-Frame-Options, nosniff, Permissions-Policy e Cross-Origin-Opener-Policy; CORS desligado.
- A API não recebe arquivos e limita o tamanho dos pedidos.
- Links enviados por e-mail são de uso único e expiram; no banco fica só o hash SHA-256.
- LGPD: aceite dos termos registrado, exportação de todos os dados e exclusão definitiva da conta.
- A planilha importada é lida no navegador e descartada; só os lançamentos confirmados são enviados.
- A Fynavero organiza o que você investe, mas não faz recomendação de investimento.

## Próximos passos

Open Finance, contas compartilhadas (casal ou família), notificações push, planos pagos e painel administrativo. O `user_id` em todas as tabelas e a separação por áreas permitem adicionar esses itens sem reescrever o núcleo.
