package com.example.training.repository;

import com.example.training.domain.Attendance;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface AttendanceRepository extends JpaRepository<Attendance, Long> {

    List<Attendance> findBySessionId(Long sessionId);

    long countBySessionId(Long sessionId);

    /** 删除课次时，连同出勤一起删 */
    void deleteBySessionId(Long sessionId);
}
