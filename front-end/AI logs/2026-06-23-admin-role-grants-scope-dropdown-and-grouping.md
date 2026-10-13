# AI Session Log — Admin Role Grants: Scope dropdown, gộp role theo user & tinh chỉnh UX

**Date:** 2026-06-23
**Project:** SEAL – Software Engineering Hackathon Management System
**Branch:** `develop`
**Scope:** Tái thiết kế màn hình admin **Role Grants** (`AdminRolesPage.tsx`): thêm **dropdown lọc Scope theo từng kì**, **gộp nhiều role của một user vào một dòng**, đưa **REVOKE thành một cột riêng căn theo từng role**, **auto-select event đang diễn ra/gần nhất** ở modal Grant Role, đổi nhãn **"System-wide" → "System"**, và **kiểm chứng mapping API** cho thắc mắc "Summer 2026 chỉ có 3 user".

> Phạm vi phiên này **chỉ chạm 1 file front-end**: `front-end/src/seal-web/src/features/users/AdminRolesPage.tsx`. Không sửa backend, không sửa DB.

---

## PHẦN 0 — Lấy context & chốt yêu cầu qua thảo luận

### 0.1. Tài liệu đã đọc để hiểu hệ thống
- `back-end/Postman/Postman_Full_Collection.json` — toàn bộ API; trong đó nhóm **2. ADMIN – USER & ROLE MGMT** có `GET /api/admin/roles`, `POST /roles/grant`, `DELETE /roles/revoke`.
- `docs/documents/ProjectRequirements.md` — đề bài SWP391 (6 actor: Team Member/Leader, Mentor, Judge, Event Coordinator, Admin; cấu trúc Event → Track → Round; mỗi giảng viên có thể vừa Mentor hạng mục này vừa Judge hạng mục khác).
- Front-end: `features/users/AdminRolesPage.tsx`, `shared/apiClient.ts` (interface `RoleGrantItem`, `HackathonEvent`, `UserItem`), `shared/components/PixelComponents.tsx` (`PixelButton`, `PixelBadge`).
- Back-end (giai đoạn kiểm chứng): `controller/AdminController.java`, `service/AdminService.java`, `dto/response/UserEventRoleResponse.java`, `database scripts/seal_seed.sql`.

### 0.2. Hiện trạng & vấn đề
Màn hình Role Grants render **mỗi `RoleGrantItem` (user + role + scope) thành một dòng riêng**. Hệ quả:
- Nguyen Van Coordinator = COORDINATOR ở Spring/Summer/Fall → **3 dòng**.
- Tran Van An = MENTOR (Spring) + MENTOR (Summer) + JUDGE (Spring) → nhiều dòng.

→ Bảng bị lặp user, khó quét nhanh.

### 0.3. Quyết định chốt với người dùng (qua nhiều vòng Q&A)

| Câu hỏi | Lựa chọn |
|---|---|
| "Scope có date gần nhất" để default hiểu thế nào? | **Event active / gần hôm nay nhất** (today trong `[startDate, endDate]` → distance 0; nếu không thì kì gần nhất) |
| Revoke khi gộp nhiều role | Ban đầu chọn **× trên từng badge**, sau đổi sang **nút REVOKE riêng ở cột Actions, mỗi role 1 nút căn thẳng hàng** |
| Dropdown Scope tổ chức thế nào | **Theo từng kì** (mỗi event = 1 kì), group `<optgroup>` theo năm |
| Grant system-wide hiển thị ra sao | Một mục riêng trong dropdown; sau rút gọn nhãn **"System"** |
| Cột Scope trong bảng | **Ẩn** (vì kì đã nằm ở dropdown) |
| Event Scope ở modal Grant Role | **Auto-select** event đang diễn ra/gần nhất, **không** gắn nhãn `★`; user vẫn chọn event khác bình thường |

> Phiên có dùng **plan mode**: đã viết plan, người dùng **approve**, rồi mới tiếp tục.

---

## PHẦN 1 — Thiết kế chốt

**Dropdown Scope** (cạnh ô Search):
- Options = từng kì (event) group theo năm bằng `<optgroup>`, cộng **một mục `System`** cho các grant không thuộc kì (eventId null).
- Sắp xếp kì mới → cũ; **default** = kì active/gần hôm nay nhất.

