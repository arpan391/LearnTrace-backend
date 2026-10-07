package com.learntrace.backend.controller;

import com.learntrace.backend.entity.Concept;
import com.learntrace.backend.service.KnowledgeGapService;
import org.springframework.web.bind.annotation.*;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@CrossOrigin(origins = "http://localhost:5173")
@RestController
@RequestMapping("/analysis")
public class KnowledgeGapController {

    private final KnowledgeGapService knowledgeGapService;

    public KnowledgeGapController(KnowledgeGapService knowledgeGapService) {
        this.knowledgeGapService = knowledgeGapService;
    }

    @GetMapping("/knowledge-gaps")
    public List<Map<String, Object>> getKnowledgeGaps() {

        List<Concept> concepts = knowledgeGapService.getAllConcepts();
        List<Map<String, Object>> results = new ArrayList<>();

        for (Concept concept : concepts) {

            Map<String, Object> item = new LinkedHashMap<>();

            item.put("id", concept.getId());
            item.put("name", concept.getName());
            item.put("subject", concept.getSubject());
            item.put("prerequisite", concept.getPrerequisite());
            item.put("score", concept.getScore());

            item.put(
                    "status",
                    knowledgeGapService.classifyScore(
                            concept.getScore()
                    )
            );

            results.add(item);
        }

        return results;
    }

    @GetMapping("/learning-path")
    public List<Map<String, Object>> getLearningPath() {

        List<Concept> learningPath =
                knowledgeGapService.getPersonalizedLearningPath();

        List<Map<String, Object>> results = new ArrayList<>();

        int step = 1;

        for (Concept concept : learningPath) {

            Map<String, Object> item = new LinkedHashMap<>();

            item.put("step", step++);
            item.put("id", concept.getId());
            item.put("name", concept.getName());
            item.put("subject", concept.getSubject());
            item.put("prerequisite", concept.getPrerequisite());
            item.put("score", concept.getScore());
            item.put(
                    "status",
                    knowledgeGapService.classifyScore(
                            concept.getScore()
                    )
            );

            results.add(item);
        }

        return results;
    }
}