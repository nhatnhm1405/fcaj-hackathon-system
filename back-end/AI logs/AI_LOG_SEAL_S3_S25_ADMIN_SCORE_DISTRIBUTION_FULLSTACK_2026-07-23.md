# AI LOG — S3/S25 completed-flow parity + Admin score-distribution histogram + deterministic judge-score seed — FULLSTACK — 2026-07-23

> Phiên làm việc ngày **2026-07-23** (Asia/Saigon) bắt đầu bằng việc lấy context từ
> commit/source của `nhatnhm1405`, tiếp tục sửa seed **S3** để đi qua đúng production
> workflow từ cut-point **S25**, sau đó thiết kế và triển khai dashboard thống kê điểm
> cho System Admin dưới dạng histogram. Cuối phiên, seed vòng loại được chỉnh thành
> **2 judge/submission** và phân bố chính xác `1,2,3,5,6,8,3,2` trên 8 bin từ
> `20–30` đến `90–100`, áp dụng chung cho cả S25 và S3.
>
> Deliverable đã code, compile/test/smoke-seed và verify DB thật. Toàn bộ thay đổi vẫn
> **chưa commit/chưa push**, nằm trên branch `NhatNHM-fix-s3-seed`, base đúng
> `origin/develop@5b55e57`.

---

## 1. Metadata và trạng thái phiên

| Thuộc tính | Giá trị |
|---|---|
| Ngày | 2026-07-23 |
| Timezone | Asia/Saigon |
| Repository | `seal-hackathon-system` |
| Working directory | `D:\FPTU Documents\S5 SU26\SWP391\seal-hackathon-system` |
| Branch cuối phiên | `NhatNHM-fix-s3-seed` |
| HEAD/base | `5b55e57805cd6bc18154588c09d74abb4360473e` |
| Upstream base | `origin/develop` (cùng commit `5b55e57`) |
| So sánh `origin/main...origin/develop` | main có 1 commit riêng, develop có 31 commit riêng |
| Commit/push trong phiên | Không |
| Database local cuối phiên | `seal_hackathon`, fresh S3, event `COMPLETED` |
| Backend process cuối phiên | Không chạy ở port 8080 |
| Stash backup | `stash@{0}: On main: wip: align S3 with completed S25 workflow` |

### 1.1. Git status trước khi tạo AI log

```text
## NhatNHM-fix-s3-seed...origin/develop
 M back-end/src/seal-api/src/main/java/com/seal/hackathon/config/seed/DemoScenario.java
 M back-end/src/seal-api/src/main/java/com/seal/hackathon/controller/AdminController.java
 M back-end/src/seal-api/src/main/java/com/seal/hackathon/repository/ScoreRepository.java
 M back-end/src/seal-api/src/main/java/com/seal/hackathon/service/RoundResultService.java
 M front-end/src/seal-web/src/features/events/AdminEventsPage.tsx
 M front-end/src/seal-web/src/shared/api/admin.ts
?? back-end/src/seal-api/src/main/java/com/seal/hackathon/dto/response/AdminScoreDistributionResponse.java
?? back-end/src/seal-api/src/main/java/com/seal/hackathon/service/AdminScoreDistributionService.java
?? back-end/src/seal-api/src/main/java/com/seal/hackathon/service/ScoreNormalization.java
?? back-end/src/seal-api/src/test/java/com/seal/hackathon/service/AdminScoreDistributionServiceTest.java
?? front-end/src/seal-web/src/features/events/EventScoreDistribution.tsx
```

File AI log hiện tại được tạo sau snapshot trên nên sẽ xuất hiện thêm trong Git status.

---

## 2. Timeline yêu cầu và quyết định

Phiên có nhiều pha nối tiếp nhau; các quyết định sau phụ thuộc trực tiếp vào kết quả
điều tra của pha trước:

1. **Lấy context theo commit/source của `nhatnhm1405`.** Xác định identity Git,
   commit liên quan đến seed, assignment, frontend split và event admin.
2. **Sửa S3 khớp S25 nhưng đã hoàn tất workflow.** S3 phải bắt đầu đúng tại cut-point
   của S25 (final đã chấm đủ, chưa calculate ranking), sau đó gọi production services để
   calculate ranking, publish, generate/announce prizes và complete event.
3. **Bảo toàn thay đổi khi đang ở main.** Giải thích việc checkout khi working tree dirty,
   tạo stash backup, đưa thay đổi sang branch mới tuyến tính từ latest `origin/develop`.
4. **Xác minh raw score có đủ từng judge hay không.** Truy vết entity/repository/service:
   raw `Score` vẫn còn theo `(submission, judge, criteria)`, không bị RoundResult ghi đè.
5. **Thảo luận visualization trước, chưa code.** Ý tưởng grouped bar bị user từ chối;
   chốt hướng histogram/phổ điểm tương tự phổ điểm thi.
6. **Chốt bố cục UI.** Filter trên cùng, histogram full-width, thống kê và drill-down
   nằm bên dưới (không đặt panel bên phải).
7. **Lên plan rồi code fullstack.** Tạo Admin API, normalization dùng chung, Recharts
   histogram, filter, statistics, click bin xem từng team/judge.
8. **Tinh chỉnh UI theo screenshot thật.** Sửa AVG/MED đè nhau, sửa stat card cuối rơi
   lẻ xuống dòng, tăng màu cho các stat card nhưng giữ histogram màu xanh.
