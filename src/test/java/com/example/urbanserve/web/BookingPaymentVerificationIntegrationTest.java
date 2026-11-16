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
import org.springframework.test.web.servlet.ResultActions;

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
        "spring.datasource.url=jdbc:h2:mem:booking-payment-verification-tests;MODE=PostgreSQL;DATABASE_TO_LOWER=TRUE;DEFAULT_NULL_ORDERING=HIGH"
})
@AutoConfigureMockMvc
class BookingPaymentVerificationIntegrationTest {

    private static final String OWNER = "9000000999";
    private static final String OTHER_CUSTOMER = "9000000998";
    private static final String TEST_TOKEN = "booking-verification-test-token-0123456789";
    private static final String VERIFY_PATH = "/api/payments/razorpay/verify";
    private static final String PAYMENT_ID = "pay_VerificationTest";
    private static final String ORDER_ID = "order_VerificationTest";
    private static final String SIGNATURE = "a".repeat(64);
    private static final long SAVED_TOTAL = 471700L;

    private static final ObjectMapper STUB_JSON = new ObjectMapper();
    private static final List<JsonNode> CALLS = new CopyOnWriteArrayList<>();
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
    void ownerReceivesVerifiedPaymentWithoutChangingTheBookingStatus() throws Exception {
        long bookingId = createBooking();

        verifyAs(bookingId, OWNER, PAYMENT_ID, SIGNATURE)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.paymentId").value(10000L + bookingId))
                .andExpect(jsonPath("$.bookingId").value(bookingId))
                .andExpect(jsonPath("$.amount").value(471700))
                .andExpect(jsonPath("$.currency").value("INR"))
                .andExpect(jsonPath("$.paymentMethod").value("ONLINE"))
                .andExpect(jsonPath("$.paymentStatus").value("PAID"))
                .andExpect(jsonPath("$.razorpayOrderId").value(ORDER_ID))
                .andExpect(jsonPath("$.razorpayPaymentId").value(PAYMENT_ID));

        var expected = objectMapper.createObjectNode()
                .put("bookingId", bookingId)
                .put("razorpayPaymentId", PAYMENT_ID)
                .put("razorpaySignature", SIGNATURE);

        // Compare the full JSON, allowing Jackson to normalize numeric node types.
        assertEquals(List.of(objectMapper.readTree(expected.toString())), CALLS);
        assertEquals(BookingStatus.PENDING,
                bookings.findById(bookingId).orElseThrow().getStatus());
    }

    @Test
    void anotherCustomerCannotVerifyTheBooking() throws Exception {
        long bookingId = createBooking();

        verifyAs(bookingId, OTHER_CUSTOMER, PAYMENT_ID, SIGNATURE)
                .andExpect(status().isNotFound());

        assertTrue(CALLS.isEmpty());
    }

    @Test
    void malformedSignatureIsRejectedBeforeCallingThePaymentService() throws Exception {
        long bookingId = createBooking();

        verifyAs(bookingId, OWNER, PAYMENT_ID, "invalid")
                .andExpect(status().isBadRequest());

        assertTrue(CALLS.isEmpty());
    }

    @Test
    void validLookingButIncorrectSignatureReturnsBadRequest() throws Exception {
        long bookingId = createBooking();

        verifyAs(bookingId, OWNER, PAYMENT_ID, "0".repeat(64))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.paymentStatus").doesNotExist());

        assertEquals(1, CALLS.size());
    }

    @Test
    void paymentNotYetCapturedReturnsConflict() throws Exception {
        long bookingId = createBooking();

        verifyAs(bookingId, OWNER, "pay_NotCaptured", SIGNATURE)
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.paymentStatus").doesNotExist());

        assertEquals(1, CALLS.size());
    }

    @Test
    void paidResponseWithWrongAmountIsRejected() throws Exception {
        long bookingId = createBooking();

        verifyAs(bookingId, OWNER, "pay_WrongAmount", SIGNATURE)
                .andExpect(status().isBadGateway())
                .andExpect(jsonPath("$.paymentStatus").doesNotExist());

        assertEquals(1, CALLS.size());
    }

    private ResultActions verifyAs(
            long bookingId, String customer, String paymentId, String signature)
            throws Exception {
        var request = objectMapper.createObjectNode()
                .put("razorpayPaymentId", paymentId)
                .put("razorpaySignature", signature)
                .put("amount", 1)
                .put("razorpayOrderId", "order_FromBrowser");

        return mockMvc.perform(post("/api/bookings/{id}/payment/online/verify", bookingId)
                .with(jwt().jwt(token -> token.subject(customer)))
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)));
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
                .andReturn()
                .getResponse()
                .getContentAsString();

        return objectMapper.readTree(response).get("id").asLong();
    }

    private static HttpServer startPaymentStub() {
        try {
            HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);

            server.createContext(VERIFY_PATH, exchange -> {
                try {
                    if (!VERIFY_PATH.equals(exchange.getRequestURI().getPath())
                            || !"POST".equals(exchange.getRequestMethod())) {
                        exchange.sendResponseHeaders(404, -1);
                        return;
                    }
                    if (!("Bearer " + TEST_TOKEN).equals(
                            exchange.getRequestHeaders().getFirst("Authorization"))) {
                        exchange.sendResponseHeaders(401, -1);
                        return;
                    }

                    JsonNode request = STUB_JSON.readTree(exchange.getRequestBody());
                    CALLS.add(request);
                    long bookingId = request.path("bookingId").asLong();
                    String paymentId = request.path("razorpayPaymentId").asText();

                    if (!SIGNATURE.equals(request.path("razorpaySignature").asText())) {
                        exchange.sendResponseHeaders(400, -1);
                        return;
                    }
                    if ("pay_NotCaptured".equals(paymentId)) {
                        exchange.sendResponseHeaders(409, -1);
                        return;
                    }

                    long amount = "pay_WrongAmount".equals(paymentId) ? 1L : SAVED_TOTAL;
                    var response = STUB_JSON.createObjectNode()
                            .put("paymentId", 10000L + bookingId)
                            .put("bookingId", bookingId)
                            .put("amount", amount)
                            .put("currency", "INR")
                            .put("paymentMethod", "ONLINE")
                            .put("paymentStatus", "PAID")
                            .put("razorpayOrderId", ORDER_ID)
                            .put("razorpayPaymentId", paymentId);

                    byte[] bytes = STUB_JSON.writeValueAsBytes(response);
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
}