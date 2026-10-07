package com.learntrace.backend.repository;

import com.learntrace.backend.entity.Question;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface QuestionRepository extends JpaRepository<Question, Long> {

    List<Question> findBySubject(String subject);

    List<Question> findBySubjectAndConcept(String subject, String concept);

    List<Question> findBySubjectAndQuestionType(String subject, String questionType);

    List<Question> findBySubjectAndConceptAndQuestionType(
            String subject,
            String concept,
            String questionType
    );
}