9. **Sửa seed judge coverage.** Phát hiện develop mới giới hạn mỗi judge chỉ một track
   trong cùng round; 4 judge/4 track làm prelim chỉ còn 1 judge/submission. Seed tăng lên
   8 prelim judge, đúng 2 judge/track/submission.
10. **Mở rộng và sau đó chốt chính xác phổ điểm.** Ban đầu trải từ 37–98.75; user yêu
    cầu dãy cụ thể `1 2 3 5 6 8 3 2`, nên seed dùng 30 target score deterministic.
11. **Đồng bộ S25.** Xác nhận S25 và S3 dùng chung toàn bộ roster/assignment/submission/
    score trước điểm rẽ; smoke-seed S25 trên schema riêng, query DB, rồi dọn schema verify.
12. **Tạo AI log đầy đủ.** Ghi lại cả success, defect, recovery, caveat và trạng thái Git/DB.

---

## 3. Context Git của `nhatnhm1405`

### 3.1. Identity

Tên GitHub/user được yêu cầu: `nhatnhm1405`.

Identity Git được map từ repository:

```text
Nhat Nguyen <nghminhnhat2006@gmail.com>
```

### 3.2. Các commit gần và liên quan trực tiếp

| Commit | Ngày | Ý nghĩa trong context phiên |
|---|---|---|
| `5b55e57` | 2026-07-22 | Merge PR #484, latest `origin/develop`, base branch hiện tại |
| `ffdb80a` | 2026-07-18 | AI log cho Sprint 3 merge/risk fixes |
| `a711959` | 2026-07-18 | Sprint 3 merge report develop → main |
| `d7db349` | 2026-07-18 | Fix team active khi còn entry ở event chưa completed |
| `cedc251` | 2026-07-18 | Cho phép override `ddl-auto` bằng `DDL_AUTO` |
| `de62fab` | 2026-07-18 | Frontend unit tests cho providers/eventUtils/round timer |
| `8644eb2` | 2026-07-18 | Thêm `topic` cho HackathonEvent và Admin UI |
| `62d2361` | 2026-07-18 | Seed default scoring criteria template |
| `88d0be1` | 2026-07-18 | Thu gọn demo scenario còn S1/S25/S3 |
| `912910c` | 2026-07-18 | Dedupe TeamAccessGuard, reuse eventUtils trong AdminEventsPage |
| `7a924fc` | 2026-07-18 | Split frontend apiClient theo domain |
| `817a7ba` | 2026-07-18 | Split assignment service thành staff/mentor/judge services |

`git log --all` cũng hiện hai commit object `c9c3971`/`aeadf32` ngày 2026-07-23;
đây là object nội bộ được tạo bởi stash, không phải commit đã đưa lên branch.

### 3.3. Source context đã đọc

Các khu vực chính được truy vết trước khi sửa:

- Seed: `DemoScenario`, `DemoFixtures`, `DemoSeeder`, `application.properties`.
- Scoring: `Score`, `ScoringCriteria`, `ScoringService`, `RoundResultService`,
  `JudgeScoringCompletenessService`.
- Assignment: `JudgeAssignmentService`, `JudgeAssignment`, repository/test liên quan.
- Event lifecycle: `HackathonEventService`, `PrizeService`, `RoundTimerService`.
- Admin: `AdminController`, `AdminEventCsvExportService`, `AdminEventsPage`.
- Frontend API: domain modules dưới `shared/api`, đặc biệt `admin.ts`.
- Existing component system: `PixelComponents`, `PixelCard`, color token `C`.
- Chart dependency: `recharts@2.15.2` đã có sẵn; không cài package mới.

---

## 4. Git/worktree safety và tạo branch tuyến tính từ develop

### 4.1. Vấn đề ban đầu

Các sửa S3 đầu tiên được thực hiện khi working tree đang ở `main` và chưa commit. User hỏi
nếu checkout sang `develop` có mất thay đổi hay không.

Kết luận đã trao đổi:

- Git thường giữ thay đổi chưa commit khi checkout nếu file không conflict.
- Nếu target branch thay đổi cùng vùng file, checkout có thể bị chặn.
- Cách an toàn cho trường hợp này là tạo backup stash, cập nhật ref develop, tạo branch mới
  trực tiếp từ latest `origin/develop`, sau đó mang thay đổi sang.

### 4.2. Kết quả

- Tạo branch: `NhatNHM-fix-s3-seed`.
- Base/HEAD: `origin/develop@5b55e57`.
- Latest develop lúc đó đi trước main khoảng 31 commit (main có 1 merge commit riêng).
- Thay đổi giữ ở trạng thái **unstaged, uncommitted**.
- Stash backup vẫn giữ nguyên:

```text
stash@{0}: On main: wip: align S3 with completed S25 workflow
```

Không thực hiện commit/push vì user chưa yêu cầu. Không dùng `git reset --hard`, không xoá
stash và không ghi đè thay đổi ngoài scope.

---

## 5. S3 bắt đầu từ đúng cut-point S25 rồi hoàn tất bằng production services

### 5.1. Cut-point chung

S25 được định nghĩa là:

