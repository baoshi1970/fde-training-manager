package com.example.training.service;

import com.example.training.domain.Course;
import com.example.training.repository.CourseRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/** 课程管理 */
@Service
@Transactional(readOnly = true)
public class CourseService {

    private final CourseRepository courseRepository;

    public CourseService(CourseRepository courseRepository) {
        this.courseRepository = courseRepository;
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
        courseRepository.delete(course);
    }

    public long count() {
        return courseRepository.count();
    }
}
