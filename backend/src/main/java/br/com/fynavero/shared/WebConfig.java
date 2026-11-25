package br.com.fynavero.shared;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.Resource;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;
import org.springframework.web.servlet.resource.PathResourceResolver;

import java.io.IOException;
import java.time.Clock;

/** Configuração da aplicação web: relógio no fuso do app e o front-end embutido no .jar. */
@Configuration
public class WebConfig implements WebMvcConfigurer {

    /** Relógio no fuso configurado: define o que é "hoje" para Previsto/Realizado e vencimentos. */
    @Bean
    Clock clock(AppProperties props) {
        return Clock.system(props.zoneId());
    }

    /** Serve o front-end e devolve index.html para as rotas do app (ex.: /lancamentos). */
    @Override
    public void addResourceHandlers(ResourceHandlerRegistry registry) {
        registry.addResourceHandler("/**")
                .addResourceLocations("classpath:/static/")
                .resourceChain(true)
                .addResolver(new PathResourceResolver() {
                    @Override
                    protected Resource getResource(String path, Resource location) throws IOException {
                        // Path traversal / inclusão de arquivo: "../", "\", "%" ou ":" nunca saem da pasta static.
                        if (path.contains("..") || path.contains("\\") || path.contains("%") || path.contains(":")) return null;
                        Resource r = super.getResource(path, location); // confere que o arquivo fica dentro de static/
                        if (r != null) return r;
                        if (path.startsWith("api/") || path.startsWith("actuator/") || path.contains(".")) return null;
                        Resource index = location.createRelative("index.html");
                        return index.exists() ? index : null;
                    }
                });
    }
}
