
package com.learntrace.backend.controller;

import com.learntrace.backend.entity.QuizAttempt;
import com.learntrace.backend.repository.QuizAttemptRepository;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/quiz/history")
@CrossOrigin(origins = "http://localhost:5173")
public class QuizHistoryController {

    private final QuizAttemptRepository attemptRepository;

    public QuizHistoryController(
            QuizAttemptRepository attemptRepository) {
        this.attemptRepository = attemptRepository;
    }

    @GetMapping
    public List<QuizAttempt> getHistory(
            @RequestParam String subject) {
        return attemptRepository.findBySubject(subject);
    }
}