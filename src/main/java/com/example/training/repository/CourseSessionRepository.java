package com.example.training.repository;

import com.example.training.domain.CourseSession;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDate;
import java.util.List;

public interface CourseSessionRepository extends JpaRepository<CourseSession, Long> {

    List<CourseSession> findAllByOrderByDateAscStartTimeAsc();

    List<CourseSession> findByCourseIdOrderByDateAscStartTimeAsc(Long courseId);

    /** 指定日期以后的课次（主页「近期课次」用） */
    List<CourseSession> findByDateGreaterThanEqualOrderByDateAscStartTimeAsc(LocalDate from);

    long countByDateBetween(LocalDate from, LocalDate to);

    boolean existsByCourseId(Long courseId);

    boolean existsByInstructorId(Long instructorId);

    /** 指定讲师在指定日期的课次（#7 排课冲突检查用） */
    List<CourseSession> findByInstructorIdAndDate(Long instructorId, LocalDate date);
}
