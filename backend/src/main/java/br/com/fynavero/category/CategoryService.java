package br.com.fynavero.category;

import br.com.fynavero.category.CatalogItem.CatalogItemRepository;

import br.com.fynavero.category.Category.CategoryRepository;

import br.com.fynavero.category.CategoryDtos.CatalogDto;
import br.com.fynavero.category.CategoryDtos.CategoryReq;
import br.com.fynavero.ledger.Transaction.Kind;
import br.com.fynavero.shared.Formats;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import static br.com.fynavero.shared.Errors.badRequest;
import static br.com.fynavero.shared.Errors.notFound;

@Service
public class CategoryService {
    /** Grupos de despesa, na ordem em que aparecem no relatório anual. */
    public static final List<String> EXPENSE_GROUPS =
            List.of("Essenciais", "Estilo de vida", "Compromissos", "Família", "Contribuições", "Outros");
    public static final String LOAN_PAYMENT_CATEGORY = "Empréstimos e financiamentos";
    private static final int CHART_SLOTS = 6;

    private final CategoryRepository repo;
    private final CatalogItemRepository catalog;

    public CategoryService(CategoryRepository repo, CatalogItemRepository catalog) {
        this.repo = repo;
        this.catalog = catalog;
    }

    public List<CatalogDto> catalog() {
        return catalog.findAllByOrderBySortOrder().stream().map(CatalogDto::of).toList();
    }

    public List<Category> list(Long userId) {
        return repo.findByUserIdOrderByKindAscSortOrderAsc(userId);
    }

    public Map<Long, Category> map(Long userId) {
        Map<Long, Category> m = new HashMap<>();
        for (Category c : list(userId)) m.put(c.id, c);
        return m;
    }

    /** Ids das categorias de empréstimo recebido. */
    public static Set<Long> loanIds(Iterable<Category> cats) {
        Set<Long> out = new HashSet<>();
        for (Category c : cats) if (c.loan) out.add(c.id);
        return out;
    }

    public long count(Long userId, Kind kind) {
        return repo.countByUserIdAndKind(userId, kind);
    }

    public Category require(Long userId, Long id) {
        if (id == null) throw badRequest("Escolha uma categoria.");
        return repo.findByIdAndUserId(id, userId).orElseThrow(() -> badRequest("Categoria não encontrada."));
    }

    @Transactional
    public Category create(Long userId, String name, Kind kind, String group, String icon, Long goalCents) {
        String clean = name == null ? "" : name.strip();
        if (clean.isEmpty()) throw badRequest("Dê um nome para a categoria.");
        if (kind == null) throw badRequest("Escolha o tipo da categoria.");
        boolean exists = repo.findByUserIdAndKindOrderBySortOrder(userId, kind).stream()
                .anyMatch(c -> c.name.equalsIgnoreCase(clean));
        if (exists) throw badRequest("Você já tem uma categoria com esse nome.");
        long count = repo.countByUserIdAndKind(userId, kind);
        Category c = new Category();
        c.userId = userId;
        c.name = clean;
        c.kind = kind;
        c.group = normalizeGroup(kind, group);
        c.icon = icon == null || icon.isBlank() ? defaultIcon(kind) : icon;
        c.color = "chart-" + (count % CHART_SLOTS + 1);
        c.sortOrder = (int) count;
        c.goalCents = kind != Kind.RECEITA && goalCents != null && goalCents > 0 ? goalCents : null;
        c.createdAt = Instant.now();
        return repo.save(c);
    }

    /** Nova categoria pela tela, com a cor escolhida (sem cor, a próxima da paleta). */
    @Transactional
    public Category create(Long userId, CategoryReq req) {
        Category c = create(userId, req.name(), req.kind(), req.group(), req.icon(), req.goalCents());
        String color = Formats.paletteColor(req.color());
        if (color != null) c.color = color;
        return c;
    }

    /** Receita "Empréstimo": entra no saldo, mas aparece separada da renda. */
    @Transactional
    public Category ensureLoanCategory(Long userId) {
        return repo.findByUserIdAndKindOrderBySortOrder(userId, Kind.RECEITA).stream()
                .filter(c -> c.loan).findFirst()
                .orElseGet(() -> {
                    Category c = create(userId, "Empréstimo", Kind.RECEITA, null, "landmark", null);
                    c.loan = true;
                    return c;
                });
    }

    /** Despesa onde entram as parcelas de pagamento de empréstimos. */
    @Transactional
    public Category ensureLoanPaymentCategory(Long userId) {
        return repo.findByUserIdAndKindOrderBySortOrder(userId, Kind.DESPESA).stream()
                .filter(c -> c.name.equalsIgnoreCase(LOAN_PAYMENT_CATEGORY)).findFirst()
                .orElseGet(() -> create(userId, LOAN_PAYMENT_CATEGORY, Kind.DESPESA, "Compromissos", "landmark", null));
    }

    @Transactional
    public Category update(Long userId, Long id, CategoryReq req) {
        Category c = repo.findByIdAndUserId(id, userId).orElseThrow(() -> notFound());
        String clean = req.name().strip();
        boolean clash = repo.findByUserIdAndKindOrderBySortOrder(userId, c.kind).stream()
                .anyMatch(o -> !o.id.equals(id) && o.name.equalsIgnoreCase(clean));
        if (clash) throw badRequest("Você já tem uma categoria com esse nome.");
        c.name = clean;
        if (req.group() != null) c.group = normalizeGroup(c.kind, req.group());
        if (req.icon() != null && !req.icon().isBlank()) c.icon = req.icon();
        if (req.hidden() != null) c.hidden = req.hidden();
        if (req.color() != null && !req.color().isBlank()) c.color = Formats.paletteColor(req.color());
        if (c.kind != Kind.RECEITA && req.goalCents() != null) c.goalCents = req.goalCents() > 0 ? req.goalCents() : null;
        if (c.goalCents == null) c.goalDeadline = null;
        return c;
    }

    @Transactional
    public void reorder(Long userId, Kind kind, List<Long> ids) {
        List<Category> cats = repo.findByUserIdAndKindOrderBySortOrder(userId, kind);
        Map<Long, Category> byId = cats.stream().collect(Collectors.toMap(c -> c.id, c -> c));
        if (!byId.keySet().containsAll(ids)) throw notFound();
        int i = 0;
        for (Long id : ids) byId.get(id).sortOrder = i++;
        for (Category c : cats) if (!ids.contains(c.id)) c.sortOrder = i++;
    }

    private static String normalizeGroup(Kind kind, String group) {
        return switch (kind) {
            case RECEITA -> "Receitas";
            case INVESTIMENTO -> "Investimentos";
            case DESPESA -> group != null && EXPENSE_GROUPS.contains(group) ? group : "Outros";
        };
    }

    private static String defaultIcon(Kind kind) {
        return switch (kind) {
            case RECEITA -> "wallet";
            case INVESTIMENTO -> "piggy-bank";
            case DESPESA -> "tag";
        };
    }
}
