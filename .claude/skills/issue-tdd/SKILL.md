---
name: issue-tdd
description: 按「读 issue → 列边界 → 先写测试 → 实现 → 贴证据」的顺序完成本仓库的一个 GitHub issue（新增或修改业务规则）。用户说「做 issue #N」「按流程实现 #N」「先写测试再实现」，或者要实现 Service 层的校验・业务规则时使用。
---

# issue-tdd：读 issue → 列边界 → 先写测试 → 实现 → 贴证据

本仓库实现一个 issue 的标准流程。每一步结束都**停下来把结果给用户看，等用户同意再进下一步**。用户说「先不要改」时，就只做读和分析。

## 什么时候用

- 要实现的 issue 涉及业务规则（校验、计算、状态变化），需要单元测试覆盖边界情况
- 用户要求「先写测试」「先给方案」「贴证据」
- 不适合：只改文案、改样式、改文档这类没有业务规则的小改动（直接改，跑测试即可）

## 步骤

### 第 0 步：准备

1. 确认工作区干净（`git status`），记下当前分支。
2. 确认 issue 已经分配给用户：
   - 本机没有 `gh` 时，用 GitHub API：`curl -s https://api.github.com/repos/<owner>/<repo>/issues/<N>`，看 `assignees`
3. 在 `main` 上的话，先切分支，分支名按 issue 的「交付」规定（例：`f7-<GitHub ID>`）。GitHub ID 不知道就问用户，不要猜。

### 第 1 步：读 issue 和相关代码（不改代码）

1. 读 issue 正文，列出验收条件。
2. 读相关的 Service・Entity・Repository・现有测试，以及 Controller 怎么处理 `BusinessException`。
3. 向用户报告三件事：
   - **现在的处理流程**（画面 → Controller → Service → Repository）
   - **要改哪里**，写成 `文件:行号`
   - **需要哪些查询**（优先用 Spring Data 方法名查询）

### 第 2 步：列边界情况、给方案（不改代码）

1. **边界情况表**：每一行对应一条测试，写清「输入 → 期望（报错／不报错）→ 测试方法名」。至少考虑：
   - 相接（两个方向都要测：前接、后接）、部分重叠（前／后）、完全包含（两个方向）、完全相同
   - 不同的人・不同的日期等「不应该被拦住」的情况
   - 空（没有数据）、0、重复、相等
   - **修改时排除自己**，以及它的反面：修改时跟别的数据冲突仍然要报错；新增时即使表单带了 id 也不能排除
   - 多条数据中只有一条冲突时，消息里写的是冲突的那一条
   - 多个校验同时不满足时，先报哪个
   - 报错时不保存，原数据不被改掉
2. **给 2 个实现方案**，比较可测性、是否符合 CLAUDE.md 的分层规则、性能、可读性，写明推荐哪个、为什么。
3. **错误消息的写法**：中文，写清「原因」和「怎么办」，带上验收条件要求的信息。日期和时间用 `DateTimeFormatter` 固定格式。
4. 等用户选方案。

### 第 3 步：先写测试（不改实现）

1. **Service 的单元测试**（Mockito，照 `TraineeServiceTest` 的写法）：
   - **只看结果**：报错／不报错、错误消息、对象的字段。不要用 `verify` 检查内部调用了哪些方法
   - 「不报错」的测试用 `lenient()` stub。否则实现前 stub 不会被调用，Mockito 严格模式报 `UnnecessaryStubbingException`，测试会因为错误的原因失败
   - 「不同讲师」「不同日期」这类测试：把**错误的查询条件也 stub 成能查到冲突**。实现查错了条件就会报错，只看结果也能发现
   - 「先报哪个错」的测试：stub 一条会冲突的数据，只断言消息是先报的那个
2. **用户同意时**，加 `@DataJpaTest` + `@Import(Service 类...)`，在 H2 上用真实 Repository 走 Service 的公开方法，用 `count()` 确认「冲突时没保存」。
3. 测试需要新的 Repository 方法才能编译时，**先问用户**能不能只加这一行方法声明（不含业务逻辑）。不要自己先加。
4. 跑 `mvnw.cmd test`（Windows）或 `./mvnw test`，确认红绿分布：
   - 失败的**只有**「应该报错」的测试
   - 失败原因是 `Expecting code to raise a throwable`（应该报错但没报错），不是编译错误或别的异常
   - 「不报错」的测试和原有测试全部通过

