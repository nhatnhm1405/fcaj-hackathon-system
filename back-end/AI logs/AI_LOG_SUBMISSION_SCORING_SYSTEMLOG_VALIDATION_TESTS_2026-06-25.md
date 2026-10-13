# AI LOG - Submission, Scoring, SystemLog Validation & Unit Tests - 2026-06-25

Ngay ghi log: 2026-06-25

Cong cu ho tro: Codex

Pham vi phien lam viec:

- Ra soat lai 3 test file Java:
  - `SubmissionServiceTest.java`
  - `ScoringServiceTest.java`
  - `SystemLogServiceTest.java`
- Bo sung coverage cho cac validation/authorization edge cases con thieu.
- Sua code service khi test phat hien logic/signature dang lech voi controller.
- Cap nhat lai 3 test report `.md` trong `back-end/src/seal-api/Test/`.

Ket qua tong quan:

- Targeted 3 test file: **97 tests pass**.
- Full back-end test suite: **285 tests pass**.
- Khong con failure/error sau khi sua.

---

## 1. SubmissionService - Submit/Read Submission Guard

### 1.1. Van de phat hien

`SubmissionService.submit(...)` da co guard leader/team/deadline, nhung con thieu guard `userType`.

He qua:

- Mot user `STAFF` neu co membership `LEADER` trong team van co the submit/update submission bang service/API.
- Dieu nay trai voi rule da thong nhat: chi participant student (`FPT_STUDENT` / `EXTERNAL_STUDENT`) moi duoc nop bai.

### 1.2. Code da sua

File:

- `src/main/java/com/seal/hackathon/service/SubmissionService.java`

Thay doi:

- Sau khi lookup submitter, service check:
  - `FPT_STUDENT`
  - `EXTERNAL_STUDENT`
- Neu khong phai student thi throw `ForbiddenException`.
- Guard nay chay truoc khi lookup round/team de chan staff som.

### 1.3. Test da bo sung/cap nhat

File:

- `src/test/java/com/seal/hackathon/service/SubmissionServiceTest.java`

Coverage hien tai:

- Tong so JUnit executions: **46**
- Cac nhom case chinh:
  - Create submission moi.
  - Update submission da ton tai.
  - Request null / roundId null.
  - Repo URL invalid/null/blank/too long.
  - Demo URL invalid/too long.
  - Slide URL invalid/too long.
  - Description too long.
  - User missing.
  - Non-student submitter bi chan.
  - Round missing.
  - Round status invalid.
  - Round status `OPEN` duoc submit.
  - User khong thuoc team trong event.
  - Team not approved.
  - Non-leader.
  - Deadline passed.
  - `getMySubmission` success/not found.
  - List submissions by round: coordinator all, judge assigned, forbidden role.
  - Get submission by id: coordinator, student owner/non-owner, judge assigned/unassigned, requester missing, submission missing, forbidden role.

### 1.4. Tai lieu da cap nhat

File:

- `back-end/src/seal-api/Test/SubmissionServiceTest.md`

Noi dung da sync:

- JUnit executions moi: **46**
- Ghi ro guard moi: non-student submitter bi chan.
- Cap nhat detailed test case table.

### 1.5. Notes con lai

- `getMySubmission(...)` hien chua check `userType`; endpoint nay dang dua vao membership. Neu product muon student-only read theo participant endpoint, nen harden tiep.
- Race condition khi 2 first-submit cung luc cho `(team_id, round_id)` van can DB/transaction strategy neu can dam bao tuyet doi.

---

## 2. ScoringService - Judge Assignment Guard & Test Coverage

### 2.1. Van de phat hien

Khi chay test, `ScoringServiceTest` dang goi signature moi:

```java
getScoresBySubmission(requesterId, authorities, submissionId)
```

Nhung `ScoringService.java` hien tai lai van la signature cu:

```java
getScoresBySubmission(submissionId)
```

He qua:

- `ScoringController` da goi service theo signature moi.
- Test cung viet theo signature moi.
- Source service bi lech, lam compile/test fail.
- Ngoai ra, ban cu cua service van doc submission bang `findById(...)`, chua enforce judge assignment guard.

### 2.2. Code da sua

File:

- `src/main/java/com/seal/hackathon/service/ScoringService.java`

Thay doi:

- Doi `getScoresBySubmission(...)` ve dung signature:
  - `requesterId`
  - `authorities`
  - `submissionId`
- Coordinator:
  - Kiem tra submission ton tai bang `findById(...)`.
- Judge:
  - Bat buoc qua `findBySubmissionIdAndJudgeId(...)`.
- Role khac:
  - Throw `ForbiddenException`.
- `submitScores(...)`:
  - Validate request null, `submissionId` null, score list null/empty.
  - Judge phai ton tai.
  - Submission phai assigned voi judge.
  - Entry null hoac thieu `criteriaId`/`value` bi chan.
  - Criteria phai ton tai.
  - Criteria phai thuoc round cua submission.
  - Score phai >= 0 va <= maxScore.
  - Existing score duoc update theo `(submission, judge, criteria)`.
  - Draft flag/comment duoc persist.
- Bo dependency thua:
  - `ScoringCriteriaTemplateRepository`.

### 2.3. Test da bo sung/cap nhat

File:

- `src/test/java/com/seal/hackathon/service/ScoringServiceTest.java`

Coverage hien tai:

