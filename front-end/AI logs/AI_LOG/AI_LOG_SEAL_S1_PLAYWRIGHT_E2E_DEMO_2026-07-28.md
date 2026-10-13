# AI LOG - S1 Playwright E2E Demo Automation - 2026-07-28

> Ghi lai qua trinh xay dung bo demo E2E cho Scenario 1 cua SEAL Hackathon.
> Pham vi gom ba giai do `OPEN -> SETUP -> IN_PROGRESS`, chay tu cac file `.bat`
> tren Windows, mo trinh duyet de quan sat va tao Playwright HTML report.
>
> Phan nay chi them ha tang demo/test. Khong thay doi logic nghiep vu production
> cua backend hoac frontend.

---

## 1. Muc tieu

Truoc day Scenario 1 duoc khoi dong bang:

```powershell
.\run-demo.ps1 S1
npm run dev
```

Muc tieu cua phien la dong goi viec khoi dong va demo thanh ba buoc co the bam
truc tiep:

```text
00_open_phase.bat
  -> 01_setup_phase.bat
  -> 02_in_progress_phase.bat
```

Quyet dinh da chot:

- `00` reset database bang seed S1 va khoi dong backend/frontend.
- `01` va `02` tiep tuc tren cung database, khong reset lai.
- Dung chung backend port `8080` va frontend port `5173`.
- Khong tao file stop server; cac cua so backend/frontend duoc giu lai.
- Mac dinh chay Chromium co hien thi va cham de giao vien quan sat.
- Moi phase tao Playwright HTML report rieng.
- S1 dung o `IN_PROGRESS`, khong complete event.

---

## 2. Cau truc file

### Windows runners

```text
E2E testing/scenario-1/
  00_open_phase.bat
  01_setup_phase.bat
  02_in_progress_phase.bat
  open-review-edge.ps1
```

### Playwright tests va shared fixtures

```text
front-end/src/seal-web/e2e/scenario-1/
  00_open_account_team_flow.spec.ts
  01_setup_phase.spec.ts
  02_in_progress_phase.spec.ts
  s1DemoAccounts.ts
  s1TestUtils.ts
```

### Playwright setup

```text
front-end/src/seal-web/
  playwright.config.ts
  package.json
  package-lock.json
```

Playwright dependency hien tai:

```text
@playwright/test 1.61.1
```

---

## 3. Luong 00 - OPEN phase

Runner: `E2E testing/scenario-1/00_open_phase.bat`

Precondition:

- Port `8080` va `5173` phai dang trong.
- Neu mot trong hai port dang duoc su dung, runner dung som va bao loi ro rang.

Khoi dong:

```powershell
.\run-demo.ps1 S1 -Force
npm.cmd run dev -- --host 127.0.0.1 --port 5173 --strictPort
```

Playwright flow:

1. Dang ky mot tai khoan sinh vien FPT moi.
2. Coordinator duyet tai khoan.
3. Tai khoan moi gui join request vao Arsenal.
4. Arsenal leader chap nhan join request.
5. Arsenal leader moi `p42@fpt.edu.vn`.
6. `p42@fpt.edu.vn` chap nhan loi moi.
7. Coordinator kiem tra seed OPEN:
   - Event `SEAL Summer 2026`.
   - Track selection mode `RANDOM`.
   - Bon track.
   - Hai round.
   - Co final round.
   - Top N cua preliminary round la 2.
   - Moi round co 5 criteria.
8. Coordinator duyet `Real Madrid (pending)`.
9. Coordinator close registration.
10. Assert event chuyen sang `SETUP`, Arsenal co 5 thanh vien va khong con team
    `PENDING`.

`00` luon de backend/frontend tiep tuc chay de `01` su dung.

---

## 4. Luong 01 - SETUP phase

Runner: `E2E testing/scenario-1/01_setup_phase.bat`

Precondition:

- Database phai la ket qua cua `00`.
- Event `SEAL Summer 2026` phai o `SETUP`.
- Problem PDF phai ton tai. Gia tri mac dinh hien tai:

```text
C:\Users\DAO HOANG NHAT\Downloads\LAB_SWR.pdf
```

Co the override bang bien moi truong `E2E_PROBLEM_FILE`.

Neu backend hoac frontend chua chay, runner chi khoi dong phan bi thieu. Backend
duoc khoi dong bang:

```powershell
.\run-demo.ps1 S1 -NoDrop
```

Playwright flow:

1. Dang nhap coordinator va assert event dang `SETUP`.
2. Preview leftover grouping.
3. Neu con nguoi le, commit automatic grouping.
4. Assert `leftoverPeople = 0`.
5. Draw track cho moi approved team chua co track.
6. Assert khong con approved team chua co track.
7. Assert moi track co it nhat 2 approved team.
8. Gan mot mentor cho moi track:
   - `mentor1@fpt.edu.vn` -> Web Application.
   - `mentor2@fpt.edu.vn` -> AI Solution.
   - `mentor3@fpt.edu.vn` -> Education Tech.
   - `mentor4@fpt.edu.vn` -> Social Impact.
