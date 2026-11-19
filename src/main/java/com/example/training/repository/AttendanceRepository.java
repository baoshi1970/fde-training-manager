package com.example.training.repository;

import com.example.training.domain.Attendance;
import com.example.training.domain.AttendanceStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

public interface AttendanceRepository extends JpaRepository<Attendance, Long> {

    List<Attendance> findBySessionId(Long sessionId);

    Optional<Attendance> findBySessionIdAndTraineeId(Long sessionId, Long traineeId);

    boolean existsBySessionId(Long sessionId);

    boolean existsByTraineeId(Long traineeId);

    /**
     * F9 学员进度看板：按学员 × 状态汇总出勤件数。只算课次日期 ≤ 今天的记录（#9 讲师回答 1）。
     * 用 @Query 的理由：方法名查询表达不了 GROUP BY 多列汇总；逐个学员查会变成 N+1（#9 计划第 3 节）。
     * 不关联报名，所以取消报名后的出勤也照算（#9 讲师回答 2）。
     */
    @Query("select a.trainee.id as traineeId, a.status as status, count(a) as total"
            + " from Attendance a where a.session.date <= :today"
            + " group by a.trainee.id, a.status")
    List<StatusCount> countByTraineeAndStatusUpTo(@Param("today") LocalDate today);

    /** 出勤汇总的一行：学员 id・状态・件数 */
    interface StatusCount {
        Long getTraineeId();

        AttendanceStatus getStatus();

        long getTotal();
    }
}
