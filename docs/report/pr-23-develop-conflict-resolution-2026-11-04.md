# PR #23 — Tích hợp develop và giữ tên SEAL

Ngày 04/11/2026, Asia/Saigon. Thực hiện theo [plan đã thống nhất](../plans/pr-23-develop-conflict-resolution-plan-2026-11-04.md), sau khi người dùng xác nhận giữ SEAL và yêu cầu bắt đầu sửa.

## Resolution

Feature đầu vào `4049d107a78235be9e95efa9185e2b034b07c9ec`; develop đầu vào `18d1783a9d428466c166c00c1595bc1f45b59388`. Nhánh thực hiện: `codex/pr-23-keep-seal`, merge develop vào feature để giữ ancestry của cả hai bên.

Đã giải quyết 111 conflict vị trí file và 4 conflict nội dung. Backend/frontend dùng một cặp module `seal-api`/`seal-web`; Java namespace và các main class dùng `com.seal.hackathon`; workflow, Docker/Compose/Caddy và E2E launchers dùng đường dẫn/service SEAL nhất quán. Giữ sửa quyền executable `100755` của `mvnw` từ develop để Linux CI hoạt động.

Giữ backend BE1 nguyên nội dung: bootstrap admin opt-in, seed reference, JWT/cookie/CSRF/session, storage adapters, job delivery, authorization và Flyway V1/V2/V3. Bốn content conflicts ở DataSeeder, DemoFixtures, TrackProblemService và application.properties đã được resolve theo thiết kế này.

Frontend giữ các tên trung tính từ develop như AppFooter/brandLogo, selector/animation names và các references tương ứng; không phục hồi tên cũ không cần thiết. npm metadata dùng `seal-web` trong cả package.json và lockfile, không nâng dependency. Thay đổi E2E của feature được bảo toàn.

## Phần của Python worker

Giữ nguyên grader, source cleaner, GitHub fetcher, schema, JSON schemas, requirements và sample input từ develop. Hiệu chỉnh mock worker/sample output về paths SEAL; thay evidence cấu hình cũ bằng đoạn datasource environment thực tế từ application-aws.properties. Điểm mock và thuật toán tính điểm giữ nguyên, tổng mẫu vẫn 8.05. Feedback/summary ghi rõ kết quả synthetic, tránh khẳng định source mới còn cấu hình fallback đã bị loại bỏ.

Test evidence hiện kiểm cả snippet khớp chính xác đoạn source theo số dòng, ngoài kiểm file tồn tại. Sample này tham chiếu bản tích hợp local; README ghi rõ nó không phải đánh giá lại commit lịch sử trong sample input.

Hai contract Java/Python vẫn riêng: không bổ sung adapter, không đổi envelope `fcaj.ai.*`, không ghi kết quả Python vào Score/RoundResult. Các tên tài nguyên/config persisted không bị đổi hàng loạt. Smoke transport Java vẫn dùng Java MOCK worker cùng candidate.

## Kiểm chứng mới trên bản resolution

| Kiểm tra | Kết quả và phạm vi |
| --- | --- |
| Full backend/package | 633 thống kê: **624 thực thi đạt**, 0 failures/errors, 9 MySQL-only skipped. Java21, profile test, H2/mocks; kết thúc 21:20:14 ngày04/11, giờ Việt Nam. |
| Frontend | **162/162** tests; production build đạt. Còn warning bundle lớn đã có, không đổi cấu trúc bundle trong task này. |
| Python AI worker | **10/10** unit tests, model HTTP được mock; sample/mock parity và source evidence exact-match đạt. |
| Deploy gate | **8/8**; lỗi init/verify/precheck không thay backend. Các Docker commands trong suite này được giả lập. |
| Dump parser | **7/7** fixtures, không chạy dump thật. |
| Contracts | Current examples và 7 legacy fixtures đạt identity/hash/bytes checks. Không gửi message vào AWS. |
| Compose | Root dev stack và hai AWS templates parse đạt; ALB-direct thiếu bind IP bị từ chối. Parse không cần engine hoặc kết nối target. |
| Demo preflight / syntax | **19/19** parser/helper checks; PowerShell/Bash syntax đạt; không chạy demo/reset. |
| E2E discovery | 5 tests trong 5 files được nhận ở module SEAL; không chạy browser scenarios hoặc reset dataset. |
| JAR | Start-Class `com.seal.hackathon.HackathonApplication`; có migration/worker main SEAL; không có classes com/fcaj hoặc H2 runtime. SHA256 `eceba05698fc732434a1cbf0fa283f4061505fec47883bf8f46fcb2de1065198`. |
| Source preservation | Verifier mới đạt: **475 files dưới back-end** giữ nội dung, **28 contract files** giữ bytes; tổng **34 frozen files** gồm contracts và DB resources. **8 worker core files** nguyên từ develop; **175 frontend files** đối chiếu develop, với E2E feature và npm name được kiểm riêng. |

