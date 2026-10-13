# AI Session Log — Events Console Redesign + AuditLog ObjectMapper Fix

**Date:** 2026-06-18
**Project:** SEAL – Software Engineering Hackathon Management System
**Branch:** `develop`
**Scope:**
1. (Back-end) Sửa lỗi app không khởi động được: `AuditLogService` thiếu bean `ObjectMapper`.
2. (Front-end) Thiết kế lại màn hình **Events** của Admin & Coordinator: đảo bố cục, đổi quy tắc chọn mặc định, thêm tìm kiếm event.
3. (Front-end) Dọn UI: ẩn khối *reopen requests* khi rỗng, bỏ tiền tố `//` hiển thị trên giao diện.

---

## PHẦN 1 — BACK-END: Fix `AuditLogService` thiếu bean `ObjectMapper`

### 1.1. Triệu chứng

Khi chạy `seal-api`, ứng dụng fail to start:

```
***************************
APPLICATION FAILED TO START
***************************

Description:
Parameter 2 of constructor in com.seal.hackathon.service.AuditLogService
required a bean of type 'com.fasterxml.jackson.databind.ObjectMapper'
that could not be found.

Action:
Consider defining a bean of type 'com.fasterxml.jackson.databind.ObjectMapper'
in your configuration.
```

### 1.2. Phân tích nguyên nhân gốc

**Kết luận: dự án dùng Spring Boot 4.0.6 (đã chuyển sang Jackson 3), nhưng `AuditLogService` lại yêu cầu inject `ObjectMapper` của Jackson 2.**

Chuỗi suy luận:

1. `pom.xml` dùng `spring-boot-starter-parent` **4.0.6**. Spring Boot 4 / Spring Framework 7 dùng **Jackson 3** làm mặc định. Jackson 3 đổi package gốc: lớp `ObjectMapper` chuyển từ `com.fasterxml.jackson.databind.ObjectMapper` (Jackson 2) sang **`tools.jackson.databind.ObjectMapper`** (Jackson 3).

2. Do đó, bean `ObjectMapper` mà Spring Boot tự cấu hình (qua `JacksonAutoConfiguration`) là kiểu **Jackson 3** (`tools.jackson...`), **không phải** kiểu Jackson 2 (`com.fasterxml.jackson...`).

3. `AuditLogService` lại `import com.fasterxml.jackson.databind.ObjectMapper` (Jackson **2**) và khai báo:
   ```java
   @Service
   @RequiredArgsConstructor
   public class AuditLogService {
       private final AuditLogRepository auditLogRepository;
       private final UserRepository userRepository;
       private final ObjectMapper objectMapper;   // ← Jackson 2, là field final chưa khởi tạo
   }
   ```
   Lombok `@RequiredArgsConstructor` sinh constructor cho **mọi field `final` chưa được gán giá trị** → constructor đòi Spring cấp một bean Jackson-2 `ObjectMapper`. Bean đó **không tồn tại** trong context ⇒ fail to start.

**Tại sao code vẫn compile được nhưng chỉ chết lúc chạy?**
Lớp Jackson 2 vẫn nằm trên classpath vì `jjwt-jackson` 0.12.6 kéo theo `jackson-databind` (Jackson 2) như transitive dependency. Vì vậy import biên dịch OK; chỉ đến lúc Spring dựng context (runtime) mới phát hiện không có **Spring bean** kiểu đó.

**Tại sao các chỗ khác không lỗi?** Tham khảo pattern sẵn có trong dự án:
- `JwtAuthenticationEntryPoint.java`: `private final ObjectMapper objectMapper = new ObjectMapper();` — tự tạo bằng `new`, **không** nhờ Spring inject ⇒ không cần bean.
- `SystemLogService.java`: không dùng `ObjectMapper`.

`AuditLogService` là **chỗ duy nhất** đi inject bean Jackson-2 `ObjectMapper` ⇒ là chỗ duy nhất chết. Đây là code mới (module AuditLog đang phát triển), chưa từng chạy trước đó.

### 1.3. Cách sửa (1 dòng, theo đúng pattern có sẵn)

File: `back-end/src/seal-api/src/main/java/com/seal/hackathon/service/AuditLogService.java`

```diff
- private final ObjectMapper objectMapper;
+ private final ObjectMapper objectMapper = new ObjectMapper();
```

**Vì sao hết lỗi:** khi field `final` được **khởi tạo inline**, Lombok `@RequiredArgsConstructor` **loại nó ra khỏi constructor** (chỉ đưa vào các field `final` *chưa* khởi tạo). Constructor không còn tham số `ObjectMapper` ⇒ Spring không phải đi tìm bean nữa. `ObjectMapper` ở đây chỉ dùng để `writeValueAsString(Map)` nên một instance mặc định là đủ; vẫn dùng Jackson 2 sẵn có trên classpath.

