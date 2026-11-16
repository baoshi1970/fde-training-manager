package com.example.training.service;

import com.example.training.domain.Course;
import com.example.training.repository.CourseRepository;
import com.example.training.repository.CourseSessionRepository;
import com.example.training.repository.EnrollmentRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/** 课程管理 */
@Service
@Transactional(readOnly = true)
public class CourseService {

    private final CourseRepository courseRepository;
    // 注入 Repository 而不是 SessionService：SessionService 已依赖本类，反过来依赖会循环
    private final CourseSessionRepository sessionRepository;
    private final EnrollmentRepository enrollmentRepository;

    public CourseService(CourseRepository courseRepository, CourseSessionRepository sessionRepository,
                         EnrollmentRepository enrollmentRepository) {
        this.courseRepository = courseRepository;
        this.sessionRepository = sessionRepository;
        this.enrollmentRepository = enrollmentRepository;
    }

    public List<Course> findAll() {
        return courseRepository.findAllByOrderByCodeAsc();
    }

    public Course get(Long id) {
        return courseRepository.findById(id).orElseThrow(() -> new NotFoundException("课程", id));
    }

    @Transactional
    public Course create(Course course) {
        if (courseRepository.existsByCode(course.getCode())) {
            throw new BusinessException("课程编号已存在：" + course.getCode());
        }
        course.setId(null);
        return courseRepository.save(course);
    }

    @Transactional
    public Course update(Long id, Course input) {
        Course course = get(id);
        if (courseRepository.existsByCodeAndIdNot(input.getCode(), id)) {
            throw new BusinessException("课程编号已存在：" + input.getCode());
        }
        course.setCode(input.getCode());
        course.setName(input.getName());
        course.setCategory(input.getCategory());
        course.setRequired(input.isRequired());
        course.setHours(input.getHours());
        return course;
    }

    @Transactional
    public void delete(Long id) {
        Course course = get(id);
        // 课次・报名都有外键指向课程，直接删会被数据库拒绝（500），所以先检查
        if (sessionRepository.existsByCourseId(id)) {
            throw new BusinessException("该课程已有课次，不能删除。请先删除相关课次。");
        }
        if (enrollmentRepository.existsByCourseId(id)) {
            throw new BusinessException("该课程已有学员报名，不能删除。请先取消相关报名。");
        }
        courseRepository.delete(course);
    }

    public long count() {
        return courseRepository.count();
    }
}
