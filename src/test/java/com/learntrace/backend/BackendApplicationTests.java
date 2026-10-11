package com.learntrace.backend;

import com.learntrace.backend.controller.AuthController;
import com.learntrace.backend.controller.KnowledgeGapController;
import com.learntrace.backend.controller.QuestionController;
import com.learntrace.backend.controller.QuizController;
import com.learntrace.backend.controller.QuizHistoryController;
import com.learntrace.backend.service.SessionService;
import com.learntrace.backend.entity.Concept;
import com.learntrace.backend.entity.Question;
import com.learntrace.backend.entity.QuizAttempt;
import com.learntrace.backend.entity.Student;
import com.learntrace.backend.entity.StudentConceptProgress;
import com.learntrace.backend.repository.ConceptRepository;
import com.learntrace.backend.repository.QuestionRepository;
import com.learntrace.backend.repository.QuizAttemptRepository;
import com.learntrace.backend.repository.StudentConceptProgressRepository;
import com.learntrace.backend.repository.StudentRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.ResponseEntity;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.LocalDateTime;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
class BackendApplicationTests {

    @Autowired StudentRepository students;
    @Autowired ConceptRepository concepts;
    @Autowired StudentConceptProgressRepository progress;
    @Autowired QuizAttemptRepository attempts;
    @Autowired QuestionRepository questions;
    @Autowired KnowledgeGapController knowledge;
    @Autowired QuizHistoryController history;
    @Autowired QuizController quiz;
    @Autowired QuestionController questionController;
    @Autowired AuthController auth;
    @Autowired PasswordEncoder passwordEncoder;
    @Autowired SessionService sessionService;
    @Autowired JdbcTemplate jdbcTemplate;

    @BeforeEach
    void clearIsolatedDatabase() {
        progress.deleteAll();
        attempts.deleteAll();
        questions.deleteAll();
        concepts.deleteAll();
        students.deleteAll();
    }

    @Test
    void knowledgeAndHistoryAreScopedToAuthenticatedStudent() throws Exception {
        Student a = student("a@example.test", "token-a");
        Student b = student("b@example.test", "token-b");
        Concept concept = concept("Fractions", "Math");
        saveProgress(a, concept, 90);
        saveProgress(b, concept, 20);
        saveAttempt(a, "Math", "Fractions");
        saveAttempt(b, "Math", "Other concept");

        var aKnowledge = knowledge.getKnowledgeGaps("Bearer token-a");
        var bKnowledge = knowledge.getKnowledgeGaps("Bearer token-b");
        assertEquals(90.0, ((Number) ((Map<?, ?>) ((List<?>) aKnowledge.getBody()).get(0)).get("score")).doubleValue());
        assertEquals(20.0, ((Number) ((Map<?, ?>) ((List<?>) bKnowledge.getBody()).get(0)).get("score")).doubleValue());
        assertEquals(1, ((List<?>) history.getHistory("Bearer token-a", "Math").getBody()).size());
        assertEquals(1, ((List<?>) history.getHistory("Bearer token-a", null).getBody()).size());
        assertEquals(1, ((List<?>) history.getHistory("Bearer token-b", "Math").getBody()).size());
    }

    @Test
    void preservedLegacyConceptScoreIsNotPresentedAsPersonalProgress() {
        Student a = student("a@example.test", "token-a");
        Student b = student("b@example.test", "token-b");
        Concept concept = concept("Fractions", "Math");
        jdbcTemplate.update("UPDATE concept SET score = ? WHERE id = ?", 73.0, concept.getId());

        Concept reloaded = concepts.findById(concept.getId()).orElseThrow();
        assertEquals(73.0, reloaded.getLegacyScore());

        Map<?, ?> aResult = (Map<?, ?>) ((List<?>) knowledge.getKnowledgeGaps("Bearer token-a").getBody()).get(0);
        Map<?, ?> bResult = (Map<?, ?>) ((List<?>) knowledge.getKnowledgeGaps("Bearer token-b").getBody()).get(0);
        assertNull(aResult.get("score"));
        assertNull(bResult.get("score"));
        assertFalse(aResult.containsKey("legacyScore"));
        assertFalse(bResult.containsKey("legacyScore"));
        assertTrue(progress.findByStudent_Id(a.getId()).isEmpty());
        assertTrue(progress.findByStudent_Id(b.getId()).isEmpty());
    }

