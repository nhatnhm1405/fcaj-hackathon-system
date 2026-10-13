# Merge Conflict Resolution Review - 2026-06-25

## Context

Branch hien tai: `hoangnhat-draft`

Muc tieu: pull code moi nhat tu `origin/develop` ve `hoangnhat-draft`, resolve conflict, sau do push lai `hoangnhat-draft` de tao/continue PR vao `develop`.

Lenh gay conflict:

```powershell
git pull origin develop
```

Git bao conflict o 6 file:

1. `back-end/src/seal-api/.gitignore`
2. `back-end/src/seal-api/src/main/java/com/seal/hackathon/service/ScoringService.java`
3. `back-end/src/seal-api/src/main/resources/application.properties`
4. `front-end/src/seal-web/src/app/routes/index.tsx`
5. `front-end/src/seal-web/src/features/events/CoordEventsPage.tsx`
6. `front-end/src/seal-web/src/features/users/AdminSystemLogsPage.tsx`

## Resolution Summary

### 1. `.gitignore`

Quyet dinh: giu ca hai phan thay doi.

- Giu phan cua `hoangnhat-draft`: ignore runtime/crash log.
- Giu phan cua `develop`: ignore `uploads/` va `protected/`.

Ly do: hai phan nay khong mau thuan. Log file va thu muc upload runtime deu khong nen commit len source.

### 2. `application.properties`

Quyet dinh: giu ca hai phan config.

- Giu phan cua `hoangnhat-draft`: `server.servlet.session.cookie.same-site=lax`.
- Giu phan cua `develop`: config upload file, multipart size, `app.upload.dir`, `app.problem.dir`.

Ly do: OAuth can session cookie setting de giu state khi redirect. Develop co them config upload/problem file cho chuc nang track problem. Hai phan co the cung ton tai.

### 3. `ScoringService.java`

Quyet dinh: merge logic cua ca hai nhanh.

- Giu logic moi tu `develop`: update/delete criteria va helper `findCriteriaInRound`.
- Giu logic validation cua `hoangnhat-draft`: `validateSubmitScoresRequest(request)` truoc khi xu ly submit score.

Ly do: `develop` co chuc nang CRUD criteria moi, con `hoangnhat-draft` co hardening validation cho submit scoring. Hai thay doi khong loai tru nhau.

### 4. `routes/index.tsx`

Quyet dinh: giu comment/behavior moi tu `develop` o `LandingPageWrapper`.

Ly do: comment nay giai thich viec tat ca user, ke ca user da login, van thay full standalone home page. Khong thay doi routing logic cua `hoangnhat-draft`.

### 5. `CoordEventsPage.tsx`

Quyet dinh: lay version tu `develop` cho file nay.

Ly do: conflict nam trong cac khoi UI lon. Version `develop` co nhieu tinh nang moi:

- track statistics
- drag/drop assign team vao track
- unassigned team pool
- edit/delete track
- edit/delete criteria
- Track Problems tab

Neu ghep thu cong tung khoi se de gay loi UI/logic. Viec chon version `develop` giup giu nguyen tinh nang moi da merge vao develop.

### 6. `AdminSystemLogsPage.tsx`

Quyet dinh: merge co chon loc.

- Giu UI expand/collapse log row tu `develop`.
- Bo cot `IP` va khong dung `l.ipAddress`.
- Dieu chinh table con 5 cot: expand, Time, Actor, Action, Detail.
- Dieu chinh `colSpan` cho loading/error/empty/detail row.

Ly do: branch `hoangnhat-draft` da xoa `ipAddress` khoi backend/API type, nen frontend khong nen render cot IP nua. Tuy nhien expand/collapse log detail la cai tien huu ich tu `develop`, nen van giu lai.

## Verification

Da kiem tra khong con marker conflict trong 6 file:

```powershell
rg -n "^(<<<<<<<|=======|>>>>>>>)" back-end/src/seal-api/.gitignore back-end/src/seal-api/src/main/java/com/seal/hackathon/service/ScoringService.java back-end/src/seal-api/src/main/resources/application.properties front-end/src/seal-web/src/app/routes/index.tsx front-end/src/seal-web/src/features/events/CoordEventsPage.tsx front-end/src/seal-web/src/features/users/AdminSystemLogsPage.tsx
```

Ket qua: khong con marker conflict.

`git status --short` cung khong con file `UU`, nghia la conflict da duoc resolve va stage.

## Notes

Hien con mot so file `??` chua tracked. Nhung file nay khong nam trong merge commit neu khong chay `git add` cho chung:

- `back-end/AI logs/AI_LOG_SUBMISSION_SCORING_SYSTEMLOG_VALIDATION_TESTS_2026-06-25.md`
- `docs/report/hackathon-event-validation-review.md`
- `docs/report/systemlog-submission-validation-review.md`

File review nay cung la file moi. Neu muon dua file review nay vao commit merge, can add rieng:

```powershell
git add docs/report/merge-develop-conflict-resolution-review-2026-06-25.md
```

Neu chi can dung de tham khao noi bo, co the de file nay untracked.
