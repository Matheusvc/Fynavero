package br.com.fynavero.ledger;

import br.com.fynavero.domain.Kind;
import br.com.fynavero.domain.Origin;
import br.com.fynavero.domain.PaymentMethod;
import br.com.fynavero.domain.Status;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

import java.time.LocalDate;
import java.util.List;

import static br.com.fynavero.common.ApiLimits.MAX_CENTS;

/** Contratos da API de lançamentos, recorrências e parcelamentos. Valores em centavos; datas AAAA-MM-DD. */
public final class LedgerDtos {
    private LedgerDtos() {}

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
     * Novo lançamento. Em compra parcelada, amountCents é o valor TOTAL da compra, ou o valor de CADA parcela
     * quando installmentValue = true. loan: parcelas de pagamento de um empréstimo recebido.
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

    public record TxUpdateReq(@Positive @Max(MAX_CENTS) long amountCents,
                              @NotNull Long categoryId,
                              @NotNull LocalDate date,
                              @Size(max = 120) String description,
                              PaymentMethod paymentMethod,
                              @Size(max = 500) String note,
                              Status status) {}

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

    public record IdsReq(@NotEmpty @Size(max = 500) List<Long> ids) {}

    public record DeletedDto(List<Long> ids) {}

    public record RecurrenceDto(Long id, Kind kind, Long categoryId, String description, long amountCents,
                                int day, PaymentMethod paymentMethod, LocalDate startDate, LocalDate endDate,
                                boolean fixedBill) {
        public static RecurrenceDto of(Recurrence r) {
            return new RecurrenceDto(r.id, r.kind, r.categoryId, r.description, r.amountCents, r.dayOfMonth,
                    r.paymentMethod, r.startDate, r.endDate, r.fixedBill);
        }
    }

    public record RecurrenceReq(@NotNull Kind kind, @NotNull Long categoryId,
                                @Size(max = 120) String description,
                                @Positive @Max(MAX_CENTS) long amountCents,
                                @Min(1) @Max(31) int day,
                                PaymentMethod paymentMethod) {}
}
