package br.com.fynavero.notification;

import br.com.fynavero.account.Profile;
import br.com.fynavero.account.ProfileRepository;
import br.com.fynavero.ledger.LedgerCalendar;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.YearMonth;
import java.util.function.Consumer;

/**
 * Tarefas agendadas dos e-mails (horário de Brasília):
 * 8h — contas que vencem amanhã e limites; 20h — limites de novo (gastos do dia); dia 1 às 9h — resumo do mês.
 * Cada pessoa é processada em sua própria transação: uma falha não impede as outras.
 */
@Component
public class NotificationJobs {
    private static final Logger log = LoggerFactory.getLogger(NotificationJobs.class);

    private final ProfileRepository profiles;
    private final NotificationMailer mailer;
    private final LedgerCalendar calendar;

    public NotificationJobs(ProfileRepository profiles, NotificationMailer mailer, LedgerCalendar calendar) {
        this.profiles = profiles;
        this.mailer = mailer;
        this.calendar = calendar;
    }

    @Scheduled(cron = "0 0 8 * * *", zone = "America/Sao_Paulo")
    public void morning() {
        forEachProfile("lembretes de contas", p -> {
            if (p.notifyBills) mailer.sendBillReminders(p.id);
        });
        evening();
    }

    @Scheduled(cron = "0 0 20 * * *", zone = "America/Sao_Paulo")
    public void evening() {
        forEachProfile("alertas de limite", p -> {
            if (p.notifyLimits) mailer.sendLimitAlerts(p.id);
        });
    }

    @Scheduled(cron = "0 0 9 1 * *", zone = "America/Sao_Paulo")
    public void monthlySummary() {
        YearMonth last = calendar.currentMonth().minusMonths(1);
        forEachProfile("resumo do mês", p -> {
            if (p.notifySummary) mailer.sendMonthlySummary(p.id, last, false);
        });
    }

    private void forEachProfile(String task, Consumer<Profile> action) {
        for (Profile p : profiles.findAll()) {
            if (!p.onboardingDone || p.emailVerifiedAt == null) continue; // e-mail só para endereço confirmado
            try {
                action.accept(p);
            } catch (RuntimeException e) {
                log.warn("Falha em {} para a conta {}: {}", task, p.id, e.getMessage());
            }
        }
    }
}
