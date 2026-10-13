# Deploy SSH & Participant Access Fix - 2026-07-04

## Van de da xu ly

- GitHub Actions deploy SSH thieu buoc kiem tra secret, script remote khong dung ngay khi loi, va khong thong bao ro khi sai thu muc deploy.
- Backend build bi loi do Lombok annotation processor chua duoc cau hinh ro rang, `TeamService` co field repository bi khai bao trung, va mot DTO history bi goi sai nested class.
- Participant cu sau khi event cu `COMPLETED` van bi gan team cu vao `/api/auth/me` va `/api/teams/my`, lam frontend hieu sai la user dang co team hien tai nen khong vao duoc luong tao/join team cho event moi.
- Frontend build bi loi do `useRoundTimer.ts` bi truncate cuoi file.

## Thay doi chinh

- Cap nhat `.github/workflows/deploy.yml`:
  - Them preflight validate SSH secrets.
  - Ho tro ca bo secret cu va ten pho bien: `HOST/USERNAME/SSH_KEY`, `SSH_HOST/SSH_USERNAME/SSH_PRIVATE_KEY`, `EC2_HOST/EC2_USER/EC2_SSH_KEY`.
  - Them `SSH_PORT`, `APP_DIR`, `script_stop`, checkout/pull `develop` an toan bang `--ff-only`, va in `docker compose ps` sau deploy.
- Cap nhat backend:
  - Sua Lombok compiler config trong `pom.xml`.
  - `/api/auth/me` chi gan `teamId` neu user co membership trong event `OPEN`, `SETUP`, hoac `IN_PROGRESS`; khong fallback sang event `COMPLETED`.
  - `/api/teams/my` chi tra team hien tai, khong tra team lich su.
  - `createTeam` chan user chua approved hoac read-only o service layer.
  - Invite team dung rule nhat quan: event `OPEN` la dieu kien dang ky, khong chan bang registration date cu.
- Cap nhat frontend:
  - Khi nhan notification `PARTICIPATION_ACCESS_APPROVED`, client mo khoa `is_active` va clear team context cu.
  - Khoi phuc phan cuoi `useRoundTimer.ts`.
  - Xoa duplicate key trong `apiClient.ts` va `DashboardLayout.tsx`.

## Kiem tra

- `back-end/src/seal-api`: `mvnw test` pass 320/320 tests.
- `back-end/src/seal-api`: `mvnw -DskipTests package` pass.
- `front-end/src/seal-web`: `npm test` pass 28/28 tests.
- `front-end/src/seal-web`: `npm run build` pass.
- Root project: `docker compose config` parse duoc compose file.

## Luu y

- GitHub repo secrets can co it nhat: SSH host, SSH user, SSH private key. Neu project tren server khong nam o `$HOME/seal-hackathon-system`, them secret `APP_DIR`.
- Vite build con warning chunk size lon; day la canh bao toi uu bundle, khong phai loi deploy.
