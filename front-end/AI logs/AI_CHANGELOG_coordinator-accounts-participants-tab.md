# AI Changelog — Coordinator Accounts: Tab Participants (kèm cột Team)

> **Branch:** `main`
> **Ngày:** 2026-06-10
> **Phạm vi:** 1 file · màn hình Coordinator → tab Accounts
> **Công cụ:** Claude Code (AI assistant)

Tài liệu này ghi lại việc thêm **tab "Participants"** vào trang Accounts của Coordinator — bảng riêng chỉ liệt kê các account là **participant** (không phải staff), kèm cột **Team** cho biết họ đang thuộc team nào (hoặc `—` nếu chưa vào team nào).

---

## 1. Yêu cầu

Trang Accounts hiện có các tab: `All (27)` · `Pending (3)` · `Approved (24)` · `Rejected (0)` · `Staff Roles`.

Cần thêm một bảng mới — **PARTICIPANT** — với 2 yêu cầu:
1. Chỉ liệt kê account là participant, **loại trừ staff**.
2. Hiển thị **Team** mà participant đang tham gia (hoặc null nếu chưa có team).

---

## 2. Phân tích & quyết định thiết kế

### 2.1 — Phân biệt participant vs staff
Field `userType` của account có 3 giá trị: `FPT_STUDENT`, `EXTERNAL_STUDENT`, `STAFF`.
→ Participant = `userType !== 'STAFF'`. Không cần gọi thêm API, lọc ngay trên mảng `accounts` đã fetch sẵn từ `GET /api/users`.

### 2.2 — Lấy thông tin Team (vấn đề chính)
Backend **không có endpoint user → team**. Cách duy nhất để biết user thuộc team nào là đi đường vòng:

```
GET /api/events                     → lấy danh sách eventId
GET /api/teams/event/{eventId}      → mỗi team có sẵn mảng members[] (userId, fullName, ...)
```

Từ đó build map `userId → [tên team]`. Một user có thể thuộc team ở nhiều event khác nhau → giữ **mảng tên team**, render nối bằng dấu phẩy.

### 2.3 — Lazy load
Bắt chước đúng pattern có sẵn của tab **Staff Roles**: chỉ fetch dữ liệu team **lần đầu tiên** tab Participants được mở (`teamsLoaded` flag), không fetch khi vào trang. Tránh N+1 request vô ích khi coordinator chỉ vào duyệt account.

### 2.4 — Chịu lỗi từng phần
- 1 event lỗi khi fetch teams → `catch(() => [])`, coi như event đó không có team, **không làm sập cả bảng**.
- `GET /api/events` lỗi → hiện banner đỏ "team column unavailable", bảng participant **vẫn render bình thường** (chỉ thiếu cột Team).

---

## 3. Chi tiết thay đổi — `src/features/users/CoordAccountsPage.tsx`

### 3.1 — Interface mới (tolerate camelCase + snake_case)
```ts
interface ApiEventItem { id?; eventId?; event_id?; }
interface ApiTeamMemberItem { userId?; user_id?; }
interface ApiTeamItem { name?; teamName?; team_name?; members?: ApiTeamMemberItem[]; }
```

### 3.2 — State mới
```ts
const [teamsByUser, setTeamsByUser] = useState<Record<number, string[]>>({});
const [teamsLoaded, setTeamsLoaded] = useState(false);
const [teamsLoading, setTeamsLoading] = useState(false);
const [teamsError, setTeamsError] = useState<string | null>(null);
```

### 3.3 — Effect lazy-load team membership
Chạy khi `activeTab === 'PARTICIPANTS' && !teamsLoaded`:
1. Fetch `/api/events` → lấy eventIds.
2. `Promise.all` fetch `/api/teams/event/{id}` cho từng event.
3. Flatten toàn bộ teams, duyệt `members[]` của từng team → build `teamsByUser`.

### 3.4 — Tab & rows
- Union type của `activeTab` thêm `'PARTICIPANTS'` (cả `useState` lẫn cast trong `onChange`).
- Tab mới đặt giữa Rejected và Staff Roles: `Participants (${participantCount})`.
- `participantAccounts = accounts.filter(a => a.userType !== 'STAFF')` — search theo tên hoạt động như các tab khác.

### 3.5 — Bảng render
Cột: `Full Name · Email · Student Type · Student ID · University · Team · Status · Active · Applied`.

Cell Team:
```tsx
{teamsLoading ? "Loading..." : teamNames ? teamNames.join(", ") : "—"}
```
- Có team → chữ màu `C.text` (sáng), không có → `—` màu muted.
- Không có cột Actions/Expires (tab này chỉ để **xem**, action approve/reject đã có ở các tab status).

---

## 4. Luồng hoạt động

1. Coordinator mở trang Accounts → chỉ fetch `/api/users` như cũ.
2. Click tab **Participants** → bảng hiện ngay danh sách participant (lọc local), cột Team hiện "Loading..." trong lúc fetch events + teams.
3. Fetch xong → cột Team điền tên team, hoặc `—` nếu user chưa vào team nào.
4. Mở lại tab lần sau → dùng cache (`teamsLoaded`), không fetch lại.

---

## 5. Trạng thái & việc cần làm tiếp

- ✅ Build (`vite build`) thành công.
- ⬜ **Chưa commit.**
- ⬜ **Chưa chạy app verify trực quan** — cần check cột Team hiện đúng với dữ liệu thật.
- ⬜ Cân nhắc tương lai: nếu backend thêm endpoint user→team (hoặc trả `teamId` trong `GET /api/users`) thì bỏ vòng lặp fetch N event đi.

---

## 6. Ghi chú cho lần sau

- Dữ liệu team membership **chỉ có** qua `GET /api/teams/event/{id}` (kèm `members[]`) — mọi nơi cần map user→team đều phải đi đường này cho tới khi backend bổ sung endpoint.
- Pattern lazy-load theo tab (flag `xxxLoaded` + effect theo `activeTab`) đã dùng ở 2 chỗ trong `CoordAccountsPage` (Staff Roles, Participants) — nếu thêm tab nặng dữ liệu thứ 3, dùng tiếp pattern này.
- `teamsLoaded` cache vĩnh viễn trong session của trang — nếu cần dữ liệu team "tươi" sau khi đội mới được duyệt, phải reload trang (chấp nhận được cho màn admin).
