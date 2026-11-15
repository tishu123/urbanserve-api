package com.example.urbanserve.repo;

import com.example.urbanserve.model.ServiceCategory;
import org.springframework.data.jpa.repository.JpaRepository;

public interface CategoryRepository extends JpaRepository<ServiceCategory, Long> {}
