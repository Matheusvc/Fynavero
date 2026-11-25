package br.com.fynavero;

import br.com.fynavero.account.AccountLinks;
import br.com.fynavero.account.AccountToken.Purpose;
import br.com.fynavero.account.Profile.ProfileRepository;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Duration;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.ThreadLocalRandom;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;

/**
 * Testes de segurança: cada teste tenta um ataque da lista de pentest web (OWASP) e confere que ele falha.
 * JWT, SSRF e inclusão remota de arquivo não se aplicam: não usamos JWT, a API não busca URLs informadas
 * pela pessoa e não carrega arquivos por nome. O isolamento entre contas (IDOR) também é coberto no AcceptanceTest.
 */
@SpringBootTest
@AutoConfigureMockMvc
class SecurityTest {
    @Autowired MockMvc mvc;
    @Autowired ObjectMapper json;
    @Autowired AccountLinks links;
    @Autowired ProfileRepository profiles;

    private final String today = LocalDate.now(java.time.ZoneId.of("America/Sao_Paulo")).toString();

    private MockHttpServletResponse send(MockHttpSession s, MockHttpServletRequestBuilder req, String body) throws Exception {
        if (s != null) req.session(s);
        req.with(csrf()).contentType(MediaType.APPLICATION_JSON);
        if (body != null) req.content(body);
        return mvc.perform(req).andReturn().getResponse();
    }

    private JsonNode ok(MockHttpSession s, MockHttpServletRequestBuilder req, String body) throws Exception {
        MockHttpServletResponse res = send(s, req, body);
        assertThat(res.getStatus()).as(res.getContentAsString()).isEqualTo(200);
        return json.readTree(res.getContentAsString());
    }

    private static RequestPostProcessor ip(String ip) {
        return r -> {
            r.setRemoteAddr(ip);
            return r;
        };
    }

    private static String randomIp() {
        var rnd = ThreadLocalRandom.current();
        return "10." + rnd.nextInt(256) + "." + rnd.nextInt(256) + "." + rnd.nextInt(1, 255);
    }

    private record User(MockHttpSession session, String email) {}

    private User newUser() throws Exception {
        MockHttpSession s = new MockHttpSession();
        String email = "s" + UUID.randomUUID().toString().substring(0, 8) + "@teste.dev";
        ok(s, post("/api/auth/register").with(ip(randomIp())),
                "{\"name\":\"Pessoa\",\"email\":\"" + email + "\",\"password\":\"senhaforte1\",\"acceptTerms\":true}");
        return new User(s, email);
    }

    private long category(MockHttpSession s, String name) throws Exception {
        return ok(s, post("/api/categories"), "{\"name\":\"" + name + "\",\"kind\":\"DESPESA\"}").get("id").asLong();
    }

    private JsonNode launch(MockHttpSession s, long cat, String description) throws Exception {
        return ok(s, post("/api/transactions"), "{\"kind\":\"DESPESA\",\"amountCents\":1000,\"categoryId\":" + cat
                + ",\"date\":\"" + today + "\",\"description\":" + json.writeValueAsString(description) + "}").get(0);
    }

    // ------------------------------------------------------------------ injeção e XSS

    @Test
    void sqlInjectionNaBuscaNaoVazaDadosNemQuebra() throws Exception {
        User a = newUser(), b = newUser();
        launch(a.session(), category(a.session(), "Mercado"), "Compra de A");
        for (String attack : List.of("' OR '1'='1", "x' OR 1=1 --", "'; DROP TABLE transactions; --", "%' UNION SELECT * FROM profiles --")) {
            JsonNode found = ok(b.session(), get("/api/transactions/search").param("q", attack), null);
            assertThat(found.get("items")).isEmpty();
            ok(b.session(), get("/api/transactions/search").param("tag", attack), null);
        }
        assertThat(ok(a.session(), get("/api/transactions/search").param("q", "compra"), null).get("items")).hasSize(1);
    }

    @Test
    void xssEhGuardadoComoTextoEDevolvidoComoJson() throws Exception {
        User a = newUser();
        String attack = "<script>alert(document.cookie)</script><img src=x onerror=alert(1)>";
        MockHttpServletResponse res = send(a.session(), post("/api/transactions"), "{\"kind\":\"DESPESA\",\"amountCents\":1000,\"categoryId\":"
                + category(a.session(), "<b>Mercado</b>") + ",\"date\":\"" + today + "\",\"description\":" + json.writeValueAsString(attack) + "}");
        assertThat(res.getContentType()).startsWith("application/json");
        assertThat(json.readTree(res.getContentAsString()).get(0).get("description").asText()).isEqualTo(attack);
        // O navegador só executa scripts do próprio site, mesmo se algo escapar.
        String csp = send(null, get("/"), null).getHeader("Content-Security-Policy");
        assertThat(csp).contains("script-src 'self'").contains("object-src 'none'").doesNotContain("unsafe-eval");
    }

