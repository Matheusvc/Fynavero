package br.com.fynavero.account;

import br.com.fynavero.account.AccountDtos.ContributionDto;
import br.com.fynavero.account.AccountDtos.PasswordReq;
import br.com.fynavero.account.AccountDtos.ProfileReq;
import br.com.fynavero.account.AccountDtos.RegisterReq;
import br.com.fynavero.calc.FinanceCalculator.Contribution;
import br.com.fynavero.category.Category;
import br.com.fynavero.category.CategoryService;
import br.com.fynavero.domain.Kind;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.Locale;

import static br.com.fynavero.common.Errors.badRequest;
import static br.com.fynavero.common.Errors.notFound;

/** Cadastro, perfil, senha e configuração da contribuição automática. */
@Service
public class AccountService {
    private final ProfileRepository profiles;
    private final CategoryService categories;
    private final PasswordEncoder encoder;

    public AccountService(ProfileRepository profiles, CategoryService categories, PasswordEncoder encoder) {
        this.profiles = profiles;
        this.categories = categories;
        this.encoder = encoder;
    }

    public Profile require(Long userId) {
        return profiles.findById(userId).orElseThrow(() -> notFound());
    }

    /** Configuração da contribuição usada pelo módulo de cálculo. */
    public Contribution contributionOf(Long userId) {
        Profile p = require(userId);
        return new Contribution(p.contributionEnabled, p.contributionBps, p.contributionCategoryId);
    }

    @Transactional
    public Profile register(RegisterReq req) {
        String email = req.email().strip().toLowerCase(Locale.ROOT);
        if (profiles.findByEmailIgnoreCase(email).isPresent()) {
            throw badRequest("Já existe uma conta com esse e-mail. Tente entrar.");
        }
        Profile p = new Profile();
        p.email = email;
        p.name = req.name().strip();
        p.passwordHash = encoder.encode(req.password());
        p.termsAcceptedAt = Instant.now();
        p.createdAt = Instant.now();
        return profiles.save(p);
    }

    @Transactional
    public Profile updateProfile(Long userId, ProfileReq req) {
        Profile p = require(userId);
        p.name = req.name().strip();
        p.setObjectives(req.objectives());
        return p;
    }

    @Transactional
    public Profile updateContribution(Long userId, ContributionDto req) {
        Profile p = require(userId);
        if (req.categoryId() != null) {
            Category c = categories.require(userId, req.categoryId());
            if (c.kind != Kind.DESPESA) throw badRequest("Escolha uma categoria de despesa.");
        }
        p.contributionEnabled = req.enabled();
        p.contributionBps = req.bps();
        p.contributionCategoryId = req.categoryId();
        return p;
    }

    @Transactional
    public void changePassword(Long userId, PasswordReq req) {
        Profile p = require(userId);
        if (p.passwordHash != null && (req.current() == null || !encoder.matches(req.current(), p.passwordHash))) {
            throw badRequest("A senha atual está incorreta.");
        }
        p.passwordHash = encoder.encode(req.next());
    }
}
