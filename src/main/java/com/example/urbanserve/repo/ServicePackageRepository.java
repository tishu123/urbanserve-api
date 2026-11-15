
package com.example.urbanserve.repo;

import com.example.urbanserve.model.ServicePackage;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface ServicePackageRepository
        extends JpaRepository<ServicePackage, Long> {

    // Get all active packages belonging to a category.
    List<ServicePackage> findByCategory_IdAndActiveTrueOrderByIdAsc(
            Long categoryId
    );

    // Find a particular active package using its category and code.
    Optional<ServicePackage> findByCategory_IdAndCodeAndActiveTrue(
            Long categoryId,
            String code
    );

    // Check whether a package already exists.
    boolean existsByCategory_IdAndCode(
            Long categoryId,
            String code
    );
}
