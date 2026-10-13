# AI LOG - S25/S3 Playwright E2E Demo Automation - 2026-07-28

> Ghi lai phan backend seed/workflow S25-S3 va qua trinh xay dung bo demo E2E
> cho Scenario 2 (seed S25) va Scenario 3 (seed S3) cua SEAL Hackathon.
>
> Pham vi gom tinh ranking, publish result, tao/announce prize, complete event,
> kiem tra leaderboard/history va bam tai bon file CSV.
>
> Phan nay chi them ha tang demo/test. Khong thay doi logic nghiep vu production
> cua backend hoac frontend.

---

## 1. Muc tieu

Sau khi Scenario 1 ket thuc tai event `IN_PROGRESS`, can bo sung hai bo demo:

```text
Scenario 2 -> seed S25
Scenario 3 -> seed S3
```

Yeu cau da chot:

- Scenario 2 phai bam `CALCULATE RANKINGS`.
- Scenario 2 phai bam `PUBLISH RESULTS`.
- Scenario 2 phai tao prize tu final ranking va announce prize.
- System Admin phai bam `COMPLETE EVENT` o cuoi Scenario 2.
- Scenario 3 phai kiem tra event da `COMPLETED`.
- Scenario 3 phai kiem tra participant xem duoc result/history.
- Hai scenario phai bam tai tong cong 4 file CSV.
- File test va `.bat` phai co cau truc tuong tu Scenario 1.

Quyet dinh quan trong:

- Ten trinh dien "Scenario 2" duoc anh xa sang seed ky thuat `S25`.
- S25 va S3 la hai seed doc lap.
- S3 khong tiep tuc database da bi Scenario 2 thay doi.
- Moi `.bat` deu reset database bang `run-demo.ps1 ... -Force`.
- Mac dinh chay Chromium co hien thi de giao vien quan sat.
- Moi scenario co HTML report va thu muc download rieng.

---

## 2. Cau truc file

### Windows runners

```text
E2E testing/
  scenario-2/
    00_s25_results_publish_awards.bat
  scenario-3/
    00_s3_completed_results_exports.bat
```

### Playwright tests

```text
front-end/src/seal-web/e2e/
  scenario-2/
    00_s25_results_publish_awards.spec.ts
  scenario-3/
    00_s3_completed_results_exports.spec.ts
```

### Shared fixtures duoc tai su dung tu Scenario 1

```text
front-end/src/seal-web/e2e/scenario-1/
  s1DemoAccounts.ts
  s1TestUtils.ts

front-end/src/seal-web/
  playwright.config.ts

E2E testing/scenario-1/
  open-review-edge.ps1
```

Khong tao helper trung lap. Hai scenario moi dung lai:

- sign-in va reset browser session;
- authenticated API request;
- tim event `SEAL Summer 2026`;
- lay danh sach round;
- xac dinh preliminary round;
- pause giua cac buoc demo;
- Edge review window.

---

## 3. Quan he giua S25 va S3

### S25 - cut-point truoc khi tinh final ranking

S25 bat dau voi:

- Event `IN_PROGRESS`.
- Preliminary round `FINALIZED`.
- Preliminary result da publish.
- 15 team co preliminary result.
- Top 2 trong moi track advanced, tong cong 8 finalist.
- Final round `ACTIVE`.
- 8 final submission da co du diem.
- Moi final submission co 3 judge cham xong.
- Chua co final `RoundResult`.
- Chua co prize.

Scenario 2 thuc hien:

```text
S25 IN_PROGRESS
  -> calculate final ranking
  -> publish final results
  -> auto-generate top 3 prizes
  -> announce prizes
  -> complete event
  -> COMPLETED
```

### S3 - completed snapshot doc lap

S3 seed lai mot event da hoan tat:

- Event `COMPLETED`.
- Preliminary va final deu `FINALIZED`.
- Preliminary va final result da publish.
- Co 8 final result.
- Co 3 announced prize.
- Participant co history.
- Event da roi khoi live scoring va nam trong Coordinator History.

Trong backend hien tai, S3 duoc tao tu cung cut-point voi S25, sau do seed goi
production services:

```text
RoundResultService.finalizeRound
RoundResultService.publishResults
PrizeService.autoGenerate
PrizeService.announce
HackathonEventService.completeEvent
```

Do do S3 dai dien cho ket qua cua workflow production da hoan tat, nhung khi
chay `.bat` thi van la mot database moi, khong phai database cua Scenario 2.

### Hai scenario khong bi trung

| Noi dung | Scenario 2 / S25 | Scenario 3 / S3 |
|---|---|---|
| Trang thai dau | `IN_PROGRESS` | `COMPLETED` |
| Loai test | Workflow ghi du lieu | Kiem tra snapshot/read-only |
| Calculate final ranking | Bam tren UI | Da co san |
| Publish final result | Bam tren UI | Assert da published |
| Tao/announce prize | Bam tren UI | Assert da announced |
| Complete event | Admin bam tren UI | Assert da completed |
| Export | 2 ranking CSV | Winner va participant CSV |

