package com.example.urbanserve.auth;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/auth")
public class AuthController {

    public record OtpRequest(@NotBlank String phone) {}
    public record VerifyRequest(@NotBlank String phone, @NotBlank String code) {}

    private final OtpService otp;

    public AuthController(OtpService otp) { this.otp = otp; }

    @PostMapping("/otp/request")
    public Map<String, Object> request(@Valid @RequestBody OtpRequest r) {
        long cooldown = otp.requestOtp(r.phone().trim());
        return Map.of("sent", true, "resendAfterSeconds", cooldown);
    }

    @PostMapping("/otp/verify")
    public OtpService.TokenResponse verify(@Valid @RequestBody VerifyRequest r) {
        return otp.verify(r.phone().trim(), r.code().trim());
    }

    /**
     * Server-side confirmation of the logged-in user. Only reachable with a token that passed the
     * otp_verified check in JwtConfig, so a 200 here means: this phone number completed OTP login.
     */
    @GetMapping("/me")
    public Map<String, Object> me(@AuthenticationPrincipal Jwt jwt) {
        List<String> roles = jwt.getClaimAsStringList("roles");
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("phone", jwt.getSubject());
        body.put("loggedIn", true);
        body.put("otpVerified", Boolean.TRUE.equals(jwt.getClaimAsBoolean(OtpService.CLAIM_OTP_VERIFIED)));
        body.put("verifiedVia", jwt.getClaimAsString(OtpService.CLAIM_VERIFIED_VIA));
        body.put("roles", roles == null ? List.of() : roles);
        body.put("loggedInAt", jwt.getIssuedAt());
        body.put("expiresAt", jwt.getExpiresAt());
        return body;
    }
}
