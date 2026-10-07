
package com.learntrace.backend.controller;

import com.learntrace.backend.entity.Student;
import com.learntrace.backend.service.StudentService;
import org.springframework.web.bind.annotation.*;

@CrossOrigin(origins = "http://localhost:5173")
@RestController
@RequestMapping("/auth")
public class AuthController {

    private final StudentService studentService;

    public AuthController(StudentService studentService) {
        this.studentService = studentService;
    }

    @GetMapping("/test")
    public String test() {
        return "Auth API is working!";
    }

    @PostMapping("/register")
    public Student register(@RequestBody Student student) {
        return studentService.register(student);
    }

    @PostMapping("/login")
    public String login(@RequestBody Student student) {

        Student existingStudent =
                studentService.findByEmail(student.getEmail());

        if (existingStudent == null) {
            return "User not found";
        }

        if (!existingStudent.getPassword().equals(student.getPassword())) {
            return "Invalid password";
        }

        return "Login successful for " + existingStudent.getName();
    }
}