**Bảng** (sau khi chọn 1 scope):
- Group theo `userId` → **mỗi user 1 dòng**; các role xếp **dọc** trong cột Roles.
- Cột **Actions** riêng: mỗi role một nút **REVOKE** căn thẳng hàng đúng role tương ứng.
- **Bỏ** cột "Scope" (kì đã ở dropdown).
- Cột: `User | Email | Roles | Actions`.

**Revoke**: bấm REVOKE → mở **pop-up cảnh báo xác nhận** `RevokeModal` cho đúng grant đó.

---

## PHẦN 2 — IMPLEMENTATION (`AdminRolesPage.tsx`)

### 2.1. State & dữ liệu dẫn xuất
- Thêm `import { useMemo }`.
- State mới: `selectedScope: string` — `""` khi chưa resolve default; `"sys"` cho System; `String(eventId)` cho từng kì.
- `useMemo` dựng `{ eventScopes, hasSystem }` từ `grants` + map `events` theo `eventId`:
  - Mỗi kì xuất hiện **một lần** (dedup theo eventId), kèm `label` (lấy `event.name`, fallback `eventName`/`Event #id`), `year`, `sortDate`.
  - `hasSystem = grants.some(g => g.eventId == null)`.
  - eventScopes sort theo `sortDate` giảm dần (mới → cũ).

### 2.2. Default scope = kì active/gần hôm nay
- `useEffect`: nếu `selectedScope === ""` thì chọn kì có **`eventDistanceToNow` nhỏ nhất**; không có event nào thì rơi về `"sys"`.

### 2.3. Helper tái sử dụng (tránh lặp logic)
```ts
// 0 khi event đang diễn ra, ngược lại là khoảng cách tới mép gần nhất (start/end)
function eventDistanceToNow(ev: HackathonEvent, now: number): number { ... }

// event đang diễn ra, hoặc kì có khung ngày gần hôm nay nhất
function pickNearestEvent(events: HackathonEvent[]): HackathonEvent | null { ... }
```
→ Dùng chung cho **default scope của bảng** và **auto-select của modal Grant Role**.

### 2.4. Lọc + group
- `scopedGrants`: lọc theo `selectedScope` (`"sys"` → `eventId == null`; còn lại → `eventId === Number(selectedScope)`).
- Áp `search` (tên/email/role) **trong scope đang chọn**.
- `groupedRows`: gom theo `userId` thành `{ userId, userFullName, userEmail, grants[] }`, sort theo tên.

### 2.5. Chống nháy & tự phục hồi scope
- `resolving = selectedScope === "" && (eventScopes.length > 0 || hasSystem)` → khi default chưa set nhưng đã có data thì hiện **"Loading..."** thay vì nháy empty.
- `useEffect` reset `selectedScope` về `""` khi kì đang chọn **không còn grant** (vd. revoke hết role) → default-effect chọn lại kì hợp lệ.

### 2.6. Render
- Hàng control: label `SCOPE` + `<select>` (`<optgroup>` theo năm + mục `System`) ở trái, ô Search ở phải.
- Bảng: header `User | Email | Roles | Actions` (`colSpan={4}` cho loading/error/empty).
- Cột Roles & Actions cùng dùng flex-column, mỗi item `minHeight: 30` để **badge và nút REVOKE căn thẳng hàng từng role**; các `td` đặt `verticalAlign: top`.

---

## PHẦN 3 — Kiểm chứng MAPPING API (thắc mắc "Summer 2026 chỉ có 3 user/3 role")

Người dùng nghi mapping sai. Đối chiếu 3 nguồn:

1. **Backend** `AdminService.getAllRoleGrants()` — đọc toàn bộ `UserEventRole`, lấy `eventId` trực tiếp từ grant, resolve `eventName` qua lookup theo eventId. DTO `UserEventRoleResponse` có `@JsonInclude(NON_NULL)` ⇒ grant system-wide (`eventId=null`) **bị bỏ field `eventId`** khỏi JSON → FE nhận `undefined`. (FE đã dùng `g.eventId == null` loose-equality nên vẫn bắt đúng nhóm System.)

