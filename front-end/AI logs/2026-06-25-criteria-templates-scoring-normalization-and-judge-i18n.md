# AI Log — Seed 4 đội · Fix revoke role · Criteria Template · Chuẩn hoá điểm 0–100 · i18n màn hình Judge

**Ngày:** 2026-06-25
**Phạm vi:** Full-stack — DB seed (`back-end/database scripts`), Backend Spring Boot (`back-end/src/seal-api`), Frontend React (`front-end/src/seal-web`)
**Nhánh:** `KhanhNLH-logo-return-to-home`
**Trạng thái:** Hoàn tất các thay đổi code; đã verify ở mức **compile** — Backend `./mvnw -o -q compile` → exit 0; Frontend `tsc --noEmit` → exit 0. (Chưa chạy unit test/integration trong phiên này.)

> Tài liệu ghi lại toàn bộ việc đã làm trong phiên, giải thích **làm gì / vì sao / làm thế nào**, kèm file và cách kiểm chứng. Một số mục là **chẩn đoán + hướng dẫn vận hành** (không sửa code) được đánh dấu rõ.

---

## Bối cảnh nhanh (các quy ước liên quan)

- **Tiêu chí chấm điểm gắn theo ROUND** (không theo event/submission). Mọi đội trong cùng round chấm trên cùng bộ tiêu chí. `ScoringCriteria` có `event_id`, `round_id`, `template_id`, `weight`, `max_score`, `order_number`.
- **Template tiêu chí** = `ScoringCriteriaTemplate` (header) + các `ScoringCriteria` "template-only" (`event_id = NULL AND round_id = NULL`, `template_id` trỏ về template).
- **Vòng đời event:** `DRAFT → OPEN → SETUP → IN_PROGRESS → COMPLETED` (+`CANCELLED`). Khi **vào SETUP**, BE `computeTrackCapacities()` đóng băng roster (đội APPROVED) và tính `capacity` mỗi track.
- **DB nguồn thật:** `back-end/database scripts/**` (xem [[db-source-of-truth]]). `seal_schema.sql` = schema, `seal_seed.sql` = seed đầy đủ, các `migration_*.sql` = áp lên DB đang chạy.
- Mật khẩu test chung của mọi seed user: `Test@1234` (BCrypt cost 10).

---

## TASK 1 — Seed thêm 4 đội cho Event 2 (Summer 2026)

### Làm gì
Thêm 4 đội mới (`team 14–17`) vào Event 2 với 16 user mới (`user 28–43`), mỗi đội 3–5 thành viên (1 LEADER + MEMBER), track_id = NULL (theo pattern Summer — chờ self-select/draw).

| Đội | Thành viên | Status |
|-----|-----------|--------|
| Team Aurora (14) | 5 | APPROVED |
| Team Specter (15) | 4 | APPROVED |
| Team Quantum (16) | 3 | PENDING (để demo duyệt đội) |
| Team Zephyr (17) | 4 | APPROVED |

### Vì sao / quyết định
- Người dùng chọn: Event 2 (đang OPEN), **tạo user mới** làm thành viên (vì user approved sẵn trong Event 2 gần như đã hết).
- Theo yêu cầu: đội 3–5 thành viên; Event 2 đăng ký không kèm track.

### Cách làm & diễn biến
1. Ban đầu tạo file rời `seal_seed_4teams.sql`.
2. Người dùng muốn gộp vào `seal_seed.sql` để seed-từ-đầu là đủ → đã gộp (user 28–43 multi-line đúng style; team 14–17 nối vào block Event 2; thêm block TeamMember; cập nhật comment khoảng ID `user 1-43`, `team 1-17`). Xoá file rời.
3. Sau đó người dùng muốn **giữ cả file rời** để top-up nhanh DB đang chạy → tạo lại `seal_seed_4teams.sql`.
4. Người dùng gặp `Error 1062 Duplicate entry '28'` khi chạy add-on trên DB đã có sẵn dữ liệu → đổi 3 lệnh `INSERT` của add-on thành **`INSERT IGNORE`** (idempotent, chạy lại an toàn, đúng kiểu `seal_seed.sql` xử lý bảng `Role`).

### Files
| File | Thay đổi |
|------|----------|
| `back-end/database scripts/seal_seed.sql` | +16 user (28–43), +4 team (14–17), +block TeamMember; cập nhật comment ID. |
| `back-end/database scripts/seal_seed_4teams.sql` | **Mới** — add-on idempotent (`INSERT IGNORE`) cho DB đang chạy bản 13-đội. |

### Lưu ý
- Hai file cho **cùng kết quả**; **không dùng đồng thời** trên cùng DB (cùng ID tường minh). Fresh seed → chỉ `seal_seed.sql`. Top-up DB cũ → `seal_seed_4teams.sql`.

