package com.example.urbanserve.auth;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.MissingNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.util.UriBuilder;

import java.io.InputStream;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.function.Function;

/**
 * Sends and verifies OTPs through Message Central VerifyNow (https://www.messagecentral.com).
 * VerifyNow sends the SMS from its own DLT-registered sender and template, so no DLT registration
 * of your own is needed. Enable with app.otp.sender=messagecentral.
 *
 * API: GET  /auth/v1/authentication/token  -> token
 *      POST /verification/v3/send          -> data.verificationId
 *      GET  /verification/v3/validateOtp   -> data.verificationStatus = VERIFICATION_COMPLETED
 */
@Component
@ConditionalOnProperty(name = "app.otp.sender", havingValue = "messagecentral")
public class MessageCentralOtpProvider implements ManagedOtpProvider {

    private static final Logger log = LoggerFactory.getLogger(MessageCentralOtpProvider.class);
    private static final String COUNTRY = "91";
    private static final int OTP_LENGTH = 6; // the web app and OtpService expect 6 digits

    private record Reply(int httpStatus, int code, JsonNode body) {
        JsonNode data() { return body.path("data"); }
        String message() {
            String m = data().path("errorMessage").asText("");
            return m.isBlank() || m.equals("null") ? body.path("message").asText("") : m;
        }
        boolean unauthorized() { return httpStatus == 401 || httpStatus == 403 || code == 401; }
    }

    private final RestClient http;
    private final ObjectMapper json;
    private final String customerId;
    private final String key;
    private final String staticToken;
    private final String flowType;
    private volatile String token;

    public MessageCentralOtpProvider(ObjectMapper json,
                                     @Value("${app.otp.messagecentral.base-url:https://cpaas.messagecentral.com}") String baseUrl,
                                     @Value("${app.otp.messagecentral.customer-id:}") String customerId,
                                     @Value("${app.otp.messagecentral.key:}") String key,
                                     @Value("${app.otp.messagecentral.password:}") String password,
                                     @Value("${app.otp.messagecentral.auth-token:}") String authToken,
                                     @Value("${app.otp.messagecentral.flow-type:SMS}") String flowType) {
        if (customerId.isBlank()) {
            throw new IllegalStateException("app.otp.sender=messagecentral needs APP_OTP_MC_CUSTOMER_ID");
        }
        if (key.isBlank() && !password.isEmpty()) {
            key = Base64.getEncoder().encodeToString(password.getBytes(StandardCharsets.UTF_8));
        }
        if (key.isBlank() && authToken.isBlank()) {
            throw new IllegalStateException(
                    "app.otp.sender=messagecentral needs APP_OTP_MC_PASSWORD (your Message Central password), "
                    + "APP_OTP_MC_KEY (Base64 of it) or APP_OTP_MC_AUTH_TOKEN, e.g. in a .env file");
        }
        String flow = flowType.trim().toUpperCase();
        if (!flow.equals("SMS") && !flow.equals("WHATSAPP")) {
            throw new IllegalStateException("app.otp.messagecentral.flow-type must be SMS or WHATSAPP");
        }
        SimpleClientHttpRequestFactory rf = new SimpleClientHttpRequestFactory();
        rf.setConnectTimeout(5_000);
        rf.setReadTimeout(10_000);
        this.http = RestClient.builder().baseUrl(baseUrl).requestFactory(rf).build();
        this.json = json;
        this.customerId = customerId.trim();
        this.key = key.trim();
        this.staticToken = authToken.trim();
        this.flowType = flow;
        log.info("OTP sender = Message Central VerifyNow ({}), customerId={}", flow, this.customerId);
    }

    @Override
    public Started start(String phone) {
        Reply r = withToken(t -> call(HttpMethod.POST, "/verification/v3/send", t, Map.of(
                "countryCode", COUNTRY,
                "customerId", customerId,
                "flowType", flowType,
                "mobileNumber", phone,
                "otpLength", String.valueOf(OTP_LENGTH))));

        if (r.code() == 200) {
            String id = r.data().path("verificationId").asText("");
            if (!id.isBlank() && !id.equals("null")) {
                return new Started(id, r.data().path("timeout").asLong(0));
            }
        }
        switch (r.code()) {
            case 506 -> throw new ResponseStatusException(HttpStatus.TOO_MANY_REQUESTS,
                    "A code was sent recently. Please wait a minute before requesting another.");
            case 800 -> throw new ResponseStatusException(HttpStatus.TOO_MANY_REQUESTS,
                    "Too many codes requested. Try again later.");
            default -> throw providerError("send", r);
        }
    }

