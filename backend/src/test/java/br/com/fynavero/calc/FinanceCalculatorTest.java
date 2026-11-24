package br.com.fynavero.calc;

import br.com.fynavero.calc.FinanceCalculator.Contribution;
import br.com.fynavero.calc.FinanceCalculator.Entry;
import br.com.fynavero.calc.FinanceCalculator.MonthTotals;
import br.com.fynavero.domain.Kind;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class FinanceCalculatorTest {

    private static Entry receita(long c) { return new Entry(Kind.RECEITA, c, 1L); }
    private static Entry despesa(long c, long cat) { return new Entry(Kind.DESPESA, c, cat); }
    private static Entry investimento(long c) { return new Entry(Kind.INVESTIMENTO, c, 9L); }

    @Test
    void contribuicaoDe10PorCentoSobreReceitasDe3575() {
        // Critério 5: receitas de R$ 3.575 e contribuição de 10% → R$ 357,50
        var c = new Contribution(true, 1000, 20L);
        assertThat(FinanceCalculator.contribution(357_500, c)).isEqualTo(35_750);
        MonthTotals t = FinanceCalculator.totals(List.of(receita(357_500)), c);
        assertThat(t.contribuicao()).isEqualTo(35_750);
        assertThat(t.despesas()).isEqualTo(35_750);
    }

    @Test
    void contribuicaoDesativadaNaoEntra() {
        var t = FinanceCalculator.totals(List.of(receita(357_500)), new Contribution(false, 1000, 20L));
        assertThat(t.contribuicao()).isZero();
        assertThat(t.sobra()).isEqualTo(357_500);
    }

    @Test
    void contribuicaoArredondaMeioCentavoParaCima() {
        // 10% de R$ 0,05 = 0,5 centavo → 1 centavo
        assertThat(FinanceCalculator.contribution(5, new Contribution(true, 1000, null))).isEqualTo(1);
        // 7,5% de R$ 1.000,01 = 7500,075 centavos → 7500
        assertThat(FinanceCalculator.contribution(100_001, new Contribution(true, 750, null))).isEqualTo(7_500);
    }

    @Test
    void investimentoReduzSaldoSemAlterarDespesas() {
        // Critério 6: investimento de R$ 200 reduz o saldo em R$ 200 e não muda o total de despesas
        var base = List.of(receita(500_000), despesa(120_000, 2));
        var semInvest = FinanceCalculator.totals(base, Contribution.NONE);
        var comInvest = FinanceCalculator.totals(
                List.of(receita(500_000), despesa(120_000, 2), investimento(20_000)), Contribution.NONE);

        assertThat(comInvest.despesas()).isEqualTo(semInvest.despesas());
        assertThat(comInvest.sobra()).isEqualTo(semInvest.sobra());
        assertThat(semInvest.saldo() - comInvest.saldo()).isEqualTo(20_000);
        assertThat(comInvest.investido()).isEqualTo(20_000);
    }

    @Test
    void formulaCompletaDoMes() {
        var t = FinanceCalculator.totals(List.of(
                receita(357_500), despesa(150_000, 2), despesa(42_090, 3), investimento(30_000)),
                new Contribution(true, 1000, 20L));
        assertThat(t.receitas()).isEqualTo(357_500);
        assertThat(t.despesasLancadas()).isEqualTo(192_090);
        assertThat(t.despesas()).isEqualTo(192_090 + 35_750);
        assertThat(t.sobra()).isEqualTo(357_500 - 227_840);
        assertThat(t.saldo()).isEqualTo(129_660 - 30_000);
    }

    @Test
    void emprestimoEntraNoSaldoMasNaoNaContribuicao() {
        var t = FinanceCalculator.totals(List.of(receita(357_500), new Entry(Kind.RECEITA, 500_000, 7L, true)),
                new Contribution(true, 1000, 20L));
        assertThat(t.receitas()).isEqualTo(857_500);
        assertThat(t.emprestimos()).isEqualTo(500_000);
        assertThat(t.contribuicao()).isEqualTo(35_750);
        assertThat(t.saldo()).isEqualTo(857_500 - 35_750);
    }

    @Test
    void parcelasDeValorFixo() {
        assertThat(FinanceCalculator.fixedInstallments(55_000, 12)).hasSize(12).containsOnly(55_000);
        assertThatThrownBy(() -> FinanceCalculator.fixedInstallments(0, 3)).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void saldoNegativo() {
        var t = FinanceCalculator.totals(List.of(receita(100_000), despesa(150_000, 2)), Contribution.NONE);
        assertThat(t.sobra()).isEqualTo(-50_000);
        assertThat(t.saldo()).isEqualTo(-50_000);
    }

    @Test
    void compraDe4900Em10VezesGera10ParcelasDe490() {
        // Critério 4
        long[] parts = FinanceCalculator.splitInstallments(490_000, 10);
        assertThat(parts).hasSize(10).containsOnly(49_000);
    }

    @Test
    void ultimaParcelaAbsorveCentavos() {
        long[] parts = FinanceCalculator.splitInstallments(100_000, 3);
        assertThat(parts).containsExactly(33_333, 33_333, 33_334);
        assertThat(parts[0] + parts[1] + parts[2]).isEqualTo(100_000);
    }

    @Test
    void parcelasInvalidas() {
        assertThatThrownBy(() -> FinanceCalculator.splitInstallments(1000, 1)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> FinanceCalculator.splitInstallments(5, 10)).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void mediaConsideraSoMesesComValor() {
        long[] meses = {10_000, 0, 20_000, 0, 0, 0, 0, 0, 0, 0, 0, 30_000};
        assertThat(FinanceCalculator.averageOfNonZero(meses)).isEqualTo(20_000);
        assertThat(FinanceCalculator.averageOfNonZero(new long[12])).isZero();
    }

    @Test
    void contribuicaoEntraNaCategoriaConfigurada() {
        var map = FinanceCalculator.byCategory(List.of(receita(100_000), despesa(5_000, 2)), Kind.DESPESA,
                new Contribution(true, 1000, 20L));
        assertThat(map).containsEntry(2L, 5_000L).containsEntry(20L, 10_000L);
    }

    @Test
    void seisMaioresMaisOutros() {
        Map<Long, Long> m = Map.of(1L, 100L, 2L, 900L, 3L, 300L, 4L, 400L, 5L, 500L, 6L, 600L, 7L, 700L, 8L, 200L);
        var slices = FinanceCalculator.topWithOthers(m, 6);
        assertThat(slices).hasSize(7);
        assertThat(slices.get(0).categoryId()).isEqualTo(2L);
        assertThat(slices.get(6).isOthers()).isTrue();
        assertThat(slices.get(6).amountCents()).isEqualTo(300L);
    }

    @Test
    void variacaoPercentual() {
        assertThat(FinanceCalculator.changeBps(12_000, 10_000)).isEqualTo(2_000);
        assertThat(FinanceCalculator.changeBps(8_000, 10_000)).isEqualTo(-2_000);
        assertThat(FinanceCalculator.changeBps(5_000, 0)).isNull();
    }
}
