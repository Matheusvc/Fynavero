package br.com.fynavero.account;

import br.com.fynavero.account.AccountDtos.ContributionDto;
import br.com.fynavero.account.AccountDtos.DeleteAccountReq;
import br.com.fynavero.category.CategoryDtos.CategoryDto;
import br.com.fynavero.category.CategoryRepository;
import br.com.fynavero.importing.ImportBatchRepository;
import br.com.fynavero.income.IncomeSourceRepository;
import br.com.fynavero.income.IncomeSourceService;
import br.com.fynavero.ledger.InstallmentPlanRepository;
import br.com.fynavero.ledger.LedgerDtos.RecurrenceDto;
import br.com.fynavero.ledger.LedgerDtos.TxDto;
import br.com.fynavero.ledger.RecurrenceRepository;
import br.com.fynavero.ledger.TransactionRepository;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;

import static br.com.fynavero.common.Errors.badRequest;

/** Direitos da LGPD: portabilidade (exportar tudo) e exclusão definitiva da conta. */
@Service
public class PrivacyService {
    private final AccountService accounts;
    private final ProfileRepository profiles;
    private final CategoryRepository categories;
    private final IncomeSourceService incomeSources;
    private final IncomeSourceRepository incomeRepo;
    private final RecurrenceRepository recurrences;
    private final InstallmentPlanRepository plans;
    private final TransactionRepository transactions;
    private final ImportBatchRepository batches;
    private final MagicLinkTokenRepository magicLinks;
    private final PasswordEncoder encoder;

    public PrivacyService(AccountService accounts, ProfileRepository profiles, CategoryRepository categories,
                          IncomeSourceService incomeSources, IncomeSourceRepository incomeRepo,
                          RecurrenceRepository recurrences, InstallmentPlanRepository plans,
                          TransactionRepository transactions, ImportBatchRepository batches,
                          MagicLinkTokenRepository magicLinks, PasswordEncoder encoder) {
        this.accounts = accounts;
        this.profiles = profiles;
        this.categories = categories;
        this.incomeSources = incomeSources;
        this.incomeRepo = incomeRepo;
        this.recurrences = recurrences;
        this.plans = plans;
        this.transactions = transactions;
        this.batches = batches;
        this.magicLinks = magicLinks;
        this.encoder = encoder;
    }

    public Map<String, Object> exportData(Long userId) {
        Profile p = accounts.require(userId);
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("exportadoEm", Instant.now().toString());
        out.put("perfil", Map.of(
                "nome", Objects.toString(p.name, ""),
                "email", p.email,
                "objetivos", p.objectives().stream().map(Enum::name).toList(),
                "contribuicao", new ContributionDto(p.contributionEnabled, p.contributionBps, p.contributionCategoryId),
                "termosAceitosEm", p.termsAcceptedAt.toString(),
                "criadoEm", p.createdAt.toString()));
        out.put("categorias", categories.findByUserIdOrderByKindAscSortOrderAsc(userId).stream().map(CategoryDto::of).toList());
        out.put("fontesDeRenda", incomeSources.list(userId));
        out.put("recorrencias", recurrences.findByUserIdOrderByDayOfMonth(userId).stream().map(RecurrenceDto::of).toList());
        out.put("parcelamentos", plans.findByUserIdOrderByFirstDateDesc(userId).stream()
                .map(x -> Map.of("id", x.id, "descricao", Objects.toString(x.description, ""), "totalCentavos", x.totalCents,
                        "parcelas", x.installmentCount, "primeiraData", x.firstDate.toString()))
                .toList());
        out.put("lancamentos", transactions.findAllActive(userId).stream().map(TxDto::of).toList());
        out.put("importacoes", batches.findByUserIdOrderByCreatedAtDesc(userId).stream()
                .map(b -> Map.of("id", b.id, "arquivo", b.fileName, "linhas", b.rowCount, "criadoEm", b.createdAt.toString()))
                .toList());
        return out;
    }

    /** Apaga todos os dados da pessoa, na ordem das chaves estrangeiras. */
    @Transactional
    public void deleteAccount(Long userId, DeleteAccountReq req) {
        Profile p = accounts.require(userId);
        String confirm = req.confirm() == null ? "" : req.confirm().strip().toUpperCase(Locale.ROOT);
        if (!"EXCLUIR".equals(confirm)) throw badRequest("Digite EXCLUIR para confirmar.");
        if (p.passwordHash != null && (req.password() == null || !encoder.matches(req.password(), p.passwordHash))) {
            throw badRequest("Senha incorreta.");
        }
        transactions.deleteByUserId(userId);
        incomeRepo.deleteByUserId(userId);
        recurrences.deleteByUserId(userId);
        plans.deleteByUserId(userId);
        batches.deleteByUserId(userId);
        magicLinks.deleteByUserId(userId);
        categories.deleteByUserId(userId);
        profiles.delete(p);
    }
}
