# ★ AI Session Log — Revert toàn bộ PR #256 (`hoangnhat-draft`) khỏi `develop` · FULL-STACK

> **★ IMPORTANT — ca hiếm của dự án:** đây là lần đầu phải **gỡ bỏ một PR đã merge sâu** vào `develop`
> (không phải sửa tiến lên mà là lùi lại). Đọc kỹ trước khi làm thao tác tương tự. Bài học cốt lõi nằm ở
> PHẦN 4 (phụ thuộc ẩn của PR sau lên code bị gỡ).

**Date:** 2026-06-26
**Project:** SEAL – Software Engineering Hackathon Management System
**Branch làm việc:** `revert/hoangnhat-draft-256` (tạo từ `develop`), mở PR ngược vào `develop`
**Người yêu cầu:** KTrangg (FE)
**Tính chất:** Revert / history-aware rollback — KHÔNG `reset`, KHÔNG force-push nhánh chung.

---

## PHẦN 0 — VẤN ĐỀ & YÊU CẦU

- **Triệu chứng:** sau khi PR **#256** (`hoangnhat-draft`) merge vào `develop`, **toàn bộ progress frontend trước đó bị mất** — các trang team đã làm (LandingPage…) bị ghi đè bằng bản cũ/khác của nhánh draft.
- **Yêu cầu chốt qua nhiều vòng Q&A:**
  1. **Gỡ sạch 100% code của `hoangnhat-draft`**, kể cả phần backend/auth/docker/deploy — vì "những phần trong đó cũng không đúng".
  2. **Không được mất** các PR merge *sau* #256 (#259, #262, #260, #263).
  3. Commit **không kèm** trailer `Co-Authored-By`.
  4. **Bỏ** commit `f0a0a01` (fix import ScoringService — phát sinh do tích hợp với hoangnhat).
  5. Local đang dở hôm nay: được phép stash/discard.
  6. Nơi cần sửa thật là **GitHub (`origin/develop`)**, không chỉ local.

---

## PHẦN 1 — KHẢO SÁT LỊCH SỬ (đo đạc, không đoán)

Merge cần gỡ: `d90f49d` — *"Merge pull request #256 from nhatnhm1405/hoangnhat-draft"*
- 2 parent: `d5497c6` (develop trước merge) · `4fc4ec0` (tip hoangnhat-draft).
- hoangnhat-draft gồm 5 commit: `13d9688` docker workflow · `f273a78` OAuth redirects · **`1a563ca` "harden event scoring and system logs"** · `2225f47` tests · `4fc4ec0` FE pages + system log UI.

Lịch sử first-parent của `develop` *sau* #256:
```
ab57362 #263 db-scripts-refactor
f0a0a01 fix ScoringService import   (commit trực tiếp — sẽ bị bỏ)
53c7730 #260 criteria-templates
3cc788f #262 fix-login-double-session
7d1da6b #259 ai-judge-repo-analysis
d90f49d #256 hoangnhat-draft        ← GỠ cái này
```
→ Merge nằm **sâu dưới 4 PR + 1 commit**. `develop == origin/develop == ab57362` (đồng bộ, là nhánh chung).

