package br.com.fynavero.notification;

import br.com.fynavero.account.AccountToken;
import br.com.fynavero.account.AccountToken.Purpose;
import br.com.fynavero.account.OneTimeLinks;
import br.com.fynavero.category.CategoryService;
import br.com.fynavero.config.AppProperties;
import br.com.fynavero.domain.Status;
import br.com.fynavero.ledger.Transaction;
import br.com.fynavero.ledger.TransactionService;
import br.com.fynavero.notification.NotificationDtos.ActionDto;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.Instant;

/**
 * "Marcar como paga" direto do e-mail, sem precisar entrar. O link abre uma página que mostra a conta e pede
 * confirmação (leitores de e-mail que abrem links sozinhos não marcam nada); vale até 7 dias após o vencimento.
 */
@Service
public class ActionService {
    private static final String INVALID = "Este link não vale mais. Abra a Fynavero para marcar a conta como paga.";

    private final OneTimeLinks links;
    private final TransactionService transactions;
    private final CategoryService categories;
    private final AppProperties props;

    public ActionService(OneTimeLinks links, TransactionService transactions, CategoryService categories, AppProperties props) {
        this.links = links;
        this.transactions = transactions;
        this.categories = categories;
        this.props = props;
    }

    /** Cria o link de "Marcar como paga" para uma conta. */
    public String payLink(Transaction t) {
        Instant until = t.date.plusDays(8).atStartOfDay(props.zoneId()).toInstant();
        String token = links.issue(t.userId, Purpose.PAY_BILL, Duration.between(Instant.now(), until), t.id);
        return props.baseUrl() + "/conta-paga?token=" + token;
    }

    @Transactional(readOnly = true)
    public ActionDto describe(String token) {
        AccountToken a = links.peek(token, Purpose.PAY_BILL, INVALID);
        Transaction t = transactions.require(a.userId, a.refId);
        return toDto(t, t.status == Status.REALIZADO);
    }

    @Transactional
    public ActionDto pay(String token) {
        AccountToken a = links.peek(token, Purpose.PAY_BILL, INVALID);
        Transaction t = transactions.markPaid(a.userId, a.refId);
        a.usedAt = Instant.now();
        return toDto(t, true);
    }

    private ActionDto toDto(Transaction t, boolean paid) {
        return new ActionDto(NotificationService.billName(t, categories.map(t.userId)), t.amountCents, t.date, paid);
    }
}
