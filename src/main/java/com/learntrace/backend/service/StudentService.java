
package com.learntrace.backend.service;

import com.learntrace.backend.entity.Student;
import com.learntrace.backend.repository.StudentRepository;
import org.springframework.stereotype.Service;

@Service
public class StudentService {

    private final StudentRepository studentRepository;

    public StudentService(StudentRepository studentRepository) {
        this.studentRepository = studentRepository;
    }

    public Student register(Student student) {
        return studentRepository.save(student);
    }

    public Student findByEmail(String email) {
        return studentRepository.findByEmail(email);
    }
}