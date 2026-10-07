
package com.learntrace.backend.controller;

import com.learntrace.backend.entity.Concept;
import com.learntrace.backend.repository.ConceptRepository;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@CrossOrigin(origins = "http://localhost:5173")
@RestController
@RequestMapping("/concepts")
public class ConceptController {

    private final ConceptRepository conceptRepository;

    public ConceptController(ConceptRepository conceptRepository) {
        this.conceptRepository = conceptRepository;
    }

    @GetMapping
    public List<Concept> getAllConcepts() {
        return conceptRepository.findAll();
    }

    @PostMapping
    public Concept addConcept(@RequestBody Concept concept) {
        return conceptRepository.save(concept);
    }
}