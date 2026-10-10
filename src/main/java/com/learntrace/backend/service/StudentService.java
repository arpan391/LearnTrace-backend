package com.learntrace.backend.service;

import com.learntrace.backend.entity.Student;
import com.learntrace.backend.repository.StudentRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class StudentService {

    private final StudentRepository studentRepository;

    public StudentService(StudentRepository studentRepository) {
        this.studentRepository = studentRepository;
    }

    @Transactional
    public Student register(Student student) {
        if (student == null) {
            throw new IllegalArgumentException("Student details are required");
        }

        if (student.getName() == null || student.getName().isBlank()) {
            throw new IllegalArgumentException("Name is required");
        }

        if (student.getEmail() == null || student.getEmail().isBlank()) {
            throw new IllegalArgumentException("Email is required");
        }

        if (student.getPassword() == null || student.getPassword().isBlank()) {
            throw new IllegalArgumentException("Password is required");
        }

        student.setName(student.getName().trim());
        student.setEmail(student.getEmail().trim());

        if (studentRepository.findByEmail(student.getEmail()) != null) {
            throw new IllegalArgumentException("An account with this email already exists");
        }

        return studentRepository.save(student);
    }

    @Transactional(readOnly = true)
    public Student findByEmail(String email) {
        if (email == null || email.isBlank()) {
            return null;
        }

        return studentRepository.findByEmail(email.trim());
    }
}