---

## TASK 2 — Fix bug Revoke Role (báo thành công nhưng không xoá)

### Triệu chứng
Màn hình Admin → revoke role: toast "ROLE REVOKED" nhưng role vẫn còn sau refetch.

### Nguyên nhân (JPA cascade)
`User.userEventRoles` map `@OneToMany(mappedBy="user", cascade = CascadeType.ALL)`. Trong `AdminService.revokeRole`, `user` được load **kèm** collection roles (`findByIdWithRoles`), rồi `deleteAll(matches)` đánh dấu xoá; nhưng collection trên `user` **vẫn tham chiếu** các entity đó → khi flush, cascade ALL re-persist lại → lệnh xoá bị triệt tiêu → HTTP 200 nhưng DB không đổi. (FE/BE đều đúng; lỗi nằm ở tầng persistence.)

### Fix
Đồng bộ collection của parent trước khi xoá:
```java
user.getUserEventRoles().removeAll(matches);
userEventRoleRepository.deleteAll(matches);
```

### Files
| File | Thay đổi |
|------|----------|
| `back-end/.../service/AdminService.java` | Thêm `removeAll(matches)` trước `deleteAll` trong `revokeRole`, kèm comment giải thích cascade. |

### Verify / lưu ý
- Cần **rebuild + restart** backend (Spring không hot-reload). Đường `grantRole` không dính bug này. Đây là bẫy JPA điển hình của `@OneToMany` 2 chiều + `cascade=ALL`.

---

## TASK 3 — (Chẩn đoán, không sửa code) "Not enough track capacity" khi draw track

### Kết luận
Không phải bug. `computeTrackCapacities` đóng băng **tổng capacity = số đội APPROVED tại thời điểm vào SETUP**. Vì TASK 1 thêm 3 đội APPROVED **sau** khi Event 2 đã vào SETUP nên tổng capacity cũ < số đội APPROVED → `TeamService.drawTracks` ném lỗi.

### Hướng xử lý (vận hành)
- **Cách A (đúng luồng):** `PUT /api/events/2` status `SETUP→OPEN` rồi `OPEN→SETUP` → `computeTrackCapacities` tính lại theo số đội mới; rồi draw (redraw `?includeAssigned=true`).
- **Cách B (SQL nhanh):** `UPDATE Track SET capacity = CEIL(@approved/@n) WHERE event_id = 2;`

---

## TASK 4 — (Chẩn đoán, không sửa code) Lỗi `Unknown column 'announcement_id'` khi approve team

### Kết luận
DB dựng từ `seal_schema.sql` (bảng `Notification` **không** có `announcement_id`) nhưng **chưa chạy** `migration_announcement.sql` (file này mới `CREATE Announcement` + `ALTER Notification ADD announcement_id`). Entity JPA `Notification` có map cột đó → insert notification (APPROVAL) khi approve team thất bại.

### Hướng xử lý
Chạy `mysql ... < "back-end/database scripts/migration_announcement.sql"`. Lưu ý: 2 lệnh `ALTER Announcement ADD audience/link_url` ở cuối file sẽ báo "Duplicate column" trên DB mới (vì `CREATE` đã có sẵn) — **vô hại**; lệnh quan trọng `ADD announcement_id` chạy trước.

---

## TASK 5 — Tính năng Criteria Template (đầy đủ: áp + lưu template)

### Làm gì
Cho phép Coordinator ở tab CRITERIA:
1. **Chọn template có sẵn → APPLY** đổ tiêu chí vào round.
2. **SAVE AS TEMPLATE** — lưu bộ tiêu chí của round hiện tại thành template mới tái dùng.

### Bối cảnh trước khi làm
Đã có entity + repo `ScoringCriteriaTemplate` nhưng **chưa có endpoint nào**; template 1 trong seed chỉ có "tên", **chưa có item** (các criteria event-1 tuy `template_id=1` nhưng có `event_id/round_id` nên là tiêu chí thật của event, không phải item template).

### Backend
| File | Thay đổi |
|------|----------|
| `dto/response/ScoringCriteriaTemplateResponse.java` | **Mới** — `templateId, name, description, isDefault, items[]`. |
| `dto/request/CreateTemplateRequest.java` | **Mới** — `name (NotBlank), description`. |
| `repository/ScoringCriteriaRepository.java` | +`findAllByTemplate_TemplateIdAndEventIsNullAndRoundIsNullOrderByOrderNumber` (lấy item template). |
| `service/ScoringService.java` | +`listTemplates()`, +`applyTemplate()`, +`createTemplateFromRound()`, +helper `mapTemplateToResponse()`. |
| `controller/ScoringController.java` | +`GET /api/criteria-templates`, +`POST /api/events/{e}/rounds/{r}/criteria/apply-template/{templateId}`, +`POST /api/events/{e}/rounds/{r}/criteria/save-as-template` (đều `EVENT_COORDINATOR`). |

