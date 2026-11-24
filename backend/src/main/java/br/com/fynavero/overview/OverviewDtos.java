package br.com.fynavero.overview;

import br.com.fynavero.calc.FinanceCalculator.MonthTotals;

import java.time.LocalDate;
import java.util.List;

/** Contratos das telas de leitura: Início, Parcelas e Dívidas e Investimentos. Valores em centavos. */
public final class OverviewDtos {
    private OverviewDtos() {}

    /** receitas inclui emprestimos (entra no saldo); emprestimos mostra quanto disso é empréstimo recebido. */
    public record TotalsDto(long receitas, long emprestimos, long despesas, long despesasLancadas, long contribuicao,
                            long sobra, long investido, long saldo) {
        public static TotalsDto of(MonthTotals t) {
            return new TotalsDto(t.receitas(), t.emprestimos(), t.despesas(), t.despesasLancadas(), t.contribuicao(),
                    t.sobra(), t.investido(), t.saldo());
        }
    }

    /** Fatia da rosca; bps = participação em pontos-base (10000 = 100%). categoryId nulo = "Outros". */
    public record SliceDto(Long categoryId, String name, String icon, String color, long amountCents, int bps) {}

    public record MonthBarDto(String month, String label, long receitas, long despesas, long investido) {}

    public record UpcomingDto(Long id, LocalDate date, String description, Long categoryId, long amountCents,
                              Integer installmentNumber, Integer installmentTotal) {}

    /** empty: ainda não há nenhum lançamento até hoje (mostra o estado vazio com "Lançar primeiro gasto"). */
    public record DashboardDto(String month, boolean empty, TotalsDto current, TotalsDto previous,
                               List<SliceDto> breakdown, List<MonthBarDto> lastMonths,
                               List<UpcomingDto> upcoming, List<String> insights) {}

    /** current: número da parcela do mês selecionado (ex.: 5 em "5/12"). */
    public record PlanDto(Long id, String description, Long categoryId, long installmentCents, long totalCents,
                          int count, int paid, int current, long remainingCents, String lastMonth,
                          LocalDate firstDate, boolean finished) {}

    public record CommitmentDto(String month, String label, long installments, long fixedBills) {}

    public record InstallmentsDto(List<PlanDto> plans, List<CommitmentDto> timeline, long monthTotal) {}

    public record InvestmentCategoryDto(Long categoryId, String name, String icon, String color, long monthCents,
                                        long accumulatedCents, Long goalCents) {}

    public record InvestmentsDto(String month, long monthTotal, long accumulatedTotal,
                                 List<InvestmentCategoryDto> categories, List<MonthBarDto> history) {}
}
