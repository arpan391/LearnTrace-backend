
package com.learntrace.backend.repository;

import com.learntrace.backend.entity.QuizAttempt;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface QuizAttemptRepository
        extends JpaRepository<QuizAttempt, Long> {

    List<QuizAttempt> findBySubject(String subject);

    List<QuizAttempt> findBySubjectAndConcept(
            String subject, String concept);
}