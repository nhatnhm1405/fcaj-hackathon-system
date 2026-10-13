# Feature Hoangnhat Update - Review Notes - 2026-06-30

Ngay ghi note: 2026-06-30

Pham vi file nay:

- Ghi lai cac thay doi da bo sung khi kiem tra local feature voi `origin/develop`.
- Tap trung vao cac phan co nguy co bi mat code khi merge: schema/seed, mentor history, dashboard nav/routes, history/awards pages, va API client.
- Khong phai la toan bo lich su feature read-only/remember-me/password-reset tu dau; cac log cu cua feature do van nam trong AI log rieng.

---

## 1. Ket qua kiem tra conflict

Da kiem tra theo huong khong sua branch `develop`:

- `git merge-tree` bao cac file `changed in both`, nhung khong con conflict marker that.
- Da tao clone tam, copy working tree hien tai vao do, thu merge snapshot local vao `origin/develop`.
- Ket qua Git bao: `Automatic merge went well; stopped before committing as requested`.

Ket luan:

- Hien khong con conflict Git truc tiep.
- Van can review nhung file auto-merge duoc vi local va develop cung sua cung file.

---

## 2. Database schema

File:

- `back-end/database scripts/seal_schema.sql`

Da chot local:

- Header cap nhat thanh `-- 28 tables`.
- Giu day du 26 bang tu `develop`.
- Them 2 bang cua feature:
  - `ParticipationAccessRequest`
  - `PasswordResetOtp`
- Giu lai 2 bang timer tu `develop`:
  - `RoundTimer`
  - `RoundTimerNotice`
- Giu bang `Prize` tu `develop`.
- `User.is_active` co comment moi:
  - `FALSE = participant read-only, TRUE = writable/active`
- `Round.top_n_advance` va comment `RoundResult.rank_position` theo per-track van duoc giu.

Ket qua doi chieu:

- Local schema co 28 bang.
- Develop schema co 26 bang.
- Local khong thieu bang nao cua develop.
- Local them dung 2 bang moi cua feature.

---

## 3. Database seed

File:

- `back-end/database scripts/seal_seed.sql`

Da dong bo tu `develop`:

- Round preliminary:
  - `top_n_advance: 5 -> 2`
  - comment: `Top 2 PER TRACK`
- Round semi-final:
  - `top_n_advance: 2 -> 1`
  - comment: `Top 1 PER TRACK`
- `RoundResult` chuyen sang rank per-track.
- `Prize` chuyen sang event-wide awards:
  - `Top 1 - Spring 2026`
  - `Top 2 - Spring 2026`
  - `Top 3 - Spring 2026`
- `AWARD_PRIZE` audit log duoc cap nhat theo event-wide winners format.

Da dong bo password:

- Toan bo seed user dang dung hash giong `develop`:
  - `$2a$10$N9qo8uLOickgx2ZMRZoMyeIjZAgcfl7p92ldGxad68LJZdL17lhWy`
- Da kiem tra:
  - Hash cu `$2a$10$0ck6TfX5...` khong con trong `seal_seed.sql`.
  - Hash develop xuat hien 43 lan.

Da giu lai thay doi read-only cua feature:

- `leader4@fpt.edu.vn` co `is_active = FALSE`.
- `member6@uit.edu.vn` co `is_active = FALSE`.

---

## 4. Backend mentor history

Files:

- `back-end/src/seal-api/src/main/java/com/seal/hackathon/service/AssignmentService.java`
- `back-end/src/seal-api/src/main/java/com/seal/hackathon/controller/AssignmentController.java`
- `back-end/src/seal-api/src/main/java/com/seal/hackathon/repository/RoundRepository.java`
- `back-end/src/seal-api/src/main/java/com/seal/hackathon/dto/response/MentorHistoryResponse.java`
- `back-end/src/seal-api/src/main/java/com/seal/hackathon/entity/Prize.java`
- `back-end/src/seal-api/src/main/java/com/seal/hackathon/repository/PrizeRepository.java`

Da bo sung tu `develop`:

- Them `AssignmentService.getMentorHistory(Integer userId)`.
- Them endpoint:
  - `GET /api/mentor/assignments/history`
- Them DTO:
  - `MentorHistoryResponse`
- Khoi phuc entity/repository can cho mentor history:
  - `Prize`
  - `PrizeRepository`
- Them method repository:
  - `RoundRepository.findFirstByEvent_EventIdAndIsFinalTrue(Integer eventId)`

