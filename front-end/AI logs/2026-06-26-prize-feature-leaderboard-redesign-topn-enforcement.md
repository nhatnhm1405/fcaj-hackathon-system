# AI Session Log — Prize/Award feature, Leaderboard redesign (podium), Top N enforcement + tie-break

**Date:** 2026-06-26
**Project:** SEAL – Software Engineering Hackathon Management System
**Branch:** `develop`
**Stack chạm tới:** BE (Spring Boot), FE (React/Vite/TS), DB (MySQL seed), Postman.

**Scope tổng quát:** Một chuỗi yêu cầu liên tiếp, làm theo kiểu Q&A — duyệt/đề xuất trước khi code:
1. **Tính năng trao giải (Prize)** cho top trong final round — phạm vi **event-wide** (top N toàn sự kiện).
2. Fix **leaderboard hiển thị sai**: vòng loại phải rank **theo từng track**, đội đi tiếp/loại tô màu; final rank chung toàn bộ.
3. Re-seed MySQL local + tinh chỉnh UI nhiều vòng (chìm thay đỏ, bỏ dòng thừa, ẩn track ở final, status final, **podium**, màu header track).
4. **Top N**: rà logic → **enforce** (chặn đội bị loại nộp bài vòng sau) + **tie-break** (bằng điểm → nộp sớm hơn xếp trên).
5. **D1/D2**: cho phép **xóa Top N về null** + **validate Top N ≥ 1**.

> **Cách làm xuyên suốt (theo thói quen người dùng):** hỏi-đáp liên tục, **đề xuất/đối chiếu code thật trước khi sửa**, ưu tiên đọc context để tiết kiệm usage.

---

## PHẦN 0 — Lấy context

Đọc đầu phiên theo yêu cầu:
- `back-end/Postman/Postman_Full_Collection.json` — 16 nhóm API (AUTH … TRACK PROBLEMS). **Không có endpoint Prize nào.**
- `docs/documents/ProjectRequirements.md` — đề SWP391. Mục **5 (entity Prize)**, **6.9**, **UC14** nói về trao giải; **6.1/6.8/UC12** nói về top N thăng vòng.

**Phát hiện then chốt:**
- Bảng `Prize` **đã tồn tại** trong `seal_schema.sql` (+ seed + audit action `AWARD_PRIZE`) nhưng **chưa có tầng BE/FE** → chỉ cần phủ app layer, **không thiết kế lại model**.
- `RoundResultService.finalizeRound` **đã** rank **per-track** cho vòng non-final và **global** cho final (`isFinal`); `advanced = rankPosition <= topNAdvance` suy **live**.

---

## PHẦN 1 — Tính năng Trao giải (Prize) — BE + Postman + seed

**Quyết định chốt với user (qua AskUserQuestion):** làm **lập kế hoạch chi tiết** trước, phạm vi **giải toàn sự kiện** (event-wide, `Prize.track_id` luôn NULL); winner = **top N của final round (global ranking)**, độc lập với `Round.topNAdvance` (vốn chỉ để thăng vòng per-track).

**State model:** `awarded_at = NULL` → draft (chưa public); set → đã công bố (public + notify). Phản chiếu cặp `results/finalize` + `results/publish` có sẵn.

**File BE mới:**
- `entity/Prize.java` — map bảng Prize; `track` luôn NULL.
- `repository/PrizeRepository.java` — `findAllByEvent_EventIdOrderByRankPosition`, `…AndAwardedAtIsNotNull…`.
- `dto/request/CreatePrizeRequest.java`, `UpdatePrizeRequest.java`, `AutoGeneratePrizesRequest.java`.
- `dto/response/PrizeResponse.java` — kèm `teamTrackName`, `finalScore`, `announced`.
- `service/PrizeService.java` — CRUD + `autoGenerate` (đọc final round FINALIZED → top N từ `RoundResult` → tạo slot Champion/1st/2nd Runner-up) + `announce` (validate mọi slot có team → set `awarded_at` → `NotificationService` báo đội thắng → `AuditLogService.record("AWARD_PRIZE","PRIZE",eventId,…)`).
- `controller/PrizeController.java` — 6 endpoint dưới `/api/events/{eventId}/prizes` (GET public=announced/coordinator=all, POST/PUT/DELETE, POST `/auto-generate`, POST `/announce`).
- `repository/RoundRepository.java` — thêm `findFirstByEvent_EventIdAndIsFinalTrue`.

**Không cần sửa `SecurityConfig`:** `/api/events/**` đã `permitAll()` ở URL-level; action coordinator chặn bằng `@PreAuthorize("hasRole('EVENT_COORDINATOR')")` per-method (giống Tracks/Rounds/Results).

**Postman:** thêm nhóm **"17. PRIZES"** (7 request gồm test 403) + biến `prizeId`. JSON validate OK (17 nhóm).

**Seed (`seal_seed.sql`):** đổi Prize từ per-track sang **event-wide top 3**: Event 1 (COMPLETED) đã trao Champion=Phoenix / 1st=Eagle / 2nd=Tiger (khớp final ranking); Event 2 **để trống** (demo auto-generate live). Cập nhật dòng audit `AWARD_PRIZE` cho khớp.

