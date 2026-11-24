package br.com.fynavero.service;

import br.com.fynavero.api.Dto.*;
import br.com.fynavero.domain.Entities.*;
import br.com.fynavero.domain.Enums.Kind;
import br.com.fynavero.domain.Repositories.*;
import br.com.fynavero.support.Support.AppProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Duration;
import java.time.Instant;
import java.util.*;

import static br.com.fynavero.support.Support.*;

/** Conta, onboarding, perfil, fontes de renda e direitos da LGPD. */
@Service
public class AccountService {
    private static final Logger log = LoggerFactory.getLogger(AccountService.class);
    private static final SecureRandom RANDOM = new SecureRandom();

    private final ProfileRepo profiles;
    private final CatalogRepo catalog;
    private final CategoryService categories;
    private final LedgerService ledger;
    private final IncomeSourceRepo incomes;
    private final RecurrenceRepo recs;
    private final TransactionRepo txs;
    private final InstallmentPlanRepo plans;
    private final ImportBatchRepo batches;
    private final CategoryRepo categoryRepo;
    private final MagicLinkRepo magicLinks;
    private final PasswordEncoder encoder;
    private final ObjectProvider<JavaMailSender> mail;
    private final AppProperties props;

    public AccountService(ProfileRepo profiles, CatalogRepo catalog, CategoryService categories, LedgerService ledger,
                          IncomeSourceRepo incomes, RecurrenceRepo recs, TransactionRepo txs, InstallmentPlanRepo plans,
                          ImportBatchRepo batches, CategoryRepo categoryRepo, MagicLinkRepo magicLinks,
                          PasswordEncoder encoder, ObjectProvider<JavaMailSender> mail, AppProperties props) {
        this.profiles = profiles;
        this.catalog = catalog;
        this.categories = categories;
        this.ledger = ledger;
        this.incomes = incomes;
        this.recs = recs;
        this.txs = txs;
        this.plans = plans;
        this.batches = batches;
        this.categoryRepo = categoryRepo;
        this.magicLinks = magicLinks;
        this.encoder = encoder;
        this.mail = mail;
        this.props = props;
    }

    public Profile require(Long userId) {
        return profiles.findById(userId).orElseThrow(() -> notFound());
    }

    public Me me(Profile p) {
        return new Me(p.id, p.name, p.email, p.objectives(), p.onboardingDone,
                new ContributionDto(p.contributionEnabled, p.contributionBps, p.contributionCategoryId),
                p.passwordHash != null);
    }

    // ------------------------------------------------------------------ cadastro e link mágico

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

    public boolean magicLinkEnabled() {
        return true;
    }

    @Transactional
    public void requestMagicLink(String email) {
        Optional<Profile> p = profiles.findByEmailIgnoreCase(email.strip());
        if (p.isEmpty()) return; // mesma resposta para e-mails sem conta
        byte[] raw = new byte[32];
        RANDOM.nextBytes(raw);
        String token = Base64.getUrlEncoder().withoutPadding().encodeToString(raw);
        MagicLinkToken t = new MagicLinkToken();
        t.userId = p.get().id;
        t.tokenHash = sha256(token);
        t.expiresAt = Instant.now().plus(Duration.ofMinutes(15));
        magicLinks.save(t);
        String link = props.baseUrl() + "/entrar/link?token=" + token;
        JavaMailSender sender = mail.getIfAvailable();
        if (sender != null) {
            SimpleMailMessage m = new SimpleMailMessage();
            m.setFrom(props.mailFrom());
            m.setTo(p.get().email);
            m.setSubject("Seu link para entrar na Fynavero");
            m.setText("Olá!\n\nUse este link para entrar. Ele vale por 15 minutos e só pode ser usado uma vez:\n\n"
                    + link + "\n\nSe não foi você, ignore este e-mail.");
            sender.send(m);
        } else {
            log.info("Link mágico (sem servidor de e-mail configurado) para {}: {}", p.get().email, link);
        }
    }

    @Transactional
    public Profile consumeMagicLink(String token) {
        MagicLinkToken t = magicLinks.findByTokenHash(sha256(token))
                .orElseThrow(() -> badRequest("Link inválido ou expirado. Peça um novo."));
        if (t.usedAt != null || t.expiresAt.isBefore(Instant.now())) {
            throw badRequest("Link inválido ou expirado. Peça um novo.");
        }
        t.usedAt = Instant.now();
        return require(t.userId);
    }

    private static String sha256(String s) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(s.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }

    // ------------------------------------------------------------------ onboarding

