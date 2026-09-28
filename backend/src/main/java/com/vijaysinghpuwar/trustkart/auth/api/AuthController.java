package com.vijaysinghpuwar.trustkart.auth.api;

import com.vijaysinghpuwar.trustkart.auth.application.AuthService;
import com.vijaysinghpuwar.trustkart.common.error.ApiException;
import com.vijaysinghpuwar.trustkart.common.error.ErrorCode;
import com.vijaysinghpuwar.trustkart.security.AuthCookies;
import com.vijaysinghpuwar.trustkart.security.AuthenticatedUser;
import com.vijaysinghpuwar.trustkart.security.ClientInfo;
import com.vijaysinghpuwar.trustkart.shopper.ShopperService;
import io.swagger.v3.oas.annotations.Operation;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.security.oauth2.client.registration.ClientRegistrationRepository;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/auth")
@Tag(name = "Auth", description = "Cookie-based sessions: short-lived access JWT plus rotating refresh token")
class AuthController {

    record RegisterRequest(
            @NotBlank @Email @Size(max = 254) String email,
            @NotBlank @Size(max = 128) String password,
            @NotBlank @Size(min = 1, max = 60) String displayName) {}

    record LoginRequest(@NotBlank @Size(max = 254) String email, @NotBlank @Size(max = 128) String password) {}

    record SignedIn(UUID id, String displayName) {}

    private final AuthService auth;
    private final ShopperService shoppers;
    private final AuthCookies cookies;
    private final boolean googleEnabled;

    AuthController(AuthService auth, ShopperService shoppers, AuthCookies cookies,
            ObjectProvider<ClientRegistrationRepository> registrations) {
        this.auth = auth;
        this.shoppers = shoppers;
        this.cookies = cookies;
        this.googleEnabled = registrations.getIfAvailable() != null;
    }

    record Providers(boolean google) {}

    @GetMapping("/providers")
    @Operation(summary = "Which external sign-in providers are configured in this environment")
    Providers providers() {
        return new Providers(googleEnabled);
    }

    @GetMapping("/csrf")
    @Operation(summary = "Ensures the XSRF-TOKEN cookie is set before the first state-changing request")
    ResponseEntity<Void> csrf() {
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/register")
    ResponseEntity<SignedIn> register(@Valid @RequestBody RegisterRequest body, HttpServletRequest request,
            HttpServletResponse response) {
        AuthService.Issued issued = auth.register(body.email(), body.password(), body.displayName(), ClientInfo.from(request));
        return signedIn(issued, HttpStatus.CREATED, request, response);
    }

    @PostMapping("/login")
    ResponseEntity<SignedIn> login(@Valid @RequestBody LoginRequest body, HttpServletRequest request,
            HttpServletResponse response) {
        AuthService.Issued issued = auth.login(body.email(), body.password(), ClientInfo.from(request));
        return signedIn(issued, HttpStatus.OK, request, response);
    }

    @PostMapping("/refresh")
    @Operation(summary = "Rotates the refresh token and issues a new access token")
    ResponseEntity<Void> refresh(HttpServletRequest request, HttpServletResponse response) {
        String raw = AuthCookies.read(request, AuthCookies.REFRESH).orElse(null);
        try {
            AuthService.Issued issued = auth.refresh(raw, ClientInfo.from(request));
            cookies.setSession(response, issued.accessToken(), issued.refreshToken());
            return ResponseEntity.noContent().build();
        } catch (ApiException e) {
            if (e.code() == ErrorCode.SESSION_EXPIRED) {
                cookies.clearSession(response);
            }
            throw e;
        }
    }

    @PostMapping("/logout")
    ResponseEntity<Void> logout(HttpServletRequest request, HttpServletResponse response) {
        UUID sid = AuthenticatedUser.current().map(AuthenticatedUser::sessionId).orElse(null);
        auth.logout(AuthCookies.read(request, AuthCookies.REFRESH).orElse(null), sid, ClientInfo.from(request));
        cookies.clearSession(response);
        return ResponseEntity.noContent().build();
    }

    private ResponseEntity<SignedIn> signedIn(AuthService.Issued issued, HttpStatus status, HttpServletRequest request,
            HttpServletResponse response) {
        shoppers.onSignIn(issued.user().getId(), request, response);
        cookies.setSession(response, issued.accessToken(), issued.refreshToken());
        return ResponseEntity.status(status).body(new SignedIn(issued.user().getPublicId(), issued.user().getDisplayName()));
    }
}
