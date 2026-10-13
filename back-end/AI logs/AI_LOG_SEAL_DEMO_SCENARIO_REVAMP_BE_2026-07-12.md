# AI LOG — Demo Scenario Revamp (BACKEND) — 2026-07-12

> Làm giàu data demo S2/S3 (giữ nguyên 4 kịch bản S0–S3), đúng nghiệp vụ 2 vòng.
> Nhánh: `NhatNHM-grouping-setup-roster-rules`.
> Nối tiếp: [[AI_LOG_SEAL_RUN_DEMO_SCRIPT_BE_2026-07-11]].

---

## 1. Bối cảnh

`seal_seed.sql` cũ (tay) giàu hơn Java seeder nhiều (3 mùa, 43 user, 17 team…). Bàn đủ
hướng (S4 full / dump SQL) rồi **chốt: giữ 4 kịch bản cho lành**, chỉ **nâng data S2/S3**
cho track đỡ trống + luồng thi đúng nghiệp vụ. Tên đội/người đổi cho vui (bóng đá 😎).

## 2. Thay đổi `DemoScenario`

**Quy mô:** 4 track (Web / AI / Education Tech / Social Impact), phân bổ **4-4-4-3 = 15 đội**
(hằng `TEAMS_PER_TRACK`). Tên đội = CLB (`CLUBS`), tên người chơi = cầu thủ (`PLAYERS`),
lặp vòng nếu thiếu.

**Luồng 2 vòng — khớp `RoundResultService`:**
- **Vòng loại** (không final): xếp hạng **theo từng track**, `topNAdvance=2` → **top 2 mỗi
  track lên** = 8 finalist. (Trước đây seeder xếp toàn cục cho vòng loại → **sai** so với
  production; đã sửa cho rank per-track.)
- **Chung kết** (final): 8 đội xếp hạng **toàn cục**, **top 3 giải**.

**Strength tất định:** gán `strength = totalTeams − (seed*numTracks + trackIdx)` → mọi #1
seed của các track xếp trên mọi #2 seed → **top 3 giải rơi vào 3 track khác nhau**
(Nhất Arsenal/Web · Nhì Liverpool/AI · Ba PSG/Education). 5 finalist còn lại = "vào chung
kết không giải"; 7 đội loại vòng loại = participated. Điểm là hàm thuần → seed lại y hệt.

**S1 (grouping demo):** ngoài 2 solo + 1 pair, thêm **3 registrant teamless** (student
approved+active, không team) để demo tính năng gom người lẻ mới ([[AI_LOG_SEAL_LEFTOVER_GROUPING_TEAMLESS_REGISTRANTS_BE_2026-07-11]]).

## 3. Dọn dẹp

- Bỏ knob `teams-per-track` (giờ phân bổ cố định 4-4-4-3): `DemoSeeder` bỏ `@Value` +
  đổi `seed(scenario)`; xóa `app.seed.teams-per-track` khỏi `application.properties`;
  `run-demo.ps1` bỏ tham số `-TeamsPerTrack`.
- Sửa 1 lỗi lệch tham số `fx.user(...)` do đổi tên account (name rỗng / userType sai).

## 4. Lưu ý demo
- Grouping chỉ chạy khi event **SETUP**, mà S1 là **OPEN** → coordinator phải chuyển
  OPEN→SETUP mới bấm gom. Và giờ transition đó **bị chặn nếu còn team PENDING**
  (Real Madrid trong S1) → phải duyệt/từ chối trước ([[AI_LOG_SEAL_TEAM_LIFECYCLE_RULES_BE_2026-07-12]]).

## 5. File
**Sửa:** `config/seed/DemoScenario.java`, `config/seed/DemoSeeder.java`,
`resources/application.properties`, `run-demo.ps1`
