package br.com.fynavero.account;

import br.com.fynavero.domain.Objective;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.stream.Collectors;

/** Pessoa usuária: login, preferências e consentimento (LGPD). */
@Entity
@Table(name = "profiles")
public class Profile {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) public Long id;
    public String email;
    @Column(name = "password_hash") public String passwordHash;
    public String name;
    /** Objetivos separados por vírgula (ex.: "ORGANIZAR,INVESTIR"). Use {@link #objectives()}. */
    @Column(name = "objectives") public String objectivesCsv;
    @Column(name = "onboarding_done") public boolean onboardingDone;
    @Column(name = "contribution_enabled") public boolean contributionEnabled;
    /** Percentual da contribuição em pontos-base: 1000 = 10%. */
    @Column(name = "contribution_bps") public int contributionBps = 1000;
    @Column(name = "contribution_category_id") public Long contributionCategoryId;
    /** Avisos por e-mail: contas que vencem amanhã, limites de categoria e resumo do mês. */
    @Column(name = "notify_bills") public boolean notifyBills = true;
    @Column(name = "notify_limits") public boolean notifyLimits = true;
    @Column(name = "notify_summary") public boolean notifySummary = true;
    /** Quando a pessoa confirmou o e-mail (nulo = ainda não confirmou). Avisos por e-mail só saem confirmados. */
    @Column(name = "email_verified_at") public Instant emailVerifiedAt;
    @Column(name = "terms_accepted_at") public Instant termsAcceptedAt;
    @Column(name = "created_at") public Instant createdAt;

    /** Primeiro nome para cumprimentos em e-mails ("Olá, Ana!"). */
    public String firstName() {
        return name == null || name.isBlank() ? "tudo bem" : name.strip().split(" +")[0];
    }

    public List<Objective> objectives() {
        if (objectivesCsv == null || objectivesCsv.isBlank()) return List.of();
        return Arrays.stream(objectivesCsv.split(","))
                .map(String::strip)
                .filter(s -> !s.isEmpty())
                .map(Objective::valueOf)
                .distinct()
                .toList();
    }

    public void setObjectives(Collection<Objective> list) {
        objectivesCsv = list == null || list.isEmpty() ? null
                : list.stream().distinct().map(Enum::name).collect(Collectors.joining(","));
    }
}
