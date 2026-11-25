package br.com.fynavero;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@ConfigurationPropertiesScan
@EnableScheduling
// Cada repositório fica dentro do arquivo da sua entidade (ex.: Transaction.TransactionRepository).
@EnableJpaRepositories(considerNestedRepositories = true)
public class FynaveroApplication {
    public static void main(String[] args) {
        SpringApplication.run(FynaveroApplication.class, args);
    }
}
