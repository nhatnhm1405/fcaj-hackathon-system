# Nhật Ký Refactor — Tách God-Class (Team/Assignment) + Dọn Trùng Lặp — Fullstack — Ngày 18/07/2026

> Đây là phần tiếp nối trực tiếp của đợt dọn rác trước
> (`AI_LOG_SEAL_REFACTOR_CLEANUP_BE_2026-06-19.md` /
> `..._DB_2026-06-19.md` / `..._FE_2026-06-19.md` /
> `..._POSTMERGE_2026-06-19.md`), hoàn tất 2 việc mà log Phase 2 (BE)
> đã **chủ động để lại** ở mục "5. Việc để lại": tách `TeamService.java`
> (611 LOC) và dọn Front-end (Phase 3, "phần sai nhiều nhất"). Phiên
> này đi xa hơn phạm vi cũ: tách thêm `AssignmentService.java`, tách
> 2 file Front-end lớn nhất (`CoordEventsPage.tsx`, `apiClient.ts`),
> quét toàn repo tìm code trùng lặp, và thực thi 2 quick-win dọn trùng.
>
> **Toàn bộ đã code xong, biên dịch/test xanh ở từng bước** (không để
> dở dang) — 394/394 test back-end, 147/147 test front-end, `tsc`
> sạch, `npm run build` thành công xuyên suốt phiên.

---

## 1. Bối cảnh & phương châm

User yêu cầu khảo sát lại toàn bộ `back-end/src/seal-api` để tìm
"god-class" (file gánh quá nhiều trách nhiệm) và tách nếu hợp lý,
sau đó làm tương tự cho front-end. Giữ đúng phương châm đã đặt ra từ
Phase 2: **chỉ tách khi thực sự là nhiều trách nhiệm trộn lẫn**, không
tách chỉ vì file dài — file dài nhưng cohesive (một trách nhiệm, nhiều
bước) thì để nguyên. Mọi lần tách đều đi qua **Plan Mode** trước khi
đụng code, verify build/test sau mỗi bước, và dùng `AskUserQuestion`
khi có quyết định kiến trúc cần xác nhận thay vì tự đoán.

---

## 2. Backend — Tách `TeamService.java` (611 LOC)

File cũ gộp chung: quản lý thành viên, tra cứu team, kiểm duyệt
(moderation), gán track, join-request, mời, rejoin-request — quá
nhiều trách nhiệm cho một class. Tách theo trục nghiệp vụ:

| File mới | Trách nhiệm |
|---|---|
| `TeamAccessGuard.java` | Guard dùng chung: `requireCurrentEntry`, `requireLeader`, `findLeader` — logic kiểm tra quyền/trạng thái team lặp lại ở nhiều service khác |
| `TeamMembershipService.java` | Thêm/xoá/rời thành viên, chuyển leader |
| `TeamQueryService.java` | Các query đọc: my-team, danh sách theo event, active-events-with-tracks |
| `TeamModerationService.java` | Coordinator duyệt/từ chối/disqualify team |
| `TeamTrackAssignmentService.java` | Gán/redraw track cho team (self-select + random) |
| `TeamResponseMapper.java` | Tách riêng logic map Entity → DTO (`TeamResponse`/`MyTeamResponse`/`TeamDetailResponse`) — trước đây nằm rải rác trong `TeamService`, giờ 1 nơi duy nhất, tái dùng được bởi mọi service ở trên |

`JoinRequestService.java`, `TeamInviteService.java`,
`TeamRejoinRequestService.java` đã tồn tại từ trước (không phải sản
phẩm của lần tách này) nhưng được cập nhật để dùng chung
`TeamAccessGuard` (xem mục 6).

Xoá: `TeamService.java`, `TeamServiceTest.java`. Test mới tương ứng
1-1 với service mới: `TeamMembershipServiceTest`,
`TeamModerationServiceTest`, `TeamQueryServiceTest`,
`TeamTrackAssignmentServiceTest` — tổng logic test được giữ nguyên,
chỉ chia lại theo file, không mất coverage.

`TeamController.java` cập nhật để wire đúng service theo từng
endpoint (Spring DI theo constructor, không đổi route/contract API).

---

## 3. Backend — Tách `AssignmentService.java`

Tương tự, tách theo đối tượng được gán (mentor vs judge) và theo vai
người xem (coordinator quản lý vs chính mentor/judge tự xem việc của
mình):

| File mới | Trách nhiệm |
|---|---|
| `MentorAssignmentService.java` | CRUD gán mentor ↔ track |
| `JudgeAssignmentService.java` | CRUD gán judge ↔ round/track, replace judge (có audit reason) |
| `AssignableStaffService.java` | Danh sách staff (mentor/judge) đủ điều kiện được gán — dùng chung bởi picker ở FE (`Assignments` page) |
| `CoordinatorEventHistoryService.java` | Lịch sử coordinator từng chạy event nào (tách khỏi assignment logic thuần tuý) |
| `EventRoleGranter.java` | Cấp `UserEventRole` (MENTOR/JUDGE) khi gán — logic dùng chung, trước đây lặp lại ở nhiều nhánh gán |

