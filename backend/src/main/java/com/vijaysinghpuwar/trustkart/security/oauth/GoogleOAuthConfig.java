package com.vijaysinghpuwar.trustkart.security.oauth;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnExpression;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.oauth2.client.CommonOAuth2Provider;
import org.springframework.security.oauth2.client.registration.ClientRegistrationRepository;
import org.springframework.security.oauth2.client.registration.InMemoryClientRegistrationRepository;

/**
 * Registers Google as an OpenID Connect provider only when both credentials are configured. Without them the
 * app runs normally and the sign-in page simply doesn't offer Google.
 */
@Configuration(proxyBeanMethods = false)
@ConditionalOnExpression("!'${trustkart.oauth.google.client-id:}'.isBlank() and !'${trustkart.oauth.google.client-secret:}'.isBlank()")
class GoogleOAuthConfig {

    @Bean
    ClientRegistrationRepository clientRegistrationRepository(
            @Value("${trustkart.oauth.google.client-id}") String clientId,
            @Value("${trustkart.oauth.google.client-secret}") String clientSecret,
            @Value("${trustkart.public-origin:}") String publicOrigin) {
        // Behind a hosting proxy (e.g. a Vercel rewrite) the backend sees its own host, not the one the browser used,
        // so the callback must be pinned to the public origin; locally {baseUrl} resolves correctly on its own.
        String base = publicOrigin.isBlank() ? "{baseUrl}" : publicOrigin.replaceAll("/+$", "");
        return new InMemoryClientRegistrationRepository(CommonOAuth2Provider.GOOGLE.getBuilder("google")
                .clientId(clientId)
                .clientSecret(clientSecret)
                .redirectUri(base + "/api/v1/auth/oauth2/callback/{registrationId}")
                .scope("openid", "profile", "email")
                .build());
    }
}
