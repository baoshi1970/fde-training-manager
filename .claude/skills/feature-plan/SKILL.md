---
name: feature-plan
description: 按「调查（Spike）→ 出方案 → 分步实现（骨架 → 先写测试 → 实现）→ 画面确认 → 自查 → 整理 PR」的顺序，完成新功能的 GitHub issue，例如 CSV 导入学员（新类、新画面、文件上传）、学员进度看板（汇总查询、新画面）、新增实体和它的管理画面。用户说「按 feature-plan 做 #N」「先出方案再做 #N」，并且 issue 要新建类・画面时使用。在已有的 Service 上加一条报错／不报错的规则用 issue-tdd，只改文案・样式・文档的直接改。
---

# feature-plan：调查 → 出方案 → 分步实现 → 画面确认 → 自查 → 整理 PR

本仓库做一个新功能 issue 的标准流程，整理自 F10（#10 / PR #24）和 F9（#9 / PR #25）的做法、照 `issue-tdd` 做 #10 时卡住的地方（#26），以及用本 skill 试做 F9・F10・假想的新功能（新实体＋权限＋日期）时发现的问题。正文只写规则和泛化的例子；F9・F10 的具体做法和结论放在同目录的 `reference-f9-f10.md`，需要时再读。每个新功能都要按规则自己找出它的边界，不要照搬 F9・F10 的结论。

每一步结束都**停下来把结果给用户看，等用户同意再进下一步**。用户说「先不要改」时，就只做读和分析。

## 什么时候用

**判断标准**：issue 要新建类、新画面（文件上传、汇总查询、新的实体和管理画面、新的 Controller・模板）时使用。

| issue 的例子 | 用哪个 |
|---|---|
| #10 CSV 导入学员（解析器、上传画面、导入校验） | ✅ feature-plan |
| #9 学员进度看板（汇总查询、计算类、新画面） | ✅ feature-plan |
| 新增实体和它的管理画面（权限、日期、主页显示） | ✅ feature-plan |
| #7 排课冲突检查、#12 禁止重复报名、#21 名称超长报 500、#22 绕过权重合计检查 | `issue-tdd`：在已有的 Service 上加一条规则 |
| #16 ADMIN 也应该可以打分（`SecurityConfig` 的权限设置和画面，不新建类・画面） | 两个都不用 |
| 只改文案、样式、文档 | 都不用：直接改，跑测试即可 |

拿不准时，先问用户。

## 步骤

### 第 0 步：准备

1. 确认工作区干净（`git status`），记下当前分支。
2. 确认 issue 已经分配给用户（`gh issue view <N>`；找不到 `gh` 时，先确认是不是装了但不在 PATH 里，Windows 一般在 `C:\Program Files\GitHub CLI\gh.exe`）。没分配给用户、或分配给了别人时，停下来问用户，可能拿错了 issue。
3. **确认前提**：issue 写了「前提：#X 已合并」的（例：F9 要 #6・#8 先合并），用 `gh issue view`・`gh pr list` 确认。没合并的，告诉用户，商量能先做哪一部分（F9 先做了不依赖 F6・F8 实体的纯计算类）。
4. 在 `main` 上的话，先切分支，分支名按 issue 的「交付」规定（例：`f10-<GitHub ID>`）。issue 没有「交付」、或 GitHub ID 不知道时，问用户，不要猜。
5. **先跑一次测试，记下基线**：件数从 `target/surefire-reports/TEST-*.xml` 合计（脚本见 `../issue-tdd/SKILL.md` 的「读测试结果的小技巧」）。基线本来就有失败的，先告诉用户，不要开工。

### 第 1 步：调查（Spike，不改代码）

issue 写了「先做 Spike」的（例：#9），调查结果贴到 issue 里，等讲师确认后再出方案；没写的，报告给用户就行。

