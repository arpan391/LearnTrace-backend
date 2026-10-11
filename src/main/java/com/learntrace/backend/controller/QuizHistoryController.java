
package com.learntrace.backend.controller;

import com.learntrace.backend.entity.QuizAttempt;
import com.learntrace.backend.entity.Student;
import com.learntrace.backend.repository.QuizAttemptRepository;
import com.learntrace.backend.service.SessionService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@RestController
@RequestMapping("/quiz/history")
public class QuizHistoryController {

    private final QuizAttemptRepository attemptRepository;
    private final SessionService sessionService;

    public QuizHistoryController(
            QuizAttemptRepository attemptRepository,
            SessionService sessionService) {
        this.attemptRepository = attemptRepository;
        this.sessionService = sessionService;
    }

    @GetMapping
    public ResponseEntity<?> getHistory(
            @RequestHeader(
                    value = "Authorization",
                    required = false
            ) String authorization,
            @RequestParam(required = false) String subject) {

        Optional<Student> authenticatedStudent =
                sessionService.authenticate(authorization);

        if (authenticatedStudent.isEmpty()) {
            return ResponseEntity
                    .status(HttpStatus.UNAUTHORIZED)
                    .body(Map.of(
                            "message",
                            "Please log in to view your quiz history."
                    ));
        }

        Long studentId =
                authenticatedStudent.get().getId();

        List<Map<String, Object>> history =
                (subject == null || subject.isBlank()
                        ? attemptRepository.findByStudent_IdOrderByAttemptedAtDesc(studentId)
                        : attemptRepository.findByStudent_IdAndSubject(studentId, subject))
                        .stream()
                        .map(attempt -> {
                            Map<String, Object> result =
                                    new HashMap<>();

                            result.put("id", attempt.getId());
                            result.put("subject", attempt.getSubject());
                            result.put("concept", attempt.getConcept());
                            result.put(
                                    "totalQuestions",
                                    attempt.getTotalQuestions()
                            );
                            result.put(
                                    "correctAnswers",
                                    attempt.getCorrectAnswers()
                            );
                            result.put(
                                    "wrongAnswers",
                                    attempt.getWrongAnswers()
                            );
                            result.put("score", attempt.getScore());
                            result.put(
                                    "attemptedAt",
                                    attempt.getAttemptedAt()
                            );

                            return result;
                        })
                        .toList();

        return ResponseEntity.ok(history);
    }
}
