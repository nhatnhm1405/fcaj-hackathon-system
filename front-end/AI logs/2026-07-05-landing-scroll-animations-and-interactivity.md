# AI Session Log — Landing page: marquee, scroll-reveal & interactive animations

**Date:** 2026-07-05
**Project:** SEAL – Software Engineering Hackathon Management System
**Branch:** `develop`
**Stack chạm tới:** Chỉ **Frontend** (React/Vite/TS) + 1 file CSS global. Không đụng BE/DB.
**Phạm vi:** Toàn bộ nằm trong landing page — `features/landing/LandingPage.tsx` + `styles/globals.css`.

**Cách làm xuyên suốt:** Q&A liên tục, mọi hạng mục lớn đều **hỏi chốt qua AskUserQuestion trước khi code** (theo yêu cầu của user). Verify từng chặng bằng `npx tsc -p tsconfig.app.json --noEmit`, và cuối phiên `npx vite build` (exit 0).

> Đầu phiên user yêu cầu lấy context dự án qua `back-end/Postman/Postman_Full_Collection.json` và `docs/documents/ProjectRequirements.md` trước khi bắt đầu — đã đọc để nắm nghiệp vụ SEAL (event/round/track/team/submission/scoring…). Task thực tế của phiên là **UI landing page**, không chạm nghiệp vụ đó.

---

## PHẦN 1 — Yêu cầu ban đầu

User: *"thanh bar chạy chữ thì giảm đáng kể tốc độ; và landing page fade dần mỗi lần scroll (thông tin hiện dần khi cuộn)."*

Định vị code:
- **Marquee** = component `InnovationStrip` (LandingPage.tsx). Tốc độ ở inline `animation: "dataFlow 16s linear infinite"`.
- **Chưa có** hiệu ứng reveal-on-scroll cho các section.

**Phát hiện quan trọng khi khảo sát:**
1. Keyframe `dataFlow` là **dùng chung** — class `.data-flow` ở `globals.css` cũng dùng (chạy 3s). ⇒ Không sửa keyframe; chỉ đổi ở inline của marquee.
2. `dataFlow` thực chất là "quét ngang + mờ 2 mép" (`translateX(-100%)→200%` kèm opacity), **không phải vòng lặp liền mạch** ⇒ khi chạy chậm sẽ thấy khoảng trống trôi qua, hơi giật.
3. `GallerySection` có **lightbox `position: fixed; inset:0; zIndex:1000`** render **bên trong** section. ⇒ Nếu bọc section trong wrapper có `transform`, containing-block đổi → **lightbox vỡ**. Đây là ràng buộc xuyên suốt cả phiên.

---

## PHẦN 2 — Chốt qua AskUserQuestion (đợt 1)

| Câu hỏi | User chọn |
|---|---|
| Tốc độ marquee mới (đang 16s) | **60s (rất chậm, êm)** |
| Kiểu marquee | **Vòng lặp liền mạch** (không giữ kiểu quét-mờ) |
| Reveal kích hoạt kiểu gì | **Hiện 1 lần rồi giữ** (reveal-once) |
| Hero + reduced-motion | **Bỏ qua Hero** (Hero hiện ngay) + **Tôn trọng prefers-reduced-motion** |

---

## PHẦN 3 — Triển khai đợt 1 (marquee chậm liền mạch + fade-on-scroll)

**globals.css:**
- Thêm keyframe **mới** `marqueeScroll` (`translateX(0) → -50%`) + class `.seal-marquee { animation: marqueeScroll 60s linear infinite; }`. Vì markup marquee đã nhân đôi items (`[...items, ...items]`), dịch −50% = đúng 1 bản copy ⇒ **liền mạch, không khoảng trống**. Không đụng `dataFlow`.
- Block `@media (prefers-reduced-motion: reduce)`: tắt `.seal-marquee`; ép `.seal-reveal` về opacity 1 / transform none / transition none.

**LandingPage.tsx:**
- Marquee: đổi inline `animation` → `className="seal-marquee"` (16s → **60s**).
- Component **`Reveal`** (IntersectionObserver, reveal-once, `io.disconnect()` sau lần đầu). Bọc mọi section **trừ Hero**.
- **Chốt an toàn lightbox:** khi đã hiện dùng `transform: "none"` (không phải `translateY(0)`) và **`willChange: "opacity"`** (không phải transform) — cả `transform` lẫn `will-change:transform` đều tạo containing-block phá `position:fixed`. Vì gallery luôn reveal xong trước khi user kịp mở ảnh, lúc đó transform đã `none` ⇒ lightbox an toàn.

Verify: tsc sạch (lỗi `TeamViewPage.tsx` `onKeyDown` là **có sẵn từ trước**, không liên quan — lặp lại ở mọi lần verify của phiên).

---

## PHẦN 4 — User muốn "càng scroll càng reveal ấn tượng"

User: *"animation chưa ấn tượng với người mới; càng lướt xuống càng reveal nhiều thứ đặc biệt; tự đề xuất thêm gì mới cũng được."* + xác nhận: reveal-once nghĩa là hết trang sẽ hết chuyển động.

