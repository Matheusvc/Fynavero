package br.com.fynavero.report;

import br.com.fynavero.config.AppProperties;
import br.com.fynavero.report.ReportDtos.AnnualReport;
import br.com.fynavero.report.ReportDtos.CompareReport;
import br.com.fynavero.security.AuthUser;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.nio.charset.StandardCharsets;

import static br.com.fynavero.common.Formats.parseMonth;

@RestController
@RequestMapping("/api/reports")
public class ReportController {
    private static final MediaType XLSX =
            MediaType.parseMediaType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet");

    private final ReportService reports;
    private final ReportExporter exporter;
    private final AppProperties props;

    public ReportController(ReportService reports, ReportExporter exporter, AppProperties props) {
        this.reports = reports;
        this.exporter = exporter;
        this.props = props;
    }

    @GetMapping("/annual")
    public AnnualReport annual(@AuthenticationPrincipal AuthUser u, @RequestParam int year) {
        return reports.annual(u.id(), year);
    }

    @GetMapping("/compare")
    public CompareReport compare(@AuthenticationPrincipal AuthUser u, @RequestParam(required = false) String month) {
        return reports.compare(u.id(), parseMonth(month, props.zoneId()));
    }

    /** Download do relatório anual: format = xlsx (padrão) ou pdf. */
    @GetMapping("/annual/export")
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
