# Nhật Ký — Báo Cáo Merge Sprint 3 (develop → main) + Fix 2 Rủi Ro Nghiêm Trọng Nhất — Ngày 18/07/2026

> User chuẩn bị hoàn thành Sprint 3 (sprint cuối), cần context so sánh
> `main` với `develop` trước khi merge, tham khảo format
> `docs/report/sprint-2-merge-report.md` (báo cáo merge Sprint 2) để
> viết báo cáo tương tự cho Sprint 3 — nhưng **chi tiết hơn đáng kể**
> vì không còn Sprint 4 nào để bắt lỗi nếu bỏ sót. Trong lúc rà rủi ro
> để đưa vào báo cáo, phát hiện 2 vấn đề thật trong code (không phải
> suy đoán) và được yêu cầu fix trực tiếp, kèm test.

---

## 1. So sánh `main` ↔ `develop`

- `main` (`d9dce0f`, PR #348, merge 27/06/2026) là **merge-base chính
  xác** của `develop` (`296e03a`, PR #387, merge 18/07/2026) — main 0
  commit ahead, develop 232 commit ahead.
- `git merge-tree --write-tree origin/main origin/develop` chạy sạch,
  exit code 0, không có conflict section nào → merge là
  **fast-forward thuần túy**, không xung đột text.
- Quy mô: 308 file thay đổi, +34.035/−7.911 dòng.

---

## 2. Đọc báo cáo Sprint 2 để lấy format tham khảo

Đọc `docs/report/sprint-2-merge-report.md` để nắm cấu trúc: header +
bảng metric → Executive Summary → Feature Map → Database & Migration
(bảng structural changes từng bảng) → Backend API (bảng authorization
group) → Frontend → **Risks, Findings & Recommendations** (bảng
severity) → Pre-Merge Checklist + lệnh merge đề xuất.

---

## 3. Viết `docs/report/sprint-3-merge-report.md`

Dựng lại đúng khung Sprint 2 nhưng đào sâu hơn cho Sprint 3:

- **Feature Map** 12 hạng mục: tách Team/TeamEventEntry, rejoin
  request, participant history snapshot, participation access
  request, password reset OTP, leftover-team grouping, mentor
  support, auth hardening (cookie + CSRF), code-first schema
  (Hibernate), refactor tách god-class cuối sprint, scoring/ranking
  polish, misc UX.
- **Database & Migration**: đối chiếu diff entity thật giữa
  `origin/main`/`origin/develop` (`git diff --stat -- .../entity/`)
  để liệt kê chính xác bảng mới (`TeamEventEntry`,
  `TeamRejoinRequest`, `ParticipantEventHistory`,
  `ParticipationAccessRequest`, `PasswordResetOtp`,
  `MentorSupportRequest`) và cột đổi (`Team` mất `event_id`/`track_id`
  /`status`, được `is_active`; `JoinRequest`/`TeamInvite` thêm
  `event_id`; `HackathonEvent` thêm `topic`...). Ghi rõ đổi chiến lược
  schema: từ `seal_schema.sql`/`seal_seed.sql` rebuild thủ công sang
  code-first Hibernate (`ddl-auto=update`), docker-compose không còn
  mount file `.sql` nào vào `initdb.d` nữa.
- **Backend API / Frontend**: liệt kê controller/service mới
  (`CsrfController`, `TeamRejoinRequestController`,
  `ParticipantHistoryController`, ...), và refactor cuối sprint
  (`AssignmentService` → `JudgeAssignmentService`/
  `MentorAssignmentService`/`AssignableStaffService`; `TeamService` →
  `TeamQueryService`/`TeamModerationService`/`TeamMembershipService`/
  `TeamTrackAssignmentService`/`TeamAccessGuard`/`TeamResponseMapper`).

---

## 4. Rà rủi ro thật trong code (không phải liệt kê từ commit log)

Dùng 1 agent Explore đọc trực tiếp code trên `develop` theo 7 hướng
nghi vấn (ddl-auto, Team/TeamEventEntry split, JWT cookie/CSRF,
leftover-grouping concurrency, rejoin request, refactor cuối sprint,
TODO/FIXME sót lại). Kết quả 7 finding, xếp theo severity:

| # | Severity | Tóm tắt |
|---|---|---|
| 1 | High | `ddl-auto=update` chạy cả prod (`docker-compose.yml`), không phân môi trường — mọi lần deploy tự ALTER schema thật, không review |
| 2 | Medium-High | `HackathonEventService.lockCompletedEventParticipantsReadOnly` deactivate `Team.isActive` mù event — không check team có entry ở event khác chưa complete (khác logic User đã có `hasNonCompletedMembership`) |
| 3 | Medium | `LeftoverGroupingService.commit/manualAssign/applyPlan` đọc-rồi-ghi không lock → race condition khi 2 coordinator thao tác gần như đồng thời |
| 4 | Low-Medium | Chạy lại leftover-grouping có thể xoá override thủ công (đã tự document trong code) |
| 5 | Low | `TeamRejoinRequestService` không có DB constraint chống 2 request PENDING trùng |
| 6 | Low | `SecurityConfig` khai trùng matcher `/api/join-requests/**` (2 dòng giống hệt) |
| 7 | Info | `/api/events/**` permitAll ở URL layer, an toàn nhờ `@PreAuthorize` từng method — chỉ là rủi ro cấu trúc cho endpoint mới sau này |

Đưa cả 7 vào báo cáo kèm **đề xuất fix cụ thể** (không chỉ nêu vấn đề)
vì đây là sprint cuối.

---

## 5. Fix #1 — `ddl-auto=update` không phân môi trường

User yêu cầu giải thích lại nhiều lần trước khi cho code (đúng tinh
thần cẩn trọng — sửa nhầm ảnh hưởng schema DB thật). Điểm mấu chốt
làm rõ: hệ thống chỉ có **một** `docker-compose.yml`, vừa là server
demo (Caddy + `api.sealhackathon.io.vn`) vừa là nơi đổi
`SEED_SCENARIO` để demo từng kịch bản — nên **không thể** tự ý bật
`validate` mặc định (DB rỗng sau khi drop để đổi scenario sẽ khiến
app không khởi động được).

**Fix (an toàn tuyệt đối, không đổi hành vi mặc định)**:

- `application.properties`: `ddl-auto=update` →
  `ddl-auto=${DDL_AUTO:update}` (mặc định giữ nguyên `update`).
- `docker-compose.yml`: thêm dòng `# - DDL_AUTO=validate` ở dạng
  **comment sẵn**, chưa bật — công tắc để dành, bật khi nào DB có dữ
  liệu thật không muốn bị tự động ALTER nữa.

Xác nhận: local demo và server demo hiện tại không đổi hành vi gì.

---

## 6. Fix #2 — `Team.isActive` bị deactivate mù event

### 6.1. User bẻ lại 1 giả định sai trong lần giải thích đầu

Lần đầu tôi mô tả kịch bản "Team thi 2 event cùng lúc" — user chỉ ra
đúng: hệ thống có constraint DB chặn 2 event chồng ngày
(`existsOverlappingActiveEvent` trong `HackathonEventRepository`,
gọi qua `validateNoOverlappingActiveEvent`), nên 2 event **không thể**
chạy song song thật sự. Phải khảo sát lại và sửa giải thích.

### 6.2. Kịch bản đúng sau khi soát lại

Vòng đời event là **status-driven, không phải date-driven** (đã ghi
nhận từ Sprint 2) — `startDate`/`endDate` không tự đẩy status sang
`COMPLETED`, phải admin bấm tay. Nên vẫn có khe hở: mùa giải mới
(Event B) đã được mở (status khác COMPLETED) trong khi mùa giải cũ
(Event A) đã hết hạn ngày nhưng **chưa ai bấm Complete**. Team có mặt
ở cả 2 (entry tại A và B) sẽ bị deactivate sai khi A cuối cùng cũng
được complete — dù vẫn đang thi sống ở B. Hậu quả cụ thể: field
`Team.isActive` chỉ được đọc ở đúng 1 chỗ
(`TeamRejoinRequestService.requestRejoin`, chặn request khi
`isActive == true`) — team bị deactivate sai sẽ lọt qua guard này,
leader có thể gửi (và coordinator lỡ duyệt) request "rejoin" sang 1
event thứ 3, dù team chưa hề ngừng thi đấu thật.

### 6.3. Fix

Thêm hàm đối xứng với `hasNonCompletedMembership` (vốn chỉ áp dụng
cho User) sang cho Team:

```java
private boolean hasNonCompletedEntryElsewhere(Team team, Integer completedEventId) {
    return teamEventEntryRepository.findAllByTeam_TeamId(team.getTeamId()).stream()
            .map(TeamEventEntry::getEvent)
            .filter(event -> !event.getEventId().equals(completedEventId))
            .anyMatch(event -> !"COMPLETED".equalsIgnoreCase(event.getStatus()));
}
```

Lọc danh sách team trước khi set `isActive=false` trong
`lockCompletedEventParticipantsReadOnly`. `reactivateEventParticipants`
(chiều ngược lại) không cần sửa — set `isActive=true` khi đã `true`
sẵn là no-op, không có rủi ro tương tự.

---

## 7. Test cho kịch bản multi-season

Thêm 2 test vào `HackathonEventServiceTest.java`:

- `completeEvent_shouldDeactivateTeam_whenTeamHasNoEntryInAnotherNonCompletedEvent`
  — team chỉ có 1 season → vẫn bị deactivate như cũ (không phá hành
  vi gốc).
- `completeEvent_shouldKeepTeamActive_whenTeamHasEntryInAnotherNonCompletedEvent`
  — team có thêm entry ở event khác đang `OPEN` → phải **giữ
  active**.

**Verify test thật sự bắt được bug**: tạm bỏ dòng
`.filter(team -> !hasNonCompletedEntryElsewhere(...))` bằng `sed`,
chạy lại → test thứ 2 fail đúng như kỳ vọng (kèm 1 lỗi
`UnnecessaryStubbingException` phụ vì code không còn gọi tới stub —
dấu hiệu xác nhận thêm). Khôi phục lại filter, chạy lại → xanh.

---

## 8. Verify cuối phiên

- `./mvnw -o -q compile`: sạch.
- `./mvnw -o test -Dtest=HackathonEventServiceTest`: **26/26 pass**
  (24 test cũ + 2 test mới), không regression.
- Cập nhật `docs/report/sprint-3-merge-report.md`: đánh dấu finding
  #1 và #2 là **Fixed** kèm mô tả fix thật đã áp dụng (không còn ở
  dạng đề xuất).

---

## 9. File thay đổi trong phiên (chưa commit)

- `back-end/src/seal-api/src/main/java/com/seal/hackathon/service/HackathonEventService.java`
  — thêm `hasNonCompletedEntryElsewhere`, filter team-deactivation.
- `back-end/src/seal-api/src/test/java/com/seal/hackathon/service/HackathonEventServiceTest.java`
  — 2 test case mới + import `assertFalse`/`assertTrue`.
- `back-end/src/seal-api/src/main/resources/application.properties`
  — `ddl-auto` parametrize qua `DDL_AUTO`.
- `docker-compose.yml` — thêm switch `# - DDL_AUTO=validate` (comment
  sẵn).
- `docs/report/sprint-3-merge-report.md` — file mới, báo cáo merge
  đầy đủ + risk findings + trạng thái fix.

## 10. Việc để lại (đã khảo sát, KHÔNG làm trong phiên này)

- Finding #3 (race condition leftover-grouping), #4 (re-run xoá
  override thủ công), #5 (rejoin request trùng), #6 (dead matcher
  SecurityConfig) — đã đưa đề xuất fix cụ thể vào báo cáo, nhưng user
  chủ động dừng lại sau finding #1/#2, đánh giá là fast-follow (tần
  suất thấp, không mất dữ liệu).

Chưa commit bất kỳ thay đổi nào ở trên tại thời điểm ghi log này —
user chưa yêu cầu.