Các suite có test trùng nhau, không cộng thành tổng độc lập. Lượt Python trong sandbox gặp lỗi quyền thư mục tạm; cùng suite ngoài sandbox đạt10/10. Backend được chạy ngoài sandbox để test loopback hoạt động. Logs/XML/JAR/npm dependencies và merge inventory ở ignored target/node_modules, không đưa vào source commit.

**MySQL8.4 lab chưa chạy lại trong lượt này:** Docker Desktop không khởi động thành công, Linux engine pipe không tồn tại; CLI startup kết thúc với `context deadline exceeded`, Desktop backend cũng thoát sau khi mở. Chưa tạo container lab mới hoặc chạy migration lên DB nào. Migrations/backend giữ nguyên so với feature đã có bằng chứng lab lịch sử, nhưng kết quả đó không được gọi là lượt kiểm MySQL mới. Cần chạy `Test/run-ai-mysql-tests.ps1` khi engine hoạt động nếu muốn nghiệm thu lại MySQL trên candidate này. Không reset/factory-reset, sửa WSL hoặc xóa dữ liệu Docker để vượt blocker.

## Kiểm lại và phạm vi bảo toàn

Từ repository root:

```text
node docs/checks/pr-23-integration-2026-11-04/verify-integration.mjs
```

[Verifier](../checks/pr-23-integration-2026-11-04/verify-integration.mjs) đọc Git bytes của hai revision đầu vào, kiểm backend/contracts, worker core, frontend/E2E, executable wrapper và runtime naming. Nó không thay các manifests lịch sử ngày03/11. Các verifier release cũ vẫn mô tả source cũ; không sửa checksum để gọi source tích hợp là source đã phát hành trước đó.

File `docs/report/be1-tra-loi-be2-truoc-deploy-2026-11-03.md` có sửa local từ trước được giữ nguyên, không đưa phần sửa đó vào resolution commit. Không merge develop/main, deploy AWS, gọi model thật hoặc gửi tin cho team. Cập nhật PR head sau khi hoàn tất kiểm source/index là thao tác phát hành resolution; nghiệm thu AWS/browser/model và MySQL lab mới vẫn là các phạm vi riêng.

## Review bổ sung sau merge commit 4d38cb1

Khi người dùng yêu cầu xem các thay đổi, review diff với feature cũ phát hiện 5 E2E BAT launchers vẫn dùng đường dẫn Windows `fcaj-api/fcaj-web`. Mô phỏng conflict và Playwright discovery đã không bắt lỗi này; verifier ban đầu chỉ nhận path phân cách bằng `/`. Đã sửa 10 bindings về SEAL, bổ sung nhận cả `/` và `\\`, đối chiếu từng launcher với feature gốc và kiểm hai module đích tồn tại. Chỉ sửa đường dẫn launcher và kiểm chứng; không chạy các BAT vì chúng có thể reset demo data. Backend/frontend application source và các kết quả test trước đó không đổi; MySQL lab vẫn chưa chạy lại do Docker.
