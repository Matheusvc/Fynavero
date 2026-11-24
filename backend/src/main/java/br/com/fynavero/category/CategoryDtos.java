package br.com.fynavero.category;

import br.com.fynavero.domain.Kind;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;

import java.util.List;

import static br.com.fynavero.common.ApiLimits.MAX_CENTS;

public final class CategoryDtos {
    private CategoryDtos() {}

    public record CatalogDto(Long id, String name, String icon, String group) {
        public static CatalogDto of(CatalogItem c) {
            return new CatalogDto(c.id, c.name, c.icon, c.group);
        }
    }

    public record CategoryDto(Long id, String name, Kind kind, String group, String icon, String color,
                              int sortOrder, boolean hidden, boolean loan, Long goalCents) {
        public static CategoryDto of(Category c) {
            return new CategoryDto(c.id, c.name, c.kind, c.group, c.icon, c.color, c.sortOrder, c.hidden, c.loan, c.goalCents);
        }
    }

    /** Criar ou editar. Na edição, campos nulos não mudam; goalCents 0 remove a meta. */
    public record CategoryReq(@NotBlank @Size(max = 60) String name,
                              Kind kind,
                              @Size(max = 30) String group,
                              @Size(max = 40) String icon,
                              Boolean hidden,
                              @PositiveOrZero @Max(MAX_CENTS) Long goalCents) {}

    public record ReorderReq(@NotNull Kind kind, @NotEmpty List<Long> ids) {}
}