Đề xuất 4 gói, chốt qua AskUserQuestion (đợt 2):

| Gói | User chọn |
|---|---|
| 1. Reveal escalate (stagger + hướng xen kẽ) | ✅ |
| 2. Decode chữ cho tiêu đề (hiệu ứng "giải mã" hacker) | ✅ |
| 3. Dải số liệu đếm tăng (component mới, từ API) | ❌ (không chọn) |
| 4. Nền luôn sống (scroll-progress + parallax + timeline tự vẽ) | ✅ |
| Lặp reveal | **Giữ reveal-once** |

**Triển khai đợt 2:**
- **`Reveal` nâng cấp**: thêm prop `direction` (`up/down/left/right/scale`) + `delay`. Compose xen kẽ hướng: Innovation(up) · Events(left) · Timeline(up) · Sponsors(right) · FAQ(up) · CTA(scale).
- **Stagger**: `FeatureCard`/card Features hiện lần lượt (delay `i×90ms`); 6 ảnh Gallery so-le (`0→400ms`) qua observer trên lưới.
- **`DecodeText` / `DecodeHeading`**: tiêu đề "giải mã" từ ký tự nhiễu → chữ thật khi vào view, giữ gradient (lồng trong `GradientText`, kế thừa `-webkit-text-fill-color`). **Không sửa `SectionHeader`** (dùng chung toàn app) — thay bằng `DecodeHeading` tại các section landing; Timeline `<h2>` bọc `DecodeText`.
- **`ScrollProgressBar`**: thanh neon đỉnh trang (zIndex 200 > navbar 100), theo % đọc.
- **Parallax** đốm sáng nền Hero (`useScrollY`, translateY×0.18).
- **Timeline tự vẽ**: đường fill 0→mốc active (1.2s) khi section vào view (cả desktop width lẫn mobile height).
- Reduced-motion: decode/parallax/timeline-draw tắt bằng runtime `prefersReducedMotion()`; marquee/reveal tắt bằng CSS.

Dọn import thừa: gỡ `SectionHeader` khỏi import (không còn dùng). Verify tsc sạch.

---

## PHẦN 5 — Đợt 3: thêm interactive + escalation (user chọn CẢ 8)

Đề xuất 2 nhóm, chốt qua AskUserQuestion (đợt 3) — user chọn **toàn bộ 8**:

**Nhóm Escalation:** Cường độ tăng dần · Hero scroll-away · Scanline sweep khi reveal · Reveal đặc biệt theo section.
**Nhóm Interactive:** Cursor spotlight · 3D tilt card · Magnetic buttons · Marquee/Gallery phản hồi hover.
(Vẫn giữ reveal-once.)

**Triển khai từng cái:**

1. **Cường độ tăng dần** — `AmbientScrollLayer`: lớp phủ `position:fixed` blend `screen`, hue dịch **lá→dương→cyan→tím** (`140 + depth×135`) và glow đậm dần (`0.04 → ~0.14`) theo độ sâu scroll.
2. **Hero scroll-away** — nội dung foreground Hero: `opacity: 1−heroP`, `translateY(heroP×−40)`, khóa `pointerEvents` khi `heroP>0.85` (heroP = min(1, scrollY/600)).
3. **Scanline sweep** — `Reveal` thêm prop `sweep`: overlay đường neon chạy dọc 1 lần (keyframe `sealSweep`, top 0→100% + fade). Bật cho Events/Timeline/Sponsors/FAQ/CTA.
4. **Reveal đặc biệt** —
   - Gallery: `GalleryPhoto` chuyển từ trượt sang **glitch-in** (keyframe `glitchIn`: clip-path wipe + hue/translate jitter, `animation-delay` so-le).
   - Features: `FeatureCard` **vẽ viền** — top edge `scaleX 0→1` (delay+150ms), góc bracket `scale 0.2→1` (delay+320ms).
5. **Cursor spotlight** — `CursorSpotlight`: vầng sáng radial theo `pointermove` (rAF-throttle), fixed, blend screen, **tự tắt trên `(hover: none)`** (thiết bị cảm ứng).
6. **Magnetic buttons** — `Magnetic` wrapper: dịch phần tử về phía chuột (`translate(mx×0.3, my×0.3)`); bọc 2 nút Hero + 2 nút CTA.
7. **3D tilt** — `Tilt` wrapper (`perspective + rotateX/Y`, imperative không re-render). Dùng cho card Features (nhúng trong `FeatureCard`, `±7°`) và 2 card Events (`max={6}`).
8. **Hover phản hồi** — marquee `animationPlayState = paused` khi hover; ô Gallery lớn (idx 01) thêm class `seal-img-glitch` khi hover (keyframe `imgGlitch`, giữ zoom 1.06).

**Kiểu event:** dùng `type PointerEvent as ReactPointerEvent` từ "react" cho handler JSX (Tilt/Magnetic/FeatureCard), trong khi `CursorSpotlight` dùng DOM `PointerEvent` cho listener `window`.

