package br.com.fynavero.shared;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.ZoneId;

/**
 * Configurações da aplicação (prefixo {@code app} no application.yml).
 *
 * @param baseUrl  endereço público do front-end, usado em links mágicos e no retorno do login com Google
 * @param zone     fuso que define o que é "hoje" (padrão America/Sao_Paulo)
 * @param demoData cria as contas de exemplo na inicialização
 * @param mailFrom remetente dos e-mails enviados
 */
@ConfigurationProperties("app")
public record AppProperties(String baseUrl, String zone, boolean demoData, String mailFrom) {
    public ZoneId zoneId() {
        return ZoneId.of(zone == null ? "America/Sao_Paulo" : zone);
    }
}
