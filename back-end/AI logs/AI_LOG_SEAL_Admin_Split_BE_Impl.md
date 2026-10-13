# AI Log — SEAL: Đồng bộ seal-api (Backend) với schema tách role System Admin

**Dự án:** SEAL – Software Engineering Hackathon Management System (SU26SWP04)
**Phạm vi phiên:** Sửa code Java `seal-api` cho khớp `seal_schema.sql` mới (đã tách `SYSTEM_ADMIN`), implement đầy đủ Admin API + Coordinator Assignment API theo Postman collection.
**Công cụ:** Claude (Opus 4.8). Build verify bằng Maven (`./mvnw compile`).
**Tiền đề:** Nối tiếp phiên thiết kế trong [`AI_LOG_SEAL_Admin_Split.md`](./AI_LOG_SEAL_Admin_Split.md).

---

## 1. Bối cảnh & vấn đề

Schema (`seal_schema.sql`) đã được sửa sang thiết kế mới: thêm role `SYSTEM_ADMIN`, dời các cột, thêm bảng `SystemLog`. Tuy nhiên phần code Java từng được refactor (commit `f495941`) nhưng **bị revert 2 lần** (`aa37083`, `40a1437`), nên code Java vẫn ở thiết kế CŨ.

→ Hệ quả: entity JPA lệch ≥ 7 cột so với DB. Vì `spring.jpa.hibernate.ddl-auto=none`, app không vỡ lúc khởi động nhưng **vỡ runtime** khi chạm các cột đã bị xóa/đổi.

**Quyết định phạm vi (user chọn):** Làm đầy đủ theo AI Log — đồng bộ entity + tách quyền Admin/Coordinator + dựng feature admin (SystemLog, AdminController) khớp Postman collection.

---

## 2. Phân tích độ lệch (entity Java ↔ schema)

| Bảng | Lệch | Hành động |
|---|---|---|
| `User` | thiếu `judge_type` | Thêm field |
| `UserEventRole` | còn `assigned_at`, `assigned_by` | Xóa |
| `JudgeAssignment` | còn `judge_type`, `assigned_by`, `assigned_at` | Xóa (judge_type lên `User`) |
| `MentorAssignment` | còn `assigned_by`, `assigned_at` | Xóa |
| `HackathonEvent` | còn `created_by` | Xóa |
| `Round` | còn `is_calibration` | Xóa |
| `RoundResult` | còn `advanced` | Xóa (chuyển sang tính toán) |
| `Notification` | còn `related_event_id` | Xóa |
| `SystemLog` | chưa có entity | Tạo mới |

---

## 3. Thay đổi đã thực hiện

### 3.1 Entity khớp schema
- `User` **+** `judgeType` (INTERNAL/GUEST, nullable).
- `UserEventRole` **−** `assignedAt`/`assignedBy` (+ `@PrePersist`). `eventId` NULL = system-wide.
- `JudgeAssignment` **−** `judgeType`/`assignedBy`/`assignedAt`. Giữ `isActive`.
- `MentorAssignment` **−** `assignedBy`/`assignedAt`.
- `HackathonEvent` **−** `createdBy` (tra AuditLog `CREATE_EVENT` nếu cần).
- `Round` **−** `isCalibration`.
- `RoundResult` **−** `advanced`.
- `Notification` **−** `relatedEvent`.
- 2 repository `@Query` đổi `ORDER BY assignedAt` → `ORDER BY id` (cột đã bị xóa).

### 3.2 DTO
- `UserResponse` **+** `judgeType`.
- `UserEventRoleResponse` **−** `assignedAt`/`assignedById`/`assignedByName`.
- `HackathonEventResponse` **−** `createdBy`/`createdByName`.
- `RoundResponse`, `RoundDetailResponse`, `CreateRoundRequest`, `UpdateRoundRequest` **−** `isCalibration`.
- `NotificationResponse` **−** `relatedEventId`/`relatedEventName`.
- `JudgeAssignmentResponse`/`MentorAssignmentResponse` **−** `assignedAt`.
- `RoundResultResponse` **giữ** `advanced` nhưng nay là **giá trị tính toán** (`rank_position <= Round.top_n_advance`), không lưu DB.

### 3.3 Service
- `RoundResultService`: bỏ lưu `advanced`; thêm helper `isAdvanced()` suy từ rank vs `topNAdvance`.
- `HackathonEventService`: bỏ `createdBy`, bỏ phụ thuộc `UserRepository`; `createEvent(request)` (bỏ param coordinatorId).
- `NotificationService`: `createNotification(...)` bỏ param `relatedEventId`, bỏ phụ thuộc `HackathonEventRepository`.
- `RoundService`: bỏ `isCalibration` ở create/update/map.
- `AuthService.mapToUserResponse`: thêm `judgeType`.

### 3.4 Feature Admin (mới)
- **Entity** `SystemLog` + **repository** `SystemLogRepository` (fetch join actor) + **DTO** `SystemLogResponse`.
- **Service** `SystemLogService`: `record(actorId, action, detail)` (ghi best-effort) + `getAllLogs()`.
- **Service** `AdminService`: CRUD user toàn cục, `grantRole`/`revokeRole`, activate/deactivate (uỷ thác `AccountApprovalService`), `getAllRoleGrants`. Mỗi thao tác ghi `SystemLog` (CREATE_USER, GRANT_ROLE, REVOKE_ROLE, ACTIVATE/DEACTIVATE_USER).
- **Controller** `AdminController` `@RequestMapping("/api/admin")` `@PreAuthorize("hasRole('SYSTEM_ADMIN')")`.
- **DTO** `CreateUserRequest` (email/password/fullName/userType/judgeType), `GrantRoleRequest` (userId/roleName/eventId).

