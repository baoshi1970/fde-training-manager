# 培训管理系统（Training Manager）

FDE 培训用的模拟项目。管理学员、讲师、课程、课次、报名——业务就是我们自己的培训，不用花时间理解领域知识，专心练 **「指挥 AI 写、自己审」**。

- 10 个功能里，**F1〜F5 已经做好**（同时也是写法的范本），**F6〜F10 留给学员**用 Claude Code 实装
- 登录・主页・主菜单已经做好；新功能做完后，在 `web/Feature.java` 里改成已实装，菜单就会出现

## 启动

只需要 **JDK 17**（不用装 Maven，也不用装数据库）。

```bash
./mvnw spring-boot:run        # Windows：mvnw.cmd spring-boot:run
```

打开 http://localhost:8080 ，用下面的账号登录：

| 用户名 | 密码 | 角色 |
|---|---|---|
| `admin` | `admin123` | 管理员 |
| `teacher` | `teacher123` | 讲师 |

- 数据库是 H2 内存库：**每次启动都恢复成初始的种子数据**，可以放心改、放心删
- 想直接看表：http://localhost:8080/h2-console （JDBC URL：`jdbc:h2:mem:training`，用户 `sa`，密码空）

## 在线体验（Vercel）

演示环境部署在 Vercel（`Dockerfile` ＋ `vercel.json`，以容器方式运行）。
- 账号同上。**数据不会保存**：实例重启或休眠后恢复成种子数据
- 演示环境关闭了 H2 控制台

## 测试

```bash
./mvnw test
```

PR 提交后 GitHub Actions 也会自动跑同样的测试。**测试不通过的 PR 不合并。**

## 功能一览

| # | 功能 | 状态 | 说明 |
|---|---|---|---|
| F1 | 学员管理 | ✅ | 增删改查、按姓名・状态检索 |
| F2 | 讲师管理 | ✅ | 增删改查 |
| F3 | 课程管理 | ✅ | 课程编号・类别・必修・学时 |
| F4 | 课次排期 | ✅ | 日期・时段・讲师・线上/线下 |
| F5 | 报名管理 | ✅ | 在籍学员报名课程、取消报名 |
| F6 | 出勤登记 | 📝 学员课题 | ★ 按课次登记出席・迟到・缺席 |
| F7 | 排课冲突检查 | 📝 学员课题 | ★★ 同一讲师同一时段不能排两场 |
| F8 | 加权评分 | 📝 学员课题 | ★★ 评价维度×权重 → 总分・评级 |
| F9 | 学员进度看板 | 📝 学员课题 | ★★★ 出勤率・平均分一览（依赖 F6・F8） |
| F10 | CSV 导入学员 | 📝 学员课题 | ★★★ 逐行校验，报出第几行哪里错 |

每个课题的需求和验收条件写在 GitHub issue 里。

## 目录结构

```
src/main/java/com/example/training/
├── config/       登录设定・种子数据・Clock
├── domain/       实体（Trainee, Instructor, Course, CourseSession, Enrollment …）
├── repository/   Spring Data JPA
├── service/      业务规则（错误用 BusinessException 抛出）
└── web/          Controller・主菜单定义（Feature.java）
src/main/resources/templates/   Thymeleaf 画面（fragments/layout.html 是共通布局）
src/test/java/                  测试（service＝单元测试，web＝画面级测试）
```

**范本**：新功能照着「学员管理」写——`TraineeController` → `TraineeService` → `TraineeRepository`，测试照 `TraineeServiceTest` 和 `LoginAndMenuTest`。

## 做课题的流程

1. 在 GitHub 上领一个 issue（assign 给自己）
2. 建分支：`feature/F6-attendance` 这样的名字
3. 用 Claude Code 按**三段法**做：
   - **计划**：先让它读 issue 和范本代码，给出改法，你确认后再动手
   - **实现**：1 次请求 1 个目的，不顺手改别的
   - **验证**：让它跑 `./mvnw test`，贴出结果和 `git diff`
4. 开 PR（正文写：做了什么・怎么验证的・关键 prompt），请同伴 review
5. CI 通过＋review 通过 → 合并

**完成的定义**：PR 合并 ＋ 新增的业务规则有测试 ＋ 能逐行讲清 diff ＋ 关键 prompt 已提交。

## 注意

- 这是练习用项目，**不要放真实的人名・邮箱・公司信息**（种子数据全部是虚构的）
- 不要提交 API key 等机密信息