- Event `IN_PROGRESS`.
- Prelim đã `FINALIZED`, có published results để chọn 8 finalist.
- Final round `ACTIVE`.
- 8 finalist đã submit.
- Mọi final submission đã được 3 judge chấm đủ 5 criteria, `isDraft=false`.
- Contest/Judging timers đã expired.
- Chưa có final RoundResult, chưa calculate final ranking, chưa có prize.

S3 được sửa để seed **đúng cùng state trên**, rồi tiếp tục thay vì tự ghi state cuối bằng fixture.

### 5.2. Production workflow S3 gọi sau cut-point

```java
roundResultService.finalizeRound(eventId, finalRoundId, coordinatorId);
roundResultService.publishResults(eventId, finalRoundId);
prizeService.autoGenerate(eventId, topN = 3);
prizeService.announce(eventId, coordinatorId);
hackathonEventService.completeEvent(eventId);
```

Lợi ích so với hand-written fixture:

- Dùng đúng normalization/ranking production.
- Chạy completeness gate thật.
- Publish notification thật.
- Prize generation/announcement và audit `AWARD_PRIZE` thật.
- Complete event qua state machine thật.
- Lock/deactivate participant/judge theo logic lifecycle thật.
- Tạo Participant History Snapshot theo hook production thật.

### 5.3. Status/window/timer

- `eventStatusFor`: S25/S3 cùng bắt đầu `IN_PROGRESS`.
- `roundStatusFor`: prelim `FINALIZED`, final `ACTIVE`.
- `windowFor`: S25/S3 dùng cùng time window.
- Cả hai seed cùng 4 expired timers trước điểm rẽ.
- S25 return ngay trước calculate final ranking.
- S3 đi tiếp qua service calls ở trên.

### 5.4. Smoke result sau sửa

Log S3 thành công:

```text
S3 seeded from the S2.5 cut-point — COMPLETED event,
15 teams across 4 tracks, 8 published final results, 3 announced prizes.
```

---

## 6. Sự cố database trong lần smoke đầu và cách khôi phục

### 6.1. Sự cố

Lần smoke isolated đầu tiên đặt biến môi trường `DB_URL`, nhưng
`application.properties` khi đó dùng datasource URL hard-code và **không đọc `DB_URL`**.
Vì vậy lệnh dự kiến chạy `create-drop` trên schema verify lại thực tế chạm schema local mặc
định `seal_hackathon`.

Đây là defect thao tác nghiêm trọng trong phiên và đã được báo minh bạch cho user ngay khi
phát hiện. Dấu hiệu phát hiện là JDBC URL trong startup log vẫn trỏ tới schema mặc định.

### 6.2. Recovery

- Dừng và xác nhận không còn process giữ port/backend.
- Reseed lại local bằng S3 với `DDL_AUTO=update` ở thời điểm recovery.
- Sau khi branch chuyển sang latest develop và seed được hoàn thiện, local được reset có chủ
  đích lần nữa bằng `DDL_AUTO=create`, `SEED_SCENARIO=S3` theo yêu cầu user.
- Các smoke schema về sau luôn override bằng Spring argument tường minh:
  `--spring.datasource.url=jdbc:mysql://.../<verify-schema>...`.

### 6.3. Trạng thái cuối

- Schema local chính `seal_hackathon` là fresh S3 mới nhất.
- Event duy nhất `COMPLETED`.
- Schema verify S25 được tạo tách biệt, query xong và đã drop.
- Không ghi credential vào AI log này.

---

## 7. Điều tra dữ liệu score và khoảng trống của Admin hiện tại

### 7.1. Raw score có tồn tại đầy đủ không?

Có. Entity `Score` lưu một row cho từng bộ:

```text
(submission_id, judge_user_id, criteria_id)
```

Unique constraint `uq_score` đảm bảo một judge chỉ có một score cho một criterion của một
submission. `Score` chứa:

- submission;
- judge (`User`, không phải ID của JudgeAssignment);
- criterion;
- raw `value`;
- comment;
- `isDraft`;
- timestamps.

`JudgeAssignment` kiểm soát judge nào được phép/phải chấm round-track cell; raw score liên kết
trực tiếp tới judge user. Khi tạo `RoundResult`, raw rows **không bị xoá**. Vì vậy nếu một
submission được 2 judge chấm 5 criteria thì DB giữ 10 raw `Score` rows.

### 7.2. Ba cấp dữ liệu cần phân biệt

| Cấp | Một sample là gì? | Ví dụ 2 judge × 5 criteria |
|---|---|---:|
| Raw criterion score | 1 judge × 1 criterion × 1 submission | 10 rows |
| Judge evaluation | Weighted normalized total của 1 judge cho 1 submission | 2 samples |
| Submission result | Panel average/final RoundResult của submission/team | 1 sample |

### 7.3. Công thức production

Per-criterion normalization:

```text
criterionPercent = value / maxScore × 100
```

Per-judge weighted evaluation:

```text
judgePercent = 100 × Σ(weight × value / maxScore) / Σ(weight)
```

Submission result:

```text
submissionResult = average(judgePercent của toàn bộ assigned judges)
```

### 7.4. Khoảng trống của API/export cũ

- `GET /api/scores/submission/{submissionId}` chỉ cho `JUDGE` hoặc
  `EVENT_COORDINATOR`, không cho System Admin.
- Nếu frontend admin gọi endpoint trên cho từng submission sẽ tạo N requests và còn sai
  authorization boundary.
