package br.com.fynavero.notification;

import br.com.fynavero.category.Category;
import br.com.fynavero.category.CategoryService;
import br.com.fynavero.common.SecureTokens;
import br.com.fynavero.config.AppProperties;
import br.com.fynavero.domain.Status;
import br.com.fynavero.ledger.Transaction;
import br.com.fynavero.ledger.TransactionService;
import br.com.fynavero.notification.NotificationDtos.ActionDto;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.Map;

import static br.com.fynavero.common.Errors.badRequest;

/**
 * "Marcar como paga" direto do e-mail, sem precisar entrar. O link abre uma página que mostra a conta e pede
 * confirmação (assim leitores de e-mail que abrem links sozinhos não marcam nada); o token só serve para
 * aquela conta e expira 7 dias depois do vencimento.
 */
@Service
public class ActionService {
    private static final String INVALID = "Este link não vale mais. Abra a Fynavero para marcar a conta como paga.";

    private final ActionTokenRepository tokens;
    private final TransactionService transactions;
    private final CategoryService categories;
    private final AppProperties props;

    public ActionService(ActionTokenRepository tokens, TransactionService transactions, CategoryService categories,
                         AppProperties props) {
        this.tokens = tokens;
        this.transactions = transactions;
        this.categories = categories;
        this.props = props;
    }

    /** Cria o link de "Marcar como paga" para uma conta. */
    @Transactional
    public String payLink(Transaction t) {
        String token = SecureTokens.newToken();
        ActionToken a = new ActionToken();
        a.userId = t.userId;
        a.transactionId = t.id;
        a.tokenHash = SecureTokens.sha256(token);
        a.expiresAt = t.date.plusDays(8).atStartOfDay(props.zoneId()).toInstant();
        tokens.save(a);
        return props.baseUrl() + "/conta-paga?token=" + token;
    }

    @Transactional(readOnly = true)
    public ActionDto describe(String token) {
        ActionToken a = valid(token);
        Transaction t = transactions.require(a.userId, a.transactionId);
        Map<Long, Category> cats = categories.map(a.userId);
        return new ActionDto(NotificationService.billName(t, cats), t.amountCents, t.date, t.status == Status.REALIZADO);
    }

    @Transactional
    public ActionDto pay(String token) {
        ActionToken a = valid(token);
        Transaction t = transactions.markPaid(a.userId, a.transactionId);
        a.usedAt = Instant.now();
        return new ActionDto(NotificationService.billName(t, categories.map(a.userId)), t.amountCents, t.date, true);
    }

    private ActionToken valid(String token) {
        if (token == null || token.length() > 100) throw badRequest(INVALID);
        ActionToken a = tokens.findByTokenHash(SecureTokens.sha256(token)).orElseThrow(() -> badRequest(INVALID));
        if (a.expiresAt.isBefore(Instant.now())) throw badRequest(INVALID);
        return a;
    }
}
