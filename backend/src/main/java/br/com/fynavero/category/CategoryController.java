package br.com.fynavero.category;

import br.com.fynavero.category.CategoryDtos.CatalogDto;
import br.com.fynavero.category.CategoryDtos.CategoryDto;
import br.com.fynavero.category.CategoryDtos.CategoryReq;
import br.com.fynavero.category.CategoryDtos.ReorderReq;
import br.com.fynavero.security.AuthUser;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api")
public class CategoryController {
    private final CategoryService categories;

    public CategoryController(CategoryService categories) {
        this.categories = categories;
    }

    @GetMapping("/catalog")
    public List<CatalogDto> catalog() {
        return categories.catalog();
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
}
