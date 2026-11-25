# Arquitetura do Fynavero

Monólito modular: um back-end Spring Boot que expõe uma API REST em `/api` e, em produção, serve o front-end React compilado no mesmo `.jar`. Em desenvolvimento, o Vite (porta 5180) encaminha `/api` para o back-end (8080), então a sessão e o CSRF funcionam na mesma origem.

```
navegador ── React (features/*) ── React Query ── /api ── Controller ── Service ── Repository ── banco
                                                              │
                                                     calc/FinanceCalculator (puro, sem banco)
```

## Back-end: 9 pacotes por área de negócio

Cada pacote segue o mesmo molde: entidades (cada uma com o seu repositório Spring Data no mesmo arquivo, ex.: `Transaction.TransactionRepository`), poucos serviços, **um único controller** e **um único arquivo de DTOs**. Tipos básicos ficam aninhados em quem os possui (`Transaction.Kind`, `Transaction.Status`, `Profile.Objective`, `AccountToken.Purpose`). Ao todo, 61 classes. Proteções de segurança e os testes de cada uma: [SEGURANCA.md](SEGURANCA.md).

Cada pasta junta tudo de um assunto: entidade, repositório, DTOs, serviço e controlador. Um assunto só usa outro pelo serviço público dele, nunca pelo repositório.

| Pacote | Responsabilidade |
|---|---|
| `account` | Perfil, cadastro e login (senha, link por e-mail, Google), confirmação de e-mail, "Esqueci minha senha", primeiro acesso (onboarding), contribuição e LGPD (exportar e excluir). Links enviados por e-mail ficam numa única tabela (`AccountLinks`) |
| `category` | Catálogo de sugestões e categorias de cada pessoa (incluindo "Empréstimo" e "Pagamento de empréstimo") |
| `ledger` | O núcleo financeiro: lançamentos, recorrências (contas fixas), parcelamentos, fontes de renda, etiquetas, os tipos básicos (`Transaction.Kind`, `Transaction.Status`…) e o `FinanceCalculator`, **único** lugar com fórmulas financeiras |
| `importing` | Lotes de importação de planilha (confirmar e desfazer o lote inteiro) |
| `overview` | Telas de leitura: Início, Parcelas e Dívidas, Investimentos, resumo do mês e Relatórios (com exportação em Excel/PDF) |
| `planning` | Limites de gasto por categoria (alerta em 80% e 100%) e objetivos de economia com meta e prazo |
| `notification` | Sino de avisos, preferências, e-mails agendados (contas de amanhã, limites, resumo do dia 1) e o link "Marcar como paga" |
| `security` | Cadeia de filtros, sessão por cookie, CSRF para SPA, limite de tentativas (`RateLimiter`), login com Google |
| `shared` | Infraestrutura comum: erros em português (`Errors`, `ApiExceptionHandler`), formatação pt-BR (`Formats`), envio de e-mail com o modelo visual (`Mailer`), configuração (`AppProperties`, `WebConfig`, `DatabaseUrlProcessor`) e contas de exemplo (`DemoDataSeeder`) |

### Regras de cálculo

Todos os valores trafegam e são guardados em **centavos inteiros** (`long` no Java, `number` no TypeScript), com `Math.addExact` para falhar em vez de transbordar.

```
Contribuição   = ativa ? (Receitas − Empréstimos recebidos) × percentual : 0
Despesas       = despesas lançadas + Contribuição
Sobra do mês   = Receitas − Despesas
Saldo do mês   = Sobra − Investido          (investimento não é despesa)
```

Empréstimo recebido entra como receita no saldo, mas não é renda: fica fora da base da contribuição e aparece separado no relatório anual. As parcelas do pagamento são despesas na categoria "Pagamento de empréstimo".

### Avisos por e-mail

`NotificationJobs` roda às 8h (contas que vencem amanhã + limites), às 20h (limites) e no dia 1 às 9h (resumo do mês anterior), no horário de Brasília. Cada aviso enviado fica em `notification_log`, então nunca sai duas vezes. O botão "Marcar como paga" do e-mail leva a `/conta-paga?token=…`, que mostra a conta e pede confirmação antes de marcar (leitores de e-mail que abrem links sozinhos não marcam nada); o token vale só para aquela conta, até 7 dias depois do vencimento, e no banco fica só o hash. Sem servidor de e-mail configurado, o conteúdo vai para o log.