- Tong so JUnit executions: **33**
- Cac nhom case chinh:
  - `getCriteriaByRound`: non-empty/empty.
  - `createCriteria`: success, event missing, round not in event.
  - `submitScores`: success, update existing score, exact max, zero, draft/comment, multiple entries.
  - Request validation: request null, submissionId null, scores empty/null.
  - Assignment validation: judge missing, submission not assigned.
  - Entry validation: null entry, null criteriaId, null value.
  - Criteria validation: criteria missing, wrong round, null round.
  - Score validation: negative, over max.
  - `getScoresBySubmission`: coordinator, judge assigned, coordinator submission missing, judge unassigned, forbidden role, null authorities.
  - `getMyScoresByRound`: non-empty/empty.

### 2.4. Tai lieu da cap nhat

File:

- `back-end/src/seal-api/Test/ScoringServiceTest.md`

Noi dung da sync:

- JUnit executions moi: **33**
- Ghi ro service mismatch da duoc fix.
- Cap nhat detailed test case table.

### 2.5. Notes con lai

- `createCriteria(...)` hien van chu yeu dua vao controller `@Valid` cho request field validation. Service test da cover resource checks, chua harden service-level null/blank field.
- `getScoresBySubmission(...)` hien cho assigned judge xem tat ca scores cua submission. Neu product muon judge chi xem diem cua minh, can doi behavior va test lai.

---

## 3. SystemLogService - Validation/No-op/Mapping Coverage

### 3.1. Tinh trang hien tai

`SystemLogService` da co validation/normalization:

- Actor id null -> no-op.
- Actor missing -> no-op.
- Action null/blank/too long -> no-op.
- Action trim, whitespace -> underscore, uppercase.
- Detail null/blank -> null.
- Detail trim.
- Detail > 5000 -> truncate.
- Khong con ghi IP vi cot IP da bi xoa.

Trong phien nay khong can sua source `SystemLogService.java` them, nhung da ra soat va sync lai test/report.

### 3.2. Test da kiem tra

File:

- `src/test/java/com/seal/hackathon/service/SystemLogServiceTest.java`

Coverage hien tai:

- Tong so JUnit executions: **18**
- Cac nhom case chinh:
  - Record valid input.
  - Null detail.
  - Blank detail.
  - Detail 5001 truncate ve 5000.
  - Action dung 50 ky tu.
  - Multiple whitespaces -> single underscore.
  - Detail dung 5000 ky tu.
  - Actor id null no-op.
  - Action null/empty/blank/tab/newline no-op.
  - Action > 50 no-op.
  - Actor missing no-op.
  - `getAllLogs`: single log, empty list, multiple logs.

### 3.3. Tai lieu da cap nhat

File:

- `back-end/src/seal-api/Test/SystemLogServiceTest.md`

Noi dung da sync:

- JUnit executions moi: **18**
- Ghi ro no-op behavior va mapping coverage.

### 3.4. Notes con lai

- `record(...)` van co the bubble exception neu DB save fail. Neu can "never blocks" that su, can them controlled catch/log hoac transaction rieng.
- Action moi validate length/blank va normalize; chua co allow-list taxonomy.
- `getAllLogs()` chua paging/filter.

---

## 4. Test Commands

### 4.1. Targeted tests

```powershell
& 'C:\Users\DAO HOANG NHAT\.m2\wrapper\dists\apache-maven-3.9.16-bin\5grr65jo27hi51sujmtcldfovl\apache-maven-3.9.16\bin\mvn.cmd' '-Dtest=SubmissionServiceTest,ScoringServiceTest,SystemLogServiceTest' test
```

Ket qua:

- `ScoringServiceTest`: 33 pass.
- `SubmissionServiceTest`: 46 pass.
- `SystemLogServiceTest`: 18 pass.
- Tong: **97 tests**, 0 failures, 0 errors, 0 skipped.

### 4.2. Full suite

```powershell
& 'C:\Users\DAO HOANG NHAT\.m2\wrapper\dists\apache-maven-3.9.16-bin\5grr65jo27hi51sujmtcldfovl\apache-maven-3.9.16\bin\mvn.cmd' test
```

Ket qua:

- **285 tests**
- **0 failures**
- **0 errors**
- **0 skipped**

Ghi chu:

- Maven wrapper `mvnw.cmd` trong workspace van co van de rieng, nen test duoc chay bang Maven binary trong `.m2/wrapper/dists`.
- Test log co warning Mockito dynamic agent va Spring open-in-view, nhung khong gay fail.

---

## 5. Files Da Thay Doi / Tao Moi

Source code:

- `src/main/java/com/seal/hackathon/service/SubmissionService.java`
- `src/main/java/com/seal/hackathon/service/ScoringService.java`

Tests:

- `src/test/java/com/seal/hackathon/service/SubmissionServiceTest.java`
- `src/test/java/com/seal/hackathon/service/ScoringServiceTest.java`
- `src/test/java/com/seal/hackathon/service/SystemLogServiceTest.java`

Test reports:

- `back-end/src/seal-api/Test/SubmissionServiceTest.md`
- `back-end/src/seal-api/Test/ScoringServiceTest.md`
- `back-end/src/seal-api/Test/SystemLogServiceTest.md`

AI log:

- `back-end/AI logs/AI_LOG_SUBMISSION_SCORING_SYSTEMLOG_VALIDATION_TESTS_2026-06-25.md`

---

## 6. Ket Luan

Ba chuc nang `Submission`, `Scoring`, va `SystemLog` da duoc ghi nhan lai day du:

1. `SubmissionService`: bo sung guard student-only cho submit va mo rong test coverage len 46 executions.
2. `ScoringService`: sua mismatch service/controller/test, enforce judge assignment guard va mo rong test coverage len 33 executions.
3. `SystemLogService`: xac nhan validation/no-op/mapping coverage voi 18 executions va sync report.

Full back-end suite pass 285/285.