9. Hien thi mentor roster tren trang Coordinator Assignments.
10. Gan 2 judge cho moi track cua preliminary round.
11. Dung judge noi bo/guest co san va tao them 3 guest judge xac dinh neu thieu.
12. Assert preliminary round co tong cong 8 assignment, moi track co 2 judge.
13. Upload problem PDF cho ca 4 track qua UI.
14. Assert moi track hien thi dung file da upload.
15. Mo Criteria tab va apply template `Standard Rubric (5)` cho preliminary
    round qua UI.
16. Assert 5 criteria hien thi:
    - Idea.
    - Technical.
    - UI/UX.
    - Completeness.
    - Presentation.
17. Assert event van o `SETUP` de `02` tiep tuc.

Luu y: leftover grouping, draw track va phan assignment duoc thuc hien qua API
co authentication; upload problem va apply criteria template duoc thao tac tren UI.

---

## 5. Luong 02 - IN_PROGRESS phase

Runner: `E2E testing/scenario-1/02_in_progress_phase.bat`

Precondition:

- Database phai la ket qua cua `00` va `01`.
- Event phai o `SETUP` hoac mot lan chay dang do o `IN_PROGRESS`.

Thoi luong mac dinh:

```text
S1_CONTEST_SECONDS=600
S1_JUDGING_SECONDS=3600
```

Playwright flow:

1. Coordinator chuyen event tu `SETUP` sang `IN_PROGRESS`.
2. Start contest timer cho preliminary round.
3. Arsenal leader gui mentor support request.
4. Mentor dung track nhan request va mark resolved.
5. Arsenal leader dang nhap lai va kiem tra notification/support status.
6. Arsenal nop repo URL, demo URL, slide URL va description qua UI.
7. Mot seeded team dai dien cho cac track con lai nop bai qua API.
8. Coordinator stop contest timer tren UI.
9. Start judging timer.
10. Mot assigned judge chon submission va cham day du criteria tren UI.
11. Cac assigned judge con lai gui diem rieng theo tung criteria qua API.
12. Assert scoring progress cua moi submission da complete.
13. Coordinator calculate preliminary ranking tren UI.
14. Coordinator export ranking CSV.
15. Assert event van o `IN_PROGRESS`; S1 khong complete event.

---

## 6. Tai khoan demo co dinh

Mat khau chung cua demo:

```text
Test@1234
```

Tai khoan chinh:

| Vai tro | Tai khoan |
|---|---|
| Coordinator | `coordinator@fpt.edu.vn` |
| Arsenal leader | `p1@fpt.edu.vn` |
| Existing teamless member | `p42@fpt.edu.vn` |
| Mentors | `mentor1@fpt.edu.vn` -> `mentor4@fpt.edu.vn` |
| Internal judges | `judge1@fpt.edu.vn` -> `judge4@fpt.edu.vn` |
| Seeded guest judge | `guestjudge@gmail.com` |

Guest judge duoc tao them khi SETUP:

```text
s1.guestjudge.1@example.com
s1.guestjudge.2@example.com
s1.guestjudge.3@example.com
```

Ly do can them guest judge: S1 can `4 track x 2 judge = 8` assignment, trong khi
seed chi co 5 judge co san. Mot judge khong duoc gan vao hai track khac nhau trong
cung mot round.

---

## 7. Cau hinh demo va report

Gia tri mac dinh trong cac runner:

```text
E2E_SLOW_MO_MS=1500
E2E_STEP_PAUSE_MS=3000
E2E_TEST_TIMEOUT_MS=900000
PLAYWRIGHT_HTML_OPEN=never
```

Che do:

- Mac dinh: headed Chromium.
- `E2E_HEADLESS=1`: chay headless.
- `E2E_NO_PAUSE=1`: khong dung `pause` o cuoi `.bat`.
- `E2E_PROBLEM_FILE`: override problem PDF cho `01`.
- `E2E_CRITERIA_TEMPLATE`: override criteria template label.
- `E2E_MENTOR_JUDGE_PAUSE_MS`: thoi gian dung giua man mentor va judge.

Report:

```text
E2E testing/scenario-1/playwright-report/00_open_phase/index.html
E2E testing/scenario-1/playwright-report/01_setup_phase/index.html
E2E testing/scenario-1/playwright-report/02_in_progress_phase/index.html
```

Sau moi phase, `open-review-edge.ps1` mo mot cua so Edge rieng bang dedicated
profile:

