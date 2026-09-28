package com.vijaysinghpuwar.trustkart.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.vijaysinghpuwar.trustkart.auth.application.AuthService;
import com.vijaysinghpuwar.trustkart.auth.application.FederatedLoginService;
import com.vijaysinghpuwar.trustkart.auth.application.FederatedLoginService.ExternalProfile;
import com.vijaysinghpuwar.trustkart.common.error.ApiException;
import com.vijaysinghpuwar.trustkart.common.error.ErrorCode;
import com.vijaysinghpuwar.trustkart.security.ClientInfo;
import com.vijaysinghpuwar.trustkart.security.oauth.OAuthHandlers;
import com.vijaysinghpuwar.trustkart.support.Browser;
import com.vijaysinghpuwar.trustkart.support.IntegrationTest;
import java.net.URI;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.client.authentication.OAuth2AuthenticationToken;
import org.springframework.security.oauth2.core.oidc.OidcIdToken;
import org.springframework.security.oauth2.core.oidc.user.DefaultOidcUser;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.util.UriComponentsBuilder;

/** Google sign-in logic and wiring, with identity claims stubbed; no request ever reaches Google. */
@IntegrationTest
@TestPropertySource(properties = {"trustkart.oauth.google.client-id=test-client-id", "trustkart.oauth.google.client-secret=test-secret"})
class GoogleSignInIT {

    static final ClientInfo CLIENT = new ClientInfo("203.0.113.5", "Mozilla/5.0 (Macintosh) Chrome/140.0");

    @Autowired
    MockMvc mvc;

    @Autowired
    FederatedLoginService federated;

    @Autowired
    OAuthHandlers handlers;

    @Autowired
    JdbcClient jdbc;

    static String email() {
        return "g-" + UUID.randomUUID() + "@gmail.test";
    }

    static ExternalProfile profile(String sub, String email, boolean verified) {
        return new ExternalProfile("google", sub, email, verified, "Maya Chen", "https://lh3.googleusercontent.test/a/photo");
    }

    long usersWithEmail(String email) {
        return jdbc.sql("SELECT count(*) FROM app_user WHERE email = :e").param("e", email).query(Long.class).single();
    }

    @Test
    void providersEndpointAdvertisesGoogleWhenConfigured() throws Exception {
        mvc.perform(get("/api/v1/auth/providers")).andExpect(jsonPath("$.google").value(true));
    }

    @Test
    void authorizationRedirectUsesOidcWithMinimalScopesStateNonceAndPkceWithoutAServerSession() throws Exception {
        var result = mvc.perform(get("/api/v1/auth/oauth2/authorization/google").header("Host", "localhost:5173"))
                .andExpect(status().is3xxRedirection()).andReturn();
        URI location = URI.create(result.getResponse().getHeader("Location"));
        Map<String, List<String>> q = UriComponentsBuilder.fromUri(location).build().getQueryParams();

        assertThat(location.getHost()).isEqualTo("accounts.google.com");
        assertThat(q.get("scope").getFirst()).isEqualTo("openid%20profile%20email");
        assertThat(q).containsKeys("state", "nonce", "code_challenge");
        // Built from the frontend origin (same-site cookies); this is the URI to register in Google Cloud Console.
        assertThat(q.get("redirect_uri").getFirst()).isEqualTo("http://localhost:5173/api/v1/auth/oauth2/callback/google");
        assertThat(result.getResponse().getHeaders("Set-Cookie")).anyMatch(c -> c.startsWith("tk_oauth=") && c.contains("HttpOnly"));
        assertThat(result.getRequest().getSession(false)).as("stateless: no HTTP session").isNull();
    }

    @Test
    void newGoogleUserGetsAnAccountAndIsRecognisedNextTime() {
        String email = email();
        AuthService.Issued first = federated.signIn(profile("sub-" + email, email, true), CLIENT);
        AuthService.Issued second = federated.signIn(profile("sub-" + email, email, true), CLIENT);

        assertThat(second.user().getId()).isEqualTo(first.user().getId());
        assertThat(first.user().isEmailVerified()).isTrue();
        assertThat(first.user().getAvatarUrl()).startsWith("https://");
        assertThat(usersWithEmail(email)).isEqualTo(1);
    }

    @Test
    void verifiedEmailLinksToTheExistingPasswordAccountInsteadOfDuplicating() throws Exception {
        String email = email();
        new Browser(mvc).post("/api/v1/auth/register", """
                {"email":"%s","password":"a long enough passphrase","displayName":"Maya"}""".formatted(email))
                .andExpect(status().isCreated());

        AuthService.Issued viaGoogle = federated.signIn(profile("sub-link-" + email, email, true), CLIENT);

        assertThat(usersWithEmail(email)).isEqualTo(1);
        assertThat(jdbc.sql("SELECT count(*) FROM user_identity WHERE user_id = :u").param("u", viaGoogle.user().getId())
                .query(Long.class).single()).isEqualTo(1);
    }

    @Test
    void unverifiedEmailNeverLinksToAnExistingAccount() throws Exception {
        String email = email();
        new Browser(mvc).post("/api/v1/auth/register", """
                {"email":"%s","password":"a long enough passphrase","displayName":"Maya"}""".formatted(email))
                .andExpect(status().isCreated());

        assertThatThrownBy(() -> federated.signIn(profile("sub-attacker", email, false), CLIENT))
                .isInstanceOf(ApiException.class)
                .extracting(e -> ((ApiException) e).code()).isEqualTo(ErrorCode.ACCOUNT_EXISTS);
        assertThat(jdbc.sql("SELECT count(*) FROM user_identity WHERE subject = 'sub-attacker'").query(Long.class).single()).isZero();
    }

    @Test
    void missingClaimsAreRejected() {
        assertThatThrownBy(() -> federated.signIn(profile("", email(), true), CLIENT)).isInstanceOf(ApiException.class);
        assertThatThrownBy(() -> federated.signIn(profile("sub-x", null, true), CLIENT)).isInstanceOf(ApiException.class);
    }

    @Test
    void nonHttpsAvatarIsDropped() {
        String email = email();
        AuthService.Issued issued = federated.signIn(new ExternalProfile("google", "sub-" + email, email, true, "M",
                "javascript:alert(1)"), CLIENT);
        assertThat(issued.user().getAvatarUrl()).isNull();
    }

    @Test
    void successHandlerIssuesTrustKartCookiesAndRedirectsIntoTheApp() throws Exception {
        String email = email();
        OidcIdToken idToken = new OidcIdToken("id-token", Instant.now(), Instant.now().plusSeconds(60),
                Map.of("sub", "sub-handler-" + email, "email", email, "email_verified", true, "name", "Maya Chen"));
        var principal = new DefaultOidcUser(List.of(new SimpleGrantedAuthority("OIDC_USER")), idToken);
        var auth = new OAuth2AuthenticationToken(principal, principal.getAuthorities(), "google");
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setRemoteAddr("203.0.113.9");
        MockHttpServletResponse response = new MockHttpServletResponse();

        handlers.onAuthenticationSuccess(request, response, auth);

        assertThat(response.getRedirectedUrl()).isEqualTo("/account?signedIn=google");
        assertThat(response.getHeaders("Set-Cookie")).anyMatch(c -> c.startsWith("tk_at=") && c.contains("HttpOnly"));
        assertThat(response.getHeaders("Set-Cookie")).anyMatch(c -> c.startsWith("tk_rt=") && c.contains("Path=/api/v1/auth"));
        // Google's own tokens never reach the browser.
        assertThat(String.join(";", response.getHeaders("Set-Cookie"))).doesNotContain("id-token");
    }
}
