# AI Log — SEAL: Rollback màn hình Participant (3 trạng thái) + Join Request + Notifications + loạt fix

**Dự án:** SEAL – Software Engineering Hackathon Management System (SU26SWP04)
**Ngày:** 2026-06-15
**Phạm vi phiên:** Khôi phục đúng 3 màn hình nghiệp vụ của Participant (**no-team / member / leader**) theo bản UI gốc (mockData-era) nhưng **map sang API thật**; xây tính năng backend mới **Join Request** (participant xin vào team, leader duyệt); kích hoạt **Notifications in-app** trên toàn hệ thống; và xử lý loạt bug phát sinh khi demo (mời lại sau khi rời team, confirm rời team, welcome OAuth, light-mode trang public).
**Công cụ:** Claude (Opus 4.8).
**Verify:** FE `tsc --noEmit -p tsconfig.app.json --ignoreDeprecations 6.0` + `npm run build` (vite); BE `./mvnw -o clean compile` (BUILD SUCCESS).
**Tham chiếu UI gốc:** `C:\Users\userp\Downloads\frontend-fix\src` (bản dùng `mockData.ts`). Lưu ý: `mockData.ts` đã bị xóa ở repo hiện tại → **dựng lại màn hình trên `apiClient`, không khôi phục mockData**.

> ⚙️ **Quy ước:** **[BE]** = backend (`seal-api`), **[FE]** = frontend (`seal-web`).

---

## 1. Bối cảnh & yêu cầu

Phiên trước đã gỡ `mockData` và viết lại `ParticipantDashboard` gọn. Tuy nhiên user phản ánh: **3 trạng thái participant bị dùng chung 1 dashboard sai**, không thể hiện đúng luồng nghiệp vụ. Yêu cầu:

- Khôi phục **đúng** màn hình gốc cho **no-team / member / leader** (bản gốc mới đúng flow).
- **TUYỆT ĐỐI không xóa component FE**; nếu backend thiếu dữ liệu để map → **bổ sung ở backend** (hoặc báo user).
- **Q&A liên tục** trong lúc làm để bám đúng nghiệp vụ; hỏi trước khi chỉnh sửa/đề xuất.

### Quyết định nghiệp vụ (chốt qua Q&A)
- **No-team:** giữ nút `WAIT FOR INVITE`, nhưng bấm vào mở panel gồm **2 phần**: (1) lời mời nhận được (accept/decline) + (2) **tìm team để xin join** (request-to-join).
- **"Tìm kiếm invite":** làm cả 2 — xem lời mời nhận được **và** chủ động search team xin vào ⇒ **thêm backend** tính năng Join Request.
- **Member dashboard:** chỉ-xem (đúng bản gốc); rời team / xem submission đi qua menu **My Team / Submission**, không thêm nút trên dashboard.
- **Leader duyệt join-request:** đặt ở trang **My Team** (`/team/view`), giữ dashboard đúng reference (phương án a).
- **Notifications:** in-app, **user refresh là thấy** (không cần WebSocket). Danh sách sự kiện bắn thông báo: team duyệt/từ chối/loại; mời gửi/được chấp nhận; join-request gửi/duyệt/từ chối; tài khoản được duyệt; kết quả công bố.
- **API gap → xử lý:** `Track.max_teams`/spots-left → **placeholder tạm** (API chưa có field); `// activity_feed` → map từ `notificationsApi.getAll()` (API không có endpoint audit cho participant); ràng buộc deadline đăng ký → **đã có sẵn** ở `TeamService.createTeam`.

---

## 2. [BE] Tính năng mới: Join Request (participant xin vào team, leader duyệt)

Phản chiếu (đảo chiều) của `TeamInvite`: ở đây participant gửi yêu cầu, leader phản hồi.

**File mới:**
- `entity/JoinRequest.java` — `request_id, team(FK), requester(FK User), message, status(PENDING/ACCEPTED/DECLINED), created_at, responded_at`; UNIQUE `(team_id, requester_user_id)`.
- `repository/JoinRequestRepository.java`
- `dto/request/CreateJoinRequestRequest.java`, `dto/response/JoinRequestResponse.java`, `dto/response/JoinableTeamResponse.java`
- `service/JoinRequestService.java`, `controller/JoinRequestController.java`