- CSV `AdminEventCsvExportService` có EVENT/ROUND/TRACK/MENTOR/JUDGE/TEAM/MEMBER/
  SUBMISSION/RESULT/PRIZE nhưng **không có raw SCORE rows**.
- Admin Events detail trước phiên chỉ có event header/status/action, không có analytics.

Quyết định: tạo endpoint read-only riêng dưới `/api/admin`, không nới quyền scoring endpoint.

---

## 8. Thảo luận visualization và thiết kế được chốt

### 8.1. Hướng bị từ chối

Đề xuất ban đầu là grouped bar theo team: mỗi judge một bar, panel average là line. User muốn
biểu đồ phổ điểm giống thống kê điểm thi, nên grouped bar không được dùng làm main view.

### 8.2. Hướng cuối cùng: histogram

Dashboard có 3 metric:

1. **Judge evaluations** (mặc định): mỗi sample là một judge-total đã normalize cho một
   submission. Hai judge → hai samples.
2. **Submission results**: mỗi team/submission một final panel score (`RoundResult`).
3. **Criteria scores**: raw score của criterion đang chọn, normalize về 0–100, mỗi judge một
   sample.

Điểm luôn đi vào 10 bin cố định:

```text
0–10, 10–20, 20–30, ..., 90–100
```

Score 100 được đưa vào bin cuối. Filter gồm round, track, metric; criteria dropdown chỉ hiện
khi chọn `CRITERIA_SCORE`. Default round là final round nếu event có final, nếu không dùng
round cuối theo order.

### 8.3. Bố cục được user chốt

```text
Event detail header

Round filter | Track filter | Metric filter | optional Criterion

┌─────────────────────────────────────────────────────────────┐
│ Full-width histogram                                       │
│ AVG / MED legend + vertical reference line                 │
└─────────────────────────────────────────────────────────────┘

[Samples] [Teams] [Judges] [Average] [Median] [StdDev]
[Min-Max] [<50] [>=80]       (thực tế desktop ép cùng một hàng)

Selected range table (chỉ xuất hiện khi click bar)
Team | Track | Judge/Rank | Score | Delta Average
```

Các component thống kê phải nằm **dưới histogram**, không đặt panel bên phải.

---

## 9. Backend implementation của Admin score distribution

### 9.1. Endpoint và authorization

Endpoint mới:

```http
GET /api/admin/events/{eventId}/score-distribution
    ?roundId={optional}
    &trackId={optional}
    &metric=JUDGE_EVALUATION|SUBMISSION_RESULT|CRITERIA_SCORE
    &criteriaId={optional}
```

Endpoint nằm trong `AdminController`, kế thừa class-level:

```java
@PreAuthorize("hasRole('SYSTEM_ADMIN')")
```

Service từ chối event chưa `COMPLETED`:

```text
Score analytics are available only after the event is completed.
```

Điều này giữ dashboard đúng mục đích hậu kiểm sau cuộc thi. S25 đang `IN_PROGRESS` nên chưa
hiện histogram Admin; dữ liệu score của S25 sẽ hiện sau khi workflow hoàn tất/complete.

### 9.2. DTO response

`AdminScoreDistributionResponse` chứa:

- event/metric/selected IDs;
- round options;
- track options;
- criteria options;
- 10 histogram bins (lower/upper/midpoint/label/count/percentage);
- statistics;
- observations để frontend drill-down mà không gọi thêm N requests.

Statistics gồm:

- sample count;
- distinct team count;
- distinct judge count;
- average;
- median;
- population standard deviation;
- minimum/maximum;
- count/percentage `< 50`;
- count/percentage `>= 80`.

Observation giữ team, track, judge, criterion, rank, score và difference-from-average tùy
metric.

### 9.3. `AdminScoreDistributionService`

Service thực hiện:

- validate event completed;
- parse/validate metric;
- validate round/track/criteria thuộc event/round;
- resolve default final round;
- map team → track từ `TeamEventEntry`;
- group raw Score theo `(submission, judge)` cho Judge Evaluation;
- normalize raw criterion khi dùng Criteria Score;
- đọc RoundResult cho Submission Result;
- tính statistics và bins;
- sort observations theo score giảm dần.

### 9.4. Tránh N+1

`ScoreRepository` thêm query:

```java
findAllFinalizedByRoundWithDetails(roundId)
```

JPQL dùng `JOIN FETCH` cho submission, team, judge và criteria; đồng thời lọc
`isDraft=false` tại DB. Đây là đường đọc chính của analytics, tránh lazy N+1 khi event có
nhiều score rows.

### 9.5. Một nguồn scoring math

Tạo package-private utility `ScoreNormalization`:

- `weightedPercent(Collection<Score>)`;
- `criterionPercent(Score)`.

`RoundResultService.finalizeRound` được refactor dùng chính utility này thay cho block
normalization riêng. Analytics và ranking vì vậy không thể vô tình dùng hai công thức khác
nhau.

### 9.6. Unit test mới

`AdminScoreDistributionServiceTest` có 3 test:

1. Judge evaluation dùng đúng weighted ranking normalization, tạo đúng histogram/stats.
2. Criteria score chỉ normalize criterion được chọn.
3. Event chưa completed bị reject.

Targeted run cùng `RoundResultServiceTest`:

```text
Tests run: 5, Failures: 0, Errors: 0
```