Các phương án khác (đã cân nhắc, **không** chọn):
- Khai báo `@Bean ObjectMapper` Jackson-2 trong config → thêm một mapper thừa vào context, dễ gây nhầm với mapper Jackson 3 đang chạy cho web layer.
- Migrate `AuditLogService` sang Jackson 3 (`tools.jackson.databind.ObjectMapper`, bỏ `throws JsonProcessingException` vì Jackson 3 dùng exception *unchecked*) → sửa nhiều, chưa cần thiết lúc này.

### 1.4. Kiểm chứng

```
./mvnw compile   →  BUILD SUCCESS (exit 0)
```

> Lưu ý dài hạn: dự án đang ở Spring Boot 4 (Jackson 3). Nếu sau này cần inject `ObjectMapper`, nên dùng kiểu Jackson 3 `tools.jackson.databind.ObjectMapper` (bean này Spring tự cấu hình), hoặc tiếp tục tự `new` Jackson 2 — miễn là nhất quán.

---

## PHẦN 2 — FRONT-END: Thiết kế lại màn hình Events (Admin & Coordinator)

### 2.1. Yêu cầu (chốt qua Q&A)

| # | Hạng mục | Quyết định |
|---|----------|-----------|
| 1 | Chọn event mặc định | Ưu tiên event **đang quản lý hoạt động** (OPEN/SETUP/IN_PROGRESS) mới nhất theo ngày → nếu không có thì COMPLETED gần nhất. **Bỏ qua DRAFT.** |
| 2 | Bố cục | **Panel chi tiết lên trên**, **danh sách tổng hợp + tìm kiếm xuống dưới cùng**. Admin: nút *Create* + hàng đợi *reopen* vẫn ở trên cùng. |
| 3 | Tìm kiếm | Một ô "Find event…" lọc trực tiếp khi gõ, không phân biệt hoa/thường, khớp chuỗi con theo **name + season + year**. |
| 4 | Hiện DRAFT trong danh sách | **Có** — danh sách vẫn liệt kê tất cả; chỉ phần *chọn mặc định* mới bỏ qua DRAFT. |

### 2.2. `eventUtils.tsx` (file dùng chung)

**(a) Đổi quy tắc `pickDefaultEvent()`**

Trước đây ưu tiên `IN_PROGRESS` → `COMPLETED` → non-draft mới nhất. Giờ gom 3 trạng thái "đang quản lý" lại:

```diff
- const inProgress = rows.filter(e => e.status === 'IN_PROGRESS');
- if (inProgress.length > 0) return latestByEndDate(inProgress);
+ const active = rows.filter(e => e.status === 'OPEN' || e.status === 'SETUP' || e.status === 'IN_PROGRESS');
+ if (active.length > 0) return latestByEndDate(active);

  const completed = rows.filter(e => e.status === 'COMPLETED');
  if (completed.length > 0) return latestByEndDate(completed);

  return rows.find(e => e.status !== 'DRAFT') ?? rows[0];
```

> Tác động theo dữ liệu mẫu: trước đây mặc định chọn *SEAL Spring 2026 (COMPLETED)*; giờ chọn *SEAL Summer 2026 (SETUP)* vì đó là event đang được quản lý và mới hơn.

**(b) Thêm component dùng chung `EventsListCard`**

Trước đây khối "danh sách events" bị **lặp y hệt** ở cả `CoordEventsPage` và `AdminEventsPage`. Tách thành 1 component trong `eventUtils.tsx` để dùng chung + tránh phân kỳ về sau. Component nhận `events / loading / error / selectedEventId / onSelect`, tự quản lý state ô tìm kiếm:

```tsx
const [query, setQuery] = useState("");
const q = query.trim().toLowerCase();
const filtered = q
  ? events.filter(ev =>
      ev.name.toLowerCase().includes(q) ||
      ev.season.toLowerCase().includes(q) ||
      String(ev.year ?? "").includes(q))
  : events;
```

Hành vi tìm kiếm:
- Lọc **trực tiếp khi gõ** (live), case-insensitive, khớp **chuỗi con** theo tên / mùa / năm → gõ `summer`, `spring`, `2026` hay tên đều ra.
- Không khớp gì → hiển thị **"No events match your search."**
- Ô tìm kiếm **chỉ lọc danh sách** ở dưới; **không** thay đổi event đang chọn hay panel chi tiết. Nếu event đang chọn bị lọc khỏi danh sách thì chi tiết vẫn hiển thị bình thường.

### 2.3. `CoordEventsPage.tsx` & `AdminEventsPage.tsx`

