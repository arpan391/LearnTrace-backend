package com.learntrace.backend.repository;

import com.learntrace.backend.entity.Student;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface StudentRepository extends JpaRepository<Student, Long> {

    @Query("select s from Student s where lower(trim(s.email)) = :email")
    Student findByEmail(@Param("email") String email);

    Optional<Student> findBySessionTokenHash(String sessionTokenHash);
}
