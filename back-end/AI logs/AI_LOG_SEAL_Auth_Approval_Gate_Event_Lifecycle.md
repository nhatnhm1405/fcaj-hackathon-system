# AI Log — SEAL: Sửa luồng duyệt (approval) sau OAuth & siết logic Event lifecycle về Backend

**Dự án:** SEAL – Software Engineering Hackathon Management System (SU26SWP04)
**Ngày:** 2026-06-15
**Phạm vi phiên:** Vá các bug của luồng "đăng nhập nhưng chưa duyệt" (phát sinh từ OAuth complete-profile), xử lý lỗi create-team không thấy event (do seed), và **chuyển logic vòng đời Event từ Frontend xuống Backend** (validate status/transition/ngày). Kèm sửa lại seed cho khớp model.
**Công cụ:** Claude (Opus 4.8). Verify: BE `./mvnw -o compile`, FE `tsc --noEmit --ignoreDeprecations 6.0`.
**Tiền đề:** Nối tiếp [`AI_LOG_SEAL_OAuth2_Complete_Profile.md`](./AI_LOG_SEAL_OAuth2_Complete_Profile.md).

> ⚙️ **[BE]** = Backend (`seal-api`), **[FE]** = Frontend (`seal-web`).

---

## 1. [FE] Sửa luồng "đã đăng nhập nhưng chưa duyệt"

### Triệu chứng (user báo)
- Complete-profile xong **không hiện** màn `/pending-approval`.
- Đăng nhập OAuth lại (đã hoàn tất hồ sơ, chưa duyệt) **cũng không** hiện pending.
- `/pending-approval` **không có cách logout thật** (chỉ "Back to Home" → `/`, token còn nguyên).

### Nguyên nhân
| Bug | Gốc rễ |
|-----|--------|
| Complete xong không hiện pending | **Race**: `logout()` set `currentUser=null` → `RequireAuth` (đang bọc `/complete-profile`) bắn về `/login` **trước** khi `navigate('/pending-approval')` kịp chạy |
| Re-login không hiện pending | BE redirect `/login?error=ACCOUNT_NOT_APPROVED` nhưng `LoginPage` **không đọc** param `error` |
| Không logout thật | `PendingApprovalPage` chỉ có nút về `/`, không xóa token |

### Khái niệm làm rõ
"**Đã đăng nhập**" (có token, `currentUser`) ≠ "**đã được duyệt**". Pending gate chặn theo **approval**, không theo authentication. Token vẫn thật ⇒ cần logout thật.

### Sửa
| File | Thay đổi |
|------|----------|
| `app/providers/AuthProvider.tsx` | `AuthUser` **+** `approved`; `ApiUserProfile` **+** `isApproved/is_approved`; `mapApiUser` set `approved = profile.isApproved ?? true` |
| `app/routes/index.tsx` | **Approval gate** trong `RequireAuth`: đã auth nhưng `!approved` (và hồ sơ đã hoàn tất) → `Navigate /pending-approval` |
| `features/auth/CompleteProfilePage.tsx` | Bỏ `logout()` (gây race) → `patchCurrentUser({ profile_incomplete:false })` + `navigate('/pending-approval')` |
| `features/auth/LoginPage.tsx` | Đọc `?error`: `ACCOUNT_NOT_APPROVED` → `/pending-approval`; `ACCOUNT_INACTIVE` → modal inactive; khác → lỗi chung |
| `features/auth/PendingApprovalPage.tsx` | Đang auth → nút **"SIGN OUT"** (gọi `logout()` thật) → `/login`; chưa auth → "BACK TO HOME" |

**Kết quả:** mọi trạng thái "kẹt" (incomplete / chưa duyệt) đều có lối thoát thật, và user chưa duyệt không lọt vào dashboard (gate chặn dù refresh/gõ URL).

---

## 2. Lỗi create-team "không thấy event nào" — do dữ liệu seed

`GET /api/teams/active-events` chỉ trả event **status="OPEN"**. Seed cũ: Spring=COMPLETED, Summer=IN_PROGRESS, Fall=DRAFT ⇒ **không event nào OPEN** ⇒ rỗng. Ngoài ra registration window không phủ "hôm nay" (15/06/2026).

→ Sửa `seal_seed.sql`: đặt lại mốc mùa, **Summer = OPEN** với cửa sổ đăng ký phủ ngày hiện tại để test create-team chạy được (xem mục 4 — sau khi siết validation thì căn lại cho nhất quán).

---

## 3. 🔧 [BE] Chuyển logic vòng đời Event xuống Backend

**Nguyên tắc (user chốt):** business rule phải ở **Backend** — FE guard chỉ là UX, bypass được. Trước đây vòng đời event chỉ enforce ở FE (`CoordEventsPage.nextStatusActions`).

### Audit
- ✅ `createTeam` gate đúng: `status=OPEN` **và** `now ∈ [registrationStart, registrationEnd]`.
- ⚠️ `createEvent`/`updateEvent`: status **không** validate enum; **không** enforce transition; `updateEvent` **không** validate thứ tự ngày; `active-events` bỏ qua window.

