package br.com.fynavero.account;

import br.com.fynavero.account.AccountService;
import br.com.fynavero.account.Profile;
import br.com.fynavero.category.CatalogItem;
import br.com.fynavero.category.CatalogItemRepository;
import br.com.fynavero.category.Category;
import br.com.fynavero.category.CategoryService;
import br.com.fynavero.ledger.Kind;
import br.com.fynavero.ledger.IncomeDtos.IncomeSourceReq;
import br.com.fynavero.ledger.IncomeSourceService;
import br.com.fynavero.ledger.LedgerCalendar;
import br.com.fynavero.ledger.RecurrenceService;
import br.com.fynavero.account.OnboardingDtos.CustomCategoryInput;
import br.com.fynavero.account.OnboardingDtos.FixedBillInput;
import br.com.fynavero.account.OnboardingDtos.IncomeInput;
import br.com.fynavero.account.OnboardingDtos.OnboardingReq;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

import static br.com.fynavero.shared.Errors.badRequest;
import static br.com.fynavero.shared.Formats.trimOrNull;

/** Configuração inicial: objetivos, renda, categorias, contas fixas e contribuição. */
@Service
public class OnboardingService {
    private final AccountService accounts;
    private final CatalogItemRepository catalog;
    private final CategoryService categories;
    private final IncomeSourceService incomes;
    private final RecurrenceService recurrences;
    private final LedgerCalendar calendar;

    public OnboardingService(AccountService accounts, CatalogItemRepository catalog, CategoryService categories,
                             IncomeSourceService incomes, RecurrenceService recurrences, LedgerCalendar calendar) {
        this.accounts = accounts;
        this.catalog = catalog;
        this.categories = categories;
        this.incomes = incomes;
        this.recurrences = recurrences;
        this.calendar = calendar;
    }

    @Transactional
    public Profile complete(Long userId, OnboardingReq req) {
        Profile p = accounts.require(userId);
        if (p.onboardingDone) throw badRequest("A configuração inicial já foi concluída.");
        if (trimOrNull(req.name()) != null) p.name = req.name().strip();
        p.setObjectives(req.objectives());

        Map<String, Category> chosen = createExpenseCategories(userId, req);
        createIncomeAndDefaultCategories(userId, req.incomes());
        createFixedBills(userId, req.fixedBills(), chosen);

        if (req.contribution() != null && req.contribution().enabled()) {
            p.contributionEnabled = true;
            p.contributionBps = req.contribution().bps();
            p.contributionCategoryId = chosen.values().stream()
                    .filter(c -> c.group.equals("Contribuições")).map(c -> c.id).findFirst().orElse(null);
        }
        p.onboardingDone = true;
        return p;
    }

    /** Categorias da tela 3 indexadas pela chave usada nas contas fixas ("catalog:{id}" ou "custom:{nome}"). */
    private Map<String, Category> createExpenseCategories(Long userId, OnboardingReq req) {
        Map<Long, CatalogItem> catalogById = catalog.findAll().stream()
                .collect(Collectors.toMap(c -> c.id, Function.identity()));
        Map<String, Category> byKey = new LinkedHashMap<>();
        List<Long> ids = req.catalogIds() == null ? List.of() : req.catalogIds().stream().distinct().toList();
        for (Long id : ids) {
            CatalogItem item = catalogById.get(id);
            if (item != null) byKey.put("catalog:" + id, fromCatalog(userId, item));
        }
        if (req.customCategories() != null) {
            for (CustomCategoryInput cc : req.customCategories()) {
                byKey.put("custom:" + cc.name().strip(), categories.create(userId, cc.name(), Kind.DESPESA, cc.group(), cc.icon(), null));
            }
        }
        if (byKey.isEmpty()) {
            // Pulou a escolha: começa com o essencial para conseguir lançar.
            catalogById.values().stream()
                    .filter(c -> c.group.equals("Essenciais") || c.name.equals("Lazer") || c.name.equals("Imprevistos"))
                    .sorted(Comparator.comparingInt(c -> c.sortOrder))
                    .forEach(c -> byKey.put("catalog:" + c.id, fromCatalog(userId, c)));
        }
        return byKey;
    }

    private Category fromCatalog(Long userId, CatalogItem item) {
        return categories.create(userId, item.name, Kind.DESPESA, item.group, item.icon, null);
    }

    private void createIncomeAndDefaultCategories(Long userId, List<IncomeInput> list) {
        if (list != null) {
            for (IncomeInput in : list) {
                incomes.create(userId, new IncomeSourceReq(in.name(), in.amountCents(), in.fixed(), in.day()));
            }
        }
        if (categories.count(userId, Kind.RECEITA) == 0) {
            categories.create(userId, "Salário", Kind.RECEITA, null, "briefcase", null);
        }
        categories.create(userId, "Outras receitas", Kind.RECEITA, null, "wallet", null);
        categories.ensureLoanCategory(userId);
        categories.create(userId, "Reserva de emergência", Kind.INVESTIMENTO, null, "piggy-bank", null);
    }

    private void createFixedBills(Long userId, List<FixedBillInput> bills, Map<String, Category> chosen) {
        if (bills == null || bills.isEmpty()) return;
        Category fallback = chosen.values().iterator().next();
        for (FixedBillInput fb : bills) {
            Category c = chosen.getOrDefault(fb.categoryKey(), fallback);
            recurrences.create(userId, Kind.DESPESA, c.id, fb.name(), fb.amountCents(), fb.day(), null,
                    calendar.nextOccurrence(fb.day()), true);
        }
    }
}
