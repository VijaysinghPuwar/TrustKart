package com.vijaysinghpuwar.trustkart.auth.application;

import com.vijaysinghpuwar.trustkart.auth.domain.AppUser;
import com.vijaysinghpuwar.trustkart.security.AuthProperties;
import com.vijaysinghpuwar.trustkart.security.JwtClaims;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.stereotype.Component;

/** Issues short-lived access tokens carrying the session id plus current roles and permissions. */
@Component
public class TokenIssuer {

    private final JwtEncoder encoder;
    private final AuthProperties props;

    public TokenIssuer(JwtEncoder encoder, AuthProperties props) {
        this.encoder = encoder;
        this.props = props;
    }

    public String accessToken(AppUser user, UUID sessionId, List<String> roles, List<String> permissions, Instant now) {
        JwtClaimsSet claims = JwtClaimsSet.builder()
                .issuer(JwtClaims.ISSUER)
                .audience(List.of(JwtClaims.AUDIENCE))
                .subject(user.getPublicId().toString())
                .id(UUID.randomUUID().toString())
                .issuedAt(now)
                .expiresAt(now.plus(props.accessTokenTtl()))
                .claim(JwtClaims.USER_ID, user.getId())
                .claim(JwtClaims.SESSION_ID, sessionId.toString())
                .claim(JwtClaims.NAME, user.getDisplayName())
                .claim(JwtClaims.ROLES, roles)
                .claim(JwtClaims.PERMISSIONS, permissions)
                .build();
        JwsHeader header = JwsHeader.with(MacAlgorithm.HS256).build();
        return encoder.encode(JwtEncoderParameters.from(header, claims)).getTokenValue();
    }
}
