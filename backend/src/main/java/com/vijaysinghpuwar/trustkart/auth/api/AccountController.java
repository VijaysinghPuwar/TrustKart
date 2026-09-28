package com.vijaysinghpuwar.trustkart.auth.api;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.vijaysinghpuwar.trustkart.auth.application.AccountService;
import com.vijaysinghpuwar.trustkart.common.error.ApiException;
import com.vijaysinghpuwar.trustkart.common.error.ErrorCode;
import com.vijaysinghpuwar.trustkart.security.AuthenticatedUser;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.util.List;
import java.util.UUID;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/me")
@Tag(name = "Account")
class AccountController {

    @JsonInclude(JsonInclude.Include.NON_NULL)
    record Me(boolean authenticated, AccountService.Profile profile) {}

    record ChangePasswordRequest(@NotBlank @Size(max = 128) String currentPassword, @NotBlank @Size(max = 128) String newPassword) {}

    record Revoked(int count) {}

    private final AccountService accounts;

    AccountController(AccountService accounts) {
        this.accounts = accounts;
    }

    /** Public: lets the SPA ask "am I signed in?" without triggering a 401. */
    @GetMapping
    Me me() {
        return AuthenticatedUser.current().map(u -> new Me(true, accounts.profile(u))).orElse(new Me(false, null));
    }

    @GetMapping("/sessions")
    List<AccountService.SessionView> sessions() {
        return accounts.sessions(requireUser());
    }

    @DeleteMapping("/sessions/{id}")
    ResponseEntity<Void> revoke(@PathVariable UUID id) {
        accounts.revokeSession(requireUser(), id);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/sessions/revoke-others")
    Revoked revokeOthers() {
        return new Revoked(accounts.revokeOtherSessions(requireUser()));
    }

    @GetMapping("/login-events")
    List<AccountService.LoginEventView> loginEvents() {
        return accounts.loginHistory(requireUser());
    }

    @PostMapping("/password")
    ResponseEntity<Void> changePassword(@Valid @RequestBody ChangePasswordRequest body) {
        accounts.changePassword(requireUser(), body.currentPassword(), body.newPassword());
        return ResponseEntity.noContent().build();
    }

    private static AuthenticatedUser requireUser() {
        return AuthenticatedUser.current().orElseThrow(() -> new ApiException(ErrorCode.UNAUTHENTICATED));
    }
}
