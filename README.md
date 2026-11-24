# Fynavero

Plataforma web de gestão financeira pessoal, multiusuário, em português do Brasil.
Cada pessoa cria a conta, escolhe as próprias categorias, lança gastos, rendas e investimentos, importa a planilha que já usa e acompanha para onde vai o dinheiro.

Também tem: etiquetas (#viagem) e busca no ano todo, limite de gasto por categoria com alerta em 80% e 100%, objetivos de economia com prazo e barra de progresso, sino de avisos e e-mails (conta que vence amanhã com "Marcar como paga", limites e resumo do mês no dia 1).

| Camada | Tecnologia |
|---|---|
| Back-end | Java 21, Spring Boot 3.5 (Web, Security, Data JPA, Validation, OAuth2 Client, Mail), Flyway |
| Banco | H2 em arquivo (padrão, sem instalar nada) ou PostgreSQL (perfil `postgres`) |
| Front-end | React 19 + TypeScript + Vite, Tailwind CSS 4, componentes no padrão shadcn/ui (Radix), Recharts, React Query |
| Planilhas | SheetJS (.xlsx/.xls) e Papa Parse (.csv), lidos no navegador |
| Exportação | Apache POI (Excel) e OpenPDF (PDF), gerados no back-end |

## Rodar em desenvolvimento

Pré-requisitos: **Java 21**, **Maven 3.9+** e **Node 20+**.

```bash
# 1. Back-end (porta 8080). Na primeira execução cria o banco e os dados de exemplo.
cd backend
mvn spring-boot:run

# 2. Front-end (porta 5180), em outro terminal
cd frontend
npm install
npm run dev
```

Abra **http://localhost:5180**. O Vite encaminha `/api` para o back-end, então a sessão funciona na mesma origem.

## Um único arquivo para rodar (produção)

```bash
cd frontend && npm install && npm run build      # gera frontend/dist
cd ../backend && mvn package                      # embute o front no .jar
java -jar target/fynavero.jar                     # http://localhost:8080
```

Ou, na raiz, `./build.ps1` (Windows) / `./build.sh` (Linux/macOS) fazem os dois passos.

## Contas de exemplo

Criadas automaticamente quando `DEMO_DATA=true` (padrão no H2 local), com 6 meses de histórico:

| E-mail | Senha | Perfil |
|---|---|---|
| `ana@fynavero.dev` | `Fynavero@2026` | Objetivo "Organizar meus gastos", dízimo de 10%, notebook em 10×, metas de investimento |
| `joao@fynavero.dev` | `Fynavero@2026` | Objetivo "Sair das dívidas", salário de R$ 3.575, geladeira em 12× e empréstimo em 24× |

Para começar com o banco limpo, apague a pasta `backend/data`.

## Configuração (variáveis de ambiente)

Todas são opcionais; `.env.example` lista cada uma com um exemplo.

| Variável | Padrão | Para quê |
|---|---|---|
| `DATABASE_URL`, `DATABASE_USER`, `DATABASE_PASSWORD` | H2 em `./data` | Banco de dados. Com `SPRING_PROFILES_ACTIVE=postgres`, usa PostgreSQL |
| `APP_BASE_URL` | `http://localhost:5180` | Endereço público do front (links mágicos e retorno do Google). Em produção com o .jar: `http://localhost:8080` |
| `COOKIE_SECURE` | `false` | Use `true` atrás de HTTPS |
| `DEMO_DATA` | `true` (H2) / `false` (postgres) | Cria as contas de exemplo |
| `SPRING_MAIL_HOST`, `SPRING_MAIL_PORT`, `SPRING_MAIL_USERNAME`, `SPRING_MAIL_PASSWORD`, `MAIL_FROM` | — | Envio do link mágico. **Sem servidor de e-mail, o link aparece no log do back-end** |
| `SPRING_SECURITY_OAUTH2_CLIENT_REGISTRATION_GOOGLE_CLIENT_ID` e `..._CLIENT_SECRET` | — | Ativa "Continuar com Google". Sem elas, o botão não aparece. URI de retorno: `{APP_BASE_URL}/login/oauth2/code/google` |

## Testes

```bash
cd backend && mvn test          # 15 testes do módulo de cálculo + 13 testes de aceite de ponta a ponta
cd frontend && npm test         # 25 testes do leitor de planilhas
cd frontend && npm run check:tokens   # garante que não há cor/fonte/sombra fixa fora dos tokens
```

O GitHub Actions (`.github/workflows/ci.yml`) roda tudo isso a cada push e pull request.

Os testes de aceite cobrem: isolamento entre contas, R$ 4.900 em 10× = 10 parcelas de R$ 490, contribuição de 10% sobre R$ 3.575 = R$ 357,50, investimento reduzindo o saldo sem alterar despesas, excluir/desfazer, importar e desfazer só aquele lote, relatório anual com exportação e exclusão definitiva da conta.

## Estrutura

Código organizado **por funcionalidade** nas duas pontas. Detalhes, regras de cálculo e decisões em [docs/ARQUITETURA.md](docs/ARQUITETURA.md).

```
backend/src/main/java/br/com/fynavero/
  account/  category/  ledger/  income/  onboarding/  overview/  report/  importing/
            ← cada pasta: entidade, repositório, DTOs, serviço e controlador REST
  calc/FinanceCalculator.java   ← único módulo de cálculo (Início, Relatórios e exportação)
  security/                     ← sessão por cookie httpOnly, CSRF, bloqueio após 5 tentativas, Google
  common/  config/  domain/     ← erros em português, formatação pt-BR, propriedades e enums
  demo/                         ← contas de exemplo
backend/src/main/resources/db/migration   ← esquema e catálogo de categorias (Flyway)

frontend/src/
  app/                          ← rotas e casca (menu, cabeçalho, seletor de mês)
  features/                     ← auth, onboarding, home, transactions, installments, investments,
                                  reports, import, settings, legal, design-system
  components/ui, components/charts   ← componentes visuais reutilizáveis
  lib/                          ← API, React Query, formatação, tipos
  styles/tokens.css             ← identidade visual Fynavero (único lugar com valores visuais)
```

## Segurança e privacidade

- Cada consulta ao banco recebe o id da pessoa logada; recursos de outra conta respondem 404 (equivalente à Row Level Security).
- Sessão em cookie `httpOnly` + `SameSite=Lax`, token CSRF em toda alteração, senha com BCrypt, CSP restritiva e bloqueio de 15 minutos após 5 senhas erradas.
- LGPD: aceite dos termos registrado no cadastro, exportação de todos os dados (JSON) e exclusão definitiva da conta em Configurações › Privacidade e dados.
- A planilha importada é lida no navegador e descartada; só os lançamentos confirmados são enviados.
- A plataforma não faz recomendação de investimento.

## Preparado para depois (não implementado)

Open Finance, contas compartilhadas, planos pagos, painel administrativo, notificações push e recursos de IA. A separação em serviços e o `user_id` em todas as tabelas permitem adicionar esses itens sem reescrever o núcleo.