### 3.5 Feature Coordinator Assignment (mới)
- `AssignmentService` thêm `assignMentor(...)`, `assignJudge(...)`:
  - Validate luật `is_final`: vòng thường bắt buộc `trackId`, vòng final `trackId` phải null.
  - Track phải cùng event với round.
  - Judge chưa có `judge_type` → mặc định `INTERNAL` (đảm bảo integrity check #5).
  - `ensureRole(...)`: tự cấp `UserEventRole` JUDGE/MENTOR scope theo event để user có quyền truy cập.
- **Controller** `CoordinatorAssignmentController` `/api/coordinator/assignments` `@PreAuthorize("hasRole('EVENT_COORDINATOR')")`.
- **DTO** `AssignJudgeRequest` (judgeUserId/roundId/trackId), `AssignMentorRequest` (mentorUserId/trackId).

### 3.6 Security & dọn dẹp
- `SecurityConfig`:
  - **+** `/api/admin/**` → `hasRole("SYSTEM_ADMIN")`.
  - **+** `/api/coordinator/**` → `hasRole("EVENT_COORDINATOR")`.
  - **−** `/api/users/**`.
- **Xóa** `UserController`, `UserRoleService`, `CreateStaffRequest`, `AssignRoleRequest` (thiết kế cũ, user-management gộp chung với coordinator).

---

## 4. Khớp với Postman Collection

| Endpoint | Method | Quyền | Trạng thái |
|---|---|---|---|
| `/api/admin/users` | GET/POST | SYSTEM_ADMIN | ✅ |
| `/api/admin/users/{id}` | GET | SYSTEM_ADMIN | ✅ |
| `/api/admin/users/{id}/activate`,`/deactivate` | PUT | SYSTEM_ADMIN | ✅ |
| `/api/admin/roles` | GET | SYSTEM_ADMIN | ✅ |
| `/api/admin/roles/grant` | POST | SYSTEM_ADMIN | ✅ |
| `/api/admin/roles/revoke` | DELETE | SYSTEM_ADMIN | ✅ |
| `/api/admin/system-logs` | GET | SYSTEM_ADMIN | ✅ |
| `/api/coordinator/assignments/mentors` | POST | EVENT_COORDINATOR | ✅ |
| `/api/coordinator/assignments/judges` | POST | EVENT_COORDINATOR | ✅ |
| Security: participant→admin 403, coordinator→system-log 403 | | | ✅ (path rule) |

> Mục "Lưu ý" số 4 trong `AI_LOG_SEAL_Admin_Split.md` (BE cần implement đúng path admin/coordinator) → **đã hoàn tất**.

---

## 5. Quyết định thiết kế đáng chú ý

1. **`advanced` không lưu DB** — tính khi map response (`rank ≤ top_n_advance`), đúng tinh thần denormalize tối thiểu của schema.
2. **Tách rõ Admin vs Coordinator:** Admin tạo account + cấp role COORDINATOR; Coordinator gán judge/mentor vào competition. Khi Coordinator gán judge/mentor sẽ tự cấp role JUDGE/MENTOR (UserEventRole) scope theo event để luồng đăng nhập–truy cập liền mạch.
3. **Guest judge:** Admin tạo account (`judgeType=GUEST`), Coordinator assign vào round — khớp phân chia vùng xám trong log thiết kế.
4. **SystemLog best-effort:** ghi log không chặn nghiệp vụ chính nếu actor không tồn tại.

---

## 6. Kiểm thử trong phiên

- `./mvnw -DskipTests compile` → **BUILD SUCCESS**, 0 lỗi.
- Grep toàn repo: không còn tham chiếu tới class/field đã xóa (`UserRoleService`, `CreateStaffRequest`, `AssignRoleRequest`, `isCalibration`, `createdBy`, `advanced`, `relatedEvent`).
- Test source duy nhất (context-load mặc định) không tham chiếu code đã xóa.

---

## 7. Việc cần làm tiếp (TODO cho team)

1. **Chạy lại DB:** `seal_schema.sql` + `seal_seed.sql` để có bảng `SystemLog`, cột `User.judge_type`, role `SYSTEM_ADMIN` và account admin seed — vì `ddl-auto=none` BE không tự tạo.
2. **Test thật bằng Postman** với MySQL local (chưa chạy trong phiên này — mới verify ở mức biên dịch).
3. **FE:** route guard `SYSTEM_ADMIN` (nhận diện qua `event_id === null`), màn System Log, tách user-management khỏi Coordinator dashboard.
4. **(Tùy chọn)** Bổ sung ghi `AuditLog` cho `CREATE_EVENT`, `ASSIGN_JUDGE`, `ASSIGN_MENTOR` nếu cần truy vết người thực hiện (hiện chỉ còn ghi qua log nghiệp vụ).

---

## 8. Artifact thay đổi trong phiên

**Sửa:** 8 entity, 10 DTO, 5 service, 2 controller, 2 repository, `SecurityConfig`.
**Tạo mới:** `SystemLog` (entity/repo/DTO/service), `AdminService` + `AdminController`, `CoordinatorAssignmentController`, 4 DTO request (`CreateUserRequest`, `GrantRoleRequest`, `AssignJudgeRequest`, `AssignMentorRequest`), `SystemLogResponse`.
**Xóa:** `UserController`, `UserRoleService`, `CreateStaffRequest`, `AssignRoleRequest`.
**Tài liệu:** `AI_LOG_SEAL_Admin_Split_BE_Impl.md` (file này).
