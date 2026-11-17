package com.example.training.service;

import com.example.training.domain.Trainee;
import com.example.training.domain.TraineeStatus;
import com.example.training.repository.EnrollmentRepository;
import com.example.training.repository.TraineeRepository;
import jakarta.validation.Validator;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.InputStream;
import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/** 学员管理 */
@Service
@Transactional(readOnly = true)
public class TraineeService {

    /** F10 CSV 导入：表头（列顺序固定） */
    private static final List<String> CSV_HEADER = List.of("name", "email", "affiliation", "joinedOn");
    /** F10 CSV 导入：一次最多导入的数据行数 */
    private static final int MAX_IMPORT_ROWS = 500;
    // 长度上限（和 Trainee 实体的列长度一致）
    private static final int MAX_NAME_LENGTH = 50;
    private static final int MAX_EMAIL_LENGTH = 100;
    private static final int MAX_AFFILIATION_LENGTH = 100;

    private final TraineeRepository traineeRepository;
    private final EnrollmentRepository enrollmentRepository;
    private final Validator validator;

    public TraineeService(TraineeRepository traineeRepository, EnrollmentRepository enrollmentRepository,
                          Validator validator) {
        this.traineeRepository = traineeRepository;
        this.enrollmentRepository = enrollmentRepository;
        this.validator = validator;
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

    // ================= F10 CSV 导入 =================

    /**
     * 从 CSV 导入学员。全部行都没有错误时才登记（状态一律为「在籍」）。
     *
     * @return 登记的件数
     * @throws BusinessException   文件级错误（空文件・表头不对・只有表头・超过 500 行・CSV 格式错误・不是 UTF-8）
     * @throws CsvImportException 行级错误（全部列出，一条也不登记）
     * @throws java.io.UncheckedIOException 读取文件失败（不是业务错误，按系统错误处理）
     */
    @Transactional
    public int importCsv(InputStream in) {
        List<CsvRecord> records = CsvParser.parse(in);
        checkFile(records);
        List<CsvRecord> rows = records.subList(1, records.size());

        // 已有学员的邮箱：先收集文件里的邮箱，只查一次数据库
        Set<String> emailsInFile = rows.stream()
                .filter(r -> r.fields().size() == CSV_HEADER.size())
                .map(r -> clean(r.fields().get(1)))
                .filter(e -> !e.isEmpty())
                .collect(Collectors.toSet());
        Set<String> existingEmails = emailsInFile.isEmpty() ? Set.of()
                : traineeRepository.findByEmailIn(emailsInFile).stream()
                        .map(Trainee::getEmail)
                        .collect(Collectors.toSet());

        Map<String, Integer> firstLineByEmail = new HashMap<>();
        List<String> errors = new ArrayList<>();
        List<Trainee> trainees = new ArrayList<>();
        for (CsvRecord row : rows) {
            Trainee trainee = validateRow(row, firstLineByEmail, existingEmails, errors);
            if (trainee != null) {
                trainees.add(trainee);
            }
        }
        if (!errors.isEmpty()) {
            throw new CsvImportException(errors);
        }
        traineeRepository.saveAll(trainees);
        return trainees.size();
    }

    /** 文件级检查。出错时直接抛 BusinessException（不再做行级检查） */
    private void checkFile(List<CsvRecord> records) {
        if (records.isEmpty()) {
            throw new BusinessException("文件是空的。请上传包含表头和学员数据的 CSV 文件。");
        }
        List<String> header = records.get(0).fields().stream().map(TraineeService::clean).toList();
        if (!header.equals(CSV_HEADER)) {
            throw new BusinessException("表头不正确，应为：" + String.join(",", CSV_HEADER) + "（列顺序固定）。");
        }
        int dataRows = records.size() - 1;
        if (dataRows == 0) {
            throw new BusinessException("只有表头，没有学员数据。");
        }
        if (dataRows > MAX_IMPORT_ROWS) {
            throw new BusinessException("数据行有 " + dataRows + " 行，超过上限 " + MAX_IMPORT_ROWS + " 行，请分批导入。");
        }
    }

    /**
     * 检查一行，把错误追加到 errors。
     *
     * @param firstLineByEmail 文件内每个邮箱第一次出现的行号（重复检查用，会被更新）
     * @param existingEmails   已被已有学员使用的邮箱
     * @return 这一行没有错误时，返回要登记的学员；有错误时返回 null
     */
    private Trainee validateRow(CsvRecord row, Map<String, Integer> firstLineByEmail,
                                Set<String> existingEmails, List<String> errors) {
        String prefix = "第 " + row.lineNo() + " 行：";
        if (row.fields().size() != CSV_HEADER.size()) {
            errors.add(prefix + "列数应为 " + CSV_HEADER.size() + " 列，实际 " + row.fields().size() + " 列");
            return null;
        }
        int errorsBefore = errors.size();
        String name = clean(row.fields().get(0));
        String email = clean(row.fields().get(1));
        String affiliation = clean(row.fields().get(2));
        String joinedOnText = clean(row.fields().get(3));

        // 姓名
        if (name.isEmpty()) {
            errors.add(prefix + "姓名（name）不能为空");
        } else {
            checkLength(name, "姓名（name）", MAX_NAME_LENGTH, prefix, errors);
        }

        // 邮箱：格式 → 长度 → 文件内重复 → 已有学员
        if (email.isEmpty()) {
            errors.add(prefix + "邮箱（email）不能为空");
        } else {
            // 和画面新增学员一样，用 Trainee.email 上的 @Email 规则检查
            if (!validator.validateValue(Trainee.class, "email", email).isEmpty()) {
                errors.add(prefix + "邮箱（email）格式不正确（" + email + "）");
            }
            checkLength(email, "邮箱（email）", MAX_EMAIL_LENGTH, prefix, errors);
            Integer firstLine = firstLineByEmail.putIfAbsent(email, row.lineNo());
            if (firstLine != null) {
                errors.add(prefix + "邮箱与第 " + firstLine + " 行重复（" + email + "）");
            }
            if (existingEmails.contains(email)) {
                errors.add(prefix + "该邮箱已被已有学员使用（" + email + "）");
            }
        }

        // 所属（可以为空）
        checkLength(affiliation, "所属（affiliation）", MAX_AFFILIATION_LENGTH, prefix, errors);

        // 入学日期：ISO 格式严格解析（2026-02-30・2026/11/01 都算错）
        LocalDate joinedOn = null;
        if (joinedOnText.isEmpty()) {
            errors.add(prefix + "入学日期（joinedOn）不能为空");
        } else {
            try {
                joinedOn = LocalDate.parse(joinedOnText);
            } catch (DateTimeParseException e) {
                errors.add(prefix + "入学日期（joinedOn）不正确（" + joinedOnText + "），应为 yyyy-MM-dd 格式的有效日期，如 2026-11-01");
            }
        }

        if (errors.size() > errorsBefore) {
            return null;
        }
        return new Trainee(name, email, affiliation, TraineeStatus.ACTIVE, joinedOn);
    }

    /**
     * 长度检查。按 String.length()（char 数）计算，扩展汉字算 2 个字符：
     * 和实体的 @Size、H2 的计算方式一致（同 F8 #23 的规则）
     */
    private static void checkLength(String value, String label, int max, String prefix, List<String> errors) {
        if (value.length() > max) {
            errors.add(prefix + label + "不能超过 " + max + " 个字符（当前 " + value.length()
                    + " 个，扩展汉字按 2 个字符计算）");
        }
    }

    /** 去掉前后空白。用 strip() 而不是 trim()，全角空格（U+3000）也去掉（同 F8 #23 的规则） */
    private static String clean(String value) {
        return value.strip();
    }
}
