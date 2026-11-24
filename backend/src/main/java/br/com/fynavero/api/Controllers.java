package br.com.fynavero.api;

import br.com.fynavero.api.Dto.*;
import br.com.fynavero.domain.Entities.Profile;
import br.com.fynavero.domain.Entities.Recurrence;
import br.com.fynavero.domain.Enums.Kind;
import br.com.fynavero.domain.Enums.PaymentMethod;
import br.com.fynavero.domain.Enums.Scope;
import br.com.fynavero.security.AuthUser;
import br.com.fynavero.security.LoginThrottle;
import br.com.fynavero.security.SecurityConfig;
import br.com.fynavero.service.*;
import br.com.fynavero.support.Support.AppProperties;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import jakarta.validation.Valid;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.client.registration.ClientRegistrationRepository;
import org.springframework.security.web.context.SecurityContextRepository;
import org.springframework.security.web.csrf.CsrfTokenRepository;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.YearMonth;
import java.util.List;
import java.util.Map;

import static br.com.fynavero.support.Support.parseMonth;

public final class Controllers {
    private Controllers() {}

    private static ResponseStatusException unauthorized() {
        return new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Entre na sua conta para continuar.");
    }

    // ------------------------------------------------------------------ autenticação

    @RestController
    @RequestMapping("/api/auth")
    public static class AuthController {
        private final AccountService accounts;
        private final AuthenticationManager authManager;
        private final SecurityContextRepository contextRepo;
        private final CsrfTokenRepository csrfRepo;
        private final LoginThrottle throttle;
        private final ObjectProvider<ClientRegistrationRepository> oauth;

        public AuthController(AccountService accounts, AuthenticationManager authManager,
                              SecurityContextRepository contextRepo, CsrfTokenRepository csrfRepo,
                              LoginThrottle throttle, ObjectProvider<ClientRegistrationRepository> oauth) {
            this.accounts = accounts;
            this.authManager = authManager;
            this.contextRepo = contextRepo;
            this.csrfRepo = csrfRepo;
            this.throttle = throttle;
            this.oauth = oauth;
        }

        private Me signIn(Profile p, HttpServletRequest req, HttpServletResponse res) throws IOException {
            SecurityConfig.signIn(AuthUser.of(p), req, res, contextRepo);
            csrfRepo.saveToken(null, req, res); // novo token CSRF após o login
            return accounts.me(p);
        }

        @GetMapping("/csrf")
        public ResponseEntity<Void> csrf() {
            return ResponseEntity.noContent().build();
        }

        @GetMapping("/providers")
        public Providers providers() {
            return new Providers(oauth.getIfAvailable() != null, accounts.magicLinkEnabled());
        }

        @GetMapping("/me")
        public Me me(@AuthenticationPrincipal AuthUser user) {
            if (user == null) throw unauthorized();
            try {
                return accounts.me(accounts.require(user.id()));
            } catch (ResponseStatusException e) {
                throw unauthorized();
            }
        }

        @PostMapping("/register")
        public Me register(@Valid @RequestBody RegisterReq body, HttpServletRequest req, HttpServletResponse res) throws IOException {
            return signIn(accounts.register(body), req, res);
        }

        @PostMapping("/login")
        public Me login(@Valid @RequestBody LoginReq body, HttpServletRequest req, HttpServletResponse res) throws IOException {
            String email = body.email().strip();
            if (throttle.isBlocked(email)) {
                throw new ResponseStatusException(HttpStatus.TOO_MANY_REQUESTS, "Muitas tentativas. Tente de novo em 15 minutos.");
            }
            Authentication auth;
            try {
                auth = authManager.authenticate(UsernamePasswordAuthenticationToken.unauthenticated(email, body.password()));
            } catch (AuthenticationException e) {
                throttle.failure(email);
                throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "E-mail ou senha incorretos.");
            }
            throttle.success(email);
            return signIn(accounts.require(((AuthUser) auth.getPrincipal()).id()), req, res);
        }

        @PostMapping("/magic-link")
        public ResponseEntity<Void> magicLink(@Valid @RequestBody EmailReq body) {
            accounts.requestMagicLink(body.email());
            return ResponseEntity.noContent().build();
        }