2. **Seed** `database scripts/seal_seed.sql`, bảng `UserEventRole`, event_id=2 (Summer 2026) chỉ có **đúng 3 dòng**:
```sql
(2,  2, 2),   -- user 2  → EVENT_COORDINATOR
(27, 3, 2),   -- user 27 → MENTOR (Thay Hung)
(3,  3, 2),   -- user 3  → MENTOR (Thay An)
```

3. **Frontend** group theo user khớp đúng 3 dòng đó.

**Kết luận: mapping ĐÚNG — không có bug.** Summer ít vì **chưa grant Judge nào** cho event 2 (đang giai đoạn đăng ký; `JudgeAssignment` cũng chỉ có dữ liệu event 1). Phân bố grant theo seed:

| Kì (event) | Số grant | Chi tiết |
|---|---|---|
| Spring 2026 (1) | 6 | Coordinator + Thay An (MENTOR+JUDGE) + Binh + Cô Cẩm + Guest (JUDGE) |
| Summer 2026 (2) | 3 | Coordinator + 2 Mentor |
| Fall 2026 (3) | 1 | Coordinator |
| System | 1 | Admin (SYSTEM_ADMIN) |

→ Muốn Summer "đầy" hơn là chuyện **bổ sung seed**, không phải sửa mapping.

---

## PHẦN 4 — CÁC VÒNG TINH CHỈNH UX

### 4.1. Revoke: × trên badge → nút REVOKE → cột Actions căn hàng
- (a) Bản đầu: dấu `×` đỏ cạnh mỗi badge (mở `RevokeModal`).
- (b) Người dùng thấy rối → đổi về **nút REVOKE kiểu cũ** (`PixelButton size="sm" variant="danger"`) cạnh badge.
- (c) Vẫn rối → **Option A đã chọn**: tách **cột Actions riêng**, mỗi role một REVOKE **căn thẳng hàng** với badge (qua `minHeight: 30`). Pop-up xác nhận `RevokeModal` giữ nguyên.

### 4.2. Grant Role → Event Scope: auto-select, bỏ nhãn `★`
- Auto **pre-select** `recommendedEventId = pickNearestEvent(events)` ⇒ mở modal đã chọn sẵn kì hợp lý; chuyển role MENTOR/JUDGE là có ngay event.
- Ban đầu có gắn nhãn `★ đang diễn ra / ★ gần nhất` vào option → **người dùng không muốn** ⇒ **bỏ nhãn**, option chỉ còn tên event sạch; user vẫn chọn kì khác bình thường.

### 4.3. Xóa note `//` ở modal
- `ModalShell` trước in dòng `// {tag}` (vd `// grant_role`). **Bỏ dòng này** + **bỏ luôn prop `tag`** không còn dùng ở cả 2 chỗ gọi (`GrantRoleModal`, `RevokeModal`).

### 4.4. "System-wide" → "System" (đồng nhất nhãn)
- Dropdown Scope: gộp optgroup lồng → **một mục `System`** (`<option value="sys">System</option>`).
- Modal Grant Role → Event Scope placeholder: `System-wide (no event)` → `System (no event)`.
- Hộp xác nhận Revoke: `... (system-wide)?` → `... (system)?`.
- Giữ nguyên 2 **comment nội bộ** dùng từ "system-wide" (không hiển thị UI).

> Lưu ý đã nhắc người dùng: nếu vẫn thấy "System-wide" trên app thì do **chưa rebuild/hot-reload**, code trong file đã đúng.

---

## PHẦN 5 — KIỂM THỬ

- **Typecheck:** `npx tsc --noEmit --ignoreDeprecations 6.0` tại `front-end/src/seal-web` → **pass** sau mỗi vòng sửa.
  - Lưu ý: phải kèm `--ignoreDeprecations 6.0` vì `tsconfig.json` còn dùng `baseUrl` (deprecated ở TS 7.0) → đây là **vấn đề config có sẵn**, không liên quan thay đổi phiên này. Project không có script `typecheck` riêng (`build` = `vite build`, không chạy tsc).
