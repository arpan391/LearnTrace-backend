package com.learntrace.backend.controller;

import com.learntrace.backend.entity.Question;
import com.learntrace.backend.repository.QuestionRepository;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/questions")
@CrossOrigin(origins = "http://localhost:5173")
public class QuestionController {

    private final QuestionRepository questionRepository;

    public QuestionController(QuestionRepository questionRepository) {
        this.questionRepository = questionRepository;
    }

    @GetMapping
    public List<Map<String, Object>> getQuestions(
            @RequestParam String subject,
            @RequestParam(required = false) String concept,
            @RequestParam(required = false) String questionType) {

        System.out.println("=================================");
        System.out.println("QUESTIONS REQUEST");
        System.out.println("SUBJECT: " + subject);
        System.out.println("CONCEPT: " + concept);
        System.out.println("QUESTION TYPE: " + questionType);
        System.out.println("=================================");

        List<Question> questions;

        if (concept != null
                && !concept.isBlank()
                && questionType != null
                && !questionType.isBlank()) {

            questions =
                    questionRepository
                            .findBySubjectAndConceptAndQuestionType(
                                    subject,
                                    concept,
                                    questionType
                            );

        } else if (concept != null
                && !concept.isBlank()) {

            questions =
                    questionRepository
                            .findBySubjectAndConcept(
                                    subject,
                                    concept
                            );

        } else if (questionType != null
                && !questionType.isBlank()) {

            questions =
                    questionRepository
                            .findBySubjectAndQuestionType(
                                    subject,
                                    questionType
                            );

        } else {

            questions =
                    questionRepository
                            .findBySubject(subject);
        }

        System.out.println(
                "QUESTIONS FOUND: " + questions.size()
        );

        return questions.stream()
                .map(q -> {

                    Map<String, Object> result =
                            new java.util.HashMap<>();

                    result.put("id", q.getId());
                    result.put("subject", q.getSubject());
                    result.put("concept", q.getConcept());
                    result.put(
                            "questionType",
                            q.getQuestionType()
                    );
                    result.put(
                            "questionText",
                            q.getQuestionText()
                    );
                    result.put(
                            "optionA",
                            q.getOptionA()
                    );
                    result.put(
                            "optionB",
                            q.getOptionB()
                    );
                    result.put(
                            "optionC",
                            q.getOptionC()
                    );
                    result.put(
                            "optionD",
                            q.getOptionD()
                    );

                    return result;
                })
                .toList();
    }
}