1. **读 issue**：背景、要做的事、格式、验收条件、提示，都要读。**背景里的使用场景**（例：#10「新一期开班一次登记几十名」）是后面判断「做到什么程度」的依据。
2. **读相关代码**：
   - **实体上的约束**（长度、必填、unique）。不检查的话，保存时数据库报错，画面出 500
   - **和已有入口的规则一致**：同一个实体已经有画面输入的，新入口（导入、批量）逐字段对照画面，不能比画面松：
     - 单行输入框 → 值里不能有换行
     - 空的输入框保存成什么（`""` 还是 `null`），新入口存成一样的
     - 格式校验（`@Email` 等注解）：用 `Validator` 调同一套规则，不另写正则
   - **同类 issue 已经定下的规则**，沿用，不另起一套（例：#23 的 `strip()` 去全角空格、长度按 `String.length()` 算、消息写「扩展汉字按 2 个字符计算」）
   - **能复用的类**：已有的计算类・结果类，不重复写同样的规则
   - **「今天」**：验收条件里有「今天」「当天」的，看现有写法。本仓库有两种：Controller 注入 `Clock`，算出 `LocalDate.now(clock)` 传给 Service（`HomeController`）；Service 自己注入 `Clock`（`ProgressService`）。选一种写进方案
   - **权限**：有「只有某角色能做」的验收条件时，读 `SecurityConfig`。主菜单（`Feature.java`）所有角色共用，所有角色都能看的画面不要放在 `/admin/**` 下（别的角色点菜单会 403）；写操作按 URL・HTTP 方法写明角色
   - 共通布局、主页（`HomeController`・`index.html`）、`Feature.java`、`DataInitializer`
3. **读已合并的相关 PR 的评审结论**（`gh pr list --state merged --json number,title`、`gh pr view <N> --json reviews,comments`，结果写进 UTF-8 文件再读），逐条写成「对本功能的影响」（例：评审定了「有记录的数据不能删」→ 本功能的统计不会因为删除而悄悄改变）。
4. 向用户报告：
   - **需要哪些数据**，现在有没有
   - **定义里的模糊点**，每条给建议和理由。以下几类每个功能都要想一遍：
     - 「没有输入」和「输入了空内容」分不分开（例：没选文件 vs 选了空文件）
     - 没有数据时显示什么（「—」、0、不显示）
     - 正好等于界限值时算哪边（`<` 还是 `≤`）
     - 显示用的取整和判断用的比较会不会矛盾（例：四舍五入后显示的值到了界限，却按「小于界限」标红）
     - 日期的边界：开始日当天、结束日当天、前一天、后一天、为空
     - 纯计算类收到不可能的输入（负数、部分 > 合计）：程序错误用 `IllegalArgumentException`，用户操作错误才用 `BusinessException`
   - **需要用户（讲师）决定的事项**，每条写明对应哪个验收条件（例：大小写区不区分、并发冲突怎么处理、上限多少）
   - **性能风险**（汇总类功能：按一条一条循环查询会变成 N+1）

### 第 2 步：出方案（不改代码）

先把方案全文给用户看，用户同意后再贴到 issue 评论里（`gh issue comment`）。之后实现和方案不一致时，**先改方案**：改动内容也先给用户看，同意后再用 `gh api` 改评论，保证方案和代码一致。贴评论、改评论都是对外操作，不要自己先做。

1. **需求编号表**：把 issue 的要求编上号（例：要做、格式-1〜3、AC1〜AC6），后面的表都用这个编号。
2. **要改的文件表**：「类型（新建／修改）・文件・定义的函数和用途・对应的需求编号」。每个文件都要对上需求编号，对不上的写理由（例：只为写明配置的上限而改 `application.properties`）。以下也要列进表里：
   - 新增实体的：`DataInitializer.resetToSeed()` 里的删除（按外键依赖顺序：引用别的表的先删）、要不要加种子数据
   - 要改已有画面的（例：主页显示新数据）：Controller 构造函数的变化、模板
   - `SecurityConfig` 的修改
   - `Feature.java`：改成 `done = true` 并填 path（没有这一行的就新增一行）。新功能的「主菜单可以点击」是验收条件，所以和 `issue-tdd`（第 5 步等用户指示）不同，放在做画面的那一步，先写 MockMvc 测试（断言页面里有 `href="/路径"`）再改
