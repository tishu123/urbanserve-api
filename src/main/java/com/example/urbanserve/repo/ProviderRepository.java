package com.example.urbanserve.repo;

import com.example.urbanserve.model.Provider;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;

public interface ProviderRepository extends JpaRepository<Provider, Long> {
    List<Provider> findByCategoryIdOrderByRatingDesc(Long categoryId);
}
