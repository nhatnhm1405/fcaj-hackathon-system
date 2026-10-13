# AI LOG — Demo Runner Script (`run-demo.ps1`) — BACKEND — 2026-07-11

> Thêm 1 script PowerShell **gói các bước chạy demo code-first** thành 1 lệnh.
> Không đụng logic seed (đã có sẵn ở `DemoScenario`/`DemoSeeder`) — chỉ là "remote 1 nút".
> Tiếp nối session code-first: [[AI_LOG_SEAL_★_CODE_FIRST_HIBERNATE_MIGRATION_BE_2026-07-10]].

---

## 1. Bối cảnh

Sau khi chuyển sang code-first, mỗi lần đổi kịch bản demo phải làm tay 4 bước:
1. Vào MySQL `DROP DATABASE seal_hackathon;` (nếu không → `DemoSeeder` guard sẽ **skip**)
2. `set SEED_SCENARIO=Sx`
3. `cd back-end/src/seal-api`
4. `./mvnw.cmd spring-boot:run`

Lặp lại hoài, dễ quên bước drop → tưởng lỗi. Cần đóng gói cho gọn, và **chạy được trên mọi máy team** (DB pass mỗi máy một khác).

## 2. Quyết định thiết kế

| Vấn đề | Quyết định |
|--------|-----------|
| Pass DB khác nhau mỗi máy | Script **tự đọc `DB_USERNAME`/`DB_PASSWORD` từ `.env`** — KHÔNG hardcode |
| Lộ pass khi commit | Không nhét pass vào script; `.env` vốn đã gitignore → an toàn commit chung |
| `mysql` không có trên PATH | Tự dò `mysql.exe`: PATH trước, fallback `C:\Program Files\MySQL\...\Server\...\bin` |
| Lỡ tay drop DB thật | Mặc định **hỏi xác nhận** trước khi drop; `-Force` để bỏ qua lúc demo gấp |
| Không muốn drop | Cờ `-NoDrop` (chạy tiếp trên DB hiện có; lưu ý seed sẽ bị skip) |
| Ngôn ngữ output | Tiếng Anh (theo yêu cầu — nhìn gọn/chuyên nghiệp) |

## 3. File

**Mới:** `back-end/src/seal-api/run-demo.ps1`

**Tham số:** `-Scenario NONE|S0|S1|S2|S3` (positional), `-TeamsPerTrack <int>` (mặc định 3),
`-Force`, `-NoDrop`. Có comment-based help (`Get-Help ./run-demo.ps1`).

**Luồng thực thi:**
```
./run-demo.ps1 S3
   ├─ đọc .env  → DB_USERNAME, DB_PASSWORD (máy hiện tại)
   ├─ dò mysql.exe (PATH → Program Files)
   ├─ [hỏi y/n trừ khi -Force]  DROP DATABASE seal_hackathon
   ├─ set SEED_SCENARIO + SEED_TEAMS_PER_TRACK
   └─ ./mvnw.cmd spring-boot:run   (Hibernate tạo lại schema + DemoSeeder seed)
```

## 4. Cách dùng

```powershell
./run-demo.ps1 S3            # full: scores + rankings + prizes (COMPLETED)
./run-demo.ps1 S2           # cảnh JUDGE chấm điểm (bài đã có sẵn)
./run-demo.ps1 S1           # tạo/ghép đội (event OPEN)
./run-demo.ps1 S0           # chỉ tài khoản demo
./run-demo.ps1              # NONE - chạy sạch, không data giả

./run-demo.ps1 S3 -Force    # drop không hỏi
./run-demo.ps1 S2 -NoDrop   # không drop, chạy trên DB hiện có
```
Lần đầu nếu bị chặn: `Set-ExecutionPolicy -Scope Process -ExecutionPolicy Bypass`.

## 5. Verify

- Parse cú pháp bằng `[Parser]::ParseFile` → **không lỗi**.
- Test đọc `.env` tách rời → parse đúng `DB_USERNAME=root` + pass 7 ký tự.
- **Chưa** chạy end-to-end thật (app boot) trong session này — người dùng tự chạy trên IntelliJ.

## 6. Ghi chú

- Script **không** thay/lặp logic seed — chỉ orchestrate. Đổi kịch bản = sửa Java (`DemoScenario`), không sửa script.
- IntelliJ: có thể thay bằng Run Configuration với env `SEED_SCENARIO=Sx`, nhưng script tiện hơn vì lo luôn phần drop DB.