---

## 10. Frontend implementation và UI refinements

### 10.1. API client

`shared/api/admin.ts` thêm:

- `ScoreDistributionMetric` union;
- response/bin/statistics/observation interfaces;
- `adminApi.getEventScoreDistribution(eventId, filters)`.

BigDecimal backend được nhận dưới dạng number; các statistics có thể null khi sample count
bằng 0 và frontend xử lý bằng dấu `—`.

### 10.2. Component mới

`EventScoreDistribution.tsx` dùng Recharts:

- `ResponsiveContainer` + `BarChart`;
- numeric X axis 0–100 với tick label theo bin;
- integer Y axis frequency;
- tooltip count/score range;
- AVG/MED reference lines;
- click bar để chọn bin;
- selected observations table;
- loading/refreshing/error/empty states;
- horizontal overflow cho chart/stat cards trên màn hình nhỏ.

`AdminEventsPage` chỉ mount component khi selected event có status `COMPLETED`, đặt sau event
detail card và trước all-events list.

### 10.3. Sửa lỗi UI từ screenshot thật

User gửi screenshot và chỉ ra hiển thị lỗi/thiếu màu. Các fix:

1. **AVG và MED trùng giá trị:** label của hai ReferenceLine đè nhau. Chuyển label sang hai
   chip legend độc lập ở góc trên chart. Nếu hai số chênh dưới 0.1, chỉ vẽ một vertical line
   nhưng vẫn hiện đủ hai chip.
2. **Stat card thứ 9 rơi lẻ xuống hàng:** desktop đổi thành grid đúng 9 cột, min-width 1080;
   màn hình nhỏ cuộn ngang, không tạo một card cô lập ở dòng kế.
3. **Stat cards quá đơn sắc:** dùng 9 accent khác nhau (green, blue, purple, cyan, yellow,
   orange, pink, rose, lime), subtle gradient/background/border/glow theo accent.
4. **Histogram:** theo correction của user, histogram **vẫn giữ màu xanh**, không tô rainbow.

### 10.4. Drill-down semantics

- Judge metric: cột “Judge” hiện tên judge thật vì đây là System Admin view.
- Submission Result: cột đó đổi thành “Rank”.
- Delta Average có dấu `+/-` và màu green/red.
- Bin cuối dùng inclusive upper bound để score 100 xuất hiện đúng.

## 11. Thiết kế lại seed: hai judges và phân bố điểm chính xác

### 11.1. Nguyên nhân vòng loại chỉ có một judge/submission

Rule hiện tại của develop không cho một judge chấm nhiều track trong cùng một round. Seed cũ có
4 internal judges cho 4 tracks, vì vậy mỗi track chỉ nhận đúng một judge và mỗi submission vòng
loại chỉ có một lượt đánh giá. Điều này không khớp kỳ vọng nghiệp vụ “ít nhất hai người chấm”.

Seed được đổi như sau:

- thêm hằng số `PRELIM_JUDGES_PER_TRACK = 2`;
- tạo thêm `judge5` đến `judge8`, nâng pool internal judges lên 8 người;
- chia 8 judges thành 4 panel, mỗi panel gồm 2 judges phụ trách một track;
- vòng chung kết vẫn giữ panel 3 người: `judge1`, `judge2`, `guestJudge`;
- mọi score, assignment và evaluation của S25 được tạo trước nhánh rẽ lifecycle, do đó S3 và
  S25 dùng chung chính xác bộ dữ liệu chấm điểm.

Số record sau thay đổi:

| Phần thi | Submissions | Judges/submission | Criteria | Raw score rows |
|---|---:|---:|---:|---:|
| Vòng loại | 15 | 2 | 5 | 150 |
| Chung kết | 8 | 3 | 5 | 120 |
| **Tổng** |  |  |  | **270** |

### 11.2. Hai lần điều chỉnh phổ điểm

Lần đầu, seed dùng công thức trải điểm khoảng 37–98.75. Phổ vòng loại khi đó là:

```text
30-40: 1
40-50: 5
50-60: 5
60-70: 5
70-80: 5
80-90: 5
90-100: 4
```

User sau đó yêu cầu chính xác dãy `1 2 3 5 6 8 3 2`. Dãy này được diễn giải theo tám bin liên
tiếp từ 20–30 đến 90–100, tổng cộng 30 judge evaluations:

```text
20-30: 1
30-40: 2
40-50: 3
50-60: 5
60-70: 6
70-80: 8
80-90: 3
90-100: 2
```

`PRELIM_EVALUATION_TARGETS` được chốt thành:

```text
25,
34, 38,
42, 46, 49,
52, 54, 56, 58, 59,
62, 64, 65, 67, 68, 69,
72, 73, 74, 75, 76, 77, 78, 79,
82, 86, 89,
93, 97
```

Mỗi submission có một `strength` từ 1 đến 15. Target của từng judge được lấy bằng index
`(strength - 1) * 2 + judgeIdx`. Mỗi criterion trong lượt chấm đó nhận `target / 10`, nên sau
normalize, evaluation score khớp chính xác target. Cách này vừa tạo đúng histogram yêu cầu, vừa
giữ thứ tự năng lực/ranking mang tính đơn điệu và deterministic.

