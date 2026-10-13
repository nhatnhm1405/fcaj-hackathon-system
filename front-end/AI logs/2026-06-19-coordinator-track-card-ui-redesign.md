# AI Log — Thiết kế lại Track Card (Coordinator · tab Tracks)

**Ngày:** 2026-06-19
**Phạm vi:** Frontend React (`front-end/src/seal-web`) — thuần UI/UX, **không đụng backend**
**Nhánh:** `develop`
**Màn hình:** Coordinator → **Events** → chọn 1 event → tab **Tracks** → header mỗi track card
**Trạng thái:** Hoàn tất, đã verify (vitest 28/28 · vite build OK · tsc sạch ở file đã sửa)

> Phiên này chỉ tinh chỉnh phần hiển thị (presentation) của track card. Mọi ràng buộc nghiệp vụ
> (luật ≥2 đội/track, gate START EVENT, xoá track → unassign…) **giữ nguyên** ở backend.
> Tài liệu ghi lại **làm gì / vì sao / làm thế nào**, kèm quá trình Q&A chốt thiết kế.

---

## Bối cảnh

Trong tab **Tracks**, mỗi track hiển thị một card: tên + mô tả ở trái, và một cụm thông tin/thao tác
ở góc phải. Trước phiên này, góc phải gom **3 badge + 1 nút** (`PixelBadge`/`PixelButton`):

```
[2 TEAMS]  [⚠ MIN 2]  [2 SLOTS]  [REMOVE]
```

Người dùng phản hồi: **chữ nhỏ, nhìn rối, không rõ ràng**, các thành phần
`TEAM / ⚠ MIN 2 / SLOTS / REMOVE` sắp xếp chưa hợp lý.

### Chẩn đoán vấn đề (gốc)

1. **Chữ quá nhỏ** — `PixelBadge` cố định `fontSize: 10` trên nền tối → khó đọc. Tên track 13px gần
   bằng badge nên thiếu "dòng chính" rõ ràng.
2. **Trộn 3 loại ngữ nghĩa** thành các chip nhìn ngang hàng: `TEAMS` = số đếm, `MIN 2` = trạng thái
   hợp lệ, `SLOTS` = sức chứa. Người đọc phải tự phân loại.
3. **Dư thừa**: track thiếu đội → `1 TEAM` (đỏ) **và** `⚠ MIN 2` cùng nói một việc.
4. **Trộn hành động phá huỷ với thông tin**: `REMOVE` (danger) nằm cạnh các badge chỉ-đọc → dễ bấm nhầm.
5. **Lỗi số nhiều + khái niệm khó**: `1 SLOTS` (đúng phải là `1 SLOT`); và "SLOTS" (capacity đóng băng
   lúc vào SETUP) **khác** "Max/track" ở dải Overview → dễ nhầm.

---

## Quá trình Q&A & quyết định

Đây là yêu cầu kiểu "thảo luận để ra hướng tốt nhất", nên thiết kế được chốt qua nhiều vòng hỏi-đáp:

| Quyết định | Lựa chọn cuối |
|------------|---------------|
| Hướng tổng thể | **Hướng A** — số đội là thông tin chính, trạng thái tách riêng |
| Hiển thị `SLOTS`/capacity | **Bỏ khỏi card** (đã có "Max / track" ở Overview → tránh nhầm capacity vs max) |
| Phạm vi tăng cỡ chữ | **Bump `PixelBadge` dùng chung** (dễ đọc hơn toàn app) |
| Bố cục các ô | **Mỗi thành phần một ô có khung**; REMOVE **hiện luôn** (không giấu trong menu `⋯`) |
| Hiển thị số đội | **Phương án A — `2 teams`** (số đậm, chữ "teams" mờ) |
| Vị trí status | **Xuống 1 hàng** so với ô `2 teams` và `REMOVE` |
| Text trạng thái | **Bỏ icon**, **viết hoa chữ đầu**: `Ready` / `Needs 2 teams to run` |
| Màu khung | **Theo trạng thái** (xanh = đủ đội, đỏ/vàng = thiếu) |

> Ghi chú: bản nháp giữa chừng từng dùng menu `⋯` (kebab) để giấu REMOVE; sau phản hồi của người dùng
> đã **bỏ kebab**, đưa REMOVE ra ngoài thành ô hiện luôn.

---

## Thiết kế cuối cùng

Góc phải mỗi track (chỉ hiện **từ SETUP trở đi**, khi roster đã đóng băng) gồm **2 hàng, canh phải**:

```
Track đủ đội (khung XANH):
AI Solution                          [ 2 teams ]  [ REMOVE ]
San pham ung dung AI/ML              [ Ready ]

Track thiếu đội (khung ĐỎ / VÀNG):
Green Tech                           [ 1 team ]  [ REMOVE ]
Giai phap cong nghe xanh            [ Needs 2 teams to run ]
```

- **Hàng 1:** ô `[ N teams ]` (số in đậm 13px, chữ "team(s)" mờ) + ô `[ REMOVE ]` (viền đỏ, có hover).
  REMOVE chỉ render trong **SETUP**.
