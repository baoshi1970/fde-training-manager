package com.example.training.service;

import com.example.training.domain.Attendance;
import com.example.training.domain.AttendanceStatus;
import com.example.training.domain.CourseSession;
import com.example.training.domain.Enrollment;
import com.example.training.domain.Trainee;
import com.example.training.repository.AttendanceRepository;
import com.example.training.repository.EnrollmentRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

/** 出勤登记 */
@Service
@Transactional(readOnly = true)
public class AttendanceService {

    private final AttendanceRepository attendanceRepository;
    private final EnrollmentRepository enrollmentRepository;
    private final SessionService sessionService;
    private final TraineeService traineeService;

    public AttendanceService(AttendanceRepository attendanceRepository, EnrollmentRepository enrollmentRepository,
                             SessionService sessionService, TraineeService traineeService) {
        this.attendanceRepository = attendanceRepository;
        this.enrollmentRepository = enrollmentRepository;
        this.sessionService = sessionService;
        this.traineeService = traineeService;
    }

    /** 该课次所属课程的报名者（没报名的学员不出现） */
    public List<Enrollment> enrollees(Long sessionId) {
        CourseSession session = sessionService.get(sessionId);
        return enrollmentRepository.findByCourseIdOrderByIdAsc(session.getCourse().getId());
    }

    /**
     * 已取消报名、但在该课次还留有出勤记录的学员的出勤（按学员 ID 排序）。
     * 取消报名不删出勤（#9：取消报名后出勤照算），但表单只列报名者就看不到这些记录，
     * 课次也就删不掉。所以在表单里另外列出，只允许改为「未记录」。
     */
    public List<Attendance> cancelledWithAttendance(Long sessionId) {
        Long courseId = sessionService.get(sessionId).getCourse().getId();
        return attendanceRepository.findBySessionId(sessionId).stream()
                .filter(a -> !enrollmentRepository.existsByTraineeIdAndCourseId(a.getTrainee().getId(), courseId))
                .sorted(Comparator.comparing(a -> a.getTrainee().getId()))
                .toList();
    }

    /** 已保存的状态：学员 ID → 状态（没有记录的学员不在 Map 里 = 未记录） */
    public Map<Long, AttendanceStatus> statusesOf(Long sessionId) {
        return attendanceRepository.findBySessionId(sessionId).stream()
                .collect(Collectors.toMap(a -> a.getTrainee().getId(), Attendance::getStatus));
    }

    /**
     * 保存一个课次的出勤。
     * - 状态有值：有旧记录就更新，没有就新增（同一课次・同一学员只有 1 条）
     * - 状态为 null（未记录）：有旧记录就删除，没有就跳过
     * - 只能登记该课程的报名者；有一个不是报名者，整批都不保存
     * - 例外：已取消报名但留有出勤记录的学员，只能改为「未记录」（删除记录）；
     *   原样提交当前状态时什么都不做，改成其他状态则拒绝
     *
     * @param statuses 学员 ID → 状态。null 或空则什么都不做
     */
    @Transactional
    public void save(Long sessionId, Map<Long, AttendanceStatus> statuses) {
        CourseSession session = sessionService.get(sessionId);
        if (statuses == null || statuses.isEmpty()) {
            return;
        }
        Long courseId = session.getCourse().getId();

        // 先全部检查，再写入
        Map<Trainee, AttendanceStatus> checked = new LinkedHashMap<>();
        for (Map.Entry<Long, AttendanceStatus> entry : statuses.entrySet()) {
            Trainee trainee = traineeService.get(entry.getKey());
            AttendanceStatus status = entry.getValue();
            if (!enrollmentRepository.existsByTraineeIdAndCourseId(trainee.getId(), courseId)) {
                Optional<Attendance> existing =
                        attendanceRepository.findBySessionIdAndTraineeId(sessionId, trainee.getId());
                if (existing.isEmpty()) {
                    throw new BusinessException("学员「" + trainee.getName() + "」没有报名课程「"
                            + session.getCourse().getDisplayName() + "」，不能登记出勤。请先在「报名管理」里报名。");
                }
                if (status == existing.get().getStatus()) {
                    continue;   // 已取消报名者：原样提交当前状态 → 不变
                }
                if (status != null) {
                    throw new BusinessException("学员「" + trainee.getName() + "」已取消报名，出勤只能改为「未记录」。"
                            + "如需继续登记，请先在「报名管理」里重新报名。");
                }
            }
            checked.put(trainee, status);
        }

        checked.forEach((trainee, status) -> {
            Optional<Attendance> existing = attendanceRepository.findBySessionIdAndTraineeId(sessionId, trainee.getId());
            if (status == null) {
                existing.ifPresent(attendanceRepository::delete);
            } else if (existing.isPresent()) {
                existing.get().setStatus(status);
            } else {
                attendanceRepository.save(new Attendance(session, trainee, status));
            }
        });
    }
}
