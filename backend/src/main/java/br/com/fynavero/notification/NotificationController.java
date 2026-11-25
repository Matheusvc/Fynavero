package br.com.fynavero.notification;

import br.com.fynavero.shared.Mailer;
import br.com.fynavero.ledger.LedgerCalendar;
import br.com.fynavero.notification.NotificationDtos.ActionDto;
import br.com.fynavero.notification.NotificationDtos.ActionReq;
import br.com.fynavero.notification.NotificationDtos.NotificationDto;
import br.com.fynavero.notification.NotificationDtos.PreferencesDto;
import br.com.fynavero.notification.NotificationDtos.PreferencesReq;
import br.com.fynavero.notification.NotificationDtos.TestSummaryDto;
import br.com.fynavero.security.AuthUser;
import br.com.fynavero.security.RateLimiter;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import java.time.Duration;
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
    private final NotificationService actions;
    private final LedgerCalendar calendar;
    private final Mailer mail;
    private final RateLimiter limiter;

    public NotificationController(NotificationService notifications, NotificationMailer mailer, NotificationService actions,
                                  LedgerCalendar calendar, Mailer mail, RateLimiter limiter) {
        this.limiter = limiter;
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

    @PostMapping("/api/actions/describe")
    public ActionDto describe(@Valid @RequestBody ActionReq body, HttpServletRequest req) {
        limitActions(req);
        return actions.describe(body.token());
    }

    @PostMapping("/api/actions/pay")
    public ActionDto pay(@Valid @RequestBody ActionReq body, HttpServletRequest req) {
        limitActions(req);
        return actions.pay(body.token());
    }

    private void limitActions(HttpServletRequest req) {
        limiter.check("action:" + req.getRemoteAddr(), 60, Duration.ofHours(1), "Muitas tentativas. Tente de novo mais tarde.");
    }
}
