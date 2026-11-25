package br.com.fynavero.overview;

import br.com.fynavero.shared.AppProperties;
import br.com.fynavero.overview.OverviewDtos.DashboardDto;
import br.com.fynavero.overview.OverviewDtos.InstallmentsDto;
import br.com.fynavero.overview.OverviewDtos.InvestmentsDto;
import br.com.fynavero.security.AuthUser;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import static br.com.fynavero.shared.Formats.parseMonth;

@RestController
@RequestMapping("/api")
public class OverviewController {
    private final DashboardService dashboard;
    private final InstallmentOverviewService installments;
    private final InvestmentOverviewService investments;
    private final AppProperties props;

    public OverviewController(DashboardService dashboard, InstallmentOverviewService installments,
                              InvestmentOverviewService investments, AppProperties props) {
        this.dashboard = dashboard;
        this.installments = installments;
        this.investments = investments;
        this.props = props;
    }

    @GetMapping("/dashboard")
    public DashboardDto dashboard(@AuthenticationPrincipal AuthUser u, @RequestParam(required = false) String month) {
        return dashboard.dashboard(u.id(), parseMonth(month, props.zoneId()));
    }

    @GetMapping("/installments")
    public InstallmentsDto installments(@AuthenticationPrincipal AuthUser u, @RequestParam(required = false) String month) {
        return installments.overview(u.id(), parseMonth(month, props.zoneId()));
    }

    @GetMapping("/investments")
    public InvestmentsDto investments(@AuthenticationPrincipal AuthUser u, @RequestParam(required = false) String month) {
        return investments.overview(u.id(), parseMonth(month, props.zoneId()));
    }
}