3. **已经定下来的事项**：第 1 步里用户决定的，逐条写上。
4. **偏离 CLAUDE.md 写法的地方写理由**（例：汇总用 `@Query`：CLAUDE.md 是「优先」方法名查询，`GROUP BY` 的多列汇总用方法名表达不了）。
5. **测试**：每个测试类测什么；测试数据放文件还是在代码里生成（见「小技巧」）。期望值要等第 4 步实测才知道的测试（例：上传超过上限时的状态码），写明在第 4 步之后补（见第 3 步）。
6. **范围外**：做不做、为什么。按背景里的使用场景判断，不要因为 AI 把问题看得太重就做多（例：使用场景里实际不会发生的极端输入，不做专门提示）。不做的，实测出的行为（状态码、数据有没有被登记）也用测试固定下来。
7. **实施顺序**：分成几步，每步写「内容・commit 消息」。依赖的 issue 没合并的，分阶段：先做不依赖它的部分（例：只收数字的纯计算类），合并后再做查询和画面，每个阶段各出一次方案。
8. 等用户（issue 要求时等讲师）确认方案。

### 第 3 步：分步实现（每一步：骨架 → 先写测试 → 实现）

按方案的实施顺序，一步一步做。每一步：

1. **先建骨架**，让测试能编译：
   - 新的 Service・计算类的方法：只写签名，方法体写 `throw new UnsupportedOperationException("未实现")`。不要返回 `0`・`null`・空列表当骨架：「应该返回 0 件」这类测试会在实现前就通过
   - 实体、Repository 接口：没法写成骨架，直接写最终的样子。`@Query` 的 JPQL 也在这时写好（JPQL 写错时，`@DataJpaTest`・`@SpringBootTest` 启动就会报错，先修好再看红灯）。它们的行为由 Service 和 `@DataJpaTest` 的测试确认。方案里列过的 Repository 方法不用再问；方案外要加的，照 `issue-tdd` 先问用户
   - Controller・模板：不建（MockMvc 得到 404），或者建骨架方法抛 `UnsupportedOperationException`（MockMvc 测试出错），两种都可以，红灯的样子不同（见下表）
   - 修改已有的类（`SecurityConfig`・主页・`Feature.java`・`DataInitializer`）：骨架阶段不改，当作实现的一部分
2. **写测试**（Service 用 Mockito，照 `TraineeServiceTest`；画面用 MockMvc，照 `LoginAndMenuTest`；查询用 `@DataJpaTest`）：
   - `verify(..., never()).save*` 确认「没保存」可以用，其他 `verify` 不要用（同 `issue-tdd`）。要确认**保存的内容**时，用 stub 记下来再断言：`lenient().when(repo.saveAll(any())).thenAnswer(inv -> { saved.addAll(inv.getArgument(0)); return inv.getArgument(0); })`
   - **权限**：两个方向都写：能做的角色 200／302，不能做的角色 403，并确认数据没变。POST 一律带 `.with(csrf())`：不带的话不管什么角色都是 403，「不能做的角色 403」会因为错误的原因通过
   - **日期**：Service 的测试直接传固定日期，或者传固定的 `Clock`（`Clock.fixed(...)`）。日期条件写在 Repository 查询里的，边界 Mockito 测不出来，用 `@DataJpaTest` 测。`@DataJpaTest` 里要用 `Clock` 的，在测试类里用 `@TestConfiguration` 提供（F9 试做时实测：不会自动加载）。画面测试里的「今天」用注入的 `Clock` 算
   - 改已有画面、往共用的 H2 里写数据的画面测试，类上加 `@Transactional`，测试结束自动回滚，不影响其他测试类
