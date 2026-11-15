
package com.example.urbanserve.web;

import com.example.urbanserve.model.ServiceCategory;
import com.example.urbanserve.repo.CategoryRepository;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.math.BigDecimal;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

// Rohnit: Integration tests for backend service package catalogue.
@SpringBootTest(properties = {
        "app.dispatch.transport=none",
        "spring.datasource.url=jdbc:h2:mem:urbanserve-package-tests;MODE=PostgreSQL;DATABASE_TO_LOWER=TRUE;DEFAULT_NULL_ORDERING=HIGH"
})
@AutoConfigureMockMvc
class CatalogPackageIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private CategoryRepository categories;

    @Autowired
    private ObjectMapper objectMapper;

    @Test
    void publicPackageEndpointReturnsAll22PackagesWithCorrectPrices()
            throws Exception {

        Map<String, Map<String, BigDecimal>> expected = Map.of(

                "Home Cleaning", Map.of(
                        "1bhk", new BigDecimal("3519"),
                        "2bhk", new BigDecimal("4469"),
                        "3bhk", new BigDecimal("5589"),
                        "4bhk", new BigDecimal("6869"),
                        "villa", new BigDecimal("9189"),
                        "kitchen", new BigDecimal("1599"),
                        "bathroom", new BigDecimal("599"),
                        "sofa", new BigDecimal("999")
                ),

                "Plumbing", Map.of(
                        "tap", new BigDecimal("399"),
                        "leak", new BigDecimal("499"),
                        "basin", new BigDecimal("699"),
                        "toilet", new BigDecimal("599"),
                        "tank", new BigDecimal("1199")
                ),

                "Electrician", Map.of(
                        "switch", new BigDecimal("349"),
                        "fan", new BigDecimal("439"),
                        "light", new BigDecimal("379"),
                        "mcb", new BigDecimal("519"),
                        "wiring", new BigDecimal("869")
                ),

                "Salon at Home", Map.of(
                        "haircut", new BigDecimal("599"),
                        "facial", new BigDecimal("1049"),
                        "manipedi", new BigDecimal("899"),
                        "wax", new BigDecimal("779")
                )
        );

        List<ServiceCategory> allCategories = categories.findAll();

        assertEquals(4, allCategories.size(),
                "Expected four service categories");

        int totalPackages = 0;
        Set<String> foundCategories = new HashSet<>();

        for (ServiceCategory category : allCategories) {

            Map<String, BigDecimal> expectedPackages =
                    expected.get(category.getName());

            assertNotNull(expectedPackages,
                    "Unexpected category: " + category.getName());

            foundCategories.add(category.getName());

            // No JWT is supplied: this also checks that catalogue access is public.
            MvcResult result = mockMvc.perform(
                            get("/api/categories/{id}/packages",
                                    category.getId()))
                    .andExpect(status().isOk())
                    .andReturn();

            JsonNode response = objectMapper.readTree(
                    result.getResponse().getContentAsString()
            );

            assertTrue(response.isArray(),
                    "Package response must be an array");

            assertEquals(expectedPackages.size(), response.size(),
                    "Incorrect package count for " + category.getName());

            Set<String> foundCodes = new HashSet<>();

            for (JsonNode item : response) {

                String code = item.path("code").asText();

                BigDecimal expectedPrice = expectedPackages.get(code);

                assertNotNull(expectedPrice,
                        "Unexpected package code: " + code);

                assertEquals(
                        expectedPrice,
                        item.path("price").decimalValue(),
                        "Incorrect price for " + category.getName()
                                + " / " + code
                );

                assertEquals(
                        category.getId().longValue(),
                        item.path("categoryId").asLong(),
                        "Incorrect category ID for package " + code
                );

                foundCodes.add(code);
            }

            assertEquals(expectedPackages.keySet(), foundCodes,
                    "Package codes do not match for " + category.getName());

            totalPackages += response.size();
        }

        assertEquals(expected.keySet(), foundCategories);
        assertEquals(22, totalPackages);
    }

    @Test
    void unknownCategoryReturns404() throws Exception {

        mockMvc.perform(
                        get("/api/categories/{id}/packages", Long.MAX_VALUE))
                .andExpect(status().isNotFound());
    }
}
