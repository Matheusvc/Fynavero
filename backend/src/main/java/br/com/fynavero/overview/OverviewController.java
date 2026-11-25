package br.com.fynavero.overview;

import br.com.fynavero.overview.OverviewDtos.AnnualReport;
import br.com.fynavero.overview.OverviewDtos.CompareReport;
import br.com.fynavero.overview.OverviewDtos.DashboardDto;
import br.com.fynavero.overview.OverviewDtos.InstallmentsDto;
import br.com.fynavero.overview.OverviewDtos.InvestmentsDto;
import br.com.fynavero.security.AuthUser;
import br.com.fynavero.shared.AppProperties;
import java.nio.charset.StandardCharsets;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import static br.com.fynavero.shared.Formats.parseMonth;

/** API das telas de leitura: Início, Parcelas e Dívidas, Investimentos e Relatórios. */
@RestController
public class OverviewController {
    private static final MediaType XLSX =
            MediaType.parseMediaType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet");

    private final DashboardService dashboard;
    private final ScreensService screens;
    private final AppProperties props;
    private final ReportService reports;
    private final ReportExporter exporter;

    public OverviewController(DashboardService dashboard, ScreensService screens, AppProperties props, ReportService reports, ReportExporter exporter) {
        this.dashboard = dashboard;
        this.screens = screens;
        this.props = props;
        this.reports = reports;
        this.exporter = exporter;
    }

    // ---------------------------------------------------------------- /api
    @GetMapping("/api/dashboard")
    public DashboardDto dashboard(@AuthenticationPrincipal AuthUser u, @RequestParam(required = false) String month) {
        return dashboard.dashboard(u.id(), parseMonth(month, props.zoneId()));
    }

    @GetMapping("/api/installments")
    public InstallmentsDto installments(@AuthenticationPrincipal AuthUser u, @RequestParam(required = false) String month) {
        return screens.installments(u.id(), parseMonth(month, props.zoneId()));
    }

    @GetMapping("/api/investments")
    public InvestmentsDto investments(@AuthenticationPrincipal AuthUser u, @RequestParam(required = false) String month) {
        return screens.investments(u.id(), parseMonth(month, props.zoneId()));
    }

    // ---------------------------------------------------------------- /api/reports
    @GetMapping("/api/reports/annual")
    public AnnualReport annual(@AuthenticationPrincipal AuthUser u, @RequestParam int year) {
        return reports.annual(u.id(), year);
    }

    @GetMapping("/api/reports/compare")
    public CompareReport compare(@AuthenticationPrincipal AuthUser u, @RequestParam(required = false) String month) {
        return reports.compare(u.id(), parseMonth(month, props.zoneId()));
    }

    /** Download do relatório anual: format = xlsx (padrão) ou pdf. */
    @GetMapping("/api/reports/annual/export")
    public ResponseEntity<byte[]> export(@AuthenticationPrincipal AuthUser u, @RequestParam int year,
                                         @RequestParam(defaultValue = "xlsx") String format) {
        AnnualReport r = reports.annual(u.id(), year);
        boolean pdf = "pdf".equalsIgnoreCase(format);
        String name = "fynavero-relatorio-" + year + (pdf ? ".pdf" : ".xlsx");
        return ResponseEntity.ok()
                .contentType(pdf ? MediaType.APPLICATION_PDF : XLSX)
                .header(HttpHeaders.CONTENT_DISPOSITION,
                        ContentDisposition.attachment().filename(name, StandardCharsets.UTF_8).build().toString())
                .body(pdf ? exporter.pdf(r) : exporter.xlsx(r));
    }
}
