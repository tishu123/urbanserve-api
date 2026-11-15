
package com.example.urbanserve.dto;

import java.math.BigDecimal;

// Rohnit: Response containing customer-visible package and pricing details.
public record ServicePackageResponse(
        Long id,
        Long categoryId,
        String code,
        String name,
        BigDecimal price
) {
}
