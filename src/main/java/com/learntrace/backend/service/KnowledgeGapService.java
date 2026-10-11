
package com.learntrace.backend.service;

import com.learntrace.backend.entity.Concept;
import com.learntrace.backend.repository.ConceptRepository;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class KnowledgeGapService {

    private final ConceptRepository conceptRepository;

    public KnowledgeGapService(ConceptRepository conceptRepository) {
        this.conceptRepository = conceptRepository;
    }

    public List<Concept> getAllConcepts() {
        return conceptRepository.findAll();
    }

    public String classifyScore(Double score) {
        if (score == null) {
            return "Not Assessed";
        }

        if (score >= 80) {
            return "Strong";
        } else if (score >= 60) {
            return "Moderate";
        } else if (score >= 40) {
            return "Weak";
        } else {
            return "Critical";
        }
    }

}