Trade-off có chủ đích: trong dữ liệu seed vòng loại, các criteria của cùng một judge/submission
có cùng giá trị. Dữ liệu chung kết vẫn giữ biến thiên giữa criteria và judges. Đây là seed demo,
không thay đổi cách hệ thống xử lý score thật.

### 11.3. Đồng bộ `RoundResult.totalScore` với production

Seed cũ lưu `RoundResult.totalScore` theo raw weighted score khoảng 30–50, trong khi production
service normalize score về thang 0–100. Seed được đổi sang cùng công thức production. Kiểm tra ở
giai đoạn công thức phân bố trung gian cho thấy độ lệch lớn nhất giữa result đã lưu và trung bình
panel được tính lại chỉ là `0.005`, do làm tròn; min/max result lúc đó là 40.50/95.38.

Sau khi chuyển sang target chính xác, result của một submission là trung bình hai target của hai
judges tương ứng. Logic ranking, finalist selection và prize calculation tiếp tục dùng production
services, không dùng kết quả hard-code.

## 12. Reseed database và xác minh S3/S25

### 12.1. Reseed S3 trên local database

Trước khi reseed, đã kiểm tra không có backend process lắng nghe port 8080. Theo yêu cầu của user,
local database mặc định `seal_hackathon` được tạo lại với:

```text
DDL_AUTO=create
SEED_SCENARIO=S3
```

Smoke run hoàn tất thành công. Trạng thái cuối:

- event `COMPLETED`;
- 15 teams trên 4 tracks;
- 8 final results đã publish;
- 3 prizes đã announce;
- 15 submissions vòng loại, mỗi submission có đúng 2 judges;
- 30 judge-evaluation samples ở vòng loại;
- 150 raw score rows vòng loại, 120 raw score rows chung kết, tổng 270.

Direct SQL verification của vòng loại:

```text
20-30  1
30-40  2
40-50  3
50-60  5
60-70  6
70-80  8
80-90  3
90-100 2
```

### 12.2. Xác minh S25 trên schema cô lập

S25 được chạy trên schema tạm `seal_hackathon_s25_verify_20260723` bằng datasource URL truyền
trực tiếp qua Spring arguments. Local database chính vẫn giữ nguyên S3.

Trong schema trống, Hibernate in các WARN/stack trace khi cố drop foreign keys/tables chưa tồn
tại trước bước create. Run vẫn tiếp tục và kết thúc `BUILD SUCCESS`; đây là noise của schema-create
trên database mới, không phải seed failure.

Kết quả S25:

- event `IN_PROGRESS`;
- vòng loại đã finalize;
- 15 prelim results;
- 8 finalists đã được chấm đủ vòng chung kết nhưng chưa calculate/publish ranking;
- 0 final results;
- 15 submissions vòng loại, min/max đều 2 judges;
- 30 judge-evaluation samples;
- phổ điểm đúng chính xác `1 2 3 5 6 8 3 2` như S3.

Schema kiểm tra S25 đã được drop sau khi query. Database local cuối phiên vẫn là S3 completed.

## 13. Kiểm thử và bằng chứng xác minh

### 13.1. Backend

Các lần kiểm thử quan trọng trong phiên:

- compile backend thành công sau khi thêm analytics;
- targeted tests `AdminScoreDistributionServiceTest,RoundResultServiceTest`: **5/5 passed**;
- full Maven test sau implementation analytics: **406/406 passed**;
- full Maven test sau tối ưu `JOIN FETCH`: **406/406 passed**;
- sau thay đổi cuối chỉ liên quan deterministic seed targets: backend compile thành công, S3
  smoke/reseed thật thành công và S25 isolated smoke thành công.

Lưu ý trung thực: full 406-test suite không được chạy lại lần thứ ba sau thay đổi cuối cùng của
seed target; hai smoke run thực tế và compile là verification cuối cho phần đó.

### 13.2. Frontend

- production build thành công nhiều lần, 2,456 modules transformed;
- chỉ còn warning bundle hiện hữu lớn hơn 500 KB;
- scoped unit test `vitest run src`: **8 files, 147/147 tests passed**.

Lệnh `npm run test` toàn cục trả exit code 1 vì Vitest còn collect file Playwright
`e2e/scenario-1/00_open_account_team_flow.spec.ts`, trong khi local environment không resolve được
module `@playwright/test`. Toàn bộ 147 unit tests vẫn pass; không sửa test configuration ngoài scope.

`tsc --noEmit -p tsconfig.app.json` báo 6 lỗi có sẵn, không thuộc component mới:

- `NotificationProvider.test.tsx`: mismatch type `full_name`;
- `NoTeamDashboard.tsx`: thiếu field `topic` ở hai vị trí;
- `eventUtils.test.ts`: `topic` optional mismatch;
- `eventUtils.test.ts`: fixture thiếu `topic`;
- `CoordJudgesPage.tsx`: component type `ReactPortal | null`.

Không có lỗi TypeScript được báo từ `EventScoreDistribution`.

### 13.3. Static/data checks

- `git diff --check` sạch về whitespace; chỉ có warning line-ending LF → CRLF;
- direct SQL query xác nhận số judge/submission, số raw score rows và histogram bins;
- S3/S25 smoke logs xác nhận lifecycle state đúng với từng scenario.

## 14. Danh sách file thay đổi

