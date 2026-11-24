package br.com.fynavero.api;

import br.com.fynavero.domain.Entities.Category;
import br.com.fynavero.domain.Entities.Transaction;
import br.com.fynavero.domain.Enums.*;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

/** Contratos da API. Valores sempre em centavos; datas no formato AAAA-MM-DD. */
public final class Dto {
    private Dto() {}

    private static final long MAX_CENTS = 100_000_000_000L; // R$ 1 bilhão

    // ---------- Autenticação e perfil ----------
    public record RegisterReq(@NotBlank @Size(max = 100) String name,
                              @NotBlank @Email @Size(max = 254) String email,
                              @NotBlank @Size(min = 8, max = 72) String password,
                              @AssertTrue(message = "Aceite os termos para continuar.") boolean acceptTerms) {}

    public record LoginReq(@NotBlank String email, @NotBlank String password) {}

    public record EmailReq(@NotBlank @Email String email) {}

    public record TokenReq(@NotBlank @Size(max = 200) String token) {}

    public record Providers(boolean google, boolean magicLink) {}

    public record ContributionDto(boolean enabled, @Min(1) @Max(10000) int bps, Long categoryId) {}

    public record Me(Long id, String name, String email, List<Objective> objectives, boolean onboardingDone,
                     ContributionDto contribution, boolean hasPassword) {}

    public record ProfileReq(@NotBlank @Size(max = 100) String name, @Size(max = 4) List<Objective> objectives) {}

    public record PasswordReq(String current, @NotBlank @Size(min = 8, max = 72) String next) {}

    public record DeleteAccountReq(@NotBlank String confirm, String password) {}

    // ---------- Onboarding ----------
    public record CatalogDto(Long id, String name, String icon, String group) {}

    public record IncomeInput(@NotBlank @Size(max = 60) String name,
                              @PositiveOrZero @Max(MAX_CENTS) long amountCents,
                              boolean fixed,
                              @Min(1) @Max(31) Integer day) {}

    public record CustomCategoryInput(@NotBlank @Size(max = 60) String name,
                                      @NotBlank @Size(max = 40) String icon,
                                      @NotBlank @Size(max = 30) String group) {}

    /** categoryKey: "catalog:{id}" ou "custom:{nome}" — referência às categorias escolhidas na tela 3. */
    public record FixedBillInput(@NotBlank @Size(max = 60) String name,
                                 @Positive @Max(MAX_CENTS) long amountCents,
                                 @Min(1) @Max(31) int day,
                                 @NotBlank String categoryKey) {}

    public record OnboardingReq(@Size(max = 100) String name,
                                @Size(max = 4) List<Objective> objectives,
                                @Valid @Size(max = 20) List<IncomeInput> incomes,
                                @Size(max = 60) List<Long> catalogIds,
                                @Valid @Size(max = 30) List<CustomCategoryInput> customCategories,
                                @Valid @Size(max = 30) List<FixedBillInput> fixedBills,
                                @Valid ContributionDto contribution) {}

    // ---------- Categorias ----------
    public record CategoryDto(Long id, String name, Kind kind, String group, String icon, String color,
                              int sortOrder, boolean hidden, boolean loan, Long goalCents) {
        public static CategoryDto of(Category c) {
            return new CategoryDto(c.id, c.name, c.kind, c.group, c.icon, c.color, c.sortOrder, c.hidden, c.loan, c.goalCents);
        }
    }

    public record CategoryReq(@NotBlank @Size(max = 60) String name,
                              Kind kind,
                              @Size(max = 30) String group,
                              @Size(max = 40) String icon,
                              Boolean hidden,
                              @PositiveOrZero @Max(MAX_CENTS) Long goalCents) {}

    public record ReorderReq(@NotNull Kind kind, @NotEmpty List<Long> ids) {}

    // ---------- Fontes de renda e contas fixas ----------
    public record IncomeSourceDto(Long id, String name, long amountCents, boolean fixed, Long categoryId, Integer day) {}

    public record IncomeSourceReq(@NotBlank @Size(max = 60) String name,
                                  @PositiveOrZero @Max(MAX_CENTS) long amountCents,
                                  boolean fixed,
                                  @Min(1) @Max(31) Integer day) {}

    public record RecurrenceDto(Long id, Kind kind, Long categoryId, String description, long amountCents,
                                int day, PaymentMethod paymentMethod, LocalDate startDate, LocalDate endDate,
                                boolean fixedBill) {}

    public record RecurrenceReq(@NotNull Kind kind, @NotNull Long categoryId,
                                @Size(max = 120) String description,
                                @Positive @Max(MAX_CENTS) long amountCents,
                                @Min(1) @Max(31) int day,
                                PaymentMethod paymentMethod) {}

    // ---------- Lançamentos ----------
    public record TxDto(Long id, Kind kind, LocalDate date, String description, Long categoryId, long amountCents,
                        PaymentMethod paymentMethod, Status status, Integer installmentNumber,
                        Integer installmentTotal, Long recurrenceId, Long installmentPlanId, Origin origin,
                        Long importBatchId, String note) {
        public static TxDto of(Transaction t) {
            return new TxDto(t.id, t.kind, t.date, t.description, t.categoryId, t.amountCents, t.paymentMethod,
                    t.status, t.installmentNumber, t.installmentTotal, t.recurrenceId, t.installmentPlanId,
                    t.origin, t.importBatchId, t.note);
        }
    }

