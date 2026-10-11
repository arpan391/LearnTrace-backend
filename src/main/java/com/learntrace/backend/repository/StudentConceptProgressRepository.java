package com.learntrace.backend.repository;

import com.learntrace.backend.entity.StudentConceptProgress;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface StudentConceptProgressRepository
        extends JpaRepository<StudentConceptProgress, Long> {

    List<StudentConceptProgress> findByStudent_Id(Long studentId);

    Optional<StudentConceptProgress> findByStudent_IdAndConcept_Id(
            Long studentId,
            Long conceptId
    );
}