| File | Trạng thái | Vai trò |
|---|---|---|
| `back-end/src/seal-api/src/main/java/com/seal/hackathon/config/seed/DemoScenario.java` | Modified | S25/S3 shared flow, 2-judge panels, exact targets, normalized prelim results |
| `back-end/src/seal-api/src/main/java/com/seal/hackathon/controller/AdminController.java` | Modified | Admin distribution endpoint |
| `back-end/src/seal-api/src/main/java/com/seal/hackathon/repository/ScoreRepository.java` | Modified | Query finalized scores với required relations |
| `back-end/src/seal-api/src/main/java/com/seal/hackathon/service/RoundResultService.java` | Modified | Dùng normalization math dùng chung |
| `front-end/src/seal-web/src/features/events/AdminEventsPage.tsx` | Modified | Mount analytics cho completed event |
| `front-end/src/seal-web/src/shared/api/admin.ts` | Modified | API types và client call |
| `back-end/src/seal-api/src/main/java/com/seal/hackathon/dto/response/AdminScoreDistributionResponse.java` | New | Response contract |
| `back-end/src/seal-api/src/main/java/com/seal/hackathon/service/AdminScoreDistributionService.java` | New | Aggregation/statistics/filter logic |
| `back-end/src/seal-api/src/main/java/com/seal/hackathon/service/ScoreNormalization.java` | New | Shared normalization utility |
| `back-end/src/seal-api/src/test/java/com/seal/hackathon/service/AdminScoreDistributionServiceTest.java` | New | Backend unit tests |
| `front-end/src/seal-web/src/features/events/EventScoreDistribution.tsx` | New | Histogram, stats, filters, drill-down |
| `back-end/AI logs/AI_LOG_SEAL_S3_S25_ADMIN_SCORE_DISTRIBUTION_FULLSTACK_2026-07-23.md` | New | Log đầy đủ của phiên hiện tại |

Không có package/dependency mới được cài. Trước khi thêm file log này, diff stat của sáu tracked
files là 210 insertions và 73 deletions; stat đó không tính các file untracked mới.

## 15. Contract API và cách đọc dữ liệu

Ví dụ request:

```http
GET /api/admin/events/{eventId}/score-distribution?metric=JUDGE_EVALUATION&roundId={roundId}
GET /api/admin/events/{eventId}/score-distribution?metric=SUBMISSION_RESULT&roundId={roundId}&trackId={trackId}
```

Response chứa metadata event/filter, `bins`, `statistics` và `observations`. Với judge metric, mỗi
observation là một lượt chấm hoàn chỉnh của một judge cho một submission, được tổng hợp từ các
criterion scores. Với result metric, observation là final result của submission.

Admin chọn round, track và metric; histogram luôn render trên thang 0–100. Khi click một bin, bảng
drill-down lọc các observations nằm trong khoảng đó. Bin 90–100 nhận cả score đúng bằng 100.

## 16. Sự cố đã gặp và cách xử lý

| Sự cố | Tác động | Cách xử lý/kết quả |
|---|---|---|
| Biến môi trường `DB_URL` không được Spring config hiện tại đọc | Smoke `create-drop` đầu tiên tác động local database thay vì schema test dự kiến | Dừng, phục hồi local bằng S3 seed; các run cô lập sau truyền `--spring.datasource.url` trực tiếp |
| Maven lần đầu bị sandbox/network chặn khi resolve parent POM | Compile/test chưa chạy được trong sandbox mặc định | Chạy lại với quyền network đã được user phê duyệt; compile và tests thành công |
| Hibernate báo lỗi drop constraint/table trên schema S25 mới | Log rất nhiễu, dễ bị hiểu nhầm là seed fail | Kiểm tra exit/result cuối: `BUILD SUCCESS`, data query đúng; xác định là DDL noise trên schema trống |
| AVG và MED bằng nhau | Hai labels đè lên nhau trên histogram | Dùng hai legend chips; chỉ vẽ một line khi gần trùng |
| Có 9 stat cards | Card cuối rơi lẻ sang hàng tiếp theo | Desktop grid 9 cột, mobile horizontal overflow |
| Stat cards quá đơn sắc | UI thiếu phân cấp thị giác | Gán 9 accent palettes riêng; histogram vẫn giữ blue theo correction của user |
| Seed vòng loại chỉ có 1 judge/submission | Không phản ánh rule demo kỳ vọng | Tăng lên 8 internal judges, chia 2 judges/track |
| Seeded prelim `RoundResult` dùng raw scale 30–50 | Không đồng nhất production result thang 0–100 | Chuyển sang shared normalized calculation |
| Vitest toàn cục collect Playwright e2e | `npm run test` exit 1 dù unit tests pass | Chạy scoped `vitest run src`; không sửa config ngoài scope |
| Global TypeScript check có lỗi cũ | `tsc` không xanh toàn repo | Xác nhận không có lỗi từ file analytics mới; ghi rõ 6 lỗi ngoài scope |
| `apply_patch` đôi lúc trả về chậm trong lúc ghi log | Cần chờ tiến trình và kiểm tra file sau mỗi phần | Chia log thành các patch nhỏ, chờ cell hoàn tất rồi kiểm tra section/tail |

## 17. Giới hạn và quyết định còn hiệu lực

- Analytics chỉ hiện trên Admin event detail khi event ở trạng thái `COMPLETED`; vì vậy S25 đang
  `IN_PROGRESS` chưa hiển thị widget dù đã có score data.
