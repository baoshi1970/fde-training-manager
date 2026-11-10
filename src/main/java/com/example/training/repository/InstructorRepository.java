package com.example.training.repository;

import com.example.training.domain.Instructor;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface InstructorRepository extends JpaRepository<Instructor, Long> {

    List<Instructor> findAllByOrderByIdAsc();

    boolean existsByEmail(String email);

    boolean existsByEmailAndIdNot(String email, Long id);
}
