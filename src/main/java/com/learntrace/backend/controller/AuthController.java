package com.learntrace.backend.controller;

import com.learntrace.backend.entity.Student;
import com.learntrace.backend.service.StudentService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@CrossOrigin(origins = {
        "http://localhost:5173",
        "http://127.0.0.1:5173"
}, allowedHeaders = "*")
@RestController
@RequestMapping("/auth")
public class AuthController {

    private final StudentService studentService;

    public AuthController(StudentService studentService) {
        this.studentService = studentService;
    }

    @GetMapping("/test")
    public ResponseEntity<String> test() {
        return ResponseEntity.ok("Auth API is working!");
    }

    @PostMapping("/register")
    public ResponseEntity<?> register(@RequestBody Student student) {
        try {
            if (student.getName() == null || student.getName().isBlank()
                    || student.getEmail() == null || student.getEmail().isBlank()
                    || student.getPassword() == null || student.getPassword().isBlank()) {
                return ResponseEntity.badRequest()
                        .body(Map.of("message", "Name, email, and password are required"));
            }

            Student existingStudent =
                    studentService.findByEmail(student.getEmail().trim());

            if (existingStudent != null) {
                return ResponseEntity.status(HttpStatus.CONFLICT)
                        .body(Map.of("message", "An account with this email already exists"));
            }

            student.setEmail(student.getEmail().trim());

            Student savedStudent = studentService.register(student);

            return ResponseEntity.status(HttpStatus.CREATED).body(savedStudent);

        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("message", "Registration failed. Please try again."));
        }
    }

    @PostMapping("/login")
    public ResponseEntity<?> login(@RequestBody Student student) {
        try {
            if (student.getEmail() == null || student.getEmail().isBlank()
                    || student.getPassword() == null || student.getPassword().isBlank()) {
                return ResponseEntity.badRequest()
                        .body(Map.of("message", "Email and password are required"));
            }

            Student existingStudent =
                    studentService.findByEmail(student.getEmail().trim());

            if (existingStudent == null) {
                return ResponseEntity.status(HttpStatus.NOT_FOUND)
                        .body(Map.of("message",
                                "User not found. Please register with this email first."));
            }

            if (existingStudent.getPassword() == null
                    || !existingStudent.getPassword().equals(student.getPassword())) {
                return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                        .body(Map.of("message", "Invalid email or password"));
            }

            return ResponseEntity.ok(Map.of(
                    "message", "Login successful",
                    "name", existingStudent.getName(),
                    "email", existingStudent.getEmail()
            ));

        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("message", "Login failed. Please try again."));
        }
    }
}