- Judge names được hiển thị thật trong drill-down vì đây là System Admin view. Chưa có anonymization.
- API trả toàn bộ observations trong một response. Phù hợp demo/quy mô vừa; event rất lớn có thể
  cần server-side pagination hoặc lazy drill-down về sau.
- Histogram cố định 10 bins trên thang 0–100.
- Standard deviation đang dùng population standard deviation.
- Seed vòng loại dùng target deterministic và cùng một criterion value trong một judge evaluation;
  đây là lựa chọn phục vụ phổ điểm demo chính xác, không phải constraint của scoring production.
- S3 và S25 dùng chung score/assignment/submission data. Điểm khác biệt chỉ là S3 tiếp tục chạy
  calculate/publish ranking, announce prizes và complete event; S25 dừng tại cut-point.
- CSV export hiện hữu vẫn không export raw score rows. Scope này chỉ thêm visualization/API, chưa
  thay đổi CSV contract.
- Schema S25 test đã dọn; database local kết thúc ở trạng thái S3 completed.
- Backend cần restart để UI gọi được endpoint mới nếu một process cũ vẫn đang chạy.

## 18. Trạng thái Git và deliverable cuối phiên

### 18.1. Branch/worktree

- Current branch: `NhatNHM-fix-s3-seed`.
- Base: đúng `origin/develop` tại `5b55e57805cd6bc18154588c09d74abb4360473e`.
- Worktree chứa toàn bộ implementation và log này, chưa commit.
- Không có commit hoặc push nào được thực hiện trong phiên.
- Stash ban đầu vẫn được giữ làm safety copy:
  `stash@{0}: On main: wip: align S3 with completed S25 workflow`.

### 18.2. Deliverables đã đạt

- [x] S3 xuất phát từ cùng cut-point/data với S25.
- [x] S3 chạy tiếp bằng production services đến ranking, prizes và event completed.
- [x] Vòng loại có đúng 2 judges/submission.
- [x] Judge-evaluation histogram có đúng phổ `1 2 3 5 6 8 3 2`.
- [x] S25 dùng chung chính xác dữ liệu score với S3.
- [x] Admin có histogram, filters, statistics và click-to-drill-down.
- [x] Stat cards nằm dưới histogram và có nhiều accent colors.
- [x] Backend/frontend đã được kiểm tra theo phạm vi mô tả ở mục 13.
- [x] Local database được reseed S3 completed để có thể xem demo.
- [x] Tạo AI log chi tiết của phiên.

### 18.3. Việc tiếp theo nếu user yêu cầu

1. Restart backend/frontend và review lại UI bằng dữ liệu S3 local.
2. Commit toàn bộ thay đổi trên branch `NhatNHM-fix-s3-seed` với message được thống nhất.
3. Push branch và mở PR về develop.
4. Tuỳ chọn: sửa Vitest config để tách unit/e2e, xử lý 6 lỗi TypeScript cũ hoặc mở rộng CSV
   export để bao gồm judge-level scoring.

## 19. Các lệnh kiểm tra chính đã dùng (đã lược bỏ thông tin nhạy cảm)

```powershell
git status --short --branch
git log --all --author="nhatnhm1405|Nhat Nguyen" --oneline
git rev-list --left-right --count origin/main...origin/develop
git stash list

# Backend
mvn test
mvn -Dtest=AdminScoreDistributionServiceTest,RoundResultServiceTest test
mvn -DskipTests compile

# Frontend
npm run build
npx vitest run src
npx tsc --noEmit -p tsconfig.app.json

# Seed S3 local
$env:DDL_AUTO='create'
$env:SEED_SCENARIO='S3'
mvn spring-boot:run -Dspring-boot.run.arguments="...web-application-type=none..."

# Seed S25 isolated; datasource URL được truyền trực tiếp nhưng credentials không ghi vào log
mvn spring-boot:run -Dspring-boot.run.arguments="--spring.datasource.url=<isolated-schema-url> ..."
```

Không có password, token hoặc connection secret nào được cố ý ghi trong tài liệu này.

## 20. Các AI log liên quan đã tham khảo

Các log trước trong cùng thư mục được dùng để đối chiếu context nghiệp vụ và lịch sử implementation:

- `AI_LOG_SEAL_S2_5_AWARD_DEMO_SEED_BE_2026-07-14.md`;
- `AI_LOG_SEAL_DEMO_SCENARIO_REVAMP_BE_2026-07-12.md`;
- `AI_LOG_SEAL_SPRINT3_MERGE_REPORT_AND_HIGH_RISK_FIXES_2026-07-18.md`;
- `AI_LOG_SUBMISSION_SCORING_SYSTEMLOG_VALIDATION_TESTS_2026-06-25.md`;
- `AI_LOG_SEAL_ROUND_TIMER_FULLSTACK_2026-06-26.md`.

---

Kết luận: phiên làm việc đã đưa S3 và S25 về cùng một nguồn dữ liệu scoring có hai judges cho mỗi
submission vòng loại, tạo đúng phổ điểm được yêu cầu, giữ khác biệt lifecycle hợp lý giữa hai
scenario, đồng thời bổ sung Admin score-distribution visualization full-stack. Toàn bộ thay đổi
vẫn ở worktree trên branch tuyến tính từ develop và chưa được commit/push.