**Quy tắc nghiệp vụ trong service:**
- Gửi request: team thuộc event `OPEN` + còn trong hạn đăng ký; team chưa đầy (max 5); chưa là thành viên; chưa có team khác trong cùng event; không trùng request PENDING.
- Leader accept: kiểm tra lại event-uniqueness + cap 5; thêm `TeamMember`; **hủy các request PENDING khác** của người đó trong cùng event.
- `getJoinableTeams(userId, eventId?, query?)`: team event `OPEN` còn hạn ĐK, status PENDING/APPROVED, chưa đầy, người xem chưa có team trong event đó; kèm cờ `alreadyRequested`.

**Endpoint mới** (`/api/join-requests`, đều `hasRole('PARTICIPANT')`):
| Method | Path | Mục đích |
|---|---|---|
| POST | `/teams/{teamId}` | Gửi yêu cầu xin join |
| GET | `/joinable-teams?eventId=&query=` | Liệt kê team còn chỗ để xin |
| GET | `/my` | Request mình đã gửi |
| DELETE | `/{requestId}` | Rút request đang chờ |
| GET | `/teams/{teamId}` | Leader xem request pending của team |
| PUT | `/{requestId}/accept` | Leader duyệt |
| PUT | `/{requestId}/decline` | Leader từ chối |

**File sửa kèm:**
- `repository/TeamRepository.java` — thêm `findAllByEvent_Status(String)`.
- `config/SecurityConfig.java` — rule `/api/join-requests/** → hasRole('PARTICIPANT')`.

**DB:** vì `spring.jpa.hibernate.ddl-auto=none`, bảng **không tự sinh**.
- Thêm `CREATE TABLE JoinRequest (...)` vào `database scripts/seal_schema.sql` (sau `TeamInvite`).
- Tạo migration độc lập `database scripts/migration_join_request.sql` (dùng `CREATE TABLE IF NOT EXISTS`). **User đã chạy migration.**

---

## 3. [BE] Notifications in-app — kích hoạt `NotificationService.createNotification`

Phát hiện: hàm `createNotification` đã tồn tại nhưng **chưa service nào gọi** ⇒ trung tâm thông báo luôn rỗng. Đã inject `NotificationService` và bắn thông báo tại các sự kiện:

- `TeamService` — team **APPROVED / REJECTED / DISQUALIFIED** → báo **toàn bộ thành viên** (helper `notifyTeam`).
- `TeamInviteService` — gửi lời mời → báo người được mời; chấp nhận → báo leader.
- `JoinRequestService` — gửi join request → báo leader; duyệt → báo người xin; từ chối → báo người xin.
- `AccountService` — duyệt tài khoản → báo user ("Account approved — Welcome to SEAL Hackathon!"). *(Từ chối không bắn vì user bị `is_active=false`, không đăng nhập được.)*
- `RoundResultService` — công bố kết quả → báo các team kèm thứ hạng (dùng helper `isAdvanced` thay vì field — `advanced` là derived).

**Bổ sung field cho FE:** `dto/response/TeamInviteResponse.java` thêm `trackName` + `teamStatus` (+ map trong `TeamInviteService`) để card lời mời ở FE hiển thị đủ như bản gốc.

> Chưa làm (chờ user): notification "vòng mới mở" và "deadline sắp tới" (cái sau cần scheduler/cron). Không có push real-time/WebSocket — user refresh trang để thấy.

---

## 4. [FE] Khôi phục 3 màn hình Participant (map API, không xóa component)

Cấu trúc gốc = monolith `ParticipantDashboard.tsx` điều phối theo `team_id` + `is_leader` (2 file `TeamLeaderDashboard`/`TeamMemberDashboard` trong reference là **file chết, không được route** → bỏ qua). Tái dựng dạng module dưới `features/dashboard/dashboards/participant/`.

