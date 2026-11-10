package com.example.training.service;

import com.example.training.domain.Course;
import com.example.training.domain.Enrollment;
import com.example.training.domain.Trainee;
import com.example.training.domain.TraineeStatus;
import com.example.training.repository.EnrollmentRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.List;

/** 报名管理 */
@Service
@Transactional(readOnly = true)
public class EnrollmentService {

    private final EnrollmentRepository enrollmentRepository;
    private final TraineeService traineeService;
    private final CourseService courseService;

    public EnrollmentService(EnrollmentRepository enrollmentRepository,
                             TraineeService traineeService, CourseService courseService) {
        this.enrollmentRepository = enrollmentRepository;
        this.traineeService = traineeService;
        this.courseService = courseService;
    }

    public List<Enrollment> findByCourse(Long courseId) {
        return enrollmentRepository.findByCourseIdOrderByIdAsc(courseId);
    }

    public long countByCourse(Long courseId) {
        return enrollmentRepository.countByCourseId(courseId);
    }

    /**
     * 报名。只有「在籍」的学员可以报名。
     */
    @Transactional
    public Enrollment enroll(Long traineeId, Long courseId, LocalDate today) {
        Trainee trainee = traineeService.get(traineeId);
        Course course = courseService.get(courseId);
        if (trainee.getStatus() != TraineeStatus.ACTIVE) {
            throw new BusinessException("只有「在籍」的学员可以报名（" + trainee.getName() + "：" + trainee.getStatus().getLabel() + "）");
        }
        return enrollmentRepository.save(new Enrollment(trainee, course, today));
    }

    /** 取消报名 */
    @Transactional
    public void cancel(Long enrollmentId) {
        Enrollment e = enrollmentRepository.findById(enrollmentId)
                .orElseThrow(() -> new NotFoundException("报名", enrollmentId));
        enrollmentRepository.delete(e);
    }
}
