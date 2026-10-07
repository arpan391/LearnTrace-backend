package com.learntrace.backend.repository;

import com.learntrace.backend.entity.Concept;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface ConceptRepository extends JpaRepository<Concept, Long> {

    List<Concept> findByNameAndSubject(String name, String subject);
}