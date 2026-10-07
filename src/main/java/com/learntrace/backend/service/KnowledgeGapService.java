
package com.learntrace.backend.service;

import com.learntrace.backend.entity.Concept;
import com.learntrace.backend.repository.ConceptRepository;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
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

    public List<Concept> getPersonalizedLearningPath() {

        List<Concept> concepts = conceptRepository.findAll();
        List<Concept> learningPath = new ArrayList<>();

        for (Concept concept : concepts) {

            Double score = concept.getScore();

            if (score != null && score < 60) {
                addPrerequisites(
                        concept,
                        concepts,
                        learningPath
                );
            }
        }

        return learningPath;
    }

    private void addPrerequisites(
            Concept concept,
            List<Concept> concepts,
            List<Concept> learningPath) {

        String prerequisite = concept.getPrerequisite();

        if (prerequisite != null && !prerequisite.isBlank()) {

            for (Concept parent : concepts) {

                if (parent.getName().equalsIgnoreCase(prerequisite)) {

                    addPrerequisites(
                            parent,
                            concepts,
                            learningPath
                    );

                    break;
                }
            }
        }

        if (!learningPath.contains(concept)) {
            learningPath.add(concept);
        }
    }
}

