package com.example.training.service;

import com.example.training.domain.Trainee;
import com.example.training.domain.TraineeStatus;
import com.example.training.repository.EnrollmentRepository;
import com.example.training.repository.TraineeRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/** 学员管理 */
@Service
@Transactional(readOnly = true)
public class TraineeService {

    private final TraineeRepository traineeRepository;
    private final EnrollmentRepository enrollmentRepository;

    public TraineeService(TraineeRepository traineeRepository, EnrollmentRepository enrollmentRepository) {
        this.traineeRepository = traineeRepository;
        this.enrollmentRepository = enrollmentRepository;
    }

    /**
     * 学员检索。
     *
     * @param keyword 姓名（部分一致）。空则不限
     * @param status  状态。null 则不限
     */
    public List<Trainee> search(String keyword, TraineeStatus status) {
        boolean noKeyword = keyword == null || keyword.isBlank();
        if (noKeyword && status == null) {
            return traineeRepository.findAllByOrderByIdAsc();
        }
        if (noKeyword) {
            return traineeRepository.findByStatusOrderByIdAsc(status);
        }
        String k = keyword.trim();
        if (status == null) {
            return traineeRepository.findByNameStartingWithOrderByIdAsc(k);
        }
        return traineeRepository.findByNameStartingWithAndStatusOrderByIdAsc(k, status);
    }

    public Trainee get(Long id) {
        return traineeRepository.findById(id).orElseThrow(() -> new NotFoundException("学员", id));
    }

    @Transactional
    public Trainee create(Trainee trainee) {
        if (traineeRepository.existsByEmail(trainee.getEmail())) {
            throw new BusinessException("该邮箱已被其他学员使用：" + trainee.getEmail());
        }
        trainee.setId(null);
        return traineeRepository.save(trainee);
    }

    @Transactional
    public Trainee update(Long id, Trainee input) {
        Trainee trainee = get(id);
        if (traineeRepository.existsByEmailAndIdNot(input.getEmail(), id)) {
            throw new BusinessException("该邮箱已被其他学员使用：" + input.getEmail());
        }
        trainee.setName(input.getName());
        trainee.setEmail(input.getEmail());
        trainee.setAffiliation(input.getAffiliation());
        trainee.setStatus(input.getStatus());
        trainee.setJoinedOn(input.getJoinedOn());
        return trainee;
    }

    @Transactional
    public void delete(Long id) {
        Trainee trainee = get(id);
        if (enrollmentRepository.existsByTraineeId(id)) {
            throw new BusinessException("该学员有报名记录，不能删除。请先取消报名，或把状态改为「结业」。");
        }
        traineeRepository.delete(trainee);
    }

    public long countActive() {
        return traineeRepository.countByStatus(TraineeStatus.ACTIVE);
    }
}
