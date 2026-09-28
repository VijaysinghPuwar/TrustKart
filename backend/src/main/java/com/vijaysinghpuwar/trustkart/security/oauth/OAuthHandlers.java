package com.vijaysinghpuwar.trustkart.security.oauth;

import com.vijaysinghpuwar.trustkart.auth.application.AuthService;
import com.vijaysinghpuwar.trustkart.auth.application.FederatedLoginService;
import com.vijaysinghpuwar.trustkart.common.error.ApiException;
import com.vijaysinghpuwar.trustkart.security.AuthCookies;
import com.vijaysinghpuwar.trustkart.security.ClientInfo;
import com.vijaysinghpuwar.trustkart.shopper.ShopperService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.core.oidc.user.OidcUser;
import org.springframework.security.web.authentication.AuthenticationFailureHandler;
import org.springframework.security.web.authentication.AuthenticationSuccessHandler;
import org.springframework.stereotype.Component;

/**
 * After Google verifies the user (Spring has already checked state, nonce and the ID token signature), this issues
 * TrustKart's own cookie session, merges the guest cart, and sends the browser back to the store. Google's tokens
 * are not kept.
 */
@Component
public class OAuthHandlers implements AuthenticationSuccessHandler, AuthenticationFailureHandler {

    private static final Logger log = LoggerFactory.getLogger(OAuthHandlers.class);

    private final FederatedLoginService federated;
    private final ShopperService shoppers;
    private final AuthCookies cookies;

    public OAuthHandlers(FederatedLoginService federated, ShopperService shoppers, AuthCookies cookies) {
        this.federated = federated;
        this.shoppers = shoppers;
        this.cookies = cookies;
    }

    @Override
    public void onAuthenticationSuccess(HttpServletRequest request, HttpServletResponse response, Authentication authentication)
            throws IOException {
        SecurityContextHolder.clearContext();
        if (!(authentication.getPrincipal() instanceof OidcUser oidc)) {
            response.sendRedirect("/signin?error=google");
            return;
        }
        try {
            AuthService.Issued issued = federated.signIn(new FederatedLoginService.ExternalProfile("google", oidc.getSubject(),
                    oidc.getEmail(), Boolean.TRUE.equals(oidc.getEmailVerified()), oidc.getFullName(), oidc.getPicture()),
                    ClientInfo.from(request));
            shoppers.onSignIn(issued.user().getId(), request, response);
            cookies.setSession(response, issued.accessToken(), issued.refreshToken());
            response.sendRedirect("/account?signedIn=google");
        } catch (ApiException e) {
            response.sendRedirect("/signin?error=" + (e.code().name().equals("ACCOUNT_EXISTS") ? "google-link" : "google"));
        }
    }

    @Override
    public void onAuthenticationFailure(HttpServletRequest request, HttpServletResponse response, AuthenticationException exception)
            throws IOException {
        log.info("Google sign-in failed: {}", exception.getClass().getSimpleName());
        response.sendRedirect("/signin?error=google");
    }
}