**Verify:** `mvnw -o compile` → `BUILD SUCCESS`.

---

## PHẦN 2 — Leaderboard rank per-track (chẩn đoán + sửa seed + FE)

**Triệu chứng user báo:** kỳ Spring, vòng loại đang rank **tất cả team chung**; muốn rank **theo từng track**, đội đi tiếp **xanh**, đội loại **đỏ**; final rank chung.

**Chẩn đoán (đọc code thật):**
- BE đã rank per-track đúng; `LeaderboardPage` đã group theo track.
- **Thủ phạm = dữ liệu seed**: `RoundResult` vòng loại Event 1 đánh rank **toàn cục 1..5** + `top_n_advance` global (r1=5, r2=2) → group theo track thì số rank nhảy cóc (#1,#4,#5) và **không đội nào bị loại**.

**Phần A — sửa seed (đúng theo điểm số có sẵn, khớp đội đã đi tiếp):**
- `RoundResult` r1/r2 → **rank per-track** (Web: Phoenix#1/Dragon#2/Falcon#3; Mobile: Tiger#1; AI: Eagle#1 | Semi Web: Phoenix#1/Dragon#2; …).
- `top_n_advance`: Prelim **5→2**, Semi **2→1** ⇒ tái hiện đúng Semi 4 đội, Final 3 đội; Falcon (Web #3) & Dragon (Web #2 ở semi) bị loại.
- Cập nhật audit log seed.

**Phần B — FE** (`LeaderboardPage.tsx`, `CoordScoringPage.tsx`): đội bị loại đánh dấu (eliminated = `!isFinal && topN!=null && !advanced`).

---

## PHẦN 3 — Re-seed MySQL local + verify

- Config: `seal_hackathon` @ localhost:3306, root / `TrangNhi2004`; service `MySQL80` đang chạy; `mysql.exe` ở `C:\Program Files\MySQL\MySQL Server 8.0\bin`.
- `seal_schema.sql` tự `DROP DATABASE … CREATE … USE`; `seal_seed.sql` `USE` + insert ⇒ chạy **schema → seed** (reset sạch).
- Nạp 2 file qua `cmd /c "mysql -u root < file"` (MYSQL_PWD env), cả hai exit 0.
- **Verify SQL:** Prelim Web = Phoenix#1/Dragon#2/Falcon#3(loại), Mobile Tiger#1, AI Eagle#1; Semi Web Phoenix#1/Dragon#2(loại); Prize event-wide Champion Phoenix / 1st Eagle / 2nd Tiger. ✔

---

## PHẦN 4 — Loạt tinh chỉnh UI leaderboard (nhiều vòng review)

User chê xấu nhiều lần → sửa lặp:

1. **Đỏ → chìm/mờ:** đội bị loại không bôi đỏ rực mà `opacity:0.5` + nền đỏ rất nhạt (0.04) + rank `textMuted` + badge xám "Eliminated".
2. **Bỏ dòng thừa:** xóa banner "Cut-off: Top N · X of Y advance" + dòng kẻ "▲ cut-off line ▼" (màu sắc đã nói lên rồi). Dọn biến/`Fragment` không dùng.
3. **Final bỏ cột Track:** final rank chung nên cột Track vô nghĩa → ẩn header + cell khi `isFinalRound`; giữ cột Status.
4. **Status cho Final:** final `topNAdvance` null nên status cũ ra "—" hết. Chốt (AskUserQuestion) **Winner/Finalist đơn giản**: top 3 (hoặc topN nếu set) = "Winner", còn lại = "Finalist".
5. **Fix bug lọc track ở Final:** dropdown Track vẫn lọc nhầm bảng final → reorder để tính `isFinalRound` trước, `activeTrackFilter = isFinalRound ? "" : trackFilter`, và **ẩn dropdown Track** khi final.
6. **Redesign theo pattern leaderboard chuẩn (podium):**
   - `const MEDAL` (gold `#FFD24A` / silver `#CBD5E1` / bronze `#E0915A`, kèm soft/glow/icon).
   - **Podium top 3** (card riêng): xếp 2–1–3, champion ở giữa cao hơn + glow vàng, huy chương tròn có số hạng, tên + điểm + nhãn Champion/Runner-up/3rd; đội mình có ★.
   - Bảng **"Full Standings"** bên dưới: top 3 có huy chương tròn ở cột Rank + tông kim loại; còn lại trung tính.
7. **Màu header track (theo ảnh user gửi):** nền gradient xanh của header **trùng** với nền row "Advanced" → **bỏ nền**, để divider sạch: thanh bar xanh (glow) + tên track **chữ trắng sáng đậm** + pill đếm team trung tính.

> Tất cả FE đều verify bằng `npx vite build` (exit 0). Lưu ý môi trường: `npx tsc --noEmit` fail do `tsconfig.json` dùng `baseUrl` (deprecated TS6) — không liên quan thay đổi.

---

## PHẦN 5 — Rà logic Top N + Enforcement + Tie-break

**User hỏi:** "config Top N đã đúng logic chưa?" → đọc `RoundService`, `RoundResultService`, `SubmissionService`.

**Kết luận rà soát:**
- ✅ Config (create/update) + rank per-track/global + `advanced` derived live: **đúng**.
- ⚠️ **Top N chưa được enforce ở submission** — `SubmissionService.submit()` chỉ check ACTIVE + approved + đúng event, **không** check advancement ⇒ đội bị loại vẫn nộp được vòng sau.
- ⚠️ Không xóa được Top N về null qua PUT (partial); không validate `≥1`.

**A1 — Enforce (`SubmissionService.java`):**
- Inject `RoundResultRepository`; thêm import `Comparator`.
- Trước khi lưu bài: tìm **vòng liền trước** (`max orderNumber < current`); nếu vòng đó **FINALIZED** và **có cutoff** → đội phải có `RoundResult.rankPosition <= cutoff`, nếu không (hoặc thiếu kết quả) → `BadRequestException("…did not advance…")`. Vòng đầu / chưa finalize / không cutoff → cho qua. Tính transitive.

**A2 — Tie-break (`RoundResultService.java`):**
- Thêm helper `rankingComparator(subByTeam)`: điểm desc; **bằng điểm → `submittedAt` sớm hơn xếp trên**; thiếu time xếp cuối.
- Thay 2 chỗ `sort` (final-global + per-track group) sang dùng comparator này.

**Làm rõ với user:** dấu vết đội trong 1 vòng = **bài nộp**; chặn nộp = đội không vào/không được lưu ở vòng sau. Gate chặn ở **thời điểm nộp** (bản ghi cũ trước khi finalize không tự xóa, nhưng luồng chuẩn không xảy ra).

---

## PHẦN 6 — D1/D2

**D2 — validate Top N ≥ 1:**
- `@Min(1)` cho `topNAdvance` ở `CreateRoundRequest` + `UpdateRoundRequest` (null vẫn hợp lệ = no cutoff).
- `RoundController.updateRound` **trước đó thiếu `@Valid`** trên `@RequestBody` → đã thêm (nếu thiếu thì @Min không chạy).

**D1 — cho phép xóa Top N về null:**
- Thêm field `clearTopNAdvance` (Boolean) vào `UpdateRoundRequest` + `UpdateRoundPayload` (FE `apiClient.ts`).
- `RoundService.updateRound`: `clearTopNAdvance==true` → set null; else có giá trị → set; else không đổi.
- **FE `CoordEventsPage.tsx`:** `rdTopN` đổi `number → number|null`; ô input **để trống = null** (placeholder "Empty = no cut-off"); create gửi `topNAdvance ?? undefined`; update gửi `clearTopNAdvance:true` khi null, else `topNAdvance`. (Fix kèm: trước đây edit round no-cutoff set ô về `0` — giờ `@Min(1)` sẽ reject `0` nên đổi hiển thị trống.)

**Verify:** BE `BUILD SUCCESS`, FE `vite build` exit 0.

---

## Tổng hợp file đã chạm

**BE — mới (Prize):** `entity/Prize.java`, `repository/PrizeRepository.java`, `dto/request/{CreatePrizeRequest,UpdatePrizeRequest,AutoGeneratePrizesRequest}.java`, `dto/response/PrizeResponse.java`, `service/PrizeService.java`, `controller/PrizeController.java`.

**BE — sửa:** `repository/RoundRepository.java` (finalRound lookup), `service/SubmissionService.java` (enforce gate), `service/RoundResultService.java` (tie-break), `service/RoundService.java` (clearTopN), `controller/RoundController.java` (@Valid), `dto/request/CreateRoundRequest.java` (@Min), `dto/request/UpdateRoundRequest.java` (@Min + clearTopNAdvance).

**DB:** `back-end/database scripts/seal_seed.sql` (Prize event-wide; RoundResult per-track; top_n_advance r1=2/r2=1; audit log).

**Postman:** `back-end/Postman/Postman_Full_Collection.json` (nhóm 17 PRIZES + var `prizeId`).

**FE:** `features/scoring/LeaderboardPage.tsx` (per-track display, dimmed eliminated, trim, final no-track, final status, track-filter fix, podium, MEDAL, header recolor), `features/scoring/CoordScoringPage.tsx` (dimmed eliminated), `features/events/CoordEventsPage.tsx` (D1 wiring), `shared/apiClient.ts` (clearTopNAdvance).

---

## Điểm còn mở / lưu ý cho phiên sau
- Màn **Coordinator (`CoordScoringPage`)** chưa áp podium/redesign như participant (mới đồng bộ phần "chìm" cho eliminated) — user chưa yêu cầu.
- Prize: chưa làm **FE** (màn Coordinator quản lý giải + trang "Vinh danh" public) và export CSV/giấy chứng nhận — mới xong BE+Postman+seed.
- Enforce + tie-break chỉ hiệu lực với **submission mới / finalize lại**; seed hiện không có cặp bằng điểm để demo tie-break.
- `npx tsc --noEmit` fail do `baseUrl` deprecated (TS6) — bypass `--ignoreDeprecations 6.0`; build production dùng `vite build`.
