package com.learntrace.backend.controller;

import com.learntrace.backend.entity.Question;
import com.learntrace.backend.entity.QuizAttempt;
import com.learntrace.backend.entity.Concept;
import com.learntrace.backend.repository.QuestionRepository;
import com.learntrace.backend.repository.QuizAttemptRepository;
import com.learntrace.backend.repository.ConceptRepository;

import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/quiz")
@CrossOrigin(origins = "http://localhost:5173")
public class QuizController {

    private final QuestionRepository questionRepository;
    private final QuizAttemptRepository attemptRepository;
    private final ConceptRepository conceptRepository;

    public QuizController(
            QuestionRepository questionRepository,
            QuizAttemptRepository attemptRepository,
            ConceptRepository conceptRepository) {

        this.questionRepository = questionRepository;
        this.attemptRepository = attemptRepository;
        this.conceptRepository = conceptRepository;
    }

    @PostMapping("/submit")
    public Map<String, Object> submitQuiz(
            @RequestBody Map<Long, String> answers,
            @RequestParam String subject,
            @RequestParam(required = false) String concept,
            @RequestParam(required = false, defaultValue = "MCQ") String questionType) {

        System.out.println("=================================");
        System.out.println("QUIZ SUBMISSION");
        System.out.println("SUBJECT: " + subject);
        System.out.println("CONCEPT: " + concept);
        System.out.println("QUESTION TYPE: " + questionType);
        System.out.println("=================================");

        List<Question> questions;

        if (concept == null || concept.isBlank()) {

            questions = questionRepository
                    .findBySubjectAndQuestionType(
                            subject,
                            questionType
                    );

        } else {

            questions = questionRepository
                    .findBySubjectAndConceptAndQuestionType(
                            subject,
                            concept,
                            questionType
                    );
        }

        int total = questions.size();
        int correct = 0;

        for (Question question : questions) {

            String selectedAnswer = answers.get(question.getId());

            if (selectedAnswer == null) {
                continue;
            }

            /*
             * Convert A/B/C/D into the actual option text.
             *
             * Example:
             * A -> Stack
             * B -> Queue
             * C -> Tree
             * D -> Graph
             */
            String selectedText = selectedAnswer;

            switch (selectedAnswer.toUpperCase()) {

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

            String correctAnswer = question.getCorrectAnswer();

            boolean isCorrect = false;

            if (correctAnswer != null) {

                /*
                 * Case 1:
                 * Database stores actual answer text.
                 *
                 * Example:
                 * correctAnswer = "Queue"
                 * selectedText = "Queue"
                 */
                if (selectedText.equalsIgnoreCase(correctAnswer.trim())) {
                    isCorrect = true;
                }

                /*
                 * Case 2:
                 * Database stores A/B/C/D.
                 *
                 * Example:
                 * correctAnswer = "B"
                 * selectedAnswer = "B"
                 */
                if (selectedAnswer.equalsIgnoreCase(correctAnswer.trim())) {
                    isCorrect = true;
                }
            }

            System.out.println(
                    "Question ID: " + question.getId()
                            + " | Selected: " + selectedAnswer
                            + " | Selected Text: " + selectedText
                            + " | Correct Answer: " + correctAnswer
                            + " | Result: " + (isCorrect ? "CORRECT" : "WRONG")
            );

            if (isCorrect) {
                correct++;
            }
        }

        int wrong = total - correct;

        double score = total == 0
                ? 0
                : correct * 100.0 / total;

        long finalScore = Math.round(score);

        QuizAttempt attempt = new QuizAttempt();

        attempt.setSubject(subject);

        attempt.setConcept(
                concept == null || concept.isBlank()
                        ? "Full Subject Test"
                        : concept
        );

        attempt.setTotalQuestions(total);
        attempt.setCorrectAnswers(correct);
        attempt.setWrongAnswers(wrong);
        attempt.setScore(finalScore);
        attempt.setAttemptedAt(LocalDateTime.now());

        attemptRepository.save(attempt);

        /*
         * Update concept score when a specific concept is being tested.
         */
        if (concept != null && !concept.isBlank()) {

            System.out.println(
                    "SEARCHING CONCEPT: [" +
                            concept +
                            "] SUBJECT: [" +
                            subject +
                            "]"
            );

            List<Concept> concepts =
                    conceptRepository.findByNameAndSubject(
                            concept.trim(),
                            subject.trim()
                    );

            System.out.println(
                    "CONCEPTS FOUND: " +
                            concepts.size()
            );

            if (!concepts.isEmpty()) {

                Concept selectedConcept = concepts.get(0);

                System.out.println(
                        "UPDATING " +
                                selectedConcept.getName() +
                                " SCORE FROM " +
                                selectedConcept.getScore() +
                                " TO " +
                                finalScore
                );

                selectedConcept.setScore(
                        (double) finalScore
                );

                conceptRepository.save(selectedConcept);

                System.out.println(
                        "CONCEPT SCORE UPDATED SUCCESSFULLY"
                );

            } else {

                System.out.println(
                        "CONCEPT NOT FOUND IN DATABASE"
                );
            }
        }

        return Map.of(
                "subject", subject,
                "concept", attempt.getConcept(),
                "questionType", questionType,
                "totalQuestions", total,
                "correctAnswers", correct,
                "wrongAnswers", wrong,
                "score", finalScore,
                "message",
                "Quiz result saved and concept score updated"
        );
    }
}