3. **跑测试，确认红灯**。新功能的测试全部是红的，这是正常的；要确认的是**每一条都因为「还没实现」而失败**（以下是本仓库实测的失败原因）：

   | 测试的写法 | 还没实现时的结果 |
   |---|---|
   | `assertThatThrownBy(...).isInstanceOf(BusinessException.class)` | 失败：`Expecting actual throwable to be an instance of: ...BusinessException but was: java.lang.UnsupportedOperationException: 未实现` |
   | `assertThatThrownBy(...).hasMessageContaining("第 3 行")`（不写类型） | 失败：`Expecting throwable message: "未实现" to contain: "第 3 行"` |
   | 直接调用，再断言返回值 | 出错（error）：`UnsupportedOperationException: 未实现` |
   | `assertThatCode(...).doesNotThrowAnyException()` | 失败：`Expecting code not to raise a throwable but caught "java.lang.UnsupportedOperationException: 未实现` |
   | MockMvc 访问还没有 Controller 的画面，期望 200 | 失败：`Status expected:<200> but was:<404>` |
   | MockMvc 访问骨架 Controller（抛 `UnsupportedOperationException`） | 出错（error）：`Request processing failed: java.lang.UnsupportedOperationException: 未实现` |
   | 已有画面（主页、主菜单）还没改，断言页面里有新内容 | 失败：`Response content Expected: a string containing "…"` |
   | `resetToSeed()` 还没删新表，断言重置后是 0 件 | 失败：`expected: 0L but was: 1L` |

   - **骨架阶段就通过的测试，要查原因**：
     - 测试本身测不出实现对不对的，改测试。实测：`assertThatThrownBy(...).isInstanceOf(RuntimeException.class)` 在骨架阶段就通过（`UnsupportedOperationException` 也是 `RuntimeException`），改成写具体的异常类型和消息
     - 测的是已有机制、在 Controller 之前就生效的，通过是正常的，在结果里注明原因后保留（例：未登录跳转登录页、不带 CSRF 403，Spring Security 在 Controller 之前就处理了）
   - **权限测试要确认真的在测**：临时删掉 `SecurityConfig` 里那一行，确认「不能做的角色 403」的测试变红，再恢复，用 `git hash-object` 确认和改之前一致
   - 不能是编译错误、`UnnecessaryStubbingException`、`NullPointerException` 这类测试本身的问题
   - 原有测试全部通过，件数和基线对得上
4. **实现**，跑测试，全部通过。
5. **期望值要等第 4 步实测才知道的测试**（例：上传超过上限时的状态码），第 4 步实测后再补写。这类测试没法先红，改用「临时改坏配置（例：把上限改大）→ 测试失败 → 恢复，`git hash-object` 一致」证明它有效。
6. **commit 前检查看不见的字符**（见「小技巧」）。
7. **贴结果，等用户确认后再 commit**：本步的测试件数、红灯→绿灯的结果、diff 的统计。用户同意后，确认 `git config user.email` 是用户要用的邮箱，按方案里的 commit 消息提交（不要 push）。
8. 用户要求时，**在新的会话里评审这一步**：写代码的会话自己评自己容易漏（#10 每一步都在新窗口里评审，查出了测试断言不足、依赖种子数据的问题）。给评审用的会话准备「方案的对应部分＋这一步的 commit」。

### 第 4 步：画面确认

1. **启动**：
   - 自己之前在后台启动过应用的，用 TaskStop 停掉，确认端口释放后再启动
   - 8080 被不是自己启动的进程占着时，不要去停它，问用户，或者换端口：`-Dspring-boot.run.arguments=--server.port=8081`（PowerShell 里整个参数要加引号）
   - 等日志出现 `Started TrainingManagerApplication`（后台启动时：`until grep -qE "Started TrainingManagerApplication|APPLICATION FAILED|BUILD FAILURE" app.log; do sleep 1; done`）
   - cookie 文件、日志放在这个功能专用的目录里，不要和别的会话共用（共用时 cookie 文件会被对方删掉）
2. **用 curl 登录**：先 `GET /login` 取 `_csrf`，带着它 `POST /login`。
   - **登录后要重新取 `_csrf`**：登录时 token 会换掉，登录前取的 token 提交会 403（实测）
   - 页面上有多个 `_csrf`（共通布局里的退出表单也有一个）时，同一个页面里的值是一样的，取哪个都行（实测）
   - 不同页面取的 `_csrf` 字符串不一样（每次输出都不同），但只要是登录后同一个会话里取的，都能用（实测）。不要用「字符串相同」来判断 token 对不对
   - 不带 `_csrf` 或值不对，403
3. **提交表单**：
   - 普通表单：`curl -s -c jar -b jar -d "_csrf=$TOKEN" -d "title=$ENC" -o result.html -w "%{http_code} %{redirect_url}\n" http://localhost:8081/路径`
   - 上传文件（multipart）：`curl -s -c jar -b jar --form-string "_csrf=$TOKEN" -F "file=@test.csv;type=text/csv" -o result.html -w "%{http_code} %{redirect_url}\n" http://localhost:8081/上传的路径`（`_csrf` 这种值用 `--form-string` 传，`-F` 会把以 `@`・`<` 开头的值当成文件）
   - 成功和失败时返回什么（302 重定向，还是 200 回到原画面显示错误），读自己的 Controller 后实测确认
