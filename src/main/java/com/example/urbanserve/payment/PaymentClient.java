package com.example.urbanserve.payment;

import java.net.http.HttpClient;
import java.time.Duration;
import java.util.Objects;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;
import org.springframework.web.server.ResponseStatusException;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

@Component
public class PaymentClient {

    private final RestClient client;
    private final String token;

    public PaymentClient(
            @Value("${app.payment.base-url}") String baseUrl,
            @Value("${app.payment.internal-token:}") String token) {
        this.token = token;

        HttpClient httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(3))
                .followRedirects(HttpClient.Redirect.NEVER)
                .build();

        JdkClientHttpRequestFactory requestFactory =
                new JdkClientHttpRequestFactory(httpClient);
        requestFactory.setReadTimeout(Duration.ofSeconds(5));

        this.client = RestClient.builder()
                .baseUrl(baseUrl)
                .requestFactory(requestFactory)
                .build();
    }

    public PaymentDetails registerCashPayment(long bookingId, long amountPaise) {
        PaymentDetails result = postPayment(
                "/api/payments",
                new CreatePayment(bookingId, amountPaise, "COD_CASH"),
                PaymentDetails.class,
                "This booking already has a different payment",
                "Payment service could not register the payment");

        if (result == null || result.paymentId() == null
                || !Objects.equals(result.bookingId(), bookingId)
                || !Objects.equals(result.amount(), amountPaise)
                || !"INR".equals(result.currency())
                || !"COD_CASH".equals(result.paymentMethod())
                || result.paymentStatus() == null) {
            throw unexpectedPaymentDetails();
        }

        return result;
    }

    public OnlinePaymentDetails prepareOnlinePayment(long bookingId, long amountPaise) {
        OnlinePaymentDetails result = postPayment(
                "/api/payments/razorpay/order",
                new CreatePayment(bookingId, amountPaise, "ONLINE"),
                OnlinePaymentDetails.class,
                "This booking has a conflicting payment or its online order is not ready",
                "Payment service could not prepare the online order");

        if (result == null || result.paymentId() == null || result.paymentId() <= 0
                || !Objects.equals(result.bookingId(), bookingId)
                || !Objects.equals(result.amount(), amountPaise)
                || !"INR".equals(result.currency())
                || !"ONLINE".equals(result.paymentMethod())
                || !"PENDING".equals(result.paymentStatus())
                || result.razorpayOrderId() == null
                || !result.razorpayOrderId().matches("order_[A-Za-z0-9]+")) {
            throw unexpectedPaymentDetails();
        }

        return result;
    }

    private <T> T postPayment(
            String path, CreatePayment request, Class<T> responseType,
            String conflictMessage, String failureMessage) {
        if (token.isBlank() || token.length() < 32) {
            throw new ResponseStatusException(
                    HttpStatus.SERVICE_UNAVAILABLE, "Payment service is not configured");
        }

        try {
            return client.post()
                    .uri(path)
                    .headers(headers -> headers.setBearerAuth(token))
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(request)
                    .retrieve()
                    .body(responseType);
        } catch (ResourceAccessException e) {
            throw new ResponseStatusException(
                    HttpStatus.SERVICE_UNAVAILABLE, "Payment service is unavailable");
        } catch (RestClientResponseException e) {
            if (e.getStatusCode().value() == 409) {
                throw new ResponseStatusException(HttpStatus.CONFLICT, conflictMessage);
            }
            if (e.getStatusCode().value() == 503) {
                throw new ResponseStatusException(
                        HttpStatus.SERVICE_UNAVAILABLE, "Payment service is unavailable");
            }
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, failureMessage);
        } catch (RestClientException e) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_GATEWAY, "Payment service returned an unreadable response");
        }
    }

    private ResponseStatusException unexpectedPaymentDetails() {
        return new ResponseStatusException(
                HttpStatus.BAD_GATEWAY, "Payment service returned unexpected payment details");
    }

    private record CreatePayment(long bookingId, long amount, String paymentMethod) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record PaymentDetails(
            Long paymentId,
            Long bookingId,
            Long amount,
            String currency,
            String paymentMethod,
            String paymentStatus) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record OnlinePaymentDetails(
            Long paymentId,
            Long bookingId,
            Long amount,
            String currency,
            String paymentMethod,
            String paymentStatus,
            String razorpayOrderId) {}
}