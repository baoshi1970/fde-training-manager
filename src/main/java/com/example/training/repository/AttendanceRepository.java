package com.example.training.repository;

import com.example.training.domain.Attendance;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface AttendanceRepository extends JpaRepository<Attendance, Long> {

    List<Attendance> findBySessionId(Long sessionId);

    Optional<Attendance> findBySessionIdAndTraineeId(Long sessionId, Long traineeId);

    boolean existsBySessionId(Long sessionId);

    boolean existsByTraineeId(Long traineeId);
}
