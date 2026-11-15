package com.example.urbanserve.auth;

import com.example.urbanserve.model.OtpChallenge;
import com.example.urbanserve.repo.OtpChallengeRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import javax.crypto.Mac;
import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.*;
import java.util.regex.Pattern;

@Service
public class OtpService {

    public static final Pattern PHONE = Pattern.compile("^[6-9]\\d{9}$");

    public record TokenResponse(String token, Instant expiresAt, String phone, List<String> roles) {}

    private final OtpChallengeRepository challenges;
    private final OtpSender sender;
    private final JwtEncoder jwtEncoder;
    private final SecretKey key;
    private final SecureRandom random = new SecureRandom();

    private final Duration otpTtl;
    private final int maxAttempts;
    private final Duration resendCooldown;
    private final int maxPerHour;
    private final Duration jwtTtl;
    private final String issuer;
    private final Set<String> adminPhones;

    public OtpService(OtpChallengeRepository challenges, OtpSender sender, JwtEncoder jwtEncoder, SecretKey key,
                      @Value("${app.otp.ttl:5m}") Duration otpTtl,
                      @Value("${app.otp.max-attempts:5}") int maxAttempts,
                      @Value("${app.otp.resend-cooldown:30s}") Duration resendCooldown,
                      @Value("${app.otp.max-per-hour:5}") int maxPerHour,
                      @Value("${app.jwt.ttl:24h}") Duration jwtTtl,
                      @Value("${app.jwt.issuer:urbanserve}") String issuer,
                      @Value("${app.admin-phones:}") String adminPhones) {
        this.challenges = challenges;
        this.sender = sender;
        this.jwtEncoder = jwtEncoder;
        this.key = key;
        this.otpTtl = otpTtl;
        this.maxAttempts = maxAttempts;
        this.resendCooldown = resendCooldown;
        this.maxPerHour = maxPerHour;
        this.jwtTtl = jwtTtl;
        this.issuer = issuer;
        this.adminPhones = new HashSet<>();
        for (String p : adminPhones.split(",")) if (!p.isBlank()) this.adminPhones.add(p.trim());
    }

    /** Generates and sends a new code. Returns seconds until a resend is allowed. */
    @Transactional
    public long requestOtp(String phone) {
        requireValidPhone(phone);
        Instant now = Instant.now();

        Optional<OtpChallenge> last = challenges.findFirstByPhoneOrderByCreatedAtDesc(phone);
        if (last.isPresent()) {
            long wait = Duration.between(now, last.get().getCreatedAt().plus(resendCooldown)).toSeconds();
            if (wait > 0) {
                throw new ResponseStatusException(HttpStatus.TOO_MANY_REQUESTS,
                        "Please wait " + wait + "s before requesting another code");
            }
        }
        long lastHour = challenges.countByPhoneAndCreatedAtAfter(phone, utc(now.minus(Duration.ofHours(1))));
        if (lastHour >= maxPerHour) {
            throw new ResponseStatusException(HttpStatus.TOO_MANY_REQUESTS,
                    "Too many codes requested. Try again in an hour.");
        }

        challenges.consumeAllForPhone(phone); // only the newest code is valid
        String code = String.format("%06d", random.nextInt(1_000_000));
        challenges.save(new OtpChallenge(phone, hash(phone, code), now, now.plus(otpTtl)));
        sender.send(phone, code);
        return resendCooldown.toSeconds();
    }

    /** Checks the code and, if valid, issues a JWT. */
    @Transactional(noRollbackFor = ResponseStatusException.class) // keep the attempt counter on failure
    public TokenResponse verify(String phone, String code) {
        requireValidPhone(phone);
        if (code == null || !code.matches("^\\d{6}$")) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Enter the 6-digit code");
        }
        OtpChallenge c = challenges.findFirstByPhoneOrderByCreatedAtDesc(phone)
                .filter(x -> !x.isConsumed())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Code expired. Request a new one."));

        if (Instant.now().isAfter(c.getExpiresAt()) || c.getAttempts() >= maxAttempts) {
            c.consume();
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Code expired. Request a new one.");
        }
        c.incrementAttempts();
        boolean ok = MessageDigest.isEqual(
                c.getCodeHash().getBytes(StandardCharsets.US_ASCII),
                hash(phone, code).getBytes(StandardCharsets.US_ASCII));
        if (!ok) {
            int left = maxAttempts - c.getAttempts();
            if (left <= 0) c.consume();
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED,
                    left > 0 ? "Incorrect code. " + left + " attempt(s) left." : "Too many attempts. Request a new code.");
        }
        c.consume();
        return issueToken(phone);
    }

    private TokenResponse issueToken(String phone) {
        Instant now = Instant.now();
        Instant exp = now.plus(jwtTtl);
        List<String> roles = adminPhones.contains(phone) ? List.of("CUSTOMER", "ADMIN") : List.of("CUSTOMER");
        JwtClaimsSet claims = JwtClaimsSet.builder()
                .issuer(issuer)
                .subject(phone)
                .issuedAt(now)
                .expiresAt(exp)
                .claim("roles", roles)
                .build();
        JwsHeader header = JwsHeader.with(MacAlgorithm.HS256).build();
        String token = jwtEncoder.encode(JwtEncoderParameters.from(header, claims)).getTokenValue();
        return new TokenResponse(token, exp, phone, roles);
    }

    /** Housekeeping: drop OTP rows older than a day. */
    @Scheduled(fixedDelayString = "PT1H")
    @Transactional
    public void purgeOld() {
        challenges.deleteOlderThan(utc(Instant.now().minus(Duration.ofDays(1))));
    }

    private String hash(String phone, String code) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(key);
            byte[] out = mac.doFinal((phone + ":" + code).getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(out);
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    private static void requireValidPhone(String phone) {
        if (phone == null || !PHONE.matcher(phone).matches()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Enter a valid 10-digit mobile number");
        }
    }

    private static LocalDateTime utc(Instant i) { return LocalDateTime.ofInstant(i, ZoneOffset.UTC); }
}
