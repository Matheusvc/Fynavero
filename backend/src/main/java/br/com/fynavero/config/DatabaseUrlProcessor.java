package br.com.fynavero.config;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.env.EnvironmentPostProcessor;
import org.springframework.core.env.ConfigurableEnvironment;
import org.springframework.core.env.MapPropertySource;

import java.net.URI;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;

/**
 * Render, Railway, Heroku e afins entregam o banco como {@code postgres://usuario:senha@host:5432/banco}.
 * O driver JDBC não entende esse formato, então convertemos para {@code jdbc:postgresql://host:5432/banco}
 * e separamos usuário e senha (DATABASE_USER e DATABASE_PASSWORD, se informados, continuam valendo).
 */
public class DatabaseUrlProcessor implements EnvironmentPostProcessor {

    @Override
    public void postProcessEnvironment(ConfigurableEnvironment env, SpringApplication app) {
        Map<String, Object> props = convert(env.getProperty("DATABASE_URL"), env.getProperty("DATABASE_USER"),
                env.getProperty("DATABASE_PASSWORD"));
        if (!props.isEmpty()) env.getPropertySources().addFirst(new MapPropertySource("fynaveroDatabaseUrl", props));
    }

    static Map<String, Object> convert(String url, String user, String password) {
        Map<String, Object> out = new HashMap<>();
        if (url == null || !(url.startsWith("postgres://") || url.startsWith("postgresql://"))) return out;
        URI u = URI.create(url);
        String db = u.getPath() == null || u.getPath().isBlank() ? "/postgres" : u.getPath();
        String query = u.getRawQuery() == null ? "" : "?" + u.getRawQuery();
        out.put("spring.datasource.url", "jdbc:postgresql://" + u.getHost() + (u.getPort() > 0 ? ":" + u.getPort() : "") + db + query);
        String info = u.getRawUserInfo();
        if (info != null) {
            String[] parts = info.split(":", 2);
            if (user == null || user.isBlank()) out.put("spring.datasource.username", decode(parts[0]));
            if ((password == null || password.isBlank()) && parts.length > 1) out.put("spring.datasource.password", decode(parts[1]));
        }
        return out;
    }

    private static String decode(String s) {
        return URLDecoder.decode(s, StandardCharsets.UTF_8);
    }
}
