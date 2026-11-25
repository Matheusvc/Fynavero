package br.com.fynavero.overview;

import java.util.List;

/** Contratos dos relatórios. months tem 12 posições (jan..dez); average considera só os meses com valor. */
public final class ReportDtos {
    private ReportDtos() {}

    public record ReportRow(Long categoryId, String label, long[] months, long total, long average) {}

    public record ReportGroup(String group, List<ReportRow> rows, ReportRow subtotal) {}

    public record AnnualReport(int year, List<String> monthLabels, List<ReportRow> receitas,
                               List<ReportRow> emprestimos,
                               List<ReportGroup> despesas, ReportRow totalDespesas,
                               List<ReportRow> investimentos, ReportRow totalInvestido,
                               List<ReportRow> resultado) {}

    public record CompareRow(Long categoryId, String label, long current, long previous, long average) {}

    public record CompareReport(String month, String previousMonth, List<CompareRow> totals, List<CompareRow> categories) {}
}
