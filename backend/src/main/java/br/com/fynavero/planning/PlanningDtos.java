package br.com.fynavero.planning;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;

import java.time.LocalDate;

import static br.com.fynavero.shared.Formats.MAX_CENTS;

/** Contratos de limites por categoria e objetivos de economia. Valores em centavos; percentuais em pontos-base. */
public final class PlanningDtos {
    private PlanningDtos() {}

    /** OK: abaixo de 80% do limite; ALERTA: de 80% a 99%; ESTOURADO: 100% ou mais. */
    public enum BudgetLevel { OK, ALERTA, ESTOURADO }

    public record BudgetDto(Long categoryId, String name, String icon, String color, long limitCents,
                            long spentCents, int pctBps, BudgetLevel level) {}

    public enum GoalStatus { CONCLUIDO, NO_RITMO, ATENCAO, PRAZO_VENCIDO, SEM_PRAZO }

    /**
     * Objetivo de economia. monthlyNeededCents: quanto guardar neste mês para chegar no prazo (nulo sem prazo
     * ou já concluído); savedThisMonthCents: quanto já foi guardado neste mês.
     */
    public record GoalDto(Long id, String name, String icon, String color, long targetCents, long savedCents,
                          long remainingCents, int pctBps, LocalDate deadline, Integer monthsLeft,
                          Long monthlyNeededCents, long savedThisMonthCents, GoalStatus status) {}

    /** Criar ou editar um objetivo. initialCents: quanto já está guardado (só na criação). */
    public record GoalReq(@NotBlank @Size(max = 60) String name,
                          @Positive @Max(MAX_CENTS) long targetCents,
                          LocalDate deadline,
                          @Size(max = 40) String icon,
                          @PositiveOrZero @Max(MAX_CENTS) Long initialCents) {}
}
