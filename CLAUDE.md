# CLAUDE.md — 培训管理系统

FDE 培训用的模拟项目（Spring Boot 3.3 / Java 17 / Thymeleaf / Spring Data JPA / H2 内存库 / Spring Security）。

## 命令

- 测试：`./mvnw test`（Windows 用 `mvnw.cmd test`）。**改完代码一定要跑，并把结果给用户看**
- 启动：`./mvnw spring-boot:run` → http://localhost:8080 （admin / admin123）
- 数据库每次启动都会重建，种子数据在 `config/DataInitializer.java`

## 分层规则

- **Controller**（`web/`）：只做画面的输入输出。不写业务判断
- **Service**（`service/`）：业务规则都写这里。违反规则时抛 `BusinessException`（message 会显示在画面上，用中文写清楚原因和怎么办）。找不到数据抛 `NotFoundException`（→ 404）
- **Repository**（`repository/`）：只做数据存取，优先用 Spring Data 的方法名查询
- 「今天」一律用注入的 `Clock`（`LocalDate.now(clock)`），不要直接写 `LocalDate.now()`——测试时要能固定日期

## 写法范本（新功能照着写）

- 画面：`TraineeController` ＋ `templates/trainees/list.html`・`form.html`（共通布局用 `fragments/layout.html`）
- 业务：`TraineeService`
- 测试：Service 用 Mockito 单元测试（`TraineeServiceTest`），画面用 MockMvc（`LoginAndMenuTest`）
- 新功能做完：在 `web/Feature.java` 把对应行改成 `done = true` 并填上 path，主菜单和主页会自动更新

## 必须遵守

- 新增的业务规则必须有单元测试，包括边界情况（空、0、重复、相等等）
- 只改 issue 范围内的东西。发现范围外的问题，告诉用户，不要顺手改
- 画面文字、错误消息、代码注释用中文
- 不使用真实的人名・邮箱；测试数据用 `@example.com`
- 不新增依赖库，除非用户明确同意