```text
%LOCALAPPDATA%\SEAL-E2E\scenario-1-edge-review
```

Profile nay giu localhost session giua cac phase va khong dung chung voi cua so
Playwright.

---

## 8. Van de da gap va cach xu ly

### Port 5173/8080 bi trung

`00` bat buoc port trong vi no reset DB va tao mot phien demo moi. `01/02` kiem
tra tung port doc lap, tai su dung server dang chay hoac chi khoi dong phan bi thieu.

### Cua so `.bat` dong ngay khi loi

Runner luu exit code, in ket qua cuoi cung va dung `pause` theo mac dinh. Co the
tat pause bang `E2E_NO_PAUSE=1` cho automation.

### Timeout 180 giay khi demo cham

Playwright config duoc cho phep doc `E2E_TEST_TIMEOUT_MS`. Cac runner dat mac
dinh 900000 ms (15 phut) de du thoi gian cho headed demo co slow motion.

### Modal guide/notification chan click

Shared sign-in helper tu dong dong notification modal (`DISMISS`) va role guide
(`GOT IT`) sau moi lan doi tai khoan.

### API POST bi 403 do CSRF

Shared API helper lay JWT tu cookie `seal_auth_token` va gui:

```http
Authorization: Bearer <token>
```

Sau do request duoc thuc hien bang Playwright API request context. Cach nay van
di qua production API va production authorization, nhung khong bi browser CSRF
cookie flow chan.

### UI start-event readiness khong dong nhat

Trong `02`, event status hien duoc chuyen bang authenticated API `PUT` thay vi
bam nut Start Event. Ly do trong luc lam demo la UI readiness guard bao 0 team
o mot so track du backend roster da co team. Day la workaround cua test, khong
phai bang chung rang UI Start Event da duoc E2E coverage.

---

## 9. Xac minh

Trong qua trinh xay dung, bo ba phase da tung duoc chay theo thu tu sach
`00 -> 01 -> 02` va bao pass sau khi sua timeout/modal.

Artifact con trong workspace tai ngay lap log:

- `00_open_phase` HTML report, tao luc 2026-07-23 18:05:09.
- `01_setup_phase` HTML report, tao luc 2026-07-23 18:06:31.
- `front-end/src/seal-web/test-results/.last-run.json`:

```json
{
  "status": "passed",
  "failedTests": []
}
```

Khi tao log ngay 2026-07-28, khong chay lai toan bo ba phase. Artifact
`02_in_progress_phase` khong con trong workspace, vi vay khong dung artifact hien
tai de tuyen bo `02` vua pass o lan lap log nay.

---

## 10. Gioi han va viec can tiep tuc

1. Ba phase la stateful va phai chay dung thu tu. Neu `02` loi sau khi da thay
   doi timer/submission/score, nen chay lai tu `00` de reset DB sach.
2. Default `E2E_PROBLEM_FILE` la duong dan tuyet doi tren may demo; chuyen may
   phai dat lai bien moi truong hoac cung cap file dung duong dan.
3. Mot so buoc dung API truc tiep nen chua phai full UI E2E:
   - Grouping, draw track va assignment trong `01`.
   - Start event/start timer, submission phu va scoring phu trong `02`.
4. `02` stop contest timer truoc judging nhung chua stop judging timer truoc
   khi calculate ranking.
5. `02` chi tao mot so submission dai dien; chua chung minh day du Top 2
   advanced va cac team con lai eliminated tren tung track.
6. S25 va S3 da co seed cut-point trong backend, nhung chua co Playwright
   scenario va `.bat` tuong ung trong `E2E testing/`.
7. S1 co chu y khong chuyen event sang `COMPLETED`.

---

## 11. Cac commit E2E lien quan

```text
8202492 chore(e2e): add Playwright test setup
9f445eb test(e2e): add S1 account and team flow
aedc0a1 chore(e2e): add S1 Windows demo launcher
2d9eaab test(e2e): add shared scenario fixtures and configurable timeouts
e96c094 test(e2e): add reusable Edge review window helper
f5e88b5 test(e2e): harden open-phase demo runner and invite check
073ff5a test(e2e): add setup-phase grouping and assignment scenario
633b5d0 test(e2e): add in-progress scoring and ranking scenario
```

---

## 12. Cach chay Scenario 1 demo

1. Dong cua so backend/frontend cu neu port `8080` hoac `5173` dang bi chiem.
2. Double-click `00_open_phase.bat`.
3. Sau khi `00` pass, double-click `01_setup_phase.bat`.
4. Sau khi `01` pass, double-click `02_in_progress_phase.bat`.
5. Mo HTML report cua tung phase neu can xem lai chi tiet.

Khong chay `01` tren DB moi va khong chay `02` truc tiep sau mot lan `02` da
thay doi state dang do.
