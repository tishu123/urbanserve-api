package com.example.urbanserve.web;

import com.example.urbanserve.model.BookingStatus;
import com.example.urbanserve.repo.BookingRepository;
import com.example.urbanserve.repo.CategoryRepository;
import com.fasterxml.jackson.databind.JsonNode;
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

import java.io.IOException;
import java.io.UncheckedIOException;
import java.net.InetSocketAddress;
import java.time.LocalDateTime;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(properties = {
        "app.dispatch.transport=none",
        "app.otp.sender=log",
        "spring.datasource.url=jdbc:h2:mem:booking-payment-tests;MODE=PostgreSQL;DATABASE_TO_LOWER=TRUE;DEFAULT_NULL_ORDERING=HIGH"
})
@AutoConfigureMockMvc
class BookingPaymentIntegrationTest {

    private static final String OWNER = "9000000999";
    private static final String OTHER_CUSTOMER = "9000000998";
    private static final String TEST_TOKEN = "booking-payment-integration-test-token-0123456789";
    private static final String CASH_PATH = "/api/payments";
    private static final String ONLINE_PATH = "/api/payments/razorpay/order";
    private static final ObjectMapper STUB_JSON = new ObjectMapper();
    private static final List<PaymentCall> CALLS = new CopyOnWriteArrayList<>();
    private static final HttpServer PAYMENT_STUB = startPaymentStub();

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private CategoryRepository categories;

    @Autowired
    private BookingRepository bookings;

    @DynamicPropertySource
    static void paymentProperties(DynamicPropertyRegistry registry) {
        registry.add("app.payment.base-url",
                () -> "http://127.0.0.1:" + PAYMENT_STUB.getAddress().getPort());
        registry.add("app.payment.internal-token", () -> TEST_TOKEN);
    }

    @BeforeEach
    void clearRecordedCalls() {
        CALLS.clear();
    }

    @AfterAll
    static void stopPaymentStub() {
        PAYMENT_STUB.stop(0);
    }

    @Test
    void ownerUsesSavedAmountAndRetryReturnsTheSamePaymentId() throws Exception {
        long bookingId = createBooking(true);
        long expectedPaymentId = 10000L + bookingId;

        mockMvc.perform(post("/api/bookings/{id}/payment/cash", bookingId)
                        .with(jwt().jwt(token -> token.subject(OWNER)))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"amount\": 1}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.paymentId").value(expectedPaymentId))
                .andExpect(jsonPath("$.amount").value(471700))
                .andExpect(jsonPath("$.paymentMethod").value("COD_CASH"))
                .andExpect(jsonPath("$.paymentStatus").value("PENDING"));

