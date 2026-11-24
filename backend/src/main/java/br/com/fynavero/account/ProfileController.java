package br.com.fynavero.account;

import br.com.fynavero.account.AccountDtos.ContributionDto;
import br.com.fynavero.account.AccountDtos.DeleteAccountReq;
import br.com.fynavero.account.AccountDtos.Me;
import br.com.fynavero.account.AccountDtos.PasswordReq;
import br.com.fynavero.account.AccountDtos.ProfileReq;
import br.com.fynavero.security.AuthUser;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpSession;
import jakarta.validation.Valid;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
@RequestMapping("/api/profile")
public class ProfileController {
    private final AccountService accounts;
    private final PrivacyService privacy;

    public ProfileController(AccountService accounts, PrivacyService privacy) {
        this.accounts = accounts;
        this.privacy = privacy;
    }

    @PutMapping
    public Me update(@AuthenticationPrincipal AuthUser u, @Valid @RequestBody ProfileReq body) {
        return Me.of(accounts.updateProfile(u.id(), body));
    }

    @PutMapping("/contribution")
    public Me contribution(@AuthenticationPrincipal AuthUser u, @Valid @RequestBody ContributionDto body) {
        return Me.of(accounts.updateContribution(u.id(), body));
    }

    @PutMapping("/password")
    public ResponseEntity<Void> password(@AuthenticationPrincipal AuthUser u, @Valid @RequestBody PasswordReq body) {
        accounts.changePassword(u.id(), body);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/export")
    public ResponseEntity<Map<String, Object>> export(@AuthenticationPrincipal AuthUser u) {
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION,
                        ContentDisposition.attachment().filename("fynavero-meus-dados.json").build().toString())
                .body(privacy.exportData(u.id()));
    }

    @DeleteMapping
    public ResponseEntity<Void> delete(@AuthenticationPrincipal AuthUser u, @Valid @RequestBody DeleteAccountReq body,
                                       HttpServletRequest req) {
        privacy.deleteAccount(u.id(), body);
        HttpSession session = req.getSession(false);
        if (session != null) session.invalidate();
        SecurityContextHolder.clearContext();
        return ResponseEntity.noContent().build();
    }
}