### DB
| File | Thay đổi |
|------|----------|
| `seal_seed.sql` | Mục 9: thêm template 2 `Pitch & Demo Day`; mục **10b mới**: các item template-only (event/round NULL) cho template 1 (5 item) và 2 (4 item), mô tả tiếng Anh. |
| `migration_criteria_template.sql` | **Mới** — idempotent: thêm template 2 (`INSERT ... FROM dual WHERE NOT EXISTS`), `UPDATE` text template 1 sang tiếng Anh, `DELETE` rồi `INSERT` lại các item template-only (an toàn vì item template-only không bị chấm). |

### Frontend
| File | Thay đổi |
|------|----------|
| `features/events/CoordEventsPage.tsx` | +type `CriteriaTemplate`; +state (`templates`, `selectedTemplateId`, `templateBusy`, `savingTemplate`, `newTemplateName`); +useEffect load `GET /api/criteria-templates`; +handler `applyTemplate()`/`saveAsTemplate()`; +thanh UI TEMPLATE (dropdown + APPLY + SAVE AS TEMPLATE) trong tab CRITERIA. |

### Diễn biến tinh chỉnh (theo phản hồi người dùng)
1. **Bug "(0) / has no criteria":** do DB người dùng chưa chạy migration nên template không có item → đã hướng dẫn chạy `migration_criteria_template.sql` (giải thích phân biệt "item template" vs "tiêu chí event").
2. **Thảo luận thiết kế:** xác nhận template là global, tiêu chí theo round, không có giới hạn số tiêu chí (đối chiếu `docs/documents/ProjectRequirements.md` — xem TASK 7).
3. **Thêm Preview rồi GỠ:** từng thêm khối preview tiêu chí khi chọn template, nhưng người dùng thấy **trùng/rối** → đã gỡ hẳn (revert type, biến `selectedTemplate`, block JSX).
4. **Đổi APPLY: append → REPLACE:** ban đầu APPLY cộng dồn (apply T1 rồi T2 ra cả 9 dòng). Người dùng muốn không tích lũy → đổi `applyTemplate` thành **thay thế**: xoá các tiêu chí **chưa bị chấm** của round rồi nạp template; tiêu chí **đã có điểm** thì giữ và bỏ qua item template trùng tên.
5. **Căn cột:** các ô MAX / W / EDIT / DELETE đặt trong cell **bề rộng cố định** (84/64/64/88px) để thẳng hàng giữa các dòng.

---

## TASK 6 — Chuẩn hoá điểm tổng sang thang 0–100 (trung bình có trọng số)

### Vì sao
Công thức cũ ở `RoundResultService.finalizeRound` là **tổng có trọng số** `Σ(value×weight)` (rồi trung bình theo judge) → độ lớn phụ thuộc số tiêu chí (5 tiêu chí tối đa 50, 10 tiêu chí tối đa 100). Trong cùng round vẫn công bằng (mọi đội cùng bộ tiêu chí), nhưng **so sánh chéo round** và **đọc điểm** dễ nhầm. Người dùng chọn chuẩn hoá 0–100.

### Công thức mới
```
Mỗi judge:  judgeScore = 100 × Σ(weight × value/maxScore) / Σ(weight)   // 0–100
totalScore = trung bình judgeScore qua các judge
```
Chia cho `Σ(weight)` và `maxScore` từng tiêu chí → **độc lập số lượng tiêu chí**. Cột `RoundResult.total_score` là `DECIMAL(7,2)` nên chứa 0–100 thoải mái, **không đổi schema**.

### Files
| File | Thay đổi |
|------|----------|
| `back-end/.../service/RoundResultService.java` | Đổi block tính điểm/team trong `finalizeRound` sang trung bình có trọng số 0–100; gỡ 1 query thừa `criteriaList`. |

### Verify / lưu ý
- FE hiển thị `Number(totalScore).toFixed(1)` không hardcode mẫu số → tự hiện 0–100, **không cần sửa FE**.
- Round **đã FINALIZED** giữ điểm thang cũ; muốn áp thang mới phải đưa round về `ACTIVE` rồi finalize lại (finalize chặn round đang `FINALIZED`).

---

## TASK 7 — (Tra cứu) Đối chiếu yêu cầu về số tiêu chí

