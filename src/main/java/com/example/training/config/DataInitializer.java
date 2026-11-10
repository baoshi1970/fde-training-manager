package com.example.training.config;

import com.example.training.domain.*;
import com.example.training.repository.*;
import org.springframework.boot.CommandLineRunner;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.temporal.TemporalAdjusters;

/**
 * 种子数据。
 * - 启动时：数据库为空才写入（本地 H2 每次启动都是空的；部署环境的 Postgres 只在第一次写入）
 * - 管理员在主页点「重置为初始数据」时：删掉业务数据，重新写入（{@link #resetToSeed()}）
 * 人名・邮箱全部是虚构的。课次日期以「本周一」为基准，所以任何时候主页都有近期课次。
 *
 * ★新增实体（出勤・评分等）时：在 resetToSeed() 里先删掉它的数据（有外键，要按依赖顺序删）
 */
@Component
public class DataInitializer implements CommandLineRunner {

    private final AppUserRepository userRepo;
    private final TraineeRepository traineeRepo;
    private final InstructorRepository instructorRepo;
    private final CourseRepository courseRepo;
    private final CourseSessionRepository sessionRepo;
    private final EnrollmentRepository enrollmentRepo;
    private final PasswordEncoder encoder;
    private final Clock clock;
    private final JdbcTemplate jdbc;

    public DataInitializer(AppUserRepository userRepo, TraineeRepository traineeRepo,
                           InstructorRepository instructorRepo, CourseRepository courseRepo,
                           CourseSessionRepository sessionRepo, EnrollmentRepository enrollmentRepo,
                           PasswordEncoder encoder, Clock clock, JdbcTemplate jdbc) {
        this.userRepo = userRepo;
        this.traineeRepo = traineeRepo;
        this.instructorRepo = instructorRepo;
        this.courseRepo = courseRepo;
        this.sessionRepo = sessionRepo;
        this.enrollmentRepo = enrollmentRepo;
        this.encoder = encoder;
        this.clock = clock;
        this.jdbc = jdbc;
    }

    @Override
    @Transactional
    public void run(String... args) {
        lockIfPostgres();
        if (userRepo.count() > 0) {
            return;
        }
        seedUsers();
        seedBusinessData();
    }

    /** 删掉所有业务数据（登录用户保留），重新写入种子数据 */
    @Transactional
    public void resetToSeed() {
        lockIfPostgres();
        enrollmentRepo.deleteAllInBatch();
        sessionRepo.deleteAllInBatch();
        courseRepo.deleteAllInBatch();
        instructorRepo.deleteAllInBatch();
        traineeRepo.deleteAllInBatch();
        seedBusinessData();
    }

    /**
     * 部署环境可能同时启动多个实例：用 Postgres 的咨询锁让种子数据只写一次。
     * （锁在事务结束时自动释放。H2 没有这个函数，跳过）
     */
    private void lockIfPostgres() {
        String product = jdbc.execute((java.sql.Connection c) -> c.getMetaData().getDatabaseProductName());
        if ("PostgreSQL".equals(product)) {
            jdbc.queryForList("SELECT pg_advisory_xact_lock(727401)");
        }
    }

    private void seedUsers() {
        // ---- 登录用户（README 里写有账号）----
        userRepo.save(new AppUser("admin", encoder.encode("admin123"), "管理员", AppUser.Role.ADMIN));
        userRepo.save(new AppUser("teacher", encoder.encode("teacher123"), "田中 讲师", AppUser.Role.INSTRUCTOR));
    }