---

## 4. Cach tinh diem va xep hang

Production `RoundResultService` chuan hoa diem cua tung judge ve thang 0-100:

```text
judgeScore =
  100 * SUM(weight * criterionValue / criterionMaxScore) / SUM(weight)
```

Final score cua team la trung binh diem chuan hoa cua cac judge:

```text
teamScore = average(judgeScore)
```

Vi du:

| Criterion | Weight | Max score | Judge score |
|---|---:|---:|---:|
| Idea | 40 | 10 | 8 |
| Technical | 60 | 10 | 9 |

```text
judgeScore =
  100 * ((40 * 8/10) + (60 * 9/10)) / (40 + 60)
  = 86
```

Neu 3 judge cho diem chuan hoa `86`, `82`, `88`:

```text
teamScore = (86 + 82 + 88) / 3 = 85.33
```

Quy tac ranking:

- Round khong phai final: rank rieng trong tung track.
- `rankPosition <= topNAdvance`: team duoc danh dau `advanced`.
- Final round: rank global cho toan bo finalist.
- Prize lay top 3 cua final global ranking.

---

## 5. Luong Scenario 2 - S25

Runner:

```text
E2E testing/scenario-2/00_s25_results_publish_awards.bat
```

Playwright spec:

```text
front-end/src/seal-web/e2e/scenario-2/
  00_s25_results_publish_awards.spec.ts
```

Precondition:

- Port `8080` va `5173` phai dang trong.
- Runner reset database bang seed S25.
- Backend phai tra ve event `SEAL Summer 2026` o `IN_PROGRESS`.

Playwright flow:

1. Dang nhap Coordinator.
2. Assert event dang `IN_PROGRESS`.
3. Assert preliminary round `FINALIZED`.
4. Assert final round `ACTIVE`.
5. Assert preliminary co 15 result da publish.
6. Assert top 2 cua moi track advanced, tong cong 8 team.
7. Assert final co 8 submission.
8. Assert moi final submission co 3/3 judge cham `FINAL`.
9. Assert final chua co result.
10. Mo `Scoring & Results`.
11. Chon preliminary round.
12. Bam `EXPORT CSV` de tai preliminary ranking.
13. Chon final round.
14. Bam `CALCULATE RANKINGS`.
15. Xac nhan bang nut `CALCULATE`.
16. Assert tao 8 draft result, rank 1 den 8.
17. Assert final score nam trong thang 0-100.
18. Assert participant endpoint chua tra result khi chua publish.
19. Bam `PUBLISH RESULTS`.
20. Nhap ten final round vao `Confirmation text`.
21. Xac nhan `PUBLISH RESULTS`.
22. Assert 8 final result da publish.
23. Bam `EXPORT CSV` de tai final ranking.
24. Dang nhap Arsenal leader va mo `Leaderboard`.
25. Assert participant thay `Full Standings` va champion.
26. Dang nhap lai Coordinator va mo `Awards`.
27. Bam `AUTO-GENERATE FROM FINAL`.
28. Assert 3 prize khop chinh xac team rank 1, 2, 3.
29. Bam `ANNOUNCE`.
30. Nhap ten event va bam `ANNOUNCE PRIZES`.
31. Assert 3 prize da announced va co `awardedAt`.
32. Dang nhap System Admin.
33. Bam `COMPLETE EVENT`.
34. Xac nhan `CONFIRM COMPLETE`.
35. Assert event chuyen sang `COMPLETED`.

Sau khi test ket thuc, backend/frontend duoc giu lai de review. Can dong hai cua
so nay truoc khi chay Scenario 3.

---

## 6. Luong Scenario 3 - S3

Runner:

```text
E2E testing/scenario-3/00_s3_completed_results_exports.bat
```

Playwright spec:

```text
front-end/src/seal-web/e2e/scenario-3/
  00_s3_completed_results_exports.spec.ts
```

Precondition:

- Port `8080` va `5173` phai dang trong.
- Runner reset database bang seed S3.
- Backend phai tra ve event `SEAL Summer 2026` o `COMPLETED`.

Playwright flow:

