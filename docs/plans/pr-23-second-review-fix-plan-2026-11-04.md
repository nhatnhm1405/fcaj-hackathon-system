# PR #23 — Review lần hai và kế hoạch sửa bổ sung

Ngày 04/11/2026, Asia/Saigon. Yêu cầu hiện tại: kiểm lại và lên plan; không sửa source, commit hoặc push trong lượt này.

**Cập nhật thực thi:** người dùng đã yêu cầu tiến hành sửa sau lượt review. Phần CI/path checks đã được triển khai và kiểm local theo [báo cáo sửa CI](../report/pr-23-ci-fix-2026-11-04.md). Các snapshot và mô tả chưa sửa dưới đây là checkpoint trước thực thi. Chưa có cho phép push lại sau lần từ chối trước đó.

## 1. Snapshot đã xác minh

| Bản | Revision | Trạng thái |
| --- | --- | --- |
| PR #23 trên GitHub | `4d38cb10a60e9f625a15ddfce61c9353d5a53f48` | Đã fetch và đọc lại GitHub API; PR open, mergeable=true, mergeable_state=clean, chưa merge |
| Local `codex/pr-23-keep-seal` | `6233702cb6a73a563103a40967134bf1662d9336` | Có thêm commit sửa 5 BAT launchers và verifier; chưa push vì yêu cầu push trước đó bị từ chối |
| Target `develop` | `18d1783a9d428466c166c00c1595bc1f45b59388` | Không đổi so với lượt tích hợp |
| Feature trước tích hợp | `4049d107a78235be9e95efa9185e2b034b07c9ec` | Mốc đối chiếu bảo toàn BE1 |

