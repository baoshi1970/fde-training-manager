package com.example.training.repository;

import com.example.training.domain.Trainee;
import com.example.training.domain.TraineeStatus;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.List;

public interface TraineeRepository extends JpaRepository<Trainee, Long> {

    /** 姓名检索 */
    List<Trainee> findByNameStartingWithOrderByIdAsc(String keyword);

    /** 姓名检索＋状态 */
    List<Trainee> findByNameStartingWithAndStatusOrderByIdAsc(String keyword, TraineeStatus status);

    List<Trainee> findByStatusOrderByIdAsc(TraineeStatus status);

    List<Trainee> findAllByOrderByIdAsc();

    boolean existsByEmail(String email);

    boolean existsByEmailAndIdNot(String email, Long id);

    /** F10 CSV 导入：一次查出文件里已被已有学员使用的邮箱（不逐行查询） */
    List<Trainee> findByEmailIn(Collection<String> emails);

    long countByStatus(TraineeStatus status);
}
