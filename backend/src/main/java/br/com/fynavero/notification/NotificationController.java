package br.com.fynavero.notification;

import br.com.fynavero.shared.Mailer;
import br.com.fynavero.ledger.LedgerCalendar;
import br.com.fynavero.notification.NotificationDtos.ActionDto;
import br.com.fynavero.notification.NotificationDtos.NotificationDto;
import br.com.fynavero.notification.NotificationDtos.PreferencesDto;
import br.com.fynavero.notification.NotificationDtos.PreferencesReq;
import br.com.fynavero.notification.NotificationDtos.TestSummaryDto;
import br.com.fynavero.security.AuthUser;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import java.time.YearMonth;
import java.util.List;

@RestController
public class NotificationController {
    private final NotificationService notifications;
    private final NotificationMailer mailer;
    private final ActionService actions;
    private final LedgerCalendar calendar;
    private final Mailer mail;

    public NotificationController(NotificationService notifications, NotificationMailer mailer, ActionService actions,
                                  LedgerCalendar calendar, Mailer mail) {
        this.notifications = notifications;
        this.mailer = mailer;
        this.actions = actions;
        this.calendar = calendar;
        this.mail = mail;
    }

    @GetMapping("/api/notifications")
    public List<NotificationDto> list(@AuthenticationPrincipal AuthUser u) {
        return notifications.current(u.id());
    }

    @GetMapping("/api/notifications/preferences")
    public PreferencesDto preferences(@AuthenticationPrincipal AuthUser u) {
        return notifications.preferences(u.id());
    }

    @PutMapping("/api/notifications/preferences")
    public PreferencesDto updatePreferences(@AuthenticationPrincipal AuthUser u, @RequestBody PreferencesReq body) {
        return notifications.updatePreferences(u.id(), body);
    }

    /** Envia agora, para o próprio e-mail, o resumo do mês passado (para ver como ele chega). */
    @PostMapping("/api/notifications/test-summary")
    public TestSummaryDto testSummary(@AuthenticationPrincipal AuthUser u) {
        YearMonth last = calendar.currentMonth().minusMonths(1);
        mailer.sendMonthlySummary(u.id(), last, true);
        return new TestSummaryDto(last.toString(), mail.configured());
    }

    // ------------------------------------------------------------------ link do e-mail (sem login)

    @GetMapping("/api/actions/{token}")
    public ActionDto describe(@PathVariable String token) {
        return actions.describe(token);
    }

    @PostMapping("/api/actions/{token}/pay")
    public ActionDto pay(@PathVariable String token) {
        return actions.pay(token);
    }
}