[PR #23](https://github.com/nhatnhm1405/fcaj-hackathon-system/pull/23) có **0 check runs**, **0 commit statuses**, combined status=`pending` tại thời điểm review. `clean` xác nhận khả năng merge về Git, không có nghĩa CI đã đạt.

File `docs/report/be1-tra-loi-be2-truoc-deploy-2026-11-03.md` vẫn là thay đổi local có sẵn; không đưa vào phạm vi sửa/commit của plan này.

## 2. Findings đã có bằng chứng

### F01 — P2: PR vẫn chứa 5 launcher Windows trỏ tới module không tồn tại

Trong commit đã publish `4d38cb1`, cả năm file dưới `E2E testing/scenario-1`, `scenario-2`, `scenario-3` còn khai báo:

```bat
set "BACKEND=%ROOT%\back-end\src\fcaj-api"
set "FRONTEND=%ROOT%\front-end\src\fcaj-web"
```

Hai module tracked thực tế là `seal-api`/`seal-web`. Khi chạy launcher từ một checkout sạch của PR, `Set-Location` trỏ sai và không khởi động đúng backend/frontend. Audit đọc trực tiếp Git blobs xác nhận **10/10 module bindings của PR không tìm được pom.xml/package.json tương ứng**; không phụ thuộc các thư mục rỗng còn sót trong workspace.

Commit local `6233702` đã sửa đủ 10 bindings. Audit cùng cách trên local cho **0 missing bindings**, verifier đạt và negative probe đã chứng minh launcher cũ bị từ chối. Vì vậy việc còn lại của F01 là đưa commit đã có vào candidate được kiểm và phát hành; không viết lại hoặc cherry-pick lại bản sửa.

### F02 — P2: PR vào develop không được workflow kiểm thử tự động

Repository chỉ có `.github/workflows/deploy.yml`. Trigger ở dòng4–9 chỉ nhận push/main và pull_request/main; PR #23 nhắm develop nên không chạy. GitHub API xác nhận không có check run hoặc commit status nào trên SHA của PR.

Workflow hiện còn dùng chung concurrency group `aws-preview-deploy` với `cancel-in-progress: true` ở dòng11–13. **Không chỉ thêm develop vào trigger hiện tại:** cách đó sẽ cho PR validation dùng cùng group với luồng deploy main, có thể hủy lượt deploy đang chạy. Giải pháp đề xuất là workflow validation riêng, không deploy và không dùng secrets AWS/SSH.

Hiện workflow cũng chưa gọi verifier tích hợp, Python unit tests hoặc contract fixture validators. Chỉ mở trigger mà giữ nguyên danh sách job chưa tạo được kiểm tra tự động cho các phần Python/path đã thay đổi trong PR.

### V01 — Giới hạn kiểm chứng: MySQL8.4 và browser E2E chưa có kết quả mới

Lượt kiểm lại `docker info` vẫn lỗi do không có pipe `dockerDesktopLinuxEngine`; không khởi động được lab. Không gọi đây là test MySQL fail vì chưa chạy được suite. Không reset Docker/WSL hoặc dùng DB developer/RDS để thay lab.

Browser E2E mới ở mức discovery; các BAT có lệnh reset demo DB nên không chạy trực tiếp vào môi trường đang dùng chỉ để xác minh đường dẫn. Nếu cần full demo acceptance, dùng dataset/database riêng sau khi owner chốt môi trường.

V01 là phần bằng chứng còn thiếu, không phải phát hiện backend đã hỏng. Backend source/migrations đang khớp feature gốc.

## 3. Các phần đã kiểm lại và chưa phát hiện regression trong phạm vi tích hợp

- Verifier local đạt: 475 files dưới back-end giữ nội dung, 28 contract files giữ bytes; 34 files frozen gồm DB resources/contracts; 8 worker core files nguyên từ develop; 175 frontend files đối chiếu upstream; 5 BAT launchers đúng module đích.
- `git diff` giữa feature gốc và HEAD cho `back-end/src/seal-api/src` cùng `docs/contracts/ai-job-v1` rỗng. Core grader/cleaner/fetcher/schema/schemas/sample input/requirements của Python cũng có diff rỗng so với develop.
- `mvnw` có mode100755 ở cả PR và local. Runtime module/package/service references dùng SEAL; quét bổ sung cả path Windows/POSIX và Dockerfiles/config không thấy module FCAJ còn sót trên local.
- Hai chuỗi còn chữ FCAJ trong active source là User-Agent `fcaj-backend-snapshot` và public demo repository `fcaj-demo`; chúng đã có ở feature gốc và không phải module/service references, không đổi hàng loạt.
- Không có conflict entries/markers được phát hiện trong active source đã kiểm; whitespace check đạt.
- Số test **lượt triển khai trước** vẫn là backend633 thống kê/624 thực thi đạt/9 skipped, frontend162 đạt và build đạt, Python10 đạt. Review lần hai không chạy lại full suites vì source ứng dụng không thay đổi; không gọi các số đó là lượt test mới.
- Contract Python và Java vẫn khác nhau và đã được ghi rõ; không thêm adapter/model integration vào đợt sửa launcher/CI này.

## 4. Kế hoạch thực hiện đề xuất

### S01 — Giữ nguyên bản sửa local và bảo toàn thay đổi người dùng

1. Fetch lại head/base khi bắt đầu thực hiện. Nếu SHA đổi, cập nhật audit trước khi tích hợp tiếp.
2. Tiếp tục từ local `6233702`; không reset/rebase/force-push hoặc ghi đè file báo cáo đang sửa.
3. Giữ nguyên backend logic, migrations/contracts và core worker. Không tạo thêm commit sửa lại cùng 5 BAT đã đúng.

### S02 — Thêm CI riêng cho PR vào develop

Tạo `.github/workflows/pr-validation.yml` với phạm vi:

- `pull_request` vào `develop`; không thêm event deploy/push develop trong task này.
- `permissions: contents: read`; không dùng AWS/SSH secrets và không có job deploy.
- Concurrency riêng theo workflow và PR number; chỉ hủy lượt cũ của cùng PR, không dùng `aws-preview-deploy`.
- Checkout đủ Git history (`fetch-depth: 0`) cho verifier: script cần đọc blobs của feature/develop SHAs cố định, default shallow checkout một commit là không đủ.
- Các job frontend/backend/Python chạy độc lập; các Maven commands cùng một job ghi chung target phải chạy tuần tự.

| Nhóm kiểm tra | Lệnh/phạm vi đề xuất |
| --- | --- |
| Snapshot/path preservation | `node docs/checks/pr-23-integration-2026-11-04/verify-integration.mjs`; chạy trên candidate checkout, không dùng bản local khác với SHA đang được CI kiểm |
| Frontend | `npm ci`, `npm test`, `npm run build` tại `front-end/src/seal-web`; không nâng dependencies |
| Backend | Java21, Maven Wrapper profile test/package tại module SEAL; lưu kết quả test gắn đúng SHA |
| MySQL8.4 | `bash Test/run-ai-mysql-tests.sh` trên Ubuntu runner có Docker; script dùng container/catalog riêng, bind loopback và cleanup hiện có |
| Python | Python3.12, `python -m unittest discover -s ai-worker/tests -v`; model HTTP đã mock, không cần model key |
| Contract/scripts | Current/legacy fixture validators, dump parser và deploy-gate tests; không gọi queue thật hoặc deploy script vận hành |

Node version phải được khai báo trong workflow, thống nhất với toolchain được kiểm; có thể dùng Node22 để khớp frontend Dockerfile. Đây là cấu hình CI, không phải nâng runtime/dependencies của sản phẩm trong task này.

Giữ workflow deploy main hiện tại ngoài phạm vi hành vi. Nếu nhóm muốn branch protection bắt buộc checks mới, owner cấu hình sau; không tự đổi repository protection hoặc quyền admin.

### S03 — Kiểm candidate trước phát hành

- Chạy verifier trên candidate; kiểm cả 5 BAT và 10 module bindings. Chạy negative probe cho path FCAJ Windows/POSIX, chỉ đọc/ghi fixture tạm và khôi phục trong finally; không chạy BAT reset DB.
- Kiểm YAML/workflow events, permissions, concurrency và điều kiện deploy. Không dùng workflow mới để đụng AWS hoặc main deployment.
- Chạy các checks mới độc lập có thể chạy local; không lặp full tests không có thay đổi liên quan chỉ để tăng số lượt kiểm.
- MySQL lab chạy khi có Docker, hoặc lấy kết quả từ job CI mới sau khi phát hành candidate. Nếu runner không chạy được Docker thì giữ trạng thái chưa nghiệm thu, không thay bằng H2 rồi gọi đó là MySQL pass.
- Giữ nguyên manifests/checksums lịch sử. Bổ sung checkpoint mới với candidate SHA, CI check URLs và kết quả thực tế; phân biệt local, published và target acceptance.

### S04 — Phát hành sau khi người dùng cho phép push trở lại

- Commit riêng phần CI/docs sau khi review; giữ lịch sử `4d38cb1 → 6233702 → <CI commit>`.
- **Yêu cầu push trước đó đã bị từ chối. Lượt này không retry push; chỉ thực hiện khi có cho phép mới của người dùng.**
- Khi được phép, push không force lên chính head branch của PR #23; không merge develop/main.
- Đọc lại GitHub API: head phải đúng candidate, check runs phải xuất hiện và kết thúc thành công. `mergeable=clean` với0 checks không đạt tiêu chí CI.

## 5. Điều kiện chấp nhận

- PR được cập nhật bằng commit có sửa cả 5 BAT; audit Git tree của published SHA không còn binding thiếu module.
- CI chạy được cho PR vào develop, gồm verifier/Python/backend/frontend/MySQL và contract/script checks phù hợp; không khởi động hoặc hủy main deployment.
- Source/contracts BE1 và core worker giữ đúng phạm vi đã thống nhất, hoặc mọi thay đổi mới được review rõ ràng.
- Kết quả MySQL mới có SHA/log đúng candidate, hoặc được ghi rõ là chưa có trước khi quyết định merge. Không tuyên bố browser/AWS/model acceptance từ các checks này.
- File báo cáo local của người dùng còn nguyên; không merge/deploy hoặc gửi tin cho team ngoài phạm vi được giao.

## 6. Trạng thái cuối lượt review lần hai

Chỉ đọc source/diffs, fetch refs, query PR/checks, chạy verifier và audit Git blobs; thêm plan này và scratch evidence ignored ở `back-end/src/seal-api/target/merge-conflict-scan-2026-11-04/second-review.json`. Chưa thay source/workflow, chưa tạo commit hoặc push thêm trong lượt này.
