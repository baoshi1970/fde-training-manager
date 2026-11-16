# F6 出勤登记 设计文档集

> 范围：分支 `feature/F6-attendance-piao` 追加开发的内容（F6 出勤登记）。F1〜F5 的既有功能只在「与 F6 的接点」处提及。

## 文档一览

| No. | 文档 | 日文名称 | 阶段 |
|---|---|---|---|
| 01 | [功能概要・需求定义](01-功能概要・需求定义.md) | 機能概要・要件定義書 | 上流 |
| 02 | [画面设计书](02-画面设计书.md) | 画面設計書 | 基本设计 |
| 03 | [表定义书](03-表定义书.md) | テーブル定義書 | 基本设计 |
| 04 | [接口设计书（URL・请求）](04-接口设计书.md) | インターフェース設計書 | 基本设计 |
| 05 | [程序设计书](05-程序设计书.md) | プログラム設計書 | 详细设计 |
| 06 | [单体测试规格书](06-单体测试规格书.md) | 単体テスト仕様書 | 测试 |
| 07 | [结合测试规格书](07-结合测试规格书.md) | 結合テスト仕様書 | 测试 |
| 08 | [追踪矩阵](08-追踪矩阵.md) | トレーサビリティマトリクス | 横断 |

## 变更对象一览（对应 git 改动）

| 类别 | 文件 | 新增/修改 |
|---|---|---|
| 实体 | `domain/Attendance.java`、`domain/AttendanceStatus.java` | 新增 |
| Repository | `repository/AttendanceRepository.java` | 新增 |
| Service | `service/AttendanceService.java` | 新增 |
| Service | `service/SessionService.java`（删除课次时连带删出勤） | 修改 |
| Controller | `web/AttendanceController.java` | 新增 |
| 菜单 | `web/Feature.java`（F6 → done） | 修改 |
| 画面 | `templates/attendance/list.html`、`form.html` | 新增 |
| 画面 | `templates/sessions/list.html`（加「出勤」按钮） | 修改 |
| 初始化 | `config/DataInitializer.java`（`resetToSeed` 先删出勤） | 修改 |
| 测试 | `AttendanceServiceTest`、`AttendanceControllerTest`、`SessionServiceTest`（追加 2 例） | 新增/修改 |

## 已知事项（供评审时确认，未擅自修改）

1. **「未记录」人数的算法**：画面用「报名人数 − 出勤记录条数」计算（`form.html`）。如果某学员在登记出勤后被取消报名，出勤记录仍在，「未记录」会少算，极端情况下为负数。当前版本是否有「取消报名」功能需要确认；若有，应在取消报名时一并处理出勤，或改为只统计报名者的记录。
2. **出勤以「课程」为单位判定报名者**：课次（CourseSession）属于课程（Course），报名（Enrollment）也是学员 × 课程，所以同一课程的所有课次都显示同一批报名者。
3. **并发保存**：同一课次同时被两人保存时，依赖唯一约束 `(session_id, trainee_id)` 兜底，当前没有对约束冲突做友好提示（会出 500）。