Da giu lai thay doi cua feature:

- `listApprovedStaff()` van KHONG filter `isActive`.
- Khong them lai dong:
  - `.filter(u -> Boolean.TRUE.equals(u.getIsActive()))`

Verification:

- Da chay backend compile bang Maven binary trong `.m2`:
  - `mvn -q -DskipTests compile`
- Ket qua: pass.

---

## 5. Dashboard layout

File:

- `front-end/src/seal-web/src/app/layouts/DashboardLayout.tsx`

Da bo sung lai tu `develop`:

- Them `DASHBOARD_PATHS`.
- Them nav participant:
  - `/history`
- Them nav mentor:
  - `/mentor/history`
- Them nav coordinator:
  - `/coordinator/prizes`
- Them page title map:
  - `/history` -> `History`
  - `/mentor/history` -> `Mentoring History`
  - `/coordinator/prizes` -> `Awards`

Da giu lai fix local cua feature:

- Nut/logo tren top navbar van navigate ve landing page `/`.
- Logo hitbox duoc gioi han lai de khong tran xuong nut Dashboard trong sidebar.
- Dashboard sidebar item van highlight dung cho cac dashboard aliases.

---

## 6. Frontend routes

File:

- `front-end/src/seal-web/src/app/routes/index.tsx`

Da bo sung lai routes tu `develop`:

- Participant:
  - `/history`
  - component: `HistoryPage`
- Mentor:
  - `/mentor/history`
  - component: `MentorHistoryPage`
- Coordinator:
  - `/coordinator/prizes`
  - component: `CoordPrizesPage`

Da giu lai route feature:

- Admin:
  - `/admin/participation-requests`
  - component: `AdminParticipationRequestsPage`

---

## 7. Frontend API client

File:

- `front-end/src/seal-web/src/shared/apiClient.ts`

Da bo sung lai tu `develop`:

- `clearTopNAdvance` trong `UpdateRoundPayload`.
- Round timer client:
  - `TimerPhase`
  - `TimerStatus`
  - `RoundTimerState`
  - `StartTimerPayload`
  - `timersApi`
- Team history:
  - `TeamHistoryEntry`
  - `teamsApi.getMyHistory()` tra ve `TeamHistoryEntry[]`
- Awards/prizes:
  - `Prize`
  - `CreatePrizePayload`
  - `UpdatePrizePayload`
  - `prizesApi`
- Mentor history:
  - `MentorHistoryEntry`
  - `assignmentsApi.getMentorHistory()`
- Team member compatibility:
  - `TeamMember.role` optional
  - `TeamMember.memberRole` optional
  - `TeamMember.joinedAt` optional

Da giu lai API feature:

- `getTokenStorage`
- forgot/reset password APIs:
  - `/api/auth/forgot-password`
  - `/api/auth/verify-reset-otp`
  - `/api/auth/reset-password`
- `participationRequestsApi`
- Khong restore `activateUser` / `deactivateUser` vi rule feature da bo nut activate/deactivate account cu.

---

## 8. Frontend pages restored/added

Files:

- `front-end/src/seal-web/src/features/teams/HistoryPage.tsx`
- `front-end/src/seal-web/src/features/teams/CertificateModal.tsx`
- `front-end/src/seal-web/src/features/tracks/MentorHistoryPage.tsx`
- `front-end/src/seal-web/src/features/scoring/CoordPrizesPage.tsx`

Da them:

- Participant history page:
  - Hien danh sach event/team da tham gia.
  - Hien members, round results, submissions neu backend co data.
  - Defensive voi data thieu de UI khong crash.
- Certificate modal:
  - Modal don gian de hien certificate participation/achievement.
- Mentor history page:
  - Goi `assignmentsApi.getMentorHistory()`.
  - Hien event -> track -> teams.
- Coordinator awards page:
  - Chon event.
  - Doc final round, teams, prizes.
  - Auto-generate awards.
  - Them/xoa/save prize slot.
  - Announce prizes.
  - Export winners CSV.
  - Export participants CSV.

Ly do khong copy y nguyen 100% UI tu `develop`:

- Local feature hien tai da co rule read-only/route/API khac, nen cac page duoc them theo huong tuong thich va defensive.
- Muc tieu la giu lai chuc nang/routes/API cua develop ma khong lam frontend build fail.

---

## 9. Verification frontend

Da chay:

