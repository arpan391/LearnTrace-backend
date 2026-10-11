
package com.learntrace.backend.controller;

import com.learntrace.backend.entity.Concept;
import com.learntrace.backend.entity.Question;
import com.learntrace.backend.entity.QuizAttempt;
import com.learntrace.backend.entity.Student;
import com.learntrace.backend.entity.StudentConceptProgress;
import com.learntrace.backend.repository.ConceptRepository;
import com.learntrace.backend.repository.QuestionRepository;
import com.learntrace.backend.repository.QuizAttemptRepository;
import com.learntrace.backend.repository.StudentConceptProgressRepository;
import com.learntrace.backend.service.SessionService;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@RestController
@RequestMapping("/quiz")
public class QuizController {

    private final QuestionRepository questionRepository;
    private final QuizAttemptRepository attemptRepository;
    private final ConceptRepository conceptRepository;
    private final StudentConceptProgressRepository progressRepository;
    private final SessionService sessionService;

    public QuizController(
            QuestionRepository questionRepository,
            QuizAttemptRepository attemptRepository,
            ConceptRepository conceptRepository,
            StudentConceptProgressRepository progressRepository,
            SessionService sessionService) {

        this.questionRepository = questionRepository;
        this.attemptRepository = attemptRepository;
        this.conceptRepository = conceptRepository;
        this.progressRepository = progressRepository;
        this.sessionService = sessionService;
    }

    @PostMapping("/submit")
    @Transactional
    public ResponseEntity<?> submitQuiz(
            @RequestHeader(value = "Authorization", required = false)
            String authorizationHeader,
            @RequestBody Map<Long, String> answers,
            @RequestParam String subject,
            @RequestParam(required = false) String concept,
            @RequestParam(required = false, defaultValue = "MCQ")
            String questionType) {

        Optional<Student> authenticatedStudent =
                sessionService.authenticate(authorizationHeader);

        if (authenticatedStudent.isEmpty()) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(Map.of(
                            "message",
                            "Your session is invalid or expired. Please log in again."
                    ));
        }

        Student student = authenticatedStudent.get();

        List<Question> questions;

        if (concept == null || concept.isBlank()) {
            questions = questionRepository.findBySubjectAndQuestionType(
                    subject, questionType
            );
        } else {
            questions = questionRepository
                    .findBySubjectAndConceptAndQuestionType(
                            subject, concept, questionType
                    );
        }

        if (questions.isEmpty()) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(Map.of(
                            "message",
                            "No questions were found for this assessment."
                    ));
        }

        int total = questions.size();
        int correct = 0;

        List<Map<String, Object>> review = new ArrayList<>();

        for (Question question : questions) {

            String selectedAnswer = answers.get(question.getId());
            String selectedText = selectedAnswer;

            if (selectedAnswer != null) {
                switch (selectedAnswer.trim().toUpperCase()) {
                    case "A":
                        selectedText = question.getOptionA();
                        break;
                    case "B":
                        selectedText = question.getOptionB();
                        break;
                    case "C":
                        selectedText = question.getOptionC();
                        break;
                    case "D":
                        selectedText = question.getOptionD();
                        break;
                    default:
                        selectedText = selectedAnswer;
                }
            }

            String correctAnswer = question.getCorrectAnswer();
            boolean isCorrect = false;

            if (selectedAnswer != null && correctAnswer != null) {
                String expected = correctAnswer.trim();

                isCorrect = selectedAnswer.trim()
                        .equalsIgnoreCase(expected)
                        || (selectedText != null
                        && selectedText.trim().equalsIgnoreCase(expected));
            }

            if (isCorrect) {
                correct++;
            }

            Map<String, Object> item = new LinkedHashMap<>();
            item.put("id", question.getId());
            item.put("questionText", question.getQuestionText());
            item.put("concept", question.getConcept());
            item.put("questionType", question.getQuestionType());
            item.put("optionA", question.getOptionA());
            item.put("optionB", question.getOptionB());
            item.put("optionC", question.getOptionC());
            item.put("optionD", question.getOptionD());
            item.put("selectedAnswer", selectedAnswer);
            item.put("selectedText", selectedText);
            item.put("correctAnswer", correctAnswer);
            item.put("isCorrect", isCorrect);

            review.add(item);
        }

        int wrong = total - correct;
        long finalScore = Math.round(correct * 100.0 / total);

        QuizAttempt attempt = new QuizAttempt();
        attempt.setStudent(student);
        attempt.setSubject(subject);
        attempt.setConcept(
                concept == null || concept.isBlank()
                        ? "Full Subject Test"
                        : concept.trim()
        );
        attempt.setTotalQuestions(total);
        attempt.setCorrectAnswers(correct);
        attempt.setWrongAnswers(wrong);
        attempt.setScore(finalScore);
        attempt.setAttemptedAt(LocalDateTime.now());

        // Save the attempt against the verified student.
        attemptRepository.save(attempt);

        // Keep concept progress separate for each student.
        if (concept != null && !concept.isBlank()) {

            List<Concept> matchingConcepts =
                    conceptRepository.findByNameAndSubject(
                            concept.trim(), subject.trim()
                    );

            if (!matchingConcepts.isEmpty()) {

                Concept selectedConcept = matchingConcepts.get(0);

                StudentConceptProgress progress =
                        progressRepository
                                .findByStudent_IdAndConcept_Id(
                                        student.getId(),
                                        selectedConcept.getId()
                                )
                                .orElseGet(StudentConceptProgress::new);

                progress.setStudent(student);
                progress.setConcept(selectedConcept);
                progress.setScore((double) finalScore);
                progress.setUpdatedAt(LocalDateTime.now());

                progressRepository.save(progress);
            }
        }

        Map<String, Object> response = new LinkedHashMap<>();
        response.put("subject", subject);
        response.put("concept", attempt.getConcept());
        response.put("questionType", questionType);
        response.put("totalQuestions", total);
        response.put("correctAnswers", correct);
        response.put("wrongAnswers", wrong);
        response.put("score", finalScore);
        response.put("review", review);
        response.put("message", "Assessment result saved successfully");

        return ResponseEntity.ok(response);
    }
}
