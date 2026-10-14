package com.learntrace.backend.controller;

import com.learntrace.backend.entity.Student;
import com.learntrace.backend.repository.StudentRepository;
import com.learntrace.backend.service.StudentService;
import com.learntrace.backend.service.SessionService;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.bind.annotation.*;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.LocalDateTime;
import java.util.Base64;
import java.util.HexFormat;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Pattern;

@RestController
@RequestMapping("/auth")
public class AuthController {

    private static final Pattern EMAIL_PATTERN =
            Pattern.compile("^[^\\s@]+@[^\\s@]+\\.[^\\s@]+$");

    private final StudentService studentService;
    private final StudentRepository studentRepository;
    private final PasswordEncoder passwordEncoder;
    private final SessionService sessionService;

    private final SecureRandom secureRandom = new SecureRandom();

    public AuthController(
            StudentService studentService,
            StudentRepository studentRepository,
            PasswordEncoder passwordEncoder,
            SessionService sessionService) {

        this.studentService = studentService;
        this.studentRepository = studentRepository;
        this.passwordEncoder = passwordEncoder;
        this.sessionService = sessionService;
    }

    @GetMapping("/test")
    public ResponseEntity<String> test() {
        return ResponseEntity.ok("Auth API is working!");
    }

    // =====================================================
    // REGISTER
    // =====================================================

    @PostMapping("/register")
    public ResponseEntity<?> register(
            @RequestBody Map<String, String> request) {

        try {
            if (request == null) {
                return ResponseEntity.badRequest().body(
                        Map.of("message", "Registration details are required"));
            }

            String name = request.get("name");
            String requestEmail = request.get("email");
            String password = request.get("password");

            if (name == null || name.isBlank()
                    || requestEmail == null || requestEmail.isBlank()
                    || password == null || password.isBlank()) {

                return ResponseEntity.badRequest().body(
                        Map.of("message",
                                "Name, email, and password are required"));
            }

            String email =
                    requestEmail.trim().toLowerCase(Locale.ROOT);

            if (name.trim().length() > 255
                    || email.length() > 254
                    || !EMAIL_PATTERN.matcher(email).matches()) {

                return ResponseEntity.badRequest().body(
                        Map.of("message",
                                "Enter a valid name and email address"));
            }

            int passwordBytes =
                    password.getBytes(StandardCharsets.UTF_8).length;

            if (password.length() < 8 || passwordBytes > 72) {
                return ResponseEntity.badRequest().body(
                        Map.of("message",
                                "Password must be at least 8 characters "
                                        + "and no more than 72 UTF-8 bytes"));
            }

            if (studentService.findByEmail(email) != null) {
                return ResponseEntity.status(HttpStatus.CONFLICT).body(
                        Map.of("message",
                                "An account with this email already exists"));
            }

            Student student = new Student();
            student.setName(name.trim());
            student.setEmail(email);
            student.setPassword(password);

            Student savedStudent = studentService.register(student);

            return ResponseEntity.status(HttpStatus.CREATED).body(
                    Map.of(
                            "id", savedStudent.getId(),
                            "name", savedStudent.getName(),
                            "email", savedStudent.getEmail(),
                            "message", "Registration successful"
                    ));

        } catch (DataIntegrityViolationException e) {
            return ResponseEntity.status(HttpStatus.CONFLICT).body(
                    Map.of("message",
                            "An account with this email already exists"));

        } catch (Exception e) {
            return ResponseEntity.status(
                    HttpStatus.INTERNAL_SERVER_ERROR).body(
                    Map.of("message",
                            "Registration failed. Please try again."));
        }
    }

    // =====================================================
    // LOGIN
    // =====================================================

    @PostMapping("/login")
    public ResponseEntity<?> login(
            @RequestBody Map<String, String> request) {

        try {
            if (request == null) {
                return ResponseEntity.badRequest().body(
                        Map.of("message", "Login details are required"));
            }

            String requestEmail = request.get("email");
            String submittedPassword = request.get("password");

            if (requestEmail == null || requestEmail.isBlank()
                    || submittedPassword == null
                    || submittedPassword.isBlank()) {

                return ResponseEntity.badRequest().body(
                        Map.of("message",
                                "Email and password are required"));
            }

            String email =
                    requestEmail.trim().toLowerCase(Locale.ROOT);

            Student student = studentService.findByEmail(email);

            if (student == null || student.getPassword() == null) {
                return ResponseEntity.status(
                        HttpStatus.UNAUTHORIZED).body(
                        Map.of("message", "Invalid email or password"));
            }

            String storedPassword = student.getPassword();

            boolean passwordMatches =
                    verifyPassword(submittedPassword, storedPassword);

            if (!passwordMatches) {
                return ResponseEntity.status(
                        HttpStatus.UNAUTHORIZED).body(
                        Map.of("message", "Invalid email or password"));
            }

            // Upgrade a legacy plain-text password after successful login.
            if (!storedPassword.startsWith("{")) {
                student.setPassword(
                        passwordEncoder.encode(submittedPassword));
            }

            byte[] randomBytes = new byte[32];
            secureRandom.nextBytes(randomBytes);

            String rawToken = Base64.getUrlEncoder()
                    .withoutPadding()
                    .encodeToString(randomBytes);

            student.setSessionTokenHash(hashToken(rawToken));
            student.setSessionExpiresAt(
                    LocalDateTime.now().plusDays(7));

            studentRepository.save(student);

            return ResponseEntity.ok(
                    Map.of(
                            "id", student.getId(),
                            "name", student.getName(),
                            "email", student.getEmail(),
                            "token", rawToken,
                            "message", "Login successful"
                    ));

        } catch (NoSuchAlgorithmException e) {
            return ResponseEntity.status(
                    HttpStatus.INTERNAL_SERVER_ERROR).body(
                    Map.of("message",
                            "Could not create a login session."));

        } catch (Exception e) {
            return ResponseEntity.status(
                    HttpStatus.INTERNAL_SERVER_ERROR).body(
                    Map.of("message",
                            "Login failed. Please try again."));
        }
    }

