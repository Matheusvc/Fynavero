package br.com.fynavero.income;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;

/** Fonte de renda. Com valor informado, gera uma recorrência mensal de receita (estimada, se variável). */
@Entity
@Table(name = "income_sources")
public class IncomeSource {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) public Long id;
    @Column(name = "user_id") public Long userId;
    public String name;
    @Column(name = "amount_cents") public long amountCents;
    /** Fixa (mesmo valor todo mês) ou variável (valor estimado). */
    public boolean fixed;
    @Column(name = "category_id") public Long categoryId;
    @Column(name = "recurrence_id") public Long recurrenceId;
    @Column(name = "created_at") public Instant createdAt;
}
