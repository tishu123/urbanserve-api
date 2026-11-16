package com.example.urbanserve.web;

import com.example.urbanserve.repo.CategoryRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.net.InetSocketAddress;
import java.time.LocalDateTime;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(properties = {
        "app.dispatch.transport=none",
        "app.otp.sender=log",
        "spring.datasource.url=jdbc:h2:mem:booking-payment-status-tests;MODE=PostgreSQL;DATABASE_TO_LOWER=TRUE;DEFAULT_NULL_ORDERING=HIGH"
})
@AutoConfigureMockMvc
class BookingPaymentStatusIntegrationTest {

    private static final String OWNER = "9000000999";
    private static final String OTHER_CUSTOMER = "9000000998";
    private static final String TEST_TOKEN = "booking-status-integration-test-token-0123456789";
    private static final String STATUS_PREFIX = "/api/payments/bookings/";
    private static final int SAVED_TOTAL = 471700;

    private static final ObjectMapper STUB_JSON = new ObjectMapper();
    private static final List<String> CALLS = new CopyOnWriteArrayList<>();
    private static final AtomicReference<StubReply> REPLY = new AtomicReference<>();
    private static final HttpServer PAYMENT_STUB = startPaymentStub();

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private CategoryRepository categories;

    @DynamicPropertySource
    static void paymentProperties(DynamicPropertyRegistry registry) {
        registry.add("app.payment.base-url",
                () -> "http://127.0.0.1:" + PAYMENT_STUB.getAddress().getPort());
        registry.add("app.payment.internal-token", () -> TEST_TOKEN);
    }

    @BeforeEach
    void resetStub() {
        CALLS.clear();
        REPLY.set(new StubReply(200, "PAID", SAVED_TOTAL));
    }

    @AfterAll
    static void stopStub() {
        PAYMENT_STUB.stop(0);
    }

    @Test
    void ownerCanReadSavedPaidStatusWithoutCachingIt() throws Exception {
        long bookingId = createBooking();

        getStatus(bookingId, OWNER)
                .andExpect(status().isOk())
                .andExpect(header().string("Cache-Control", "no-store"))
                .andExpect(jsonPath("$.bookingId").value(bookingId))
                .andExpect(jsonPath("$.amount").value(SAVED_TOTAL))
                .andExpect(jsonPath("$.currency").value("INR"))
                .andExpect(jsonPath("$.paymentMethod").value("ONLINE"))
                .andExpect(jsonPath("$.paymentStatus").value("PAID"))
                .andExpect(jsonPath("$.razorpayPaymentId").doesNotExist());

        assertEquals(List.of(STATUS_PREFIX + bookingId + "/status"), CALLS);
    }

    @Test
    void ownerCanReadPendingStatusWithoutItBecomingPaid() throws Exception {
        long bookingId = createBooking();
        REPLY.set(new StubReply(200, "PENDING", SAVED_TOTAL));

        getStatus(bookingId, OWNER)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.paymentStatus").value("PENDING"));
    }

    @Test
    void bookingWithoutAPaymentReturnsNotStarted() throws Exception {
        long bookingId = createBooking();
        REPLY.set(new StubReply(200, "NOT_STARTED", null));

        getStatus(bookingId, OWNER)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.bookingId").value(bookingId))
                .andExpect(jsonPath("$.paymentStatus").value("NOT_STARTED"))
                .andExpect(jsonPath("$.amount").doesNotExist());
    }

    @Test
    void anotherCustomerCannotReadThePaymentStatus() throws Exception {
        long bookingId = createBooking();

        getStatus(bookingId, OTHER_CUSTOMER)
                .andExpect(status().isNotFound());

        assertTrue(CALLS.isEmpty());
    }

    @Test
    void paidStatusWithWrongAmountIsRejected() throws Exception {
        long bookingId = createBooking();
        REPLY.set(new StubReply(200, "PAID", 1));

        getStatus(bookingId, OWNER)
                .andExpect(status().isBadGateway())
                .andExpect(jsonPath("$.paymentStatus").doesNotExist());
    }

    @Test
    void unavailablePaymentServiceDoesNotLookLikeAnUnpaidBooking() throws Exception {
        long bookingId = createBooking();
        REPLY.set(new StubReply(503, null, null));

        getStatus(bookingId, OWNER)
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.paymentStatus").doesNotExist());
    }

    private ResultActions getStatus(long bookingId, String customer) throws Exception {
        return mockMvc.perform(get("/api/bookings/{id}/payment/status", bookingId)
                .with(jwt().jwt(token -> token.subject(customer))));
    }

    private long createBooking() throws Exception {
        long categoryId = categories.findAll().stream()
                .filter(category -> "Home Cleaning".equals(category.getName()))
                .findFirst()
                .orElseThrow()
                .getId();

        var request = objectMapper.createObjectNode()
                .put("customerName", "Test Customer")
                .put("address", "12 Test Road")
                .put("city", "Bengaluru")
                .put("scheduledAt", LocalDateTime.now().plusDays(2).withNano(0).toString())
                .put("categoryId", categoryId);

        var items = request.putArray("items");
        items.addObject().put("packageCode", "1bhk").put("quantity", 1);
        items.addObject().put("packageCode", "bathroom").put("quantity", 2);

        String response = mockMvc.perform(post("/api/bookings")
                        .with(jwt().jwt(token -> token.subject(OWNER)))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.totalAmountPaise").value(SAVED_TOTAL))
                .andReturn()
                .getResponse()
                .getContentAsString();

        return objectMapper.readTree(response).get("id").asLong();
    }

    private static HttpServer startPaymentStub() {
        try {
            HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);

            server.createContext(STATUS_PREFIX, exchange -> {
                try {
                    String path = exchange.getRequestURI().getPath();
                    CALLS.add(path);

                    if (!"GET".equals(exchange.getRequestMethod())
                            || !path.matches("/api/payments/bookings/[1-9][0-9]*/status")) {
                        exchange.sendResponseHeaders(404, -1);
                        return;
                    }

                    if (!("Bearer " + TEST_TOKEN).equals(
                            exchange.getRequestHeaders().getFirst("Authorization"))) {
                        exchange.sendResponseHeaders(401, -1);
                        return;
                    }

                    StubReply reply = REPLY.get();
                    if (reply.httpStatus() != 200) {
                        exchange.sendResponseHeaders(reply.httpStatus(), -1);
                        return;
                    }

                    long bookingId = Long.parseLong(path.substring(
                            STATUS_PREFIX.length(), path.length() - "/status".length()));

                    var body = STUB_JSON.createObjectNode()
                            .put("bookingId", bookingId)
                            .put("paymentStatus", reply.paymentStatus());

                    if (reply.amount() != null) {
                        body.put("amount", reply.amount().intValue())
                                .put("currency", "INR")
                                .put("paymentMethod", "ONLINE");
                    }

                    byte[] bytes = STUB_JSON.writeValueAsBytes(body);
                    exchange.getResponseHeaders().set("Content-Type", "application/json");
                    exchange.sendResponseHeaders(200, bytes.length);
                    exchange.getResponseBody().write(bytes);
                } finally {
                    exchange.close();
                }
            });

            server.start();
            return server;
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private record StubReply(int httpStatus, String paymentStatus, Integer amount) {}
}