        @PostMapping("/magic-link/consume")
        public Me consume(@Valid @RequestBody TokenReq body, HttpServletRequest req, HttpServletResponse res) throws IOException {
            return signIn(accounts.consumeMagicLink(body.token()), req, res);
        }
    }

    // ------------------------------------------------------------------ perfil e onboarding

    @RestController
    @RequestMapping("/api")
    public static class ProfileController {
        private final AccountService accounts;

        public ProfileController(AccountService accounts) {
            this.accounts = accounts;
        }

        @GetMapping("/catalog")
        public List<CatalogDto> catalog() {
            return accounts.catalog();
        }

        @PostMapping("/onboarding")
        public Me onboarding(@AuthenticationPrincipal AuthUser u, @Valid @RequestBody OnboardingReq body) {
            return accounts.me(accounts.completeOnboarding(u.id(), body));
        }

        @PutMapping("/profile")
        public Me update(@AuthenticationPrincipal AuthUser u, @Valid @RequestBody ProfileReq body) {
            return accounts.me(accounts.updateProfile(u.id(), body));
        }

        @PutMapping("/profile/contribution")
        public Me contribution(@AuthenticationPrincipal AuthUser u, @Valid @RequestBody ContributionDto body) {
            return accounts.me(accounts.updateContribution(u.id(), body));
        }

        @PutMapping("/profile/password")
        public ResponseEntity<Void> password(@AuthenticationPrincipal AuthUser u, @Valid @RequestBody PasswordReq body) {
            accounts.changePassword(u.id(), body);
            return ResponseEntity.noContent().build();
        }

        @GetMapping("/profile/export")
        public ResponseEntity<Map<String, Object>> export(@AuthenticationPrincipal AuthUser u) {
            return ResponseEntity.ok()
                    .header(HttpHeaders.CONTENT_DISPOSITION, ContentDisposition.attachment()
                            .filename("fynavero-meus-dados.json").build().toString())
                    .body(accounts.exportData(u.id()));
        }

        @DeleteMapping("/profile")
        public ResponseEntity<Void> delete(@AuthenticationPrincipal AuthUser u, @Valid @RequestBody DeleteAccountReq body,
                                           HttpServletRequest req) {
            accounts.deleteAccount(u.id(), body);
            HttpSession s = req.getSession(false);
            if (s != null) s.invalidate();
            SecurityContextHolder.clearContext();
            return ResponseEntity.noContent().build();
        }

        @GetMapping("/income-sources")
        public List<IncomeSourceDto> incomes(@AuthenticationPrincipal AuthUser u) {
            return accounts.incomeSources(u.id());
        }

        @PostMapping("/income-sources")
        public IncomeSourceDto createIncome(@AuthenticationPrincipal AuthUser u, @Valid @RequestBody IncomeSourceReq body) {
            return accounts.createIncomeSource(u.id(), body);
        }

        @PutMapping("/income-sources/{id}")
        public IncomeSourceDto updateIncome(@AuthenticationPrincipal AuthUser u, @PathVariable Long id,
                                            @Valid @RequestBody IncomeSourceReq body) {
            return accounts.updateIncomeSource(u.id(), id, body);
        }

        @DeleteMapping("/income-sources/{id}")
        public ResponseEntity<Void> deleteIncome(@AuthenticationPrincipal AuthUser u, @PathVariable Long id) {
            accounts.deleteIncomeSource(u.id(), id);
            return ResponseEntity.noContent().build();
        }
    }

    // ------------------------------------------------------------------ categorias e recorrências

    @RestController
    @RequestMapping("/api")
    public static class CategoryController {
        private final CategoryService categories;
        private final LedgerService ledger;

        public CategoryController(CategoryService categories, LedgerService ledger) {
            this.categories = categories;
            this.ledger = ledger;
        }

        @GetMapping("/categories")
        public List<CategoryDto> list(@AuthenticationPrincipal AuthUser u) {
            return categories.list(u.id()).stream().map(CategoryDto::of).toList();
        }

        @PostMapping("/categories")
        public CategoryDto create(@AuthenticationPrincipal AuthUser u, @Valid @RequestBody CategoryReq body) {
            return CategoryDto.of(categories.create(u.id(), body.name(), body.kind(), body.group(), body.icon(), body.goalCents()));
        }

        @PutMapping("/categories/{id}")
        public CategoryDto update(@AuthenticationPrincipal AuthUser u, @PathVariable Long id, @Valid @RequestBody CategoryReq body) {
            return CategoryDto.of(categories.update(u.id(), id, body));
        }

        @PutMapping("/categories/order")
        public ResponseEntity<Void> reorder(@AuthenticationPrincipal AuthUser u, @Valid @RequestBody ReorderReq body) {
            categories.reorder(u.id(), body.kind(), body.ids());
            return ResponseEntity.noContent().build();
        }

        private static RecurrenceDto dto(Recurrence r) {
            return new RecurrenceDto(r.id, r.kind, r.categoryId, r.description, r.amountCents, r.dayOfMonth,
                    r.paymentMethod, r.startDate, r.endDate, r.fixedBill);
        }

        @GetMapping("/recurrences")
        public List<RecurrenceDto> recurrences(@AuthenticationPrincipal AuthUser u) {
            return ledger.recurrences(u.id()).stream().map(CategoryController::dto).toList();
        }

        @PostMapping("/recurrences")
        public RecurrenceDto createRecurrence(@AuthenticationPrincipal AuthUser u, @Valid @RequestBody RecurrenceReq body) {
            return dto(ledger.createFixedBill(u.id(), body));
        }

        @PutMapping("/recurrences/{id}")
        public RecurrenceDto updateRecurrence(@AuthenticationPrincipal AuthUser u, @PathVariable Long id,
                                              @Valid @RequestBody RecurrenceReq body) {
            return dto(ledger.updateRecurrence(u.id(), id, body));
        }

        @DeleteMapping("/recurrences/{id}")
        public ResponseEntity<Void> endRecurrence(@AuthenticationPrincipal AuthUser u, @PathVariable Long id) {
            ledger.endRecurrence(u.id(), id);
            return ResponseEntity.noContent().build();
        }
    }

    // ------------------------------------------------------------------ lançamentos

    @RestController
    @RequestMapping("/api/transactions")
    public static class TransactionController {
        private final LedgerService ledger;
        private final CategoryService categories;
        private final AppProperties props;

        public TransactionController(LedgerService ledger, CategoryService categories, AppProperties props) {
            this.ledger = ledger;
            this.categories = categories;
            this.props = props;
        }

        @GetMapping
        public List<TxDto> list(@AuthenticationPrincipal AuthUser u, @RequestParam(required = false) String month,
                                @RequestParam(required = false) String q, @RequestParam(required = false) Long categoryId,
                                @RequestParam(required = false) Kind kind,
                                @RequestParam(required = false) PaymentMethod paymentMethod) {
            YearMonth m = parseMonth(month, props.zoneId());
            var list = ledger.filter(ledger.month(u.id(), m), q, categoryId, kind, paymentMethod, categories.map(u.id()));
            return list.stream().map(TxDto::of).toList();
        }

        @PostMapping
        public List<TxDto> create(@AuthenticationPrincipal AuthUser u, @Valid @RequestBody TxReq body) {
            return ledger.create(u.id(), body).stream().map(TxDto::of).toList();
        }

        @PutMapping("/{id}")
        public TxDto update(@AuthenticationPrincipal AuthUser u, @PathVariable Long id,
                            @RequestParam(defaultValue = "ONE") Scope scope, @Valid @RequestBody TxUpdateReq body) {
            return TxDto.of(ledger.update(u.id(), id, scope, body));
        }

        @DeleteMapping("/{id}")
        public DeletedDto delete(@AuthenticationPrincipal AuthUser u, @PathVariable Long id,
                                 @RequestParam(defaultValue = "ONE") Scope scope) {
            return new DeletedDto(ledger.delete(u.id(), id, scope));
        }

        @PostMapping("/restore")
        public ResponseEntity<Void> restore(@AuthenticationPrincipal AuthUser u, @Valid @RequestBody IdsReq body) {
            ledger.restore(u.id(), body.ids());
            return ResponseEntity.noContent().build();
        }

        @PostMapping("/{id}/duplicate")
        public TxDto duplicate(@AuthenticationPrincipal AuthUser u, @PathVariable Long id) {
            return TxDto.of(ledger.duplicate(u.id(), id));
        }

        @PostMapping("/{id}/mark-paid")
        public TxDto markPaid(@AuthenticationPrincipal AuthUser u, @PathVariable Long id) {
            return TxDto.of(ledger.markPaid(u.id(), id));
        }

        @PostMapping("/{id}/installments")
        public List<TxDto> convertToInstallments(@AuthenticationPrincipal AuthUser u, @PathVariable Long id,
                                                 @Valid @RequestBody InstallmentConvertReq body) {
            return ledger.convertToInstallments(u.id(), id, body).stream().map(TxDto::of).toList();
        }
    }

    @RestController
    @RequestMapping("/api/installment-plans")
    public static class InstallmentPlanController {
        private final LedgerService ledger;

        public InstallmentPlanController(LedgerService ledger) {
            this.ledger = ledger;
        }

        @PutMapping("/{id}")
        public List<TxDto> resize(@AuthenticationPrincipal AuthUser u, @PathVariable Long id,
                                  @Valid @RequestBody PlanResizeReq body) {
            return ledger.resizePlan(u.id(), id, body.count()).stream().map(TxDto::of).toList();
        }
    }

    // ------------------------------------------------------------------ telas de leitura e relatórios

    @RestController
    @RequestMapping("/api")
    public static class InsightsController {
        private final InsightsService insights;
        private final ExportService export;
        private final AppProperties props;

        public InsightsController(InsightsService insights, ExportService export, AppProperties props) {
            this.insights = insights;
            this.export = export;
            this.props = props;
        }

        @GetMapping("/dashboard")
        public DashboardDto dashboard(@AuthenticationPrincipal AuthUser u, @RequestParam(required = false) String month) {
            return insights.dashboard(u.id(), parseMonth(month, props.zoneId()));
        }

        @GetMapping("/installments")
        public InstallmentsDto installments(@AuthenticationPrincipal AuthUser u, @RequestParam(required = false) String month) {
            return insights.installments(u.id(), parseMonth(month, props.zoneId()));
        }

        @GetMapping("/investments")
        public InvestmentsDto investments(@AuthenticationPrincipal AuthUser u, @RequestParam(required = false) String month) {
            return insights.investments(u.id(), parseMonth(month, props.zoneId()));
        }

        @GetMapping("/reports/annual")
        public AnnualReport annual(@AuthenticationPrincipal AuthUser u, @RequestParam int year) {
            return insights.annual(u.id(), year);
        }

        @GetMapping("/reports/compare")
        public CompareReport compare(@AuthenticationPrincipal AuthUser u, @RequestParam(required = false) String month) {
            return insights.compare(u.id(), parseMonth(month, props.zoneId()));
        }

        @GetMapping("/reports/annual/export")
        public ResponseEntity<byte[]> exportAnnual(@AuthenticationPrincipal AuthUser u, @RequestParam int year,
                                                   @RequestParam(defaultValue = "xlsx") String format) {
            AnnualReport r = insights.annual(u.id(), year);
            boolean pdf = "pdf".equalsIgnoreCase(format);
            byte[] body = pdf ? export.pdf(r) : export.xlsx(r);
            String name = "fynavero-relatorio-" + year + (pdf ? ".pdf" : ".xlsx");
            return ResponseEntity.ok()
                    .contentType(pdf ? MediaType.APPLICATION_PDF
                            : MediaType.parseMediaType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"))
                    .header(HttpHeaders.CONTENT_DISPOSITION,
                            ContentDisposition.attachment().filename(name, StandardCharsets.UTF_8).build().toString())
                    .body(body);
        }
    }

    // ------------------------------------------------------------------ importação

    @RestController
    @RequestMapping("/api/imports")
    public static class ImportController {
        private final ImportService imports;

        public ImportController(ImportService imports) {
            this.imports = imports;
        }

        @PostMapping("/check")
        public DuplicateCheckDto check(@AuthenticationPrincipal AuthUser u, @Valid @RequestBody DuplicateCheckReq body) {
            return imports.checkDuplicates(u.id(), body.rows());
        }

        @PostMapping
        public ImportBatchDto importRows(@AuthenticationPrincipal AuthUser u, @Valid @RequestBody ImportReq body) {
            return imports.importRows(u.id(), body);
        }

        @GetMapping
        public List<ImportBatchDto> list(@AuthenticationPrincipal AuthUser u) {
            return imports.list(u.id());
        }

        @DeleteMapping("/{id}")
        public ImportBatchDto undo(@AuthenticationPrincipal AuthUser u, @PathVariable Long id) {
            return imports.undo(u.id(), id);
        }
    }
}
