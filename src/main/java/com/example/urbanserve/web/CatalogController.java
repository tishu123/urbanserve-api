package com.example.urbanserve.web;

import java.util.List;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import com.example.urbanserve.dto.ServicePackageResponse;
import com.example.urbanserve.model.Provider;
import com.example.urbanserve.model.ServiceCategory;
import com.example.urbanserve.repo.CategoryRepository;
import com.example.urbanserve.repo.ProviderRepository;
import com.example.urbanserve.repo.ServicePackageRepository;

@RestController
@RequestMapping("/api")
public class CatalogController {

    private final CategoryRepository categories;
    private final ProviderRepository providers;
    private final ServicePackageRepository packages;

    public CatalogController(CategoryRepository categories,
                             ProviderRepository providers,
                             ServicePackageRepository packages) {
        this.categories = categories;
        this.providers = providers;
        this.packages = packages;
    }

    @GetMapping("/categories")
    public List<ServiceCategory> categories() {
        return categories.findAll();
    }

    @GetMapping("/categories/{categoryId}/packages")
    public List<ServicePackageResponse> packages(@PathVariable Long categoryId) {
        ServiceCategory category = categories.findById(categoryId)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND, "Category not found"));

        return packages.findByCategory_IdAndActiveTrueOrderByIdAsc(categoryId)
                .stream()
                .map(pkg -> new ServicePackageResponse(
                        pkg.getId(),
                        category.getId(),
                        pkg.getCode(),
                        pkg.getName(),
                        pkg.priceInRupees(category.getBasePrice())
                ))
                .toList();
    }

    @GetMapping("/providers")
    public List<Provider> providers(@RequestParam(required = false) Long categoryId,
                                    @RequestParam(required = false) String city) {
        List<Provider> list = categoryId == null
                ? providers.findAll()
                : providers.findByCategoryIdOrderByRatingDesc(categoryId);

        return list.stream()
                .filter(p -> city == null || p.getCity().equalsIgnoreCase(city))
                .toList();
    }
}