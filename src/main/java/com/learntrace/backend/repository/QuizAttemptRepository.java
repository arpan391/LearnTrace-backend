package com.learntrace.backend.repository;

import com.learntrace.backend.entity.QuizAttempt;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface QuizAttemptRepository
        extends JpaRepository<QuizAttempt, Long> {

    List<QuizAttempt> findByStudent_Id(Long studentId);

    List<QuizAttempt> findByStudent_IdOrderByAttemptedAtDesc(Long studentId);

    List<QuizAttempt> findByStudent_IdAndSubject(
            Long studentId,
            String subject
    );

    List<QuizAttempt> findByStudent_IdAndSubjectAndConcept(
            Long studentId,
            String subject,
            String concept
    );
}