### 4.1 Orchestrator — `ParticipantDashboard.tsx` (viết lại)
- `screen==='success'` → `SuccessScreen` (ưu tiên trước khi check team, vì sau khi tạo team `team_id` đã set).
- `team_id !== null` → `ExistingTeamDashboard` (member/leader).
- `screen==='create'` → `CreateTeamScreen`.
- còn lại → `NoTeamDashboard` + `InvitationsDrawer` (mở bởi WAIT FOR INVITE) + `EventDetailDrawer`.
- Load số lời mời chờ (`invitesApi.getPending`) để hiện badge trên nút WAIT FOR INVITE; reload khi đóng drawer. Toast "Team Created!" qua `useNotifications().addToast`.

### 4.2 File mới
- `participant/utils/formatters.ts` — `fmtDate`, `fmtShort`, `roundStatusColor`, `teamStatusColor`.
- `participant/screens/NoTeamDashboard.tsx` — hero "Join an Event" + `CREATE A TEAM` + `WAIT FOR INVITE` (badge) + listing **open events** (số tracks/rounds + active round, map `teamsApi.getActiveEvents` + `roundsApi.getAll` cho từng event) + `VIEW DETAILS`.
- `participant/components/EventDetailDrawer.tsx` — tracks (badge **Max teams: 5** *placeholder*) + timeline rounds (`tracksApi`/`roundsApi`).
- `participant/components/InvitationsDrawer.tsx` — (1) lời mời nhận được dạng card đầy đủ (team/track/status/inviter/message/date, accept/decline qua `invitesApi`) + (2) section `// find_a_team`: search `joinRequestsApi.getJoinableTeams` → `REQUEST TO JOIN`.
- `participant/screens/CreateTeamScreen.tsx` — đúng UI gốc + placeholder spots + tạo team qua `teamsApi.create` + `refreshTeamContext`.
- `participant/screens/SuccessScreen.tsx` — màn hình thành công (PENDING approval).
- `participant/screens/ExistingTeamDashboard.tsx` — **member** (`// participant_console`, "Member of X", badge MEMBER, **không nút**) và **leader** (`// team_leader_console`, "Leading X", badge LEADER, **MANAGE TEAM + SUBMIT PROJECT**). Gồm `// team_info`, 3 **stat cards** (Submission/Last Round Rank/Next Deadline — map `submissionsApi`/`resultsApi`/`roundsApi`, hiện `—` khi chưa có dữ liệu thật), `// activity_feed` đổ từ `notificationsApi.getAll()`.

### 4.3 File sửa
- `shared/apiClient.ts` — thêm `joinRequestsApi` + types `JoinableTeam`, `JoinRequest`; thêm `trackName`/`teamStatus` vào `TeamInvite`.
- `features/teams/TeamViewPage.tsx` — **thêm card `// join_requests`** cho leader (accept/decline qua `joinRequestsApi`, khóa khi team đầy 5) + **modal confirm rời team** (nội dung theo ngữ cảnh leader/member); dọn 1 prop `onKeyDown` chết trên `PixelInput` (lỗi type có sẵn).

---

## 5. [FE/BE] Bug fixes phát sinh khi demo

1. **[BE] Mời lại sau khi đã rời team báo lỗi** — bản ghi `TeamInvite` cũ còn (status `ACCEPTED`) + UNIQUE `(team_id, invited_user_id)` chặn tạo mới. Fix `TeamInviteService.createInvite`: nếu đã có invite — PENDING thì báo lỗi, còn ACCEPTED/DECLINED thì **tái sử dụng, reset về PENDING** (cập nhật inviter/message/thời gian). Áp dụng **cùng cách cho `JoinRequestService.createRequest`** (thêm `findByTeam_TeamIdAndRequester_UserId` vào repo).
2. **[FE] Confirm rời team** — `TeamViewPage`: nút LEAVE TEAM mở modal xác nhận (leader còn người khác → khóa, yêu cầu chuyển quyền trước; leader đơn độc → cảnh báo giải tán; member → xác nhận thường).
3. **[BE] Sửa import hỏng chặn build** — `event/AccountApprovalEmailListener.java` dòng 6: `org.springframeworkail.MailException` → `org.springframework.mail.MailException` (file không do AI đụng tới, nghi sửa nhầm lúc demo). Đã quét toàn repo, không còn file khác.
4. **[BE] Welcome notification cho user OAuth** — `CustomOAuth2UserService.loadUser`: khi tài khoản OAuth tạo **lần đầu** (`isNewAccount`) bắn notification "Welcome to SEAL Hackathon!" (phân biệt Google/GitHub). Không bắn lại mỗi lần login.
5. **[FE] Thiếu pop-up "WELCOME BACK" khi login OAuth** — toast này chỉ có ở `LoginPage` (login mật khẩu); login OAuth đi qua `OAuth2RedirectPage` không bắn. Fix: sau khi set token, gọi `authApi.me()` rồi `addAuthToast` — user đã hoàn tất hồ sơ → "WELCOME BACK — Authenticated as {tên}"; user `PENDING_PROFILE` → "WELCOME — Complete your profile…".
6. **[FE] Light mode làm vỡ trang public ở footer** — `AboutPage` (About Our Project), `TeamPage` (Our Team), `ContactPage` dùng màu `C.*` dark cố định nhưng **không** gọi `useForceDark()` như landing/auth → bật light mode bị "chỗ đen chỗ trắng". Fix: thêm `useForceDark()` vào cả 3 trang (luôn render dark; tự khôi phục theme khi rời trang).

