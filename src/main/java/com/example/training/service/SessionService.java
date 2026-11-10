package com.example.training.service;

import com.example.training.domain.CourseSession;
import com.example.training.repository.CourseSessionRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.temporal.TemporalAdjusters;
import java.util.List;

/** 课次排期 */
@Service
@Transactional(readOnly = true)
public class SessionService {

    private final CourseSessionRepository sessionRepository;
    private final CourseService courseService;
    private final InstructorService instructorService;

    public SessionService(CourseSessionRepository sessionRepository,
                          CourseService courseService, InstructorService instructorService) {
        this.sessionRepository = sessionRepository;
        this.courseService = courseService;
        this.instructorService = instructorService;
    }

    /** @param courseId 指定则只列该课程的课次 */
    public List<CourseSession> findAll(Long courseId) {
        if (courseId == null) {
            return sessionRepository.findAllByOrderByDateAscStartTimeAsc();
        }
        return sessionRepository.findByCourseIdOrderByDateAscStartTimeAsc(courseId);
    }

    public CourseSession get(Long id) {
        return sessionRepository.findById(id).orElseThrow(() -> new NotFoundException("课次", id));
    }

    @Transactional
    public CourseSession create(CourseSession session) {
        validate(session);
        session.setId(null);
        // 画面只传 id 过来，这里换成实体
        session.setCourse(courseService.get(session.getCourse().getId()));
        session.setInstructor(instructorService.get(session.getInstructor().getId()));
        return sessionRepository.save(session);
    }

    @Transactional
    public CourseSession update(Long id, CourseSession input) {
        CourseSession session = get(id);
        validate(input);
        session.setCourse(courseService.get(input.getCourse().getId()));
        session.setInstructor(instructorService.get(input.getInstructor().getId()));
        session.setDate(input.getDate());
        session.setStartTime(input.getStartTime());
        session.setEndTime(input.getEndTime());
        session.setMode(input.getMode());
        session.setLocation(input.getLocation());
        return session;
    }

    @Transactional
    public void delete(Long id) {
        sessionRepository.delete(get(id));
    }

    /** 主页用：今天以后的课次，最多 limit 件 */
    public List<CourseSession> upcoming(LocalDate today, int limit) {
        return sessionRepository.findByDateGreaterThanEqualOrderByDateAscStartTimeAsc(today)
                .stream().limit(limit).toList();
    }

    /** 主页用：本周（周一〜周日）的课次数 */
    public long countThisWeek(LocalDate today) {
        LocalDate monday = today.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY));
        return sessionRepository.countByDateBetween(monday, monday.plusDays(6));
    }

    private void validate(CourseSession s) {
        if (!s.getEndTime().isAfter(s.getStartTime())) {
            throw new BusinessException("结束时间必须晚于开始时间");
        }
        // TODO(#7) 同一讲师同一时段不能排两场：排课冲突检查
    }
}