    private void seedBusinessData() {
        LocalDate today = LocalDate.now(clock);
        LocalDate monday = today.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY));

        // ---- 学员 ----
        Trainee t1 = traineeRepo.save(new Trainee("山田 太郎", "taro.yamada@example.com", "A 社 开发部", TraineeStatus.ACTIVE, monday.minusWeeks(3)));
        Trainee t2 = traineeRepo.save(new Trainee("李 明", "ming.li@example.com", "A 社 开发部", TraineeStatus.ACTIVE, monday.minusWeeks(3)));
        Trainee t3 = traineeRepo.save(new Trainee("佐藤 花子", "hanako.sato@example.com", "B 社 系统部", TraineeStatus.ACTIVE, monday.minusWeeks(3)));
        Trainee t4 = traineeRepo.save(new Trainee("王 磊", "lei.wang@example.com", "B 社 系统部", TraineeStatus.ACTIVE, monday.minusWeeks(2)));
        Trainee t5 = traineeRepo.save(new Trainee("陈 静", "jing.chen@example.com", "C 社 咨询部", TraineeStatus.ACTIVE, monday.minusWeeks(2)));
        traineeRepo.save(new Trainee("鈴木 一郎", "ichiro.suzuki@example.com", "C 社 咨询部", TraineeStatus.ON_LEAVE, monday.minusWeeks(3)));
        traineeRepo.save(new Trainee("张 伟", "wei.zhang@example.com", "A 社 开发部", TraineeStatus.GRADUATED, monday.minusWeeks(12)));
        Trainee t8 = traineeRepo.save(new Trainee("高橋 健太", "kenta.takahashi@example.com", "D 社 IT 部", TraineeStatus.ACTIVE, monday.minusWeeks(1)));

        // ---- 讲师 ----
        Instructor i1 = instructorRepo.save(new Instructor("田中 诚", "makoto.tanaka@example.com", "Claude Code・AI 辅助编码"));
        Instructor i2 = instructorRepo.save(new Instructor("刘 洋", "yang.liu@example.com", "RAG・知识库"));
        Instructor i3 = instructorRepo.save(new Instructor("小林 美咲", "misaki.kobayashi@example.com", "FDE 方法论・对客沟通"));

        // ---- 课程 ----
        Course s0 = courseRepo.save(new Course("S0", "AI 素养", CourseCategory.COMMON, true, 8));
        Course s1 = courseRepo.save(new Course("S1", "Support 实操", CourseCategory.PRODUCT, true, 12));
        Course s2 = courseRepo.save(new Course("S2", "AI 辅助编码", CourseCategory.COMMON, true, 16));
        Course s3 = courseRepo.save(new Course("S3", "核心技术栈", CourseCategory.COMMON, true, 24));
        courseRepo.save(new Course("C1", "Knowledge 进阶", CourseCategory.PRODUCT, false, 8));

        // ---- 课次（以本周一为基准）----
        LocalTime evening = LocalTime.of(19, 30);
        LocalTime eveningEnd = LocalTime.of(21, 0);
        sessionRepo.save(new CourseSession(s0, i3, monday.minusWeeks(2), evening, eveningEnd, SessionMode.ONLINE, "Zoom"));
        sessionRepo.save(new CourseSession(s0, i3, monday.minusWeeks(2).plusDays(2), evening, eveningEnd, SessionMode.ONLINE, "Zoom"));
        sessionRepo.save(new CourseSession(s1, i2, monday.minusWeeks(1), evening, eveningEnd, SessionMode.ONLINE, "Zoom"));
        sessionRepo.save(new CourseSession(s1, i2, monday.minusWeeks(1).plusDays(2), evening, eveningEnd, SessionMode.ONLINE, "Zoom"));
        sessionRepo.save(new CourseSession(s2, i1, monday, evening, eveningEnd, SessionMode.ONLINE, "Zoom"));
        sessionRepo.save(new CourseSession(s2, i1, monday.plusDays(2), evening, eveningEnd, SessionMode.ONLINE, "Zoom"));
        sessionRepo.save(new CourseSession(s2, i1, monday.plusDays(4), LocalTime.of(13, 0), LocalTime.of(17, 0), SessionMode.ONSITE, "本社 3F 会议室"));
        sessionRepo.save(new CourseSession(s3, i2, monday.plusWeeks(1), evening, eveningEnd, SessionMode.ONLINE, "Zoom"));
        sessionRepo.save(new CourseSession(s3, i1, monday.plusWeeks(1).plusDays(2), evening, eveningEnd, SessionMode.ONLINE, "Zoom"));

        // ---- 报名 ----
        for (Trainee t : new Trainee[]{t1, t2, t3, t4, t5}) {
            enrollmentRepo.save(new Enrollment(t, s0, monday.minusWeeks(3)));
            enrollmentRepo.save(new Enrollment(t, s1, monday.minusWeeks(2)));
            enrollmentRepo.save(new Enrollment(t, s2, monday.minusWeeks(1)));
        }
        enrollmentRepo.save(new Enrollment(t8, s2, monday.minusWeeks(1)));
    }
}