    // ------------------------------------------------------------------ CSRF, CORS e clickjacking

    @Test
    void alteracaoSemTokenCsrfEhRecusada() throws Exception {
        User a = newUser();
        var res = mvc.perform(post("/api/categories").session(a.session()).contentType(MediaType.APPLICATION_JSON)
                .content("{\"name\":\"Sem token\",\"kind\":\"DESPESA\"}")).andReturn().getResponse();
        assertThat(res.getStatus()).isEqualTo(403);
    }

    @Test
    void outroSiteNaoLeARespostaDaApi() throws Exception {
        User a = newUser();
        var simple = mvc.perform(get("/api/auth/me").session(a.session()).header("Origin", "https://site-malicioso.com"))
                .andReturn().getResponse();
        assertThat(simple.getHeader("Access-Control-Allow-Origin")).isNull();
        assertThat(simple.getHeader("Access-Control-Allow-Credentials")).isNull();
        var preflight = mvc.perform(options("/api/transactions").header("Origin", "https://site-malicioso.com")
                .header("Access-Control-Request-Method", "POST")).andReturn().getResponse();
        assertThat(preflight.getHeader("Access-Control-Allow-Origin")).isNull();
    }

    @Test
    void cabecalhosDeSegurancaContraClickjackingESniffing() throws Exception {
        var res = mvc.perform(get("/").secure(true)).andReturn().getResponse();
        assertThat(res.getHeader("X-Frame-Options")).isEqualTo("DENY");
        assertThat(res.getHeader("Content-Security-Policy")).contains("frame-ancestors 'none'");
        assertThat(res.getHeader("X-Content-Type-Options")).isEqualTo("nosniff");
        assertThat(res.getHeader("Strict-Transport-Security")).contains("max-age=31536000").contains("includeSubDomains");
        assertThat(res.getHeader("Permissions-Policy")).contains("camera=()");
        assertThat(res.getHeader("Cross-Origin-Opener-Policy")).isEqualTo("same-origin");
        assertThat(res.getHeader("Referrer-Policy")).isEqualTo("strict-origin-when-cross-origin");
    }

    // ------------------------------------------------------------------ acesso, autenticação e sessão

    @Test
    void semLoginNadaDaApiAbre() throws Exception {
        for (String url : List.of("/api/transactions?month=2026-01", "/api/categories", "/api/dashboard?month=2026-01",
                "/api/profile/export", "/api/transactions/tags")) {
            assertThat(mvc.perform(get(url)).andReturn().getResponse().getStatus()).as(url).isEqualTo(401);
        }
        assertThat(send(null, put("/api/profile/chart-colors"), "{}").getStatus()).isEqualTo(401);
    }

    @Test
    void outraContaNaoAlteraNemLeRecursosAlheios() throws Exception {
        User a = newUser(), b = newUser();
        long cat = category(a.session(), "Mercado");
        long tx = launch(a.session(), cat, "Compra").get("id").asLong();
        assertThat(send(b.session(), put("/api/categories/" + cat), "{\"name\":\"x\",\"color\":\"chart-7\"}").getStatus()).isEqualTo(404);
        assertThat(send(b.session(), delete("/api/transactions/" + tx), null).getStatus()).isEqualTo(404);
        assertThat(send(b.session(), post("/api/transactions/" + tx + "/mark-paid"), null).getStatus()).isEqualTo(404);
        var foreign = send(b.session(), get("/api/transactions/by-category/" + cat), null);
        assertThat(foreign.getStatus()).isIn(400, 404);
        assertThat(foreign.getContentAsString()).doesNotContain("Compra");
        assertThat(ok(a.session(), get("/api/transactions/by-category/" + cat), null)).hasSize(1);
    }

    @Test
    void forcaBrutaEhBloqueadaPorContaEPorRede() throws Exception {
        User victim = newUser();
        String net = randomIp();
        for (int i = 0; i < 5; i++) {
            assertThat(send(null, post("/api/auth/login").with(ip(randomIp())), "{\"email\":\"" + victim.email() + "\",\"password\":\"errada" + i + "\"}").getStatus()).isEqualTo(401);
        }
        // Mesmo com a senha certa, a conta fica bloqueada por 15 minutos.
        assertThat(send(null, post("/api/auth/login").with(ip(randomIp())), "{\"email\":\"" + victim.email() + "\",\"password\":\"senhaforte1\"}").getStatus()).isEqualTo(429);

        // Muitas contas diferentes tentadas da mesma rede (credential stuffing).
        for (int i = 0; i < 30; i++) {
            send(null, post("/api/auth/login").with(ip(net)), "{\"email\":\"ninguem" + i + "@teste.dev\",\"password\":\"x\"}");
        }
        User other = newUser();
        assertThat(send(null, post("/api/auth/login").with(ip(net)), "{\"email\":\"" + other.email() + "\",\"password\":\"senhaforte1\"}").getStatus()).isEqualTo(429);
    }