**Nhánh các PR sau (sau `git fetch --prune` — tất cả CÒN trên GitHub):**
| Nhánh | Chứa `d90f49d`? | Ghi chú |
|---|---|---|
| `NhatNHM-fix-login-double-session` (#262) | **Không** | fork sạch từ `d5497c6` |
| `TrangNHK-ai-judge-repo-analysis` (#259) | Có | đã "merge develop into branch" |
| `KhanhNLH-criteria-templates-and-scoring` (#260) | Có | nt |
| `NhatNHM-db-scripts-refactor` (#263) | Có | nt |

→ Vì 3/4 nhánh đã có hoangnhat trong ancestry, hướng **reset + re-merge sẽ kéo hoangnhat quay lại** ⇒ loại.

---

## PHẦN 2 — QUYẾT ĐỊNH: `revert -m 1`, KHÔNG `reset`

**Điểm mấu chốt giải toả lo lắng "mất commit sau":**
- `git revert -m 1 d90f49d` = tạo **1 commit mới** trừ ngược diff hoangnhat. Các commit #259/#262/#260/#263 **vẫn nguyên trong lịch sử** → không có gì để "cứu".
- `git reset` mới thực sự vứt commit + buộc **force-push nhánh chung** (ảnh hưởng cả team). ⇒ loại.

Bảng so sánh đã trình bày cho user; chốt **revert** + đưa lên qua **nhánh + PR** để team review.

---

## PHẦN 3 — DRY-RUN trong WORKTREE TÁCH BIỆT (không đụng local)

`git worktree add --detach <scratchpad> origin/develop` rồi `git revert -m 1 --no-commit d90f49d`:
- **2 file conflict:** `ScoringService.java`, `AiJudgeAssistantService.java`.
- **86 file bị xóa** = "đảo" hoangnhat (bộ `shared/components/ui/*` shadcn, `AuthPage/DashboardHome/JudgePage/*Section`, `OAuth2LoginFailureHandler`, `Dockerfile`, `docker-compose.yml`, `Caddyfile`, `.github/workflows/deploy.yml`).
- **29 file modified** = khôi phục bản FE của team (LandingPage, DashboardLayout, AdminEventsPage…).
- **4 file restore** = hoangnhat đã xóa (3 AI logs FE + `fpt-logo.png`).
- **0 file của #259/#260 bị đụng nhầm** (verify: `RepoDigest`, `GitHubRepoService`, `ScoringCriteriaRepository`, `RoundResultService`… an toàn).

**Kiểm chứng "đảo FE khép kín":** mọi file import `components/ui/*` và `ImageWithFallback` đều chính là file do hoangnhat thêm ⇒ xóa cả cụm cùng lúc, **không có file sống sót nào vỡ import**. Dọn worktree sau khi xong.

---

## PHẦN 4 — ★ BÀI HỌC CỐT LÕI: PHỤ THUỘC ẨN #259 → hoangnhat

Sau khi resolve 2 conflict (giữ import tường minh của #260 ở `ScoringService`; giữ khối #259 ở `AiJudgeAssistantService`), **compile main BÁO LỖI** `cannot find symbol`: `Set`, `ROLE_EVENT_COORDINATOR`, `ROLE_JUDGE`, `SUBMISSION_NOT_ASSIGNED_MESSAGE`, `ForbiddenException`, `submissionRepository.findBySubmissionIdAndJudgeId(...)`.

**Truy nguồn:** tất cả các symbol đó do commit **`1a563ca` (hoangnhat)** thêm vào `AiJudgeAssistantService` + `SubmissionRepository` — một lớp **kiểm tra quyền truy cập submission** (`findReadableSubmission`/`hasAuthority`). Trong khi đó **#259 (ai-judge-repo) viết khối phân tích GitHub repo ĐÈ LÊN** và *dùng nhờ* submission mà lớp auth của hoangnhat fetch.

> ⇒ Đây chính là loại phụ thuộc mà revert **không** tự phát hiện được — diff sạch nhưng **compile gãy**.
> Một revert của merge **chỉ đúng về mặt văn bản, không đảm bảo build**. **Bắt buộc compile + test sau revert.**

**Cách gỡ sạch mà vẫn giữ #259 chạy được** (đúng yêu cầu "gỡ hết hoangnhat"):
1. Lấy bản `develop` đầy đủ của `AiJudgeAssistantService` (đang compile được) làm nền (`checkout --ours`).
2. Cắt sạch lớp auth của hoangnhat:
   - bỏ hằng `ROLE_*`, `SUBMISSION_NOT_ASSIGNED_MESSAGE`;
   - bỏ method `findReadableSubmission(...)` + `hasAuthority(...)`;
   - đổi chữ ký `analyzeSubmission(requesterId, authorities, submissionId)` → **`analyzeSubmission(submissionId)`** (đúng bản pre-hoangnhat);
   - fetch lại bằng `submissionRepository.findById(submissionId).orElseThrow(...)`;
   - bỏ import `java.util.Set`, `ForbiddenException` (không còn dùng).
3. **Giữ nguyên** khối repo-analysis của #259 (`buildRepoAnalysis`, `commitSignal`, `appendRepoSection`, field `gitHubRepoService`) — chỉ phụ thuộc `GitHubRepoService`/`RepoDigest`/`AiInsightResponse` (đều là file riêng của #259, không bị xóa).
4. **Lưu ý giữ:** message tiếng Anh của AI assistant là chủ ý của #259 (commit "localize AI assistant to English") → KHÔNG đổi lại tiếng Việt.

**Khớp caller:** `AiController` sau revert gọi đúng `analyzeSubmission(submissionId)` (1 tham số). `findBySubmissionIdAndJudgeId` sau khi bỏ `findReadableSubmission` thì **không còn ai dùng** ⇒ revert bỏ nó khỏi `SubmissionRepository` là an toàn.

`ScoringService` conflict chỉ là khác biệt **import** (HEAD/#260 dùng import tường minh + `ArrayList/BigDecimal/Objects/Set`; bản revert dùng wildcard + chỉ `List`) → giữ HEAD vì body #260 cần.

---

## PHẦN 5 — KIỂM CHỨNG

| Bước | Kết quả |
|---|---|
| `mvnw -DskipTests compile` (main) | ✅ exit 0 |
| `mvnw test-compile` | ✅ exit 0 (test hoangnhat đã bị gỡ; test #259/#260 còn lại biên dịch sạch) |
| `mvnw -Dtest=AiJudgeAssistantRepoAnalysisTest,GitHubRepoServiceTest test` | ✅ exit 0 — logic repo-analysis #259 vẫn đúng sau khi nối lại |
| Không còn marker conflict trong toàn repo | ✅ |
| File #259/#260 hiện diện · đảo hoangnhat đã xóa | ✅ |

---

## PHẦN 6 — PHẠM VI COMMIT

`git diff --cached --stat develop`: **121 files changed, 967 insertions(+), 13566 deletions(−)**
— 4 added (restore) · 86 deleted (đảo hoangnhat) · 31 modified.

**Được giữ nguyên:** #259 (ai-judge repo analysis), #262 (fix login double-session, nhánh sạch), #260 (criteria templates + scoring normalization), #263 (db scripts refactor).
**Bị gỡ:** toàn bộ hoangnhat-draft — FE redesign + shadcn kit, lớp auth submission, OAuth2LoginFailureHandler, Docker/Caddy/`deploy.yml`, và phần mở rộng service do `1a563ca` mang vào.

---

## PHẦN 7 — VIỆC CÒN LẠI / LƯU Ý CHO TEAM

- **`f0a0a01`** (fix import ScoringService) để nguyên trong lịch sử — vô hại sau resolve; không cần thao tác thêm.
- **Sau khi PR revert merge:** cả team chỉ cần `git pull` (KHÔNG ai phải reset cứng vì không rewrite history).
- **Hạ tầng deploy (Docker/Caddy/CI) đã bị gỡ** theo yêu cầu — nếu sau này cần deploy, dựng lại từ đầu (không khôi phục bản hoangnhat).
- Local của KTrangg hôm nay đã được `git stash` (message: *"WIP local 2026-06-26 before hoangnhat revert"*) — còn recoverable nếu cần.
- **Bài học để lại:** với mọi lần gỡ PR đã merge sâu — luôn (1) kiểm ancestry các nhánh sau, (2) ưu tiên `revert` thay vì `reset` trên nhánh chung, (3) **compile + test bắt buộc** vì phụ thuộc ẩn không lộ ra trong diff.
