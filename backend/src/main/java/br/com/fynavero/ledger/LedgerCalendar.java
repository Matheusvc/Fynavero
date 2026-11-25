package br.com.fynavero.ledger;

import br.com.fynavero.ledger.Transaction.Status;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.LocalDate;
import java.time.YearMonth;

/** "Hoje" no fuso da aplicação e as regras de data compartilhadas pelos lançamentos. */
@Component
public class LedgerCalendar {
    /** Recorrências são geradas até 12 meses à frente (e sob demanda além disso). */
    static final int HORIZON_MONTHS = 12;
    static final int MAX_AHEAD_MONTHS = 60;

    private final Clock clock;

    public LedgerCalendar(Clock clock) {
        this.clock = clock;
    }

    public LocalDate today() {
        return LocalDate.now(clock);
    }

    public YearMonth currentMonth() {
        return YearMonth.now(clock);
    }

    /** Data futura é Previsto; hoje ou antes é Realizado. */
    public Status statusFor(LocalDate date) {
        return date.isAfter(today()) ? Status.PREVISTO : Status.REALIZADO;
    }

    LocalDate horizon() {
        return currentMonth().plusMonths(HORIZON_MONTHS).atEndOfMonth();
    }

    LocalDate maxAhead() {
        return currentMonth().plusMonths(MAX_AHEAD_MONTHS).atEndOfMonth();
    }

    /** O dia informado neste mês, ou no próximo se já passou; ajusta dias que o mês não tem (31 → 30). */
    public LocalDate nextOccurrence(int day) {
        LocalDate today = today();
        LocalDate d = dayIn(YearMonth.from(today), day);
        return d.isBefore(today) ? dayIn(YearMonth.from(today).plusMonths(1), day) : d;
    }

    static LocalDate dayIn(YearMonth m, int day) {
        return m.atDay(Math.min(day, m.lengthOfMonth()));
    }
}