4. **中文参数要事先编成 UTF-8 百分号编码，curl 的参数里只放 ASCII**：
   - `ENC=$(python -I -c "import urllib.parse,sys;print(urllib.parse.quote(sys.argv[1]))" "测试")`，再用 `-d "name=$ENC"` 传
   - 日文 Windows（cp932）的 Git Bash 里实测：`--data-urlencode "name=测试"` 和 `-d "name=测试"` 都会让服务器收到乱码，先用 Python 编码好再传才正常
   - 结果 HTML 里的中文，写进 UTF-8 文件再读（见「小技巧」）
5. **造数据**（汇总类功能）：种子数据里没有的（例：出勤、得分），用已有画面的 POST 造。先读那个画面的 Controller 和模板，确认参数名；id 从画面 HTML 里取，或按 `DataInitializer` 的插入顺序推。
6. **截图**：issue 的「交付」要求附截图。按验收条件逐条列出「观点・文件・内容・预期结果」，让用户截图。改了已有画面（主页等）的，也列进去。手动测试用的数据可以不提交，表里写清内容，别人能让 AI 再生成。
7. **性能**（汇总类功能）：
   - 用 SQL 条数证明不是 N+1：`@DataJpaTest(properties = "spring.jpa.properties.hibernate.generate_statistics=true")`，用 `emf.unwrap(SessionFactory.class).getStatistics()` 取统计；查询前 `em.clear()`（不清的话实体在缓存里，测不出 EAGER 关联的连带查询），`Statistics.clear()` 后执行，再看 `getPrepareStatementCount()`。人数不同（例：2 人和 20 人）条数相同才算通过（例：`ProgressQueryCountTest`）
   - 故意改成逐个查询，确认这个测试会变红，再恢复，用 `git hash-object` 确认和改之前一致
   - 数据量按实际可能的最大规模再测一次（别的功能会让数据变多的也要算进去，例：批量导入一次最多能登记多少条）。花时间的不放进 CI，PR 里写数字和测法
   - 测试里打印数字时只用 ASCII，中文在 cp932 的控制台里会乱码
8. 做完停掉自己启动的应用，确认端口释放（`netstat -ano` 里这个端口没有 `LISTENING`）。

### 第 5 步：自查

1. **对照方案**：方案里每一项都做了吗？有没有方案外的实现（做多了）？有的话，理由是什么，或者删掉。
2. **和方案的差异**：文件、行为、测试，和方案不一样的地方逐条写理由，并改方案（第 2 步的评论）。
3. **范围外的问题**：发现了就列出来，写清是哪个功能主导的（规则由原来的功能改，新功能跟着改），不要顺手改。
4. **和其他功能的衔接**：新功能的数据会流到哪些画面（例：新登记的数据出现在别的一览、下拉框、汇总里），在那些画面上会不会出错。
5. **跑全部测试**：`mvnw.cmd clean test`，从 XML 确认全部通过。

### 第 6 步：整理 PR 正文（等用户指示再 push・开 PR）

参考 PR #25（F9，已合并）和 PR #24（F10，写这个 skill 时还在评审中）的结构：

- `Closes #N`、方案的链接
- 做了什么：需求编号表、修改一览（文件・内容・对应需求）
- 与方案的差异（含理由）、已知限制・范围外（含判断）
- 怎么验证的：单元测试（按测试类：件数・观点）、画面确认（观点・内容・预期结果・截图）、性能（SQL 条数・数据量）
- 关联的其他 issue・功能
- 关键 prompt：按阶段（调查・方案・实现・测试・画面确认）整理，写上「→ 结果或要点」。AI 的说法和实测不一样的地方单独列出

列出还没做的事（push、开 PR、看板移到 In review），**等用户指示**。

## 小技巧