### 第 4 步：实现

1. 只改用户同意的文件：一般是 Service，加上需要的 Repository 方法。
2. 遵守 CLAUDE.md：
   - 业务规则写在 Service
   - 违反规则抛 `BusinessException`
   - 「今天」用注入的 `Clock`
3. 关键的判断写上中文注释（例：重叠条件、为什么严格小于、为什么排除自己）。
4. 跑测试，确认全部通过。

### 第 5 步：贴证据

1. **测试结果**：贴 `Tests run: N, Failures: 0, Errors: 0` 和 `BUILD SUCCESS`，按测试类列出。
2. **git diff**：贴实现代码的 diff。测试文件太长时只写行数统计。
3. **画面确认**：
   - 用 TaskStop 停掉旧的应用，确认 8080 端口释放后，再在后台执行 `spring-boot:run`
   - 等日志出现 `Started TrainingManagerApplication`
   - 用 curl 登录 admin，按表单的字段（带上 `_csrf`）提交。中文参数用 **UTF-8 百分号编码**（Git Bash 直接传中文会变成 cp932 乱码）
   - 至少试：应该报错的一种、边界上不报错的一种、修改时只改无关字段的一种
4. **确认测试真的在测**（用户要求时）：
   - 先用 `git hash-object <文件>` 记下哈希值
   - 临时注释掉关键的一行，跑测试，贴出哪几条失败、失败原因是什么
   - 恢复原样，再跑一次，确认全部通过
   - 再算一次哈希值，确认和注释前一致。`git diff` 比较的是 HEAD，看不出注释前后的差别，所以用哈希比对
5. 列出还没做的事（`web/Feature.java` 改成 `done = true`、提交、PR），**等用户指示**。

## 读测试结果的小技巧

- 系统语言是日文时，Maven 控制台里的中文测试名会变成乱码。要从 `target/surefire-reports/TEST-*.xml` 读，并且把结果写进 UTF-8 文件再读。直接 `print` 到控制台会出 `UnicodeEncodeError`（cp932）。
- 用 `| Select-String "Tests run:"` 过滤输出之前，先确认测试真的跑了：编译失败时 `target/surefire-reports` 里还是**上一次的旧报告**，看文件时间戳。
- 示例脚本，用 `python -I - <输出文件>` 执行：

```python
import glob, sys, xml.etree.ElementTree as ET
lines = []
for f in sorted(glob.glob("target/surefire-reports/TEST-*.xml")):
    r = ET.parse(f).getroot()
    lines.append(f"== {r.get('name').split('.')[-1]} tests={r.get('tests')} fail={r.get('failures')} err={r.get('errors')}")
    for tc in r.findall("testcase"):
        fl = tc.find("failure") if tc.find("failure") is not None else tc.find("error")
        status = "FAIL" if fl is not None else "ok  "
        msg = (fl.get("message") or "").replace("\n", " ")[:160] if fl is not None else ""
        lines.append(f"  [{status}] {tc.get('name')}  {msg}")
open(sys.argv[1], "w", encoding="utf-8").write("\n".join(lines))
```

## 禁止事项

- **不要提前改代码**：第 1、2 步只读和分析；第 3 步只改测试。要往下一步走，先得到用户同意。
- **不要顺手改范围外的东西**：包括 `Feature.java`、Controller、模板、原有测试。发现范围外的问题就告诉用户，不要改。
- **不要绕过权限拒绝**：工具调用被拒绝时，不要换个命令达到同样目的。停下来说明想做什么、为什么，让用户决定。
- **不要没跑就报结果**：每个「通过／失败」都要有本次运行的输出作为证据。不要把旧报告当成本次结果。
- **测试里不要用 `verify` 检查实现细节**：只看报错／不报错和可观察的结果。原有测试里的 `verify` 不在范围内，不要动。
- **不要用 `AndIdNot(id)` 在查询里排除自己**：新增时 id 是 null，SQL 变成 `id <> NULL`，结果永远不成立，一条都查不到，检查就失效了。在 Java 里用 `Objects.equals` 过滤。
- **不要留下临时改动**：注释掉的代码、临时加的声明，确认完必须恢复，并用哈希值证明。
- **不要自己提交、push、开 PR**：等用户明确指示。
- **不要新增依赖库**；测试数据不用真实人名・邮箱，用 `@example.com`。
- 画面文字、错误消息、代码注释、测试方法名用中文。
