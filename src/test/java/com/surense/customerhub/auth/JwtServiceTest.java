package com.surense.customerhub.auth;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import org.assertj.core.api.InstanceOfAssertFactories;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class JwtServiceTest {

    private final JwtProperties properties = new JwtProperties(
            "unit-test-secret-must-be-at-least-32-characters-long",
            "customer-support-hub-test",
            60L
    );
    private final JwtService jwtService = new JwtService(properties);

    @Test
    void issuedTokenIsSignedAndCarriesExpectedClaims() {
        JwtService.IssuedToken issued = jwtService.issueToken("alice", List.of("AGENT"));

        assertThat(issued.token()).isNotBlank();
        assertThat(issued.expiresInSeconds()).isEqualTo(3600L);

        Claims claims = Jwts.parser()
                .verifyWith(jwtService.signingKey())
                .build()
                .parseSignedClaims(issued.token())
                .getPayload();

        assertThat(claims.getSubject()).isEqualTo("alice");
        assertThat(claims.getIssuer()).isEqualTo("customer-support-hub-test");
        assertThat(claims.get("roles"))
                .asInstanceOf(InstanceOfAssertFactories.list(String.class))
                .containsExactly("AGENT");
        assertThat(claims.getExpiration()).isAfter(claims.getIssuedAt());
    }
}