    public List<CatalogDto> catalog() {
        return catalog.findAllByOrderBySortOrder().stream()
                .map(c -> new CatalogDto(c.id, c.name, c.icon, c.group)).toList();
    }

    @Transactional
    public Profile completeOnboarding(Long userId, OnboardingReq req) {
        Profile p = require(userId);
        if (p.onboardingDone) throw badRequest("A configuração inicial já foi concluída.");
        if (trimOrNull(req.name()) != null) p.name = req.name().strip();
        p.setObjectives(req.objectives());

        Map<String, Category> byKey = new LinkedHashMap<>();
        Map<Long, CatalogItem> catalogById = new HashMap<>();
        catalog.findAll().forEach(c -> catalogById.put(c.id, c));
        List<Long> chosen = req.catalogIds() == null ? List.of() : req.catalogIds().stream().distinct().toList();
        for (Long id : chosen) {
            CatalogItem item = catalogById.get(id);
            if (item == null) continue;
            byKey.put("catalog:" + id, categories.create(userId, item.name, Kind.DESPESA, item.group, item.icon, null));
        }
        if (req.customCategories() != null) {
            for (CustomCategoryInput cc : req.customCategories()) {
                byKey.put("custom:" + cc.name().strip(),
                        categories.create(userId, cc.name(), Kind.DESPESA, cc.group(), cc.icon(), null));
            }
        }
        if (byKey.isEmpty()) {
            // Pulou a escolha: começa com o essencial para conseguir lançar.
            catalogById.values().stream()
                    .filter(c -> c.group.equals("Essenciais") || c.name.equals("Lazer") || c.name.equals("Imprevistos"))
                    .sorted(Comparator.comparingInt(c -> c.sortOrder))
                    .forEach(c -> byKey.put("catalog:" + c.id, categories.create(userId, c.name, Kind.DESPESA, c.group, c.icon, null)));
        }

        if (req.incomes() != null) {
            for (IncomeInput in : req.incomes()) {
                createIncomeSource(userId, new IncomeSourceReq(in.name(), in.amountCents(), in.fixed(), in.day()));
            }
        }
        if (categoryRepo.countByUserIdAndKind(userId, Kind.RECEITA) == 0) {
            categories.create(userId, "Salário", Kind.RECEITA, null, "briefcase", null);
        }
        categories.create(userId, "Outras receitas", Kind.RECEITA, null, "wallet", null);
        categories.ensureLoanCategory(userId);
        categories.create(userId, "Reserva de emergência", Kind.INVESTIMENTO, null, "piggy-bank", null);

        if (req.fixedBills() != null) {
            Category fallback = byKey.values().iterator().next();
            for (FixedBillInput fb : req.fixedBills()) {
                Category c = byKey.getOrDefault(fb.categoryKey(), fallback);
                ledger.createRecurrence(userId, Kind.DESPESA, c.id, fb.name(), fb.amountCents(), fb.day(), null,
                        ledger.nextOccurrence(fb.day()), true);
            }
        }

        if (req.contribution() != null && req.contribution().enabled()) {
            Long contribCat = byKey.values().stream()
                    .filter(c -> c.group.equals("Contribuições")).map(c -> c.id).findFirst().orElse(null);
            p.contributionEnabled = true;
            p.contributionBps = req.contribution().bps();
            p.contributionCategoryId = contribCat;
        }
        p.onboardingDone = true;
        return p;
    }

    // ------------------------------------------------------------------ perfil

    @Transactional
    public Profile updateProfile(Long userId, ProfileReq req) {
        Profile p = require(userId);
        p.name = req.name().strip();
        p.setObjectives(req.objectives());
        return p;
    }

    @Transactional
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

    // ------------------------------------------------------------------ fontes de renda

    public List<IncomeSourceDto> incomeSources(Long userId) {
        return incomes.findByUserIdOrderByCreatedAt(userId).stream().map(this::dto).toList();
    }

    private IncomeSourceDto dto(IncomeSource s) {
        Integer day = s.recurrenceId == null ? null
                : recs.findById(s.recurrenceId).map(r -> r.dayOfMonth).orElse(null);
        return new IncomeSourceDto(s.id, s.name, s.amountCents, s.fixed, s.categoryId, day);
    }

