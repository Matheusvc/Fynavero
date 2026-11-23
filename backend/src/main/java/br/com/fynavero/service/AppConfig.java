package br.com.fynavero.service;

import br.com.fynavero.support.Support.AppProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Clock;

@Configuration
public class AppConfig {
    /** Relógio no fuso America/Sao_Paulo: define o que é "hoje" para Previsto/Realizado e vencimentos. */
    @Bean
    Clock clock(AppProperties props) {
        return Clock.system(props.zoneId());
    }
}