        mockMvc.perform(post("/api/bookings/{id}/payment/cash", bookingId)
                        .with(jwt().jwt(token -> token.subject(OWNER))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.paymentId").value(expectedPaymentId))
                .andExpect(jsonPath("$.amount").value(471700));

        PaymentCall expectedCall = new PaymentCall(CASH_PATH, bookingId, 471700L, "COD_CASH");
        assertEquals(List.of(expectedCall, expectedCall), CALLS);
    }

    @Test
    void otherCustomerCannotRegisterPaymentForThisBooking() throws Exception {
        long bookingId = createBooking(true);

        mockMvc.perform(post("/api/bookings/{id}/payment/cash", bookingId)
                        .with(jwt().jwt(token -> token.subject(OTHER_CUSTOMER))))
                .andExpect(status().isNotFound());

        assertTrue(CALLS.isEmpty());
    }

    @Test
    void bookingWithoutSavedPriceIsRejected() throws Exception {
        long bookingId = createBooking(false);

        mockMvc.perform(post("/api/bookings/{id}/payment/cash", bookingId)
                        .with(jwt().jwt(token -> token.subject(OWNER))))
                .andExpect(status().isConflict());

        assertTrue(CALLS.isEmpty());
    }

    @Test
    void cancelledBookingIsRejected() throws Exception {
        long bookingId = createBooking(true);
        var booking = bookings.findById(bookingId).orElseThrow();
        booking.cancel("Cancelled for test");
        bookings.saveAndFlush(booking);
        assertEquals(BookingStatus.CANCELLED, booking.getStatus());

        mockMvc.perform(post("/api/bookings/{id}/payment/cash", bookingId)
                        .with(jwt().jwt(token -> token.subject(OWNER))))
                .andExpect(status().isConflict());

        assertTrue(CALLS.isEmpty());
    }

    @Test
    void ownerStartsOnlinePaymentAtSavedPriceAndReceivesTheSameOrderOnRetry() throws Exception {
        long bookingId = createBooking(true);
        long expectedPaymentId = 10000L + bookingId;
        String expectedOrderId = testOrderId(bookingId);

        mockMvc.perform(post("/api/bookings/{id}/payment/online", bookingId)
                        .with(jwt().jwt(token -> token.subject(OWNER)))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"amount\": 1}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.paymentId").value(expectedPaymentId))
                .andExpect(jsonPath("$.amount").value(471700))
                .andExpect(jsonPath("$.currency").value("INR"))
                .andExpect(jsonPath("$.paymentMethod").value("ONLINE"))
                .andExpect(jsonPath("$.paymentStatus").value("PENDING"))
                .andExpect(jsonPath("$.razorpayOrderId").value(expectedOrderId));

        mockMvc.perform(post("/api/bookings/{id}/payment/online", bookingId)
                        .with(jwt().jwt(token -> token.subject(OWNER))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.paymentId").value(expectedPaymentId))
                .andExpect(jsonPath("$.amount").value(471700))
                .andExpect(jsonPath("$.paymentStatus").value("PENDING"))
                .andExpect(jsonPath("$.razorpayOrderId").value(expectedOrderId));

        PaymentCall expectedCall = new PaymentCall(ONLINE_PATH, bookingId, 471700L, "ONLINE");
        assertEquals(List.of(expectedCall, expectedCall), CALLS);
    }

    @Test
    void otherCustomerCannotStartOnlinePaymentForThisBooking() throws Exception {
        long bookingId = createBooking(true);

        mockMvc.perform(post("/api/bookings/{id}/payment/online", bookingId)
                        .with(jwt().jwt(token -> token.subject(OTHER_CUSTOMER))))
                .andExpect(status().isNotFound());

        assertTrue(CALLS.isEmpty());
    }

    private long createBooking(boolean withItems) throws Exception {
        long categoryId = categories.findAll().stream()
                .filter(category -> "Home Cleaning".equals(category.getName()))
                .findFirst()
                .orElseThrow()
                .getId();

        var request = objectMapper.createObjectNode();
        request.put("customerName", "Test Customer");
        request.put("address", "12 Test Road");
        request.put("city", "Bengaluru");
        request.put("scheduledAt", LocalDateTime.now().plusDays(2).withNano(0).toString());
        request.put("categoryId", categoryId);

        if (withItems) {
            var items = request.putArray("items");
            items.addObject().put("packageCode", "1bhk").put("quantity", 1);
            items.addObject().put("packageCode", "bathroom").put("quantity", 2);
        }

        String response = mockMvc.perform(post("/api/bookings")
                        .with(jwt().jwt(token -> token.subject(OWNER)))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andReturn()
                .getResponse()
                .getContentAsString();

        return objectMapper.readTree(response).get("id").asLong();
    }

    private static HttpServer startPaymentStub() {
        try {
            HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);

            server.createContext(CASH_PATH, exchange -> {
                try {
                    String path = exchange.getRequestURI().getPath();
                    boolean online = ONLINE_PATH.equals(path);
                    if (!online && !CASH_PATH.equals(path)) {
                        exchange.sendResponseHeaders(404, -1);
                        return;
                    }

                    if (!"POST".equals(exchange.getRequestMethod())) {
                        exchange.sendResponseHeaders(405, -1);
                        return;
                    }

                    if (!("Bearer " + TEST_TOKEN).equals(
                            exchange.getRequestHeaders().getFirst("Authorization"))) {
                        exchange.sendResponseHeaders(401, -1);
                        return;
                    }

                    JsonNode body = STUB_JSON.readTree(exchange.getRequestBody());
                    long bookingId = body.path("bookingId").asLong();
                    long amount = body.path("amount").asLong();
                    String method = body.path("paymentMethod").asText();

                    CALLS.add(new PaymentCall(path, bookingId, amount, method));

                    String expectedMethod = online ? "ONLINE" : "COD_CASH";
                    if (!expectedMethod.equals(method)) {
                        exchange.sendResponseHeaders(400, -1);
                        return;
                    }

                    var reply = STUB_JSON.createObjectNode();
                    reply.put("paymentId", 10000L + bookingId);
                    reply.put("bookingId", bookingId);
                    reply.put("amount", amount);
                    reply.put("currency", "INR");
                    reply.put("paymentMethod", method);
                    reply.put("paymentStatus", "PENDING");
                    if (online) {
                        reply.put("razorpayOrderId", testOrderId(bookingId));
                    }

                    byte[] response = STUB_JSON.writeValueAsBytes(reply);
                    exchange.getResponseHeaders().set("Content-Type", "application/json");
                    exchange.sendResponseHeaders(online ? 200 : 201, response.length);
                    exchange.getResponseBody().write(response);
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

    private static String testOrderId(long bookingId) {
        return "order_Test" + bookingId;
    }

    private record PaymentCall(String path, long bookingId, long amount, String method) {}
}