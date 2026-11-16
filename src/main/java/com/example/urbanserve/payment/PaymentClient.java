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
        if (token.isBlank() || token.length() < 32) {
            throw new ResponseStatusException(
                    HttpStatus.SERVICE_UNAVAILABLE, "Payment service is not configured");
        }

        PaymentDetails result;
        try {
            result = client.post()
                    .uri("/api/payments")
                    .headers(headers -> headers.setBearerAuth(token))
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(new CreatePayment(bookingId, amountPaise, "COD_CASH"))
                    .retrieve()
                    .body(PaymentDetails.class);
        } catch (ResourceAccessException e) {
            throw new ResponseStatusException(
                    HttpStatus.SERVICE_UNAVAILABLE, "Payment service is unavailable");
        } catch (RestClientResponseException e) {
            if (e.getStatusCode().value() == 409) {
                throw new ResponseStatusException(
                        HttpStatus.CONFLICT, "This booking already has a different payment");
            }
            if (e.getStatusCode().value() == 503) {
                throw new ResponseStatusException(
                        HttpStatus.SERVICE_UNAVAILABLE, "Payment service is unavailable");
            }
            throw new ResponseStatusException(
                    HttpStatus.BAD_GATEWAY, "Payment service could not register the payment");
        } catch (RestClientException e) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_GATEWAY, "Payment service returned an unreadable response");
        }

        if (result == null || result.paymentId() == null
                || !Objects.equals(result.bookingId(), bookingId)
                || !Objects.equals(result.amount(), amountPaise)
                || !"INR".equals(result.currency())
                || !"COD_CASH".equals(result.paymentMethod())
                || result.paymentStatus() == null) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_GATEWAY, "Payment service returned unexpected payment details");
        }

        return result;
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
}