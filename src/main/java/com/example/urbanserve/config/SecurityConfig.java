package com.example.urbanserve.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationConverter;
import org.springframework.security.oauth2.server.resource.authentication.JwtGrantedAuthoritiesConverter;
import org.springframework.security.web.SecurityFilterChain;

import static org.springframework.security.web.util.matcher.AntPathRequestMatcher.antMatcher;

@Configuration
@EnableMethodSecurity
@EnableScheduling
public class SecurityConfig {

    @Bean
    SecurityFilterChain api(HttpSecurity http,
                            @Value("${spring.h2.console.enabled:false}") boolean h2Console) throws Exception {
        http
            // Stateless bearer-token API: no cookies, no sessions, so CSRF protection is not applicable.
            .csrf(csrf -> csrf.disable())
            .sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
            .authorizeHttpRequests(auth -> {
                // Explicit ant matchers: avoids Spring Security's "ambiguous servlet" error when the
                // H2 console servlet is registered alongside the DispatcherServlet.
                // Rohnit: Public catalogue access for categories, providers and service packages.
auth.requestMatchers(
        antMatcher(HttpMethod.GET, "/api/categories"),
        antMatcher(HttpMethod.GET, "/api/providers"),
        antMatcher(HttpMethod.GET, "/api/categories/*/packages")
).permitAll();
                auth.requestMatchers(antMatcher(HttpMethod.POST, "/api/auth/otp/request"), antMatcher(HttpMethod.POST, "/api/auth/otp/verify")).permitAll();
                auth.requestMatchers(antMatcher("/error")).permitAll();
                if (h2Console) auth.requestMatchers(antMatcher("/h2-console/**")).permitAll();
                auth.requestMatchers(antMatcher("/api/**")).authenticated();
                auth.anyRequest().denyAll();
            })
            .oauth2ResourceServer(o -> o.jwt(j -> j.jwtAuthenticationConverter(rolesConverter())));

        if (h2Console) {
            http.headers(h -> h.frameOptions(f -> f.sameOrigin())); // H2 console uses frames (dev only)
        }
        return http.build();
    }

    /** Maps the "roles" claim (["CUSTOMER","ADMIN"]) to ROLE_CUSTOMER / ROLE_ADMIN. */
    private JwtAuthenticationConverter rolesConverter() {
        JwtGrantedAuthoritiesConverter roles = new JwtGrantedAuthoritiesConverter();
        roles.setAuthoritiesClaimName("roles");
        roles.setAuthorityPrefix("ROLE_");
        JwtAuthenticationConverter c = new JwtAuthenticationConverter();
        c.setJwtGrantedAuthoritiesConverter(roles);
        return c;
    }
}