### Decisões

- **Entidades com campos públicos.** As entidades JPA são registros de dados simples, sem regra de negócio; getters e setters não protegeriam nada e só aumentariam o código. As regras ficam nos serviços.
- **Sem associações JPA.** As entidades guardam ids (`categoryId`, `planId`…) em vez de `@ManyToOne`. Toda consulta recebe o `userId` da sessão, o que substitui a Row Level Security: um recurso de outra conta responde 404. Isso também evita carregamento preguiçoso fora de transação (`open-in-view: false`).
- **Cálculo isolado.** `FinanceCalculator` não conhece banco nem Spring, e por isso tem testes unitários rápidos. Início, Relatórios e exportação chamam o mesmo código, então os números sempre batem.
- **Relógio injetável.** Tudo que depende de "hoje" usa o `Clock` do `TimeConfig` e o fuso `America/Sao_Paulo`, o que deixa os testes determinísticos.
- **Migrações versionadas.** O esquema muda só por Flyway (`db/migration/V*.sql`); o Hibernate apenas valida (`ddl-auto: validate`).

### Segurança

- Sessão em memória no cookie `FYN_SESSION` (`httpOnly`, `SameSite=Lax`, `Secure` atrás de HTTPS). O id da sessão é trocado no login.
- CSRF com cookie `XSRF-TOKEN` e cabeçalho `X-XSRF-TOKEN` em toda alteração.
- Senhas com BCrypt; 5 tentativas erradas bloqueiam o e-mail por 15 minutos (`LoginThrottle`).
- Content-Security-Policy restritiva, sem scripts de terceiros.
- Exclusão de conta apaga todas as linhas da pessoa; lançamentos excluídos ficam na lixeira para o "Desfazer" e são removidos de vez pela tarefa agendada da madrugada (3h30), depois de 1 dia.

## Front-end: pastas por funcionalidade

```
src/
  app/            App (rotas, guardas de login) e AppShell (menu, cabeçalho, seletor de mês)
  features/
    auth/         Entrar, Criar conta, Link mágico
    onboarding/   Primeiro acesso
    home/         Início: resumo do mês, "Para onde foi seu dinheiro", próximos vencimentos
    transactions/ Lançamentos e quick-add/ (ficha de lançamento usada em todo o app)
    installments/ Parcelas e Dívidas
    investments/  Investimentos
    goals/        Objetivos de economia (meta, prazo, quanto guardar por mês)
    notifications/ Página do link "Marcar como paga" do e-mail
    reports/      Relatórios
    import/       Importação de planilhas (parser.ts com testes, leitura de arquivo, revisão, histórico)
    settings/     Configurações (uma seção por arquivo em sections/) e a página "Mais"
    legal/        Termos e Privacidade
    design-system/ Vitrine dos componentes e tokens
  components/
    ui/           Componentes básicos (button, card, badge, field, dialog, feedback…)
    charts/       Rosca e barras (Recharts) com alternativa em tabela
  lib/            API (fetch + CSRF), React Query, formatação pt-BR, rótulos, tipos
  styles/tokens.css  Identidade visual: único lugar com cores, fontes e sombras
```

- `components/` não conhece regra de negócio; `features/` pode usar `components/` e `lib/`, mas uma feature não importa outra, com duas exceções explícitas: a ficha de lançamento (`transactions/quick-add`) e o histórico de importações exibido em Configurações.
- Estado do servidor só via React Query (`lib/queries.ts`); depois de qualquer alteração financeira, `invalidateFinance` atualiza Início, Lançamentos, Parcelas e Relatórios de uma vez.
- `npm run check:tokens` falha se aparecer cor, fonte ou sombra fixa fora de `tokens.css`.
- Páginas pesadas (Relatórios, Importar, Configurações…) são carregadas sob demanda; a leitura de `.xlsx` carrega o SheetJS só quando um arquivo é escolhido.

## Dados

- **Desenvolvimento:** H2 em arquivo em `backend/data/` (modo PostgreSQL). A pasta está no `.gitignore` e nunca vai para o repositório, porque guarda dados pessoais. Para recomeçar do zero, apague-a.
- **Produção:** PostgreSQL com `SPRING_PROFILES_ACTIVE=postgres`; o mesmo conjunto de migrações roda nos dois bancos.
- Configuração por variáveis de ambiente; veja `.env.example`.
