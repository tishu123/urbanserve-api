package com.example.urbanserve.auth;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;

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

    @GetMapping("/me")
    public Map<String, Object> me(@AuthenticationPrincipal Jwt jwt) {
        List<String> roles = jwt.getClaimAsStringList("roles");
        return Map.of("phone", jwt.getSubject(), "roles", roles == null ? List.of() : roles,
                "expiresAt", jwt.getExpiresAt());
    }
}