**Reduced-motion (đầy đủ):** mở rộng `@media (prefers-reduced-motion)` để tắt `.seal-sweep / .seal-glitch-in / .seal-img-glitch`; tilt/magnetic/hero-parallax kiểm tra runtime `prefersReducedMotion()` và no-op.

**Bảo toàn an toàn lightbox:** Gallery & Features **không** bọc `Reveal` (tự quản stagger nội bộ); `AmbientScrollLayer`/`CursorSpotlight`/`ScrollProgressBar` fixed ở **root** (ngoài mọi wrapper transform). Lightbox không bị ảnh hưởng.

Verify: `npx tsc` sạch (LandingPage/globals). **`npx vite build` exit 0** (chỉ còn cảnh báo chunk >500kB có sẵn, không liên quan).

---

## Chi tiết kỹ thuật đáng nhớ (gotchas)

- **`transform` + `will-change:transform` tạo containing-block** cho `position:fixed`. Đây là lý do `Reveal` settle về `transform:"none"` và chỉ `will-change:opacity`; và Gallery/Features không bọc Reveal. Nếu sau này bọc lại → lightbox Gallery sẽ vỡ.
- **Keyframe `dataFlow` ≠ `marqueeScroll`**: giữ tách biệt cố ý (`dataFlow`/`.data-flow` còn dùng chỗ khác với hiệu ứng quét-mờ).
- **`SectionHeader` là component chung** — không sửa; landing dùng `DecodeHeading` riêng.
- **Grid Gallery đặt ô tường minh** (`gridColumn/gridRow`) ⇒ không thể bọc mỗi ảnh trong `Reveal` (sẽ phá layout); thay vào đó truyền `revealed/revealDelay` vào `GalleryPhoto`. Grid Features auto-flow nên trước đó từng bọc được, sau gộp hẳn vào `FeatureCard`.

---

## Tổng hợp file đã chạm

**FE sửa (2 file):**
- `src/features/landing/LandingPage.tsx` — thêm primitives: `prefersReducedMotion`, `useScrollY`, `useInView`, `Reveal` (direction/delay/sweep), `DecodeText`, `DecodeHeading`, `ScrollProgressBar`, `AmbientScrollLayer`, `CursorSpotlight`, `Tilt`, `Magnetic`, `FeatureCard`; sửa `HeroSection` (parallax + scroll-away + Magnetic), `InnovationStrip` (marquee 60s + hover-pause), `EventsSection` (DecodeHeading + Tilt), `TimelineSection` (DecodeText + tự vẽ track), `GallerySection`/`GalleryPhoto` (DecodeHeading + glitch-in + hover glitch), `SponsorsSection`/`FAQSection` (DecodeHeading), `CTASection` (Magnetic), `LandingPage` compose (lớp nền/spotlight/progress + hướng reveal + sweep); gỡ import `SectionHeader`.
- `src/styles/globals.css` — keyframes mới `marqueeScroll`, `sealSweep`, `glitchIn`, `imgGlitch` + class tương ứng; mở rộng `@media (prefers-reduced-motion: reduce)`.

**BE/DB:** không chạm.

---

## Trạng thái & việc còn mở

- **Xong** cả 3 đợt (marquee + fade-on-scroll → escalation/decode/nền-sống → 8 hiệu ứng interactive/escalation). tsc sạch, `vite build` exit 0. Không phát sinh lỗi mới (lỗi `TeamViewPage.tsx` là tiền tồn).
- **Chưa xem trực quan trên trình duyệt** — nhiều hiệu ứng (tilt, cursor spotlight, magnetic, hover-glitch) cần tương tác thật; đã đề xuất chạy `npm run dev` để user tự cảm nhận hoặc mình chụp màn hình (chờ user quyết).
- **Điểm cần user review khi xem thật** (dễ "quá đà" khi cộng dồn):
  - `AmbientScrollLayer` + `CursorSpotlight` cùng blend `screen` → có thể làm chữ sáng hơn ở cuối trang; nếu chói → giảm `intensity`/bỏ blend hoặc tắt bớt 1 lớp.
  - `sweep` đang bật 5 section → nếu thấy lặp, để lại 2–3.
- **Thông số tinh chỉnh nhanh (1 dòng/cái):** marquee 60s · decode 0.9s · stagger Features 90ms / Gallery 80ms · parallax 0.18× · timeline-draw 1.2s · hero scroll-away qua 600px · tilt ±7° (Events ±6°) · magnetic 0.3× · spotlight bán kính 260px · ambient glow tối đa ~0.14.
- **Gói chưa làm (user không chọn):** dải số liệu đếm tăng ("By the Numbers") từ API thật.

---

## Ghi chú vận hành
- Verify FE: `cd front-end/src/seal-web && npx tsc -p tsconfig.app.json --noEmit` (dùng `tsconfig.app.json`, KHÔNG phải `-p .`). Build: `npx vite build`.
- Memory đã lưu: `landing-animation-system` (primitives + gotcha lightbox/reduced-motion) để tái sử dụng phiên sau.
