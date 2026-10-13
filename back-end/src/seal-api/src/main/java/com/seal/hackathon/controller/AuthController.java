package com.seal.hackathon.controller;

import com.seal.hackathon.dto.request.LoginRequest;
import com.seal.hackathon.dto.request.ForgotPasswordRequest;
import com.seal.hackathon.dto.request.RegisterRequest;
import com.seal.hackathon.dto.request.ResetPasswordRequest;
import com.seal.hackathon.dto.request.VerifyResetOtpRequest;
import com.seal.hackathon.dto.response.ApiResponse;
import com.seal.hackathon.dto.response.AuthResponse;
import com.seal.hackathon.dto.response.ResetOtpResponse;
import com.seal.hackathon.dto.response.UserResponse;
import com.seal.hackathon.security.JwtCookieFactory;
import com.seal.hackathon.service.AuthService;
import com.seal.hackathon.service.PasswordResetService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseCookie;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.web.csrf.CsrfTokenRepository;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
public class AuthController {

    private final AuthService authService;
    private final PasswordResetService passwordResetService;
    private final JwtCookieFactory jwtCookieFactory;
    private final CsrfTokenRepository csrfTokenRepository;

    /**
     * POST /api/auth/register
     * Public. Creates a new account pending coordinator approval.
     */
    @PostMapping("/register")
    public ResponseEntity<ApiResponse<AuthResponse>> register(@Valid @RequestBody RegisterRequest request) {
        AuthResponse response = authService.register(request);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success("Registration successful. Please await approval.", response));
    }

    /**
     * POST /api/auth/login
     * Public. On success, sets the JWT as an HttpOnly cookie — the token is
     * never exposed to client-side JS.
     */
    @PostMapping("/login")
    public ResponseEntity<ApiResponse<AuthResponse>> login(
            @Valid @RequestBody LoginRequest request, HttpServletResponse httpResponse) {
        AuthResponse response = authService.login(request);

        ResponseCookie cookie = jwtCookieFactory.buildAuthCookie(response.getToken(), request.isRememberMe());
        httpResponse.addHeader(HttpHeaders.SET_COOKIE, cookie.toString());

        // Don't echo the token back in the JSON body — it now lives only in the
        // HttpOnly cookie. @JsonInclude(NON_NULL) drops both fields from the wire.
        response.setToken(null);
        response.setTokenType(null);

        return ResponseEntity.ok(ApiResponse.success("Login successful.", response));
    }

    /**
     * POST /api/auth/forgot-password
     * Public. Validates a local, active, approved account and sends a short-lived OTP.
     */
    @PostMapping("/forgot-password")
    public ResponseEntity<ApiResponse<?>> forgotPassword(@Valid @RequestBody ForgotPasswordRequest request) {
        passwordResetService.requestOtp(request);
        return ResponseEntity.ok(ApiResponse.success("OTP sent to your email."));
    }

    /**
     * POST /api/auth/verify-reset-otp
     * Public. Checks the email OTP and returns a temporary reset token when valid.
     */
    @PostMapping("/verify-reset-otp")
    public ResponseEntity<ApiResponse<ResetOtpResponse>> verifyResetOtp(
            @Valid @RequestBody VerifyResetOtpRequest request) {
        ResetOtpResponse response = passwordResetService.verifyOtp(request);
        return ResponseEntity.ok(ApiResponse.success("OTP verified successfully.", response));
    }

    /**
     * POST /api/auth/reset-password
     * Public. Uses the temporary reset token to update the local password.
     */
    @PostMapping("/reset-password")
    public ResponseEntity<ApiResponse<?>> resetPassword(@Valid @RequestBody ResetPasswordRequest request) {
        passwordResetService.resetPassword(request);
        return ResponseEntity.ok(ApiResponse.success("Password reset successful."));
    }

    /**
     * GET /api/auth/me
     * Requires valid JWT. Returns the current user's profile.
     */
    @GetMapping("/me")
    public ResponseEntity<ApiResponse<UserResponse>> getCurrentUser(Authentication authentication) {
        String email = ((UserDetails) authentication.getPrincipal()).getUsername();
        UserResponse user = authService.getCurrentUser(email);
        return ResponseEntity.ok(ApiResponse.success("User profile retrieved.", user));
    }

    /**
     * PUT /api/auth/complete-profile
     * First-time OAuth user picks account type + student details.
     */
    @PutMapping("/complete-profile")
    public ResponseEntity<ApiResponse<UserResponse>> completeProfile(
            @Valid @RequestBody com.seal.hackathon.dto.request.CompleteProfileRequest request,
            Authentication authentication) {
        String email = ((UserDetails) authentication.getPrincipal()).getUsername();
        return ResponseEntity.ok(ApiResponse.success("Profile completed.",
                authService.completeProfile(email, request)));
    }

    /**
     * PUT /api/auth/me
     * Requires valid JWT. The user patches their own profile fields.
     */
    @PutMapping("/me")
    public ResponseEntity<ApiResponse<UserResponse>> updateProfile(
            @Valid @RequestBody com.seal.hackathon.dto.request.UpdateProfileRequest request,
            Authentication authentication) {
        String email = ((UserDetails) authentication.getPrincipal()).getUsername();
        return ResponseEntity.ok(ApiResponse.success("Profile updated.",
                authService.updateOwnProfile(email, request)));
    }

    /**
     * PUT /api/auth/me/password
     * Requires valid JWT. Signed-in user changes their own password by proving
     * the current one. Distinct from the public OTP-based reset flow.
     */
    @PutMapping("/me/password")
    public ResponseEntity<ApiResponse<?>> changePassword(
            @Valid @RequestBody com.seal.hackathon.dto.request.ChangePasswordRequest request,
            Authentication authentication) {
        String email = ((UserDetails) authentication.getPrincipal()).getUsername();
        authService.changePassword(email, request);
        return ResponseEntity.ok(ApiResponse.success("Password changed successfully."));
    }

    /**
     * POST /api/auth/me/avatar
     * Requires valid JWT. Uploads a new profile picture (multipart "file").
     */
    @PostMapping(value = "/me/avatar", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<ApiResponse<UserResponse>> uploadAvatar(
            @RequestParam("file") MultipartFile file,
            Authentication authentication) {
        String email = ((UserDetails) authentication.getPrincipal()).getUsername();
        return ResponseEntity.ok(ApiResponse.success("Avatar updated.",
                authService.updateAvatar(email, file)));
    }

    /**
     * DELETE /api/auth/me/avatar
     * Requires valid JWT. Removes the current user's profile picture.
     */
    @DeleteMapping("/me/avatar")
    public ResponseEntity<ApiResponse<UserResponse>> removeAvatar(Authentication authentication) {
        String email = ((UserDetails) authentication.getPrincipal()).getUsername();
        return ResponseEntity.ok(ApiResponse.success("Avatar removed.",
                authService.removeAvatar(email)));
    }

    /**
     * GET /api/auth/check-student-id?id=SE123456
     * Public. Returns true if a student ID is already taken.
     */
    @GetMapping("/check-student-id")
    public ResponseEntity<ApiResponse<Boolean>> checkStudentId(@RequestParam String id) {
        return ResponseEntity.ok(ApiResponse.success("OK", authService.checkStudentId(id)));
    }

    /**
     * POST /api/auth/logout
     * Clears the auth cookie (and the CSRF cookie, reissued on the next request).
     */
    @PostMapping("/logout")
    public ResponseEntity<ApiResponse<?>> logout(HttpServletRequest request, HttpServletResponse response) {
        response.addHeader(HttpHeaders.SET_COOKIE, jwtCookieFactory.clearAuthCookie().toString());
        csrfTokenRepository.saveToken(null, request, response);
        return ResponseEntity.ok(ApiResponse.success("Logged out successfully."));
    }
}