    @Test
    void loginTrocaOIdDaSessaoContraFixacao() throws Exception {
        User a = newUser();
        MockHttpSession s = new MockHttpSession();
        String before = s.getId();
        ok(s, post("/api/auth/login").with(ip(randomIp())), "{\"email\":\"" + a.email() + "\",\"password\":\"senhaforte1\"}");
        assertThat(s.getId()).isNotEqualTo(before);
    }

    @Test
    void trocarASenhaRenovaASessaoEExigeASenhaAtual() throws Exception {
        User a = newUser();
        String before = a.session().getId();
        assertThat(send(a.session(), put("/api/profile/password"), "{\"current\":\"errada\",\"next\":\"novasenha123\"}").getStatus()).isEqualTo(400);
        assertThat(send(a.session(), put("/api/profile/password"), "{\"current\":\"senhaforte1\",\"next\":\"novasenha123\"}").getStatus()).isEqualTo(204);
        assertThat(a.session().getId()).isNotEqualTo(before);
    }

    @Test
    void respostasNaoExpoemSenhaNemDetalhesInternos() throws Exception {
        User a = newUser();
        String me = send(a.session(), get("/api/auth/me"), null).getContentAsString();
        assertThat(me).doesNotContain("password\"", "$2a$", "{bcrypt}");
        String export = send(a.session(), get("/api/profile/export"), null).getContentAsString();
        assertThat(export).doesNotContain("$2a$", "{bcrypt}", "passwordHash");
        var broken = send(a.session(), post("/api/categories"), "{\"name\": ");
        assertThat(broken.getStatus()).isEqualTo(400);
        assertThat(broken.getContentAsString()).doesNotContain("Exception", "at br.com", "jackson");
        assertThat(mvc.perform(get("/actuator/env")).andReturn().getResponse().getStatus()).isNotEqualTo(200);
        assertThat(mvc.perform(get("/actuator/health")).andReturn().getResponse().getContentAsString()).doesNotContain("components", "db");
    }

    // ------------------------------------------------------------------ arquivos e corrida

    @Test
    void caminhoComPontoPontoNaoLeArquivosDoServidor() throws Exception {
        for (String path : List.of("/../application.yml", "/..%2fapplication.yml", "/%2e%2e/%2e%2e/etc/passwd",
                "/static/../application.yml", "/assets/..\\..\\application.yml", "/application.yml", "/db/migration/V1__schema.sql")) {
            String body;
            int status;
            try {
                var res = mvc.perform(get(path)).andReturn().getResponse();
                body = res.getContentAsString();
                status = res.getStatus();
            } catch (Exception rejected) { // o firewall do Spring Security recusa antes de chegar no app
                continue;
            }
            assertThat(body).as(path).doesNotContain("datasource", "CREATE TABLE", "root:");
            assertThat(status).as(path).isNotEqualTo(500);
        }
    }

    @Test
    void uploadDeArquivoEPedidoGiganteSaoRecusados() throws Exception {
        User a = newUser();
        var upload = mvc.perform(multipart("/api/imports").file("file", "<?php system($_GET['c']); ?>".getBytes())
                .session(a.session()).with(csrf())).andReturn().getResponse();
        assertThat(upload.getStatus()).isEqualTo(415);
        String huge = "{\"name\":\"" + "a".repeat(9 * 1024 * 1024) + "\",\"kind\":\"DESPESA\"}";
        assertThat(send(a.session(), post("/api/categories"), huge).getStatus()).isEqualTo(413);
    }

    @Test
    void linkDeUsoUnicoUsadoDuasVezesAoMesmoTempoSoFuncionaUmaVez() throws Exception {
        User a = newUser();
        Long id = profiles.findByEmailIgnoreCase(a.email()).orElseThrow().id;
        String token = links.issue(id, Purpose.MAGIC_LINK, Duration.ofMinutes(10), null);
        int tries = 8;
        var pool = Executors.newFixedThreadPool(tries);
        CountDownLatch start = new CountDownLatch(1);
        List<Future<Integer>> results = new ArrayList<>();
        for (int i = 0; i < tries; i++) {
            Callable<Integer> c = () -> {
                start.await();
                return send(new MockHttpSession(), post("/api/auth/magic-link/consume").with(ip(randomIp())), "{\"token\":\"" + token + "\"}").getStatus();
            };
            results.add(pool.submit(c));
        }
        start.countDown();
        int success = 0;
        for (Future<Integer> f : results) if (f.get() == 200) success++;
        pool.shutdown();
        assertThat(success).isEqualTo(1);
    }
}
