package br.com.fynavero.calc;

import br.com.fynavero.domain.Enums.Kind;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.*;

/**
 * Único módulo de cálculo da plataforma. Sem acesso a banco: recebe lançamentos e devolve números.
 * Todos os valores em centavos inteiros (long), com aritmética que falha em vez de transbordar.
 *
 * TotalReceitas  = Σ receitas do mês
 * Contribuicao   = ativa ? TotalReceitas × percentual : 0   (entra como despesa)
 * TotalDespesas  = Σ despesas do mês + Contribuicao
 * SobraDoMes     = TotalReceitas − TotalDespesas
 * InvestidoNoMes = Σ investimentos do mês                   (investimento NÃO é despesa)
 * SaldoParaOMes  = SobraDoMes − InvestidoNoMes
 */
public final class FinanceCalculator {
    private FinanceCalculator() {}

    /** Chave usada para a contribuição quando ela não está ligada a uma categoria. */
    public static final long CONTRIBUTION_KEY = -1L;

    /** loan = receita que é empréstimo recebido: entra no saldo, mas não é renda nem base da contribuição. */
    public record Entry(Kind kind, long amountCents, Long categoryId, boolean loan) {
        public Entry(Kind kind, long amountCents, Long categoryId) {
            this(kind, amountCents, categoryId, false);
        }
    }

    /** Percentual em pontos-base: 1000 = 10%. */
    public record Contribution(boolean enabled, int bps, Long categoryId) {
        public static final Contribution NONE = new Contribution(false, 0, null);
    }

    /** receitas inclui emprestimos; emprestimos é a parte que veio de empréstimo recebido. */
    public record MonthTotals(long receitas, long despesasLancadas, long contribuicao, long despesas,
                              long sobra, long investido, long saldo, long emprestimos) {
        public static final MonthTotals ZERO = new MonthTotals(0, 0, 0, 0, 0, 0, 0, 0);
    }

    public record Slice(Long categoryId, long amountCents) {
        public boolean isOthers() { return categoryId == null; }
    }

    public static long contribution(long receitas, Contribution c) {
        if (c == null || !c.enabled() || receitas <= 0 || c.bps() <= 0) return 0;
        return BigDecimal.valueOf(receitas)
                .multiply(BigDecimal.valueOf(c.bps()))
                .divide(BigDecimal.valueOf(10_000), 0, RoundingMode.HALF_UP)
                .longValueExact();
    }

    public static MonthTotals totals(Collection<Entry> entries, Contribution c) {
        long receitas = 0, emprestimos = 0, despesas = 0, investido = 0;
        for (Entry e : entries) {
            if (e.amountCents() < 0) throw new IllegalArgumentException("Valor negativo em lançamento");
            switch (e.kind()) {
                case RECEITA -> {
                    receitas = Math.addExact(receitas, e.amountCents());
                    if (e.loan()) emprestimos = Math.addExact(emprestimos, e.amountCents());
                }
                case DESPESA -> despesas = Math.addExact(despesas, e.amountCents());
                case INVESTIMENTO -> investido = Math.addExact(investido, e.amountCents());
            }
        }
        long contrib = contribution(Math.subtractExact(receitas, emprestimos), c);
        long totalDespesas = Math.addExact(despesas, contrib);
        long sobra = Math.subtractExact(receitas, totalDespesas);
        long saldo = Math.subtractExact(sobra, investido);
        return new MonthTotals(receitas, despesas, contrib, totalDespesas, sobra, investido, saldo, emprestimos);
    }

    /** Soma por categoria de um tipo. Para despesas, inclui a contribuição na categoria configurada. */
    public static Map<Long, Long> byCategory(Collection<Entry> entries, Kind kind, Contribution c) {
        Map<Long, Long> out = new LinkedHashMap<>();
        long receitas = 0;
        for (Entry e : entries) {
            if (e.kind() == Kind.RECEITA && !e.loan()) receitas = Math.addExact(receitas, e.amountCents());
            if (e.kind() == kind) out.merge(e.categoryId(), e.amountCents(), Math::addExact);
        }
        if (kind == Kind.DESPESA) {
            long contrib = contribution(receitas, c);
            if (contrib > 0) {
                long key = c.categoryId() != null ? c.categoryId() : CONTRIBUTION_KEY;
                out.merge(key, contrib, Math::addExact);
            }
        }
        return out;
    }

    /** n parcelas de valor fixo (quando a pessoa informa o valor de cada parcela). */
    public static long[] fixedInstallments(long installmentCents, int count) {
        if (count < 1 || count > 240) throw new IllegalArgumentException("Parcelas devem ser entre 1 e 240");
        if (installmentCents <= 0) throw new IllegalArgumentException("Informe o valor da parcela");
        Math.multiplyExact(installmentCents, count);
        long[] parts = new long[count];
        Arrays.fill(parts, installmentCents);
        return parts;
    }

    /** Divide um total em n parcelas iguais; a última absorve a diferença de centavos. */
    public static long[] splitInstallments(long totalCents, int count) {
        if (count < 2 || count > 120) throw new IllegalArgumentException("Parcelas devem ser entre 2 e 120");
        if (totalCents < count) throw new IllegalArgumentException("Valor menor que o número de parcelas");
        long base = totalCents / count;
        long[] parts = new long[count];
        Arrays.fill(parts, base);
        parts[count - 1] = totalCents - base * (count - 1);
        return parts;
    }

    /** Média mensal considerando só os meses com valor. */
    public static long averageOfNonZero(long[] values) {
        long sum = 0;
        int n = 0;
        for (long v : values) {
            if (v != 0) {
                sum = Math.addExact(sum, v);
                n++;
            }
        }
        if (n == 0) return 0;
        return BigDecimal.valueOf(sum).divide(BigDecimal.valueOf(n), 0, RoundingMode.HALF_UP).longValueExact();
    }

    /** As maiores categorias (até {@code max}) e o restante somado em "Outros" (categoryId nulo). */
    public static List<Slice> topWithOthers(Map<Long, Long> amounts, int max) {
        List<Map.Entry<Long, Long>> sorted = amounts.entrySet().stream()
                .filter(e -> e.getValue() > 0)
                .sorted(Map.Entry.<Long, Long>comparingByValue().reversed())
                .toList();
        List<Slice> out = new ArrayList<>();
        long others = 0;
        for (int i = 0; i < sorted.size(); i++) {
            var e = sorted.get(i);
            if (i < max) out.add(new Slice(e.getKey(), e.getValue()));
            else others = Math.addExact(others, e.getValue());
        }
        if (others > 0) out.add(new Slice(null, others));
        return out;
    }

    /** Variação percentual em pontos-base (10000 = 100%). Nulo quando não há base de comparação. */
    public static Integer changeBps(long current, long previous) {
        if (previous == 0) return null;
        return BigDecimal.valueOf(current - previous)
                .multiply(BigDecimal.valueOf(10_000))
                .divide(BigDecimal.valueOf(Math.abs(previous)), 0, RoundingMode.HALF_UP)
                .intValueExact();
    }
}