- **Trực quan:** chưa chạy app (cần BE + DB). Để người dùng tự refresh dev server kiểm tra.

---

## PHẦN 6 — FILE THAY ĐỔI

| File | Thay đổi |
|---|---|
| `front-end/src/seal-web/src/features/users/AdminRolesPage.tsx` | Toàn bộ nội dung phiên: scope dropdown + group theo user + cột Actions căn hàng + helper nearest-event + auto-select modal + đổi nhãn System + xóa note `//` |

(Plan file tạm: `~/.claude/plans/effervescent-conjuring-hamster.md` — chỉ phục vụ plan mode.)

---

## PHẦN 7 — ĐIỂM CÒN TREO (đã flag, chưa xử lý)

- **Mâu thuẫn `EVENT_COORDINATOR`:** trong `GrantRoleModal`, `EVENT_COORDINATOR` đang bị ép **system-wide** (`eventId=null`, ô Event Scope disabled), **nhưng seed lại gắn COORDINATOR theo kì** (event 1/2/3). Hệ quả: coordinator grant qua form sẽ rơi vào nhóm **System** thay vì một kì → lệch với cách hiển thị/seed. Chưa sửa trong phiên này; cần task riêng nếu muốn coordinator gắn theo kì.

---

## PHẦN 8 — TÓM TẮT NHANH

- Role Grants giờ **lọc theo từng kì** + **mỗi user một dòng**, REVOKE gọn ở **cột riêng căn hàng**, có **pop-up xác nhận**.
- Modal Grant Role **tự chọn sẵn kì đang diễn ra/gần nhất**, nhãn sạch.
- Nhãn **"System"** đồng nhất, bỏ note `//`.
- **Mapping API đã được kiểm chứng là đúng** — "Summer 3 user" là đúng dữ liệu seed, không phải bug.
- **Không đụng** backend/DB; typecheck pass.

---

# PHỤ LỤC A — GIẢI THÍCH CHI TIẾT CODE & LOGIC

> Phần này giải thích **từng khối code** trong `AdminRolesPage.tsx` và **lý do** đằng sau, để đọc lại là hiểu ngay logic vận hành. Đọc theo thứ tự dữ liệu chảy: **fetch → dựng scope → chọn default → lọc/group → render**.

## A.1. Dữ liệu đầu vào & "phép nối" (join) then chốt

Backend trả về 3 nguồn, FE tự ghép:

```ts
const [grants, setGrants] = useState<RoleGrantItem[]>([]); // GET /api/admin/roles
const [users,  setUsers]  = useState<UserItem[]>([]);      // GET /api/admin/users (cho modal Grant)
const [events, setEvents] = useState<HackathonEvent[]>([]);// GET /api/events     (cho ngày & tên kì)
```

**Mấu chốt:** `RoleGrantItem` **không có ngày** của event:
```ts
interface RoleGrantItem { id; userId; userFullName; userEmail; roleName; eventId?; eventName?; }
```
Nhưng `HackathonEvent` **có** `startDate / endDate / year / name`. Vì vậy muốn biết "kì nào đang diễn ra / gần nhất" ta phải **nối** `grant.eventId → event` qua một `Map`:
```ts
const eventMap = new Map(events.map(e => [e.eventId, e]));
const ev = eventMap.get(grant.eventId); // lấy được startDate/endDate/year
```
→ Đây là lý do trang phải load `events` dù bảng chính chỉ hiển thị grants. **Không cần sửa backend.**

`Promise.all([...])` tải song song cả 3; riêng users/events có `.catch(() => [])` để **một nguồn lỗi không làm sập cả trang** (bảng grants vẫn hiện).

## A.2. Hai helper "nearest event" — trái tim của logic default/recommend

```ts
function eventDistanceToNow(ev, now): number {
  const start = new Date(ev.startDate).getTime();
  const end   = new Date(ev.endDate).getTime();
  if (Number.isNaN(start) || Number.isNaN(end)) return Infinity; // dữ liệu hỏng → loại
  if (now >= start && now <= end) return 0;          // ĐANG diễn ra → khoảng cách 0 (ưu tiên cao nhất)
  return now < start ? start - now : now - end;       // chưa tới: cách start | đã qua: cách end
}
```
**Ý tưởng:** quy mọi kì về **một con số "khoảng cách tới hôm nay"** để so sánh:
- Kì đang chạy → `0` (luôn thắng).
- Kì tương lai → `start - now` (càng sắp tới càng nhỏ).
- Kì đã qua → `now - end` (vừa kết thúc thì nhỏ).