Xoá `AssignmentService.java`. `AssignmentController.java` và
`CoordinatorAssignmentController.java` cập nhật wiring tương ứng
(route giữ nguyên).

---

## 4. Quyết định KHÔNG tách — `LeftoverGroupingService` & `HackathonEventService`

Cả 2 đều dài nhưng khảo sát cho thấy **cohesive** (một trách nhiệm rõ
ràng, chia nhiều bước tuần tự) chứ không phải trộn nhiều trách nhiệm
khác nhau — tách ra sẽ chỉ tạo thêm file mà không giảm độ phức tạp
thật. Đưa ra `AskUserQuestion` để user tự quyết thay vì tự ý tách,
user chọn **giữ nguyên cả hai**. Ghi nhận lại để lần sau không tốn
công khảo sát lại.

---

## 5. Frontend — hoàn tất "Phase 3" đã hẹn từ Phase 2

### 5.1. Tách `CoordEventsPage.tsx`

Tách theo tab UI sẵn có (Tracks / Rounds / Criteria / Audit) thành 4
component riêng: `TracksTab.tsx`, `RoundsTab.tsx`, `CriteriaTab.tsx`,
`AuditTab.tsx`, cộng với `eventUtils.tsx` chứa type/helper dùng
chung (`EventRow`, `normalizeEvent`, badge/format helper) cho cả
`CoordEventsPage.tsx` lẫn `AdminEventsPage.tsx`.

**1 bug regression xảy ra rồi được fix ngay trong phiên**: khi tách,
`<DndProvider>` (react-dnd) bị thu hẹp phạm vi chỉ bọc
`TracksTab.tsx`, nhưng `LeftoverGroupingModal.tsx` (render như overlay
độc lập bởi `CoordEventsPage`, cũng dùng `useDrag`/`useDrop`) lọt ra
ngoài context → crash "Expected drag drop context" khi mở modal Group
Leftover. Fix: đưa `<DndProvider>` trở lại bọc toàn bộ
`CoordEventsPage`, bỏ provider thừa trong `TracksTab.tsx`. Verify
sống qua Playwright trên event SETUP thật.

### 5.2. Tách `apiClient.ts` (1888 dòng, 58 file import)

File lớn thứ nhì front-end, mọi API call của app gộp chung 1 file,
mỗi domain đã có ranh giới rõ (comment `// ── Domain ──`, không chồng
lấn). Vì có **58 file tiêu thụ** qua
`import { X } from "@/shared/apiClient"`, tách theo domain vào
`shared/api/*.ts` (core, auth, admin, events, tracks, rounds, teams,
submissions, scoring, ai, results, prizes, notifications,
assignments, support, coordinator, participationRequests,
teamRejoinRequests — 18 module) rồi biến `apiClient.ts` thành barrel
thuần (`export * from './api/...'`). **Không đổi import path ở bất
kỳ file nào trong 58 file tiêu thụ** — xác nhận bằng grep sau khi
tách: chỉ còn `apiClient.ts` khớp `from ['"]@/shared/apiClient['"]`
ngoài thư mục `shared/api/`.

---

## 6. Quét toàn repo tìm code trùng lặp/file lớn/thiếu test

Chạy song song nhiều agent nền để quét: hàm trùng lặp giữa các
service, file FE quá lớn còn sót, chỗ thiếu test. Kết quả đáng chú ý
(chưa xử lý hết, xem mục 8):

- **Trùng logic**: `JoinRequestService`/`TeamInviteService` tự định
  nghĩa lại `requireCurrentEntry`/`findLeader` — bản sao y hệt của
  `TeamAccessGuard` (vừa tách ở mục 2) nhưng chưa được wire vào.
- `AdminEventsPage.tsx` tự định nghĩa lại type/helper format event
  đã có sẵn trong `eventUtils.tsx` (vừa tách ở mục 5.1) thay vì tái
  dùng.
- File FE còn lớn nhưng chưa xử lý: `ScoringService.java`,
  `AuthService.java` (BE); `TeamViewPage.tsx`, `DashboardLayout.tsx`,
  `CoordTeamsPage.tsx` (FE, mức độ nhẹ hơn).
- Thiếu test: `LeftoverGroupingService`, `TeamAccessGuard`,
  `TeamResponseMapper`, `MentorAssignmentService`,
  `JudgeAssignmentService`.

---

## 7. Thực thi 2 quick-win dọn trùng

### 7.1. Wire `TeamAccessGuard` vào `JoinRequestService`/`TeamInviteService`

Thay method riêng `requireCurrentEntry`/`findLeader` (byte-identical
với bản trong `TeamAccessGuard`) bằng gọi thẳng
`teamAccessGuard.requireCurrentEntry(team)` /
`teamAccessGuard.findLeader(team)`, xoá method cũ.

