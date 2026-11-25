# Segurança da Fynavero

Como a plataforma se protege de cada item da lista de pentest web (OWASP). Cada proteção tem um teste automático em
[`SecurityTest`](../backend/src/test/java/br/com/fynavero/SecurityTest.java) (ou no `AcceptanceTest`) que tenta o ataque e confere que ele falha. Os testes rodam a cada push no GitHub Actions.

| Ataque | Como a Fynavero se protege | Teste |
|---|---|---|
| **SQL Injection** | Todas as consultas usam JPA com parâmetros (`:userId`, métodos derivados); nenhum SQL é montado com texto digitado. | `sqlInjectionNaBuscaNaoVazaDadosNemQuebra` |
| **XSS** | React escapa todo texto; não usamos `dangerouslySetInnerHTML`. E-mails escapam o HTML (`Mailer.esc`). Content-Security-Policy só deixa rodar scripts do próprio site (`script-src 'self'`, `object-src 'none'`). | `xssEhGuardadoComoTextoEDevolvidoComoJson` |
| **CSRF** | Token CSRF (cookie `XSRF-TOKEN` + cabeçalho `X-XSRF-TOKEN`) exigido em toda alteração; cookie de sessão `SameSite=Lax`. | `alteracaoSemTokenCsrfEhRecusada` |
| **Broken Access Control / IDOR** | Toda consulta filtra pela pessoa logada (`userId`); recurso de outra conta responde 404/400, sem dados. `/api/**` exige login. | `outraContaNaoAlteraNemLeRecursosAlheios`, `semLoginNadaDaApiAbre`, `duasContasNuncaEnxergamOsDadosUmaDaOutra` |
| **Authentication Bypass** | Só `/api/auth/**` e os links de e-mail (token de 256 bits) são públicos; o resto responde 401. Senhas com BCrypt. | `semLoginNadaDaApiAbre` |
| **Brute Force** | 5 senhas erradas bloqueiam a conta por 15 min; 30 falhas da mesma rede bloqueiam a rede; limites em cadastro, links por e-mail, troca de senha e links de uso único. | `forcaBrutaEhBloqueadaPorContaEPorRede` |
| **Session Fixation** | O id da sessão muda a cada login (e-mail e senha, link e Google). | `loginTrocaOIdDaSessaoContraFixacao` |
| **Session Hijacking** | Cookie `httpOnly`, `Secure` (HTTPS) e `SameSite`; HSTS de 1 ano. Trocar ou redefinir a senha encerra as sessões nos outros aparelhos e renova a atual. | `trocarASenhaRenovaASessaoEExigeASenhaAtual` |
| **JWT Misconfiguration** | Não se aplica: não usamos JWT. A sessão fica no servidor (banco, em produção). | — |
| **CORS Misconfiguration** | CORS desligado: nenhum outro domínio lê as respostas da API nem envia cookies. | `outroSiteNaoLeARespostaDaApi` |
| **Sensitive Data Exposure** | Respostas nunca trazem hash de senha; erros sem detalhes internos; Actuator só com `health` sem detalhes; tokens de link guardados só como hash SHA-256 e enviados no corpo (não na URL); em produção o conteúdo dos e-mails não vai para o log. | `respostasNaoExpoemSenhaNemDetalhesInternos` |
| **SSRF** | Não se aplica: a API não acessa URLs informadas pela pessoa (o único acesso externo é a API fixa do Brevo). | — |
| **Path Traversal / Local File Inclusion** | Arquivos só saem da pasta `static/` do front-end; caminhos com `..`, `\`, `%` ou `:` são recusados (e o firewall do Spring Security barra antes). | `caminhoComPontoPontoNaoLeArquivosDoServidor` |
| **Remote File Inclusion** | Não se aplica: nada é carregado por nome ou URL vindo da pessoa. | — |
| **File Upload / Unrestricted File Upload** | A API não recebe arquivos: planilhas são lidas no navegador e só os lançamentos (JSON) chegam ao servidor. `multipart` é recusado (415) e o corpo é limitado a 8 MB (413). | `uploadDeArquivoEPedidoGiganteSaoRecusados` |
| **Open Redirect** | Os redirecionamentos do servidor usam só o endereço fixo do site (`APP_BASE_URL`); nenhum parâmetro decide para onde ir. | — |
| **Clickjacking** | `X-Frame-Options: DENY` e `frame-ancestors 'none'`: o site não abre dentro de `<iframe>`. | `cabecalhosDeSegurancaContraClickjackingESniffing` |
| **Race Condition** | Link de uso único é marcado como usado num `UPDATE ... WHERE used_at IS NULL` atômico: dois cliques ao mesmo tempo, só um vale. Contador de tentativas também é atômico. | `linkDeUsoUnicoUsadoDuasVezesAoMesmoTempoSoFuncionaUmaVez` |

Cabeçalhos enviados em toda resposta: `Content-Security-Policy`, `X-Frame-Options`, `X-Content-Type-Options: nosniff`,
`Strict-Transport-Security` (em HTTPS), `Referrer-Policy`, `Permissions-Policy` (câmera, microfone, localização e pagamento bloqueados),
`Cross-Origin-Opener-Policy` e `Cross-Origin-Resource-Policy`.

## Limites conhecidos

- O contador de tentativas fica na memória de cada servidor: com mais de uma instância, use Redis.
- O plano grátis da Render não tem backup automático do banco; veja [LANCAMENTO.md](LANCAMENTO.md).
- Um pentest manual por terceiros continua recomendado antes de crescer.
