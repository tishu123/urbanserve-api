
package com.example.urbanserve.web;

import com.example.urbanserve.dto.ServicePackageResponse;
import com.example.urbanserve.model.Provider;
import com.example.urbanserve.model.ServiceCategory;
import com.example.urbanserve.repo.CategoryRepository;
import com.example.urbanserve.repo.ProviderRepository;
import com.example.urbanserve.repo.ServicePackageRepository;
import com.example.urbanserve.model.ServicePackage;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;

@RestController
@RequestMapping("/api")
public class CatalogController {

    private final CategoryRepository categories;
    private final ProviderRepository providers;
    private final ServicePackageRepository packages;

    public CatalogController(
            CategoryRepository categories,
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

    // Rohnit: Return active packages with backend-calculated prices.
    @GetMapping("/categories/{categoryId}/packages")
    public List<ServicePackageResponse> packages(
            @PathVariable Long categoryId) {

        ServiceCategory category = categories.findById(categoryId)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND, "Category not found"
                ));

        return packages
                .findByCategory_IdAndActiveTrueOrderByIdAsc(categoryId)
                .stream()
                .map(pkg -> new ServicePackageResponse(
                        pkg.getId(),
                        category.getId(),
                        pkg.getCode(),
                        pkg.getName(),
                        calculatePrice(category.getBasePrice(), pkg.getPriceFactor())
                ))
                .toList();
    }

    // Matches the existing frontend pricing formula.
    private BigDecimal calculatePrice(
            double basePrice,
            BigDecimal priceFactor) {

        BigDecimal rawPrice = BigDecimal.valueOf(basePrice)
                .multiply(priceFactor);

        BigDecimal roundedToTens = rawPrice.divide(
                BigDecimal.TEN, 0, RoundingMode.HALF_UP
        ).multiply(BigDecimal.TEN);

        return roundedToTens.subtract(BigDecimal.ONE)
                .max(BigDecimal.valueOf(99));
    }

    @GetMapping("/providers")
    public List<Provider> providers(
            @RequestParam(required = false) Long categoryId,
            @RequestParam(required = false) String city) {

        List<Provider> list = categoryId == null
                ? providers.findAll()
                : providers.findByCategoryIdOrderByRatingDesc(categoryId);

        return list.stream()
                .filter(p -> city == null
                        || p.getCity().equalsIgnoreCase(city))
                .toList();
    }
}