    @Test
    void confirmedLegacyRecoveryIsIdempotentPreservesNewerProgressAndStaysPrivate() {
        Student arpan = student(" Arpan@Example.com ", "token-a");
        Student another = student("another@example.test", "token-b");
        Concept legacyConcept = concept("Arrays", "Data Structures");
        Concept newerConcept = concept("Linked List", "Data Structures");
        jdbcTemplate.update("UPDATE concept SET score = ? WHERE id = ?", 100.0, legacyConcept.getId());
        jdbcTemplate.update("UPDATE concept SET score = ? WHERE id = ?", 100.0, newerConcept.getId());
        saveProgress(arpan, newerConcept, 96.0);

        QuizAttempt legacyAttempt = new QuizAttempt();
        legacyAttempt.setSubject("Data Structures");
        legacyAttempt.setConcept("Arrays");
        legacyAttempt.setTotalQuestions(2);
        legacyAttempt.setCorrectAnswers(1);
        legacyAttempt.setWrongAnswers(1);
        legacyAttempt.setScore(50);
        legacyAttempt.setAttemptedAt(LocalDateTime.of(2026, 10, 2, 20, 20, 18, 349_456_000));
        attempts.save(legacyAttempt);
        Long legacyAttemptId = legacyAttempt.getId();

        QuizAttempt anotherStudentAttempt = new QuizAttempt();
        anotherStudentAttempt.setStudent(another);
        anotherStudentAttempt.setSubject("Data Structures");
        anotherStudentAttempt.setConcept("Other student's quiz");
        anotherStudentAttempt.setAttemptedAt(LocalDateTime.now());
        attempts.save(anotherStudentAttempt);
        long originalAttemptCount = attempts.count();

        applyConfirmedRecoveryForTest("arpan@example.com", Set.of(legacyAttemptId));
        applyConfirmedRecoveryForTest("arpan@example.com", Set.of(legacyAttemptId));

        assertEquals(100.0, progress.findByStudent_IdAndConcept_Id(arpan.getId(), legacyConcept.getId()).orElseThrow().getScore());
        assertEquals(96.0, progress.findByStudent_IdAndConcept_Id(arpan.getId(), newerConcept.getId()).orElseThrow().getScore());
        assertEquals(2, progress.findByStudent_Id(arpan.getId()).size());
        assertEquals(1, attempts.findByStudent_Id(arpan.getId()).size());
        assertEquals(originalAttemptCount, attempts.count());
        QuizAttempt recovered = attempts.findById(legacyAttemptId).orElseThrow();
        assertEquals(arpan.getId(), recovered.getStudent().getId());
        assertEquals(2, recovered.getTotalQuestions());
        assertEquals(1, recovered.getCorrectAnswers());
        assertEquals(1, recovered.getWrongAnswers());
        assertEquals(50.0, recovered.getScore());
        assertEquals(LocalDateTime.of(2026, 10, 2, 20, 20, 18, 349_456_000), recovered.getAttemptedAt());

        Map<Long, Double> arpanScores = ((List<Map<String, Object>>) knowledge.getKnowledgeGaps("Bearer token-a").getBody())
                .stream().collect(java.util.stream.Collectors.toMap(row -> ((Number) row.get("id")).longValue(), row -> (Double) row.get("score"), (left, right) -> left));
        assertEquals(100.0, arpanScores.get(legacyConcept.getId()));
        assertEquals(96.0, arpanScores.get(newerConcept.getId()));
        assertEquals(1, ((List<?>) history.getHistory("Bearer token-a", null).getBody()).size());
        List<?> anotherHistory = (List<?>) history.getHistory("Bearer token-b", null).getBody();
        assertEquals(1, anotherHistory.size());
        assertNotEquals(legacyAttemptId, ((Map<?, ?>) anotherHistory.get(0)).get("id"));
        assertTrue(progress.findByStudent_Id(another.getId()).isEmpty());
    }