1. Dang nhap Coordinator.
2. Assert event `COMPLETED`.
3. Assert preliminary va final deu `FINALIZED`.
4. Assert preliminary co 15 published result.
5. Assert co 8 team advanced.
6. Assert final co 8 published result, rank 1 den 8.
7. Assert co 3 prize da announced.
8. Assert team nhan prize khop final rank 1, 2, 3.
9. Dem participant tu roster de dung doi chieu CSV.
10. Dang nhap Arsenal leader.
11. Mo final leaderboard va assert `Full Standings`.
12. Assert champion hien thi.
13. Chuyen sang preliminary round.
14. Assert bon track hien thi.
15. Assert co status `Advanced` va `Eliminated`.
16. Mo participant `History`.
17. Assert event va team Arsenal hien thi.
18. Dang nhap lai Coordinator.
19. Mo live scoring va assert khong con event dang chay.
20. Mo Coordinator History va assert event completed hien thi.
21. Mo `Awards`.
22. Assert badge `COMPLETED` va `ANNOUNCED`.
23. Bam `EXPORT WINNERS CSV`.
24. Kiem tra filename, header, winner count va winner team.
25. Bam `EXPORT PARTICIPANTS CSV`.
26. Kiem tra filename, header, participant count va Arsenal.

---

## 7. Bon file CSV duoc bam tai

| Thu tu | Scenario | Nut UI | File |
|---:|---|---|---|
| 1 | S25 | `EXPORT CSV` tai preliminary | `rankings-round-{preliminaryRoundId}.csv` |
| 2 | S25 | `EXPORT CSV` tai final | `rankings-round-{finalRoundId}.csv` |
| 3 | S3 | `EXPORT WINNERS CSV` | `winners-SEAL_Summer_2026.csv` |
| 4 | S3 | `EXPORT PARTICIPANTS CSV` | `participants-SEAL_Summer_2026.csv` |

Moi download duoc Playwright:

1. Cho browser phat `download` event.
2. Kiem tra `suggestedFilename`.
3. Luu file vao `E2E_DOWNLOAD_DIR`.
4. Doc lai CSV.
5. Kiem tra header.
6. Kiem tra so dong/noi dung co ban.

Ly do hai ranking CSV nam o Scenario 2:

- `CoordScoringPage` chi lam viec voi event chua ket thuc.
- Event `COMPLETED` khong con trong live scoring.
- S3 chi co the export winner va participant tren trang Awards.

---

## 8. Tai khoan demo co dinh

Mat khau chung cua demo:

```text
Test@1234
```

Tai khoan duoc hai scenario su dung:

| Vai tro | Tai khoan |
|---|---|
| System Admin | `admin@fpt.edu.vn` |
| Coordinator | `coordinator@fpt.edu.vn` |
| Arsenal leader / Participant | `p1@fpt.edu.vn` |

Participant trong event completed co the dang nhap de xem leaderboard/history
theo read-only policy cua he thong.

---

## 9. Cau hinh demo, download va report

Gia tri mac dinh:

```text
E2E_SLOW_MO_MS=1200
E2E_STEP_PAUSE_MS=2500
PLAYWRIGHT_HTML_OPEN=never
```

Timeout:

```text
Scenario 2: E2E_TEST_TIMEOUT_MS=900000
Scenario 3: E2E_TEST_TIMEOUT_MS=600000
```

Che do:

- Mac dinh: headed Chromium.
- `E2E_HEADLESS=1`: chay headless.
- `E2E_NO_PAUSE=1`: khong dung `pause` o cuoi `.bat`.

Report du kien:

```text
E2E testing/scenario-2/playwright-report/
  00_s25_results_publish_awards/index.html

E2E testing/scenario-3/playwright-report/
  00_s3_completed_results_exports/index.html
```

Download du kien:

```text
E2E testing/scenario-2/downloads/
E2E testing/scenario-3/downloads/
```

Sau moi test, runner tai su dung `scenario-1/open-review-edge.ps1` de mo:

```text
Scenario 2 -> /admin/events
Scenario 3 -> /leaderboard
```

---

## 10. Van de da phan tich va cach xu ly

### "Scenario 2" khong co seed S2

Backend chi chap nhan `S1`, `S25`, `S3`. Vi vay Scenario 2 dung `S25`, dung voi
cut-point "S2.5" trong nghiep vu demo.

### S25 va S3 co ve giong nhau

Hai seed co chung roster, submission va score truoc diem re. Khac biet nam o
lifecycle:

- S25 dung truoc calculate/publish/award/complete.
- S3 da di het production workflow va o `COMPLETED`.

Do do can giu ca hai: mot scenario test action, mot scenario test output sau
khi event ket thuc.

### Publish result co phai la phan cong bo ket qua

Co. `CALCULATE RANKINGS` chi tao draft `RoundResult`. Participant endpoint chua
tra draft result. Chi sau khi bam `PUBLISH RESULTS`, ranking moi hien thi tren
participant leaderboard.

### Vi sao khong tai ca 4 file trong S3

Trang live scoring loai event `COMPLETED`, nen nut ranking `EXPORT CSV` khong
con duoc dung trong S3. Hai ranking CSV phai tai truoc khi complete event o S25.
Hai bao cao winner/participant duoc tai trong S3.

