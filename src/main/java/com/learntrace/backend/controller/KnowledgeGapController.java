
package com.learntrace.backend.controller;

import com.learntrace.backend.entity.Concept;
import com.learntrace.backend.entity.Student;
import com.learntrace.backend.entity.StudentConceptProgress;
import com.learntrace.backend.repository.ConceptRepository;
import com.learntrace.backend.repository.StudentConceptProgressRepository;
import com.learntrace.backend.service.KnowledgeGapService;
import com.learntrace.backend.service.SessionService;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

@RestController
@RequestMapping("/analysis")
public class KnowledgeGapController {

    private final ConceptRepository conceptRepository;
    private final StudentConceptProgressRepository progressRepository;
    private final KnowledgeGapService knowledgeGapService;
    private final SessionService sessionService;

    public KnowledgeGapController(
            ConceptRepository conceptRepository,
            StudentConceptProgressRepository progressRepository,
            KnowledgeGapService knowledgeGapService,
            SessionService sessionService) {

        this.conceptRepository = conceptRepository;
        this.progressRepository = progressRepository;
        this.knowledgeGapService = knowledgeGapService;
        this.sessionService = sessionService;
    }

    @GetMapping("/knowledge-gaps")
    public ResponseEntity<?> getKnowledgeGaps(
            @RequestHeader(value = "Authorization", required = false)
            String authorizationHeader) {

        Optional<Student> authenticatedStudent =
                sessionService.authenticate(authorizationHeader);

        if (authenticatedStudent.isEmpty()) {
            return unauthorized();
        }

        Student student = authenticatedStudent.get();

        List<Concept> concepts = conceptRepository.findAll();
        Map<Long, Double> scores = getStudentScores(student.getId());

        List<Map<String, Object>> results = new ArrayList<>();

        for (Concept concept : concepts) {
            Double score = scores.get(concept.getId());

            Map<String, Object> item = new LinkedHashMap<>();
            item.put("id", concept.getId());
            item.put("name", concept.getName());
            item.put("subject", concept.getSubject());
            item.put("prerequisite", concept.getPrerequisite());
            item.put("score", score);
            item.put(
                    "status",
                    knowledgeGapService.classifyScore(score)
            );

            results.add(item);
        }

        return ResponseEntity.ok(results);
    }

    @GetMapping("/learning-path")
    public ResponseEntity<?> getLearningPath(
            @RequestHeader(value = "Authorization", required = false)
            String authorizationHeader) {

        Optional<Student> authenticatedStudent =
                sessionService.authenticate(authorizationHeader);

        if (authenticatedStudent.isEmpty()) {
            return unauthorized();
        }

        Student student = authenticatedStudent.get();

        List<Concept> concepts = conceptRepository.findAll();
        Map<Long, Double> scores = getStudentScores(student.getId());

        Map<String, Concept> conceptsByName = new HashMap<>();

        for (Concept concept : concepts) {
            conceptsByName.put(
                    concept.getName().toLowerCase(),
                    concept
            );
        }

        Set<Long> added = new LinkedHashSet<>();
        Set<Long> visiting = new HashSet<>();
        List<Concept> learningPath = new ArrayList<>();

        for (Concept concept : concepts) {
            Double score = scores.get(concept.getId());

            if (score != null && score < 60) {
                addPrerequisites(
                        concept,
                        conceptsByName,
                        added,
                        visiting,
                        learningPath
                );
            }
        }

        List<Map<String, Object>> results = new ArrayList<>();
        int step = 1;

        for (Concept concept : learningPath) {
            Double score = scores.get(concept.getId());

            Map<String, Object> item = new LinkedHashMap<>();
            item.put("step", step++);
            item.put("id", concept.getId());
            item.put("name", concept.getName());
            item.put("subject", concept.getSubject());
            item.put("prerequisite", concept.getPrerequisite());
            item.put("score", score);
            item.put(
                    "status",
                    knowledgeGapService.classifyScore(score)
            );

            results.add(item);
        }

        return ResponseEntity.ok(results);
    }

    private Map<Long, Double> getStudentScores(Long studentId) {
        List<StudentConceptProgress> progressRecords =
                progressRepository.findByStudent_Id(studentId);

        Map<Long, Double> scores = new HashMap<>();

        for (StudentConceptProgress progress : progressRecords) {
            if (progress.getConcept() != null) {
                scores.put(
                        progress.getConcept().getId(),
                        progress.getScore()
                );
            }
        }

        return scores;
    }

    private void addPrerequisites(
            Concept concept,
            Map<String, Concept> conceptsByName,
            Set<Long> added,
            Set<Long> visiting,
            List<Concept> learningPath) {

        Long conceptId = concept.getId();

        if (added.contains(conceptId)
                || visiting.contains(conceptId)) {
            return;
        }

        visiting.add(conceptId);

        String prerequisite = concept.getPrerequisite();

        if (prerequisite != null && !prerequisite.isBlank()) {
            Concept parent = conceptsByName.get(
                    prerequisite.trim().toLowerCase()
            );

            if (parent != null) {
                addPrerequisites(
                        parent,
                        conceptsByName,
                        added,
                        visiting,
                        learningPath
                );
            }
        }

        visiting.remove(conceptId);

        if (added.add(conceptId)) {
            learningPath.add(concept);
        }
    }

    private ResponseEntity<Map<String, String>> unauthorized() {
        return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                .body(Map.of(
                        "message",
                        "Your session is invalid or expired. Please log in again."
                ));
    }
}
