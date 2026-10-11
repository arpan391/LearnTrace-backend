package com.learntrace.backend.service;

import com.learntrace.backend.entity.Student;
import com.learntrace.backend.repository.StudentRepository;

import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.LocalDateTime;
import java.util.HexFormat;
import java.util.Optional;

@Service
public class SessionService {

    private final StudentRepository studentRepository;

    public SessionService(StudentRepository studentRepository) {
        this.studentRepository = studentRepository;
    }

    public Optional<Student> authenticate(String authorizationHeader) {

        if (authorizationHeader == null
                || !authorizationHeader.startsWith("Bearer ")) {
            return Optional.empty();
        }

        String token = authorizationHeader.substring(7).trim();

        if (token.isEmpty()) {
            return Optional.empty();
        }

        try {
            String tokenHash = hashToken(token);

            Optional<Student> result =
                    studentRepository.findBySessionTokenHash(tokenHash);

            return result.filter(student ->
                    student.getSessionExpiresAt() != null
                    && student.getSessionExpiresAt()
                            .isAfter(LocalDateTime.now())
            );

        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(
                    "Unable to verify the login session.", e
            );
        }
    }

    @org.springframework.transaction.annotation.Transactional
    public boolean invalidate(String authorizationHeader) {
        Optional<Student> student = authenticate(authorizationHeader);
        if (student.isEmpty()) return false;
        Student account = student.get();
        account.setSessionTokenHash(null);
        account.setSessionExpiresAt(null);
        studentRepository.save(account);
        return true;
    }

    private String hashToken(String token)
            throws NoSuchAlgorithmException {

        MessageDigest digest = MessageDigest.getInstance("SHA-256");

        byte[] hash = digest.digest(
                token.getBytes(StandardCharsets.UTF_8)
        );

        return HexFormat.of().formatHex(hash);
    }
}