    // =====================================================
    // CHANGE PASSWORD
    // =====================================================

    @PostMapping("/change-password")
    public ResponseEntity<?> changePassword(
            @RequestHeader(
                    value = "Authorization",
                    required = false)
            String authorization,

            @RequestBody(required = false)
            Map<String, String> request) {

        // 1. Identify the logged-in student using the existing session.
        Student student = sessionService.authenticate(authorization)
                .orElse(null);

        if (student == null) {
            return ResponseEntity.status(
                    HttpStatus.UNAUTHORIZED).body(
                    Map.of("message",
                            "Session expired. Please log in again."));
        }

        if (request == null) {
            return ResponseEntity.badRequest().body(
                    Map.of("message",
                            "Password details are required."));
        }

        String currentPassword = request.get("currentPassword");
        String newPassword = request.get("newPassword");
        String confirmPassword = request.get("confirmPassword");

        // 2. Validate the submitted fields.
        if (currentPassword == null || currentPassword.isBlank()
                || newPassword == null || newPassword.isBlank()
                || confirmPassword == null
                || confirmPassword.isBlank()) {

            return ResponseEntity.badRequest().body(
                    Map.of("message",
                            "All password fields are required."));
        }

        if (!newPassword.equals(confirmPassword)) {
            return ResponseEntity.badRequest().body(
                    Map.of("message",
                            "New passwords do not match."));
        }

        int passwordBytes =
                newPassword.getBytes(StandardCharsets.UTF_8).length;

        if (newPassword.length() < 8 || passwordBytes > 72) {
            return ResponseEntity.badRequest().body(
                    Map.of("message",
                            "New password must be at least 8 characters "
                                    + "and no more than 72 UTF-8 bytes."));
        }

        if (newPassword.equals(currentPassword)) {
            return ResponseEntity.badRequest().body(
                    Map.of("message",
                            "Choose a new password different "
                                    + "from your current password."));
        }

        // 3. Verify the current password.
        String storedPassword = student.getPassword();

        if (!verifyPassword(currentPassword, storedPassword)) {
            return ResponseEntity.status(
                    HttpStatus.UNAUTHORIZED).body(
                    Map.of("message",
                            "Current password is incorrect."));
        }

        try {
            // 4. Hash the new password using the existing encoder.
            student.setPassword(
                    passwordEncoder.encode(newPassword));

            studentRepository.save(student);

            return ResponseEntity.ok(
                    Map.of("message",
                            "Password changed successfully."));

        } catch (Exception e) {
            return ResponseEntity.status(
                    HttpStatus.INTERNAL_SERVER_ERROR).body(
                    Map.of("message",
                            "Password could not be changed. Please try again."));
        }
    }

    // =====================================================
    // LOGOUT
    // =====================================================

    @PostMapping("/logout")
    public ResponseEntity<?> logout(
            @RequestHeader(
                    value = "Authorization",
                    required = false)
            String authorization) {

        if (sessionService.invalidate(authorization)) {
            return ResponseEntity.noContent().build();
        }

        return ResponseEntity.status(
                HttpStatus.UNAUTHORIZED).body(
                Map.of("message",
                        "Your session is invalid or expired. "
                                + "Please log in again."));
    }

    // =====================================================
    // HELPER METHODS
    // =====================================================

    private boolean verifyPassword(
            String submittedPassword,
            String storedPassword) {

        if (submittedPassword == null || storedPassword == null) {
            return false;
        }

        // Spring's DelegatingPasswordEncoder format, e.g. {bcrypt}...
        if (storedPassword.startsWith("{")) {
            try {
                return passwordEncoder.matches(
                        submittedPassword, storedPassword);
            } catch (IllegalArgumentException e) {
                return false;
            }
        }

        // Compatibility with legacy plain-text records.
        return MessageDigest.isEqual(
                storedPassword.getBytes(StandardCharsets.UTF_8),
                submittedPassword.getBytes(StandardCharsets.UTF_8));
    }

    private String hashToken(String token)
            throws NoSuchAlgorithmException {

        MessageDigest digest =
                MessageDigest.getInstance("SHA-256");

        byte[] hash = digest.digest(
                token.getBytes(StandardCharsets.UTF_8));

        return HexFormat.of().formatHex(hash);
    }
}