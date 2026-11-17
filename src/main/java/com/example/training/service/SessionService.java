package com.example.training.service;

import com.example.training.domain.CourseSession;
import com.example.training.repository.AttendanceRepository;
import com.example.training.repository.CourseSessionRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.temporal.TemporalAdjusters;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;

/** 课次排期 */
@Service
@Transactional(readOnly = true)
public class SessionService {

    private static final DateTimeFormatter DATE = DateTimeFormatter.ofPattern("yyyy-MM-dd");
    private static final DateTimeFormatter TIME = DateTimeFormatter.ofPattern("HH:mm");

    private final CourseSessionRepository sessionRepository;
    private final CourseService courseService;
    private final InstructorService instructorService;
    private final AttendanceRepository attendanceRepository;

    public SessionService(CourseSessionRepository sessionRepository,
                          CourseService courseService, InstructorService instructorService,
                          AttendanceRepository attendanceRepository) {
        this.sessionRepository = sessionRepository;
        this.courseService = courseService;
        this.instructorService = instructorService;
        this.attendanceRepository = attendanceRepository;
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
        // 新增时没有「自己」：即使表单带了 id 也不排除
        validate(session, null);
        session.setId(null);
        // 画面只传 id 过来，这里换成实体
        session.setCourse(courseService.get(session.getCourse().getId()));
        session.setInstructor(instructorService.get(session.getInstructor().getId()));
        return sessionRepository.save(session);
    }

    @Transactional
    public CourseSession update(Long id, CourseSession input) {
        CourseSession session = get(id);
        // 修改时排除自己（画面传来的 input 不一定带 id，所以用路径上的 id）
        validate(input, id);
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
        CourseSession session = get(id);
        // 出勤是历史记录（F9 要用），不连带删除
        if (attendanceRepository.existsBySessionId(id)) {
            throw new BusinessException("该课次已有出勤记录，不能删除。请先在「出勤登记」里把该课次的出勤全部改为「未记录」并保存，再删除。");
        }
        sessionRepository.delete(session);
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

    /**
     * @param selfId 修改时是被修改课次的 id（冲突检查时排除自己），新增时为 null
     */
    private void validate(CourseSession s, Long selfId) {
        if (!s.getEndTime().isAfter(s.getStartTime())) {
            throw new BusinessException("结束时间必须晚于开始时间");
        }
        checkConflict(s, selfId);
    }

    /**
     * 同一讲师、同一天、时段重叠的课次不能排两场。
     * 两个时段 [s1, e1) 和 [s2, e2) 在 s1 < e2 且 s2 < e1 时重叠（严格小于，所以相接不算冲突）。
     * 有多场冲突时，只报开始最早的那一场。
     */
    private void checkConflict(CourseSession s, Long selfId) {
        sessionRepository.findByInstructorIdAndDate(s.getInstructor().getId(), s.getDate()).stream()
                .filter(other -> !Objects.equals(other.getId(), selfId))
                .filter(other -> s.getStartTime().isBefore(other.getEndTime())
                        && other.getStartTime().isBefore(s.getEndTime()))
                .min(Comparator.comparing(CourseSession::getStartTime))
                .ifPresent(conflict -> {
                    throw new BusinessException(conflictMessage(conflict));
                });
    }

    private static String conflictMessage(CourseSession c) {
        return "排课冲突：讲师「" + c.getInstructor().getName() + "」在 "
                + c.getDate().format(DATE) + " "
                + c.getStartTime().format(TIME) + "–" + c.getEndTime().format(TIME)
                + " 已有课次「" + c.getCourse().getName() + "」。请调整日期或时段，或者更换讲师。";
    }
}