```ts
function pickNearestEvent(events): HackathonEvent | null {
  // duyệt 1 lượt, giữ event có distance nhỏ nhất
}
```

**Ví dụ với hôm nay = 2026-06-23** (Summer chạy 06/01–08/31):
| Kì | Khoảng | distance | Kết quả |
|---|---|---|---|
| Spring 2026 | đã qua (kết 04/30) | ~54 ngày | |
| **Summer 2026** | đang chạy | **0** | ✅ được chọn |
| Fall 2026 | tương lai | >0 | |

→ Vì sao tách thành helper: **dùng ở 2 nơi** (default scope của bảng + auto-select của modal), tránh viết lặp công thức.

## A.3. Dựng danh sách Scope cho dropdown (`useMemo`)

```ts
const { eventScopes, hasSystem } = useMemo(() => {
  const eventMap = new Map(events.map(e => [e.eventId, e]));
  const hasSystem = grants.some(g => g.eventId == null);   // có grant nào không thuộc kì?
  const seen = new Map<number, {...}>();
  for (const g of grants) {
    if (g.eventId == null || seen.has(g.eventId)) continue; // bỏ system + DEDUP kì
    const ev = eventMap.get(g.eventId);
    seen.set(g.eventId, {
      key: String(g.eventId),
      label: ev?.name ?? g.eventName ?? `Event #${g.eventId}`, // fallback nhiều tầng
      year: ev?.year ?? 0,
      sortDate: ev ? new Date(ev.startDate).getTime() : 0,
    });
  }
  const eventScopes = [...seen.values()].sort((a, b) => b.sortDate - a.sortDate); // mới → cũ
  return { eventScopes, hasSystem };
}, [grants, events]);
```

**Logic & lý do:**
- **Dẫn xuất từ `grants`, không phải từ `events`**: dropdown chỉ liệt kê kì **thực sự có grant** → không bao giờ chọn vào một kì rỗng.
- **Dedup bằng `seen.has(eventId)`**: 3 grant của Coordinator ở Summer chỉ tạo **một** mục "Summer" trong dropdown.
- **`label` fallback nhiều tầng**: ưu tiên tên từ `events`; nếu thiếu thì dùng `eventName` của grant; tệ nhất hiện `Event #id` (không bao giờ trống).
- **`hasSystem`**: dùng `== null` (loose) để bắt **cả `null` lẫn `undefined`** — quan trọng vì backend có `@JsonInclude(NON_NULL)` nên grant system-wide bị **mất hẳn field `eventId`** → ở FE là `undefined`.
- **`useMemo`**: chỉ tính lại khi `grants`/`events` đổi, và giữ **identity ổn định** cho mảng (tránh effect bên dưới chạy vô ích).

## A.4. Chọn Scope mặc định (effect 1) — vì sao có sentinel `""`

```ts
const [selectedScope, setSelectedScope] = useState<string>(""); // "" = CHƯA resolve

useEffect(() => {
  if (selectedScope !== "") return;            // đã có lựa chọn → không ghi đè ý người dùng
  if (eventScopes.length === 0) {              // không kì nào
    if (hasSystem) setSelectedScope("sys");
    return;
  }
  // chọn kì có eventDistanceToNow nhỏ nhất
  let best = eventScopes[0], bestDist = Infinity;
  for (const o of eventScopes) {
    const ev = eventMap.get(Number(o.key));
    const dist = ev ? eventDistanceToNow(ev, now) : Infinity;
    if (dist < bestDist) { bestDist = dist; best = o; }
  }
  setSelectedScope(best.key);
}, [eventScopes, hasSystem, events, selectedScope]);
```

