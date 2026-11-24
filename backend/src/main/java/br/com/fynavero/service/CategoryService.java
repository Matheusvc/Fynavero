package br.com.fynavero.service;

import br.com.fynavero.api.Dto.CategoryReq;
import br.com.fynavero.domain.Entities.Category;
import br.com.fynavero.domain.Enums.Kind;
import br.com.fynavero.domain.Repositories.CategoryRepo;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.*;

import static br.com.fynavero.support.Support.badRequest;
import static br.com.fynavero.support.Support.notFound;

@Service
public class CategoryService {
    public static final List<String> EXPENSE_GROUPS =
            List.of("Essenciais", "Estilo de vida", "Compromissos", "Família", "Contribuições", "Outros");
    private static final int CHART_SLOTS = 6;

    private final CategoryRepo repo;

    public CategoryService(CategoryRepo repo) {
        this.repo = repo;
    }

    public List<Category> list(Long userId) {
        return repo.findByUserIdOrderByKindAscSortOrderAsc(userId);
    }

    public Map<Long, Category> map(Long userId) {
        Map<Long, Category> m = new HashMap<>();
        for (Category c : list(userId)) m.put(c.id, c);
        return m;
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
        c.goalCents = kind == Kind.INVESTIMENTO && goalCents != null && goalCents > 0 ? goalCents : null;
        c.createdAt = Instant.now();
        return repo.save(c);
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
                .filter(c -> c.name.equalsIgnoreCase("Empréstimos e financiamentos")).findFirst()
                .orElseGet(() -> create(userId, "Empréstimos e financiamentos", Kind.DESPESA, "Compromissos", "landmark", null));
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
        if (c.kind == Kind.INVESTIMENTO && req.goalCents() != null) c.goalCents = req.goalCents() > 0 ? req.goalCents() : null;
        return c;
    }

    @Transactional
    public void reorder(Long userId, Kind kind, List<Long> ids) {
        List<Category> cats = repo.findByUserIdAndKindOrderBySortOrder(userId, kind);
        Map<Long, Category> byId = new HashMap<>();
        cats.forEach(c -> byId.put(c.id, c));
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
