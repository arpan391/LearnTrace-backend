
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

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.LocalDateTime;
import java.util.Base64;
import java.util.HexFormat;
import java.util.Map;
import java.util.regex.Pattern;

@RestController
@RequestMapping("/auth")
public class AuthController {

    private static final Pattern EMAIL_PATTERN = Pattern.compile("^[^\\s@]+@[^\\s@]+\\.[^\\s@]+$");

    private final StudentService studentService;
    private final StudentRepository studentRepository;
    private final PasswordEncoder passwordEncoder;
    private final SecureRandom secureRandom = new SecureRandom();
    private final SessionService sessionService;

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

    @PostMapping("/register")
    public ResponseEntity<?> register(@RequestBody Map<String, String> request) {
        try {
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

            String email = requestEmail.trim().toLowerCase(java.util.Locale.ROOT);
            if (name.trim().length() > 255
                    || email.length() > 254
                    || !EMAIL_PATTERN.matcher(email).matches()) {
                return ResponseEntity.badRequest().body(
                        Map.of("message", "Enter a valid name and email address"));
            }
            int passwordBytes = password.getBytes(java.nio.charset.StandardCharsets.UTF_8).length;
            if (password.length() < 8 || passwordBytes > 72) {
                return ResponseEntity.badRequest().body(
                        Map.of("message", "Password must be at least 8 characters and no more than 72 UTF-8 bytes"));
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
                    Map.of("message", "An account with this email already exists"));
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(
                    Map.of("message",
                            "Registration failed. Please try again."));
        }
    }

    @PostMapping("/login")
    public ResponseEntity<?> login(@RequestBody Map<String, String> request) {
        try {
            String requestEmail = request.get("email");
            String submittedPassword = request.get("password");
            if (requestEmail == null || requestEmail.isBlank()
                    || submittedPassword == null || submittedPassword.isBlank()) {
                return ResponseEntity.badRequest().body(
                        Map.of("message", "Email and password are required"));
            }

            String email = requestEmail.trim().toLowerCase(java.util.Locale.ROOT);

            Student existingStudent = studentService.findByEmail(email);

            if (existingStudent == null
                    || existingStudent.getPassword() == null) {
                return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(
                        Map.of("message", "Invalid email or password"));
            }

            String storedPassword = existingStudent.getPassword();
            boolean passwordMatches;

            if (storedPassword.startsWith("{")) {
                try {
                    passwordMatches = passwordEncoder.matches(submittedPassword, storedPassword);
                } catch (IllegalArgumentException unsupportedPasswordHash) {
                    passwordMatches = false;
                }
            } else {
                // Compatibility path for an existing plain-text password.
                passwordMatches = MessageDigest.isEqual(
                        storedPassword.getBytes(java.nio.charset.StandardCharsets.UTF_8),
                        submittedPassword.getBytes(java.nio.charset.StandardCharsets.UTF_8));

                if (passwordMatches) {
                    // Upgrade it immediately after successful verification.
                    existingStudent.setPassword(
                            passwordEncoder.encode(submittedPassword));
                }
            }

            if (!passwordMatches) {
                return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(
                        Map.of("message", "Invalid email or password"));
            }

            byte[] randomBytes = new byte[32];
            secureRandom.nextBytes(randomBytes);

            String rawToken = Base64.getUrlEncoder()
                    .withoutPadding()
                    .encodeToString(randomBytes);

            existingStudent.setSessionTokenHash(hashToken(rawToken));
            existingStudent.setSessionExpiresAt(
                    LocalDateTime.now().plusDays(7));

            studentRepository.save(existingStudent);

            return ResponseEntity.ok(
                    Map.of(
                            "id", existingStudent.getId(),
                            "name", existingStudent.getName(),
                            "email", existingStudent.getEmail(),
                            "token", rawToken,
                            "message", "Login successful"
                    ));

        } catch (NoSuchAlgorithmException e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(
                    Map.of("message",
                            "Could not create a login session."));
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(
                    Map.of("message",
                            "Login failed. Please try again."));
        }
    }

    @PostMapping("/logout")
    public ResponseEntity<?> logout(@RequestHeader(value = "Authorization", required = false) String authorization) {
        if (sessionService.invalidate(authorization)) return ResponseEntity.noContent().build();
        return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(Map.of("message", "Your session is invalid or expired. Please log in again."));
    }

    private String hashToken(String token)
            throws NoSuchAlgorithmException {
        MessageDigest digest = MessageDigest.getInstance("SHA-256");
        byte[] hash = digest.digest(
                token.getBytes(java.nio.charset.StandardCharsets.UTF_8));

        return HexFormat.of().formatHex(hash);
    }
}
