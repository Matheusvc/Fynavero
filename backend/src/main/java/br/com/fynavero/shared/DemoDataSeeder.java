package br.com.fynavero.shared;

import br.com.fynavero.account.AccountDtos.ContributionDto;
import br.com.fynavero.account.Profile;
import br.com.fynavero.account.Profile.ProfileRepository;
import br.com.fynavero.category.CatalogItem.CatalogItemRepository;
import br.com.fynavero.category.Category;
import br.com.fynavero.category.CategoryDtos.CategoryReq;
import br.com.fynavero.category.CategoryService;
import br.com.fynavero.ledger.Transaction.Kind;
import br.com.fynavero.account.Profile.Objective;
import br.com.fynavero.ledger.Transaction.PaymentMethod;
import br.com.fynavero.ledger.IncomeSource;
import br.com.fynavero.ledger.IncomeSource.IncomeSourceRepository;
import br.com.fynavero.ledger.InstallmentService;
import br.com.fynavero.ledger.LedgerCalendar;
import br.com.fynavero.ledger.LedgerDtos.TxReq;
import br.com.fynavero.ledger.Recurrence;
import br.com.fynavero.ledger.RecurrenceService;
import br.com.fynavero.ledger.TransactionService;
import br.com.fynavero.account.AccountDtos.CustomCategoryInput;
import br.com.fynavero.account.AccountDtos.OnboardingReq;
import br.com.fynavero.account.OnboardingService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.List;
import java.util.Random;
import java.util.Set;

/**
 * Dois perfis de exemplo com 6 meses de histórico, relativos à data de hoje.
 * Criados só com app.demo-data=true (padrão no banco H2 local) e quando ainda não existem.
 */
@Component
public class DemoDataSeeder implements ApplicationRunner {
    private static final Logger log = LoggerFactory.getLogger(DemoDataSeeder.class);
    public static final String PASSWORD = "Fynavero@2026";
    public static final String ANA = "ana@fynavero.dev";
    public static final String JOAO = "joao@fynavero.dev";

    private final AppProperties props;
    private final ProfileRepository profiles;
    private final CatalogItemRepository catalog;
    private final OnboardingService onboarding;
    private final CategoryService categories;
    private final RecurrenceService recurrences;
    private final InstallmentService installments;
    private final TransactionService transactions;
    private final IncomeSourceRepository incomes;
    private final LedgerCalendar calendar;
    private final PasswordEncoder encoder;