### Modal chan click sau khi doi account

Shared sign-in helper tu Scenario 1 tu dong dong:

- notification modal bang `DISMISS`;
- role guide bang `GOT IT`.

### API request can authentication

Shared helper lay JWT tu cookie `seal_auth_token` va gui Bearer token bang
Playwright request context. Test van di qua production API va authorization.

### Database bi reset

Ca hai runner dung `-Force`. Day la hanh vi co chu dich cua demo nhung co tinh
pha huy doi voi database local. Vi vay runner in warning va chi chay khi hai
port demo dang trong.

---

## 11. Xac minh

### Playwright discovery

Da chay:

```powershell
npx.cmd playwright test `
  e2e/scenario-2/00_s25_results_publish_awards.spec.ts `
  e2e/scenario-3/00_s3_completed_results_exports.spec.ts `
  --project=chromium --list
```

Ket qua:

```text
Total: 2 tests in 2 files
```

### TypeScript

Lan goi dau tien gap `TS5112` do TypeScript 6 khong cho truyen file truc tiep
trong khi van tu dong thay `tsconfig.json`.

Da chay lai:

```powershell
npx.cmd tsc --ignoreConfig --noEmit --skipLibCheck `
  --target ES2020 `
  --module ESNext `
  --moduleResolution bundler `
  --types node,@playwright/test `
  e2e/scenario-2/00_s25_results_publish_awards.spec.ts `
  e2e/scenario-3/00_s3_completed_results_exports.spec.ts
```

Ket qua: exit code `0`, khong co TypeScript error.

### Full runtime E2E

Trong phien tao hai file test, khong chay toan bo `.bat` vi moi runner se reset
database bang `-Force`.

Tai ngay lap log:

- Khong co Scenario 2 HTML report.
- Khong co Scenario 3 HTML report.
- Khong co Scenario 2 download directory.
- Khong co Scenario 3 download directory.
- `.last-run.json` hien co duoc tao luc 2026-07-23 18:06:31 va thuoc lan chay
  Scenario 1; khong duoc dung lam bang chung S25/S3 da pass.

Vi vay chi co the ket luan:

- Hai test duoc Playwright nhan dien.
- Hai test type-check thanh cong.
- Chua co bang chung trong workspace de tuyen bo full runtime E2E pass.

---

## 12. Gioi han va FR chua duoc cover

Hai scenario da cover:

- preliminary rank theo track;
- top N advanced;
- final global rank;
- draft result khong hien cho participant;
- publish result;
- participant leaderboard;
- top 3 prize;
- announce prize;
- complete event;
- participant/coordinator history;
- ranking, winner va participant CSV.

Hai scenario chua cover truc tiep:

1. Tao/sua event, track, round, criteria va assignment.
2. Participant nop bai trong S25/S3.
3. Tung judge dang nhap va nhap score trong S25/S3.
4. Disqualify team/submission va ly do.
5. Assert chi tiet audit log sau calculate/publish/award/complete.
6. Assert so luong/noi dung notification trong database.
7. Export Excel `.xlsx`.
8. GitHub/GitLab repository metadata.
9. RBL calibration, anonymized dataset va variance dashboard.

Khong duoc xem seed data la bang chung cac UI workflow chua liet ke o tren da
duoc E2E test.

---

## 13. Commit E2E lien quan

Tai ngay lap log, bon file Scenario 2/3 nam trong:

```text
2301839 test(e2e): add results and exports scenarios
```

Commit tren them:

```text
E2E testing/scenario-2/00_s25_results_publish_awards.bat
E2E testing/scenario-3/00_s3_completed_results_exports.bat
front-end/src/seal-web/e2e/scenario-2/00_s25_results_publish_awards.spec.ts
front-end/src/seal-web/e2e/scenario-3/00_s3_completed_results_exports.spec.ts
```

Tong thong ke cua commit: 4 file, 741 dong them.

---

## 14. Cach chay demo

### Scenario 2

1. Dong backend/frontend cu neu port `8080` hoac `5173` dang bi chiem.
2. Double-click:

```text
E2E testing/scenario-2/00_s25_results_publish_awards.bat
```

3. Cho Playwright chay het luong calculate -> publish -> prize -> complete.
4. Kiem tra HTML report va 2 ranking CSV.
5. Dong hai cua so backend/frontend.

### Scenario 3

1. Dam bao port `8080` va `5173` da trong.
2. Double-click:

```text
E2E testing/scenario-3/00_s3_completed_results_exports.bat
```

3. Kiem tra leaderboard/history.
4. Kiem tra HTML report.
5. Kiem tra winners CSV va participants CSV.

Khong chay Scenario 3 khi server cua Scenario 2 van con giu hai port. Scenario 3
se reset database va seed lai S3, khong tiep tuc state ma Scenario 2 vua tao.
