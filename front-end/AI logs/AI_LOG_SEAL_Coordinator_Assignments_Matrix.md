# AI Log — SEAL: Redesign trang phân công Coordinator thành ma trận (Notion-style) Mentor × Judge

**Dự án:** SEAL – Software Engineering Hackathon Management System (SU26SWP04)
**Ngày:** 2026-06-15
**Phạm vi phiên:** Làm lại UI trang phân công của Event Coordinator — từ danh sách dọc rối mắt (nhiều round × track) sang **bảng ma trận kiểu Notion** (dọc = Track, ngang = Round, ô = thêm/chọn người phụ trách). Bổ sung backend còn thiếu cho mentor (roster + remove). **Bỏ "Create Guest Account"** khỏi coordinator (đúng vai trò Admin). Nhiều vòng tinh chỉnh màu cho hợp theme.
**Công cụ:** Claude (Opus 4.8).
**Verify:** FE `tsc --noEmit -p tsconfig.app.json --ignoreDeprecations 6.0` + `npm run build`; BE `./mvnw -o clean compile` (BUILD SUCCESS).
**Cách làm:** Q&A liên tục với user; **đối chiếu `docs/documents/ProjectRequirements.md`** trước khi code.

> ⚙️ **Quy ước:** **[BE]** = backend (`seal-api`), **[FE]** = frontend (`seal-web`).

---

## 1. Bối cảnh & yêu cầu

Trang `/coordinator/judges` (cũ tên "Judges & Mentors") chỉ render **một card/round** kèm form "Assign Judge" → khi event có nhiều round × track thì danh sách dọc rất dài, "rối và đau mắt". Mentor **chưa có UI** ở coordinator. Có nút **Create Guest Account** mà user cho là thừa (việc của Admin).

**User yêu cầu:** bảng ma trận kiểu Notion — dọc = Track, ngang = Round, mỗi ô thêm/chọn người phụ trách; bỏ Create Guest Account; **luôn Q&A** để nắm nghiệp vụ.

## 2. Đối chiếu ProjectRequirements.md (xác nhận nghiệp vụ)

| Vai trò | Phân công theo | Nguồn |
|---|---|---|
| **Mentor** | **TRACK / hạng mục** (cả event), hỗ trợ mọi team trong track | §4.3, §6.3 (dòng 151), UC05 |
| **Judge** | **ROUND + TRACK**; vòng **Final** = tất cả track (`trackId=null`) | §4.4 (dòng 68), UC09 |

- **Điểm chỉnh:** user nói "mentor team nào" nhưng requirements là **mentor theo HẠNG MỤC (track)**, không theo từng team → khớp backend sẵn có.
- §6.3 (dòng 152): **một người có thể là Mentor ở hạng mục này và Judge ở hạng mục khác** trong cùng event → dùng chung pool, cho phép trùng người khác track.
- §4.6: **Admin** tạo/quản lý tài khoản; **Coordinator** chỉ *duyệt tài khoản* + *phân công* ⇒ **bỏ Create Guest Account khỏi coordinator là đúng requirements**.

➡️ Kết luận: matrix (dọc=Track; cột MENTORS track-level; cột round thường = ô judges theo track×round; **Final tách 1 hàng all-tracks**) khớp 100% requirements. Đã vẽ ASCII mockup cho user duyệt trước khi code.

## 3. [BE] Bổ sung mentor roster (phần còn thiếu)

Backend trước chỉ có `POST /mentors` (assign) — **thiếu** GET roster + DELETE. Đã thêm:
- DTO mới `MentorRosterItemResponse` (id, mentorUserId, mentorName, trackId, trackName).
- `MentorAssignmentRepository.findActiveByEvent(eventId)` (JOIN FETCH track + mentor).
- `AssignmentService`: `listMentorAssignmentsByEvent(eventId)` + `removeMentorAssignment(id)` (hard delete, theo đúng pattern judge).
- `CoordinatorAssignmentController`: **`GET /api/coordinator/assignments/mentors?eventId=`** + **`DELETE /api/coordinator/assignments/mentors/{id}`**.

> Không đổi schema/DB — bảng `MentorAssignment` đã tồn tại. **Không cần migration.**

## 4. [FE] apiClient

- Type `MentorRosterItem` + `AssignMentorPayload`.
- `coordinatorApi`: thêm `getMentorRoster(eventId)`, `assignMentor(payload)`, `removeMentorAssignment(id)` (judge đã có sẵn từ trước).