- **看不见的字符**：源码里的 BOM、全角空格、扩展汉字、不换行空格，写成 `\uFEFF`・`\u3000`・`\uD842\uDFB7`・`\u00A0` 这样的转义。AI 写文件时可能把转义写成字符本身，屏幕上看不出来，测试也照样通过（#10 的实际开发和本 skill 的试做里都发生过）。commit 前对**本次改的文件**按字节检查：

  ```bash
  ( cd "$(git rev-parse --show-toplevel)" && { git diff --name-only --diff-filter=d HEAD; git ls-files -o --exclude-standard; } \
    | sort -u | grep '\.java$' | LC_ALL=C xargs -r grep -nP '\xEF\xBB\xBF|\xE3\x80\x80|\xC2\xA0|[\xF0-\xF4]' )
  ```

  - 能查到：改过的、已 add 的、新建还没 add 的文件；在子目录里运行也一样（先回到仓库根，`git diff` 和 `git ls-files` 的路径基准才一致）；删除的文件不查（`--diff-filter=d`）。中文和 é 这类字母不会命中
  - **有输出就是有问题**，没有输出才算通过。退出码不可靠（命中是 0，没命中是 123，没有改过的文件是 0），所以不要用 `&&` 把它和 `git commit` 连在一起
  - 不要查整个 `src/`：已有的文件里本来就有全角空格（例：`EvaluationService` 的注释、#23 的测试数据）。改到这些文件时也会命中，逐条看是不是自己新写的
- **测试数据放文件还是在代码里生成**：
  - 带 BOM、GBK 等编码：在代码里用字节数组生成，做成文件的话编辑器保存时编码容易被改掉
  - 换行：CRLF・LF・单独的 CR 三种都要测，在代码里生成（`"a\r\nb"`・`"a\rb"`）。仓库的 `.gitattributes` 是 `eol=lf`，放在文件里提交时会被转成 LF
  - 500 行、1MB 这类大数据：用循环生成，不往仓库提交大文件
- **要真正启动服务器的测试**（例：上传大小上限，#10 的方案里写明 MockMvc 不走这个检查）：用 `@SpringBootTest(webEnvironment = RANDOM_PORT)` ＋ `TestRestTemplate`。
  - 这时会启动新的 Spring 上下文，`spring.datasource.url` 用单独的 H2 库名（例：`jdbc:h2:mem:size-limit-test`），避免重建其他测试共用的表
  - `TestRestTemplate` 要自己登录：`GET /login` 取 `_csrf` → `POST /login` → 登录后重新取 `_csrf`。本仓库用 `spring-session-jdbc`，session cookie 名是 `SESSION`，不是 `JSESSIONID`
- **测试类名用英文**（包括 `@Nested` 内部类），中文显示名放 `@DisplayName`。`@Nested` 会生成 `外部类$内部类.class`，中文类名在日文 Windows（MS932）上已确认 clean 构建会失败（`328e1cc`）。测试方法名可以用中文
- **读测试结果**：
  - 日文 Windows（cp932）上 Maven 控制台的中文测试名会乱码，从 `target/surefire-reports/TEST-*.xml` 读，结果写进 UTF-8 文件再看。示例脚本见 `issue-tdd` 的「读测试结果的小技巧」
  - `target/surefire-reports` 里可能有上一次的旧报告：编译失败时，以及用 `-Dtest=类名` 只跑一部分时（其他类的 XML 都是旧的）。看文件的修改时间（XML 里没有时间戳属性），只合计这次的
- **和 main 冲突时**：不要关闭 PR，在分支上合并 main、解决冲突后 push。`Feature.java` 里相邻两行的功能同时做完时容易冲突（F9・F10），两边都保留「已实装」

## 禁止事项

- **不要提前改代码**：第 1、2 步只读和分析。方案没确认之前不写代码。
- **不要做方案外的东西**：要加的，先说理由，等用户同意并改方案。发现范围外的问题就告诉用户，不要顺手改。
- **不要绕过权限拒绝**：工具调用被拒绝时，不要换个命令达到同样目的。停下来说明想做什么、为什么，让用户决定。
- **不要没跑就报结果**：每个「通过／失败」「SQL 条数」「耗时」都要有本次运行的输出作为证据。AI 推测的行为（例：#10 超过 1MB 时显示什么，AI 前后说了 500・413・403）要实测后再写进方案和 PR。
- **不要留下临时改动**：故意改坏的代码、临时加的数据，确认完必须恢复，并用 `git hash-object` 的哈希值证明和改之前一致。
- **不要自己 commit、push、开 PR**：commit 等用户确认这一步之后；push 和开 PR 等用户明确指示。
- **不要新增依赖库**（除非用户明确同意，见 CLAUDE.md）；测试数据不用真实人名・邮箱，用 `@example.com`。
- 不要写只在单个实例内存里有效的状态（static 缓存等），部署环境是多实例（见 CLAUDE.md）。
- 画面文字、错误消息、代码注释、测试方法名用中文。测试类名用英文。
