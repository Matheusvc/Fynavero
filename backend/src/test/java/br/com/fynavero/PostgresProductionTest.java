package br.com.fynavero;

import io.zonky.test.db.postgres.embedded.EmbeddedPostgres;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Configuração de produção num PostgreSQL de verdade (embutido no teste): todas as migrações rodam,
 * o servidor sobe por HTTP e a sessão de login fica salva no banco (Spring Session JDBC).
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT, properties = {
        "spring.autoconfigure.exclude=",
        "spring.session.jdbc.initialize-schema=never",
        "server.servlet.session.cookie.name=FYN_SESSION",
        "app.demo-data=true"
})
class PostgresProductionTest {
    private static final EmbeddedPostgres PG = start();

    private static EmbeddedPostgres start() {
        try {
            return EmbeddedPostgres.builder().start();
        } catch (IOException e) {
            throw new IllegalStateException("Não foi possível iniciar o PostgreSQL embutido", e);
        }
    }

    @DynamicPropertySource
    static void database(DynamicPropertyRegistry r) {
        // Mesmo formato que Render e Railway entregam: postgres://usuario:senha@host:porta/banco
        r.add("spring.datasource.url", () -> "jdbc:postgresql://localhost:" + PG.getPort() + "/postgres");
        r.add("spring.datasource.username", () -> "postgres");
        r.add("spring.datasource.password", () -> "postgres");
    }

    @AfterAll
    static void stop() throws IOException {
        PG.close();
    }

    @LocalServerPort int port;
    @Autowired JdbcTemplate jdbc;

    private final HttpClient http = HttpClient.newHttpClient();

    private HttpResponse<String> send(String method, String path, String body, String cookies, String xsrf) throws Exception {
        HttpRequest.Builder b = HttpRequest.newBuilder(URI.create("http://localhost:" + port + path))
                .header("Content-Type", "application/json")
                .method(method, body == null ? HttpRequest.BodyPublishers.noBody() : HttpRequest.BodyPublishers.ofString(body));
        if (cookies != null) b.header("Cookie", cookies);
        if (xsrf != null) b.header("X-XSRF-TOKEN", xsrf);
        return http.send(b.build(), HttpResponse.BodyHandlers.ofString());
    }

    private static Optional<String> cookie(HttpResponse<?> res, String name) {
        List<String> set = res.headers().allValues("Set-Cookie");
        return set.stream().filter(c -> c.startsWith(name + "=")).map(c -> c.substring(name.length() + 1, c.indexOf(';')))
                .filter(v -> !v.isEmpty()).findFirst();
    }

    @Test
    void migracoesSessoesNoBancoELoginDeExemplo() throws Exception {
        assertThat(jdbc.queryForObject("select count(*) from flyway_schema_history where success", Integer.class)).isGreaterThanOrEqualTo(5);
        assertThat(send("GET", "/actuator/health", null, null, null).statusCode()).isEqualTo(200);

        String xsrf = cookie(send("GET", "/api/auth/csrf", null, null, null), "XSRF-TOKEN").orElseThrow();
        String email = "pg" + UUID.randomUUID().toString().substring(0, 8) + "@teste.dev";
        HttpResponse<String> reg = send("POST", "/api/auth/register",
                "{\"name\":\"Pessoa PG\",\"email\":\"" + email + "\",\"password\":\"senhaforte1\",\"acceptTerms\":true}",
                "XSRF-TOKEN=" + xsrf, xsrf);
        assertThat(reg.statusCode()).isEqualTo(200);
        String session = cookie(reg, "FYN_SESSION").orElseThrow();
        String newXsrf = cookie(reg, "XSRF-TOKEN").orElse(xsrf);

        // A sessão está gravada no PostgreSQL, ligada ao e-mail da pessoa.
        assertThat(jdbc.queryForObject("select count(*) from spring_session where principal_name = ?", Integer.class, email)).isEqualTo(1);
        HttpResponse<String> me = send("GET", "/api/auth/me", null, "FYN_SESSION=" + session + "; XSRF-TOKEN=" + newXsrf, null);
        assertThat(me.statusCode()).isEqualTo(200);
        assertThat(me.body()).contains(email).contains("\"emailVerified\":false");

        // As contas de exemplo, com 6 meses de lançamentos, também sobem no PostgreSQL.
        assertThat(jdbc.queryForObject("select count(*) from transactions", Integer.class)).isGreaterThan(50);
        HttpResponse<String> demo = send("POST", "/api/auth/login", "{\"email\":\"ana@fynavero.dev\",\"password\":\"Fynavero@2026\"}",
                "XSRF-TOKEN=" + xsrf, xsrf);
        assertThat(demo.statusCode()).isEqualTo(200);
        String demoSession = cookie(demo, "FYN_SESSION").orElseThrow();
        assertThat(send("GET", "/api/dashboard", null, "FYN_SESSION=" + demoSession, null).statusCode()).isEqualTo(200);
        assertThat(send("GET", "/api/goals", null, "FYN_SESSION=" + demoSession, null).body()).contains("Viagem");
    }
}
