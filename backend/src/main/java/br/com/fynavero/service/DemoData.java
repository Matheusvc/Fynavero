package br.com.fynavero.service;

import br.com.fynavero.api.Dto.*;
import br.com.fynavero.domain.Entities.*;
import br.com.fynavero.domain.Enums.Kind;
import br.com.fynavero.domain.Enums.Objective;
import br.com.fynavero.domain.Enums.PaymentMethod;
import br.com.fynavero.domain.Repositories.*;
import br.com.fynavero.support.Support.AppProperties;
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
import java.util.*;

/** Dois perfis de exemplo com 6 meses de histórico, criados só quando app.demo-data=true. */
@Component
public class DemoData implements ApplicationRunner {
    private static final Logger log = LoggerFactory.getLogger(DemoData.class);
    public static final String PASSWORD = "Fynavero@2026";

    private final AppProperties props;
    private final ProfileRepo profiles;
    private final CatalogRepo catalog;
    private final AccountService accounts;
    private final CategoryService categories;
    private final LedgerService ledger;
    private final IncomeSourceRepo incomes;
    private final PasswordEncoder encoder;

    public DemoData(AppProperties props, ProfileRepo profiles, CatalogRepo catalog, AccountService accounts,
                    CategoryService categories, LedgerService ledger, IncomeSourceRepo incomes, PasswordEncoder encoder) {
        this.props = props;
        this.profiles = profiles;
        this.catalog = catalog;
        this.accounts = accounts;
        this.categories = categories;
        this.ledger = ledger;
        this.incomes = incomes;
        this.encoder = encoder;
    }

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        if (!props.demoData() || profiles.findByEmailIgnoreCase("ana@fynavero.dev").isPresent()) return;
        seedAna();
        seedJoao();
        log.info("Dados de exemplo criados: ana@fynavero.dev e joao@fynavero.dev (senha {}).", PASSWORD);
    }

    private Profile newProfile(String email, String name) {
        Profile p = new Profile();
        p.email = email;
        p.name = name;
        p.passwordHash = encoder.encode(PASSWORD);
        p.termsAcceptedAt = Instant.now();
        p.createdAt = Instant.now();
        return profiles.save(p);
    }

    private List<Long> catalogIds(String... names) {
        Set<String> wanted = Set.of(names);
        return catalog.findAllByOrderBySortOrder().stream().filter(c -> wanted.contains(c.name)).map(c -> c.id).toList();
    }

    private Category cat(Long userId, String name) {
        return categories.list(userId).stream().filter(c -> c.name.equals(name)).findFirst().orElseThrow();
    }

    private void fixedIncome(Long userId, String name, long cents, int day, LocalDate start) {
        Category c = cat(userId, name);
        Recurrence r = ledger.createRecurrence(userId, Kind.RECEITA, c.id, name, cents, day, PaymentMethod.TRANSFERENCIA, start, false);
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

    private void tx(Long userId, Kind kind, String category, long cents, LocalDate date, String desc, PaymentMethod pm) {
        if (date.isAfter(ledger.today())) return;
        ledger.create(userId, new TxReq(kind, cents, cat(userId, category).id, date, desc, pm, null, false, null, null, null));
    }

    private static LocalDate day(YearMonth m, int d) {
        return m.atDay(Math.min(d, m.lengthOfMonth()));
    }

    private static long between(Random r, long min, long max) {
        return min + (long) (r.nextDouble() * (max - min));
    }

    private void seedAna() {
        Profile p = newProfile("ana@fynavero.dev", "Ana Souza");
        Long id = p.id;
        accounts.completeOnboarding(id, new OnboardingReq("Ana Souza", List.of(Objective.ORGANIZAR, Objective.OBJETIVO), List.of(),
                catalogIds("Moradia", "Contas da casa", "Internet e celular", "Mercado", "Transporte", "Saúde",
                        "Restaurantes e delivery", "Lazer", "Assinaturas", "Cartão de crédito", "Dízimo", "Presentes"),
                List.of(new CustomCategoryInput("Cursos", "graduation-cap", "Estilo de vida")),
                List.of(), new ContributionDto(true, 1000, null)));

        YearMonth now = YearMonth.from(ledger.today());
        LocalDate start = now.minusMonths(5).atDay(1);
        fixedIncome(id, "Salário", 420_000, 5, start);
        ledger.createRecurrence(id, Kind.DESPESA, cat(id, "Moradia").id, "Aluguel", 135_000, 10, PaymentMethod.BOLETO, start, true);
        ledger.createRecurrence(id, Kind.DESPESA, cat(id, "Internet e celular").id, "Internet + celular", 11_990, 15, PaymentMethod.DEBITO, start, true);
        ledger.createRecurrence(id, Kind.DESPESA, cat(id, "Assinaturas").id, "Streaming", 5_590, 20, PaymentMethod.CREDITO, start, true);

        Category reserva = cat(id, "Reserva de emergência");
        categories.update(id, reserva.id, new CategoryReq(reserva.name, null, null, null, null, 1_500_000L));
        categories.create(id, "Viagem", Kind.INVESTIMENTO, null, "plane", 600_000L);

        ledger.createInstallments(id, cat(id, "Cartão de crédito").id, "Notebook", 490_000, 10,
                day(now.minusMonths(3), 12), PaymentMethod.CREDITO, null);

        Random r = new Random(42);
        for (int i = 5; i >= 0; i--) {
            YearMonth m = now.minusMonths(i);
            for (int k = 0; k < 4; k++) tx(id, Kind.DESPESA, "Mercado", between(r, 12_000, 42_000), day(m, 3 + k * 7), "Supermercado", PaymentMethod.DEBITO);
            for (int k = 0; k < 3; k++) tx(id, Kind.DESPESA, "Restaurantes e delivery", between(r, 3_500, 11_000), day(m, 6 + k * 8), "Delivery", PaymentMethod.PIX);
            for (int k = 0; k < 4; k++) tx(id, Kind.DESPESA, "Transporte", between(r, 1_800, 5_500), day(m, 2 + k * 7), "Aplicativo de transporte", PaymentMethod.CREDITO);
            tx(id, Kind.DESPESA, "Contas da casa", between(r, 16_000, 24_000), day(m, 18), "Luz e água", PaymentMethod.BOLETO);
            tx(id, Kind.DESPESA, "Lazer", between(r, 8_000, 22_000), day(m, 22), "Cinema e passeio", PaymentMethod.CREDITO);
            if (i % 2 == 0) tx(id, Kind.DESPESA, "Saúde", between(r, 6_000, 18_000), day(m, 14), "Farmácia", PaymentMethod.DEBITO);
            if (i % 3 == 1) tx(id, Kind.RECEITA, "Outras receitas", between(r, 60_000, 150_000), day(m, 20), "Freela de design", PaymentMethod.PIX);
            tx(id, Kind.INVESTIMENTO, "Reserva de emergência", 40_000, day(m, 6), "Aporte mensal", PaymentMethod.TRANSFERENCIA);
            tx(id, Kind.INVESTIMENTO, "Viagem", 20_000, day(m, 6), "Aporte viagem", PaymentMethod.TRANSFERENCIA);
        }
    }

    private void seedJoao() {
        Profile p = newProfile("joao@fynavero.dev", "João Lima");
        Long id = p.id;
        accounts.completeOnboarding(id, new OnboardingReq("João Lima", List.of(Objective.DIVIDAS), List.of(),
                catalogIds("Moradia", "Contas da casa", "Mercado", "Transporte", "Saúde", "Restaurantes e delivery",
                        "Filhos", "Empréstimos e financiamentos", "Cartão de crédito", "Imprevistos", "Ofertas e doações"),
                List.of(), List.of(), null));

        YearMonth now = YearMonth.from(ledger.today());
        LocalDate start = now.minusMonths(5).atDay(1);
        fixedIncome(id, "Salário", 357_500, 1, start);
        ledger.createRecurrence(id, Kind.DESPESA, cat(id, "Moradia").id, "Aluguel", 90_000, 5, PaymentMethod.PIX, start, true);
        ledger.createRecurrence(id, Kind.DESPESA, cat(id, "Contas da casa").id, "Luz e água", 21_000, 12, PaymentMethod.BOLETO, start, true);

        ledger.createInstallments(id, cat(id, "Cartão de crédito").id, "Geladeira", 240_000, 12,
                day(now.minusMonths(4), 8), PaymentMethod.CREDITO, null);
        ledger.createInstallments(id, cat(id, "Empréstimos e financiamentos").id, "Empréstimo pessoal", 600_000, 24,
                day(now.minusMonths(6), 15), PaymentMethod.BOLETO, null);

        Random r = new Random(7);
        for (int i = 5; i >= 0; i--) {
            YearMonth m = now.minusMonths(i);
            for (int k = 0; k < 3; k++) tx(id, Kind.DESPESA, "Mercado", between(r, 15_000, 38_000), day(m, 4 + k * 9), "Mercado do bairro", PaymentMethod.DEBITO);
            for (int k = 0; k < 2; k++) tx(id, Kind.DESPESA, "Transporte", between(r, 9_000, 16_000), day(m, 10 + k * 12), "Combustível", PaymentMethod.DEBITO);
            tx(id, Kind.DESPESA, "Filhos", between(r, 12_000, 30_000), day(m, 16), "Material escolar", PaymentMethod.PIX);
            tx(id, Kind.DESPESA, "Restaurantes e delivery", between(r, 4_000, 9_000), day(m, 21), "Lanche", PaymentMethod.PIX);
            if (i % 2 == 1) tx(id, Kind.DESPESA, "Imprevistos", between(r, 10_000, 25_000), day(m, 25), "Conserto", PaymentMethod.DINHEIRO);
            tx(id, Kind.DESPESA, "Ofertas e doações", 5_000, day(m, 7), "Oferta", PaymentMethod.PIX);
            if (i % 2 == 0) tx(id, Kind.INVESTIMENTO, "Reserva de emergência", 10_000, day(m, 2), "Guardar um pouco", PaymentMethod.TRANSFERENCIA);
        }
    }
}
