package br.com.fynavero.category;

import br.com.fynavero.ledger.Kind;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;

/** Categoria criada pela pessoa (despesa, receita ou investimento). */
@Entity
@Table(name = "categories")
public class Category {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) public Long id;
    @Column(name = "user_id") public Long userId;
    public String name;
    @Enumerated(EnumType.STRING) public Kind kind;
    @Column(name = "group_name") public String group;
    /** Nome do ícone (lucide), ex.: "shopping-cart". */
    public String icon;
    /** Token de cor do gráfico (chart-1..chart-6); a cor acompanha a categoria. */
    public String color;
    @Column(name = "sort_order") public int sortOrder;
    public boolean hidden;
    /** Receita que é empréstimo recebido: entra no saldo, mas não conta como renda nem para a contribuição. */
    public boolean loan;
    /**
     * Meta opcional, em centavos. Em investimento é o valor a juntar (objetivo de economia);
     * em despesa é o limite de gasto do mês.
     */
    @Column(name = "goal_cents") public Long goalCents;
    /** Prazo do objetivo de economia (só investimentos). */
    @Column(name = "goal_deadline") public java.time.LocalDate goalDeadline;
    @Column(name = "created_at") public Instant createdAt;
}