    @Test
    void submissionUpdatesOnlyAuthenticatedStudentsProgress() {
        Student a = student("a@example.test", "token-a");
        Student b = student("b@example.test", "token-b");
        Concept concept = concept("Fractions", "Math");
        Question question = new Question();
        question.setSubject("Math");
        question.setConcept("Fractions");
        question.setQuestionType("MCQ");
        question.setQuestionText("One half equals?");
        question.setOptionA("1/2");
        question.setOptionB("1/3");
        question.setCorrectAnswer("A");
        questions.save(question);

        Question wrongAnswerQuestion = new Question();
        wrongAnswerQuestion.setSubject("Math");
        wrongAnswerQuestion.setConcept("Fractions");
        wrongAnswerQuestion.setQuestionType("MCQ");
        wrongAnswerQuestion.setQuestionText("One third equals?");
        wrongAnswerQuestion.setOptionA("1/2");
        wrongAnswerQuestion.setOptionB("1/3");
        wrongAnswerQuestion.setCorrectAnswer("B");
        questions.save(wrongAnswerQuestion);

        Question otherTypeQuestion = new Question();
        otherTypeQuestion.setSubject("Math");
        otherTypeQuestion.setConcept("Fractions");
        otherTypeQuestion.setQuestionType("SCENARIO");
        otherTypeQuestion.setQuestionText("Scenario item");
        otherTypeQuestion.setCorrectAnswer("A");
        questions.save(otherTypeQuestion);

        ResponseEntity<?> result = quiz.submitQuiz("Bearer token-a", Map.of(
                question.getId(), "A",
                wrongAnswerQuestion.getId(), "A"), "Math", "Fractions", "MCQ");

        assertEquals(200, result.getStatusCode().value());
        Map<?, ?> body = (Map<?, ?>) result.getBody();
        assertEquals(2, body.get("totalQuestions"));
        assertEquals(1, body.get("correctAnswers"));
        assertEquals(1, body.get("wrongAnswers"));
        assertEquals(50L, body.get("score"));
        List<?> review = (List<?>) body.get("review");
        assertEquals(2, review.size());
        assertEquals("B", ((Map<?, ?>) review.get(1)).get("correctAnswer"));
        assertEquals(50.0, progress.findByStudent_IdAndConcept_Id(a.getId(), concept.getId()).orElseThrow().getScore());
        assertTrue(progress.findByStudent_Id(b.getId()).isEmpty());
        assertEquals(1, attempts.findByStudent_Id(a.getId()).size());
        assertTrue(attempts.findByStudent_Id(b.getId()).isEmpty());
        assertEquals(a.getId(), attempts.findByStudent_Id(a.getId()).get(0).getStudent().getId());
    }

    @Test
    void invalidAndExpiredTokensCannotReadProtectedAnalytics() {
        student("expired@example.test", "expired-token", LocalDateTime.now().minusMinutes(1));
        assertEquals(401, knowledge.getKnowledgeGaps(null).getStatusCode().value());
        assertEquals(401, knowledge.getKnowledgeGaps("Bearer unknown").getStatusCode().value());
        assertEquals(401, history.getHistory("Bearer expired-token", "Math").getStatusCode().value());
    }

    @Test
    void sharedQuestionFetchOmitsAnswerAndLegacyUnownedAttemptsRemainUnattributed() {
        Question question = new Question();
        question.setSubject("Math");
        question.setConcept("Fractions");
        question.setQuestionType("MCQ");
        question.setQuestionText("Question?");
        question.setCorrectAnswer("A");
        questions.save(question);
        Map<?, ?> publicQuestion = (Map<?, ?>) questionController.getQuestions("Math", null, null).get(0);
        assertFalse(publicQuestion.containsKey("correctAnswer"));

        Student a = student("a@example.test", "token-a");
        QuizAttempt legacy = new QuizAttempt();
        legacy.setSubject("Math");
        legacy.setConcept("Legacy");
        legacy.setAttemptedAt(LocalDateTime.now());
        attempts.save(legacy);
        assertEquals(1, attempts.count());
        assertNull(attempts.findById(legacy.getId()).orElseThrow().getStudent());
        assertTrue(attempts.findByStudent_Id(a.getId()).isEmpty());
        Student b = student("b@example.test", "token-b");
        assertTrue(attempts.findByStudent_Id(b.getId()).isEmpty());
        assertTrue(((List<?>) history.getHistory("Bearer token-a", "Math").getBody()).isEmpty());
        assertTrue(((List<?>) history.getHistory("Bearer token-b", "Math").getBody()).isEmpty());
    }