    @Transactional
    public IncomeSourceDto createIncomeSource(Long userId, IncomeSourceReq req) {
        String name = req.name().strip();
        Category cat = categories.list(userId).stream()
                .filter(c -> c.kind == Kind.RECEITA && c.name.equalsIgnoreCase(name)).findFirst()
                .orElseGet(() -> categories.create(userId, name, Kind.RECEITA, null, "briefcase", null));
        IncomeSource s = new IncomeSource();
        s.userId = userId;
        s.name = name;
        s.amountCents = req.amountCents();
        s.fixed = req.fixed();
        s.categoryId = cat.id;
        s.createdAt = Instant.now();
        if (req.amountCents() > 0) {
            int day = req.day() == null ? 5 : req.day();
            s.recurrenceId = ledger.createRecurrence(userId, Kind.RECEITA, cat.id, incomeDescription(name, req.fixed()),
                    req.amountCents(), day, null, ledger.nextOccurrence(day), false).id;
        }
        return dto(incomes.save(s));
    }

    /** Renda variável entra todo mês como Previsto com o valor estimado, para ajustar quando receber. */
    private static String incomeDescription(String name, boolean fixed) {
        return fixed ? name : name + " (valor estimado)";
    }

    @Transactional
    public IncomeSourceDto updateIncomeSource(Long userId, Long id, IncomeSourceReq req) {
        IncomeSource s = incomes.findByIdAndUserId(id, userId).orElseThrow(() -> notFound());
        s.name = req.name().strip();
        s.amountCents = req.amountCents();
        boolean wantsRecurrence = req.amountCents() > 0;
        int day = req.day() == null ? 5 : req.day();
        String desc = incomeDescription(s.name, req.fixed());
        if (s.recurrenceId != null && wantsRecurrence) {
            ledger.updateRecurrence(userId, s.recurrenceId,
                    new RecurrenceReq(Kind.RECEITA, s.categoryId, desc, s.amountCents, day, null));
        } else if (s.recurrenceId != null) {
            ledger.endRecurrence(userId, s.recurrenceId);
            s.recurrenceId = null;
        } else if (wantsRecurrence) {
            s.recurrenceId = ledger.createRecurrence(userId, Kind.RECEITA, s.categoryId, desc, s.amountCents, day,
                    null, ledger.nextOccurrence(day), false).id;
        }
        s.fixed = req.fixed();
        return dto(s);
    }

    @Transactional
    public void deleteIncomeSource(Long userId, Long id) {
        IncomeSource s = incomes.findByIdAndUserId(id, userId).orElseThrow(() -> notFound());
        if (s.recurrenceId != null) ledger.endRecurrence(userId, s.recurrenceId);
        incomes.delete(s);
    }

    // ------------------------------------------------------------------ LGPD

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
        out.put("categorias", categories.list(userId).stream().map(CategoryDto::of).toList());
        out.put("fontesDeRenda", incomeSources(userId));
        out.put("recorrencias", recs.findByUserIdOrderByDayOfMonth(userId).stream()
                .map(r -> new RecurrenceDto(r.id, r.kind, r.categoryId, r.description, r.amountCents, r.dayOfMonth,
                        r.paymentMethod, r.startDate, r.endDate, r.fixedBill)).toList());
        out.put("parcelamentos", plans.findByUserIdOrderByFirstDateDesc(userId).stream()
                .map(x -> Map.of("id", x.id, "descricao", Objects.toString(x.description, ""), "totalCentavos", x.totalCents,
                        "parcelas", x.installmentCount, "primeiraData", x.firstDate.toString())).toList());
        out.put("lancamentos", txs.findAllActive(userId).stream().map(TxDto::of).toList());
        out.put("importacoes", batches.findByUserIdOrderByCreatedAtDesc(userId).stream()
                .map(b -> Map.of("id", b.id, "arquivo", b.fileName, "linhas", b.rowCount, "criadoEm", b.createdAt.toString()))
                .toList());
        return out;
    }

    /** Exclusão definitiva: apaga todos os dados da pessoa. */
    @Transactional
    public void deleteAccount(Long userId, DeleteAccountReq req) {
        Profile p = require(userId);
        if (!"EXCLUIR".equals(req.confirm() == null ? null : req.confirm().strip().toUpperCase(Locale.ROOT))) {
            throw badRequest("Digite EXCLUIR para confirmar.");
        }
        if (p.passwordHash != null && (req.password() == null || !encoder.matches(req.password(), p.passwordHash))) {
            throw badRequest("Senha incorreta.");
        }
        txs.deleteByUserId(userId);
        incomes.deleteByUserId(userId);
        recs.deleteByUserId(userId);
        plans.deleteByUserId(userId);
        batches.deleteByUserId(userId);
        magicLinks.deleteByUserId(userId);
        categoryRepo.deleteByUserId(userId);
        profiles.delete(p);
    }
}
