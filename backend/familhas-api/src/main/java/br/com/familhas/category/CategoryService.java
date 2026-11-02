package br.com.familhas.category;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import br.com.familhas.category.dto.CategoryFilter;
import br.com.familhas.category.dto.CategoryRequest;
import br.com.familhas.category.dto.CategoryResponse;
import br.com.familhas.common.PageResponse;
import br.com.familhas.common.Pagination;
import br.com.familhas.common.Specs;

@Service
@Transactional
public class CategoryService {

    static final Set<String> SORTABLE = Set.of("title", "createdAt", "updatedAt");

    private final CategoryRepository categoryRepository;

    public CategoryService(CategoryRepository categoryRepository) {
        this.categoryRepository = categoryRepository;
    }

    @Transactional(readOnly = true)
    public PageResponse<CategoryResponse> findAll(CategoryFilter filter, Pageable pageable) {
        Pagination.validateSort(pageable, SORTABLE);
        return PageResponse.of(categoryRepository.findAll(toSpecification(filter), pageable), this::toResponse);
    }

    private static Specification<CategoryEntity> toSpecification(CategoryFilter filter) {
        List<Specification<CategoryEntity>> specs = new ArrayList<>();
        if (filter.status() != null) {
            specs.add((root, query, cb) -> cb.equal(root.get("status"), filter.status()));
        }
        if (Specs.hasText(filter.q())) {
            specs.add(Specs.containsIgnoreCase("title", filter.q()));
        }
        return Specification.allOf(specs);
    }

    @Transactional(readOnly = true)
    public CategoryResponse findById(UUID id) {
        return toResponse(getCategoryOrThrow(id));
    }

    public CategoryResponse create(CategoryRequest request) {
        CategoryEntity category = new CategoryEntity();
        apply(category, request);
        return toResponse(categoryRepository.save(category));
    }

    public CategoryResponse update(UUID id, CategoryRequest request) {
        CategoryEntity category = getCategoryOrThrow(id);
        apply(category, request);
        return toResponse(categoryRepository.save(category));
    }

    public void delete(UUID id) {
        CategoryEntity category = getCategoryOrThrow(id);
        categoryRepository.delete(category);
        categoryRepository.flush();
    }

    private CategoryEntity getCategoryOrThrow(UUID id) {
        return categoryRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Category not found: " + id));
    }

    private void apply(CategoryEntity category, CategoryRequest request) {
        category.setTitle(request.title().trim());
        category.setStatus(request.status());
        category.setImageUrl(blankToNull(request.imageUrl()));
        category.setDescription(blankToNull(request.description()));
    }

    private static String blankToNull(String s) {
        if (s == null || s.isBlank()) {
            return null;
        }
        return s.trim();
    }

    private CategoryResponse toResponse(CategoryEntity category) {
        return new CategoryResponse(
                category.getId(),
                category.getTitle(),
                category.getStatus(),
                category.getImageUrl(),
                category.getDescription());
    }
}