- Import `EventsListCard` từ `eventUtils`.
- **Bỏ khối danh sách inline** (cũ), thay bằng `<EventsListCard ... />`.
- **Đảo thứ tự render**: panel chi tiết event được chọn **lên trên**, `EventsListCard` xuống **dưới cùng** (ngay trước `ConfirmDialog`).
- Admin: nút **Create event** + **hàng đợi reopen request** giữ ở **trên cùng** (theo bố cục đã chốt).
- Admin: bỏ import `PixelBadge` thừa (sau khi gỡ list inline thì không còn dùng trực tiếp).

Thứ tự khối sau khi đảo:

```
Coordinator:  Header → [banners] → CHI TIẾT → EventsListCard(search+list) → ConfirmDialog
Admin:        Header(+Create) → ReopenQueue → CreateForm → CHI TIẾT → EventsListCard → ConfirmDialog
```

### 2.4. Kiểm chứng

```
npx tsc --noEmit --ignoreDeprecations 6.0   →  exit 0 (no errors)
```

---

## PHẦN 3 — FRONT-END: Dọn UI (reopen requests + bỏ tiền tố `//`)

### 3.1. Admin — khối *reopen requests*

File: `AdminEventsPage.tsx`

- **Ẩn cả section khi không có request**: điều kiện hiển thị đổi từ `canManageReopenRequests` → `canManageReopenRequests && requests.length > 0`. Bỏ luôn nhánh "Loading…" và "No pending requests.".
- **Bỏ tiền tố `//` trên giao diện**: tiêu đề `// reopen requests` → **`Reopen Requests · N pending`**.
- Dọn theo: gỡ state `requestsLoading` (không còn nơi đọc) và đơn giản hoá `loadRequests()`:

```diff
  function loadRequests() {
-   if (!canManageReopenRequests) { setRequestsLoading(false); return; }
-   setRequestsLoading(true);
+   if (!canManageReopenRequests) return;
    reopenRequestsApi.getPending()
      .then(res => setRequests(res.data ?? []))
-     .catch(() => { /* non-fatal */ })
-     .finally(() => setRequestsLoading(false));
+     .catch(() => { /* non-fatal */ });
  }
```

### 3.2. Coordinator — phần *track draw*

File: `CoordEventsPage.tsx` (heading chỉ hiện khi event ở trạng thái SETUP)

- **Bỏ tiền tố `//`** và đặt tiêu đề chuẩn:

```diff
- // {selectedEvent.trackSelectionMode === 'RANDOM' ? 'random track draw' : 'fill unassigned tracks'}
+ {selectedEvent.trackSelectionMode === 'RANDOM' ? 'Random track draw' : 'Fill unassigned tracks'}
```

> Các dòng bắt đầu bằng `//` còn lại trong 2 file đều là **comment code thật** (không render ra UI) nên giữ nguyên.

### 3.3. Kiểm chứng

```
npx tsc --noEmit --ignoreDeprecations 6.0   →  exit 0 (no errors)
```

---

## Tổng hợp file thay đổi

| File | Thay đổi |
|------|----------|
| `back-end/.../service/AuditLogService.java` | `ObjectMapper` khởi tạo inline (`new ObjectMapper()`) → hết lỗi thiếu bean |
| `front-end/.../features/events/eventUtils.tsx` | Đổi `pickDefaultEvent` (ưu tiên OPEN/SETUP/IN_PROGRESS); thêm component `EventsListCard` (có tìm kiếm) |
| `front-end/.../features/events/CoordEventsPage.tsx` | Dùng `EventsListCard`; đảo bố cục (chi tiết trên, list dưới); bỏ `//` ở heading track draw |
| `front-end/.../features/events/AdminEventsPage.tsx` | Dùng `EventsListCard`; đảo bố cục; ẩn khối reopen khi rỗng + tiêu đề "Reopen Requests"; gỡ `requestsLoading` & import `PixelBadge` thừa |

## Cách chạy / kiểm thử
- **Back-end:** `cd back-end/src/seal-api && ./mvnw spring-boot:run` (cần MySQL `seal_hackathon`). App phải khởi động không còn lỗi `ObjectMapper`.
- **Front-end:** `cd front-end/src/seal-web && npm run dev`. Vào màn **Events** của Admin và Coordinator:
  - Mặc định chọn đúng event đang quản lý mới nhất (bỏ DRAFT).
  - Panel chi tiết nằm trên, danh sách + ô "Find event…" nằm dưới; gõ để lọc.
  - Admin: khối *Reopen Requests* chỉ hiện khi có request; không còn tiền tố `//`.
  - Coordinator (event SETUP): tiêu đề track draw không còn `//`.