### Sửa (`HackathonEventService` + `TeamService`)
| # | Thay đổi | File |
|---|----------|------|
| 1 | Validate **status ∈ enum** {DRAFT, OPEN, IN_PROGRESS, COMPLETED, CANCELLED} — create & update | `HackathonEventService` |
| 2 | Validate **thứ tự ngày** `regStart ≤ regEnd ≤ startDate ≤ endDate` (đăng ký đóng trước thi) — create & update | `HackathonEventService` |
| 3 | Enforce **transition lifecycle** (map): DRAFT→{OPEN,CANCELLED}, OPEN→{IN_PROGRESS,CANCELLED}, IN_PROGRESS→{COMPLETED,CANCELLED}, COMPLETED→{}, CANCELLED→{DRAFT}. Mirror y hệt FE nhưng BE là nguồn chân lý | `HackathonEventService` |
| 4 | `getActiveEventsWithTracks` lọc thêm **registration window** (chỉ OPEN + now trong window) | `TeamService` |

Helper mới: `requireValidStatus`, `requireValidTransition`, `requireValidDates`. Giờ gọi thẳng API với status rác / transition bậy đều bị 400.

---

## 4. Hệ quả: sửa lại seed cho khớp validation

Validation #2 (`registrationEnd ≤ startDate`) khiến seed ở mục 2 **mâu thuẫn** (start_date để đầu mùa, trùng/trước registrationEnd) ⇒ nếu Coordinator update event đó qua API sẽ bị chặn. Đã căn lại: **competition = 2 ngày cuối mùa** (đúng "cuộc thi chỉ 2 ngày" theo nghiệp vụ).

| Event | Registration | Competition (start–end) | Status |
|-------|--------------|--------------------------|--------|
| Spring 2026 | 15/01 – 28/02 | **14–15/04** | COMPLETED |
| Summer 2026 | 15/05 – 30/06 *(phủ 15/06 ✓)* | **14–15/08** | OPEN |
| Fall 2026 | 15/09 – 31/10 | **14–15/12** | DRAFT |

→ Tất cả thỏa `regStart ≤ regEnd ≤ startDate ≤ endDate`. Summer OPEN + window phủ hôm nay ⇒ create-team chạy.

**Nạp lại DB đang chạy (ddl-auto=none):**
```sql
UPDATE HackathonEvent SET registration_start='2026-01-15 00:00:00', registration_end='2026-02-28 23:59:59', start_date='2026-04-14 08:00:00', end_date='2026-04-15 23:59:59', status='COMPLETED' WHERE event_id=1;
UPDATE HackathonEvent SET registration_start='2026-05-15 00:00:00', registration_end='2026-06-30 23:59:59', start_date='2026-08-14 08:00:00', end_date='2026-08-15 23:59:59', status='OPEN' WHERE event_id=2;
UPDATE HackathonEvent SET registration_start='2026-09-15 00:00:00', registration_end='2026-10-31 23:59:59', start_date='2026-12-14 08:00:00', end_date='2026-12-15 23:59:59', status='DRAFT' WHERE event_id=3;
```

---

## 5. Verify
| | Kết quả |
|---|---------|
| BE `mvnw compile` | ✅ EXIT 0 |
| FE `tsc` | ✅ EXIT 0 |

> Chưa test live — cần reseed/UPDATE DB rồi đi lại luồng OAuth + create-team.

---

## 6. Còn lại / DEFER
- **Status không tự suy theo ngày**: vẫn do Coordinator bấm tay (DRAFT→OPEN→IN_PROGRESS→COMPLETED). Muốn tự nhảy theo mốc thời gian cần **scheduler** — việc về sau (schema đã đủ field).
- **Round-trong-Event chưa validate**: round của Summer trong seed có thể có ngày nằm ngoài cửa sổ thi 2 ngày (14–15/08). Không gây lỗi (không có ràng buộc), nhưng nên căn lại ngày round/criteria cho nhất quán.
- **Comment header `seal_seed.sql`** (dòng 18–20) vẫn ghi "Summer → IN_PROGRESS (Jun–Aug)" — lệch với data thật (giờ OPEN, thi 14–15/08); nên cập nhật comment.
- Các DEFER từ phiên trước (forgot/change-password, mentor/judge scoring guards...) vẫn còn.

---

## 7. Tổng kết file đụng tới
**[FE] sửa:** `app/providers/AuthProvider.tsx`, `app/routes/index.tsx`, `features/auth/CompleteProfilePage.tsx`, `features/auth/LoginPage.tsx`, `features/auth/PendingApprovalPage.tsx`.
**[BE] sửa:** `service/HackathonEventService.java`, `service/TeamService.java`.
**Data:** `database scripts/seal_seed.sql`.

---

*Log sinh bởi Claude (Opus 4.8) — phiên 2026-06-15.*
