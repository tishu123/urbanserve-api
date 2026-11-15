package com.example.urbanserve.web;

import com.example.urbanserve.model.Provider;
import com.example.urbanserve.model.ServiceCategory;
import com.example.urbanserve.repo.CategoryRepository;
import com.example.urbanserve.repo.ProviderRepository;
import org.springframework.web.bind.annotation.*;
import java.util.List;

@RestController
@RequestMapping("/api")
public class CatalogController {
    private final CategoryRepository categories;
    private final ProviderRepository providers;

    public CatalogController(CategoryRepository categories, ProviderRepository providers) {
        this.categories = categories;
        this.providers = providers;
    }

    @GetMapping("/categories")
    public List<ServiceCategory> categories() {
        return categories.findAll();
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
