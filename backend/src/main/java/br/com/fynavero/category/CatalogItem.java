package br.com.fynavero.category;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/** Catálogo global de categorias sugeridas no onboarding (carregado pela migração V2). */
@Entity
@Table(name = "category_catalog")
public class CatalogItem {
    @Id public Long id;
    public String name;
    public String icon;
    @Column(name = "group_name") public String group;
    @Column(name = "sort_order") public int sortOrder;
}
