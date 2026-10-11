package com.learntrace.backend.controller;

import com.learntrace.backend.entity.Concept;
import com.learntrace.backend.repository.ConceptRepository;
import org.springframework.web.bind.annotation.*;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/concepts")
public class ConceptController {

    private final ConceptRepository conceptRepository;

    public ConceptController(ConceptRepository conceptRepository) {
        this.conceptRepository = conceptRepository;
    }

    @GetMapping
    public List<Map<String, Object>> getAllConcepts() {
        return conceptRepository.findAll()
                .stream()
                .map(concept -> {
                    Map<String, Object> result =
                            new LinkedHashMap<>();

                    result.put("id", concept.getId());
                    result.put("name", concept.getName());
                    result.put("subject", concept.getSubject());
                    result.put(
                            "prerequisite",
                            concept.getPrerequisite()
                    );

                    // Do not expose the shared global score.
                    return result;
                })
                .toList();
    }
}