**Bẫy đã tránh**: `requireLeader` ở 2 service này ném
`ForbiddenException` (403), còn `TeamAccessGuard.requireLeader` ném
`BadRequestException` (400) — có test cố định
(`TeamInviteServiceTest.java:203,427`) assert đúng
`ForbiddenException.class`. Nếu dùng chung sẽ đổi status code API mà
không ai yêu cầu. **Cố tình chỉ dedup phần byte-identical**
(`requireCurrentEntry`/`findLeader`), giữ nguyên `requireLeader`
riêng của từng service.

### 7.2. `AdminEventsPage.tsx` dùng lại `eventUtils.tsx`

Xoá type/helper cục bộ trùng lặp, import thẳng từ
`eventUtils.tsx` — cùng nguồn với `CoordEventsPage.tsx`, đảm bảo 2
trang admin/coordinator luôn hiển thị event nhất quán.

---

## 8. Việc để lại (đã khảo sát, KHÔNG làm trong phiên này)

- Tách `ScoringService.java`, `AuthService.java` (BE) — chưa yêu cầu.
- Tách `TeamViewPage.tsx`, `DashboardLayout.tsx`, `CoordTeamsPage.tsx`
  (FE, mức độ nhẹ) — chưa yêu cầu.
- Bổ sung test cho `LeftoverGroupingService`, `TeamAccessGuard`,
  `TeamResponseMapper`, `MentorAssignmentService`,
  `JudgeAssignmentService` — đã hỏi user (mục JoinRequestService
  test riêng), chưa được xác nhận làm tiếp.
- Đổi tên `AssignmentController` cho bớt nhầm với
  `CoordinatorAssignmentController` — carry-over từ Phase 2, vẫn chưa
  làm (đụng route/import nhiều file, rủi ro cao hơn lợi ích).

---

## 9. Verify

- Backend: `./mvnw -o -q compile` + `./mvnw -o -q test` sau **mỗi**
  bước tách — xuyên suốt phiên luôn giữ **394/394 test xanh**
  (breakdown cuối phiên: `TeamMembershipServiceTest` 38,
  `TeamModerationServiceTest` 20, `TeamQueryServiceTest` 24,
  `TeamTrackAssignmentServiceTest` 7, `TeamInviteServiceTest` 26,
  `TeamRejoinRequestServiceTest` 13, `JoinRequestServiceTest` 2, cộng
  toàn bộ suite còn lại không bị ảnh hưởng).
- Frontend: `npx tsc --noEmit` + `npm run test` (147/147) +
  `npm run build` sau mỗi bước tách — không method/type nào bị lệch
  qua barrel `apiClient.ts` hay `eventUtils.tsx`.
- Verify sống qua Playwright (dev server thật, login coordinator
  seed) cho riêng phần regression DnD ở mục 5.1 — xác nhận Group
  Leftover modal mở lại bình thường sau fix.

---

## 10. Ngoài phạm vi log này

Sau đợt refactor/dọn trùng ở trên, phiên còn tiếp tục với một loạt
việc **không thuộc refactor/cleanup** (sửa bug UX lẻ, thêm tính năng
nhỏ, chỉnh seed demo) — liệt kê ngắn để tra cứu sau, không đi vào chi
tiết vì không phải trọng tâm log này:

- Thêm thông báo khi participant bị leader remove khỏi team.
- Ẩn dropdown "Event" ở trang Submit Project khi participant chỉ có
  1 team/1 lịch sử event.
- Rút gọn seed scenario demo: bỏ `S0`/`S2` (cũ), giữ `S1`; sau đó đổi
  tên `S25` → `S2` để khớp kịch bản demo "chờ tính ranking" mới.
- Sửa hiển thị track capacity (badge "x/max teams" lệch số giữa các
  track dù chia đều) và badge "NEW" theo từng thành viên trong modal
  Group Leftover.
- Seed sẵn 1 Criteria Template mặc định (5 tiêu chí, có mô tả, tiếng
  Anh) để dropdown Template không còn trống.
- Chặn guest judge được gán làm mentor (chặn cả UI lẫn API).
- Ẩn nút "Request Help" khỏi thành viên không phải leader.
- Thêm field `topic` (chủ đề chung cuộc thi) cho `HackathonEvent`,
  seed + hiển thị trên toàn bộ màn hình liên quan (Admin, Coordinator,
  Landing page, Dashboard/MyTeam của participant); đổi tên event demo
  từ "SEAL Demo Summer 2026" → "SEAL Summer 2026".
- Điều chỉnh layout khối "Team Info" trên Dashboard participant (đổi
  thứ tự Team→Event→Track→Round, co giãn độ rộng, làm màu chữ topic
  sáng hơn).

Chưa commit bất kỳ thay đổi nào ở trên tại thời điểm ghi log này —
user chưa yêu cầu.