    public DemoDataSeeder(AppProperties props, ProfileRepository profiles, CatalogItemRepository catalog,
                          OnboardingService onboarding, CategoryService categories, RecurrenceService recurrences,
                          InstallmentService installments, TransactionService transactions,
                          IncomeSourceRepository incomes, LedgerCalendar calendar, PasswordEncoder encoder) {
        this.props = props;
        this.profiles = profiles;
        this.catalog = catalog;
        this.onboarding = onboarding;
        this.categories = categories;
        this.recurrences = recurrences;
        this.installments = installments;
        this.transactions = transactions;
        this.incomes = incomes;
        this.calendar = calendar;
        this.encoder = encoder;
    }

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        if (!props.demoData() || profiles.findByEmailIgnoreCase(ANA).isPresent()) return;
        seedAna();
        seedJoao();
        log.info("Dados de exemplo criados: {} e {} (senha {}).", ANA, JOAO, PASSWORD);
    }

    /** Organizar gastos + objetivo: dízimo de 10%, notebook em 10×, metas de investimento. */
    private void seedAna() {
        Long id = newProfile(ANA, "Ana Souza");
        onboarding.complete(id, new OnboardingReq("Ana Souza", List.of(Objective.ORGANIZAR, Objective.OBJETIVO), List.of(),
                catalogIds("Moradia", "Contas da casa", "Internet e celular", "Mercado", "Transporte", "Saúde",
                        "Restaurantes e delivery", "Lazer", "Assinaturas", "Cartão de crédito", "Dízimo", "Presentes"),
                List.of(new CustomCategoryInput("Cursos", "graduation-cap", "Estilo de vida")),
                List.of(), new ContributionDto(true, 1000, null)));

        YearMonth now = calendar.currentMonth();
        LocalDate start = now.minusMonths(5).atDay(1);
        fixedIncome(id, "Salário", 420_000, 5, start);
        fixedBill(id, "Moradia", "Aluguel", 135_000, 10, PaymentMethod.BOLETO, start);
        fixedBill(id, "Internet e celular", "Internet + celular", 11_990, 15, PaymentMethod.DEBITO, start);
        fixedBill(id, "Assinaturas", "Streaming", 5_590, 20, PaymentMethod.CREDITO, start);

        Category reserva = cat(id, "Reserva de emergência");
        categories.update(id, reserva.id, new CategoryReq(reserva.name, null, null, null, null, 1_500_000L, null));
        categories.create(id, "Viagem", Kind.INVESTIMENTO, null, "plane", 600_000L);
        installments.create(id, cat(id, "Cartão de crédito").id, "Notebook", InstallmentService.parts(490_000, 10, false),
                day(now.minusMonths(3), 12), PaymentMethod.CREDITO, null);

        Random r = new Random(42);
        for (int i = 5; i >= 0; i--) {
            YearMonth m = now.minusMonths(i);
            for (int k = 0; k < 4; k++) expense(id, "Mercado", between(r, 12_000, 42_000), day(m, 3 + k * 7), "Supermercado", PaymentMethod.DEBITO);
            for (int k = 0; k < 3; k++) expense(id, "Restaurantes e delivery", between(r, 3_500, 11_000), day(m, 6 + k * 8), "Delivery", PaymentMethod.PIX);
            for (int k = 0; k < 4; k++) expense(id, "Transporte", between(r, 1_800, 5_500), day(m, 2 + k * 7), "Aplicativo de transporte", PaymentMethod.CREDITO);
            expense(id, "Contas da casa", between(r, 16_000, 24_000), day(m, 18), "Luz e água", PaymentMethod.BOLETO);
            expense(id, "Lazer", between(r, 8_000, 22_000), day(m, 22), "Cinema e passeio", PaymentMethod.CREDITO);
            if (i % 2 == 0) expense(id, "Saúde", between(r, 6_000, 18_000), day(m, 14), "Farmácia", PaymentMethod.DEBITO);
            if (i % 3 == 1) tx(id, Kind.RECEITA, "Outras receitas", between(r, 60_000, 150_000), day(m, 20), "Freela de design", PaymentMethod.PIX);
            tx(id, Kind.INVESTIMENTO, "Reserva de emergência", 40_000, day(m, 6), "Aporte mensal", PaymentMethod.TRANSFERENCIA);
            tx(id, Kind.INVESTIMENTO, "Viagem", 20_000, day(m, 6), "Aporte viagem", PaymentMethod.TRANSFERENCIA);
        }
    }

    /** Sair das dívidas: salário de R$ 3.575, geladeira em 12× e empréstimo em 24×. */
    private void seedJoao() {
        Long id = newProfile(JOAO, "João Lima");
        onboarding.complete(id, new OnboardingReq("João Lima", List.of(Objective.DIVIDAS), List.of(),
                catalogIds("Moradia", "Contas da casa", "Mercado", "Transporte", "Saúde", "Restaurantes e delivery",
                        "Filhos", "Empréstimos e financiamentos", "Cartão de crédito", "Imprevistos", "Ofertas e doações"),
                List.of(), List.of(), null));

        YearMonth now = calendar.currentMonth();
        LocalDate start = now.minusMonths(5).atDay(1);
        fixedIncome(id, "Salário", 357_500, 1, start);
        fixedBill(id, "Moradia", "Aluguel", 90_000, 5, PaymentMethod.PIX, start);
        fixedBill(id, "Contas da casa", "Luz e água", 21_000, 12, PaymentMethod.BOLETO, start);
        installments.create(id, cat(id, "Cartão de crédito").id, "Geladeira", InstallmentService.parts(240_000, 12, false),
                day(now.minusMonths(4), 8), PaymentMethod.CREDITO, null);
        installments.create(id, cat(id, "Empréstimos e financiamentos").id, "Empréstimo pessoal",
                InstallmentService.parts(600_000, 24, false), day(now.minusMonths(6), 15), PaymentMethod.BOLETO, null);

        Random r = new Random(7);
        for (int i = 5; i >= 0; i--) {
            YearMonth m = now.minusMonths(i);
            for (int k = 0; k < 3; k++) expense(id, "Mercado", between(r, 15_000, 38_000), day(m, 4 + k * 9), "Mercado do bairro", PaymentMethod.DEBITO);
            for (int k = 0; k < 2; k++) expense(id, "Transporte", between(r, 9_000, 16_000), day(m, 10 + k * 12), "Combustível", PaymentMethod.DEBITO);
            expense(id, "Filhos", between(r, 12_000, 30_000), day(m, 16), "Material escolar", PaymentMethod.PIX);
            expense(id, "Restaurantes e delivery", between(r, 4_000, 9_000), day(m, 21), "Lanche", PaymentMethod.PIX);
            if (i % 2 == 1) expense(id, "Imprevistos", between(r, 10_000, 25_000), day(m, 25), "Conserto", PaymentMethod.DINHEIRO);
            expense(id, "Ofertas e doações", 5_000, day(m, 7), "Oferta", PaymentMethod.PIX);
            if (i % 2 == 0) tx(id, Kind.INVESTIMENTO, "Reserva de emergência", 10_000, day(m, 2), "Guardar um pouco", PaymentMethod.TRANSFERENCIA);
        }
    }

    // ------------------------------------------------------------------ auxiliares

    private Long newProfile(String email, String name) {
        Profile p = new Profile();
        p.email = email;
        p.name = name;
        p.passwordHash = encoder.encode(PASSWORD);
        p.termsAcceptedAt = Instant.now();
        p.emailVerifiedAt = Instant.now();
        p.createdAt = Instant.now();
        return profiles.save(p).id;
    }

    private List<Long> catalogIds(String... names) {
        Set<String> wanted = Set.of(names);
        return catalog.findAllByOrderBySortOrder().stream().filter(c -> wanted.contains(c.name)).map(c -> c.id).toList();
    }

    private Category cat(Long userId, String name) {
        return categories.list(userId).stream().filter(c -> c.name.equals(name)).findFirst().orElseThrow();
    }

    /** Renda fixa com histórico: a recorrência começa meses atrás (ocorrências passadas ficam Realizado). */
    private void fixedIncome(Long userId, String name, long cents, int day, LocalDate start) {
        Category c = cat(userId, name);
        Recurrence r = recurrences.create(userId, Kind.RECEITA, c.id, name, cents, day, PaymentMethod.TRANSFERENCIA, start, false);
        IncomeSource s = new IncomeSource();
        s.userId = userId;
        s.name = name;
        s.amountCents = cents;
        s.fixed = true;
        s.categoryId = c.id;
        s.recurrenceId = r.id;
        s.createdAt = Instant.now();
        incomes.save(s);
    }

    private void fixedBill(Long userId, String category, String name, long cents, int day, PaymentMethod pm, LocalDate start) {
        recurrences.create(userId, Kind.DESPESA, cat(userId, category).id, name, cents, day, pm, start, true);
    }

    private void expense(Long userId, String category, long cents, LocalDate date, String desc, PaymentMethod pm) {
        tx(userId, Kind.DESPESA, category, cents, date, desc, pm);
    }

    private void tx(Long userId, Kind kind, String category, long cents, LocalDate date, String desc, PaymentMethod pm) {
        if (date.isAfter(calendar.today())) return;
        transactions.create(userId, new TxReq(kind, cents, cat(userId, category).id, date, desc, pm, null, false, null, null, null, null));
    }

    private static LocalDate day(YearMonth m, int d) {
        return m.atDay(Math.min(d, m.lengthOfMonth()));
    }

    private static long between(Random r, long min, long max) {
        return min + (long) (r.nextDouble() * (max - min));
    }
}