---

## 6. Tổng hợp file

**[BE] Tạo:** `entity/JoinRequest.java`, `repository/JoinRequestRepository.java`, `dto/request/CreateJoinRequestRequest.java`, `dto/response/JoinRequestResponse.java`, `dto/response/JoinableTeamResponse.java`, `service/JoinRequestService.java`, `controller/JoinRequestController.java`, `database scripts/migration_join_request.sql`.

**[BE] Sửa:** `repository/TeamRepository.java`, `config/SecurityConfig.java`, `dto/response/TeamInviteResponse.java`, `service/TeamInviteService.java`, `service/JoinRequestService.java`, `service/TeamService.java`, `service/AccountService.java`, `service/RoundResultService.java`, `security/oauth2/CustomOAuth2UserService.java`, `event/AccountApprovalEmailListener.java`, `database scripts/seal_schema.sql`.

**[FE] Tạo:** `participant/utils/formatters.ts`, `participant/screens/{NoTeamDashboard,CreateTeamScreen,SuccessScreen,ExistingTeamDashboard}.tsx`, `participant/components/{EventDetailDrawer,InvitationsDrawer}.tsx`.

**[FE] Sửa:** `dashboards/ParticipantDashboard.tsx`, `shared/apiClient.ts`, `features/teams/TeamViewPage.tsx`, `features/auth/OAuth2RedirectPage.tsx`, `features/landing/{AboutPage,TeamPage,ContactPage}.tsx`.

---

## 7. Kiểm thử & dữ liệu seed

- BE `./mvnw -o clean compile` → **BUILD SUCCESS** (138 file).
- FE `tsc --noEmit` → chỉ còn 3 lỗi **có sẵn từ trước** (`DashboardLayout`, `ProfilePage`), không liên quan; `npm run build` → **built OK**.
- **Tài khoản test** (mật khẩu chung `Test@1234`), dựa trên event 2 "SEAL Summer 2026" (OPEN):
  - No-team: `member2@fpt.edu.vn`
  - Member (Team Nexus): `member1@fpt.edu.vn`
  - Leader (Team Nexus): `leader1@fpt.edu.vn`
- Luồng kiểm: no-team gửi join-request → leader thấy ở **My Team** → accept → member; logout/login OAuth thấy toast WELCOME; bật light mode → footer pages dark đồng nhất.

---

## 8. Việc còn lại / lưu ý vận hành

- **Khởi động lại backend** để áp dụng thay đổi BE (notifications, fix mời-lại, welcome OAuth). Migration đã chạy → **không cần** chạy lại.
- API gap còn để **placeholder**: `Track.maxTeams`/spots-left (nếu muốn số thật → thêm field `maxTeams` cho Track + tính spots).
- Notification "vòng mới mở" / "deadline sắp tới" (cần scheduler) — chờ user xác nhận.
- Notification hiện **không real-time** (refresh để thấy) — nâng cấp WebSocket/SSE là phần mở rộng riêng.
