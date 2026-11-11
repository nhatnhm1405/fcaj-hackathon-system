# Source bàn giao BE2 — 03/11/2026

- Branch riêng đã push: [feature/seal-be1-handoff-2026-11-03](https://github.com/nhatnhm1405/fcaj-hackathon-system/tree/feature/seal-be1-handoff-2026-11-03).
- Source SHA cố định để build: **`3eda1fb189a972b9a5b5c85ce482900a1c66ac42`**.
- [Báo cáo trả lời BE2](../../report/be1-tra-loi-be2-truoc-deploy-2026-11-03.md) ghi full SHA, checkout, migrations/config/API/worker và đầu vào còn chờ.
- Commit tài liệu theo sau source chỉ thêm verifier/manifest và cập nhật báo cáo/index. Không merge main hoặc deploy AWS.

`source-manifest.json` ghi inventory/mode/OID/SHA256 của **Git blob bytes tại source commit**, cùng kết quả kiểm test và staged payload. 418 file module cũ đều có bản SEAL tương ứng; không có deletion không có replacement. Pattern scan không thấy private key/AWS access key/GitHub token/Google API key/JWT literal trong payload; đây là bằng chứng hỗ trợ kiểm scope, không phải tuyên bố đã pentest.

Trước commit, staged bytes được đối chiếu với working tree đã test: khác biệt nếu có chỉ là CRLF→LF theo Git attributes. Không sửa source/runtime/test khi tạo checkpoint phát hành. Kết quả624 thực thi đạt/9 MySQL-only skipped và component36/36 là các lượt local đã lưu, không phải test mới chạy lại sau commit. MySQL36/36 thuộc checkpoint trước fix test-only. Các suite có ca trùng, không cộng tổng.

Từ checkout tip branch tài liệu, có source commit trong local Git:

```text
node docs/checks/seal-be1-release-2026-11-03/verify-release.mjs
```

Verifier chỉ đọc: kiểm tree/blob của source commit, code hiện hành khớp source và links tài liệu. Với clone shallow thiếu source/parent, fetch lịch sử branch trước. Dùng SHA source đã ghi khi chạy deploy gate; không lấy hash JAR/image hoặc baseline `bab44e5…` thay commit.

Manifest/verifier local, review, JWT và phản hồi trước đó giữ nguyên để truy lịch sử working-tree bytes trên baseline cũ. Chúng không phải release verifier và có thể báo HEAD/inventory/EOL drift sau commit hoặc trên clone Linux. Không refresh checksum lịch sử; dùng verifier release này cho bản đã phát hành.

Source được push không kèm raw target logs/XML/JAR/image, private env/truststore/dump hoặc file người dùng. BE2 build từ source SHA, ghi image identity thực, kiểm target/TLS/IAM và hai job Java MOCK trong window đã chốt. Model Python contract/upstream integration, browser, restart toàn process và AWS acceptance vẫn cần bằng chứng riêng.
