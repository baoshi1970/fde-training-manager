package com.example.training.service;

import com.example.training.domain.Attendance;
import com.example.training.domain.AttendanceStatus;
import com.example.training.domain.CourseSession;
import com.example.training.domain.Enrollment;
import com.example.training.domain.Trainee;
import com.example.training.repository.AttendanceRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** 出勤登记 */
@Service
@Transactional(readOnly = true)
public class AttendanceService {

    private final AttendanceRepository attendanceRepository;
    private final SessionService sessionService;
    private final EnrollmentService enrollmentService;

    public AttendanceService(AttendanceRepository attendanceRepository,
                             SessionService sessionService, EnrollmentService enrollmentService) {
        this.attendanceRepository = attendanceRepository;
        this.sessionService = sessionService;
        this.enrollmentService = enrollmentService;
    }

    /** 课次一览用：课次 ＋ 报名人数 ＋ 已记录出勤的人数 */
    public record SessionOverview(CourseSession session, long enrolled, long recorded) {
    }

    public List<SessionOverview> overview() {
        return sessionService.findAll(null).stream()
                .map(s -> new SessionOverview(s,
                        enrollmentService.countByCourse(s.getCourse().getId()),
                        attendanceRepository.countBySessionId(s.getId())))
                .toList();
    }

    /** 各状态的人数（没有人的状态为 0；「未记录」不在内，用报名人数减去已记录人数得到） */
    public Map<AttendanceStatus, Long> countByStatus(Long sessionId) {
        Map<AttendanceStatus, Long> counts = new java.util.EnumMap<>(AttendanceStatus.class);
        for (AttendanceStatus s : AttendanceStatus.values()) {
            counts.put(s, 0L);
        }
        for (Attendance a : attendanceRepository.findBySessionId(sessionId)) {
            counts.merge(a.getStatus(), 1L, Long::sum);
        }
        return counts;
    }

    /** 该课次所属课程的报名者（没报名的学员不会出现） */
    public List<Enrollment> findEnrollments(Long sessionId) {
        CourseSession session = sessionService.get(sessionId);
        return enrollmentService.findByCourse(session.getCourse().getId());
    }

    /** 已保存的出勤：学员 id → 状态。没有记录的学员不在 Map 里（＝未记录） */
    public Map<Long, AttendanceStatus> findStatuses(Long sessionId) {
        Map<Long, AttendanceStatus> result = new HashMap<>();
        for (Attendance a : attendanceRepository.findBySessionId(sessionId)) {
            result.put(a.getTrainee().getId(), a.getStatus());
        }
        return result;
    }

    /**
     * 保存一个课次的出勤。
     * - 状态为 null（未选）：不新增记录；已有记录则删除，恢复为「未记录」
     * - 已有记录：更新；没有：新增（同一课次・同一学员只有 1 条）
     * - 学员没有报名该课程：整体拒绝，一条都不保存
     *
     * @param statuses 学员 id → 状态（可为 null）
     */
    @Transactional
    public void save(Long sessionId, Map<Long, AttendanceStatus> statuses) {
        CourseSession session = sessionService.get(sessionId);
        Map<Long, Trainee> enrolled = new HashMap<>();
        for (Enrollment e : enrollmentService.findByCourse(session.getCourse().getId())) {
            enrolled.put(e.getTrainee().getId(), e.getTrainee());
        }
        for (Long traineeId : statuses.keySet()) {
            if (!enrolled.containsKey(traineeId)) {
                throw new BusinessException("学员（id=" + traineeId + "）没有报名「"
                        + session.getCourse().getDisplayName() + "」，不能登记出勤。请先在报名管理里报名");
            }
        }

        Map<Long, Attendance> existing = new HashMap<>();
        for (Attendance a : attendanceRepository.findBySessionId(sessionId)) {
            existing.put(a.getTrainee().getId(), a);
        }
        statuses.forEach((traineeId, status) -> {
            Attendance current = existing.get(traineeId);
            if (status == null) {
                if (current != null) {
                    attendanceRepository.delete(current);
                }
            } else if (current != null) {
                current.setStatus(status);
            } else {
                attendanceRepository.save(new Attendance(session, enrolled.get(traineeId), status));
            }
        });
    }
}