    @Override
    public Result check(String reference, String phone, String code) {
        Reply r = withToken(t -> call(HttpMethod.GET, "/verification/v3/validateOtp", t, Map.of(
                "countryCode", COUNTRY,
                "customerId", customerId,
                "flowType", flowType,
                "mobileNumber", phone,
                "verificationId", reference,
                "code", code)));

        if (r.code() == 200) {
            return "VERIFICATION_COMPLETED".equals(r.data().path("verificationStatus").asText())
                    ? Result.VALID : Result.WRONG;
        }
        return switch (r.code()) {
            case 700, 702 -> Result.WRONG;                // VERIFICATION_FAILED, WRONG_OTP_PROVIDED
            case 505, 703, 705, 800 -> Result.EXPIRED;    // invalid id, already verified, expired, limit reached
            default -> throw providerError("validateOtp", r);
        };
    }

    // ---------------------------------------------------------------- HTTP

    /** Runs a call with the cached token; on 401 fetches a fresh token and retries once. */
    private Reply withToken(Function<String, Reply> fn) {
        Reply r = fn.apply(token());
        if (r.unauthorized() && staticToken.isBlank()) {
            token = null;
            r = fn.apply(token());
        }
        if (r.unauthorized()) {
            log.error("Message Central rejected the auth token (HTTP {}, code {}): {}", r.httpStatus(), r.code(), r.message());
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "The SMS service is unavailable right now. Please try again.");
        }
        return r;
    }

    private String token() {
        if (!staticToken.isBlank()) return staticToken;
        String t = token;
        if (t != null) return t;
        synchronized (this) {
            if (token == null) {
                Reply r = call(HttpMethod.GET, "/auth/v1/authentication/token", null, Map.of(
                        "customerId", customerId,
                        "key", key,
                        "scope", "NEW",
                        "country", COUNTRY));
                String fresh = r.body().path("token").asText("");
                if (fresh.isBlank() || fresh.equals("null")) {
                    log.error("Message Central token request failed (HTTP {}, code {}): {}. Check APP_OTP_MC_CUSTOMER_ID and APP_OTP_MC_KEY.",
                            r.httpStatus(), r.code(), r.message());
                    throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "The SMS service is unavailable right now. Please try again.");
                }
                token = fresh;
            }
            return token;
        }
    }

    private Reply call(HttpMethod method, String path, String authToken, Map<String, String> params) {
        try {
            return http.method(method)
                    .uri(b -> build(b, path, params))
                    .headers(h -> { if (authToken != null) h.set("authToken", authToken); })
                    .exchange((req, res) -> {
                        int status = res.getStatusCode().value();
                        JsonNode body = MissingNode.getInstance();
                        try (InputStream in = res.getBody()) {
                            body = json.readTree(in);
                            if (body == null) body = MissingNode.getInstance();
                        } catch (Exception ignored) { /* empty or non-JSON body */ }
                        return new Reply(status, body.path("responseCode").asInt(status), body);
                    });
        } catch (RestClientException e) {
            log.error("Message Central {} call failed: {}", path, e.getMessage());
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "Could not reach the SMS service. Please try again.");
        }
    }

    /** Values go in as URI variables so they are fully percent-encoded (the Base64 key may contain + / =). */
    private static URI build(UriBuilder b, String path, Map<String, String> params) {
        b.path(path);
        Map<String, String> vars = new LinkedHashMap<>();
        params.forEach((k, v) -> { b.queryParam(k, "{" + k + "}"); vars.put(k, v); });
        return b.build(vars);
    }

    private static ResponseStatusException providerError(String op, Reply r) {
        log.error("Message Central {} failed (HTTP {}, code {}): {}", op, r.httpStatus(), r.code(), r.message());
        return new ResponseStatusException(HttpStatus.BAD_GATEWAY, "The SMS service is unavailable right now. Please try again.");
    }
}