## 5. [FE] Trang Assignments (ma trận)

Viết lại `features/scoring/CoordJudgesPage.tsx` (giữ tên export để route `/coordinator/judges` không đổi; đổi tiêu đề + nav → **"Assignments"** trong `DashboardLayout`).

**Cấu trúc:**
- CSS grid: cột 1 = **TRACK** (kèm **số team mỗi track**, vd "2 teams"), cột 2 = **MENTORS** (track-level), tiếp theo là **một cột / round thường**.
- Mỗi ô: chip `⬡ Tên [INT/GUEST] ✕` (gỡ ngay) + nút **`+ add`** → **popover search staff** ngay tại ô (thêm nhiều người, đánh dấu `✓ added`, click nền ngoài để đóng).
- **Hàng FINAL** riêng full-width (judge chấm tất cả track → `assignJudge(trackId=null)`).
- Map API: `eventsApi.getAll`, `roundsApi.getAll`, `tracksApi.getAll`, `teamsApi.getByEvent` (đếm team/track), `coordinatorApi.getJudgeRoster` + `getMentorRoster`. Thao tác: `assignMentor`/`assignJudge`/`removeMentorAssignment`/`removeJudgeAssignment` rồi reload roster tương ứng.
- 1 người được phép ở nhiều ô (mentor track A + judge track B) — đúng §6.3.

**Bỏ Create Guest Account:** gỡ toàn bộ state/form/nút guest khỏi FE. Endpoint BE `createGuestJudge` **để nguyên** (chưa xóa — chờ user xác nhận).

## 6. Tinh chỉnh màu (nhiều vòng theo phản hồi user)

| Phản hồi | Xử lý |
|---|---|
| Nền bảng tối, bị "chìm" | Thử nền sáng hơn (translucent-white elevated) |
| Vẫn xấu/tối, muốn sáng & không trong suốt | Đổi sang bảng **solid sáng** kiểu Notion (chữ tối / nền trắng) + **phóng to** kích thước (cột 240px, ô cao 84px, font 13px) |
| Chữ chìm; header xám xấu; final vàng nhạt | Header **xanh lá đặc** + chữ trắng; track column xanh nhạt; final **vàng đậm** hơn |
| **Sáng chói, không hợp theme web** | **Quay về theme dark cyber:** nền tối `C.surface*`, header xanh-tint + chữ `C.green`, viền gradient xanh→blue ở mép, chip cyber xanh, final amber nhẹ, popover tối — tất cả dùng token `C.*` (tự đồng bộ light/dark) |
| Cột mentor **tím xấu** | Đổi mentor sang **xanh dương** `#60a5fa` (`rgba(59,130,246,…)`) — accent có sẵn theme, phân biệt với judge xanh lá |

**Chốt:** bảng nền tối elevated đúng theme cyber; header xanh lá, cột mentor xanh dương, track có vạch xanh trái + số team cyan, final amber nhẹ, chip xanh phát sáng.

## 7. Tổng hợp file

**[BE] Tạo:** `dto/response/MentorRosterItemResponse.java`.
**[BE] Sửa:** `repository/MentorAssignmentRepository.java` (+`findActiveByEvent`), `service/AssignmentService.java` (+`listMentorAssignmentsByEvent`, `removeMentorAssignment`), `controller/CoordinatorAssignmentController.java` (+GET/DELETE mentors).
**[FE] Sửa:** `shared/apiClient.ts` (+mentor methods/types), `features/scoring/CoordJudgesPage.tsx` (**viết lại** thành matrix, bỏ guest account), `app/layouts/DashboardLayout.tsx` (đổi nhãn nav + title → "Assignments").

**Endpoint mới:** `GET /api/coordinator/assignments/mentors?eventId=`, `DELETE /api/coordinator/assignments/mentors/{id}`.

## 8. Việc còn lại / lưu ý vận hành

- **Khởi động lại backend** để có 2 endpoint mentor mới. **Không cần migration.**
- **Câu hỏi treo (chưa chốt):** có **xóa luôn endpoint BE `createGuestJudge`** (`POST /api/coordinator/guest-judges`) hay giữ lại? Hiện chỉ mới gỡ UI.
- Test bằng `coordinator@fpt.edu.vn` / `Test@1234`, chọn event có track + round (vd SEAL Summer 2026).