```powershell
npm.cmd run build
```

Thu muc chay:

```text
front-end/src/seal-web
```

Ket qua:

- Vite build pass.
- Co warning chunk size lon hon 500 kB, day la warning build size, khong phai loi compile.

Ghi chu:

- Lenh `npm run build` bi PowerShell chan do execution policy voi `npm.ps1`.
- Da dung `npm.cmd run build` de chay dung tren Windows.

---

## 10. Luu y backend con thieu so voi frontend vua restore

Khi kiem tra backend hien tai:

- Co mentor history backend:
  - `GET /api/mentor/assignments/history`
- Co team history endpoint:
  - `GET /api/teams/my/history`
  - Nhung hien tra `MyTeamResponse`, chua phai rich history data giong `TeamHistoryEntry` cua develop.
- Chua thay backend prize endpoints trong local hien tai:
  - `/api/events/{eventId}/prizes`
  - `/api/events/{eventId}/prizes/auto-generate`
  - `/api/events/{eventId}/prizes/announce`
- Chua thay backend timer endpoints trong local hien tai:
  - `/api/events/{eventId}/rounds/{roundId}/timer/{phase}`

Tac dong:

- Frontend da co lai route/API/page cua develop va build pass.
- `MentorHistoryPage` co backend tuong ung.
- `HistoryPage` co endpoint nhung co the chi hien duoc data co san neu backend chua tra rounds/prize/submissions theo rich format.
- `CoordPrizesPage` can restore backend prize controller/service neu muon chay day du.
- `timersApi` da duoc them lai de khong mat API cua develop, nhung chua co UI route moi trong scope nay va backend timer endpoints hien chua thay.

---

## 11. Files nen review khi add/commit

Files chinh da sua/tao trong dot dong bo nay:

- `back-end/database scripts/seal_schema.sql`
- `back-end/database scripts/seal_seed.sql`
- `back-end/src/seal-api/src/main/java/com/seal/hackathon/controller/AssignmentController.java`
- `back-end/src/seal-api/src/main/java/com/seal/hackathon/service/AssignmentService.java`
- `back-end/src/seal-api/src/main/java/com/seal/hackathon/repository/RoundRepository.java`
- `back-end/src/seal-api/src/main/java/com/seal/hackathon/dto/response/MentorHistoryResponse.java`
- `back-end/src/seal-api/src/main/java/com/seal/hackathon/entity/Prize.java`
- `back-end/src/seal-api/src/main/java/com/seal/hackathon/repository/PrizeRepository.java`
- `front-end/src/seal-web/src/app/layouts/DashboardLayout.tsx`
- `front-end/src/seal-web/src/app/routes/index.tsx`
- `front-end/src/seal-web/src/shared/apiClient.ts`
- `front-end/src/seal-web/src/features/teams/HistoryPage.tsx`
- `front-end/src/seal-web/src/features/teams/CertificateModal.tsx`
- `front-end/src/seal-web/src/features/tracks/MentorHistoryPage.tsx`
- `front-end/src/seal-web/src/features/scoring/CoordPrizesPage.tsx`

Untracked/ngoai scope can can nhac:

- `BUSINESS_RULES.md`
- `back-end/src/seal-api/hs_err_pid4464.log`
- `front-end/package-lock.json`

Khuyen nghi:

- Khong dua `hs_err_pid4464.log` vao PR.
- `front-end/package-lock.json` o thu muc `front-end/` can xem lai vi project thuc te nam o `front-end/src/seal-web`.
- `BUSINESS_RULES.md` tuy noi dung co the huu ich, nhung truoc do da duoc note la co the de ngoai PR neu khong muon dua docs rule vao.

---

## 12. Ket luan review

Trang thai hien tai sau khi bo sung:

- Khong con conflict Git truc tiep voi `develop`.
- Schema/seed da dong bo cac thay doi quan trong cua `develop` va giu thay doi read-only cua feature.
- Mentor history backend/frontend da duoc restore.
- Frontend da co lai nav/routes/API/pages cho History, Mentor History, Awards.
- Frontend build pass.

Phan can quyet dinh tiep:

- Neu muon Awards page chay day du, can restore backend prize controller/service tu `develop`.
- Neu muon timer feature chay day du, can restore backend round timer controller/service/repository tu `develop`.
- Neu muon participant history hien du rich data nhu develop, can kiem tra/restore backend response cho `/api/teams/my/history`.
