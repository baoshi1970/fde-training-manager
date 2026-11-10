package com.example.training.service;

import com.example.training.domain.Instructor;
import com.example.training.repository.CourseSessionRepository;
import com.example.training.repository.InstructorRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/** 讲师管理 */
@Service
@Transactional(readOnly = true)
public class InstructorService {

    private final InstructorRepository instructorRepository;
    private final CourseSessionRepository sessionRepository;

    public InstructorService(InstructorRepository instructorRepository, CourseSessionRepository sessionRepository) {
        this.instructorRepository = instructorRepository;
        this.sessionRepository = sessionRepository;
    }

    public List<Instructor> findAll() {
        return instructorRepository.findAllByOrderByIdAsc();
    }

    public Instructor get(Long id) {
        return instructorRepository.findById(id).orElseThrow(() -> new NotFoundException("讲师", id));
    }

    @Transactional
    public Instructor create(Instructor instructor) {
        if (instructorRepository.existsByEmail(instructor.getEmail())) {
            throw new BusinessException("该邮箱已被其他讲师使用：" + instructor.getEmail());
        }
        instructor.setId(null);
        return instructorRepository.save(instructor);
    }

    @Transactional
    public Instructor update(Long id, Instructor input) {
        Instructor instructor = get(id);
        if (instructorRepository.existsByEmailAndIdNot(input.getEmail(), id)) {
            throw new BusinessException("该邮箱已被其他讲师使用：" + input.getEmail());
        }
        instructor.setName(input.getName());
        instructor.setEmail(input.getEmail());
        instructor.setSpecialty(input.getSpecialty());
        return instructor;
    }

    @Transactional
    public void delete(Long id) {
        Instructor instructor = get(id);
        if (sessionRepository.existsByInstructorId(id)) {
            throw new BusinessException("该讲师已被排入课次，不能删除。请先修改或删除相关课次。");
        }
        instructorRepository.delete(instructor);
    }
}