**Vì sao dùng `""` làm "cờ chưa resolve":** lúc mới mount, `events`/`grants` chưa về → chưa thể tính kì gần nhất. Để `""` nghĩa là *"chưa quyết"*. Khi data về, effect chạy, set đúng kì. **`if (selectedScope !== "") return;`** đảm bảo effect chỉ tự chọn **một lần**; sau đó người dùng đổi scope thì không bị nhảy lại.

## A.5. Tự phục hồi khi scope hết grant (effect 2)

```ts
useEffect(() => {
  if (selectedScope === "") return;
  const valid = selectedScope === "sys" ? hasSystem
              : eventScopes.some(o => o.key === selectedScope);
  if (!valid) setSelectedScope("");   // kì đã biến mất → quay về "" để effect 1 chọn lại
}, [selectedScope, eventScopes, hasSystem]);
```
**Tình huống:** đang xem Fall (chỉ có 1 grant Coordinator) rồi revoke nó → Fall không còn trong `eventScopes` → `valid = false` → reset `""` → **effect 1** tự chọn lại kì hợp lệ khác. Không để màn hình "kẹt" ở scope rỗng.

## A.6. Lọc → tìm kiếm → GỘP theo user (3 bước)

```ts
// (1) lọc theo scope đang chọn
const scopedGrants =
  selectedScope === ""    ? []
: selectedScope === "sys" ? grants.filter(g => g.eventId == null)
:                           grants.filter(g => g.eventId === Number(selectedScope));

// (2) áp search TRONG scope (tên / email / role)
const filtered = query ? scopedGrants.filter(g => ...includes(query)) : scopedGrants;

// (3) GỘP theo userId → mỗi user 1 dòng, gom mảng grants[]
const groupedRows = (() => {
  const map = new Map<number, { userId; userFullName; userEmail; grants: RoleGrantItem[] }>();
  for (const g of filtered) {
    const row = map.get(g.userId);
    if (row) row.grants.push(g);                    // user đã có → thêm role vào dòng cũ
    else map.set(g.userId, { ...g, grants: [g] });  // user mới → tạo dòng
  }
  return [...map.values()].sort((a, b) => a.userFullName.localeCompare(b.userFullName));
})();
```

**Đây chính là phần trả lời yêu cầu "gộp role":**
- Trước: 1 grant = 1 dòng → Tran Van An (MENTOR + JUDGE ở Spring) ra 2 dòng.
- Giờ: gom theo `userId`, đẩy mọi role của cùng user vào `row.grants[]` → **1 dòng/user**, cột Roles render từng phần tử `grants[]` thành nhiều badge.
- Search **đặt sau bước lọc scope** → tìm kiếm chỉ trong kì đang xem (đúng kỳ vọng "dropdown là điều hướng chính").

## A.7. Cờ `resolving` — chống "nháy" empty

```ts
const resolving = selectedScope === "" && (eventScopes.length > 0 || hasSystem);
...
{(loading || resolving) && <Loading/> }
{!loading && !resolving && groupedRows.length === 0 && <No role grants in this scope/> }
```
Giữa lúc `loading=false` nhưng **effect 1 chưa kịp set** `selectedScope`, `groupedRows` tạm rỗng → nếu không chặn sẽ **nháy** chữ "No role grants" 1 frame. `resolving` = *"đã có data nhưng chưa chọn xong scope"* → hiển thị "Loading..." mượt, rồi mới ra bảng.

## A.8. Render bảng — căn thẳng hàng Roles ↔ Actions

Hai cột Roles và Actions đều dùng **flex-column cùng `gap: 8` và mỗi item `minHeight: 30`**:
```tsx
<td style={{ verticalAlign: "top" }}>            {/* Roles */}
  <div style={{ display:"flex", flexDirection:"column", gap:8 }}>
    {row.grants.map(g => (
      <div key={g.id} style={{ minHeight:30, display:"flex", alignItems:"center" }}>
        {roleBadge(g.roleName)}
      </div>
    ))}
  </div>
</td>
<td style={{ verticalAlign: "top" }}>            {/* Actions — cùng số phần tử, cùng minHeight */}
  ... <PixelButton size="sm" variant="danger" onClick={() => setRevokeTarget(g)}>REVOKE</PixelButton> ...
</td>
```
**Vì sao thẳng hàng:** cả 2 cột lặp **cùng `row.grants`** (cùng thứ tự, cùng số dòng) với **cùng chiều cao mỗi dòng** (`minHeight 30`). Nên badge thứ *n* luôn nằm ngang với nút REVOKE thứ *n*. `verticalAlign:"top"` để tên/email dính mép trên, thẳng với role đầu tiên.