    @Test
    void legacyPlaintextPasswordCanLogInAndIsUpgradedToHash() {
        Student legacy = new Student();
        legacy.setName("Legacy");
        legacy.setEmail("Legacy@Example.test");
        legacy.setPassword("old-password");
        students.save(legacy);

        ResponseEntity<?> response = auth.login(Map.of("email", "LEGACY@example.test", "password", "old-password"));

        assertEquals(200, response.getStatusCode().value());
        Student upgraded = students.findById(legacy.getId()).orElseThrow();
        assertNotEquals("old-password", upgraded.getPassword());
        assertTrue(passwordEncoder.matches("old-password", upgraded.getPassword()));
        assertNotNull(upgraded.getSessionTokenHash());
    }

    @Test
    void normalizedEmailLookupPreventsCaseVariantRegistration() {
        student("learner@example.test", "token-a");
        ResponseEntity<?> response = auth.register(Map.of(
                "name", "Another Learner",
                "email", " LEARNER@EXAMPLE.TEST ",
                "password", "a-new-password"));
        assertEquals(409, response.getStatusCode().value());
        assertEquals(1, students.count());
    }

    @Test
    void registrationHashesPasswordsAndIgnoresProtectedClientFields() {
        ResponseEntity<?> response = auth.register(Map.of(
                "name", " New Learner ",
                "email", " Learner@Example.test ",
                "password", "safe-password",
                "id", "999",
                "sessionTokenHash", "attacker-controlled"));

        assertEquals(201, response.getStatusCode().value());
        Student created = students.findByEmail("learner@example.test");
        assertNotNull(created);
        assertNotEquals(999L, created.getId());
        assertNull(created.getSessionTokenHash());
        assertTrue(passwordEncoder.matches("safe-password", created.getPassword()));
        assertFalse(created.getPassword().equals("safe-password"));
        assertFalse(((Map<?, ?>) response.getBody()).containsKey("password"));
    }

    @Test
    void logoutInvalidatesTheCurrentBearerToken() {
        student("a@example.test", "token-a");
        assertEquals(204, auth.logout("Bearer token-a").getStatusCode().value());
        assertTrue(sessionService.authenticate("Bearer token-a").isEmpty());
    }

    @Test
    void missingOrExpiredTokenCannotSubmitQuiz() {
        assertEquals(401, quiz.submitQuiz(null, Map.of(), "Math", "Fractions", "MCQ").getStatusCode().value());
    }

    private Student student(String email, String token) {
        return student(email, token, LocalDateTime.now().plusDays(1));
    }

    private Student student(String email, String token, LocalDateTime expiresAt) {
        Student student = new Student();
        student.setName(email);
        student.setEmail(email);
        student.setPassword("unused");
        student.setSessionTokenHash(hash(token));
        student.setSessionExpiresAt(expiresAt);
        return students.save(student);
    }

    private Concept concept(String name, String subject) {
        Concept concept = new Concept();
        concept.setName(name);
        concept.setSubject(subject);
        return concepts.save(concept);
    }

    private void saveProgress(Student student, Concept concept, double score) {
        StudentConceptProgress record = new StudentConceptProgress();
        record.setStudent(student);
        record.setConcept(concept);
        record.setScore(score);
        record.setUpdatedAt(LocalDateTime.now());
        progress.save(record);
    }

    private void applyConfirmedRecoveryForTest(String email, Set<Long> backupAttemptIds) {
        List<Student> matches = students.findAll().stream()
                .filter(student -> student.getEmail() != null
                        && student.getEmail().trim().equalsIgnoreCase(email.trim()))
                .toList();
        if (matches.size() != 1) {
            throw new IllegalStateException("Expected exactly one existing recovery account");
        }
        Student owner = matches.get(0);

        for (Concept concept : concepts.findAll()) {
            if (concept.getLegacyScore() == null) continue;
            if (progress.findByStudent_IdAndConcept_Id(owner.getId(), concept.getId()).isPresent()) continue;
            saveProgress(owner, concept, concept.getLegacyScore());
        }

        for (QuizAttempt attempt : attempts.findAll()) {
            if (backupAttemptIds.contains(attempt.getId()) && attempt.getStudent() == null) {
                attempt.setStudent(owner);
                attempts.save(attempt);
            }
        }
    }

    private void saveAttempt(Student student, String subject, String concept) {
        QuizAttempt attempt = new QuizAttempt();
        attempt.setStudent(student);
        attempt.setSubject(subject);
        attempt.setConcept(concept);
        attempt.setAttemptedAt(LocalDateTime.now());
        attempts.save(attempt);
    }

    private String hash(String token) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(token.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }
}
