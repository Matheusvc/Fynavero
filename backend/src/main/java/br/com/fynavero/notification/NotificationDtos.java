package br.com.fynavero.notification;

import java.time.LocalDate;

/** Contratos da central de avisos, das preferências de e-mail e da página "Marcar como paga". */
public final class NotificationDtos {
    private NotificationDtos() {}

    public enum NotificationType { CONTA_VENCIDA, CONTA_HOJE, CONTA_AMANHA, LIMITE_ALERTA, LIMITE_ESTOURADO }

    /** Aviso exibido no sino. transactionId vem nas contas (para "Marcar como paga"); categoryId nos limites. */
    public record NotificationDto(String id, NotificationType type, String title, String body, LocalDate date,
                                  Long transactionId, Long categoryId, Long amountCents) {}

    public record PreferencesDto(boolean bills, boolean limits, boolean summary, boolean emailConfigured) {}

    public record PreferencesReq(boolean bills, boolean limits, boolean summary) {}

    public record TestSummaryDto(String month, boolean emailConfigured) {}

    /** O que a página aberta pelo link do e-mail mostra antes de confirmar. */
    /** O token do link vai no corpo (não na URL), para não ficar em logs nem no histórico. */
    public record ActionReq(@jakarta.validation.constraints.NotBlank @jakarta.validation.constraints.Size(max = 100) String token) {}

    public record ActionDto(String description, long amountCents, LocalDate date, boolean paid) {}
}