**Cơ chế revoke chính xác:** mỗi nút gắn `onClick={() => setRevokeTarget(g)}` với **đúng object `g`** (chính là một `RoleGrantItem`). `RevokeModal` nhận `grant=g` và gọi `revokeRole({ userId, roleName, eventId })` đúng role đó → gộp dòng nhưng **không mất tính chính xác từng role**.

## A.9. Dropdown Scope — optgroup theo năm + mục System

```tsx
<select value={selectedScope} onChange={e => setSelectedScope(e.target.value)}>
  {eventScopes.length === 0 && !hasSystem && <option value="">No scopes</option>}
  {years.map(year => (                                  // years = các năm distinct, giảm dần
    <optgroup key={year} label={year ? String(year) : "Other"}>
      {eventScopes.filter(s => s.year === year).map(o =>
        <option key={o.key} value={o.key}>{o.label}</option>)}
    </optgroup>
  ))}
  {hasSystem && <option value="sys">System</option>}    {/* mục đơn, value="sys" */}
</select>
```
- `years = [...new Set(eventScopes.map(s => s.year))].sort((a,b)=>b-a)` → gom kì **theo từng năm** (đúng yêu cầu "scope theo từng kì").
- Mục **System** là `<option value="sys">` đơn giản (đã bỏ `<optgroup>` lồng cho gọn). `value="sys"` chính là khoá mà `scopedGrants` dùng để lọc `g.eventId == null`.

## A.10. Modal Grant Role — auto-select & ràng buộc

```ts
const recommendedEventId = useMemo(() => pickNearestEvent(events)?.eventId ?? null, [events]);
const [eventId, setEventId] = useState<number | "">(recommendedEventId ?? ""); // PRE-SELECT
const systemWide = roleName === 'SYSTEM_ADMIN' || roleName === 'EVENT_COORDINATOR';
```
- **Auto-select:** `useState(recommendedEventId ?? "")` → vừa mở modal, ô Event Scope **đã chọn sẵn** kì gần nhất; người dùng vẫn bấm dropdown đổi kì khác. Option chỉ hiện **tên event sạch** (đã bỏ nhãn `★`).
- **`systemWide`:** với role hệ thống, ô Event Scope **disabled** + gửi `eventId: null`. Validation `if (!systemWide && eventId === "")` chặn quên chọn kì cho MENTOR/JUDGE.
- Submit gọi `adminApi.grantRole({ userId, roleName, eventId: systemWide ? null : Number(eventId) })`, thành công thì `onGranted()` → cha gọi `loadGrants()` refresh bảng.

## A.11. Vòng đời tổng thể (end-to-end)

```
mount → Promise.all tải grants/users/events
      → useMemo dựng {eventScopes, hasSystem}
      → effect1 chọn selectedScope = kì gần nhất (hoặc "sys")
      → scopedGrants (lọc) → filtered (search) → groupedRows (gộp user)
      → render: dropdown + bảng (Roles ↔ Actions căn hàng)

người dùng đổi Scope  → setSelectedScope → tính lại scopedGrants/groupedRows
bấm REVOKE            → setRevokeTarget(g) → RevokeModal xác nhận → revokeRole → loadGrants
revoke hết kì         → effect2 reset "" → effect1 chọn lại kì hợp lệ
bấm + GRANT ROLE      → GrantRoleModal (auto-select kì) → grantRole → loadGrants
```

**Nguyên tắc thiết kế xuyên suốt:** mọi state phái sinh (`eventScopes`, `scopedGrants`, `groupedRows`, `resolving`) đều **tính từ `grants/events/selectedScope/search`** — không lưu trùng state → dữ liệu luôn nhất quán, chỉ cần `loadGrants()` là cả bảng tự cập nhật.