    /**
     * Em compra parcelada, amountCents é o valor TOTAL da compra, ou o valor de CADA parcela quando
     * installmentValue = true. loan: parcelas de pagamento de um empréstimo recebido (só em receitas de empréstimo).
     */
    public record TxReq(@NotNull Kind kind,
                        @Positive @Max(MAX_CENTS) long amountCents,
                        @NotNull Long categoryId,
                        LocalDate date,
                        @Size(max = 120) String description,
                        PaymentMethod paymentMethod,
                        @Size(max = 500) String note,
                        boolean repeatMonthly,
                        @Min(2) @Max(120) Integer installments,
                        Boolean installmentValue,
                        @Valid LoanRepayment loan) {}

    /** Como um empréstimo recebido será pago: parcelas futuras de despesa. */
    public record LoanRepayment(@Min(1) @Max(240) int count,
                                @Positive @Max(MAX_CENTS) long installmentCents,
                                LocalDate firstDate,
                                Long categoryId) {}

    /** Transforma um lançamento simples em compra parcelada. */
    public record InstallmentConvertReq(@Positive @Max(MAX_CENTS) long amountCents,
                                        @NotNull Long categoryId,
                                        @NotNull LocalDate date,
                                        @Size(max = 120) String description,
                                        PaymentMethod paymentMethod,
                                        @Size(max = 500) String note,
                                        @Min(2) @Max(120) int count,
                                        boolean installmentValue) {}

    /** Muda o número total de parcelas de um parcelamento. */
    public record PlanResizeReq(@Min(1) @Max(120) int count) {}

    public record TxUpdateReq(@Positive @Max(MAX_CENTS) long amountCents,
                              @NotNull Long categoryId,
                              @NotNull LocalDate date,
                              @Size(max = 120) String description,
                              PaymentMethod paymentMethod,
                              @Size(max = 500) String note,
                              Status status) {}

    public record IdsReq(@NotEmpty @Size(max = 500) List<Long> ids) {}

    public record DeletedDto(List<Long> ids) {}

    // ---------- Início ----------
    /** receitas inclui emprestimos (entra no saldo); emprestimos mostra quanto disso é empréstimo recebido. */
    public record TotalsDto(long receitas, long emprestimos, long despesas, long despesasLancadas, long contribuicao,
                            long sobra, long investido, long saldo) {}

    public record SliceDto(Long categoryId, String name, String icon, String color, long amountCents, int bps) {}

    public record MonthBarDto(String month, String label, long receitas, long despesas, long investido) {}

    public record UpcomingDto(Long id, LocalDate date, String description, Long categoryId, long amountCents,
                              Integer installmentNumber, Integer installmentTotal) {}

    public record DashboardDto(String month, boolean empty, TotalsDto current, TotalsDto previous,
                               List<SliceDto> breakdown, List<MonthBarDto> lastMonths,
                               List<UpcomingDto> upcoming, List<String> insights) {}

    // ---------- Parcelas ----------
    /** current: número da parcela do mês selecionado (ex.: 5 em "5/12"). */
    public record PlanDto(Long id, String description, Long categoryId, long installmentCents, long totalCents,
                          int count, int paid, int current, long remainingCents, String lastMonth,
                          LocalDate firstDate, boolean finished) {}

    public record CommitmentDto(String month, String label, long installments, long fixedBills) {}

    public record InstallmentsDto(List<PlanDto> plans, List<CommitmentDto> timeline, long monthTotal) {}

    // ---------- Investimentos ----------
    public record InvestmentCategoryDto(Long categoryId, String name, String icon, String color, long monthCents,
                                        long accumulatedCents, Long goalCents) {}

    public record InvestmentsDto(String month, long monthTotal, long accumulatedTotal,
                                 List<InvestmentCategoryDto> categories, List<MonthBarDto> history) {}

    // ---------- Relatórios ----------
    public record ReportRow(Long categoryId, String label, long[] months, long total, long average) {}

    public record ReportGroup(String group, List<ReportRow> rows, ReportRow subtotal) {}

    public record AnnualReport(int year, List<String> monthLabels, List<ReportRow> receitas,
                               List<ReportRow> emprestimos,
                               List<ReportGroup> despesas, ReportRow totalDespesas,
                               List<ReportRow> investimentos, ReportRow totalInvestido,
                               List<ReportRow> resultado) {}

    public record CompareRow(Long categoryId, String label, long current, long previous, long average) {}

    public record CompareReport(String month, String previousMonth, List<CompareRow> totals, List<CompareRow> categories) {}

    // ---------- Importação ----------
    public record ImportRow(@NotNull Kind kind, @NotNull LocalDate date, @Size(max = 120) String description,
                            @NotNull Long categoryId, @Positive @Max(MAX_CENTS) long amountCents,
                            PaymentMethod paymentMethod, @Min(1) @Max(240) Integer installmentNumber,
                            @Min(1) @Max(240) Integer installmentTotal, @Size(max = 500) String note) {}

    public record ImportReq(@NotBlank @Size(max = 200) String fileName,
                            @Valid @NotEmpty @Size(max = 10_000) List<ImportRow> rows) {}

    public record DuplicateCandidate(@NotNull LocalDate date, long amountCents, String description) {}

    public record DuplicateCheckReq(@NotNull @Size(max = 10_000) List<DuplicateCandidate> rows) {}

    public record DuplicateCheckDto(List<Integer> duplicateIndexes) {}

    public record ImportBatchDto(Long id, String fileName, int rowCount, Instant createdAt, Instant undoneAt,
                                 long receitas, long despesas, long investido) {}
}
