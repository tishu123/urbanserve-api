package com.example.urbanserve.web;

import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.assertEquals;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import org.springframework.test.web.servlet.MockMvc;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.example.urbanserve.repo.BookingRepository;
import com.example.urbanserve.repo.CategoryRepository;
import com.fasterxml.jackson.databind.ObjectMapper;

@SpringBootTest(properties = {
        "app.dispatch.transport=none",
        "app.otp.sender=log",
        "spring.datasource.url=jdbc:h2:mem:urbanserve-booking-pricing-tests;MODE=PostgreSQL;DATABASE_TO_LOWER=TRUE;DEFAULT_NULL_ORDERING=HIGH"
})
@AutoConfigureMockMvc
class BookingPricingIntegrationTest {

    private static final String TEST_PHONE = "9000000999";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private CategoryRepository categories;

    @Autowired
    private BookingRepository bookings;

    @Test
    void savesPackagePricesAndReturnsThemWhenBookingIsReadBack() throws Exception {
        String response = mockMvc.perform(post("/api/bookings")
                        .with(jwt().jwt(token -> token.subject(TEST_PHONE)))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(bookingBody("""
                                [
                                  {"packageCode": "1bhk", "quantity": 1},
                                  {"packageCode": "bathroom", "quantity": 2}
                                ]
                                """)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("PENDING"))
                .andExpect(jsonPath("$.totalAmountPaise").value(471700))
                .andReturn()
                .getResponse()
                .getContentAsString();

        long bookingId = objectMapper.readTree(response).get("id").asLong();

        mockMvc.perform(get("/api/bookings/{id}", bookingId)
                        .with(jwt().jwt(token -> token.subject(TEST_PHONE))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalAmountPaise").value(471700))
                .andExpect(jsonPath("$.items.length()").value(2))
                .andExpect(jsonPath("$.items[0].packageCode").value("1bhk"))
                .andExpect(jsonPath("$.items[0].packageName").value("1 BHK"))
                .andExpect(jsonPath("$.items[0].quantity").value(1))
                .andExpect(jsonPath("$.items[0].unitAmountPaise").value(351900))
                .andExpect(jsonPath("$.items[1].packageCode").value("bathroom"))
                .andExpect(jsonPath("$.items[1].quantity").value(2))
                .andExpect(jsonPath("$.items[1].unitAmountPaise").value(59900));
    }

    @Test
    void acceptsExistingCheckoutRequestWithoutItemsButLeavesTotalUnset() throws Exception {
        mockMvc.perform(post("/api/bookings")
                        .with(jwt().jwt(token -> token.subject(TEST_PHONE)))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(bookingBody(null)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("PENDING"))
                .andExpect(jsonPath("$.totalAmountPaise").doesNotExist())
                .andExpect(jsonPath("$.items.length()").value(0));
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "[{\"packageCode\":\"1bhk\",\"quantity\":0}]",
            "[{\"packageCode\":\"1bhk\",\"quantity\":1.5}]",
            "[{\"packageCode\":\"tap\",\"quantity\":1}]",
            "[{\"packageCode\":\"1bhk\",\"quantity\":1},{\"packageCode\":\"1bhk\",\"quantity\":2}]"
    })
    void rejectsInvalidItemsWithoutSavingABooking(String itemsJson) throws Exception {
        long countBefore = bookings.count();

        mockMvc.perform(post("/api/bookings")
                        .with(jwt().jwt(token -> token.subject(TEST_PHONE)))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(bookingBody(itemsJson)))
                .andExpect(status().isBadRequest());

        assertEquals(countBefore, bookings.count());
    }

    private String bookingBody(String itemsJson) {
        long categoryId = categories.findAll().stream()
                .filter(category -> category.getName().equals("Home Cleaning"))
                .findFirst()
                .orElseThrow()
                .getId();

        String itemsField = itemsJson == null ? "" : ", \"items\": " + itemsJson;

        return """
                {
                  "customerName": "Test Customer",
                  "address": "12 Test Road",
                  "city": "Bengaluru",
                  "scheduledAt": "%s",
                  "categoryId": %d
                  %s
                }
                """.formatted(
                LocalDateTime.now().plusDays(2).withNano(0),
                categoryId,
                itemsField
        );
    }
}