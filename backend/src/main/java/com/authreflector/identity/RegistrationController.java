package com.authreflector.identity;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import com.authreflector.security.SessionLogin;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;

// Ported from SplitMate RegistrationController (SPLIT-10); signs the new user in (AUTH-12)
@RestController
@RequestMapping("/api/v1/auth")
public class RegistrationController {

    public record SignUpResponse(String name, String email, String redirectUrl) {
    }

    private final RegistrationService registrationService;
    private final SessionLogin sessionLogin;

    public RegistrationController(RegistrationService registrationService, SessionLogin sessionLogin) {
        this.registrationService = registrationService;
        this.sessionLogin = sessionLogin;
    }

    @PostMapping("/register")
    @ResponseStatus(HttpStatus.CREATED)
    public SignUpResponse register(
            @Valid @RequestBody RegistrationRequest request,
            HttpServletRequest httpRequest,
            HttpServletResponse httpResponse) {

        RegistrationResponse account = registrationService.register(request);

        // Decision: signed in automatically. Uses the normal password check, so it can only
        // sign in the account that was just created with this exact password.
        String redirectUrl = sessionLogin.signIn(account.email(), request.password(), httpRequest, httpResponse);

        return new SignUpResponse(account.name(), account.email(), redirectUrl);
    }
}