- **Hàng 2:** ô trạng thái — không icon, viết hoa chữ đầu: `Ready` (xanh) hoặc `Needs 2 teams to run` (vàng).
- **Mỗi ô có khung riêng** (`TrackChip`): viền + nền mờ + chữ đổi màu theo trạng thái.
- **Viền trái card** dày 3px đổi màu xanh/đỏ theo tính hợp lệ (giữ từ bản trước).
- Ngoài SETUP (IN_PROGRESS/COMPLETED): chỉ còn ô số đội + ô trạng thái (read-only, không có REMOVE).
- Trước SETUP (DRAFT/OPEN): không hiện cụm này; viền trái trung tính.

Tính hợp lệ vẫn dựa trên pure function `isTrackValid(teamCount) = teamCount >= MIN_TEAMS_PER_TRACK (=2)`
trong `features/events/trackStats.ts`, đồng nhất với gate backend `requireSetupComplete`.

---

## Files thay đổi

| File | Thay đổi |
|------|----------|
| `src/features/events/CoordEventsPage.tsx` | • Thêm component **`TrackChip`** (ô khung, màu theo `tone`, **không** uppercase nên đọc được "Ready"/"Needs…").<br>• Thay khối góc phải sang bố cục 2 hàng có khung (ô số đội + REMOVE inline + ô trạng thái).<br>• **Bỏ** component menu `⋯` `TrackActionsMenu` (bản nháp), REMOVE ra inline.<br>• **Bỏ** badge `SLOTS`; gộp count + "MIN 2" thành (ô số đội màu trạng thái + ô status chữ rõ).<br>• Viền trái card 3px theo trạng thái.<br>• Bump `TeamRow` (tên đội 12→13, dòng thành viên 11→12). |
| `src/shared/components/PixelComponents.tsx` | **`PixelBadge`**: `fontSize 10 → 11`, `padding 2px 8px → 3px 9px`, `letterSpacing 0.08em → 0.06em`. Ảnh hưởng mọi badge toàn app (status event, rounds, criteria, audit, events list…) — dễ đọc hơn, build qua sạch, không vỡ layout. |

---

## Điểm thiết kế đáng nhớ

1. **Không dùng `PixelBadge` cho ô status** — vì `PixelBadge` ép `text-transform: uppercase` (sẽ ra
   "READY"/"NEEDS…"). Người dùng muốn viết hoa chữ đầu kiểu câu → tự dựng `TrackChip` để kiểm soát.
2. **Tách hành động khỏi thông tin**: REMOVE là ô hành động (viền đỏ, hover, con trỏ pointer) đặt cạnh
   ô số đội; ô trạng thái xuống hàng dưới → mắt phân biệt rõ "đọc" vs "bấm".
3. **Màu khung = tín hiệu trạng thái**: số đội xanh/đỏ, status xanh ("Ready")/vàng ("Needs 2 teams to run"),
   REMOVE luôn đỏ. Không cần icon vẫn đọc được trạng thái.
4. **Bỏ `SLOTS` khỏi card** để loại điểm nhầm capacity (cố định lúc vào SETUP) ↔ Max/track (tính live ở
   Overview). Hai khái niệm khác nhau, không nên đặt cạnh nhau trên cùng card.
5. **Bump cỡ chữ ở 2 cấp**: cục bộ (TrackChip/TeamRow trong card này) + dùng chung (`PixelBadge` toàn app).
6. **Chỉ là lớp trình bày** — `showTrackStats` (SETUP trở đi) và `isSetup` (chỉ SETUP) quyết định hiện
   cụm/REMOVE; mọi enforce thật vẫn ở backend.

---

## Kiểm chứng

| Hạng mục | Lệnh | Kết quả |
|----------|------|---------|
| FE unit test | `npx vitest run` | **28/28** pass (trackStats 16, permissions 7, ConfirmDialog 5) |
| FE build | `npx vite build` | **Thành công** (exit 0; chỉ còn cảnh báo chunk-size có sẵn) |
| Typecheck | `npx tsc -p tsconfig.app.json --noEmit` | Không lỗi ở file đã sửa. *Lưu ý:* còn lỗi config có sẵn
`tsconfig.app.json(17): Option 'baseUrl' is deprecated (TS5101)` — không thuộc phạm vi phiên này. |
| Dead code | `grep TrackActionsMenu src/` | Không còn tham chiếu (kebab đã gỡ sạch). |

## Việc nên kiểm thử thủ công (khó test tự động)
Phần nhìn + drag-drop chưa có e2e test. Nên chạy `npm run dev` → Coordinator → Events → tab Tracks và thử:
1. Track đủ đội (≥2): khung **xanh**, ô `[ N teams ]` xanh, ô `[ Ready ]` xanh, viền trái xanh.
2. Track thiếu đội (<2): khung **đỏ/vàng**, ô số đội đỏ, ô `[ Needs 2 teams to run ]` vàng, viền trái đỏ.
3. Ở SETUP: ô `[ REMOVE ]` hiện luôn cạnh ô số đội, hover đổi nền; bấm → dialog xác nhận xoá track.
4. Ngoài SETUP (IN_PROGRESS/COMPLETED): chỉ còn ô số đội + status, **không** có REMOVE.
5. Badge ở các tab khác (Rounds/Criteria/Audit) và Events list to hơn một chút, không vỡ layout.