Kiểm tra `docs/documents/ProjectRequirements.md`: **không có** ràng buộc "tối đa 5 tiêu chí". Mục 6.2 cho phép ban tổ chức **thêm/xóa/điều chỉnh** tiêu chí tự do + đặt trọng số; ví dụ liệt kê **6** tiêu chí. Con số "3–5" trong đề là **số thành viên đội** (dòng 97/157), không phải tiêu chí. → Đúng spec là **không giới hạn** số tiêu chí (Phương án A), không cần thêm ràng buộc.

---

## TASK 8 — i18n màn hình Judge + đổi icon nút AI Assist

### Làm gì
1. Dịch các chuỗi tiếng Việt **hiển thị** trên màn hình Judge sang tiếng Anh.
2. Bỏ sticker ngôi sao ✨ ở mục AI Assistant.
3. Thêm icon vector (không phải sao vàng) cho nút AI ASSIST, làm nổi bật hơn.

### Nguồn tiếng Việt & cách xử lý
- **Frontend** `JudgeScoringPage.tsx`: "Đang phân tích bài nộp… (vài giây)" → "Analyzing submission… (a few seconds)".
- **Backend** `AiJudgeAssistantService.java` (nơi sinh phần lớn text hiển thị): dịch `DISCLAIMER`, lỗi cấu hình (`...is not configured. Set GEMINI_API_KEY...`), lỗi thiếu dữ liệu, và **toàn bộ message lỗi Gemini** (503/429/404/key sai...). Quan trọng: **prompt** gửi Gemini đổi sang tiếng Anh + yêu cầu *"Write all text values in English"* → AI trả kết quả tiếng Anh, không còn ra tiếng Việt.

### Bỏ sao + icon
- Bỏ ✨ ở nút `AI ASSIST` và tiêu đề `AI JUDGE ASSISTANT`.
- Thêm icon **`Bot`** (lucide-react — đã có sẵn trong dự án) cho trạng thái mặc định, **`RefreshCw`** cho trạng thái chạy lại (thay ký tự ↻). Icon kế thừa màu trắng của nút `variant="cyber"` (gradient xanh + glow) → **không vàng**, nổi bật.

### Files
| File | Thay đổi |
|------|----------|
| `front-end/.../features/scoring/JudgeScoringPage.tsx` | Dịch loading text; bỏ ✨ ở nút + tiêu đề; +import `{ Bot, RefreshCw } from "lucide-react"`; render icon trong nút AI ASSIST. |
| `back-end/.../service/AiJudgeAssistantService.java` | Dịch disclaimer + mọi message lỗi + prompt sang tiếng Anh (yêu cầu output tiếng Anh). |

### Verify / lưu ý
- Quét lại: **không còn ký tự tiếng Việt hay ✨** trong 2 file.
- AI Assist vẫn cần tự đặt `GEMINI_API_KEY` trong `.env` backend mới chạy thực (đây là key cá nhân — chỉ dịch thông báo, không cấu hình hộ).

---

## Tổng hợp file thay đổi trong phiên

**Backend / DB**
- `back-end/database scripts/seal_seed.sql` — seed 4 đội + template 2 + item template.
- `back-end/database scripts/seal_seed_4teams.sql` *(mới)* — add-on idempotent 4 đội.
- `back-end/database scripts/migration_criteria_template.sql` *(mới)* — nạp template/item cho DB đang chạy.
- `back-end/.../service/AdminService.java` — fix revoke role (cascade).
- `back-end/.../dto/response/ScoringCriteriaTemplateResponse.java` *(mới)*, `dto/request/CreateTemplateRequest.java` *(mới)*.
- `back-end/.../repository/ScoringCriteriaRepository.java` — finder item template.
- `back-end/.../service/ScoringService.java` — list/apply(replace)/save template.
- `back-end/.../controller/ScoringController.java` — 3 endpoint template.
- `back-end/.../service/RoundResultService.java` — chuẩn hoá điểm 0–100.
- `back-end/.../service/AiJudgeAssistantService.java` — i18n EN + prompt EN.

**Frontend**
- `front-end/.../features/events/CoordEventsPage.tsx` — UI criteria template + căn cột.
- `front-end/.../features/scoring/JudgeScoringPage.tsx` — i18n + bỏ ✨ + icon `Bot`/`RefreshCw`.

## Việc cần làm sau (để các thay đổi có hiệu lực)
1. **Rebuild + restart backend** (revoke fix, endpoint template, scoring 0–100, i18n AI).
2. **Refresh/rebuild frontend**.
3. Chạy `migration_announcement.sql` (nếu DB chưa có `Notification.announcement_id`) và `migration_criteria_template.sql` (để template có item) — hoặc seed lại từ `seal_seed.sql`.
4. Đặt `GEMINI_API_KEY` trong `.env` backend nếu muốn dùng AI Assist.
5. (Tuỳ chọn) commit gộp toàn bộ thay đổi phiên này.
