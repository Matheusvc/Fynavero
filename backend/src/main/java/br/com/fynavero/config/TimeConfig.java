package br.com.fynavero.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Clock;

@Configuration
public class TimeConfig {
    /** Relógio no fuso configurado: define o que é "hoje" para Previsto/Realizado e vencimentos. */
    @Bean
    Clock clock(AppProperties props) {
        return Clock.system(props.zoneId());
    }
}
