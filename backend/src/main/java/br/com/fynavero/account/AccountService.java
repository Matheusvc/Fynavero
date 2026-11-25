package br.com.fynavero.account;

import br.com.fynavero.account.AccountDtos.ChartColors;
import br.com.fynavero.account.AccountDtos.ContributionDto;
import br.com.fynavero.account.AccountDtos.DeleteAccountReq;
import br.com.fynavero.account.AccountDtos.PasswordReq;
import br.com.fynavero.account.AccountDtos.ProfileReq;
import br.com.fynavero.account.AccountDtos.RegisterReq;
import br.com.fynavero.account.AccountToken.AccountTokenRepository;
import br.com.fynavero.account.Profile.ProfileRepository;
import br.com.fynavero.category.Category;
import br.com.fynavero.category.Category.CategoryRepository;
import br.com.fynavero.category.CategoryDtos.CategoryDto;
import br.com.fynavero.category.CategoryService;
import br.com.fynavero.importing.ImportBatch.ImportBatchRepository;
import br.com.fynavero.ledger.FinanceCalculator.Contribution;
import br.com.fynavero.ledger.IncomeSource.IncomeSourceRepository;
import br.com.fynavero.ledger.RecurrenceService;
import br.com.fynavero.ledger.InstallmentPlan.InstallmentPlanRepository;
import br.com.fynavero.ledger.LedgerDtos.RecurrenceDto;
import br.com.fynavero.ledger.LedgerDtos.TxDto;
import br.com.fynavero.ledger.Recurrence.RecurrenceRepository;
import br.com.fynavero.ledger.Transaction.Kind;
import br.com.fynavero.ledger.Transaction.TransactionRepository;
import br.com.fynavero.shared.Formats;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import static br.com.fynavero.shared.Errors.badRequest;
import static br.com.fynavero.shared.Errors.notFound;

/** Cadastro, perfil, senha e configuração da contribuição automática. */
@Service
public class AccountService {
    private final ProfileRepository profiles;
    private final CategoryService categories;
    private final PasswordEncoder encoder;
    private final CategoryRepository categoryRepo;
    private final RecurrenceService incomeSources;
    private final IncomeSourceRepository incomeRepo;
    private final RecurrenceRepository recurrences;
    private final InstallmentPlanRepository plans;
    private final TransactionRepository transactions;
    private final ImportBatchRepository batches;
    private final AccountTokenRepository links;

    public AccountService(ProfileRepository profiles, CategoryService categories, PasswordEncoder encoder, CategoryRepository categoryRepo, RecurrenceService incomeSources, IncomeSourceRepository incomeRepo, RecurrenceRepository recurrences, InstallmentPlanRepository plans, TransactionRepository transactions, ImportBatchRepository batches, AccountTokenRepository links) {
        this.profiles = profiles;
        this.categories = categories;
        this.encoder = encoder;
        this.categoryRepo = categoryRepo;
        this.incomeSources = incomeSources;
        this.incomeRepo = incomeRepo;
        this.recurrences = recurrences;
        this.plans = plans;
        this.transactions = transactions;
        this.batches = batches;
        this.links = links;
    }

    public Profile require(Long userId) {
        return profiles.findById(userId).orElseThrow(() -> notFound());
    }

    /** Configuração da contribuição usada pelo módulo de cálculo. */
    public Contribution contributionOf(Long userId) {
        Profile p = require(userId);
        return new Contribution(p.contributionEnabled, p.contributionBps, p.contributionCategoryId);
    }

    @Transactional
    public Profile register(RegisterReq req) {
        String email = req.email().strip().toLowerCase(Locale.ROOT);
        if (profiles.findByEmailIgnoreCase(email).isPresent()) {
            throw badRequest("Já existe uma conta com esse e-mail. Tente entrar.");
        }
        Profile p = new Profile();
        p.email = email;
        p.name = req.name().strip();
        p.passwordHash = encoder.encode(req.password());
        p.termsAcceptedAt = Instant.now();
        p.createdAt = Instant.now();
        return profiles.save(p);
    }

    @Transactional
    public Profile updateProfile(Long userId, ProfileReq req) {
        Profile p = require(userId);
        p.name = req.name().strip();
        p.setObjectives(req.objectives());
        return p;
    }

    @Transactional
    public Profile updateChartColors(Long userId, ChartColors req) {
        Profile p = require(userId);
        p.colorIncome = Formats.paletteColor(req.income());
        p.colorExpense = Formats.paletteColor(req.expense());
        p.colorInvest = Formats.paletteColor(req.invest());
        return p;
    }

    public Profile updateContribution(Long userId, ContributionDto req) {
        Profile p = require(userId);
        if (req.categoryId() != null) {
            Category c = categories.require(userId, req.categoryId());
            if (c.kind != Kind.DESPESA) throw badRequest("Escolha uma categoria de despesa.");
        }
        p.contributionEnabled = req.enabled();
        p.contributionBps = req.bps();
        p.contributionCategoryId = req.categoryId();
        return p;
    }

    @Transactional
    public void changePassword(Long userId, PasswordReq req) {
        Profile p = require(userId);
        if (p.passwordHash != null && (req.current() == null || !encoder.matches(req.current(), p.passwordHash))) {
            throw badRequest("A senha atual está incorreta.");
        }
        p.passwordHash = encoder.encode(req.next());
    }

    // ================================================================ Privacidade e dados (LGPD): exportar e excluir a conta
    public Map<String, Object> exportData(Long userId) {
        Profile p = require(userId);
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("exportadoEm", Instant.now().toString());
        out.put("perfil", Map.of(
                "nome", Objects.toString(p.name, ""),
                "email", p.email,
                "objetivos", p.objectives().stream().map(Enum::name).toList(),
                "contribuicao", new ContributionDto(p.contributionEnabled, p.contributionBps, p.contributionCategoryId),
                "termosAceitosEm", p.termsAcceptedAt.toString(),
                "criadoEm", p.createdAt.toString()));
        out.put("categorias", categoryRepo.findByUserIdOrderByKindAscSortOrderAsc(userId).stream().map(CategoryDto::of).toList());
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
        Profile p = require(userId);
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
        links.deleteByUserId(userId);
        categoryRepo.deleteByUserId(userId);
        profiles.delete(p);
    }
}
